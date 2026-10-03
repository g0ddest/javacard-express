package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * GlobalPlatform Secure Channel Protocol '02' (SCP02), host side, explicit initiation
 * (GlobalPlatform Card Specification v2.3.1 Appendix E).
 *
 * <p>Supported implementation options ("i", Table E-1): explicit initiation (b3 = 1), C-MAC on modified
 * APDU (b2 = 0), with or without ICV encryption (b5), with or without R-MAC support (b6), three Secure
 * Channel keys or one base key (b1). The mandatory options '15' and '55' (E.1.1) and, for R-MAC, '35'/'75'
 * are covered. The default is '15'.</p>
 *
 * <p>Supported security levels (Table E-11): '00' (no secure messaging after authentication), '01'
 * (C-MAC), '03' (C-MAC and C-DECRYPTION), '11' (C-MAC and R-MAC) and '13' (C-MAC, C-DECRYPTION and
 * R-MAC). Level '10' (R-MAC only) is not supported; '30', '31' and '33' are RFU.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * SCP02 scp = SCP02.from(keys, initUpdateResponse, GP.SECURITY_C_MAC, 0x55);
 * scp.verifyCardCryptogram(hostChallenge);          // E.4.2.1; throws on mismatch
 * byte[] extAuth = scp.externalAuthenticate();      // send it, require '9000'
 * byte[] wrapped = scp.wrap(plainApdu);             // C-MAC (E.4.4), C-ENC (E.4.6)
 * APDUResponse plain = scp.unwrap(cardResponse);    // R-MAC (E.4.5)
 * </pre>
 *
 * <h2>Session keys (E.4.1): 3DES-CBC(static key, constant || sequence counter || '00' x 12)</h2>
 * <ul>
 *   <li>C-MAC: constant '0101' with Key-MAC; R-MAC: '0102' with Key-MAC</li>
 *   <li>S-ENC: '0182' with Key-ENC; DEK: '0181' with Key-DEK</li>
 * </ul>
 *
 * @see SecureChannel
 * @see GP
 */
public final class SCP02 implements SecureChannel {

    /** Default implementation option: explicit initiation, C-MAC on modified APDU, ICV encryption, 3 keys. */
    public static final int DEFAULT_OPTION = 0x15;

    private static final int OPTION_THREE_KEYS = 0x01;
    private static final int OPTION_UNMODIFIED_APDU = 0x02;
    private static final int OPTION_EXPLICIT = 0x04;
    private static final int OPTION_ICV_ENCRYPTION = 0x10;
    private static final int OPTION_RMAC = 0x20;
    private static final int MAC_LENGTH = 8;

    private final byte[] sessionMacKey;
    private final byte[] sessionEncKey;
    private final byte[] sessionDekKey;
    private final byte[] sessionRmacKey;
    private final byte[] sequenceCounter;
    private final byte[] cardChallenge;
    private final byte[] cardCryptogram;
    private final int securityLevel;
    private final int option;

    /** Host challenge whose card cryptogram was verified; null until verification succeeded. */
    private byte[] verifiedHostChallenge;
    /** Last C-MAC (ICV of the next one, E.3.2/E.4.4); null until EXTERNAL AUTHENTICATE was produced. */
    private byte[] macChaining;
    /** ICV of the next R-MAC: C-MAC of EXTERNAL AUTHENTICATE, then the card's last R-MAC (E.3.2, E.4.5). */
    private byte[] rmacChaining = new byte[MAC_LENGTH];
    /** Stripped clear command of the last wrapped command, input of the R-MAC (E.4.5). */
    private byte[] lastStrippedCommand;
    private boolean destroyed;

    private SCP02(SCPKeys keys, InitializeUpdateResponse response, int securityLevel, int option) {
        this.sequenceCounter = response.sequenceCounter();
        this.cardChallenge = response.cardChallenge();
        this.cardCryptogram = response.cardCryptogram();
        this.securityLevel = securityLevel;
        this.option = option;
        this.sessionMacKey = sessionKey(keys.mac(), GP.SCP02_DERIVE_C_MAC);
        this.sessionEncKey = sessionKey(keys.enc(), GP.SCP02_DERIVE_ENC);
        this.sessionDekKey = sessionKey(keys.dek(), GP.SCP02_DERIVE_DEK);
        this.sessionRmacKey = sessionKey(keys.mac(), GP.SCP02_DERIVE_R_MAC);
    }

