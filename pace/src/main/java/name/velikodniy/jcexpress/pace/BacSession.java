package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.sm.SMAlgorithm;
import name.velikodniy.jcexpress.sm.SMKeys;

import javax.security.auth.Destroyable;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Basic Access Control, inspection system side (ICAO Doc 9303-11, 4.3).
 *
 * <p>Runs the three-pass challenge-response protocol of 4.3.1 with the Document Basic Access Keys
 * {@code KEnc = KDF(Kseed, 1)} and {@code KMAC = KDF(Kseed, 2)}, where Kseed is the first 16 bytes of
 * {@code SHA-1(MRZ_information)} (4.3.2, 9.7.2):</p>
 * <ol>
 *   <li>GET CHALLENGE (4.3.4.1) returns the chip's nonce RND.IC (8 bytes).</li>
 *   <li>EXTERNAL AUTHENTICATE (4.3.4.2) sends {@code E_IFD || M_IFD}: {@code E_IFD = E(KEnc, RND.IFD || RND.IC ||
 *       K.IFD)} with two-key 3DES in CBC mode, zero IV and no padding (4.3.3.1), and the checksum
 *       {@code M_IFD = MAC(KMAC, E_IFD)}, ISO/IEC 9797-1 MAC algorithm 3 with padding method 2 (4.3.3.2).</li>
 *   <li>The chip answers {@code E_IC || M_IC}. The checksum is verified, {@code R = RND.IC || RND.IFD || K.IC} is
 *       decrypted and both nonces are compared (4.3.1 step 4).</li>
 *   <li>The session keys are {@code KSEnc = KDF(K.IFD xor K.IC, 1)} and {@code KSMAC = KDF(K.IFD xor K.IC, 2)}
 *       (4.3.1 step 5, 9.7.4); the SSC is {@code RND.IC (4 least significant bytes) || RND.IFD (4 least significant
 *       bytes)} (9.8.6.3).</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 * BacResult bac = BacSession.builder()
 *     .mrz("L898902C&lt;", "690806", "940623")
 *     .build()
 *     .perform(card);
 * SMSession secure = bac.toSMSession(card);   // 3DES Secure Messaging (9.8.6)
 * </pre>
 */
public final class BacSession implements Destroyable {

    private static final SMAlgorithm DES3 = SMAlgorithm.DES3;
    private static final int NONCE_LENGTH = 8;
    private static final int KEY_LENGTH = 16;
    private static final int CRYPTOGRAM_LENGTH = 2 * NONCE_LENGTH + KEY_LENGTH;
    private static final int CHECKSUM_LENGTH = 8;
    /** Ne of EXTERNAL AUTHENTICATE: {@code E_IC || M_IC}, Le = '28' as in ICAO App. D.3. */
    private static final int AUTHENTICATION_DATA_LENGTH = CRYPTOGRAM_LENGTH + CHECKSUM_LENGTH;
    private static final int SSC_PART = 4;

    private final byte[] encKey;
    private final byte[] macKey;
    private final RandomSource random;
    private boolean destroyed;

    /**
     * Supplies the inspection system's random values RND.IFD and K.IFD (in this order).
     *
     * <p>Package-private seam for known-answer tests; the default is a {@link SecureRandom}.</p>
     */
    @FunctionalInterface
    interface RandomSource {
        void nextBytes(byte[] bytes);
    }

    private BacSession(Builder builder) {
        this.encKey = builder.accessKeys.encKey();
        this.macKey = builder.accessKeys.macKey();
        this.random = builder.random;
    }

    /**
     * Creates a new BAC session builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Derives the Document Basic Access Keys {@code KEnc = KDF(Kseed, 1)} and {@code KMAC = KDF(Kseed, 2)} from the
     * MRZ (ICAO Doc 9303-11, 4.3.2 step 1, 9.7.2), with the parity bits adjusted as in App. D.1 and D.2 step 5.
     *
     * @param documentNumber the document number (padded with {@code '<'} to nine characters if shorter)
     * @param dateOfBirth    the date of birth (YYMMDD)
     * @param dateOfExpiry   the date of expiry (YYMMDD)
     * @return the two 16-byte two-key 3DES keys
     * @throws IllegalArgumentException if a field is not a valid MRZ field (see {@link PaceMrz#mrzInformation})
     */
    public static SMKeys documentBasicAccessKeys(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        byte[] kSeed = PaceMrz.bacKeySeed(documentNumber, dateOfBirth, dateOfExpiry);
        try {
            return des3Keys(kSeed);
        } finally {
            Arrays.fill(kSeed, (byte) 0);
        }
    }

