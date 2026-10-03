package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.crypto.CryptoException;
import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * GlobalPlatform Secure Channel Protocol '03' (SCP03), host side (GlobalPlatform Card Specification
 * Amendment D v1.1.2; S16 mode per Amendment D v1.2).
 *
 * <p>SCP03 uses AES-CMAC for MACs and AES-CBC for encryption, with AES-128, AES-192 or AES-256 static
 * keys. The "i" parameter returned by the card in INITIALIZE UPDATE (Table 5-1) selects random or
 * pseudo-random card challenges, R-MAC / R-ENCRYPTION support and S8 or S16 mode; the response layout
 * follows Table 7-3. Supported security levels (Table 7-6): '00', '01', '03', '11', '13' and '33'.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * SCP03 scp = SCP03.from(keys, hostChallenge, initUpdateResponse, GP.SECURITY_C_MAC);  // verifies the card
 * byte[] extAuth = scp.externalAuthenticate();      // send it, require '9000'
 * byte[] wrapped = scp.wrap(plainApdu);             // C-MAC (6.2.4), C-DECRYPTION (6.2.6)
 * APDUResponse plain = scp.unwrap(cardResponse);    // R-MAC (6.2.5), R-ENCRYPTION (6.2.7)
 * </pre>
 *
 * <h2>Key differences from SCP02:</h2>
 * <ul>
 *   <li>Session keys S-ENC, S-MAC, S-RMAC from the NIST SP 800-108 KDF ({@link SCP03KeyDerivation}) with
 *       context host challenge || card challenge (6.2.1); cryptograms are derived from S-MAC (6.2.2)</li>
 *   <li>C-MAC over the MAC chaining value and the command with the <em>encrypted</em> data field; the
 *       R-MAC uses the same MAC chaining value (6.2.4, 6.2.5)</li>
 *   <li>Encryption ICVs derived from a counter incremented for every command (6.2.6, 6.2.7)</li>
 *   <li>Key encryption (PUT KEY) uses the static Key-DEK (6.2.8)</li>
 * </ul>
 *
 * @see SecureChannel
 * @see GP
 */
public final class SCP03 implements SecureChannel {

    private static final int OPTION_RFU = 0x0E;
    private static final int OPTION_RMAC = 0x20;
    private static final int OPTION_RENC = 0x40;
    private static final int BLOCK = 16;

    private final byte[] sessionEncKey;
    private final byte[] sessionMacKey;
    private final byte[] sessionRmacKey;
    private final byte[] staticDekKey;
    private final byte[] hostCryptogram;
    private final int securityLevel;
    private final int option;
    private final int macLength;

    /** MAC chaining value (6.2.3): 16 bytes '00' for EXTERNAL AUTHENTICATE, then the last full C-MAC. */
    private byte[] macChaining = new byte[BLOCK];
    /** Encryption counter (6.2.6): 1 for the first command after EXTERNAL AUTHENTICATE. */
    private long encryptionCounter;
    private boolean authenticated;
    private boolean destroyed;

    private SCP03(SCPKeys keys, byte[] context, InitializeUpdateResponse response, int securityLevel) {
        int keyBits = keys.keyLength() * 8;
        this.option = response.option();
        this.securityLevel = securityLevel;
        this.macLength = response.s16() ? 16 : 8;
        this.sessionEncKey = SCP03KeyDerivation.derive(keys.enc(), GP.SCP03_DERIVE_ENC, keyBits, context);
        this.sessionMacKey = SCP03KeyDerivation.derive(keys.mac(), GP.SCP03_DERIVE_C_MAC, keyBits, context);
        this.sessionRmacKey = SCP03KeyDerivation.derive(keys.mac(), GP.SCP03_DERIVE_R_MAC, keyBits, context);
        this.staticDekKey = keys.dek();
        int cryptogramBits = response.cardCryptogram().length * 8;
        byte[] expected = SCP03KeyDerivation.derive(sessionMacKey, GP.SCP03_DERIVE_CARD_CRYPTO, cryptogramBits,
                context);
        if (!MessageDigest.isEqual(expected, response.cardCryptogram())) {
            destroy();
            throw new SCPException("Card cryptogram verification failed (Amendment D 6.2.2.2): wrong keys or"
                    + " not the expected card");
        }
        this.hostCryptogram = SCP03KeyDerivation.derive(sessionMacKey, GP.SCP03_DERIVE_HOST_CRYPTO,
                cryptogramBits, context);
    }

