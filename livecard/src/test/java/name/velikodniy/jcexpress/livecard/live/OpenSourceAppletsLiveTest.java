package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import name.velikodniy.jcexpress.livecard.thirdparty.ThirdPartyApplet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-OSS: two real-world open-source applets on the card, built from their upstream sources (git submodules under
 * {@code livecard/third_party}, see {@link ThirdPartyApplet}), converted by this project's converter and deployed
 * like the test applets; signatures made on the card are verified on the host. The flows are the ones proven on
 * the validated card. A case is aborted, naming the command that initializes it, when its submodule is not
 * initialized, and when the submodule is not at the pinned commit or has local changes (only the pinned upstream
 * code was validated).
 */
@LiveCardTest
@Order(6)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenSourceAppletsLiveTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final byte[] MESSAGE = "javacard-express on a real card".getBytes(StandardCharsets.UTF_8);
    /** PivApplet's default card management key (slot 9B, 3DES). */
    private static final byte[] ADMIN_KEY = HEX.parseHex("010203040506070801020304050607080102030405060708");
    /** SHA-256 DigestInfo prefix (RFC 8017 9.2). */
    private static final String SHA256_DIGEST_INFO = "3031300D060960864801650304020105000420";

    /** The applet this test deployed (JUnit creates one instance per test). */
    private ThirdPartyApplet deployed;

    /** Each applet is deleted after its case, so that the next one finds the card's memory free. */
    @AfterEach
    void deleteTheApplet(LiveCard card) {
        if (deployed != null) {
            assertThat(card.delete(deployed.packageAid(card.config()), true).sw()).isEqualTo(0x9000);
        }
    }

    /** PivApplet (NIST SP 800-73-4): PIN, 9B mutual authentication, P-256 key generation and signature. */
    @Test
    @Order(1)
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void pivApplet(LiveCard card) throws GeneralSecurityException {
        ThirdPartyApplet piv = ThirdPartyApplet.PIV;
        piv.requireInitialized();
        piv.requirePinnedCommit();
        card.transcript().note("deploying " + piv.describe());
        card.deploy(piv.pkg(card.config()));
        deployed = piv;
        UnaryOperator<String> tx = command -> HEX.formatHex(card.session().transmit(HEX.parseHex(command)));

        assertThat(tx.apply(select(piv.moduleAid(card.config())))).as("SELECT: application property template")
                .startsWith("61").endsWith("9000");
        assertThat(tx.apply(apdu("00200080", "313233343536FFFF", false))).as("VERIFY PIN 123456 (default)")
                .isEqualTo("9000");
        mutualAuthentication(tx);
        byte[] point = generateP256(tx);
        signDigestWithP256(tx, point);
    }

    /** SmartPGP (OpenPGP card 3.4): PW3, RSA-2048 key generation, PW1, digital signature. */
    @Test
    @Order(2)
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void smartPgp(LiveCard card) throws GeneralSecurityException {
        ThirdPartyApplet pgp = ThirdPartyApplet.SMART_PGP;
        pgp.requireInitialized();
        pgp.requirePinnedCommit();
        card.transcript().note("deploying " + pgp.describe());
        card.deploy(pgp.pkg(card.config()));
        deployed = pgp;
        APDUSequence sequence = APDUSequence.on(card.session());
        UnaryOperator<String> tx = command -> hex(sequence.transmit(HEX.parseHex(command)));

        assertThat(tx.apply(select(pgp.moduleAid(card.config())))).as("SELECT").endsWith("9000");
        assertThat(tx.apply("00CA006E00")).as("GET DATA 6E: signature key RSA-2048, e = 65537 (C1 06 010800001103)")
                .contains("C106010800001103").endsWith("9000");
        assertThat(tx.apply(apdu("00200083", "3132333435363738", false))).as("VERIFY PW3 12345678 (admin)")
                .isEqualTo("9000");
        PublicKey key = generateRsa2048(tx, card);
        assertThat(tx.apply(apdu("00200081", "313233343536", false))).as("VERIFY PW1 123456 (signing)")
                .isEqualTo("9000");
        signDigestInfoWithRsa(tx, key);
        assertThat(tx.apply("00CA00C400")).as("GET DATA C4: PIN status bytes").endsWith("9000");
    }

    /** GENERAL AUTHENTICATE with the 9B key: decrypt the card's witness, then check its encryption of ours. */
    private static void mutualAuthentication(UnaryOperator<String> tx) throws GeneralSecurityException {
        String witness = tx.apply(apdu("0087039B", "7C028000", true));
        assertThat(witness).as("GENERAL AUTHENTICATE 9B: card witness").startsWith("7C0A8008").endsWith("9000");
        byte[] decrypted = des3(HEX.parseHex(witness.substring(8, 24)), Cipher.DECRYPT_MODE);
        byte[] challenge = new byte[8];
        new SecureRandom().nextBytes(challenge);
        String answer = tx.apply(apdu("0087039B", "7C14" + "8008" + HEX.formatHex(decrypted) + "8108"
                + HEX.formatHex(challenge), true));
        assertThat(answer).as("GENERAL AUTHENTICATE 9B: mutual authentication").startsWith("7C0A8208")
                .endsWith("9000");
        assertThat(answer.substring(8, 24)).as("the card's encryption of the host challenge")
                .isEqualTo(HEX.formatHex(des3(challenge, Cipher.ENCRYPT_MODE)));
    }

    /** GENERATE ASYMMETRIC KEY PAIR, EC P-256, slot 9A; returns the uncompressed point without its '04'. */
    private static byte[] generateP256(UnaryOperator<String> tx) {
        String generated = tx.apply(apdu("0047009A", "AC03800111", true));
        assertThat(generated).as("GENERATE EC P-256 key pair in slot 9A").startsWith("7F49").endsWith("9000");
        int at = generated.indexOf("864104");
        assertThat(at).as("public point (tag 86, 65 bytes, uncompressed)").isPositive();
        return HEX.parseHex(generated.substring(at + 6, at + 6 + 128));
    }

    /** GENERAL AUTHENTICATE 9A over a SHA-256 digest; the ECDSA signature must verify with the card's key. */
    private static void signDigestWithP256(UnaryOperator<String> tx, byte[] point) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(MESSAGE);
        String signed = tx.apply(apdu("0087119A", "7C24" + "8200" + "8120" + HEX.formatHex(digest), true));
        assertThat(signed).as("GENERAL AUTHENTICATE 9A: sign a SHA-256 digest").startsWith("7C").endsWith("9000");
        assertThat(signed.substring(4, 6)).as("response tag 82").isEqualTo("82");
        int length = Integer.parseInt(signed.substring(6, 8), 16);
        Signature verifier = Signature.getInstance("NONEwithECDSA");
        verifier.initVerify(p256(point));
        verifier.update(digest);
        assertThat(verifier.verify(HEX.parseHex(signed.substring(8, 8 + 2 * length))))
                .as("ECDSA signature verifies on the host with the card's public key").isTrue();
    }

    /** GENERATE ASYMMETRIC KEY PAIR for the signature key; the 270-byte answer comes through '61XX'. */
    private static PublicKey generateRsa2048(UnaryOperator<String> tx, LiveCard card) throws GeneralSecurityException {
        long start = System.nanoTime();
        String generated = tx.apply("0047800002B60000");
        card.transcript().note("SmartPGP: RSA-2048 key generation took " + (System.nanoTime() - start) / 1_000_000
                + " ms");
        assertThat(generated).as("GENERATE RSA-2048 signature key").startsWith("7F49").endsWith("9000");
        int modulus = generated.indexOf("81820100");
        int exponent = generated.indexOf("8203", modulus + 8 + 512);
        assertThat(modulus).as("modulus (tag 81, 256 bytes)").isPositive();
        assertThat(exponent).as("public exponent (tag 82, 3 bytes)").isPositive();
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, HEX.parseHex(generated.substring(modulus + 8, modulus + 8 + 512))),
                new BigInteger(1, HEX.parseHex(generated.substring(exponent + 4, exponent + 10)))));
    }

    /** PSO: COMPUTE DIGITAL SIGNATURE over a SHA-256 DigestInfo; PKCS#1 v1.5 verification on the host. */
    private static void signDigestInfoWithRsa(UnaryOperator<String> tx, PublicKey key)
            throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(MESSAGE);
        String signed = tx.apply(apdu("002A9E9A", SHA256_DIGEST_INFO + HEX.formatHex(digest), true));
        assertThat(signed).as("PSO: COMPUTE DIGITAL SIGNATURE (256 bytes)").hasSize(2 * 256 + 4).endsWith("9000");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(MESSAGE);
        assertThat(verifier.verify(HEX.parseHex(signed.substring(0, 512))))
                .as("RSA PKCS#1 v1.5 signature verifies on the host (SHA256withRSA)").isTrue();
    }

    private static PublicKey p256(byte[] point) throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOf(point, 32)),
                new BigInteger(1, Arrays.copyOfRange(point, 32, 64)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w,
                parameters.getParameterSpec(ECParameterSpec.class)));
    }

    private static byte[] des3(byte[] block, int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(ADMIN_KEY, "DESede"));
        return cipher.doFinal(block);
    }

    /** SELECT by DF name with Le '00'. */
    private static String select(AID aid) {
        return String.format("00A40400%02X", aid.toBytes().length) + aid.toHex() + "00";
    }

    /** A short case 3 or 4 command: header, Lc and data (and Le '00'). */
    private static String apdu(String header, String data, boolean le) {
        return header + String.format("%02X", data.length() / 2) + data + (le ? "00" : "");
    }

    private static String hex(APDUResponse response) {
        return HEX.formatHex(response.data()) + String.format("%04X", response.sw());
    }
}