    /**
     * Creates an SCP02 session from the INITIALIZE UPDATE response.
     *
     * <p>The response data (28 bytes, GPCS v2.3.1 Table E-8) is structured as:</p>
     * <pre>
     * Offset  Length  Description
     * 0       10      Key diversification data
     * 10      2       Key information (Key Version Number, '02')
     * 12      2       Sequence counter
     * 14      6       Card challenge
     * 20      8       Card cryptogram
     * </pre>
     *
     * @param keys                 the static key set (16-byte DES keys, Table E-3)
     * @param responseData         the 28-byte INITIALIZE UPDATE response data
     * @param securityLevel        the security level for EXTERNAL AUTHENTICATE (Table E-11)
     * @param implementationOption the SCP02 "i" parameter supported by the card (Table E-1)
     * @return a new SCP02 session
     * @throws SCPException if the response, keys, option or level are not supported
     */
    public static SCP02 from(SCPKeys keys, byte[] responseData, int securityLevel, int implementationOption) {
        validateOption(implementationOption, keys);
        validateLevel(securityLevel, implementationOption);
        validateKeys(keys);
        InitializeUpdateResponse response = InitializeUpdateResponse.parse(responseData, 2);
        return new SCP02(keys, response, securityLevel, implementationOption);
    }

    /**
     * Creates an SCP02 session with the default implementation option {@link #DEFAULT_OPTION}.
     *
     * @param keys          the static key set
     * @param responseData  the 28-byte INITIALIZE UPDATE response data
     * @param securityLevel the desired security level (e.g., {@link GP#SECURITY_C_MAC})
     * @return a new SCP02 session
     * @throws SCPException if the response is malformed or the level is not supported
     */
    public static SCP02 from(SCPKeys keys, byte[] responseData, int securityLevel) {
        return from(keys, responseData, securityLevel, DEFAULT_OPTION);
    }

    /**
     * Creates an SCP02 session with C-MAC security level and the default implementation option.
     *
     * @param keys         the static key set
     * @param responseData the 28-byte INITIALIZE UPDATE response data
     * @return a new SCP02 session with C-MAC security
     * @see #from(SCPKeys, byte[], int, int)
     */
    public static SCP02 from(SCPKeys keys, byte[] responseData) {
        return from(keys, responseData, GP.SECURITY_C_MAC);
    }

    /**
     * Verifies the card cryptogram (GPCS v2.3.1 E.4.2.1): full 3DES MAC with S-ENC and a zero ICV over
     * {@code host challenge (8) || sequence counter (2) || card challenge (6)}, padded with
     * {@code '80 00 00 00 00 00 00 00'}.
     *
     * <p>On success the host challenge is remembered for {@link #externalAuthenticate()}.</p>
     *
     * @param hostChallenge the 8-byte host challenge sent in INITIALIZE UPDATE
     * @throws SCPException if the card cryptogram is invalid (do NOT send EXTERNAL AUTHENTICATE then)
     */
    public void verifyCardCryptogram(byte[] hostChallenge) {
        requireUsable();
        requireHostChallenge(hostChallenge);
        byte[] expected = authenticationCryptogram(hostChallenge, sequenceCounter, cardChallenge);
        if (!MessageDigest.isEqual(expected, cardCryptogram)) {
            throw new SCPException("Card cryptogram verification failed (GPCS v2.3.1 E.4.2.1):"
                    + " wrong keys, wrong implementation option or not the expected card");
        }
        verifiedHostChallenge = hostChallenge.clone();
    }

