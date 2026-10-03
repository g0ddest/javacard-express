package name.velikodniy.jcexpress.crypto;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Output length of {@link CryptoUtil#deriveSCP03SessionKey} per GlobalPlatform Amendment D v1.1.2
 * section 4.1.5: the KDF returns exactly L bits ('0040', '0080', '00C0' or '0100').
 */
class CryptoUtilScp03KdfLengthTest {

    private static final byte SCP03_DERIVE_C_MAC = 0x06;
    private static final byte SCP03_DERIVE_CARD_CRYPTO = 0x00;

    /**
     * Samsung OpenSCP-Java AES-192 S8 transcript (Apache-2.0, data only): Key-MAC, host challenge
     * 06F85B77251BF794, card challenge 9CE033FA78E6B10D, card cryptogram 6E7C64F962A822A4.
     */
    @Test
    void amdD_4_1_5_aes192SessionKeyIs24BytesAndReproducesCardCryptogram() {
        byte[] keyMac = Hex.decode("F4932BA02FFC3098D172790099D2838236F2E61068D56F44");
        byte[] context = Hex.decode("06F85B77251BF7949CE033FA78E6B10D");

        byte[] sMac = CryptoUtil.deriveSCP03SessionKey(keyMac, context, SCP03_DERIVE_C_MAC, 192);
        byte[] cryptogram = CryptoUtil.deriveSCP03SessionKey(sMac, context, SCP03_DERIVE_CARD_CRYPTO, 64);

        assertThat(sMac).hasSize(24);
        assertThat(Hex.encode(cryptogram)).isEqualTo("6E7C64F962A822A4");
    }
}
