package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMKeys;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.tlv.Tags;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;
import java.util.Objects;

/**
 * PACE protocol session (BSI TR-03110 / ICAO 9303 Part 11).
 *
 * <p>Executes the full PACE handshake: MSE:Set AT followed by four steps of
 * GENERAL AUTHENTICATE. On success, returns a {@link PaceResult} containing
 * session keys for Secure Messaging.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * PaceResult result = PaceSession.builder()
 *     .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
 *     .parameterId(PaceParameterId.BRAINPOOL_P256R1)
 *     .mrzPassword("L898902C", "690806", "940623")
 *     .build()
 *     .perform(session);
 *
 * SMSession smSession = result.toSMSession(session);
 * </pre>
 */
public final class PaceSession {

    private final PaceAlgorithm algorithm;
    private final PaceParameterId parameterId;
    private final PasswordRef passwordRef;
    private final byte[] password; // K_π (derived password key)

    private PaceSession(PaceAlgorithm algorithm, PaceParameterId parameterId,
                        PasswordRef passwordRef, byte[] password) {
        this.algorithm = algorithm;
        this.parameterId = parameterId;
        this.passwordRef = passwordRef;
        this.password = password;
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
     * @param session the smart card session
     * @return the PACE result with session keys
     * @throws PaceException if the protocol fails
     */
    public PaceResult perform(SmartCardSession session) {
        ECParameterSpec ecParams = parameterId.ecParameterSpec();
        int fieldSize = PaceCrypto.fieldSize(ecParams.getCurve());
        int keyLength = algorithm.keyLength();

        // Step 0: MSE:Set AT
        sendMseSetAt(session);

        // Step 1: Get encrypted nonce
        byte[] encryptedNonce = gaStep1(session);
        byte[] nonce = CryptoUtil.aesCbcDecrypt(password, encryptedNonce);
        BigInteger s = new BigInteger(1, nonce);

        // Step 2: Map nonce (Generic Mapping via ECDH)
        KeyPair mappingKeyPair = PaceCrypto.generateKeyPair(ecParams);
        ECPublicKey mappingPub = (ECPublicKey) mappingKeyPair.getPublic();
        ECPrivateKey mappingPriv = (ECPrivateKey) mappingKeyPair.getPrivate();

        byte[] mappingPubEncoded = PaceCrypto.encodePoint(mappingPub.getW(), fieldSize);
        byte[] cardMappingPubEncoded = gaStep2(session, mappingPubEncoded);

        ECPoint cardMappingPoint = PaceCrypto.decodePoint(cardMappingPubEncoded, ecParams.getCurve());
        byte[] sharedSecret = PaceCrypto.ecdh(mappingPriv, cardMappingPoint, ecParams);
        ECPoint sharedPoint = PaceCrypto.scalarMultiply(
                new BigInteger(1, sharedSecret),
                ecParams.getGenerator(),
                ecParams.getCurve());

        // G' = s * G + H
        ECPoint mappedGenerator = PaceCrypto.genericMapping(s, ecParams.getGenerator(),
                sharedPoint, ecParams.getCurve());

        // Step 3: Key agreement on mapped generator G'
        KeyPair ephKeyPair = PaceCrypto.generateKeyPairOnGenerator(mappedGenerator, ecParams);
        ECPublicKey ephPub = (ECPublicKey) ephKeyPair.getPublic();
        ECPrivateKey ephPriv = (ECPrivateKey) ephKeyPair.getPrivate();

        byte[] ephPubEncoded = PaceCrypto.encodePoint(ephPub.getW(), fieldSize);
        byte[] cardEphPubEncoded = gaStep3(session, ephPubEncoded);

        ECPoint cardEphPoint = PaceCrypto.decodePoint(cardEphPubEncoded, ecParams.getCurve());
        byte[] sharedSecretFinal = PaceCrypto.ecdh(ephPriv, cardEphPoint, ecParams);

        // Derive session keys
        byte[] kSeed = sharedSecretFinal;
        SMKeys sessionKeys = PaceMrz.deriveKeys(kSeed, keyLength);

        // Step 4: Mutual authentication
        byte[] oidBytes = algorithm.oidBytes();
        byte[] termToken = PaceCrypto.authToken(sessionKeys.macKey(), oidBytes, cardEphPubEncoded);
        byte[] cardToken = gaStep4(session, termToken);

        // Verify card's token
        byte[] expectedCardToken = PaceCrypto.authToken(sessionKeys.macKey(), oidBytes, ephPubEncoded);
        if (!Arrays.equals(cardToken, expectedCardToken)) {
            throw new PaceException("PACE mutual authentication failed: card token mismatch");
        }

        return new PaceResult(sessionKeys.encKey(), sessionKeys.macKey(), cardToken, termToken);
    }

    /**
     * Sends MSE:Set AT to select PACE algorithm and parameters.
     */
    private void sendMseSetAt(SmartCardSession session) {
        byte[] oidBytes = algorithm.oidBytes();
        byte[] data = TLVBuilder.create()
                .add(0x80, oidBytes)
                .add(0x83, new byte[]{(byte) passwordRef.ref()})
                .add(0x84, new byte[]{(byte) parameterId.id()})
                .build();

        APDUResponse response = session.send(0x00, 0x22, 0xC1, 0xA4, data);
        if (!response.isSuccess()) {
            throw new PaceException("MSE:Set AT failed: SW=" + String.format("%04X", response.sw()));
        }
    }

    /**
     * GENERAL AUTHENTICATE Step 1: get encrypted nonce.
     *
     * @return the encrypted nonce bytes
     */
    private byte[] gaStep1(SmartCardSession session) {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b -> {})
                .build();

        APDUResponse response = session.send(0x10, 0x86, 0x00, 0x00, data, 0);
        checkGaResponse(response, 1);

        return extractChildValue(response.data(), Tags.PACE_NONCE, 1);
    }

