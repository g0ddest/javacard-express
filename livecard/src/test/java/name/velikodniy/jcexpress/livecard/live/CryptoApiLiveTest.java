package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * LC-CRYPTO: the card's crypto API through the converted CryptoApplet (port of real-card step 4).
 *
 * <p>Each case first asks the applet to create the algorithm's objects (INS '1F'): a card that answers '6F03'
 * there (CryptoException.NO_SUCH_ALGORITHM) does not support the algorithm, and the case is reported as aborted
 * ("not supported by this card"), not failed; every other error is a failure. Then deterministic algorithms must
 * give the published known answer, on the card and on jCardSim; random numbers must be fresh; ECDSA and RSA
 * signatures made on the card must verify on the host (JCA).</p>
 */
@LiveCardTest
@Order(7)
class CryptoApiLiveTest {

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.US_ASCII);
    private static final int INS_ALLOCATE = 0x1F;
    /** The CryptoApplet's answer when creating an algorithm's objects throws CryptoException.NO_SUCH_ALGORITHM. */
    static final int SW_NO_SUCH_ALGORITHM = 0x6F03;
    private static final String[] CRYPTO_REASONS = {"", "ILLEGAL_VALUE", "UNINITIALIZED_KEY", "NO_SUCH_ALGORITHM",
        "INVALID_INIT", "ILLEGAL_USE"};
    private static EmbeddedSession simulator;

    /**
     * A deterministic probe command and its published answer.
     *
     * @param algorithm what the command computes, and the source of the answer
     * @param ins       the CryptoApplet instruction
     * @param expected  the expected response data, hex
     */
    record KnownAnswer(String algorithm, int ins, String expected) {
        @Override
        public String toString() {
            return algorithm;
        }
    }

    /** The known answers; {@code CryptoKnownAnswersTest} recomputes each with the JDK. */
    static final List<KnownAnswer> KNOWN_ANSWERS = List.of(
            new KnownAnswer("AES-128 ECB (FIPS-197 C.1)", 0x10, "69C4E0D86A7B0430D8CDB78070B4C55A"),
            new KnownAnswer("AES-128 CBC with zero IV (one block)", 0x11, "69C4E0D86A7B0430D8CDB78070B4C55A"),
            new KnownAnswer("SHA-256 of abc (FIPS 180-4)", 0x12,
                    "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"),
            new KnownAnswer("SHA-1 of abc (FIPS 180-4)", 0x13, "A9993E364706816ABA3E25717850C26C9CD0D89D"),
            new KnownAnswer("2-key 3DES ECB", 0x14, "31A7364CAC91CA39"),
            new KnownAnswer("HMAC-SHA-256 (RFC 4231 test case 2)", 0x18,
                    "5BDCC146BF60754E6A042426089575C75A003F089D2739839DEC58B964EC3843"),
            new KnownAnswer("AES-128 CBC ISO 9797-1 M2 of abc", 0x19, "DBD0B134C556C3779D5F113FD277B3D8"));

    @BeforeAll
    static void deploy(LiveCard card) {
        card.deploy(TestApplet.CRYPTO.pkg(card.config()));
        simulator = new EmbeddedSession();
        simulator.install(TestApplet.CRYPTO.load(), TestApplet.CRYPTO.moduleAid(card.config()));
    }

    @AfterAll
    static void closeSimulator() {
        if (simulator != null) {
            simulator.close();
        }
    }

    @BeforeEach
    void select(LiveCard card) {
        card.session().select(TestApplet.CRYPTO.moduleAid(card.config()));
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("KNOWN_ANSWERS")
    void knownAnswers(KnownAnswer answer, LiveCard card) {
        requireSupported(card.session(), answer.ins(), answer.algorithm());

        assertThat(Hex.encode(call(card.session(), answer.ins(), 0))).as("card").isEqualTo(answer.expected());
        requireSupported(simulator, answer.ins(), answer.algorithm());
        assertThat(Hex.encode(call(simulator, answer.ins(), 0))).as("jCardSim").isEqualTo(answer.expected());
    }

    @Test
    void secureRandomGivesFreshBytes(LiveCard card) {
        requireSupported(card.session(), 0x15, "RandomData.ALG_SECURE_RANDOM");
        byte[] first = call(card.session(), 0x15, 0);
        byte[] second = call(card.session(), 0x15, 0);

        assertThat(first).hasSize(16).isNotEqualTo(second).isNotEqualTo(new byte[16]);
    }

    @Test
    void ecdsaP256SignatureVerifiesOnTheHost(LiveCard card) throws GeneralSecurityException {
        requireSupported(card.session(), 0x16, "ECDSA P-256 with SHA-256");
        byte[] response = call(card.session(), 0x16, 0);
        int length = response[65] & 0xFF;

        assertThat(response).hasSize(66 + length);
        assertThat(response[0]).as("uncompressed point").isEqualTo((byte) 0x04);
        PublicKey key = p256PublicKey(Arrays.copyOf(response, 65));
        assertThat(verify("SHA256withECDSA", key, Arrays.copyOfRange(response, 66, 66 + length))).isTrue();
    }

    /** Key generation takes about 20 s on the validated card. */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void rsa2048SignatureVerifiesOnTheHost(LiveCard card) throws GeneralSecurityException {
        requireSupported(card.session(), 0x17, "RSA-2048 with SHA-256 PKCS#1");
        assertThat(Hex.encode(call(card.session(), 0x17, 0))).as("modulus length").isEqualTo("0100");
        byte[] modulus = call(card.session(), 0x17, 1);
        byte[] signature = call(card.session(), 0x17, 2);
        byte[] exponent = call(card.session(), 0x17, 3);

        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent)));
        assertThat(new BigInteger(1, modulus).bitLength()).isEqualTo(2048);
        assertThat(verify("SHA256withRSA", key, signature)).isTrue();
    }

    /**
     * Creates the algorithm's objects; only NO_SUCH_ALGORITHM ('6F03') means the card does not support it, any
     * other error fails.
     */
    static void requireSupported(SmartCardSession session, int ins, String algorithm) {
        APDUResponse response = session.send(0x80, INS_ALLOCATE, ins, 0x00);
        if (response.sw() == SW_NO_SUCH_ALGORITHM) {
            abort("not supported by this card: " + algorithm + " (CryptoException.NO_SUCH_ALGORITHM when creating"
                    + " its objects)");
        }
        assertThat(response.sw()).as("creating the objects of %s: %s", algorithm, explain(response.sw()))
                .isEqualTo(0x9000);
    }

    /** What the CryptoApplet's answer to INS '1F' means. */
    static String explain(int sw) {
        int reason = sw & 0xFF;
        if (sw == 0x6F00) {
            return "SW 6F00: an exception that is not a CryptoException (memory full, a wrong API constant, a"
                    + " converter defect?)";
        }
        if ((sw & 0xFF00) == 0x6F00 && reason < CRYPTO_REASONS.length) {
            return String.format("SW %04X: CryptoException reason %d (%s)", sw, reason, CRYPTO_REASONS[reason]);
        }
        return String.format("SW %04X", sw);
    }

    /** Sends a probe command and requires '9000'. */
    static byte[] call(SmartCardSession session, int ins, int p1) {
        APDUResponse response = session.send(0x80, ins, p1, 0x00, null, 256);
        assertThat(response.sw()).as("SW of INS %02X P1 %02X", ins, p1).isEqualTo(0x9000);
        return response.data();
    }

    static PublicKey p256PublicKey(byte[] point) throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, curve));
    }

    static boolean verify(String algorithm, PublicKey key, byte[] signature) throws GeneralSecurityException {
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(key);
        verifier.update(ABC);
        return verifier.verify(signature);
    }
}