    /**
     * Derives two-key 3DES keys {@code KDF(K, 1)} and {@code KDF(K, 2)} (ICAO Doc 9303-11, 9.7.1.1): octets 1-8 and
     * 9-16 of the SHA-1 keydata, with the OPTIONAL parity adjustment applied so that the keys equal the values of
     * App. D. DES ignores the parity bits, so chips that skip the adjustment compute the same cryptograms.
     */
    private static SMKeys des3Keys(byte[] kSeed) {
        return new SMKeys(adjustParity(PaceMrz.kdf(kSeed, 1, KEY_LENGTH)),
                adjustParity(PaceMrz.kdf(kSeed, 2, KEY_LENGTH)));
    }

    /** Sets the least significant bit of every byte so that it has odd parity (FIPS 46-3 DES key format). */
    private static byte[] adjustParity(byte[] key) {
        for (int i = 0; i < key.length; i++) {
            int bits = key[i] & 0xFE;
            key[i] = (byte) (bits | (~Integer.bitCount(bits) & 1));
        }
        return key;
    }

    /**
     * Performs Basic Access Control on the given session.
     *
     * <p>The session keeps the Document Basic Access Keys, so it can be performed again (e.g. after a card reset)
     * until it is {@linkplain #destroy() destroyed}.</p>
     *
     * @param session the session to the chip (plain, i.e. not yet protected)
     * @return the session keys and SSC for 3DES Secure Messaging
     * @throws BacException          if the chip rejects a command or its response fails the checks of 4.3.1 step 4
     * @throws IllegalStateException if the session has been destroyed
     */
    public BacResult perform(SmartCardSession session) {
        Objects.requireNonNull(session, "session");
        if (destroyed) {
            throw new IllegalStateException("The BAC session has been destroyed");
        }
        byte[] rndIc = getChallenge(session);
        byte[] rndIfd = new byte[NONCE_LENGTH];
        byte[] kIfd = new byte[KEY_LENGTH];
        random.nextBytes(rndIfd);
        random.nextBytes(kIfd);
        byte[] kIc = null;
        try {
            byte[] response = externalAuthenticate(session, concat(rndIfd, rndIc, kIfd));
            kIc = openChipResponse(response, rndIc, rndIfd);
            return result(kIfd, kIc, rndIc, rndIfd);
        } finally {
            Arrays.fill(kIfd, (byte) 0);
            if (kIc != null) {
                Arrays.fill(kIc, (byte) 0);
            }
        }
    }

    /**
     * Wipes the Document Basic Access Keys; the session cannot be performed afterwards.
     */
    @Override
    public void destroy() {
        Arrays.fill(encKey, (byte) 0);
        Arrays.fill(macKey, (byte) 0);
        destroyed = true;
    }

    /**
     * Returns whether {@link #destroy()} has been called.
     *
     * @return true if the access keys have been wiped
     */
    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    /** 4.3.4.1: GET CHALLENGE with Le '08'. */
    private static byte[] getChallenge(SmartCardSession session) {
        APDUResponse response = requireSuccess(session.send(0x00, 0x84, 0x00, 0x00, null, NONCE_LENGTH),
                "GET CHALLENGE");
        byte[] rndIc = response.data();
        if (rndIc.length != NONCE_LENGTH) {
            throw new BacException("GET CHALLENGE returned " + rndIc.length
                    + " bytes; the nonce RND.IC must have 8 bytes (ICAO 9303-11 4.3.1)");
        }
        return rndIc;
    }

    /** 4.3.1 step 2 and 4.3.4.2: EXTERNAL AUTHENTICATE with {@code E_IFD || M_IFD} and Le '28'. */
    private byte[] externalAuthenticate(SmartCardSession session, byte[] s) {
        byte[] eIfd;
        try {
            eIfd = DES3.encrypt(encKey, s, new byte[NONCE_LENGTH]);
        } finally {
            Arrays.fill(s, (byte) 0);
        }
        byte[] commandData = concat(eIfd, checksum(eIfd));
        return requireSuccess(session.send(0x00, 0x82, 0x00, 0x00, commandData, AUTHENTICATION_DATA_LENGTH),
                "EXTERNAL AUTHENTICATE").data();
    }

    /** 4.3.1 step 4: checks M_IC, decrypts E_IC and compares the nonces; returns K.IC. */
    private byte[] openChipResponse(byte[] response, byte[] rndIc, byte[] rndIfd) {
        if (response.length != AUTHENTICATION_DATA_LENGTH) {
            throw new BacException("EXTERNAL AUTHENTICATE returned " + response.length
                    + " bytes; E_IC || M_IC must have 40 bytes (ICAO 9303-11 4.3.1)");
        }
        byte[] eIc = Arrays.copyOf(response, CRYPTOGRAM_LENGTH);
        byte[] mIc = Arrays.copyOfRange(response, CRYPTOGRAM_LENGTH, AUTHENTICATION_DATA_LENGTH);
        if (!MessageDigest.isEqual(checksum(eIc), mIc)) {
            throw new BacException("EXTERNAL AUTHENTICATE: the checksum M_IC is wrong (ICAO 9303-11 4.3.1 step 4a)");
        }
        byte[] r = DES3.decrypt(encKey, eIc, new byte[NONCE_LENGTH]);
        try {
            if (!MessageDigest.isEqual(Arrays.copyOfRange(r, NONCE_LENGTH, 2 * NONCE_LENGTH), rndIfd)) {
                throw new BacException("EXTERNAL AUTHENTICATE: the chip did not return RND.IFD"
                        + " (ICAO 9303-11 4.3.1 step 4c)");
            }
            if (!MessageDigest.isEqual(Arrays.copyOf(r, NONCE_LENGTH), rndIc)) {
                throw new BacException("EXTERNAL AUTHENTICATE: R does not start with RND.IC"
                        + " (ICAO 9303-11 4.3.1 step 3e)");
            }
            return Arrays.copyOfRange(r, 2 * NONCE_LENGTH, CRYPTOGRAM_LENGTH);
        } finally {
            Arrays.fill(r, (byte) 0);
        }
    }

