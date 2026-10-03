package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMKeys;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.tlv.Tags;

import javax.security.auth.Destroyable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;
import java.util.Objects;

/**
 * PACE protocol session, terminal side (ICAO Doc 9303-11, 4.4; BSI TR-03110-2), for ECDH with Generic Mapping
 * and AES session keys (id-PACE-ECDH-GM-AES-CBC-CMAC-128/192/256).
 *
 * <p>Executes MSE:Set AT followed by the chain of four GENERAL AUTHENTICATE commands (4.4.4, 4.4.5):</p>
 * <ol>
 *   <li>request the encrypted nonce z and decrypt {@code s = D(K_pi, z)};</li>
 *   <li>exchange mapping keys and compute {@code G^ = s*G + H} with {@code H = SK_Map,IFD * PK_Map,IC}
 *       (4.4.3.3.1);</li>
 *   <li>exchange ephemeral keys on {@code G^} and derive {@code KSEnc = KDF(K,1)} and {@code KSMAC = KDF(K,2)}
 *       from the x-coordinate K of the shared point (9.7.1);</li>
 *   <li>exchange and verify the authentication tokens (4.4.3.4).</li>
 * </ol>
 * <p>On success, returns a {@link PaceResult} whose keys start AES Secure Messaging with SSC 0.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * PaceResult result = PaceSession.builder()
 *     .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
 *     .parameterId(PaceParameterId.BRAINPOOL_P256R1)
 *     .mrzPassword("L898902C&lt;", "690806", "940623")
 *     .build()
 *     .perform(session);
 *
 * SMSession smSession = result.toSMSession(session);
 * </pre>
 */
public final class PaceSession implements Destroyable {

    /** Ne = 256: Le field '00' on a short APDU, as in every GENERAL AUTHENTICATE of ICAO App. G.1. */
    private static final int NE_256 = 256;
    private static final int AES_BLOCK = 16;

    private final PaceAlgorithm algorithm;
    private final PaceParameterId parameterId;
    private final PasswordRef passwordRef;
    private final byte[] passwordKey; // K_pi = KDF(f(pi), 3)
    private final boolean includeParameterId;
    private final EphemeralKeySource ephemeralKeys;
    private boolean destroyed;

    /**
     * Supplies the terminal's ephemeral private keys SK_Map,IFD and SK_DH,IFD (in this order).
     *
     * <p>Package-private seam for known-answer tests; the default draws uniformly random scalars.</p>
     */
    @FunctionalInterface
    interface EphemeralKeySource {
        BigInteger nextPrivateKey(ECParameterSpec params);
    }

    /** Ephemeral public keys and session keys after the key agreement (4.4.1 step 3c-3e). */
    private record KeyAgreement(byte[] terminalPublicKey, byte[] chipPublicKey, SMKeys sessionKeys) {
    }

    private PaceSession(Builder builder, byte[] kPi) {
        this.algorithm = builder.algorithm;
        this.parameterId = builder.parameterId;
        this.passwordRef = builder.passwordRef;
        this.passwordKey = kPi;
        this.includeParameterId = builder.includeParameterId;
        this.ephemeralKeys = builder.ephemeralKeys;
    }

    /**
     * Creates a new PACE session builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the full PACE protocol on the given session.
     *
     * <p>The session keeps the password key K_pi, so it can be performed again (e.g. after a card reset) until it
     * is {@linkplain #destroy() destroyed}.</p>
     *
     * @param session the smart card session
     * @return the PACE result with session keys
     * @throws PaceException         if the protocol fails
     * @throws IllegalStateException if the session has been destroyed
     */
    public PaceResult perform(SmartCardSession session) {
        if (destroyed) {
            throw new IllegalStateException("The PACE session has been destroyed");
        }
        ECParameterSpec params = parameterId.ecParameterSpec();
        sendMseSetAt(session);
        BigInteger nonce = decryptNonce(generalAuthenticate(session, 1, -1, null, Tags.PACE_NONCE));
        ECPoint mappedGenerator = mapNonce(session, params, nonce);
        KeyAgreement agreement = agreeOnKeys(session, params, mappedGenerator);
        return authenticate(session, agreement);
    }

    /**
     * Wipes the password key K_pi; the session cannot be performed afterwards.
     */
    @Override
    public void destroy() {
        Arrays.fill(passwordKey, (byte) 0);
        destroyed = true;
    }