    /**
     * Creates an SCP03 session from the INITIALIZE UPDATE response and verifies the card cryptogram.
     *
     * <p>The response layout (Amendment D Table 7-3) is {@code key diversification data (10) || Key Version
     * Number || '03' || i || card challenge (8|16) || card cryptogram (8|16) [|| sequence counter (3)]}.
     * The session key derivation context is always {@code host challenge || card challenge} (6.2.1).</p>
     *
     * @param keys          the static key set (AES-128, AES-192 or AES-256, all three of the same length)
     * @param hostChallenge the host challenge sent in INITIALIZE UPDATE (8 bytes, or 16 in S16 mode)
     * @param responseData  the INITIALIZE UPDATE response data
     * @param securityLevel the security level for EXTERNAL AUTHENTICATE (Table 7-6)
     * @return a new SCP03 session
     * @throws SCPException if the response is malformed, the level is not supported by the card's "i"
     *                      parameter, or the card cryptogram is invalid
     */
    public static SCP03 from(SCPKeys keys, byte[] hostChallenge, byte[] responseData, int securityLevel) {
        InitializeUpdateResponse response = InitializeUpdateResponse.parse(responseData, 3);
        validate(keys, hostChallenge, response, securityLevel);
        byte[] context = concat(hostChallenge, response.cardChallenge());
        return new SCP03(keys, context, response, securityLevel);
    }

    /**
     * Creates an SCP03 session from the INITIALIZE UPDATE response.
     *
     * @param keys                  the static key set
     * @param hostChallenge         the host challenge sent in INITIALIZE UPDATE
     * @param responseData          the INITIALIZE UPDATE response data
     * @param securityLevel         the desired security level
     * @param pseudoRandomChallenge ignored: the layout is defined by the "i" parameter in the response
     * @return a new SCP03 session
     * @throws SCPException if the response is malformed or the card cryptogram is invalid
     * @deprecated the INITIALIZE UPDATE response itself tells whether the card uses pseudo-random
     *             challenges (Amendment D Table 5-1, Table 7-3); use {@link #from(SCPKeys, byte[], byte[], int)}
     */
    @Deprecated
    public static SCP03 from(SCPKeys keys, byte[] hostChallenge, byte[] responseData, int securityLevel,
                             boolean pseudoRandomChallenge) {
        return from(keys, hostChallenge, responseData, securityLevel);
    }

    /**
     * Creates an SCP03 session with C-MAC security level.
     *
     * @param keys          the static key set
     * @param hostChallenge the host challenge
     * @param responseData  the INITIALIZE UPDATE response data
     * @return a new SCP03 session with C-MAC security
     */
    public static SCP03 from(SCPKeys keys, byte[] hostChallenge, byte[] responseData) {
        return from(keys, hostChallenge, responseData, GP.SECURITY_C_MAC);
    }

