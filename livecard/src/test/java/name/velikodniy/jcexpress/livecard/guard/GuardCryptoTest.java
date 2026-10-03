package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Known answers for the guard's own AES-CMAC (NIST SP 800-38B / RFC 4493) and SCP03 KDF (Amendment D 4.1.5).
 */
class GuardCryptoTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final String MESSAGE = "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51"
            + "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710";

    /** RFC 4493 section 4 (AES-128) and NIST SP 800-38B appendix D.2/D.3 (AES-192, AES-256). */
    @ParameterizedTest(name = "{0}-bit key, {1} message bytes")
    @CsvSource({
        "128, 0, 2b7e151628aed2a6abf7158809cf4f3c, bb1d6929e95937287fa37d129b756746",
        "128, 16, 2b7e151628aed2a6abf7158809cf4f3c, 070a16b46b4d4144f79bdd9dd04a287c",
        "128, 40, 2b7e151628aed2a6abf7158809cf4f3c, dfa66747de9ae63030ca32611497c827",
        "128, 64, 2b7e151628aed2a6abf7158809cf4f3c, 51f0bebf7e3b9d92fc49741779363cfe",
        "192, 0, 8e73b0f7da0e6452c810f32b809079e562f8ead2522c6b7b, d17ddf46adaacde531cac483de7a9367",
        "192, 16, 8e73b0f7da0e6452c810f32b809079e562f8ead2522c6b7b, 9e99a7bf31e710900662f65e617c5184",
        "256, 0, 603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4, 028962f61b7bf89efc6b551f4667d983",
        "256, 16, 603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4, 28a7023f452e8f82bd4bf28d8c37c35c",
    })
    void aesCmacKnownAnswers(int bits, int length, String key, String mac) {
        byte[] message = Arrays.copyOf(HEX.parseHex(MESSAGE), length);

        assertThat(HEX.formatHex(GuardCrypto.aesCmac(HEX.parseHex(key), message))).isEqualTo(mac);
    }

    /**
     * The reference handshake (independent Python implementation, GP test keys): S-MAC derived from Key-MAC
     * reproduces the card cryptogram of the INITIALIZE UPDATE response and the host cryptogram and C-MAC of the
     * EXTERNAL AUTHENTICATE command.
     */
    @Test
    void scp03KdfReproducesTheReferenceHandshake() {
        byte[] keyMac = HEX.parseHex("404142434445464748494a4b4c4d4e4f");
        byte[] context = HEX.parseHex("3975b80ffd2445a1" + "f32cec24f61679c4");

        byte[] sessionMac = GuardCrypto.scp03Kdf(keyMac, 0x06, 128, context);

        assertThat(HEX.formatHex(GuardCrypto.scp03Kdf(sessionMac, 0x00, 64, context))).isEqualTo("3b609e829b5ff824");
        assertThat(HEX.formatHex(GuardCrypto.scp03Kdf(sessionMac, 0x01, 64, context))).isEqualTo("8f16d6a1e346f478");
        byte[] macInput = HEX.parseHex("00".repeat(16) + "8482010010" + "8f16d6a1e346f478");
        assertThat(HEX.formatHex(Arrays.copyOf(GuardCrypto.aesCmac(sessionMac, macInput), 8)))
                .isEqualTo("b250bc24d6186975");
    }

    @Test
    void kdfDerivesMoreThanOneBlockWithAnIncrementingCounter() {
        byte[] key = HEX.parseHex("404142434445464748494a4b4c4d4e4f");
        byte[] context = new byte[16];

        byte[] twoBlocks = GuardCrypto.scp03Kdf(key, 0x04, 256, context);

        byte[] first = new byte[32];
        first[11] = 0x04;
        first[13] = 0x01;
        first[15] = 0x01;
        byte[] second = first.clone();
        second[15] = 0x02;
        assertThat(Arrays.copyOf(twoBlocks, 16)).isEqualTo(GuardCrypto.aesCmac(key, first));
        assertThat(Arrays.copyOfRange(twoBlocks, 16, 32)).isEqualTo(GuardCrypto.aesCmac(key, second));
    }

    @Test
    void rejectsKeysAndLengthsThatAreNotAes() {
        assertThatThrownBy(() -> GuardCrypto.aesCmac(new byte[8], new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardCrypto.scp03Kdf(new byte[16], 0, 12, new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
