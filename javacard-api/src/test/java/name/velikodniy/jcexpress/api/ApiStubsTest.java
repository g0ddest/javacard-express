package name.velikodniy.jcexpress.api;

import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.APDU;
import javacard.framework.AID;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.framework.OwnerPIN;
import javacard.security.KeyBuilder;
import javacard.security.MessageDigest;
import javacard.security.Signature;
import javacard.security.RandomData;
import javacard.security.KeyPair;
import javacard.security.KeyAgreement;
import javacardx.crypto.Cipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the API stubs provide correct class/interface structure
 * for typical JavaCard applet development.
 */
@SuppressWarnings("java:S3415") // constant assertions test API values
class ApiStubsTest {

    @Test
    void helloWorldAppletExtendsApplet() {
        assertThat(Applet.class).isAssignableFrom(HelloWorldApplet.class);
    }

    @Test
    void iso7816ConstantsExist() {
        assertThat(ISO7816.SW_NO_ERROR).isEqualTo((short) 0x9000);
        assertThat(ISO7816.SW_INS_NOT_SUPPORTED).isEqualTo((short) 0x6D00);
        assertThat(ISO7816.SW_CLA_NOT_SUPPORTED).isEqualTo((short) 0x6E00);
        assertThat(ISO7816.SW_FILE_NOT_FOUND).isEqualTo((short) 0x6A82);
        assertThat(ISO7816.SW_WRONG_LENGTH).isEqualTo((short) 0x6700);
        assertThat(ISO7816.OFFSET_CLA).isEqualTo((byte) 0);
        assertThat(ISO7816.OFFSET_INS).isEqualTo((byte) 1);
        assertThat(ISO7816.OFFSET_CDATA).isEqualTo((byte) 5);
    }

    @Test
    void jcSystemVersionIs305() {
        assertThat(JCSystem.getVersion()).isEqualTo((short) 0x0305);
    }

    @Test
    void utilMakeShort() {
        short result = Util.makeShort((byte) 0x12, (byte) 0x34);
        assertThat(result).isEqualTo((short) 0x1234);
    }

    @Test
    void keyBuilderTypeConstantsExist() {
        assertThat(KeyBuilder.TYPE_AES).isEqualTo((byte) 15);
        assertThat(KeyBuilder.TYPE_DES).isEqualTo((byte) 3);
        assertThat(KeyBuilder.TYPE_RSA_PUBLIC).isEqualTo((byte) 4);
        assertThat(KeyBuilder.TYPE_EC_FP_PUBLIC).isEqualTo((byte) 11);
        assertThat(KeyBuilder.LENGTH_AES_256).isEqualTo((short) 256);
    }

    @Test
    void cipherConstantsExist() {
        assertThat(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD).isEqualTo((byte) 13);
        assertThat(Cipher.ALG_DES_CBC_NOPAD).isEqualTo((byte) 1);
        assertThat(Cipher.MODE_ENCRYPT).isEqualTo((byte) 2);
        assertThat(Cipher.MODE_DECRYPT).isEqualTo((byte) 1);
    }

    @Test
    void signatureAlgorithmConstantsExist() {
        assertThat(Signature.ALG_RSA_SHA_PKCS1).isEqualTo((byte) 10);
        assertThat(Signature.ALG_ECDSA_SHA_256).isEqualTo((byte) 33);
        assertThat(Signature.ALG_AES_CMAC_128).isEqualTo((byte) 49);
        assertThat(Signature.MODE_SIGN).isEqualTo((byte) 1);
    }

    @Test
    void messageDigestAlgorithmConstantsExist() {
        assertThat(MessageDigest.ALG_SHA).isEqualTo((byte) 1);
        assertThat(MessageDigest.ALG_SHA_256).isEqualTo((byte) 4);
    }

    @Test
    void keyPairAlgorithmConstantsExist() {
        assertThat(KeyPair.ALG_RSA).isEqualTo((byte) 1);
        assertThat(KeyPair.ALG_EC_FP).isEqualTo((byte) 5);
    }
}
