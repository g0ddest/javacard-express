package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import name.velikodniy.jcexpress.APDUResponse;

import java.util.Arrays;

/**
 * GlobalPlatform Secure Channel Protocol 03 (SCP03) implementation.
 *
 * <p>SCP03 uses AES for both MAC (AES-CMAC) and encryption (AES-CBC) operations.
 * It provides stronger security than SCP02 and supports AES-128 and AES-256 keys.</p>
 *
 * <h2>Key differences from SCP02:</h2>
 * <ul>
 *   <li>AES-CMAC instead of 3DES retail MAC</li>
 *   <li>AES-CBC instead of 3DES-CBC for encryption</li>
 *   <li>KDF based on AES-CMAC (not 3DES-CBC)</li>
 *   <li>8-byte C-MAC (truncated from 16-byte CMAC)</li>
 *   <li>Encryption IV derived from MAC chaining value</li>
 * </ul>
 *
 * <h2>Authentication flow:</h2>
 * <pre>
 * // 1. Parse INITIALIZE UPDATE response
 * SCP03 scp = SCP03.from(keys, hostChallenge, initUpdateResponse);
 *
 * // 2. Verify card cryptogram (done automatically in from())
 * // 3. Get host cryptogram for EXTERNAL AUTHENTICATE
 * byte[] hostCrypto = scp.hostCryptogram();
 *
 * // 4. Wrap subsequent commands
 * byte[] wrapped = scp.wrap(plainApdu);
 * </pre>
 *
 * @see SecureChannel
 * @see GP
 */
public final class SCP03 implements SecureChannel {

    private final byte[] sessionMacKey;
    private final byte[] sessionEncKey;
    private final byte[] sessionRmacKey;
    private final byte[] staticDekKey;
    private final byte[] hostCryptogram;
    private final int securityLevel;

    /** C-MAC chaining value — updated after each wrapped command. */
    private byte[] macChaining = new byte[16];

    /** R-MAC chaining value — updated after each unwrapped response. */
    private byte[] rmacChaining = new byte[16];

    /** Counter for encryption IV generation. */
    private int encryptionCounter = 1;

    private SCP03(byte[] sessionMacKey, byte[] sessionEncKey, byte[] sessionRmacKey,
                  byte[] staticDekKey, byte[] hostCryptogram, int securityLevel) {
        this.sessionMacKey = sessionMacKey;
        this.sessionEncKey = sessionEncKey;
        this.sessionRmacKey = sessionRmacKey;
        this.staticDekKey = staticDekKey;
        this.hostCryptogram = hostCryptogram;
        this.securityLevel = securityLevel;
    }

    /**
     * Creates an SCP03 session with explicit card challenge (i=70).
     *
     * @param keys          the static key set
     * @param hostChallenge the 8-byte host challenge sent in INITIALIZE UPDATE
     * @param responseData  the INITIALIZE UPDATE response data
     * @param securityLevel the desired security level
     * @return a new SCP03 session
     * @throws SCPException if the response is malformed or card cryptogram is invalid
     */
    public static SCP03 from(SCPKeys keys, byte[] hostChallenge,
                             byte[] responseData, int securityLevel) {
        return from(keys, hostChallenge, responseData, securityLevel, false);
    }

