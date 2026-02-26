package name.velikodniy.jcexpress.pace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PaceAlgorithm}.
 */
class PaceAlgorithmTest {

    @Test
    void oidBytesShouldBeValidDer() {
        for (PaceAlgorithm alg : PaceAlgorithm.values()) {
            byte[] oid = alg.oidBytes();
            assertThat(oid).isNotEmpty();
            // First byte should be 0*40+4 = 4 (OID starts with 0.4.0.127...)
            assertThat(oid[0] & 0xFF).isEqualTo(4);
        }
    }

    @Test
    void keyLengthShouldMatchAlgorithm() {
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.keyLength()).isEqualTo(16);
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_192.keyLength()).isEqualTo(24);
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_256.keyLength()).isEqualTo(32);
    }

    @Test
    void oidStringShouldBeCorrect() {
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oid())
                .isEqualTo("0.4.0.127.0.7.2.2.4.2.2");
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_192.oid())
                .isEqualTo("0.4.0.127.0.7.2.2.4.2.3");
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_256.oid())
                .isEqualTo("0.4.0.127.0.7.2.2.4.2.4");
    }

    @Test
    void oidBytesShouldReturnCopy() {
        byte[] oid1 = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes();
        byte[] oid2 = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes();
        assertThat(oid1).isEqualTo(oid2);
        oid1[0] = (byte) 0xFF;
        assertThat(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes()[0]).isNotEqualTo((byte) 0xFF);
    }

    @Test
    void allAlgorithmsShouldHaveSameOidPrefix() {
        // All PACE ECDH-GM AES algorithms share the OID prefix 0.4.0.127.0.7.2.2.4.2
        byte[] oid128 = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes();
        byte[] oid192 = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_192.oidBytes();
        byte[] oid256 = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_256.oidBytes();

        // Same length and same prefix, only last byte differs
        assertThat(oid128.length).isEqualTo(oid192.length);
        assertThat(oid128.length).isEqualTo(oid256.length);

        for (int i = 0; i < oid128.length - 1; i++) {
            assertThat(oid128[i]).isEqualTo(oid192[i]);
            assertThat(oid128[i]).isEqualTo(oid256[i]);
        }
    }
}
