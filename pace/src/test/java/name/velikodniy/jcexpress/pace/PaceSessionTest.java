package name.velikodniy.jcexpress.pace;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMContext;
import name.velikodniy.jcexpress.sm.SMSession;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.Tags;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceSession}.
 */
class PaceSessionTest {

    private static final PaceAlgorithm ALG = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128;
    private static final PaceParameterId PARAM = PaceParameterId.NIST_P256;

    /**
     * Stub session that simulates a card performing PACE.
     * Pre-computes all cryptographic values so the protocol succeeds.
     */
    private static class PaceCardStub implements SmartCardSession {
        final List<APDUResponse> responses = new ArrayList<>();
        final List<byte[]> sentData = new ArrayList<>();
        int callIndex = 0;

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            if (data != null) sentData.add(data.clone());
            if (callIndex < responses.size()) {
                return responses.get(callIndex++);
            }
            return new APDUResponse(new byte[]{(byte) 0x90, 0x00});
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            return send(cla, ins, p1, p2, data, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2) {
            return send(cla, ins, p1, p2, null, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins) {
            return send(cla, ins, 0, 0, null, -1);
        }

        @Override
        public void install(Class<? extends Applet> appletClass) {}
        @Override
        public void install(Class<? extends Applet> appletClass, AID aid) {}
        @Override
        public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {}
        @Override
        public void select(Class<? extends Applet> appletClass) {}
        @Override
        public void select(AID aid) {}
        @Override
        public void reset() {}
        @Override
        public byte[] transmit(byte[] rawApdu) { return new byte[]{(byte) 0x90, 0x00}; }
        @Override
        public void close() {}
    }

    @Test
    void builderShouldCreateSession() {
        byte[] password = new byte[16];
        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .password(PasswordRef.CAN, password)
                .build();
        assertThat(session).isNotNull();
    }