    /**
     * GENERAL AUTHENTICATE Step 2: map nonce (send terminal mapping public key).
     *
     * @return the card's mapping public key
     */
    private byte[] gaStep2(SmartCardSession session, byte[] terminalPubKey) {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                        b.add(Tags.PACE_MAP_DATA, terminalPubKey))
                .build();

        APDUResponse response = session.send(0x10, 0x86, 0x00, 0x00, data, 0);
        checkGaResponse(response, 2);

        return extractChildValue(response.data(), Tags.PACE_MAP_RESPONSE, 2);
    }

    /**
     * GENERAL AUTHENTICATE Step 3: key agreement (send ephemeral public key on G').
     *
     * @return the card's ephemeral public key
     */
    private byte[] gaStep3(SmartCardSession session, byte[] terminalEphPubKey) {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                        b.add(Tags.PACE_EPHEMERAL_PK, terminalEphPubKey))
                .build();

        APDUResponse response = session.send(0x10, 0x86, 0x00, 0x00, data, 0);
        checkGaResponse(response, 3);

        return extractChildValue(response.data(), Tags.PACE_EPHEMERAL_PK_RESPONSE, 3);
    }

    /**
     * GENERAL AUTHENTICATE Step 4: mutual authentication (send auth token).
     *
     * @return the card's authentication token
     */
    private byte[] gaStep4(SmartCardSession session, byte[] authToken) {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                        b.add(Tags.PACE_AUTH_TOKEN, authToken))
                .build();

        // Last step uses CLA=0x00 (no command chaining)
        APDUResponse response = session.send(0x00, 0x86, 0x00, 0x00, data, 0);
        checkGaResponse(response, 4);

        return extractChildValue(response.data(), Tags.PACE_AUTH_TOKEN_RESPONSE, 4);
    }

    private void checkGaResponse(APDUResponse response, int step) {
        if (!response.isSuccess()) {
            throw new PaceException("GENERAL AUTHENTICATE Step " + step
                    + " failed: SW=" + String.format("%04X", response.sw()));
        }
    }

    /**
     * Extracts a child TLV value from a Dynamic Authentication Data (0x7C) response.
     */
    private byte[] extractChildValue(byte[] responseData, int childTag, int step) {
        TLV outer = TLVParser.parse(responseData).find(Tags.DYNAMIC_AUTH_DATA)
                .orElseThrow(() -> new PaceException("PACE Step " + step
                        + ": missing Dynamic Authentication Data (0x7C)"));

        return outer.find(childTag)
                .orElseThrow(() -> new PaceException("PACE Step " + step
                        + ": missing tag " + String.format("%02X", childTag) + " in response"))
                .value();
    }

    /**
     * Builder for {@link PaceSession}.
     */
    public static final class Builder {

        private PaceAlgorithm algorithm;
        private PaceParameterId parameterId;
        private PasswordRef passwordRef;
        private byte[] password;

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
         * Sets the domain parameter ID (EC curve).
         *
         * @param parameterId the parameter ID
         * @return this builder
         */
        public Builder parameterId(PaceParameterId parameterId) {
            this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
            return this;
        }

        /**
         * Sets the password directly.
         *
         * @param ref      the password reference type
         * @param password the raw password key K_&pi;
         * @return this builder
         */
        public Builder password(PasswordRef ref, byte[] password) {
            this.passwordRef = Objects.requireNonNull(ref, "passwordRef");
            this.password = Objects.requireNonNull(password, "password").clone();
            return this;
        }

        /**
         * Derives the password from MRZ fields.
         *
         * <p>Computes K_seed from MRZ fields, then derives K_&pi; using KDF.</p>
         *
         * @param documentNumber the document number
         * @param dateOfBirth    the date of birth (YYMMDD)
         * @param dateOfExpiry   the date of expiry (YYMMDD)
         * @return this builder
         */
        public Builder mrzPassword(String documentNumber, String dateOfBirth, String dateOfExpiry) {
            this.passwordRef = PasswordRef.MRZ;
            byte[] kSeed = PaceMrz.computeKSeed(documentNumber, dateOfBirth, dateOfExpiry);
            // K_π = KDF(K_seed, 3, keyLength) — password derivation uses counter 3
            // For PACE with MRZ, the password is SHA-1(MRZ composite) truncated to key length
            // Actually per ICAO 9303: K_π = SHA-1(K_seed) first 16 bytes for AES-128
            // We store K_seed and derive K_π at build() when algorithm is known
            this.password = kSeed;
            return this;
        }

        /**
         * Builds the PACE session.
         *
         * @return a configured PACE session
         * @throws IllegalStateException if required fields are missing
         */
        public PaceSession build() {
            Objects.requireNonNull(algorithm, "algorithm must be set");
            Objects.requireNonNull(parameterId, "parameterId must be set");
            Objects.requireNonNull(passwordRef, "password must be set");
            Objects.requireNonNull(password, "password must be set");

            // Derive K_π from K_seed if MRZ was used
            byte[] kPi;
            if (passwordRef == PasswordRef.MRZ) {
                kPi = PaceMrz.kdf(password, 3, algorithm.keyLength());
            } else {
                kPi = password;
            }

            return new PaceSession(algorithm, parameterId, passwordRef, kPi);
        }
    }
}