    /**
     * Computes the host cryptogram for the EXTERNAL AUTHENTICATE command (GPCS v2.3.1 E.4.2.2): full 3DES
     * MAC with S-ENC and a zero ICV over {@code sequence counter (2) || card challenge (6) || host
     * challenge (8)}, padded with {@code '80 00 00 00 00 00 00 00'}.
     *
     * @param hostChallenge the 8-byte host challenge sent in INITIALIZE UPDATE
     * @return the 8-byte host cryptogram
     */
    public byte[] computeHostCryptogram(byte[] hostChallenge) {
        requireUsable();
        requireHostChallenge(hostChallenge);
        return authenticationCryptogram(sequenceCounter, cardChallenge, hostChallenge);
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP02: {@code 84 82 P1 00 10 || host cryptogram || C-MAC}, the C-MAC computed over the modified
     * APDU with a zero ICV (E.3.2) and S-MAC; P1 is the security level of this channel (Table E-11).</p>
     */
    @Override
    public byte[] externalAuthenticate() {
        requireUsable();
        if (verifiedHostChallenge == null) {
            throw new SCPException("Card cryptogram not verified: call verifyCardCryptogram() before"
                    + " EXTERNAL AUTHENTICATE");
        }
        byte[] hostCryptogram = computeHostCryptogram(verifiedHostChallenge);
        byte[] apdu = {(byte) 0x84, (byte) GP.INS_EXTERNAL_AUTHENTICATE, (byte) securityLevel, 0x00,
                (byte) hostCryptogram.length};
        return wrapExternalAuthenticate(ShortApdu.parse(concat(apdu, hostCryptogram)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP02 wrapping (E.4.4, E.4.6): the C-MAC (retail MAC with S-MAC) is computed over the
     * <em>clear</em> modified APDU ({@code CLA|'04' INS P1 P2 Lc+8 data}) with the previous C-MAC as ICV,
     * encrypted with single DES under the first half of S-MAC for implementation options with ICV
     * encryption (E.3.4); then, with C-DECRYPTION, the data field is padded and encrypted with 3DES-CBC
     * (S-ENC, zero ICV) and Lc is adjusted. Le is preserved.</p>
     */
    @Override
    public byte[] wrap(byte[] apdu) {
        requireUsable();
        ShortApdu command = ShortApdu.parse(apdu);
        if (macChaining == null) {
            return wrapLegacyExternalAuthenticate(command);
        }
        if ((securityLevel & GP.SECURITY_C_MAC) == 0) {
            return command.encode();
        }
        byte[] clear = command.data();
        requireDataLength(clear.length);
        byte[] mac = CryptoUtil.retailMac(sessionMacKey,
                CryptoUtil.pad80(command.macInput(clear, MAC_LENGTH), 8), nextIcv());
        macChaining = mac;
        lastStrippedCommand = strippedCommand(command);
        byte[] body = encryptsCommands() && clear.length > 0
                ? CryptoUtil.des3CbcEncrypt(sessionEncKey, CryptoUtil.pad80(clear, 8))
                : clear;
        return command.encode(command.wireClassByte(), body, mac);
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP02 R-MAC (E.4.5): retail MAC with the R-MAC session key over the stripped command (header
     * without secure messaging and logical channel, Lc, clear data), then {@code Li || response data}
     * for '9000'/'62xx'/'63xx' or {@code '00'} for an error, then the status word; the ICV is the C-MAC of
     * EXTERNAL AUTHENTICATE for the first response and the previous R-MAC afterwards (E.3.2). The card keeps the
     * R-MAC of every response as the next ICV "regardless of whether the APDU command completed successfully or
     * not" (E.4.5), but an error ('64'-'6F') comes without response data (ISO/IEC 7816-4:2005 5.1.3): for an error
     * answered with the status word alone, the R-MAC the card generated is computed and becomes the next ICV; an
     * error with an 8-byte R-MAC is verified; an error with other data (not covered by any R-MAC) is rejected.</p>
     */
    @Override
    public APDUResponse unwrap(APDUResponse response) {
        requireUsable();
        if ((securityLevel & GP.SECURITY_R_MAC) == 0) {
            return response;
        }
        byte[] data = response.data();
        int sw = response.sw();
        boolean error = !GP.isSuccessOrWarning(sw);
        if (error && data.length == 0) {
            if (lastStrippedCommand != null) {   // the EXTERNAL AUTHENTICATE pair has no R-MAC (E.4.5)
                rmacChaining = rmac(data, sw);
            }
            return response;
        }
        if (lastStrippedCommand == null || data.length < MAC_LENGTH || (error && data.length != MAC_LENGTH)) {
            throw new SCPException(String.format("Response %04X with %d data bytes does not carry an R-MAC as"
                    + " GPCS v2.3.1 E.4.5 defines it (data || 8-byte R-MAC; an error: the R-MAC alone or no data)",
                    sw, data.length));
        }
        byte[] payload = Arrays.copyOf(data, data.length - MAC_LENGTH);
        byte[] received = Arrays.copyOfRange(data, data.length - MAC_LENGTH, data.length);
        if (!MessageDigest.isEqual(rmac(payload, sw), received)) {
            throw new SCPException("Response MAC verification failed (GPCS v2.3.1 E.4.5)");
        }
        rmacChaining = received;
        return new APDUResponse(payload, sw);
    }

    @Override
    public int securityLevel() {
        return securityLevel;
    }

    /**
     * Returns the SCP02 implementation option ("i" parameter, Table E-1) used by this session.
     *
     * @return the implementation option
     */
    public int implementationOption() {
        return option;
    }

    @Override
    public int maxCommandDataLength() {
        if ((securityLevel & GP.SECURITY_C_MAC) == 0) {
            return ShortApdu.MAX_LC;
        }
        // C-ENC: pad80 to a multiple of 8 plus the 8-byte C-MAC must fit into 255 bytes (E.4.6)
        return encryptsCommands() ? 239 : ShortApdu.MAX_LC - MAC_LENGTH;
    }

    @Override
    public void destroy() {
        Arrays.fill(sessionMacKey, (byte) 0);
        Arrays.fill(sessionEncKey, (byte) 0);
        Arrays.fill(sessionDekKey, (byte) 0);
        Arrays.fill(sessionRmacKey, (byte) 0);
        destroyed = true;
    }

    /**
     * Returns the derived session MAC key (for testing/debugging).
     *
     * @return a copy of the session MAC key
     */
    public byte[] sessionMacKey() {
        return sessionMacKey.clone();
    }

    /**
     * Returns the derived session ENC key (for testing/debugging).
     *
     * @return a copy of the session ENC key
     */
    public byte[] sessionEncKey() {
        return sessionEncKey.clone();
    }

    /**
     * Returns the derived session DEK key (for testing/debugging).
     *
     * @return a copy of the session DEK key
     */
    public byte[] sessionDekKey() {
        return sessionDekKey.clone();
    }

    @Override
    public byte[] dek() {
        return sessionDekKey.clone();
    }

    /**
     * Returns the current C-MAC chaining value, i.e. the last C-MAC (for testing/debugging).
     *
     * @return a copy of the current chaining value (8 zero bytes before EXTERNAL AUTHENTICATE)
     */
    public byte[] macChaining() {
        return macChaining == null ? new byte[MAC_LENGTH] : macChaining.clone();
    }

    /**
     * Returns the derived session R-MAC key (for testing/debugging).
     *
     * @return a copy of the session R-MAC key
     */
    public byte[] sessionRmacKey() {
        return sessionRmacKey.clone();
    }

    /**
     * Returns the ICV of the next R-MAC (for testing/debugging): the C-MAC of EXTERNAL AUTHENTICATE, then the
     * card's last R-MAC, verified or, for an error answered without it, computed (E.4.5).
     *
     * @return a copy of the current R-MAC chaining value
     */
    public byte[] rmacChaining() {
        return rmacChaining.clone();
    }

    // ── Internal ──

    private byte[] wrapLegacyExternalAuthenticate(ShortApdu command) {
        if (command.ins() != GP.INS_EXTERNAL_AUTHENTICATE) {
            throw new SCPException("EXTERNAL AUTHENTICATE must be the first command of an SCP02 session"
                    + " (use externalAuthenticate())");
        }
        byte[] expected = externalAuthenticateData();
        if (command.p1() != securityLevel || !MessageDigest.isEqual(expected, command.data())) {
            throw new SCPException("EXTERNAL AUTHENTICATE must carry this session's host cryptogram and"
                    + " security level " + String.format("%02X", securityLevel));
        }
        return wrapExternalAuthenticate(command);
    }

    private byte[] externalAuthenticateData() {
        if (verifiedHostChallenge == null) {
            throw new SCPException("Card cryptogram not verified: call verifyCardCryptogram() before"
                    + " EXTERNAL AUTHENTICATE");
        }
        return computeHostCryptogram(verifiedHostChallenge);
    }

    /** EXTERNAL AUTHENTICATE: C-MAC with a zero ICV (E.3.2), never encrypted (E.5.2.3, Table E-10). */
    private byte[] wrapExternalAuthenticate(ShortApdu command) {
        if (macChaining != null) {
            throw new SCPException("EXTERNAL AUTHENTICATE was already produced for this session");
        }
        byte[] mac = CryptoUtil.retailMac(sessionMacKey,
                CryptoUtil.pad80(command.macInput(command.data(), MAC_LENGTH), 8), new byte[MAC_LENGTH]);
        macChaining = mac;
        rmacChaining = mac.clone();
        return command.encode(command.wireClassByte(), command.data(), mac);
    }

    /** ICV of the next C-MAC: the previous C-MAC, encrypted for options with ICV encryption (E.3.4). */
    private byte[] nextIcv() {
        if ((option & OPTION_ICV_ENCRYPTION) == 0) {
            return macChaining;
        }
        byte[] k1 = Arrays.copyOf(sessionMacKey, 8);
        byte[] singleDesKey = new byte[24];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(k1, 0, singleDesKey, 8 * i, 8);
        }
        return CryptoUtil.des3EcbEncrypt(singleDesKey, macChaining);
    }

    /** Stripped command for the R-MAC: no secure messaging, channel 0, Lc always present (E.4.5). */
    private static byte[] strippedCommand(ShortApdu command) {
        byte[] clear = command.data();
        byte[] stripped = new byte[5 + clear.length];
        stripped[0] = (byte) command.strippedClassByte();
        stripped[1] = (byte) command.ins();
        stripped[2] = (byte) command.p1();
        stripped[3] = (byte) command.p2();
        stripped[4] = (byte) clear.length;
        System.arraycopy(clear, 0, stripped, 5, clear.length);
        return stripped;
    }

    /** R-MAC of the last command's response, chained on the previous one (E.4.5; Li is the length modulo 256). */
    private byte[] rmac(byte[] payload, int sw) {
        boolean ok = GP.isSuccessOrWarning(sw);
        byte[] responsePart = ok ? concat(new byte[]{(byte) payload.length}, payload) : new byte[]{0x00};
        byte[] input = concat(concat(lastStrippedCommand, responsePart), new byte[]{(byte) (sw >> 8), (byte) sw});
        return CryptoUtil.retailMac(sessionRmacKey, CryptoUtil.pad80(input, 8), rmacChaining);
    }

    private boolean encryptsCommands() {
        return (securityLevel & GP.SECURITY_C_MAC_C_ENC) == GP.SECURITY_C_MAC_C_ENC;
    }

    private void requireDataLength(int length) {
        if (length > maxCommandDataLength()) {
            throw new SCPException("Command data of " + length + " bytes exceeds " + maxCommandDataLength()
                    + " bytes, the maximum for SCP02 security level " + String.format("%02X", securityLevel)
                    + " (GPCS v2.3.1 11.1.5, E.4.6)");
        }
    }

    private void requireUsable() {
        if (destroyed) {
            throw new SCPException("Secure channel session has been destroyed");
        }
    }

    private static void requireHostChallenge(byte[] hostChallenge) {
        if (hostChallenge == null || hostChallenge.length != 8) {
            throw new SCPException("SCP02 host challenge must be 8 bytes (GPCS v2.3.1 E.5.1.5)");
        }
    }

    /** Full 3DES MAC (B.1.2.1) with S-ENC and zero ICV over the padded 16-byte block (E.4.2). */
    private byte[] authenticationCryptogram(byte[] first, byte[] second, byte[] third) {
        byte[] block = concat(concat(first, second), third);
        return CryptoUtil.des3Mac(sessionEncKey, CryptoUtil.pad80(block, 8), new byte[MAC_LENGTH]);
    }

    private byte[] sessionKey(byte[] staticKey, byte[] constant) {
        return CryptoUtil.deriveSCP02SessionKey(staticKey, sequenceCounter, constant);
    }

    private static void validateOption(int option, SCPKeys keys) {
        if ((option & OPTION_EXPLICIT) == 0 || (option & OPTION_UNMODIFIED_APDU) != 0 || (option & 0x80) != 0) {
            throw new SCPException(String.format("SCP02 implementation option i=%02X is not supported: only"
                    + " explicit initiation with C-MAC on modified APDU (e.g. i=15, 55) is implemented", option));
        }
        if ((option & OPTION_THREE_KEYS) == 0 && !keys.allEqual()) {
            throw new SCPException(String.format("SCP02 i=%02X uses one Secure Channel base key (Table E-1):"
                    + " use SCPKeys.fromMasterKey(baseKey)", option));
        }
    }

    private static void validateLevel(int level, int option) {
        boolean supported = level == GP.SECURITY_NONE || level == GP.SECURITY_C_MAC
                || level == GP.SECURITY_C_MAC_C_ENC || level == GP.SECURITY_C_MAC_R_MAC
                || level == GP.SECURITY_C_MAC_C_ENC_R_MAC;
        if (!supported) {
            throw new SCPException(String.format("SCP02 security level %02X is not supported (GPCS v2.3.1"
                    + " Table E-11: 00, 01, 03, 11, 13; 10 is not implemented, 3x are RFU)", level));
        }
        if ((level & GP.SECURITY_R_MAC) != 0 && (option & OPTION_RMAC) == 0) {
            throw new SCPException(String.format("SCP02 security level %02X needs R-MAC support, which"
                    + " implementation option i=%02X does not declare (Table E-1 b6); set the card's i", level,
                    option));
        }
    }

    private static void validateKeys(SCPKeys keys) {
        if (keys.keyType().orElse(KeyInfo.KeyType.DES3) != KeyInfo.KeyType.DES3) {
            throw new SCPException("SCP02 uses double-length DES keys (GPCS v2.3.1 Table E-3), but the key set"
                    + " is typed AES");
        }
        if (keys.keyLength() != 16) {
            throw new SCPException("SCP02 keys must be 16-byte double-length DES keys (GPCS v2.3.1 Table E-3),"
                    + " got " + keys.keyLength() + " bytes");
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
