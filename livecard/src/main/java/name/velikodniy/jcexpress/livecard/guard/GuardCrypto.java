package name.velikodniy.jcexpress.livecard.guard;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * The guard's own AES-CMAC and SCP03 key derivation.
 *
 * <p>Deliberately independent of the {@code gp} module: the guard checks the secure channel that {@code gp}
 * opens, so it must not share that module's cryptography. Only the JDK AES block cipher is used.</p>
 * <ul>
 *   <li>AES-CMAC: NIST SP 800-38B, test vectors of RFC 4493 section 4.</li>
 *   <li>SCP03 data derivation: GlobalPlatform Card Specification v2.3 Amendment D (SCP03) v1.1.2, section
 *       4.1.5 = NIST SP 800-108 KDF in counter mode with AES-CMAC as PRF; the input is
 *       {@code 11 x '00' || derivation constant || '00' || L (2 bytes, bits) || i (1 byte) || context}.</li>
 * </ul>
 */
public final class GuardCrypto {

    /** AES block size in bytes. */
    private static final int BLOCK = 16;
    /** R_b constant for 128-bit blocks (NIST SP 800-38B 5.3). */
    private static final int RB = 0x87;

    private GuardCrypto() {
    }

    /**
     * Computes AES-CMAC (NIST SP 800-38B, RFC 4493) over a message.
     *
     * @param key     the AES key (16, 24 or 32 bytes)
     * @param message the message (any length, may be empty)
     * @return the 16-byte MAC
     * @throws IllegalArgumentException if the key length is not an AES key length
     */
    public static byte[] aesCmac(byte[] key, byte[] message) {
        byte[] k1 = doubled(aes(key, new byte[BLOCK]));
        byte[] k2 = doubled(k1);
        int blocks = Math.max(1, (message.length + BLOCK - 1) / BLOCK);
        boolean complete = message.length > 0 && message.length % BLOCK == 0;
        byte[] last = Arrays.copyOfRange(message, (blocks - 1) * BLOCK, blocks * BLOCK);
        if (!complete) {
            last[message.length - (blocks - 1) * BLOCK] = (byte) 0x80;
        }
        xorInto(last, complete ? k1 : k2);
        byte[] x = new byte[BLOCK];
        for (int b = 0; b < blocks - 1; b++) {
            xorInto(x, Arrays.copyOfRange(message, b * BLOCK, (b + 1) * BLOCK));
            x = aes(key, x);
        }
        xorInto(x, last);
        return aes(key, x);
    }

    /**
     * Derives SCP03 data (session keys, cryptograms) from a key, Amendment D section 4.1.5.
     *
     * @param key        the key to derive from (static Key-MAC for S-MAC, S-MAC for cryptograms)
     * @param constant   the derivation constant (Amendment D Table 4-1, e.g. '00' card cryptogram, '01' host
     *                   cryptogram, '06' S-MAC)
     * @param lengthBits L, the number of bits to derive (a multiple of 8)
     * @param context    the context ({@code host challenge || card challenge})
     * @return {@code lengthBits / 8} derived bytes
     */
    public static byte[] scp03Kdf(byte[] key, int constant, int lengthBits, byte[] context) {
        if (lengthBits <= 0 || lengthBits % 8 != 0 || lengthBits > 0xFFFF) {
            throw new IllegalArgumentException("L must be a positive multiple of 8 bits, got " + lengthBits);
        }
        byte[] out = new byte[0];
        for (int i = 1; out.length * 8 < lengthBits; i++) {
            byte[] input = new byte[BLOCK + context.length];
            input[11] = (byte) constant;
            input[13] = (byte) (lengthBits >> 8);
            input[14] = (byte) lengthBits;
            input[15] = (byte) i;
            System.arraycopy(context, 0, input, BLOCK, context.length);
            byte[] block = aesCmac(key, input);
            byte[] grown = Arrays.copyOf(out, out.length + BLOCK);
            System.arraycopy(block, 0, grown, out.length, BLOCK);
            out = grown;
        }
        return Arrays.copyOf(out, lengthBits / 8);
    }

    /** AES-ECB encryption of one block. */
    private static byte[] aes(byte[] key, byte[] block) {
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("AES keys are 16, 24 or 32 bytes, got " + key.length);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(block);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES is not available in this JDK", e);
        }
    }

    /** Subkey generation step of NIST SP 800-38B 6.1: shift left by one bit, XOR R_b on carry. */
    private static byte[] doubled(byte[] in) {
        byte[] out = new byte[BLOCK];
        for (int i = 0; i < BLOCK; i++) {
            int next = i < BLOCK - 1 ? (in[i + 1] & 0xFF) >>> 7 : 0;
            out[i] = (byte) ((in[i] << 1) | next);
        }
        if ((in[0] & 0x80) != 0) {
            out[BLOCK - 1] ^= (byte) RB;
        }
        return out;
    }

    private static void xorInto(byte[] target, byte[] other) {
        for (int i = 0; i < BLOCK; i++) {
            target[i] ^= other[i];
        }
    }
}
