package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUBuilder;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.KeyInfo;
import name.velikodniy.jcexpress.scp.SCP02;
import name.velikodniy.jcexpress.scp.SCP03;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.scp.SCPKeys;
import name.velikodniy.jcexpress.scp.SecureChannel;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * High-level GlobalPlatform session helper that automates the SCP authentication flow.
 *
 * <p>Wraps a {@link SmartCardSession} and provides GP-specific operations.
 * All commands sent through an opened GPSession are automatically wrapped
 * with the secure channel (C-MAC, optionally C-ENC).</p>
 *
 * <h2>Authentication flow (performed by {@link #open()}):</h2>
 * <ol>
 *   <li>Sends INITIALIZE UPDATE (INS=0x50) with random 8-byte host challenge</li>
 *   <li>Parses response → auto-detects SCP02 vs SCP03</li>
 *   <li>Derives session keys and verifies card cryptogram</li>
 *   <li>Sends EXTERNAL AUTHENTICATE (INS=0x82) with host cryptogram, wrapped with C-MAC</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * <pre>
 * GPSession gp = GPSession.on(session)
 *     .keys(SCPKeys.defaultKeys())
 *     .open();
 *
 * // All commands auto-wrapped with secure channel
 * APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00, data);
 *
 * gp.close();
 * </pre>
 *
 * <h2>Custom configuration:</h2>
 * <pre>
 * GPSession gp = GPSession.on(session)
 *     .keys(SCPKeys.of(enc, mac, dek))
 *     .securityLevel(GP.SECURITY_C_MAC_C_ENC)
 *     .keyVersion(0x30)
 *     .scpVersion(3)  // force SCP03
 *     .open();
 * </pre>
 *
 * @see SecureChannel
 * @see SCP02
 * @see SCP03
 */
public final class GPSession implements AutoCloseable {

    private final SmartCardSession session;
    private SCPKeys keys;
    private int securityLevel = GP.SECURITY_C_MAC;
    private int keyVersion = 0;
    private int forcedScpVersion = 0; // 0 = auto-detect
    private boolean pseudoRandomChallenge = false;
    private byte[] hostChallenge;
    private BiFunction<SCPKeys, byte[], SCPKeys> diversifier;

    private SecureChannel channel;
    private CardInfo cardInfo;
    private boolean opened;

    private GPSession(SmartCardSession session) {
        this.session = session;
    }

    /**
     * Creates a new GPSession wrapping the given smart card session.
     *
     * @param session the underlying session (embedded or container)
     * @return a new GPSession ready for configuration and {@link #open()}
     */
    public static GPSession on(SmartCardSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        return new GPSession(session);
    }

    /**
     * Sets the key set for authentication. Default: {@link SCPKeys#defaultKeys()}.
     *
     * @param keys the static key set
     * @return this session for chaining
     */
    public GPSession keys(SCPKeys keys) {
        this.keys = keys;
        return this;
    }

    /**
     * Sets the security level for the session.
     *
     * <p>Default: {@link GP#SECURITY_C_MAC} (command integrity only).</p>
     *
     * @param level the security level (e.g., {@link GP#SECURITY_C_MAC_C_ENC})
     * @return this session for chaining
     */
    public GPSession securityLevel(int level) {
        this.securityLevel = level;
        return this;
    }

    /**
     * Sets the key version number sent in INITIALIZE UPDATE (P2).
     *
     * <p>Default: 0 (any key version). Set to a specific value if the card
     * has multiple key sets.</p>
     *
     * @param version the key version (0-127)
     * @return this session for chaining
     */
    public GPSession keyVersion(int version) {
        this.keyVersion = version;
        return this;
    }

    /**
     * Forces a specific SCP version instead of auto-detecting from the response.
     *
     * <p>Default: 0 (auto-detect from INITIALIZE UPDATE response byte[11]).
     * Set to 2 or 3 to override detection for non-compliant cards.</p>
     *
     * @param version the SCP version (2 or 3)
     * @return this session for chaining
     */
    public GPSession scpVersion(int version) {
        if (version != 0 && version != 2 && version != 3) {
            throw new IllegalArgumentException("SCP version must be 0 (auto), 2, or 3, got: " + version);
        }
        this.forcedScpVersion = version;
        return this;
    }

    /**
     * Enables SCP03 pseudo-random card challenge mode (i=60).
     *
     * <p>When enabled, the INITIALIZE UPDATE response is parsed with 3-byte sequence
     * counter + 6-byte card challenge (29 bytes total), instead of the default 8-byte
     * card challenge (28 bytes). The key derivation context includes the sequence counter.</p>
     *
     * <p>Default: false (explicit challenge, i=70).</p>
     *
     * @param enabled true for pseudo-random (i=60), false for explicit (i=70)
     * @return this session for chaining
     */
    public GPSession pseudoRandomChallenge(boolean enabled) {
        this.pseudoRandomChallenge = enabled;
        return this;
    }

    /**
     * Sets a fixed host challenge instead of a random one.
     *
     * <p>For testing only. In production, the host challenge should be random.</p>
     *
     * @param challenge the 8-byte host challenge
     * @return this session for chaining
     */
    public GPSession hostChallenge(byte[] challenge) {
        if (challenge == null || challenge.length != 8) {
            throw new IllegalArgumentException("Host challenge must be exactly 8 bytes");
        }
        this.hostChallenge = challenge.clone();
        return this;
    }

    /**
     * Sets a key diversification function to derive card-specific keys.
     *
     * <p>When set, the master keys are diversified using the card's diversification
     * data (first 10 bytes of the INITIALIZE UPDATE response) before creating
     * the secure channel session.</p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>
     * GPSession gp = GPSession.on(card)
     *     .keys(SCPKeys.fromMasterKey(masterKey))
     *     .diversification(KeyDiversification::visa2)
     *     .open();
     * </pre>
     *
     * @param diversifier function that takes (masterKeys, diversificationData) and returns diversified keys
     * @return this session for chaining
     * @see name.velikodniy.jcexpress.scp.KeyDiversification
     */
    public GPSession diversification(BiFunction<SCPKeys, byte[], SCPKeys> diversifier) {
        this.diversifier = diversifier;
        return this;
    }

    // ── Lifecycle ──

    /**
     * Opens the secure channel by performing INITIALIZE UPDATE + EXTERNAL AUTHENTICATE.
     *
     * <p>After successful return, all commands sent via {@link #send} are automatically
     * wrapped with the negotiated secure channel protocol.</p>
     *
     * @return this GPSession (now authenticated)
     * @throws GPException if authentication fails
     */
    public GPSession open() {
        if (opened) {
            throw new GPException("GPSession is already open");
        }
        if (keys == null) {
            keys = SCPKeys.defaultKeys();
        }

        // Generate host challenge
        if (hostChallenge == null) {
            hostChallenge = new byte[8];
            new SecureRandom().nextBytes(hostChallenge);
        }

        // Step 1: INITIALIZE UPDATE
        APDUResponse initResponse = session.send(
                GP.CLA_GP, GP.INS_INITIALIZE_UPDATE,
                keyVersion, 0x00, hostChallenge);

        if (!initResponse.isSuccess()) {
            throw new GPException("INITIALIZE UPDATE failed", initResponse.sw());
        }

        byte[] responseData = initResponse.data();
        cardInfo = CardInfo.parse(responseData, pseudoRandomChallenge);

        // Apply key diversification if configured
        if (diversifier != null) {
            byte[] divData = Arrays.copyOf(responseData, 10);
            keys = diversifier.apply(keys, divData);
        }

        // Determine SCP version
        int scpVersion = forcedScpVersion != 0 ? forcedScpVersion : cardInfo.scpVersion();

        // Step 2: Create secure channel and verify card cryptogram
        byte[] hostCryptogram;
        if (scpVersion == 2) {
            SCP02 scp02 = SCP02.from(keys, responseData, securityLevel);
            scp02.verifyCardCryptogram(hostChallenge);
            hostCryptogram = scp02.computeHostCryptogram(hostChallenge);
            channel = scp02;
        } else if (scpVersion == 3) {
            SCP03 scp03 = SCP03.from(keys, hostChallenge, responseData, securityLevel, pseudoRandomChallenge);
            // SCP03.from() verifies card cryptogram automatically
            hostCryptogram = scp03.hostCryptogram();
            channel = scp03;
        } else {
            throw new GPException("Unsupported SCP version: " + scpVersion);
        }

        // Step 3: EXTERNAL AUTHENTICATE
        byte[] extAuthApdu = APDUBuilder.command()
                .cla(GP.CLA_GP_SECURE).ins(GP.INS_EXTERNAL_AUTHENTICATE)
                .p1(securityLevel).p2(0x00)
                .data(hostCryptogram)
                .build();

        byte[] wrappedExtAuth = channel.wrap(extAuthApdu);
        byte[] rawExtAuthResponse = session.transmit(wrappedExtAuth);
        APDUResponse extAuthResponse = new APDUResponse(rawExtAuthResponse);

        if (!extAuthResponse.isSuccess()) {
            throw new GPException("EXTERNAL AUTHENTICATE failed", extAuthResponse.sw());
        }

        opened = true;
        return this;
    }

    /**
     * Returns true if the secure channel has been opened.
     *
     * @return true if authenticated
     */
    public boolean isOpen() {
        return opened;
    }

    /**
     * Returns the card information parsed from the INITIALIZE UPDATE response.
     *
     * @return the card info, or null if not yet opened
     */
    public CardInfo cardInfo() {
        return cardInfo;
    }

    /**
     * Returns the underlying secure channel (SCP02 or SCP03).
     *
     * @return the secure channel, or null if not yet opened
     */
    public SecureChannel secureChannel() {
        return channel;
    }

    // ── Command dispatch ──

    /**
     * Sends a command through the secure channel with automatic wrapping.
     *
     * <p>The command is wrapped with C-MAC (and optionally C-ENC), then
     * sent through the session. GET RESPONSE chaining (SW=61XX) is handled
     * automatically.</p>
     *
     * @param cla  the CLA byte (will be modified by secure channel wrapping)
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @return the APDU response
     * @throws GPException if the session is not open
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        requireOpen();
        byte[] apdu = APDUBuilder.command()
                .cla(cla).ins(ins).p1(p1).p2(p2)
                .data(data)
                .build();
        return transmitWrapped(apdu);
    }

    /**
     * Sends a command without data.
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the APDU response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null);
    }

    /**
     * Wraps and transmits a raw APDU through the secure channel.
     *
     * @param rawApdu the raw APDU bytes (will be wrapped)
     * @return the raw response bytes
     */
    public byte[] transmit(byte[] rawApdu) {
        requireOpen();
        byte[] wrapped = channel.wrap(rawApdu);
        return session.transmit(wrapped);
    }

    // ── GET DATA ──

    /**
     * Sends a GET DATA command with the specified P1 and P2.
     *
     * @param p1 the P1 parameter
     * @param p2 the P2 parameter
     * @return the APDU response
     */
    public APDUResponse getData(int p1, int p2) {
        requireOpen();
        return send(GP.CLA_GP, GP.INS_GET_DATA, p1, p2);
    }

    /**
     * Retrieves the Issuer Identification Number (IIN).
     *
     * <p>Sends GET DATA P1P2=0042.</p>
     *
     * @return the IIN bytes
     * @throws GPException if the command fails
     */
    public byte[] getIIN() {
        APDUResponse r = getData(0x00, 0x42);
        if (!r.isSuccess()) {
            throw new GPException("GET DATA (IIN) failed", r.sw());
        }
        return r.data();
    }

    /**
     * Retrieves the Card Image Number (CIN).
     *
     * <p>Sends GET DATA P1P2=0045.</p>
     *
     * @return the CIN bytes
     * @throws GPException if the command fails
     */
    public byte[] getCIN() {
        APDUResponse r = getData(0x00, 0x45);
        if (!r.isSuccess()) {
            throw new GPException("GET DATA (CIN) failed", r.sw());
        }
        return r.data();
    }

    /**
     * Retrieves the Card Data (contains Card Recognition Data).
     *
     * <p>Sends GET DATA P1P2=0066. The response contains the Card Data template
     * (tag 0x66) with nested Card Recognition Data (tag 0x73) describing the card's
     * capabilities and supported protocols.</p>
     *
     * @return parsed CardData
     * @throws GPException if the command fails
     */
    public CardData getCardData() {
        APDUResponse r = getData(0x00, 0x66);
        if (!r.isSuccess()) {
            throw new GPException("GET DATA (Card Data) failed", r.sw());
        }
        return CardData.parse(r.data());
    }

    /**
     * Retrieves the Key Information Template (all keys on the card).
     *
     * <p>Sends GET DATA P1P2=00E0. Returns a list of key entries, each describing
     * a key set with its identifier, version, and cryptographic components.</p>
     *
     * @return list of key entries
     * @throws GPException if the command fails
     */
    public List<KeyInfoEntry> getKeyInformation() {
        APDUResponse r = getData(0x00, 0xE0);
        if (!r.isSuccess()) {
            throw new GPException("GET DATA (Key Information) failed", r.sw());
        }
        return KeyInfoEntry.parseAll(r.data());
    }

    /**
     * Retrieves the Card Production Life Cycle (CPLC) data.
     *
     * <p>Sends GET DATA P1P2=9F7F. The CPLC contains the complete production
     * history of the card: IC fabrication, module packaging, card embedding,
     * pre-personalization, and personalization details.</p>
     *
     * @return parsed CPLCData
     * @throws GPException if the command fails
     */
    public CPLCData getCPLC() {
        APDUResponse r = getData(0x9F, 0x7F);
        if (!r.isSuccess()) {
            throw new GPException("GET DATA (CPLC) failed", r.sw());
        }
        return CPLCData.parse(r.data());
    }

    /**
     * Retrieves the sequence counter value.
     *
     * <p>Sends GET DATA P1P2=00C1.</p>
     *
     * @return the sequence counter as an integer
     * @throws GPException if the command fails
     */
    public int getSequenceCounter() {
        APDUResponse r = getData(0x00, 0xC1);
        if (!r.isSuccess()) {
            throw new GPException("GET DATA (Sequence Counter) failed", r.sw());
        }
        byte[] data = r.data();
        int counter = 0;
        for (byte b : data) {
            counter = (counter << 8) | (b & 0xFF);
        }
        return counter;
    }

    // ── GP Commands ──

    /**
     * Queries card content using GET STATUS.
     *
     * <p>Sends GET STATUS (INS=F2) with TLV response format and parses
     * the result into a list of {@link AppletInfo} entries.</p>
     *
     * <p><strong>Scope values:</strong></p>
     * <ul>
     *   <li>{@code 0x80} — Issuer Security Domain only</li>
     *   <li>{@code 0x40} — Applications and Security Domains</li>
     *   <li>{@code 0x20} — Executable Load Files</li>
     *   <li>{@code 0x10} — Executable Load Files and their Executable Modules</li>
     * </ul>
     *
     * @param scope the P2 scope byte
     * @return list of parsed applet/package entries
     * @throws GPException if the command fails
     */
    public List<AppletInfo> getStatus(int scope) {
        requireOpen();
        // GET STATUS: CLA=80, INS=F2, P1=scope, P2=02 (TLV format)
        // Data = 4F 00 (tag 4F, length 0 = all AIDs)
        APDUResponse response = send(0x80, GP.INS_GET_STATUS, scope, 0x02,
                Hex.decode("4F00"));

        if (!response.isSuccess() && response.sw() != 0x6310) {
            // 6310 = more data available (GET STATUS chaining)
            throw new GPException("GET STATUS failed", response.sw());
        }

        return parseGetStatusResponse(response.data());
    }

    /**
     * Queries all applications and security domains.
     *
     * <p>Equivalent to {@code getStatus(0x40)}.</p>
     *
     * @return list of application entries
     */
    public List<AppletInfo> getStatus() {
        return getStatus(0x40);
    }

    /**
     * Sends a DELETE command for the given AID.
     *
     * @param aidHex the AID to delete, as a hex string
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse deleteAid(String aidHex) {
        return deleteAid(Hex.decode(aidHex));
    }

    /**
     * Sends a DELETE command for the given AID.
     *
     * @param aid the AID bytes to delete
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse deleteAid(byte[] aid) {
        requireOpen();
        byte[] data = InstallParams.forDelete(aid);
        APDUResponse response = send(0x80, GP.INS_DELETE, 0x00, 0x00, data);
        if (!response.isSuccess()) {
            throw new GPException("DELETE failed for AID " + Hex.encode(aid), response.sw());
        }
        return response;
    }

    /**
     * Sends INSTALL [for load] command.
     *
     * @param packageAidHex the package AID as hex
     * @param sdAidHex      the security domain AID as hex (null or empty = ISD)
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse installForLoad(String packageAidHex, String sdAidHex) {
        requireOpen();
        byte[] pkgAid = Hex.decode(packageAidHex);
        byte[] sdAid = (sdAidHex != null && !sdAidHex.isEmpty()) ? Hex.decode(sdAidHex) : new byte[0];
        byte[] data = InstallParams.forLoad(pkgAid, sdAid);
        APDUResponse response = send(0x80, GP.INS_INSTALL, 0x02, 0x00, data);
        if (!response.isSuccess()) {
            throw new GPException("INSTALL [for load] failed", response.sw());
        }
        return response;
    }

    /**
     * Sends INSTALL [for install and make selectable] command.
     *
     * @param packageAidHex  the package AID as hex
     * @param moduleAidHex   the module (applet class) AID as hex
     * @param instanceAidHex the instance AID as hex
     * @param privileges     the privilege byte (e.g., 0x00 for no special privileges)
     * @param installParams  the install parameters (may be null)
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse installForInstall(String packageAidHex, String moduleAidHex,
                                           String instanceAidHex, int privileges,
                                           byte[] installParams) {
        requireOpen();
        byte[] pkgAid = Hex.decode(packageAidHex);
        byte[] modAid = Hex.decode(moduleAidHex);
        byte[] instAid = Hex.decode(instanceAidHex);
        byte[] data = InstallParams.forInstall(pkgAid, modAid, instAid, privileges, installParams);
        APDUResponse response = send(0x80, GP.INS_INSTALL, 0x0C, 0x00, data);
        if (!response.isSuccess()) {
            throw new GPException("INSTALL [for install] failed", response.sw());
        }
        return response;
    }

    /**
     * Sends LOAD commands to transfer a CAP file to the card.
     *
     * <p>The CAP file's load data (C4-wrapped component bytes) is split into
     * blocks and sent via sequential LOAD commands (INS=0xE8). This must be
     * preceded by {@link #installForLoad(String, String)}.</p>
     *
     * @param cap the parsed CAP file
     * @return the final APDU response
     * @throws GPException if any LOAD block fails
     * @see #installForLoad(String, String)
     */
    public APDUResponse load(CAPFile cap) {
        return load(cap.loadFileData());
    }

    /**
     * Sends LOAD commands with raw load data (C4-wrapped or plain bytes).
     *
     * <p>The data is split into blocks of 247 bytes (255 - 8 for C-MAC) and
     * transmitted sequentially. P1 indicates first/last block, P2 is the block number.</p>
     *
     * @param loadData the load file data
     * @return the final APDU response
     * @throws GPException if any LOAD block fails
     */
    public APDUResponse load(byte[] loadData) {
        requireOpen();
        int maxBlockSize = 247; // 255 - 8 for C-MAC
        int offset = 0;
        int blockNumber = 0;

        while (offset < loadData.length) {
            int remaining = loadData.length - offset;
            int blockSize = Math.min(remaining, maxBlockSize);
            boolean lastBlock = (offset + blockSize >= loadData.length);

            byte[] block = new byte[blockSize];
            System.arraycopy(loadData, offset, block, 0, blockSize);

            int p1 = lastBlock ? 0x80 : 0x00;
            APDUResponse response = send(0x80, GP.INS_LOAD, p1, blockNumber, block);
            if (!response.isSuccess()) {
                throw new GPException("LOAD failed at block " + blockNumber, response.sw());
            }

            offset += blockSize;
            blockNumber++;
        }

        if (loadData.length == 0) {
            return send(0x80, GP.INS_LOAD, 0x80, 0x00, new byte[0]);
        }

        return new APDUResponse(new byte[0], 0x9000);
    }

    /**
     * Performs the complete applet loading flow: INSTALL [for load] + LOAD + INSTALL [for install].
     *
     * <p>This convenience method executes the three GP commands needed to load and
     * install an applet from a CAP file in a single call.</p>
     *
     * @param cap            the parsed CAP file
     * @param instanceAidHex the instance AID as hex (if null, uses package AID)
     * @param privileges     the privilege byte (e.g., 0x00)
     * @param installParams  the install parameters (may be null)
     * @return the final INSTALL [for install] response
     * @throws GPException if any command fails
     */
    public APDUResponse loadAndInstall(CAPFile cap, String instanceAidHex,
                                        int privileges, byte[] installParams) {
        String pkgAid = cap.packageAidHex();
        String instAid = (instanceAidHex != null) ? instanceAidHex : pkgAid;

        installForLoad(pkgAid, null);
        load(cap);
        return installForInstall(pkgAid, pkgAid, instAid, privileges, installParams);
    }

    /**
     * Sends STORE DATA with automatic chaining for large payloads.
     *
     * <p>If the data exceeds 247 bytes (255 - 8 for MAC), it is split
     * into multiple STORE DATA commands with P1 indicating first/last block.</p>
     *
     * @param data the data to store
     * @return the final APDU response
     * @throws GPException if any block fails
     */
    public APDUResponse storeData(byte[] data) {
        requireOpen();
        int maxBlockSize = 247; // 255 - 8 for C-MAC
        int offset = 0;
        int blockNumber = 0;

        while (offset < data.length) {
            int remaining = data.length - offset;
            int blockSize = Math.min(remaining, maxBlockSize);
            boolean lastBlock = (offset + blockSize >= data.length);

            byte[] block = new byte[blockSize];
            System.arraycopy(data, offset, block, 0, blockSize);

            int p1 = lastBlock ? 0x80 : 0x00;
            APDUResponse response = send(0x80, GP.INS_STORE_DATA, p1, blockNumber, block);
            if (!response.isSuccess()) {
                throw new GPException("STORE DATA failed at block " + blockNumber, response.sw());
            }

            offset += blockSize;
            blockNumber++;
        }

        // Handle empty data as single block
        if (data.length == 0) {
            return send(0x80, GP.INS_STORE_DATA, 0x80, 0x00, new byte[0]);
        }

        return new APDUResponse(new byte[0], 0x9000);
    }

    /**
     * Replaces all three keys (ENC, MAC, DEK) on the card using PUT KEY.
     *
     * <p>Uses the current session's key version as the existing key version (P1).
     * The new key version is placed in the command data. Each key is encrypted
     * with the session DEK (SCP02) or static DEK (SCP03) and accompanied by
     * a 3-byte Key Check Value.</p>
     *
     * @param newKeys       the new key set to install
     * @param newKeyVersion the version number for the new keys (1-127)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     */
    public APDUResponse putKeys(SCPKeys newKeys, int newKeyVersion) {
        return putKeys(newKeys, newKeyVersion, cardInfo.keyVersion());
    }

    /**
     * Replaces all three keys with explicit control over the existing key version.
     *
     * <p>Use {@code existingKeyVersion = 0} to add a new key set without
     * replacing an existing one.</p>
     *
     * @param newKeys             the new key set to install
     * @param newKeyVersion       the version number for the new keys (1-127)
     * @param existingKeyVersion  the version of keys being replaced (0 = add new)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     */
    public APDUResponse putKeys(SCPKeys newKeys, int newKeyVersion, int existingKeyVersion) {
        requireOpen();

        boolean isScp03 = channel instanceof SCP03;
        byte[] dekKey = channel.dek();

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(newKeyVersion);

        byte[][] keyComponents = {newKeys.enc(), newKeys.mac(), newKeys.dek()};
        for (byte[] keyComponent : keyComponents) {
            if (isScp03) {
                writeAesKeyData(data, keyComponent, dekKey);
            } else {
                writeDes3KeyData(data, keyComponent, dekKey);
            }
        }

        // P1 = existing key version (0 = new set), P2 = 0x81 (index 1 | multiple keys)
        APDUResponse response = send(0x80, GP.INS_PUT_KEY,
                existingKeyVersion, 0x81, data.toByteArray());
        if (!response.isSuccess()) {
            throw new GPException("PUT KEY failed", response.sw());
        }
        return response;
    }

    /**
     * Replaces a single key on the card using PUT KEY.
     *
     * @param keyIndex           the key index (1=ENC, 2=MAC, 3=DEK)
     * @param newKey             the new key bytes
     * @param newKeyVersion      the version number for the new key
     * @param existingKeyVersion the version of the key being replaced (0 = add new)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     */
    public APDUResponse putKey(int keyIndex, byte[] newKey, int newKeyVersion,
                                int existingKeyVersion) {
        requireOpen();

        boolean isScp03 = channel instanceof SCP03;
        byte[] dekKey = channel.dek();

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(newKeyVersion);

        if (isScp03) {
            writeAesKeyData(data, newKey, dekKey);
        } else {
            writeDes3KeyData(data, newKey, dekKey);
        }

        // P2 = key index (no 0x80 flag for single key)
        APDUResponse response = send(0x80, GP.INS_PUT_KEY,
                existingKeyVersion, keyIndex, data.toByteArray());
        if (!response.isSuccess()) {
            throw new GPException("PUT KEY failed for key index " + keyIndex, response.sw());
        }
        return response;
    }

    // ── Lifecycle management ──

    /**
     * Changes the lifecycle state of an application, security domain, or load file.
     *
     * <p>Sends SET STATUS (INS=0xF0) with the specified scope, AID, and new state.</p>
     *
     * <p><strong>Scope values:</strong></p>
     * <ul>
     *   <li>{@link Lifecycle#SCOPE_ISD} (0x80) — Issuer Security Domain</li>
     *   <li>{@link Lifecycle#SCOPE_APPS} (0x40) — Applications and Security Domains</li>
     *   <li>{@link Lifecycle#SCOPE_LOAD_FILES} (0x20) — Executable Load Files</li>
     * </ul>
     *
     * @param scope    the entity type (see {@link Lifecycle} scope constants)
     * @param aid      the AID of the target entity
     * @param newState the new lifecycle state value
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse setStatus(int scope, byte[] aid, int newState) {
        requireOpen();
        byte[] data = (scope == Lifecycle.SCOPE_ISD) ? null : InstallParams.forDelete(aid);
        APDUResponse response = send(0x80, GP.INS_SET_STATUS, scope, newState, data);
        if (!response.isSuccess()) {
            throw new GPException("SET STATUS failed", response.sw());
        }
        return response;
    }

    /**
     * Changes the lifecycle state of an application, security domain, or load file.
     *
     * @param scope    the entity type
     * @param aidHex   the AID as a hex string
     * @param newState the new lifecycle state value
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse setStatus(int scope, String aidHex, int newState) {
        return setStatus(scope, Hex.decode(aidHex), newState);
    }

    /**
     * Locks an application (sets the LOCKED bit in its lifecycle state).
     *
     * @param aidHex the application AID as a hex string
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse lockApp(String aidHex) {
        return setStatus(Lifecycle.SCOPE_APPS, aidHex, Lifecycle.APP_LOCKED);
    }

    /**
     * Locks an application.
     *
     * @param aid the application AID bytes
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse lockApp(byte[] aid) {
        return setStatus(Lifecycle.SCOPE_APPS, aid, Lifecycle.APP_LOCKED);
    }

    /**
     * Unlocks an application (reverts to SELECTABLE state).
     *
     * @param aidHex the application AID as a hex string
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse unlockApp(String aidHex) {
        return setStatus(Lifecycle.SCOPE_APPS, aidHex, Lifecycle.APP_SELECTABLE);
    }

    /**
     * Unlocks an application.
     *
     * @param aid the application AID bytes
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse unlockApp(byte[] aid) {
        return setStatus(Lifecycle.SCOPE_APPS, aid, Lifecycle.APP_SELECTABLE);
    }

    /**
     * Terminates an application (irreversible).
     *
     * @param aidHex the application AID as a hex string
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse terminateApp(String aidHex) {
        return setStatus(Lifecycle.SCOPE_APPS, aidHex, Lifecycle.APP_TERMINATED);
    }

    /**
     * Terminates an application (irreversible).
     *
     * @param aid the application AID bytes
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse terminateApp(byte[] aid) {
        return setStatus(Lifecycle.SCOPE_APPS, aid, Lifecycle.APP_TERMINATED);
    }

    /**
     * Locks the card (Issuer Security Domain enters CARD_LOCKED state).
     *
     * <p>All applications become inaccessible. The card can still be
     * unlocked by the issuer.</p>
     *
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse lockCard() {
        requireOpen();
        APDUResponse response = send(0x80, GP.INS_SET_STATUS,
                Lifecycle.SCOPE_ISD, Lifecycle.CARD_LOCKED);
        if (!response.isSuccess()) {
            throw new GPException("SET STATUS (lock card) failed", response.sw());
        }
        return response;
    }

    // ── Security Domain management ──

    /**
     * Queries all Security Domains on the card.
     *
     * <p>Retrieves applications and Security Domains (scope=0x40) and filters
     * to return only entries with the Security Domain privilege bit set.</p>
     *
     * @return list of Security Domain entries
     * @throws GPException if the GET STATUS command fails
     * @see Privileges#isSecurityDomain(int)
     */
    public List<AppletInfo> getDomains() {
        return getStatus(0x40).stream()
                .filter(AppletInfo::isSecurityDomain)
                .toList();
    }

    /**
     * Queries Executable Load Files on the card (scope=0x20).
     *
     * @return list of load file entries
     * @throws GPException if the GET STATUS command fails
     */
    public List<AppletInfo> getLoadFiles() {
        return getStatus(0x20);
    }

    /**
     * Sends INSTALL [for extradition] to move an applet to another Security Domain.
     *
     * @param appletAidHex       the applet AID as hex
     * @param targetDomainAidHex the target Security Domain AID as hex
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse extradite(String appletAidHex, String targetDomainAidHex) {
        requireOpen();
        byte[] appletAid = Hex.decode(appletAidHex);
        byte[] sdAid = Hex.decode(targetDomainAidHex);
        byte[] data = InstallParams.forExtradition(sdAid, appletAid);
        APDUResponse response = send(0x80, GP.INS_INSTALL,
                GP.INSTALL_FOR_EXTRADITION, 0x00, data);
        if (!response.isSuccess()) {
            throw new GPException("INSTALL [for extradition] failed", response.sw());
        }
        return response;
    }

    /**
     * Sends INSTALL [for personalization] to personalize a Security Domain.
     *
     * @param domainAidHex the Security Domain AID as hex
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse personalize(String domainAidHex) {
        requireOpen();
        byte[] domainAid = Hex.decode(domainAidHex);
        byte[] data = InstallParams.forPersonalization(domainAid);
        APDUResponse response = send(0x80, GP.INS_INSTALL,
                GP.INSTALL_FOR_PERSONALIZATION, 0x00, data);
        if (!response.isSuccess()) {
            throw new GPException("INSTALL [for personalization] failed", response.sw());
        }
        return response;
    }

    /**
     * Sends INSTALL [for registry update] to change applet privileges (GP 2.2+).
     *
     * @param appletAidHex  the applet AID as hex
     * @param newPrivileges the new privilege byte
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse registryUpdate(String appletAidHex, int newPrivileges) {
        requireOpen();
        byte[] appletAid = Hex.decode(appletAidHex);
        byte[] data = InstallParams.forRegistryUpdate(appletAid, newPrivileges);
        APDUResponse response = send(0x80, GP.INS_INSTALL,
                GP.INSTALL_FOR_REGISTRY_UPDATE, 0x00, data);
        if (!response.isSuccess()) {
            throw new GPException("INSTALL [for registry update] failed", response.sw());
        }
        return response;
    }

    /**
     * Terminates the card (irreversible).
     *
     * <p>The card becomes permanently unusable. This cannot be undone.</p>
     *
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse terminateCard() {
        requireOpen();
        APDUResponse response = send(0x80, GP.INS_SET_STATUS,
                Lifecycle.SCOPE_ISD, Lifecycle.CARD_TERMINATED);
        if (!response.isSuccess()) {
            throw new GPException("SET STATUS (terminate card) failed", response.sw());
        }
        return response;
    }

    @Override
    public void close() {
        opened = false;
        channel = null;
    }

    // ── Internal ──

    /**
     * Writes a 3DES key component for PUT KEY (SCP02 format).
     * Format: keyType(1) | keyLen(1) | encryptedKey(16) | kcvLen(1) | kcv(3)
     */
    private void writeDes3KeyData(ByteArrayOutputStream out, byte[] key, byte[] dekKey) {
        byte[] encrypted = CryptoUtil.des3EcbEncrypt(dekKey, key);
        byte[] kcv = KeyInfo.kcvDes3(key);

        out.write(KeyInfo.KeyType.DES3.code()); // 0x80
        out.write(encrypted.length);
        out.writeBytes(encrypted);
        out.write(kcv.length); // 0x03
        out.writeBytes(kcv);
    }

    /**
     * Writes an AES key component for PUT KEY (SCP03 format).
     * Format: keyType(1) | dataLen(1) | actualKeyLen(1) | encryptedKey(N) | kcvLen(1) | kcv(3)
     */
    private void writeAesKeyData(ByteArrayOutputStream out, byte[] key, byte[] dekKey) {
        // Pad key to multiple of 16 if needed, then encrypt with AES-CBC
        byte[] toEncrypt;
        if (key.length % 16 != 0) {
            toEncrypt = CryptoUtil.pad80(key, 16);
        } else {
            toEncrypt = key.clone();
        }
        byte[] encrypted = CryptoUtil.aesCbcEncrypt(dekKey, toEncrypt);
        byte[] kcv = KeyInfo.kcvAes(key);

        out.write(KeyInfo.KeyType.AES.code()); // 0x88
        out.write(1 + encrypted.length); // data length = actualKeyLen(1) + encrypted
        out.write(key.length); // actual key length
        out.writeBytes(encrypted);
        out.write(kcv.length); // 0x03
        out.writeBytes(kcv);
    }

    private APDUResponse transmitWrapped(byte[] plainApdu) {
        byte[] wrapped = channel.wrap(plainApdu);
        APDUResponse response = APDUSequence.on(session).transmit(wrapped);
        return channel.unwrap(response);
    }

    private void requireOpen() {
        if (!opened) {
            throw new GPException("GPSession is not open — call open() first");
        }
    }

    /**
     * Parses GET STATUS TLV response into AppletInfo entries.
     *
     * <p>Each entry is a constructed TLV with tag E3 containing:</p>
     * <ul>
     *   <li>Tag 4F: AID</li>
     *   <li>Tag 9F70: Life cycle state</li>
     *   <li>Tag C5: Privileges</li>
     * </ul>
     */
    private List<AppletInfo> parseGetStatusResponse(byte[] data) {
        List<AppletInfo> result = new ArrayList<>();
        if (data == null || data.length == 0) {
            return result;
        }

        try {
            TLVList list = TLVParser.parse(data);
            for (TLV entry : list) {
                if (entry.tag() == 0xE3) {
                    TLVList children = entry.children();
                    byte[] aid = children.find(0x4F)
                            .map(TLV::value)
                            .orElse(new byte[0]);

                    int lifecycle = children.find(0x9F70)
                            .map(t -> t.value().length > 0 ? t.value()[0] & 0xFF : 0)
                            .orElse(0);

                    int privileges = children.find(0xC5)
                            .map(t -> t.value().length > 0 ? t.value()[0] & 0xFF : 0)
                            .orElse(0);

                    result.add(new AppletInfo(aid, lifecycle, privileges));
                }
            }
        } catch (Exception e) {
            // If TLV parsing fails, return empty list rather than crashing
            // (some cards return non-standard formats)
        }

        return result;
    }
}
