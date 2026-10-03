package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUBuilder;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.KeyInfo;
import name.velikodniy.jcexpress.scp.SCP02;
import name.velikodniy.jcexpress.scp.SCP03;
import name.velikodniy.jcexpress.scp.SCPException;
import name.velikodniy.jcexpress.scp.SCPKeys;
import name.velikodniy.jcexpress.scp.SecureChannel;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * High-level GlobalPlatform session helper that automates the SCP authentication flow.
 *
 * <p>Wraps a {@link SmartCardSession} and provides GP-specific operations (GlobalPlatform Card
 * Specification v2.3.1 chapter 11). All commands sent through an opened GPSession are wrapped with the
 * secure channel (C-MAC, optionally C-ENC) and their responses unwrapped (R-MAC, R-ENC).</p>
 *
 * <h2>Authentication flow (performed by {@link #open()}):</h2>
 * <ol>
 *   <li>Optionally SELECTs the Security Domain ({@link #securityDomain(String)})</li>
 *   <li>Sends INITIALIZE UPDATE with a fresh random host challenge and Le '00' (Table E-7, Amd D 7.1.1)</li>
 *   <li>Detects SCP02 or SCP03 from the response, derives the session keys and verifies the card
 *       cryptogram; on a mismatch EXTERNAL AUTHENTICATE is never sent</li>
 *   <li>Sends EXTERNAL AUTHENTICATE (MACed, never encrypted); a failure is reported, never retried</li>
 * </ol>
 *
 * <p><b>Responses:</b> a protected command is transmitted once, also when the card answers '61XX' (completed with
 * plain GET RESPONSE commands, GPCS v2.3.1 11.1.5.2) or '6CXX' (the command is protected again with the corrected
 * Le, never re-sent with the C-MAC the card has verified, E.4.4). After an error status word the session stays
 * open, also with R-MAC (E.4.5, Amendment D 6.2.5); the command helpers report it as {@link GPException}.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * GPSession gp = GPSession.on(session)
 *     .keys(SCPKeys.defaultKeys())       // explicit: there is no fallback to test keys
 *     .open();
 *
 * List&lt;AppletInfo&gt; apps = gp.getStatus();
 * gp.close();
 * </pre>
 *
 * <h2>Custom configuration:</h2>
 * <pre>
 * GPSession gp = GPSession.on(session)
 *     .securityDomain("A000000151000000")
 *     .keys(SCPKeys.of(enc, mac, dek))
 *     .securityLevel(GP.SECURITY_C_MAC_C_ENC)
 *     .keyVersion(0x30)
 *     .open();
 * </pre>
 *
 * @see SecureChannel
 * @see SCP02
 * @see SCP03
 */
public final class GPSession implements AutoCloseable {

    /** Ne = 256, i.e. Le '00': "all GlobalPlatform APDU commands that expect response data" (11.1.5). */
    private static final int LE_ALL = 256;
    /** LOAD and STORE DATA block numbers are coded from '00' to 'FF' (11.6.2.2, 11.11.2.2). */
    private static final int MAX_BLOCKS = 256;
    /** GET STATUS P2: response data structure per Tables 11-36/11-37 (b2 = 1). */
    private static final int GET_STATUS_TLV = 0x02;
    /** GET STATUS P2 b1: get next occurrence(s) (Table 11-34). */
    private static final int GET_STATUS_NEXT = 0x01;
    private static final int SW_MORE_DATA = 0x6310;
    private static final int SW_NOT_FOUND = 0x6A88;
    private static final int MAX_GET_STATUS_COMMANDS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmartCardSession session;
    private SCPKeys keys;
    private int securityLevel = GP.SECURITY_C_MAC;
    private int keyVersion = 0;
    private int forcedScpVersion = 0; // 0 = auto-detect
    private int scp02Option = SCP02.DEFAULT_OPTION;
    private boolean s16;
    private byte[] testHostChallenge;
    private byte[] securityDomainAid;
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
     * @param session the underlying session (embedded, container or PC/SC)
     * @return a new GPSession ready for configuration and {@link #open()}
     */
    public static GPSession on(SmartCardSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        return new GPSession(session);
    }

    /**
     * Sets the static key set for authentication. Required: {@link #open()} fails without keys (there is
     * no silent fallback to the well-known test keys; use {@link SCPKeys#defaultKeys()} explicitly).
     *
     * @param keys the static key set
     * @return this session for chaining
     */
    public GPSession keys(SCPKeys keys) {
        this.keys = keys;
        return this;
    }

    /**
     * Sets the security level requested in EXTERNAL AUTHENTICATE (P1).
     *
     * <p>Default: {@link GP#SECURITY_C_MAC}. The level is validated against the protocol (GPCS v2.3.1
     * Table E-11, Amendment D Table 7-6) and the card's implementation option during {@link #open()},
     * before EXTERNAL AUTHENTICATE is sent.</p>
     *
     * @param level the security level (e.g., {@link GP#SECURITY_C_MAC_C_ENC})
     * @return this session for chaining
     */
    public GPSession securityLevel(int level) {
        if (level < 0 || level > 0xFF) {
            throw new IllegalArgumentException("Security level must be a byte, got: " + level);
        }
        this.securityLevel = level;
        return this;
    }

    /**
     * Sets the Key Version Number sent in INITIALIZE UPDATE (P1, GPCS v2.3.1 E.5.1.3).
     *
     * <p>Default: 0 (the first available key set chosen by the Security Domain).</p>
     *
     * @param version the key version (0-255)
     * @return this session for chaining
     */
    public GPSession keyVersion(int version) {
        if (version < 0 || version > 0xFF) {
            throw new IllegalArgumentException("Key version must be 0x00-0xFF, got: " + version);
        }
        this.keyVersion = version;
        return this;
    }

    /**
     * Forces a specific SCP version instead of auto-detecting from the response.
     *
     * <p>Default: 0 (auto-detect from INITIALIZE UPDATE response byte 11). Set to 2 or 3 to override
     * detection for non-compliant cards.</p>
     *
     * @param version the SCP version (0 = auto, 2 or 3)
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
     * Sets the SCP02 implementation option ("i" parameter, GPCS v2.3.1 Table E-1) of the card.
     *
     * <p>SCP02 cards do not report "i" in INITIALIZE UPDATE; it is published in the Card Recognition Data
     * (GET DATA '66', OID 1.2.840.114283.4.2.i). Default: {@link SCP02#DEFAULT_OPTION} ('15': ICV
     * encryption, no R-MAC). Use '55' or '15' for most cards, '05'/'45' for cards without ICV encryption,
     * and an option with R-MAC support (e.g. '75') for security levels '11'/'13'. Ignored for SCP03, whose
     * cards report "i" themselves.</p>
     *
     * @param option the SCP02 "i" parameter
     * @return this session for chaining
     */
    public GPSession scp02Option(int option) {
        if (option < 0 || option > 0x7F) {
            throw new IllegalArgumentException("SCP02 option must be 0x00-0x7F, got: " + option);
        }
        this.scp02Option = option;
        return this;
    }

    /**
     * Selects SCP03 S16 mode (Amendment D v1.2): 16-byte host challenge, cryptograms and MACs.
     *
     * <p>Default: false (S8). The host challenge length must be known before INITIALIZE UPDATE, so S16
     * cards need this option.</p>
     *
     * @param enabled true for S16 mode
     * @return this session for chaining
     */
    public GPSession scp03S16(boolean enabled) {
        this.s16 = enabled;
        return this;
    }

    /**
     * Formerly selected the SCP03 pseudo-random challenge layout.
     *
     * @param enabled ignored
     * @return this session for chaining
     * @deprecated the INITIALIZE UPDATE response tells whether the card uses pseudo-random challenges
     *             (Amendment D Table 5-1 "i" parameter, Table 7-3); this setting has no effect
     */
    @Deprecated
    public GPSession pseudoRandomChallenge(boolean enabled) {
        return this;
    }

    /**
     * Sets a fixed host challenge for the next {@link #open()} only (testing with known-answer vectors).
     *
     * <p>The challenge is used exactly once; every other open() generates a fresh random challenge, which
     * is what makes the card cryptogram a proof of freshness (GPCS v2.3.1 E.5.1.5).</p>
     *
     * @param challenge the 8-byte host challenge (16 bytes for SCP03 S16)
     * @return this session for chaining
     */
    public GPSession hostChallenge(byte[] challenge) {
        if (challenge == null || (challenge.length != 8 && challenge.length != 16)) {
            throw new IllegalArgumentException("Host challenge must be 8 bytes (16 bytes for SCP03 S16)");
        }
        this.testHostChallenge = challenge.clone();
        return this;
    }

    /**
     * Makes {@link #open()} SELECT the given Security Domain before INITIALIZE UPDATE.
     *
     * <p>Without it, INITIALIZE UPDATE goes to the currently selected application, which must then be the
     * Security Domain (e.g. the default-selected ISD).</p>
     *
     * @param aidHex the Security Domain AID, e.g. "A000000151000000"
     * @return this session for chaining
     */
    public GPSession securityDomain(String aidHex) {
        this.securityDomainAid = Hex.decode(aidHex);
        return this;
    }

    /**
     * Sets a key diversification function to derive card-specific keys.
     *
     * <p>When set, the master keys are diversified using the card's diversification data (first 10 bytes
     * of the INITIALIZE UPDATE response) before creating the secure channel session.</p>
     *
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
     * <p>After successful return, all commands sent via {@link #send} are wrapped with the negotiated
     * secure channel protocol. Authentication is never retried automatically.</p>
     *
     * @return this GPSession (now authenticated)
     * @throws GPException  if no keys are configured or the card rejects a command
     * @throws SCPException if the card cryptogram is wrong (EXTERNAL AUTHENTICATE is then not sent) or
     *                      the security level is not supported
     */
    public GPSession open() {
        if (opened) {
            throw new GPException("GPSession is already open");
        }
        if (keys == null) {
            throw new GPException("No keys configured: call keys(...) before open()"
                    + " (SCPKeys.defaultKeys() provides the well-known test keys of development cards)");
        }
        byte[] hostChallenge = nextHostChallenge();
        if (securityDomainAid != null) {
            selectSecurityDomain();
        }
        byte[] response = initializeUpdate(hostChallenge);
        cardInfo = forcedScpVersion != 0 ? CardInfo.parse(response, forcedScpVersion) : CardInfo.parse(response);
        SCPKeys sessionKeys = diversifier != null ? diversifier.apply(keys, cardInfo.diversificationData()) : keys;
        SecureChannel candidate = createChannel(sessionKeys, hostChallenge, response);
        SecureChannelTransport.externalAuthenticate(session, candidate);
        channel = candidate;
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
     * Returns the card information parsed from the last INITIALIZE UPDATE response (also available after
     * a failed authentication, for diagnostics).
     *
     * @return the card info, or null before {@link #open()} and after {@link #close()}
     */
    public CardInfo cardInfo() {
        return cardInfo;
    }

    /**
     * Returns the underlying secure channel (SCP02 or SCP03).
     *
     * @return the secure channel, or null if not open
     */
    public SecureChannel secureChannel() {
        return channel;
    }

    // ── Command dispatch ──

    /**
     * Sends a command without Le through the secure channel.
     *
     * <p>The command is wrapped with C-MAC (and optionally C-ENC), sent through the session with
     * automatic GET RESPONSE chaining (SW=61XX), and its response unwrapped; see
     * {@link #send(int, int, int, int, byte[], int)}.</p>
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
        return send(cla, ins, p1, p2, data, -1);
    }

    /**
     * Sends a command through the secure channel.
     *
     * <p>The command is protected and transmitted once; '61XX' is completed with plain GET RESPONSE commands and
     * the reassembled response unwrapped (GPCS v2.3.1 11.1.5.2). After '6CXX' the command is protected again with
     * Le = SW2 and sent once more (ISO/IEC 7816-4:2005 5.1.3): the card has verified the first C-MAC (E.4.4).</p>
     *
     * @param cla  the CLA byte (will be modified by secure channel wrapping)
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @param le   the expected response length Ne: 256 encodes Le '00' (as GlobalPlatform commands that
     *             expect data require, GPCS v2.3.1 11.1.5), 1-255 a short Le, -1 no Le
     * @return the unwrapped APDU response
     * @throws GPException if the session is not open
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        requireOpen();
        return transmitWrapped(APDUCodec.encode(cla, ins, p1, p2, data, le));
    }

    /**
     * Sends a command without data or Le.
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
     * Wraps and transmits a raw APDU through the secure channel and unwraps the response, like
     * {@link #send(int, int, int, int, byte[], int)}.
     *
     * @param rawApdu the raw short APDU (will be wrapped)
     * @return the unwrapped response bytes (data followed by SW1 SW2)
     */
    public byte[] transmit(byte[] rawApdu) {
        requireOpen();
        APDUResponse response = transmitWrapped(rawApdu);
        byte[] data = response.data();
        byte[] raw = Arrays.copyOf(data, data.length + 2);
        raw[data.length] = (byte) response.sw1();
        raw[data.length + 1] = (byte) response.sw2();
        return raw;
    }

    // ── GET DATA ──

    /**
     * Sends a GET DATA command ({@code 80 CA P1 P2 00}, GPCS v2.3.1 Table 11-27).
     *
     * @param p1 the P1 parameter
     * @param p2 the P2 parameter
     * @return the APDU response
     */
    public APDUResponse getData(int p1, int p2) {
        return command(GP.INS_GET_DATA, p1, p2, null, LE_ALL);
    }

    /**
     * Retrieves the Issuer Identification Number (GET DATA '0042').
     *
     * @return the IIN data object as returned by the card
     * @throws GPException if the command fails
     */
    public byte[] getIIN() {
        return requireData(getData(0x00, 0x42), "GET DATA (IIN)");
    }

    /**
     * Retrieves the Card Image Number (GET DATA '0045').
     *
     * @return the CIN data object as returned by the card
     * @throws GPException if the command fails
     */
    public byte[] getCIN() {
        return requireData(getData(0x00, 0x45), "GET DATA (CIN)");
    }

    /**
     * Retrieves the Card Data (GET DATA '0066') with the Card Recognition Data.
     *
     * @return parsed CardData
     * @throws GPException if the command fails
     */
    public CardData getCardData() {
        return CardData.parse(requireData(getData(0x00, 0x66), "GET DATA (Card Data)"));
    }

    /**
     * Retrieves the Key Information Template (GET DATA '00E0', GPCS v2.3.1 11.3.3.1.1).
     *
     * @return list of key entries
     * @throws GPException if the command fails
     */
    public List<KeyInfoEntry> getKeyInformation() {
        return KeyInfoEntry.parseAll(requireData(getData(0x00, 0xE0), "GET DATA (Key Information)"));
    }

    /**
     * Retrieves the Card Production Life Cycle data (GET DATA '9F7F').
     *
     * @return parsed CPLCData
     * @throws GPException if the command fails
     */
    public CPLCData getCPLC() {
        return CPLCData.parse(requireData(getData(0x9F, 0x7F), "GET DATA (CPLC)"));
    }

    /**
     * Retrieves the sequence counter of the default key version (GET DATA '00C1').
     *
     * @return the sequence counter as an integer
     * @throws GPException if the command fails
     */
    public int getSequenceCounter() {
        byte[] data = requireData(getData(0x00, 0xC1), "GET DATA (Sequence Counter)");
        // GP class byte: the response is the TLV coded data object 'C1' (GPCS v2.3.1 11.3.3.1)
        boolean tlv = data.length > 2 && (data[0] & 0xFF) == 0xC1 && (data[1] & 0xFF) == data.length - 2;
        int counter = 0;
        for (int i = tlv ? 2 : 0; i < data.length; i++) {
            counter = (counter << 8) | (data[i] & 0xFF);
        }
        return counter;
    }

    // ── GP Commands ──

    /**
     * Queries card content using GET STATUS (GPCS v2.3.1 11.4) with the search criterion {@code '4F' '00'}.
     *
     * <p>The response format of Tables 11-36/11-37 is requested (P2.b2 = 1). While the card answers
     * '6310' (more data available), GET STATUS [get next occurrence(s)] is sent and the entries are
     * accumulated. '6A88' (referenced data not found) for the first command yields an empty list.</p>
     *
     * <p><strong>Scope values (P1, Table 11-33):</strong></p>
     * <ul>
     *   <li>{@code 0x80} — Issuer Security Domain only</li>
     *   <li>{@code 0x40} — Applications and Security Domains</li>
     *   <li>{@code 0x20} — Executable Load Files</li>
     *   <li>{@code 0x10} — Executable Load Files and their Executable Modules</li>
     * </ul>
     *
     * @param scope the P1 scope byte
     * @return all entries returned by the card
     * @throws GPException if a command fails or a response is malformed
     */
    public List<AppletInfo> getStatus(int scope) {
        List<AppletInfo> entries = new ArrayList<>();
        int p2 = GET_STATUS_TLV;
        for (int i = 0; i < MAX_GET_STATUS_COMMANDS; i++) {
            APDUResponse response = command(GP.INS_GET_STATUS, scope, p2, Hex.decode("4F00"), LE_ALL);
            if (response.sw() == SW_NOT_FOUND && i == 0) {
                return entries;
            }
            if (!response.isSuccess() && response.sw() != SW_MORE_DATA) {
                throw new GPException("GET STATUS failed", response.sw());
            }
            entries.addAll(GetStatusParser.parse(response.data()));
            if (response.sw() != SW_MORE_DATA) {
                return entries;
            }
            p2 = GET_STATUS_TLV | GET_STATUS_NEXT;
        }
        throw new GPException("GET STATUS still reports more data after " + MAX_GET_STATUS_COMMANDS + " commands");
    }

    /**
     * Queries all applications and security domains, i.e. {@code getStatus(0x40)}.
     *
     * @return list of application entries
     */
    public List<AppletInfo> getStatus() {
        return getStatus(Lifecycle.SCOPE_APPS);
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
     * Sends a DELETE [card content] command for the given AID (object only, P2 '00').
     *
     * @param aid the AID bytes to delete
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse deleteAid(byte[] aid) {
        return deleteAid(aid, false);
    }

    /**
     * Sends a DELETE [card content] command ({@code 80 E4 00 P2 Lc '4F' len AID 00}, GPCS v2.3.1 11.2).
     *
     * @param aid            the AID of the Executable Load File or Application
     * @param deleteRelated  true to delete the object and its related objects (P2 '80', e.g. a load file
     *                       and all its applications), false for the object only (P2 '00')
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse deleteAid(byte[] aid, boolean deleteRelated) {
        APDUResponse response = command(GP.INS_DELETE, 0x00, deleteRelated ? 0x80 : 0x00,
                InstallParams.forDelete(aid), LE_ALL);
        return requireSuccess(response, "DELETE failed for AID " + Hex.encode(aid));
    }

    /**
     * Sends INSTALL [for load] without Load File Data Block hash and load parameters.
     *
     * @param packageAidHex the Load File AID as hex
     * @param sdAidHex      the security domain AID as hex (null or empty = the selected Security Domain)
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse installForLoad(String packageAidHex, String sdAidHex) {
        byte[] sdAid = (sdAidHex != null && !sdAidHex.isEmpty()) ? Hex.decode(sdAidHex) : new byte[0];
        return installForLoad(Hex.decode(packageAidHex), sdAid, new byte[0], new byte[0]);
    }

    /**
     * Sends INSTALL [for load] ({@code 80 E6 02 00 Lc data 00}, GPCS v2.3.1 Table 11-42).
     *
     * @param loadFileAid           the Load File AID
     * @param sdAid                 the Security Domain AID (empty = the selected Security Domain)
     * @param loadFileDataBlockHash the Load File Data Block hash, see {@link CAPFile#loadFileDataBlockHash}
     *                              (empty if not required by the card)
     * @param loadParameters        the Load Parameters field (Table 11-48), may be empty
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse installForLoad(byte[] loadFileAid, byte[] sdAid, byte[] loadFileDataBlockHash,
                                       byte[] loadParameters) {
        byte[] data = InstallParams.forLoad(loadFileAid, sdAid, loadFileDataBlockHash, loadParameters);
        return requireSuccess(command(GP.INS_INSTALL, GP.INSTALL_FOR_LOAD, 0x00, data, LE_ALL),
                "INSTALL [for load] failed");
    }

    /**
     * Sends INSTALL [for install and make selectable] ({@code 80 E6 0C 00 Lc data 00}, Table 11-43).
     *
     * @param packageAidHex  the Executable Load File AID as hex
     * @param moduleAidHex   the Executable Module (applet class) AID as hex, see {@link CAPFile#appletAids()}
     * @param instanceAidHex the Application (instance) AID as hex
     * @param privileges     the privileges: 0x00-0xFF for one byte, larger values for three bytes
     * @param installParams  the application specific parameters (tag 'C9'), may be null
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse installForInstall(String packageAidHex, String moduleAidHex,
                                          String instanceAidHex, int privileges,
                                          byte[] installParams) {
        byte[] data = InstallParams.forInstall(Hex.decode(packageAidHex), Hex.decode(moduleAidHex),
                Hex.decode(instanceAidHex), InstallParams.privileges(privileges), installParams);
        return requireSuccess(command(GP.INS_INSTALL, GP.INSTALL_FOR_INSTALL_AND_SELECTABLE, 0x00, data, LE_ALL),
                "INSTALL [for install] failed");
    }

    /**
     * Sends LOAD commands to transfer a CAP file to the card.
     *
     * @param cap the parsed CAP file
     * @return the response to the last LOAD command
     * @throws GPException if any LOAD block fails
     * @see #installForLoad(String, String)
     */
    public APDUResponse load(CAPFile cap) {
        return load(cap.loadFileData());
    }

    /**
     * Sends LOAD commands ({@code 80 E8 P1 P2 Lc block 00}, GPCS v2.3.1 11.6) with raw load data.
     *
     * <p>The data is split into blocks of {@link SecureChannel#maxCommandDataLength()} bytes (247 with
     * C-MAC, 239 with C-ENC); P1 '80' marks the last block and P2 is the block number ('00'-'FF').
     * More than 256 blocks are rejected before anything is sent.</p>
     *
     * @param loadData the load file (e.g. {@link CAPFile#loadFileData()})
     * @return the response to the last LOAD command
     * @throws GPException if the data needs more than 256 blocks or any LOAD block fails
     */
    public APDUResponse load(byte[] loadData) {
        requireOpen();
        List<byte[]> blocks = blocks(loadData, channel.maxCommandDataLength(), "LOAD");
        APDUResponse response = null;
        for (int i = 0; i < blocks.size(); i++) {
            int p1 = i == blocks.size() - 1 ? 0x80 : 0x00;
            response = requireSuccess(command(GP.INS_LOAD, p1, i, blocks.get(i), LE_ALL),
                    "LOAD failed at block " + i);
        }
        return response;
    }

    /**
     * Performs the complete applet loading flow for a CAP file defining exactly one applet: INSTALL
     * [for load] + LOAD + INSTALL [for install and make selectable] with that applet as Executable Module.
     *
     * @param cap            the parsed CAP file
     * @param instanceAidHex the instance AID as hex (null = the applet AID)
     * @param privileges     the privileges (e.g., 0x00)
     * @param installParams  the install parameters (may be null)
     * @return the final INSTALL [for install] response
     * @throws GPException if the CAP file does not define exactly one applet or any command fails
     */
    public APDUResponse loadAndInstall(CAPFile cap, String instanceAidHex,
                                       int privileges, byte[] installParams) {
        return loadAndInstall(cap, Hex.encode(cap.singleAppletAid()), instanceAidHex, privileges, installParams);
    }

    /**
     * Performs the complete applet loading flow: INSTALL [for load] + LOAD + INSTALL [for install and
     * make selectable]. The Executable Module AID must be one of the applet AIDs of the CAP file's Applet
     * component (GPCS v2.3.1 11.5.2.3.2, JCVM 3.1 section 6.6); this and the number of LOAD blocks are
     * checked before anything is sent.
     *
     * @param cap            the parsed CAP file
     * @param moduleAidHex   the applet (Executable Module) AID as hex
     * @param instanceAidHex the instance AID as hex (null = the module AID)
     * @param privileges     the privileges (e.g., 0x00)
     * @param installParams  the install parameters (may be null)
     * @return the final INSTALL [for install] response
     * @throws GPException if the module is not an applet of the CAP file or any command fails
     */
    public APDUResponse loadAndInstall(CAPFile cap, String moduleAidHex, String instanceAidHex,
                                       int privileges, byte[] installParams) {
        requireOpen();
        byte[] module = Hex.decode(moduleAidHex);
        if (cap.appletAids().stream().noneMatch(aid -> Arrays.equals(aid, module))) {
            throw new GPException("CAP file " + cap.packageAidHex() + " defines no applet " + moduleAidHex
                    + "; its applets are " + cap.appletAids().stream().map(Hex::encode).toList());
        }
        byte[] loadFile = cap.loadFileData();
        blocks(loadFile, channel.maxCommandDataLength(), "LOAD");
        installForLoad(cap.packageAidHex(), null);
        load(loadFile);
        return installForInstall(cap.packageAidHex(), moduleAidHex,
                instanceAidHex != null ? instanceAidHex : moduleAidHex, privileges, installParams);
    }

    /**
     * Sends STORE DATA ({@code 80 E2 P1 P2 Lc data}, GPCS v2.3.1 11.11) with automatic segmentation.
     *
     * <p>The data is split into blocks of {@link SecureChannel#maxCommandDataLength()} bytes; P1 '80'
     * marks the last block (no structure or encryption information) and P2 is the block number.</p>
     *
     * @param data the data to store
     * @return the response to the last STORE DATA command
     * @throws GPException if the data needs more than 256 blocks or any block fails
     */
    public APDUResponse storeData(byte[] data) {
        requireOpen();
        List<byte[]> blocks = blocks(data, channel.maxCommandDataLength(), "STORE DATA");
        APDUResponse response = null;
        for (int i = 0; i < blocks.size(); i++) {
            int p1 = i == blocks.size() - 1 ? 0x80 : 0x00;
            response = requireSuccess(command(GP.INS_STORE_DATA, p1, i, blocks.get(i), -1),
                    "STORE DATA failed at block " + i);
        }
        return response;
    }

    /**
     * Replaces (or, for factory key sets with a Key Version Number above '7F', adds) the three keys of the
     * current key set using PUT KEY.
     *
     * @param newKeys       the new key set to install (its key type decides the key data format)
     * @param newKeyVersion the version number for the new keys (1-127)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     * @see #putKeys(SCPKeys, int, int)
     */
    public APDUResponse putKeys(SCPKeys newKeys, int newKeyVersion) {
        requireOpen();
        int current = cardInfo.keyVersion();
        return putKeys(newKeys, newKeyVersion, current > 0x7F ? 0 : current);
    }

    /**
     * Replaces or adds a key set with PUT KEY ({@code 80 D8 P1 81 Lc data 00}, GPCS v2.3.1 11.8).
     *
     * <p>The key type is taken from {@link SCPKeys#keyType()}; key sets without an explicit type use the
     * type of the current secure channel protocol (SCP02: DES, SCP03: AES). The key values are encrypted
     * with the channel's DEK (SCP02 session DEK, SCP03 static Key-DEK) and sent with their key check
     * values. Use {@code existingKeyVersion = 0} to add a new key set.</p>
     *
     * @param newKeys             the new key set to install
     * @param newKeyVersion       the version number for the new keys (1-127)
     * @param existingKeyVersion  the version of keys being replaced (0 = add new, at most 127)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     */
    public APDUResponse putKeys(SCPKeys newKeys, int newKeyVersion, int existingKeyVersion) {
        requireOpen();
        requireKeyVersions(newKeyVersion, existingKeyVersion);
        KeyInfo.KeyType type = newKeys.keyType().orElseGet(this::channelKeyType);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(newKeyVersion);
        data.writeBytes(PutKeyData.keyDataField(newKeys.enc(), type, channel));
        data.writeBytes(PutKeyData.keyDataField(newKeys.mac(), type, channel));
        data.writeBytes(PutKeyData.keyDataField(newKeys.dek(), type, channel));
        // P2 = '81': Key Identifier 1, multiple keys (Table 11-66)
        return requireSuccess(command(GP.INS_PUT_KEY, existingKeyVersion, 0x81, data.toByteArray(), LE_ALL),
                "PUT KEY failed");
    }

    /**
     * Replaces a single key using PUT KEY, with the key type of the current secure channel protocol.
     *
     * @param keyIndex           the key identifier (1=ENC, 2=MAC, 3=DEK)
     * @param newKey             the new key bytes
     * @param newKeyVersion      the version number for the new key
     * @param existingKeyVersion the version of the key being replaced (0 = add new)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     */
    public APDUResponse putKey(int keyIndex, byte[] newKey, int newKeyVersion, int existingKeyVersion) {
        requireOpen();
        return putKey(keyIndex, newKey, channelKeyType(), newKeyVersion, existingKeyVersion);
    }

    /**
     * Replaces or adds a single key using PUT KEY ({@code 80 D8 P1 P2 Lc data 00}, GPCS v2.3.1 11.8).
     *
     * @param keyIndex           the key identifier (P2, 1-127, single key)
     * @param newKey             the new key bytes
     * @param keyType            the key type of the new key
     * @param newKeyVersion      the version number for the new key (1-127)
     * @param existingKeyVersion the version of the key being replaced (0 = add new)
     * @return the APDU response
     * @throws GPException if the PUT KEY command fails
     */
    public APDUResponse putKey(int keyIndex, byte[] newKey, KeyInfo.KeyType keyType, int newKeyVersion,
                               int existingKeyVersion) {
        requireOpen();
        requireKeyVersions(newKeyVersion, existingKeyVersion);
        if (keyIndex < 0 || keyIndex > 0x7F) {
            throw new GPException("Key identifier must be 0x00-0x7F (GPCS v2.3.1 Table 11-66), got: " + keyIndex);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(newKeyVersion);
        data.writeBytes(PutKeyData.keyDataField(newKey, keyType, channel));
        return requireSuccess(command(GP.INS_PUT_KEY, existingKeyVersion, keyIndex, data.toByteArray(), LE_ALL),
                "PUT KEY failed for key index " + keyIndex);
    }

    // ── Lifecycle management ──

    /**
     * Changes a Life Cycle State with SET STATUS ({@code 80 F0 P1 P2 Lc AID}, GPCS v2.3.1 11.10).
     *
     * <p>The data field is the raw AID of the target (11.10.2.3); for the Issuer Security Domain scope
     * ('80') no data is sent. A Security Domain changing the state of another application can only lock
     * it (P2 b8 = 1) or unlock it (P2 b8 = 0) (11.10.2.2). <strong>Scope '80' sets the card Life Cycle
     * State</strong>: read the warnings of {@link #lockCard()} and {@link #terminateCard()} first.</p>
     *
     * @param scope    the status type (P1): {@link Lifecycle#SCOPE_ISD}, {@link Lifecycle#SCOPE_APPS} or
     *                 {@link Lifecycle#SCOPE_SD_AND_APPS}
     * @param aid      the AID of the target entity
     * @param newState the state control (P2)
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse setStatus(int scope, byte[] aid, int newState) {
        byte[] data = (scope == Lifecycle.SCOPE_ISD) ? null : aid;
        return requireSuccess(command(GP.INS_SET_STATUS, scope, newState, data, -1), "SET STATUS failed");
    }

    /**
     * Changes a Life Cycle State with SET STATUS, see {@link #setStatus(int, byte[], int)} (scope '80': the card).
     *
     * @param scope    the status type (P1)
     * @param aidHex   the AID as a hex string
     * @param newState the state control (P2)
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse setStatus(int scope, String aidHex, int newState) {
        return setStatus(scope, Hex.decode(aidHex), newState);
    }

    /**
     * Locks an application (SET STATUS P1 '40', P2 '80': transition to LOCKED, GPCS v2.3.1 11.10.2.2).
     *
     * @param aidHex the application AID as a hex string
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse lockApp(String aidHex) {
        return lockApp(Hex.decode(aidHex));
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
     * Unlocks an application (SET STATUS P1 '40', P2 '00': transition from LOCKED back to the previous
     * state, GPCS v2.3.1 11.10.2.2).
     *
     * @param aidHex the application AID as a hex string
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse unlockApp(String aidHex) {
        return unlockApp(Hex.decode(aidHex));
    }

    /**
     * Unlocks an application.
     *
     * @param aid the application AID bytes
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse unlockApp(byte[] aid) {
        return setStatus(Lifecycle.SCOPE_APPS, aid, 0x00);
    }

    /**
     * Not supported: a Security Domain cannot terminate another application with SET STATUS.
     *
     * @param aidHex the application AID as a hex string
     * @return never returns normally
     * @throws GPException always
     * @deprecated GPCS v2.3.1 11.10.2.2: "For a Security Domain setting the Life Cycle State of another
     *             Application ..., the only possible transitions are to the LOCKED state and subsequently
     *             back"; use {@link #lockApp(String)} or {@link #deleteAid(String)}
     */
    @Deprecated
    public APDUResponse terminateApp(String aidHex) {
        throw new GPException("SET STATUS cannot terminate another application (GPCS v2.3.1 11.10.2.2):"
                + " use lockApp() or deleteAid()");
    }

    /**
     * Not supported: a Security Domain cannot terminate another application with SET STATUS.
     *
     * @param aid the application AID bytes
     * @return never returns normally
     * @throws GPException always
     * @deprecated see {@link #terminateApp(String)}
     */
    @Deprecated
    public APDUResponse terminateApp(byte[] aid) {
        return terminateApp(Hex.encode(aid));
    }

    /**
     * Locks the card: SET STATUS P1 '80', P2 '7F', card Life Cycle State SECURED to CARD_LOCKED (GPCS v2.3.1
     * 5.1.1.4, 9.6.3, 11.10, Table 11-6).
     *
     * <p><strong>Warning:</strong> in CARD_LOCKED only the application with the Final Application privilege can be
     * selected, Security Domains accept only GET DATA, GET STATUS and SET STATUS, and no card content, key or data
     * may change (5.1.1.4, Table 11-1). Locking and unlocking ({@link #unlockCard()}) need a Security Domain with
     * the Card Lock privilege (normally the Issuer Security Domain) and a secure channel authenticated with its keys
     * (9.6.3, 11.10.2.2); once the current session ends, that domain can be selected again only if it holds the
     * Final Application privilege (by default the ISD, 6.6.2). Where it does not, or the card restricts unlocking,
     * a lock is irreversible in practice: see {@link Lifecycle#CARD_LOCKED}.</p>
     *
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse lockCard() {
        return setStatus(Lifecycle.SCOPE_ISD, new byte[0], Lifecycle.CARD_LOCKED);
    }

    /**
     * Unlocks the card: SET STATUS P1 '80', P2 '0F', CARD_LOCKED back to SECURED (GPCS v2.3.1 5.1.1.4, 9.6.3).
     *
     * <p>Needs the Security Domain with the Card Lock privilege, a secure channel authenticated with its keys (9.6.3)
     * and that domain still selectable in CARD_LOCKED: see the warning of {@link #lockCard()}.</p>
     *
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse unlockCard() {
        return setStatus(Lifecycle.SCOPE_ISD, new byte[0], Lifecycle.CARD_SECURED);
    }

    /**
     * Terminates the card: SET STATUS P1 '80', P2 'FF', card Life Cycle State TERMINATED (GPCS v2.3.1 5.1.1.5, 9.6.4).
     *
     * <p><strong>Warning: irreversible</strong> ("The state transition from any other state to TERMINATED is
     * irreversible", 5.1.1.5). Card content management and life cycle changes are disabled for good; only the
     * application with the Final Application privilege can be selected, and a Security Domain with it processes
     * GET DATA only (Table 11-1). Needs a Security Domain with the Card Terminate privilege (11.10.2.2) and its
     * keys; see {@link Lifecycle#CARD_TERMINATED}.</p>
     *
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse terminateCard() {
        return setStatus(Lifecycle.SCOPE_ISD, new byte[0], Lifecycle.CARD_TERMINATED);
    }

    // ── Security Domain management ──

    /**
     * Queries all Security Domains (GET STATUS scope '40', filtered by the Security Domain privilege).
     *
     * @return list of Security Domain entries
     * @throws GPException if the GET STATUS command fails
     */
    public List<AppletInfo> getDomains() {
        return getStatus(Lifecycle.SCOPE_APPS).stream()
                .filter(AppletInfo::isSecurityDomain)
                .toList();
    }

    /**
     * Queries Executable Load Files on the card (scope '20').
     *
     * @return list of load file entries
     * @throws GPException if the GET STATUS command fails
     */
    public List<AppletInfo> getLoadFiles() {
        return getStatus(Lifecycle.SCOPE_LOAD_FILES);
    }

    /**
     * Sends INSTALL [for extradition] (P1 '10', Table 11-45) to move an application to another Security
     * Domain.
     *
     * @param appletAidHex       the application AID as hex
     * @param targetDomainAidHex the target Security Domain AID as hex
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse extradite(String appletAidHex, String targetDomainAidHex) {
        byte[] data = InstallParams.forExtradition(Hex.decode(targetDomainAidHex), Hex.decode(appletAidHex));
        return requireSuccess(command(GP.INS_INSTALL, GP.INSTALL_FOR_EXTRADITION, 0x00, data, LE_ALL),
                "INSTALL [for extradition] failed");
    }

    /**
     * Sends INSTALL [for personalization] (P1 '20', Table 11-47).
     *
     * @param domainAidHex the application AID as hex
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse personalize(String domainAidHex) {
        byte[] data = InstallParams.forPersonalization(Hex.decode(domainAidHex));
        return requireSuccess(command(GP.INS_INSTALL, GP.INSTALL_FOR_PERSONALIZATION, 0x00, data, LE_ALL),
                "INSTALL [for personalization] failed");
    }

    /**
     * Sends INSTALL [for registry update] (P1 '40', Table 11-46) to change application privileges.
     *
     * @param appletAidHex  the application AID as hex
     * @param newPrivileges the new privileges: 0x00-0xFF for one byte, larger values for three bytes
     * @return the APDU response
     * @throws GPException if the command fails
     */
    public APDUResponse registryUpdate(String appletAidHex, int newPrivileges) {
        byte[] data = InstallParams.forRegistryUpdate(Hex.decode(appletAidHex),
                InstallParams.privileges(newPrivileges));
        return requireSuccess(command(GP.INS_INSTALL, GP.INSTALL_FOR_REGISTRY_UPDATE, 0x00, data, LE_ALL),
                "INSTALL [for registry update] failed");
    }

    /**
     * Ends the session: destroys the session keys and forgets the card information. A new
     * {@link #open()} performs a new authentication with a fresh host challenge.
     */
    @Override
    public void close() {
        if (channel != null) {
            channel.destroy();
        }
        channel = null;
        cardInfo = null;
        opened = false;
    }

    // ── Internal ──

    private byte[] nextHostChallenge() {
        if (testHostChallenge != null) {
            byte[] challenge = testHostChallenge;
            testHostChallenge = null;
            return challenge;
        }
        byte[] challenge = new byte[s16 ? 16 : 8];
        RANDOM.nextBytes(challenge);
        return challenge;
    }

    private void selectSecurityDomain() {
        byte[] select = APDUBuilder.select(securityDomainAid).le(LE_ALL).build();
        APDUResponse response = APDUSequence.on(session).transmit(select);
        if (!response.isSuccess()) {
            throw new GPException("SELECT of Security Domain " + Hex.encode(securityDomainAid) + " failed",
                    response.sw());
        }
    }

    /** INITIALIZE UPDATE: {@code 80 50 KVN 00 Lc host-challenge 00} (GPCS Table E-7, Amd D Table 7-2). */
    private byte[] initializeUpdate(byte[] hostChallenge) {
        byte[] apdu = APDUCodec.encode(GP.CLA_GP, GP.INS_INITIALIZE_UPDATE, keyVersion, 0x00, hostChallenge,
                LE_ALL);
        APDUResponse response = APDUSequence.on(session).transmit(apdu);
        if (!response.isSuccess()) {
            throw new GPException("INITIALIZE UPDATE failed", response.sw());
        }
        return response.data();
    }

    /** Creates the channel and verifies the card cryptogram; nothing is sent to the card here. */
    private SecureChannel createChannel(SCPKeys sessionKeys, byte[] hostChallenge, byte[] response) {
        if (cardInfo.scpVersion() == 2) {
            SCP02 scp02 = SCP02.from(sessionKeys, response, securityLevel, scp02Option);
            try {
                scp02.verifyCardCryptogram(hostChallenge);
            } catch (SCPException e) {
                scp02.destroy();
                throw e;
            }
            return scp02;
        }
        return SCP03.from(sessionKeys, hostChallenge, response, securityLevel);
    }

    private APDUResponse command(int ins, int p1, int p2, byte[] data, int le) {
        requireOpen();
        return transmitWrapped(APDUCodec.encode(GP.CLA_GP, ins, p1, p2, data, le));
    }

    /** Transmits a protected command exactly once (fail closed), see {@link SecureChannelTransport}. */
    private APDUResponse transmitWrapped(byte[] plainApdu) {
        return SecureChannelTransport.transmit(session, channel, plainApdu, this::close);
    }

    private KeyInfo.KeyType channelKeyType() {
        return channel instanceof SCP03 ? KeyInfo.KeyType.AES : KeyInfo.KeyType.DES3;
    }

    private void requireOpen() {
        if (!opened) {
            throw new GPException("GPSession is not open — call open() first");
        }
    }

    private static APDUResponse requireSuccess(APDUResponse response, String message) {
        if (!response.isSuccess()) {
            throw new GPException(message, response.sw());
        }
        return response;
    }

    private static byte[] requireData(APDUResponse response, String command) {
        return requireSuccess(response, command + " failed").data();
    }

    private static void requireKeyVersions(int newKeyVersion, int existingKeyVersion) {
        if (newKeyVersion < 1 || newKeyVersion > 0x7F || existingKeyVersion < 0 || existingKeyVersion > 0x7F) {
            throw new GPException("Key Version Numbers are coded '01'-'7F' (existing: '00' = add new key set),"
                    + " GPCS v2.3.1 11.8.2.1; got new=" + newKeyVersion + ", existing=" + existingKeyVersion);
        }
    }

    /** Splits data into blocks for a sequence of numbered commands (block numbers '00'-'FF'). */
    private static List<byte[]> blocks(byte[] data, int blockSize, String command) {
        int count = Math.max(1, (data.length + blockSize - 1) / blockSize);
        if (count > MAX_BLOCKS) {
            throw new GPException(command + " of " + data.length + " bytes needs " + count + " blocks of "
                    + blockSize + " bytes, more than the 256 block numbers '00'-'FF' (GPCS v2.3.1 11.6.2.2)");
        }
        List<byte[]> blocks = new ArrayList<>(count);
        for (int offset = 0; offset < data.length || blocks.isEmpty(); offset += blockSize) {
            blocks.add(Arrays.copyOfRange(data, offset, Math.min(data.length, offset + blockSize)));
        }
        return blocks;
    }
}