    /**
     * Returns the host cryptogram for use in the EXTERNAL AUTHENTICATE command (6.2.2.3, derived from
     * S-MAC).
     *
     * @return the host cryptogram (8 bytes, or 16 in S16 mode)
     */
    public byte[] hostCryptogram() {
        return hostCryptogram.clone();
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP03: {@code 84 82 P1 00 Lc || host cryptogram || C-MAC}, Lc '10' in S8 mode; the C-MAC is
     * computed over a MAC chaining value of 16 bytes '00' (6.2.3). P1 is this channel's security level.</p>
     */
    @Override
    public byte[] externalAuthenticate() {
        requireUsable();
        if (authenticated) {
            throw new SCPException("EXTERNAL AUTHENTICATE was already produced for this session");
        }
        byte[] apdu = {(byte) 0x84, (byte) GP.INS_EXTERNAL_AUTHENTICATE, (byte) securityLevel, 0x00,
                (byte) hostCryptogram.length};
        authenticated = true;
        return macCommand(ShortApdu.parse(concat(apdu, hostCryptogram)), hostCryptogram);
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP03 wrapping: the encryption counter is incremented for every command (6.2.6); with
     * C-DECRYPTION a non-empty data field is padded (B.2.3) and encrypted with AES-CBC under S-ENC using
     * ICV = AES(S-ENC, counter block); then the C-MAC is the first 8 (S8) or 16 (S16) bytes of
     * AES-CMAC(S-MAC, MAC chaining value || {@code CLA|'04' INS P1 P2 Lc} || data field) and the full CMAC
     * becomes the new MAC chaining value (6.2.4). Le is preserved.</p>
     */
    @Override
    public byte[] wrap(byte[] apdu) {
        requireUsable();
        ShortApdu command = ShortApdu.parse(apdu);
        if (!authenticated) {
            return wrapLegacyExternalAuthenticate(command);
        }
        byte[] clear = command.data();
        requireDataLength(clear.length);
        encryptionCounter++;
        if ((securityLevel & GP.SECURITY_C_MAC) == 0) {
            return command.encode();
        }
        byte[] body = encryptsCommands() && clear.length > 0
                ? CryptoUtil.aesCbcEncrypt(sessionEncKey, CryptoUtil.pad80(clear, BLOCK), counterIcv(0x00))
                : clear;
        return macCommand(command, body);
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP03 response processing: error status words (anything but '9000', '62xx', '63xx') carry no
     * R-MAC and are returned unchanged (6.2.5: "only the status word shall be returned"; with R-MAC, an error
     * with response data is rejected, as that data is not protected). Otherwise the R-MAC (first 8/16 bytes of
     * AES-CMAC(S-RMAC, MAC chaining value || response data || SW)) is verified, and with R-ENCRYPTION a non-empty
     * data field is decrypted with ICV = AES(S-ENC, counter block with its most significant byte set to '80') and
     * unpadded (6.2.7). Responses do not change the MAC chaining value or the encryption counter, so a session
     * continues after an error exactly as the card does (6.2.5 Figure 6-3, 6.2.6).</p>
     */
    @Override
    public APDUResponse unwrap(APDUResponse response) {
        requireUsable();
        int sw = response.sw();
        if ((securityLevel & GP.SECURITY_R_MAC) == 0) {
            return response;
        }
        if (!GP.isSuccessOrWarning(sw)) {
            return unprotectedError(response);
        }
        byte[] data = response.data();
        if (data.length < macLength) {
            throw new SCPException("Response too short for R-MAC: " + data.length + " bytes (minimum "
                    + macLength + ")");
        }
        byte[] body = Arrays.copyOf(data, data.length - macLength);
        byte[] received = Arrays.copyOfRange(data, data.length - macLength, data.length);
        byte[] input = concat(concat(macChaining, body), new byte[]{(byte) (sw >> 8), (byte) sw});
        byte[] expected = Arrays.copyOf(CryptoUtil.aesCmac(sessionRmacKey, input), macLength);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new SCPException("Response MAC verification failed (Amendment D 6.2.5)");
        }
        if ((securityLevel & GP.SECURITY_R_ENC) != 0 && body.length > 0) {
            body = decryptResponse(body);
        }
        return new APDUResponse(body, sw);
    }

    @Override
    public int securityLevel() {
        return securityLevel;
    }

    /**
     * Returns the SCP03 "i" parameter reported by the card (Amendment D Table 5-1).
     *
     * @return the implementation option
     */
    public int implementationOption() {
        return option;
    }

    /**
     * Returns true when the session uses S16 mode (16-byte challenges, cryptograms and MACs).
     *
     * @return true for S16, false for S8
     */
    public boolean isS16() {
        return macLength == 16;
    }

    @Override
    public int maxCommandDataLength() {
        if ((securityLevel & GP.SECURITY_C_MAC) == 0) {
            return ShortApdu.MAX_LC;
        }
        int room = ShortApdu.MAX_LC - macLength;
        // C-DECRYPTION: the data is padded to a multiple of 16 (B.2.3, at least one padding byte)
        return encryptsCommands() ? (room / BLOCK) * BLOCK - 1 : room;
    }

    @Override
    public void destroy() {
        Arrays.fill(sessionEncKey, (byte) 0);
        Arrays.fill(sessionMacKey, (byte) 0);
        Arrays.fill(sessionRmacKey, (byte) 0);
        Arrays.fill(staticDekKey, (byte) 0);
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

    @Override
    public byte[] dek() {
        return staticDekKey.clone();
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
     * Returns the current MAC chaining value (for testing/debugging), which is also the chaining input of
     * the R-MAC of the last command's response (Amendment D 6.2.5, Figure 6-3).
     *
     * @return a copy of the 16-byte MAC chaining value
     */
    public byte[] macChaining() {
        return macChaining.clone();
    }

    /**
     * Returns the chaining value used for the R-MAC of the next response.
     *
     * @return a copy of the MAC chaining value
     * @deprecated SCP03 has no separate R-MAC chain: R-MACs use the MAC chaining value of the command
     *             (Amendment D 6.2.5); use {@link #macChaining()}
     */
    @Deprecated
    public byte[] rmacChaining() {
        return macChaining();
    }

    // ── Internal ──

    private byte[] wrapLegacyExternalAuthenticate(ShortApdu command) {
        if (command.ins() != GP.INS_EXTERNAL_AUTHENTICATE || command.p1() != securityLevel
                || !MessageDigest.isEqual(hostCryptogram, command.data())) {
            throw new SCPException("EXTERNAL AUTHENTICATE with this session's host cryptogram and security level"
                    + " must be the first command of an SCP03 session (use externalAuthenticate())");
        }
        authenticated = true;
        return macCommand(command, command.data());
    }

    private byte[] macCommand(ShortApdu command, byte[] body) {
        byte[] input = concat(macChaining, command.macInput(body, macLength));
        byte[] fullMac = CryptoUtil.aesCmac(sessionMacKey, input);
        macChaining = fullMac;
        return command.encode(command.wireClassByte(), body, Arrays.copyOf(fullMac, macLength));
    }

    /** An error carries "only the status word" (6.2.5): data next to it would reach the caller unprotected. */
    private static APDUResponse unprotectedError(APDUResponse response) {
        if (response.data().length > 0) {
            throw new SCPException(String.format("Error status word %04X with %d response data bytes in a session"
                    + " with R-MAC: errors carry only the status word (Amendment D 6.2.5)", response.sw(),
                    response.data().length));
        }
        return response;
    }

    /** ICV = AES(S-ENC, counter left-padded to 16 bytes, MSB replaced for responses) (6.2.6, 6.2.7). */
    private byte[] counterIcv(int mostSignificantByte) {
        byte[] block = new byte[BLOCK];
        for (int i = 0; i < Long.BYTES; i++) {
            block[BLOCK - 1 - i] = (byte) (encryptionCounter >>> (8 * i));
        }
        block[0] = (byte) mostSignificantByte;
        return CryptoUtil.aesEcbEncrypt(sessionEncKey, block);
    }

    private byte[] decryptResponse(byte[] body) {
        if (body.length % BLOCK != 0) {
            throw new SCPException("Encrypted response data is not a multiple of 16 bytes (Amendment D 6.2.7)");
        }
        try {
            return CryptoUtil.unpad80(CryptoUtil.aesCbcDecrypt(sessionEncKey, body, counterIcv(0x80)));
        } catch (CryptoException e) {
            throw new SCPException("Response decryption failed (Amendment D 6.2.7)", e);
        }
    }

    private boolean encryptsCommands() {
        return (securityLevel & GP.SECURITY_C_MAC_C_ENC) == GP.SECURITY_C_MAC_C_ENC;
    }

    private void requireDataLength(int length) {
        if (length > maxCommandDataLength()) {
            throw new SCPException("Command data of " + length + " bytes exceeds " + maxCommandDataLength()
                    + " bytes, the maximum for SCP03 security level " + String.format("%02X", securityLevel)
                    + " (GPCS v2.3.1 11.1.5, Amendment D 6.2.6)");
        }
    }

    private void requireUsable() {
        if (destroyed) {
            throw new SCPException("Secure channel session has been destroyed");
        }
    }

    private static void validate(SCPKeys keys, byte[] hostChallenge, InitializeUpdateResponse response,
                                 int level) {
        int option = response.option();
        if ((option & OPTION_RFU) != 0 || (option & 0x80) != 0) {
            throw new SCPException(String.format("SCP03 i=%02X uses RFU bits (Amendment D Table 5-1)", option));
        }
        int challengeLength = response.s16() ? 16 : 8;
        if (hostChallenge == null || hostChallenge.length != challengeLength) {
            throw new SCPException(String.format("SCP03 i=%02X (%s mode) needs a %d-byte host challenge", option,
                    response.s16() ? "S16" : "S8", challengeLength));
        }
        validateLevel(level, option);
        if (keys.keyType().orElse(KeyInfo.KeyType.AES) != KeyInfo.KeyType.AES) {
            throw new SCPException("SCP03 uses AES keys (Amendment D Table 6-1), but the key set is typed 3DES");
        }
        if (keys.keyLength() != 16 && keys.keyLength() != 24 && keys.keyLength() != 32) {
            throw new SCPException("SCP03 keys must be AES-128, AES-192 or AES-256 keys (Amendment D Table 6-1)");
        }
    }

    private static void validateLevel(int level, int option) {
        boolean supported = level == GP.SECURITY_NONE || level == GP.SECURITY_C_MAC
                || level == GP.SECURITY_C_MAC_C_ENC || level == GP.SECURITY_C_MAC_R_MAC
                || level == GP.SECURITY_C_MAC_C_ENC_R_MAC || level == GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC;
        if (!supported) {
            throw new SCPException(String.format("SCP03 security level %02X is not defined (Amendment D"
                    + " Table 7-6: 00, 01, 03, 11, 13, 33)", level));
        }
        if ((level & GP.SECURITY_R_MAC) != 0 && (option & OPTION_RMAC) == 0) {
            throw new SCPException(String.format("SCP03 security level %02X needs R-MAC support, but the card"
                    + " reports i=%02X (Amendment D Table 5-1)", level, option));
        }
        if ((level & GP.SECURITY_R_ENC) != 0 && (option & OPTION_RENC) == 0) {
            throw new SCPException(String.format("SCP03 security level %02X needs R-ENCRYPTION support, but"
                    + " the card reports i=%02X (Amendment D Table 5-1)", level, option));
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