    /**
     * Creates an SCP03 session from the INITIALIZE UPDATE response.
     *
     * <p>Supports both SCP03 challenge modes:</p>
     * <ul>
     *   <li><b>Explicit (i=70):</b> 28-byte response —
     *       {@code keyDivData(10) || keyInfo(2) || cardChallenge(8) || cryptogram(8)}.
     *       Context = {@code hostChallenge(8) || cardChallenge(8)}.</li>
     *   <li><b>Pseudo-random (i=60):</b> 29-byte response —
     *       {@code keyDivData(10) || keyInfo(2) || seqCounter(3) || cardChallenge(6) || cryptogram(8)}.
     *       Context = {@code hostChallenge(8) || seqCounter(3) || cardChallenge(6)}.</li>
     * </ul>
     *
     * <p>Automatically verifies the card cryptogram during creation.</p>
     *
     * @param keys                   the static key set
     * @param hostChallenge          the 8-byte host challenge sent in INITIALIZE UPDATE
     * @param responseData           the INITIALIZE UPDATE response data
     * @param securityLevel          the desired security level
     * @param pseudoRandomChallenge  true for i=60 (pseudo-random), false for i=70 (explicit)
     * @return a new SCP03 session
     * @throws SCPException if the response is malformed or card cryptogram is invalid
     */
    public static SCP03 from(SCPKeys keys, byte[] hostChallenge,
                             byte[] responseData, int securityLevel,
                             boolean pseudoRandomChallenge) {

        byte[] cardCryptogramReceived;
        byte[] context;

        if (pseudoRandomChallenge) {
            if (responseData.length < 29) {
                throw new SCPException(
                        "INITIALIZE UPDATE response too short for SCP03 pseudo-random: "
                                + responseData.length + " bytes (expected at least 29)");
            }

            byte[] seqCounter = Arrays.copyOfRange(responseData, 12, 15);
            byte[] cardChallenge = Arrays.copyOfRange(responseData, 15, 21);
            cardCryptogramReceived = Arrays.copyOfRange(responseData, 21, 29);

            // Context = hostChallenge(8) || seqCounter(3) || cardChallenge(6) = 17 bytes
            context = new byte[17];
            System.arraycopy(hostChallenge, 0, context, 0, 8);
            System.arraycopy(seqCounter, 0, context, 8, 3);
            System.arraycopy(cardChallenge, 0, context, 11, 6);
        } else {
            if (responseData.length < 28) {
                throw new SCPException(
                        "INITIALIZE UPDATE response too short: " + responseData.length
                                + " bytes (expected at least 28 for SCP03)");
            }

            byte[] cardChallenge = Arrays.copyOfRange(responseData, 12, 20);
            cardCryptogramReceived = Arrays.copyOfRange(responseData, 20, 28);

            // Context = hostChallenge(8) || cardChallenge(8) = 16 bytes
            context = new byte[16];
            System.arraycopy(hostChallenge, 0, context, 0, 8);
            System.arraycopy(cardChallenge, 0, context, 8, 8);
        }

        int keyBits = keys.keyLength() * 8;

        // Derive session keys
        byte[] sessionMacKey = CryptoUtil.deriveSCP03SessionKey(
                keys.mac(), context, GP.SCP03_DERIVE_C_MAC, keyBits);
        byte[] sessionEncKey = CryptoUtil.deriveSCP03SessionKey(
                keys.enc(), context, GP.SCP03_DERIVE_ENC, keyBits);
        byte[] sessionRmacKey = CryptoUtil.deriveSCP03SessionKey(
                keys.mac(), context, GP.SCP03_DERIVE_R_MAC, keyBits);

        // Verify card cryptogram
        byte[] expectedCardCrypto = CryptoUtil.deriveSCP03SessionKey(
                keys.mac(), context, GP.SCP03_DERIVE_CARD_CRYPTO, 64);
        byte[] cardCryptoTruncated = Arrays.copyOf(expectedCardCrypto, 8);

        if (!Arrays.equals(cardCryptoTruncated, cardCryptogramReceived)) {
            throw new SCPException("Card cryptogram verification failed");
        }

        // Compute host cryptogram
        byte[] hostCryptoFull = CryptoUtil.deriveSCP03SessionKey(
                keys.mac(), context, GP.SCP03_DERIVE_HOST_CRYPTO, 64);
        byte[] hostCryptogram = Arrays.copyOf(hostCryptoFull, 8);

        return new SCP03(sessionMacKey, sessionEncKey, sessionRmacKey, keys.dek(),
                hostCryptogram, securityLevel);
    }

