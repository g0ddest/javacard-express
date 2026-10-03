package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * SCP03 data derivation scheme (GlobalPlatform Card Specification Amendment D v1.1.2, section 4.1.5).
 *
 * <p>Key derivation, pseudo-random card challenges and authentication cryptograms all use the
 * NIST SP 800-108 KDF in counter mode with AES-CMAC (NIST SP 800-38B, full 16-byte output) as PRF.
 * The input of the i-th PRF call is:</p>
 * <pre>
 * label(12) = '00' x 11 || derivation constant
 * separator = '00'
 * L(2)      = length of the derived data in bits ('0040', '0080', '00C0' or '0100')
 * i(1)      = counter, '01' for the first PRF call, '02' for the second
 * context   = e.g. host challenge || card challenge (Amd D 6.2.1)
 * </pre>
 * <p>The PRF is called {@code ceil(L / 128)} times and the concatenated output is truncated to
 * {@code L / 8} bytes, so AES-192 session keys are 24 bytes long and cryptograms are 8 bytes long.</p>
 *
 * @see SCP03
 * @see GP#SCP03_DERIVE_ENC
 */
public final class SCP03KeyDerivation {

    private static final int PRF_OUTPUT_BITS = 128;

    private SCP03KeyDerivation() {
    }

    /**
     * Derives {@code lengthBits} bits of data (Amd D 4.1.5).
     *
     * @param key        the PRF key (static key, or session key S-MAC for cryptograms)
     * @param constant   the derivation constant of Amd D Table 4-1
     * @param lengthBits the length L of the derived data in bits: 64, 128, 192 or 256
     * @param context    the KDF context
     * @return the derived data, exactly {@code lengthBits / 8} bytes
     * @throws IllegalArgumentException if {@code lengthBits} is not one of the four values allowed by Amd D 4.1.5
     */
    public static byte[] derive(byte[] key, byte constant, int lengthBits, byte[] context) {
        if (lengthBits != 64 && lengthBits != 128 && lengthBits != 192 && lengthBits != 256) {
            throw new IllegalArgumentException(
                    "SCP03 KDF length L must be 64, 128, 192 or 256 bits (Amd D 4.1.5), got: " + lengthBits);
        }
        int iterations = (lengthBits + PRF_OUTPUT_BITS - 1) / PRF_OUTPUT_BITS;
        ByteArrayOutputStream derived = new ByteArrayOutputStream(iterations * 16);
        for (int counter = 1; counter <= iterations; counter++) {
            derived.writeBytes(CryptoUtil.aesCmac(key, fixedInput(constant, lengthBits, counter, context)));
        }
        return Arrays.copyOf(derived.toByteArray(), lengthBits / 8);
    }

    private static byte[] fixedInput(byte constant, int lengthBits, int counter, byte[] context) {
        byte[] input = new byte[16 + context.length];
        input[11] = constant;                        // label: 11 x '00' || constant
        input[12] = 0x00;                            // separation indicator
        input[13] = (byte) (lengthBits >> 8);        // L (2 bytes)
        input[14] = (byte) lengthBits;
        input[15] = (byte) counter;                  // i
        System.arraycopy(context, 0, input, 16, context.length);
        return input;
    }
}