    /** 4.3.3.2: ISO/IEC 9797-1 MAC algorithm 3 (DES, zero IV), padding method 2, 8 bytes. */
    private byte[] checksum(byte[] cryptogram) {
        return DES3.mac(macKey, DES3.pad(cryptogram));
    }

    /** 4.3.1 step 5 (9.7.1.1, 9.7.4) and 9.8.6.3. */
    private static BacResult result(byte[] kIfd, byte[] kIc, byte[] rndIc, byte[] rndIfd) {
        byte[] kSeed = new byte[KEY_LENGTH];
        for (int i = 0; i < KEY_LENGTH; i++) {
            kSeed[i] = (byte) (kIfd[i] ^ kIc[i]);
        }
        byte[] ksEnc = adjustParity(PaceMrz.kdf(kSeed, 1, KEY_LENGTH));
        byte[] ksMac = adjustParity(PaceMrz.kdf(kSeed, 2, KEY_LENGTH));
        byte[] ssc = concat(Arrays.copyOfRange(rndIc, NONCE_LENGTH - SSC_PART, NONCE_LENGTH),
                Arrays.copyOfRange(rndIfd, NONCE_LENGTH - SSC_PART, NONCE_LENGTH));
        try {
            return new BacResult(ksEnc, ksMac, ssc);
        } finally {
            Arrays.fill(kSeed, (byte) 0);
            Arrays.fill(ksEnc, (byte) 0);
            Arrays.fill(ksMac, (byte) 0);
        }
    }

    private static APDUResponse requireSuccess(APDUResponse response, String command) {
        if (!response.isSuccess()) {
            throw new BacException(command + " failed: SW=" + String.format("%04X", response.sw()));
        }
        return response;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    /**
     * Builder for {@link BacSession}.
     */
    public static final class Builder {

        private static final SecureRandom RANDOM = new SecureRandom();

        private SMKeys accessKeys;
        private RandomSource random = RANDOM::nextBytes;

        private Builder() {
        }

        /**
         * Derives the Document Basic Access Keys from the MRZ (see {@link #documentBasicAccessKeys}).
         *
         * @param documentNumber the document number (padded with {@code '<'} to nine characters if shorter)
         * @param dateOfBirth    the date of birth (YYMMDD)
         * @param dateOfExpiry   the date of expiry (YYMMDD)
         * @return this builder
         * @throws IllegalArgumentException if a field is not a valid MRZ field
         */
        public Builder mrz(String documentNumber, String dateOfBirth, String dateOfExpiry) {
            this.accessKeys = documentBasicAccessKeys(documentNumber, dateOfBirth, dateOfExpiry);
            return this;
        }

        /**
         * Sets the Document Basic Access Keys KEnc and KMAC directly (ICAO Doc 9303-11, 9.7.2).
         *
         * @param keys two-key 3DES keys of 16 bytes each (parity bits are ignored)
         * @return this builder
         * @throws IllegalArgumentException if a key does not have 16 bytes
         */
        public Builder accessKeys(SMKeys keys) {
            Objects.requireNonNull(keys, "keys");
            if (keys.encKey().length != KEY_LENGTH || keys.macKey().length != KEY_LENGTH) {
                throw new IllegalArgumentException("BAC uses two-key 3DES: KEnc and KMAC must have 16 bytes");
            }
            this.accessKeys = keys;
            return this;
        }

        /**
         * Replaces the source of RND.IFD and K.IFD (known-answer tests only).
         *
         * @param source the random source
         * @return this builder
         */
        Builder randomSource(RandomSource source) {
            this.random = Objects.requireNonNull(source, "source");
            return this;
        }

        /**
         * Builds the BAC session.
         *
         * @return a configured BAC session
         * @throws NullPointerException if neither the MRZ nor the access keys were given
         */
        public BacSession build() {
            Objects.requireNonNull(accessKeys, "mrz or accessKeys must be set");
            return new BacSession(this);
        }
    }
}