    /**
     * Creates an SCP03 session with C-MAC security level and explicit challenge (i=70).
     *
     * @param keys          the static key set
     * @param hostChallenge the 8-byte host challenge
     * @param responseData  the INITIALIZE UPDATE response data
     * @return a new SCP03 session with C-MAC security
     */
    public static SCP03 from(SCPKeys keys, byte[] hostChallenge, byte[] responseData) {
        return from(keys, hostChallenge, responseData, GP.SECURITY_C_MAC, false);
    }

    /**
     * Returns the host cryptogram for use in the EXTERNAL AUTHENTICATE command.
     *
     * @return the 8-byte host cryptogram
     */
    public byte[] hostCryptogram() {
        return hostCryptogram.clone();
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP03 wrapping:</p>
     * <ol>
     *   <li>Sets CLA bit 2 (CLA |= 0x04) for secure messaging</li>
     *   <li>If C-ENC: pads and encrypts command data with AES-CBC
     *       using an IV derived from the MAC chaining value</li>
     *   <li>Computes AES-CMAC over the modified header + data</li>
     *   <li>Appends the truncated 8-byte C-MAC</li>
     *   <li>Updates Lc and MAC chaining value</li>
     * </ol>
     */
    @Override
    public byte[] wrap(byte[] apdu) {
        if (apdu.length < 4) {
            throw new SCPException("APDU too short: " + apdu.length + " bytes (minimum 4)");
        }

        // Parse APDU
        int cla = apdu[0] & 0xFF;
        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;

        byte[] commandData = new byte[0];
        boolean hasLe = false;
        int le = 0;

        if (apdu.length > 4) {
            int lc = apdu[4] & 0xFF;
            if (apdu.length == 5) {
                hasLe = true;
                le = lc;
            } else if (apdu.length == 5 + lc) {
                commandData = Arrays.copyOfRange(apdu, 5, 5 + lc);
            } else if (apdu.length == 5 + lc + 1) {
                commandData = Arrays.copyOfRange(apdu, 5, 5 + lc);
                hasLe = true;
                le = apdu[5 + lc] & 0xFF;
            }
        }

        // C-ENC: encrypt command data
        if ((securityLevel & GP.SECURITY_C_MAC_C_ENC) == GP.SECURITY_C_MAC_C_ENC
                && commandData.length > 0) {
            // Generate encryption IV from MAC chaining value
            byte[] encIv = generateEncryptionIV();
            byte[] padded = CryptoUtil.pad80(commandData, 16);
            commandData = CryptoUtil.aesCbcEncrypt(sessionEncKey, padded, encIv);
        }

        // Set secure messaging bit in CLA
        cla |= 0x04;

        // Build MAC input: chaining value || modified header || data
        int newLc = commandData.length + 8; // +8 for MAC
        byte[] macInput = new byte[macChaining.length + 5 + commandData.length];
        System.arraycopy(macChaining, 0, macInput, 0, macChaining.length);
        macInput[macChaining.length] = (byte) cla;
        macInput[macChaining.length + 1] = (byte) ins;
        macInput[macChaining.length + 2] = (byte) p1;
        macInput[macChaining.length + 3] = (byte) p2;
        macInput[macChaining.length + 4] = (byte) newLc;
        if (commandData.length > 0) {
            System.arraycopy(commandData, 0, macInput, macChaining.length + 5, commandData.length);
        }

        byte[] fullMac = CryptoUtil.aesCmac(sessionMacKey, macInput);
        byte[] mac = Arrays.copyOf(fullMac, 8); // Truncate to 8 bytes

        // Update MAC chaining value
        macChaining = fullMac.clone();

        // Build wrapped APDU
        int wrappedLength = 5 + commandData.length + 8 + (hasLe ? 1 : 0);
        byte[] wrapped = new byte[wrappedLength];
        wrapped[0] = (byte) cla;
        wrapped[1] = (byte) ins;
        wrapped[2] = (byte) p1;
        wrapped[3] = (byte) p2;
        wrapped[4] = (byte) newLc;
        if (commandData.length > 0) {
            System.arraycopy(commandData, 0, wrapped, 5, commandData.length);
        }
        System.arraycopy(mac, 0, wrapped, 5 + commandData.length, 8);
        if (hasLe) {
            wrapped[wrapped.length - 1] = (byte) le;
        }

        return wrapped;
    }

    @Override
    public int securityLevel() {
        return securityLevel;
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
     * Returns the current R-MAC chaining value (for testing/debugging).
     *
     * @return a copy of the current R-MAC chaining value
     */
    public byte[] rmacChaining() {
        return rmacChaining.clone();
    }

    /**
     * {@inheritDoc}
     *
     * <p>SCP03 response unwrapping supports both R-MAC and R-ENC:</p>
     * <ol>
     *   <li>If R-MAC (bit 4): verify AES-CMAC over
     *       {@code rmacChaining || responseData || SW1 || SW2}, strip MAC</li>
     *   <li>If R-ENC (bit 5): decrypt payload with AES-CBC (zero IV) and strip padding</li>
     * </ol>
     *
     * <p>When both R-MAC and R-ENC are active, the wire format is
     * {@code [encrypted_data] [R-MAC(8)] [SW1] [SW2]}. R-MAC is computed
     * over the encrypted data, so verification happens first.</p>
     */
    @Override
    public APDUResponse unwrap(APDUResponse response) {
        boolean hasRmac = (securityLevel & 0x10) != 0;
        boolean hasRenc = (securityLevel & 0x20) != 0;
        if (!hasRmac && !hasRenc) {
            return response;
        }

        byte[] data = response.data();
        byte[] payload = data;

        // Step 1: R-MAC verification (over encrypted data if R-ENC is active)
        if (hasRmac) {
            if (data.length < 8) {
                throw new SCPException("Response too short for R-MAC: "
                        + data.length + " bytes (minimum 8)");
            }

            payload = Arrays.copyOfRange(data, 0, data.length - 8);
            byte[] receivedMac = Arrays.copyOfRange(data, data.length - 8, data.length);

            // SCP03 R-MAC input: rmacChaining || responseData || SW1 || SW2
            int sw = response.sw();
            byte[] macInput = new byte[rmacChaining.length + payload.length + 2];
            System.arraycopy(rmacChaining, 0, macInput, 0, rmacChaining.length);
            System.arraycopy(payload, 0, macInput, rmacChaining.length, payload.length);
            macInput[macInput.length - 2] = (byte) ((sw >> 8) & 0xFF);
            macInput[macInput.length - 1] = (byte) (sw & 0xFF);

            byte[] fullMac = CryptoUtil.aesCmac(sessionRmacKey, macInput);
            byte[] expectedMac = Arrays.copyOf(fullMac, 8);

            if (!Arrays.equals(receivedMac, expectedMac)) {
                throw new SCPException("Response MAC verification failed");
            }

            rmacChaining = fullMac.clone();
        }

        // Step 2: R-ENC decryption (AES-CBC with zero IV)
        if (hasRenc && payload.length > 0) {
            byte[] decrypted = CryptoUtil.aesCbcDecrypt(sessionEncKey, payload);
            payload = CryptoUtil.unpad80(decrypted);
        }

        return new APDUResponse(payload, response.sw());
    }

    /**
     * Generates the encryption IV for the next command.
     *
     * <p>IV = AES-ECB(sessionENC, counter block), where counter block is
     * a 16-byte block with the encryption counter in the last bytes.</p>
     */
    private byte[] generateEncryptionIV() {
        byte[] counterBlock = new byte[16];
        counterBlock[15] = (byte) (encryptionCounter & 0xFF);
        counterBlock[14] = (byte) ((encryptionCounter >> 8) & 0xFF);
        encryptionCounter++;
        return CryptoUtil.aesCbcEncrypt(sessionEncKey, counterBlock);
    }
}