    /**
     * Returns whether {@link #destroy()} has been called.
     *
     * @return true if the password key has been wiped
     */
    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * MSE:Set AT (4.4.4.1): 0x80 protocol OID, 0x83 password reference and, if enabled, 0x84 domain parameter ID.
     */
    private void sendMseSetAt(SmartCardSession session) {
        TLVBuilder data = TLVBuilder.create()
                .add(0x80, algorithm.oidBytes())
                .add(0x83, new byte[]{(byte) passwordRef.ref()});
        if (includeParameterId) {
            data.add(0x84, new byte[]{(byte) parameterId.id()});
        }
        APDUResponse response = session.send(0x00, 0x22, 0xC1, 0xA4, data.build());
        if (!response.isSuccess()) {
            throw new PaceException("MSE:Set AT failed: SW=" + String.format("%04X", response.sw()));
        }
    }

    /** 4.4.3.3: z is decrypted in CBC mode with K_pi and an all-zero IV. */
    private BigInteger decryptNonce(byte[] encryptedNonce) {
        if (encryptedNonce.length == 0 || encryptedNonce.length % AES_BLOCK != 0) {
            throw new PaceException("PACE Step 1: encrypted nonce of " + encryptedNonce.length
                    + " bytes is not a multiple of the AES block size");
        }
        byte[] nonce = CryptoUtil.aesCbcDecrypt(passwordKey, encryptedNonce);
        try {
            return new BigInteger(1, nonce);
        } finally {
            Arrays.fill(nonce, (byte) 0);
        }
    }

    /** Step 2, Generic Mapping (4.4.3.3.1, 4.4.5.2.1). */
    private ECPoint mapNonce(SmartCardSession session, ECParameterSpec params, BigInteger nonce) {
        BigInteger skMap = ephemeralKeys.nextPrivateKey(params);
        byte[] pkMap = encode(PaceCrypto.scalarMultiply(skMap, params.getGenerator(), params.getCurve()), params);
        byte[] chipKey = generalAuthenticate(session, 2, Tags.PACE_MAP_DATA, pkMap, Tags.PACE_MAP_RESPONSE);
        ECPoint chipPoint = PaceCrypto.decodePoint(chipKey, params.getCurve());
        return PaceCrypto.mapNonceGeneric(nonce, skMap, chipPoint, params);
    }

    /** Step 3, anonymous ECDH on the mapped generator and session key derivation (4.4.1 step 3c-3e, 9.7.4). */
    private KeyAgreement agreeOnKeys(SmartCardSession session, ECParameterSpec params, ECPoint mappedGenerator) {
        BigInteger skDh = ephemeralKeys.nextPrivateKey(params);
        byte[] pkDh = encode(PaceCrypto.scalarMultiply(skDh, mappedGenerator, params.getCurve()), params);
        byte[] chipKey = generalAuthenticate(session, 3, Tags.PACE_EPHEMERAL_PK, pkDh,
                Tags.PACE_EPHEMERAL_PK_RESPONSE);
        if (MessageDigest.isEqual(chipKey, pkDh)) {
            // 4.4.1 step 3d: PK_DH,IC and PK_DH,IFD SHOULD be checked to differ
            throw new PaceException("PACE Step 3: the chip's PK_DH equals the terminal's PK_DH");
        }
        ECPoint chipPoint = PaceCrypto.decodePoint(chipKey, params.getCurve());
        byte[] sharedSecret = PaceCrypto.sharedSecret(skDh, chipPoint, params);
        try {
            return new KeyAgreement(pkDh, chipKey, PaceMrz.deriveKeys(sharedSecret, algorithm.keyLength()));
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
        }
    }

    /** Step 4, mutual authentication: T_IFD = MAC(KSMAC, PK_DH,IC), T_IC = MAC(KSMAC, PK_DH,IFD) (4.4.3.4). */
    private PaceResult authenticate(SmartCardSession session, KeyAgreement agreement) {
        byte[] encKey = agreement.sessionKeys().encKey();
        byte[] macKey = agreement.sessionKeys().macKey();
        try {
            byte[] oid = algorithm.oidBytes();
            byte[] terminalToken = PaceCrypto.authToken(macKey, oid, agreement.chipPublicKey());
            byte[] chipToken = generalAuthenticate(session, 4, Tags.PACE_AUTH_TOKEN, terminalToken,
                    Tags.PACE_AUTH_TOKEN_RESPONSE);
            byte[] expectedChipToken = PaceCrypto.authToken(macKey, oid, agreement.terminalPublicKey());
            if (!MessageDigest.isEqual(chipToken, expectedChipToken)) {
                throw new PaceException("PACE mutual authentication failed: card token mismatch");
            }
            return new PaceResult(encKey, macKey, chipToken, terminalToken);
        } finally {
            Arrays.fill(encKey, (byte) 0);
            Arrays.fill(macKey, (byte) 0);
        }
    }