    @Test
    void nullAlgorithmShouldThrow() {
        assertThatThrownBy(() -> PaceSession.builder()
                .parameterId(PARAM)
                .password(PasswordRef.CAN, new byte[16])
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullParameterIdShouldThrow() {
        assertThatThrownBy(() -> PaceSession.builder()
                .algorithm(ALG)
                .password(PasswordRef.CAN, new byte[16])
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullPasswordShouldThrow() {
        assertThatThrownBy(() -> PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void performShouldSendMseSetAtFirst() {
        // Prepare a stub that will fail at step 1 (after MSE:Set AT succeeds)
        PaceCardStub stub = new PaceCardStub();
        // MSE:Set AT → success
        stub.responses.add(new APDUResponse(new byte[]{(byte) 0x90, 0x00}));
        // GA Step 1 → fail
        stub.responses.add(new APDUResponse(new byte[]{(byte) 0x6A, (byte) 0x88}));

        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .password(PasswordRef.CAN, new byte[16])
                .build();

        assertThatThrownBy(() -> session.perform(stub))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("Step 1");

        // MSE:Set AT was sent
        assertThat(stub.sentData).isNotEmpty();
    }

    @Test
    void performShouldExecuteFullProtocol() {
        // Set up a full PACE simulation
        ECParameterSpec ecParams = PARAM.ecParameterSpec();
        int fieldSize = PaceCrypto.fieldSize(ecParams.getCurve());
        byte[] password = new byte[16]; // K_π

        // Card generates a random nonce
        byte[] nonce = new byte[16];
        nonce[0] = 0x42;

        // Card encrypts nonce with K_π
        byte[] encryptedNonce = CryptoUtil.aesCbcEncrypt(password, nonce);

        // Card generates mapping key pair
        KeyPair cardMappingKp = PaceCrypto.generateKeyPair(ecParams);
        ECPublicKey cardMappingPub = (ECPublicKey) cardMappingKp.getPublic();
        ECPrivateKey cardMappingPriv = (ECPrivateKey) cardMappingKp.getPrivate();

        PaceCardStub stub = new PaceCardStub() {
            private byte[] termMappingPub;
            private ECPoint mappedG;
            private byte[] termEphPub;
            private byte[] sessionMacKey;

            @Override
            public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
                callIndex++;
                if (data != null) sentData.add(data.clone());

                if (ins == 0x22) {
                    // MSE:Set AT
                    return new APDUResponse(new byte[]{(byte) 0x90, 0x00});
                }

                if (ins == 0x86) {
                    return handleGA(data);
                }

                return new APDUResponse(new byte[]{(byte) 0x6D, 0x00});
            }

            private APDUResponse handleGA(byte[] data) {
                int step = determineStep(data);

                return switch (step) {
                    case 1 -> {
                        // Return encrypted nonce
                        byte[] resp = TLVBuilder.create()
                                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                                        b.add(Tags.PACE_NONCE, encryptedNonce))
                                .build();
                        yield new APDUResponse(resp, 0x9000);
                    }
                    case 2 -> {
                        // Extract terminal mapping pubkey, compute ECDH, return card mapping pubkey
                        termMappingPub = extractPubKey(data, Tags.PACE_MAP_DATA);
                        ECPoint termPoint = PaceCrypto.decodePoint(termMappingPub, ecParams.getCurve());

                        // Card computes ECDH shared secret
                        byte[] sharedSecret = PaceCrypto.ecdh(cardMappingPriv, termPoint, ecParams);
                        ECPoint sharedPoint = PaceCrypto.scalarMultiply(
                                new BigInteger(1, sharedSecret),
                                ecParams.getGenerator(), ecParams.getCurve());

                        // Generic Mapping
                        BigInteger s = new BigInteger(1, nonce);
                        mappedG = PaceCrypto.genericMapping(s, ecParams.getGenerator(),
                                sharedPoint, ecParams.getCurve());

                        byte[] cardPubEncoded = PaceCrypto.encodePoint(cardMappingPub.getW(), fieldSize);
                        byte[] resp = TLVBuilder.create()
                                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                                        b.add(Tags.PACE_MAP_RESPONSE, cardPubEncoded))
                                .build();
                        yield new APDUResponse(resp, 0x9000);
                    }
                    case 3 -> {
                        // Extract terminal ephemeral pubkey, generate card's, compute session keys
                        termEphPub = extractPubKey(data, Tags.PACE_EPHEMERAL_PK);

                        KeyPair cardEphKp = PaceCrypto.generateKeyPairOnGenerator(mappedG, ecParams);
                        ECPublicKey cardEphPub = (ECPublicKey) cardEphKp.getPublic();
                        ECPrivateKey cardEphPriv = (ECPrivateKey) cardEphKp.getPrivate();

                        ECPoint termEphPoint = PaceCrypto.decodePoint(termEphPub, ecParams.getCurve());
                        byte[] sharedFinal = PaceCrypto.ecdh(cardEphPriv, termEphPoint, ecParams);
                        var keys = PaceMrz.deriveKeys(sharedFinal, ALG.keyLength());
                        sessionMacKey = keys.macKey();

                        byte[] cardPubEncoded = PaceCrypto.encodePoint(cardEphPub.getW(), fieldSize);
                        byte[] resp = TLVBuilder.create()
                                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                                        b.add(Tags.PACE_EPHEMERAL_PK_RESPONSE, cardPubEncoded))
                                .build();
                        yield new APDUResponse(resp, 0x9000);
                    }
                    case 4 -> {
                        // Verify terminal token, return card token
                        byte[] oidBytes = ALG.oidBytes();
                        byte[] cardToken = PaceCrypto.authToken(sessionMacKey, oidBytes, termEphPub);

                        byte[] resp = TLVBuilder.create()
                                .addConstructed(Tags.DYNAMIC_AUTH_DATA, b ->
                                        b.add(Tags.PACE_AUTH_TOKEN_RESPONSE, cardToken))
                                .build();
                        yield new APDUResponse(resp, 0x9000);
                    }
                    default -> new APDUResponse(new byte[]{(byte) 0x69, (byte) 0x85});
                };
            }

            private int determineStep(byte[] data) {
                if (data == null || data.length <= 4) return 1; // empty 7C 00
                // Parse TLV properly to find the tag inside 7C
                var parsed = name.velikodniy.jcexpress.tlv.TLVParser.parse(data);
                var outer = parsed.find(Tags.DYNAMIC_AUTH_DATA);
                if (outer.isEmpty()) return 1;
                var children = outer.get().children();
                if (children.contains(Tags.PACE_MAP_DATA)) return 2;
                if (children.contains(Tags.PACE_EPHEMERAL_PK)) return 3;
                if (children.contains(Tags.PACE_AUTH_TOKEN)) return 4;
                return 1;
            }

            private byte[] extractPubKey(byte[] data, int tag) {
                var parsed = name.velikodniy.jcexpress.tlv.TLVParser.parse(data);
                var outer = parsed.find(Tags.DYNAMIC_AUTH_DATA);
                if (outer.isPresent()) {
                    var child = outer.get().find(tag);
                    if (child.isPresent()) return child.get().value();
                }
                throw new PaceException("Missing tag " + String.format("%02X", tag));
            }
        };

        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .password(PasswordRef.CAN, password)
                .build();

        PaceResult result = session.perform(stub);

        assertThat(result).isNotNull();
        assertThat(result.encKey()).hasSize(16);
        assertThat(result.macKey()).hasSize(16);
        assertThat(result.cardToken()).hasSize(8);
        assertThat(result.termToken()).hasSize(8);
    }

    @Test
    void resultShouldCreateSMContext() {
        PaceResult result = new PaceResult(
                new byte[16], new byte[16], new byte[8], new byte[8]);
        SMContext ctx = result.toSMContext();
        assertThat(ctx).isNotNull();
        assertThat(ctx.encKey()).hasSize(16);
        assertThat(ctx.macKey()).hasSize(16);
    }

    @Test
    void resultShouldCreateSMSession() {
        PaceResult result = new PaceResult(
                new byte[16], new byte[16], new byte[8], new byte[8]);
        PaceCardStub stub = new PaceCardStub();
        SMSession smSession = result.toSMSession(stub);
        assertThat(smSession).isNotNull();
        assertThat(smSession.delegate()).isSameAs(stub);
    }

    @Test
    void mrzPasswordShouldDeriveFromFields() {
        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .mrzPassword("L898902C<", "690806", "940623")
                .build();
        assertThat(session).isNotNull();
    }
}
