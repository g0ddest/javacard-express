package name.velikodniy.jcexpress.livecard.live;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expected values of {@link CryptoApiLiveTest} are what the JDK computes for the CryptoApplet's inputs, so a
 * difference on the card is the card's (or the converter's), not a typo in the test.
 */
class CryptoKnownAnswersTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final byte[] AES_KEY = HEX.parseHex("000102030405060708090A0B0C0D0E0F");
    private static final byte[] PLAINTEXT = HEX.parseHex("00112233445566778899AABBCCDDEEFF");
    private static final byte[] ABC = "abc".getBytes(StandardCharsets.US_ASCII);

    @Test
    void everyKnownAnswerIsWhatTheJdkComputes() throws GeneralSecurityException {
        Map<Integer, String> jdk = Map.of(
                0x10, aes("AES/ECB/NoPadding", PLAINTEXT),
                0x11, aes("AES/CBC/NoPadding", PLAINTEXT),
                0x12, digest("SHA-256"),
                0x13, digest("SHA-1"),
                0x14, tripleDes(),
                0x18, hmac(),
                0x19, aes("AES/CBC/NoPadding", HEX.parseHex("61626380000000000000000000000000")));

        assertThat(CryptoApiLiveTest.KNOWN_ANSWERS).hasSize(jdk.size()).allSatisfy(answer ->
                assertThat(answer.expected()).as(answer.algorithm()).isEqualTo(jdk.get(answer.ins())));
    }

    private static String aes(String transformation, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation);
        SecretKeySpec key = new SecretKeySpec(AES_KEY, "AES");
        if (transformation.contains("CBC")) {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[16]));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        }
        return HEX.formatHex(cipher.doFinal(input));
    }

    private static String digest(String algorithm) throws GeneralSecurityException {
        return HEX.formatHex(MessageDigest.getInstance(algorithm).digest(ABC));
    }

    /** Two-key Triple DES: K1 || K2 || K1. */
    private static String tripleDes() throws GeneralSecurityException {
        byte[] key = HEX.parseHex("0123456789ABCDEFFEDCBA9876543210" + "0123456789ABCDEF");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DESede"));
        return HEX.formatHex(cipher.doFinal(HEX.parseHex("0011223344556677")));
    }

    private static String hmac() throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("Jefe".getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        return HEX.formatHex(mac.doFinal("what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII)));
    }
}