    /**
     * Sends one GENERAL AUTHENTICATE of the chain (4.4.4.2, 4.4.4.3): CLA '10' (command chaining) for steps 1-3,
     * '00' for the last step, data {@code 7C [tag value]}, Le '00'.
     *
     * @return the value of {@code responseTag} inside the response's Dynamic Authentication Data
     */
    private byte[] generalAuthenticate(SmartCardSession session, int step, int commandTag, byte[] value,
                                       int responseTag) {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b -> {
                    if (value != null) {
                        b.add(commandTag, value);
                    }
                })
                .build();
        int cla = step < 4 ? 0x10 : 0x00;
        APDUResponse response = session.send(cla, 0x86, 0x00, 0x00, data, NE_256);
        if (!response.isSuccess()) {
            throw new PaceException("GENERAL AUTHENTICATE Step " + step
                    + " failed: SW=" + String.format("%04X", response.sw()));
        }
        return extractChildValue(response.data(), responseTag, step);
    }

    /**
     * Extracts a child TLV value from a Dynamic Authentication Data (0x7C) response.
     */
    private static byte[] extractChildValue(byte[] responseData, int childTag, int step) {
        TLV outer;
        try {
            outer = TLVParser.parse(responseData).find(Tags.DYNAMIC_AUTH_DATA)
                    .orElseThrow(() -> new PaceException("PACE Step " + step
                            + ": missing Dynamic Authentication Data (0x7C)"));
        } catch (RuntimeException e) {
            if (e instanceof PaceException paceException) {
                throw paceException;
            }
            throw new PaceException("PACE Step " + step + ": malformed response data", e);
        }
        return outer.find(childTag)
                .orElseThrow(() -> new PaceException("PACE Step " + step
                        + ": missing tag " + String.format("%02X", childTag) + " in response"))
                .value();
    }

    private static byte[] encode(ECPoint point, ECParameterSpec params) {
        return PaceCrypto.encodePoint(point, PaceCrypto.fieldSize(params.getCurve()));
    }

    /**
     * Builder for {@link PaceSession}.
     *
     * <p>The password is given either as one of the passwords of ICAO Doc 9303-11 (MRZ, CAN) or BSI TR-03110
     * (PIN, PUK), from which the builder derives {@code K_pi = KDF(f(pi), 3)} (9.7.3) for the key length of the
     * algorithm, or directly as the password key {@code K_pi} with {@link #password(PasswordRef, byte[])}.</p>
     */
    public static final class Builder {

        private static final SecureRandom RANDOM = new SecureRandom();
        private static final int PASSWORD_KEY_COUNTER = 3;

        private PaceAlgorithm algorithm;
        private PaceParameterId parameterId;
        private PasswordRef passwordRef;
        private byte[] passwordKey;      // K_pi, used as given
        private byte[] passwordEncoding; // K = f(pi), K_pi = KDF(K, 3) is derived by build()
        private boolean includeParameterId = true;
        private EphemeralKeySource ephemeralKeys = params -> PaceCrypto.randomScalar(params.getOrder(), RANDOM);

        private Builder() {
        }

        /**
         * Sets the PACE algorithm.
         *
         * @param algorithm the algorithm
         * @return this builder
         */
        public Builder algorithm(PaceAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            return this;
        }

        /**
         * Sets the standardized domain parameters (ICAO Doc 9303-11 Table 12).
         *
         * @param parameterId the parameter ID
         * @return this builder
         */
        public Builder parameterId(PaceParameterId parameterId) {
            this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
            return this;
        }

        /**
         * Chooses whether MSE:Set AT names the domain parameters in tag {@code 0x84} (default: {@code true}).
         *
         * <p>ICAO Doc 9303-11, 4.4.4.1: the tag is CONDITIONAL, REQUIRED when the chip offers more than one set of
         * domain parameters for PACE. Disable it to send the minimal command of ICAO App. G.1.</p>
         *
         * @param include whether to send tag 0x84
         * @return this builder
         */
        public Builder includeParameterId(boolean include) {
            this.includeParameterId = include;
            return this;
        }

        /**
         * Sets the password key {@code K_pi} directly; it is used exactly as given, whatever the reference.
         *
         * <p>Use this when K_pi is already known, e.g. from a test vector. To start from the password itself use
         * {@link #mrzPassword}, {@link #canPassword}, {@link #pinPassword} or {@link #pukPassword}.</p>
         *
         * @param ref         the password reference sent in MSE:Set AT (tag 0x83)
         * @param passwordKey the password key {@code K_pi = KDF(f(pi), 3)} (ICAO Doc 9303-11, 9.7.3); its length
         *                    must equal the key length of the algorithm
         * @return this builder
         */
        public Builder password(PasswordRef ref, byte[] passwordKey) {
            return setPassword(ref, Objects.requireNonNull(passwordKey, "password").clone(), null);
        }

        /**
         * Uses the MRZ password: {@code f(pi) = SHA-1(MRZ_information)} (ICAO Doc 9303-11, 9.7.3, Table 14).
         *
         * <p>A document number shorter than nine characters is padded with {@code '<'} as in the MRZ
         * (see {@link PaceMrz#mrzInformation}).</p>
         *
         * @param documentNumber the document number
         * @param dateOfBirth    the date of birth (YYMMDD)
         * @param dateOfExpiry   the date of expiry (YYMMDD)
         * @return this builder
         * @throws IllegalArgumentException if a field is not a valid MRZ field
         */
        public Builder mrzPassword(String documentNumber, String dateOfBirth, String dateOfExpiry) {
            return setPassword(PasswordRef.MRZ, null,
                    PaceMrz.encodeMrzPassword(documentNumber, dateOfBirth, dateOfExpiry));
        }

        /**
         * Uses the Card Access Number: {@code f(pi)} is the ISO/IEC 8859-1 encoded CAN (ICAO Doc 9303-11, 9.7.3,
         * Table 14).
         *
         * @param can the CAN as printed on the document, e.g. {@code "123456"}
         * @return this builder
         * @throws IllegalArgumentException if the CAN is empty or not ISO/IEC 8859-1 encodable
         */
        public Builder canPassword(String can) {
            return setPassword(PasswordRef.CAN, null, latin1(can, "can"));
        }

        /**
         * Uses the PIN, encoded like the CAN as an ISO/IEC 8859-1 character string (password encoding of
         * BSI TR-03110).
         *
         * @param pin the PIN
         * @return this builder
         * @throws IllegalArgumentException if the PIN is empty or not ISO/IEC 8859-1 encodable
         */
        public Builder pinPassword(String pin) {
            return setPassword(PasswordRef.PIN, null, latin1(pin, "pin"));
        }

        /**
         * Uses the PUK, encoded like the CAN as an ISO/IEC 8859-1 character string (password encoding of
         * BSI TR-03110).
         *
         * @param puk the PUK
         * @return this builder
         * @throws IllegalArgumentException if the PUK is empty or not ISO/IEC 8859-1 encodable
         */
        public Builder pukPassword(String puk) {
            return setPassword(PasswordRef.PUK, null, latin1(puk, "puk"));
        }

        private Builder setPassword(PasswordRef ref, byte[] key, byte[] encoding) {
            this.passwordRef = Objects.requireNonNull(ref, "passwordRef");
            this.passwordKey = key;
            this.passwordEncoding = encoding;
            return this;
        }

        private static byte[] latin1(String secret, String name) {
            Objects.requireNonNull(secret, name);
            if (secret.isEmpty() || !secret.chars().allMatch(c -> c <= 0xFF)) {
                throw new IllegalArgumentException(name + " must be a non-empty ISO/IEC 8859-1 character string");
            }
            return secret.getBytes(StandardCharsets.ISO_8859_1);
        }

        /**
         * Replaces the source of the terminal's ephemeral private keys (known-answer tests only).
         *
         * @param source the key source
         * @return this builder
         */
        Builder ephemeralKeySource(EphemeralKeySource source) {
            this.ephemeralKeys = Objects.requireNonNull(source, "source");
            return this;
        }

        /**
         * Builds the PACE session, deriving {@code K_pi = KDF(f(pi), 3)} (ICAO Doc 9303-11, 9.7.3) unless the
         * password key was given directly.
         *
         * @return a configured PACE session
         * @throws NullPointerException     if algorithm, parameter ID or password is missing
         * @throws IllegalArgumentException if a password key given directly does not have the algorithm's key length
         */
        public PaceSession build() {
            Objects.requireNonNull(algorithm, "algorithm must be set");
            Objects.requireNonNull(parameterId, "parameterId must be set");
            Objects.requireNonNull(passwordRef, "password must be set");
            if (passwordKey == null) {
                return new PaceSession(this, PaceMrz.kdf(passwordEncoding, PASSWORD_KEY_COUNTER,
                        algorithm.keyLength()));
            }
            if (passwordKey.length != algorithm.keyLength()) {
                throw new IllegalArgumentException("The password key K_pi must have " + algorithm.keyLength()
                        + " bytes for " + algorithm + ", got " + passwordKey.length);
            }
            return new PaceSession(this, passwordKey.clone());
        }
    }
}
