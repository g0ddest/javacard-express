package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.util.Arrays;

/**
 * Key type definitions and Key Check Value (KCV) computation for GlobalPlatform.
 *
 * <p>KCV is a 3-byte value used to verify key correctness without exposing
 * the key itself. It is included in PUT KEY commands to allow the card
 * to confirm that the key was received correctly.</p>
 *
 * <h2>KCV computation:</h2>
 * <ul>
 *   <li><b>DES3:</b> first 3 bytes of 3DES-ECB(key, 0x00[8])</li>
 *   <li><b>AES:</b> first 3 bytes of AES-ECB(key, 0x01[16])</li>
 * </ul>
 */
public final class KeyInfo {

    private KeyInfo() {
    }

    /**
     * GlobalPlatform key types for PUT KEY command.
     */
    public enum KeyType {
        /** Triple DES (2-key or 3-key, 16 or 24 bytes). */
        DES3(0x80),
        /** AES (128, 192, or 256 bits). */
        AES(0x88);

        private final int code;

        KeyType(int code) {
            this.code = code;
        }

        /** Returns the GP key type code byte.
         * @return key type code */
        public int code() {
            return code;
        }
    }

    /**
     * Computes the KCV for a 3DES key.
     *
     * <p>KCV = first 3 bytes of 3DES-ECB(key, 0x00[8]).</p>
     *
     * @param key the 16 or 24-byte 3DES key
     * @return 3-byte KCV
     */
    public static byte[] kcvDes3(byte[] key) {
        byte[] encrypted = CryptoUtil.des3EcbEncrypt(key, new byte[8]);
        return Arrays.copyOf(encrypted, 3);
    }

    /**
     * Computes the KCV for an AES key.
     *
     * <p>KCV = first 3 bytes of AES-ECB(key, 0x01[16]).</p>
     *
     * @param key the 16, 24, or 32-byte AES key
     * @return 3-byte KCV
     */
    public static byte[] kcvAes(byte[] key) {
        byte[] data = new byte[16];
        Arrays.fill(data, (byte) 0x01);
        byte[] encrypted = CryptoUtil.aesEcbEncrypt(key, data);
        return Arrays.copyOf(encrypted, 3);
    }

    /**
     * Computes the KCV for a key of the given type.
     *
     * @param key  the key bytes
     * @param type the key type
     * @return 3-byte KCV
     */
    public static byte[] kcv(byte[] key, KeyType type) {
        return type == KeyType.DES3 ? kcvDes3(key) : kcvAes(key);
    }
}
