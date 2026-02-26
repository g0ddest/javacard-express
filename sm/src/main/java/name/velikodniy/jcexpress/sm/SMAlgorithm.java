package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.util.Arrays;

/**
 * Algorithm suites for ISO 7816-4 Secure Messaging.
 *
 * <p>Two suites are defined:</p>
 * <ul>
 *   <li><b>DES3</b> — ePassport BAC: retail MAC (ISO 9797-1 Algorithm 3) + 3DES-CBC,
 *       8-byte blocks, 8-byte SSC</li>
 *   <li><b>AES</b> — PACE/EAC: AES-CMAC (truncated to 8 bytes) + AES-CBC,
 *       16-byte blocks, 16-byte SSC</li>
 * </ul>
 *
 * <p>Each enum constant delegates to the appropriate {@link CryptoUtil} methods.</p>
 */
public enum SMAlgorithm {

    /**
     * 3DES suite (ePassport BAC).
     *
     * <p>Uses retail MAC (ISO 9797-1 Algorithm 3) for authentication
     * and 3DES-CBC for encryption. Block size is 8 bytes.</p>
     */
    DES3(8) {
        @Override
        public byte[] encrypt(byte[] key, byte[] data, byte[] iv) {
            return CryptoUtil.des3CbcEncrypt(key, data, iv);
        }

        @Override
        public byte[] decrypt(byte[] key, byte[] data, byte[] iv) {
            return CryptoUtil.des3CbcDecrypt(key, data, iv);
        }

        @Override
        public byte[] mac(byte[] key, byte[] data) {
            return CryptoUtil.retailMac(key, data, new byte[8]);
        }
    },

    /**
     * AES suite (PACE/EAC).
     *
     * <p>Uses AES-CMAC (truncated to 8 bytes) for authentication
     * and AES-CBC for encryption. Block size is 16 bytes.</p>
     */
    AES(16) {
        @Override
        public byte[] encrypt(byte[] key, byte[] data, byte[] iv) {
            return CryptoUtil.aesCbcEncrypt(key, data, iv);
        }

        @Override
        public byte[] decrypt(byte[] key, byte[] data, byte[] iv) {
            return CryptoUtil.aesCbcDecrypt(key, data, iv);
        }

        @Override
        public byte[] mac(byte[] key, byte[] data) {
            byte[] full = CryptoUtil.aesCmac(key, data);
            return Arrays.copyOf(full, 8); // truncate to 8 bytes
        }
    };

    private final int blockSize;

    SMAlgorithm(int blockSize) {
        this.blockSize = blockSize;
    }

    /**
     * Returns the cipher block size (8 for 3DES, 16 for AES).
     *
     * @return block size in bytes
     */
    public int blockSize() {
        return blockSize;
    }

    /**
     * Encrypts data using the suite's cipher in CBC mode.
     *
     * @param key  the encryption key
     * @param data the data to encrypt (must be block-aligned)
     * @param iv   the initialization vector
     * @return encrypted data
     */
    public abstract byte[] encrypt(byte[] key, byte[] data, byte[] iv);

    /**
     * Decrypts data using the suite's cipher in CBC mode.
     *
     * @param key  the encryption key
     * @param data the data to decrypt (must be block-aligned)
     * @param iv   the initialization vector
     * @return decrypted data
     */
    public abstract byte[] decrypt(byte[] key, byte[] data, byte[] iv);

    /**
     * Computes a MAC using the suite's algorithm.
     *
     * <p>For DES3: retail MAC (ISO 9797-1 Algorithm 3) with zero IV.
     * For AES: AES-CMAC truncated to 8 bytes.</p>
     *
     * @param key  the MAC key
     * @param data the data to authenticate (must be padded)
     * @return 8-byte MAC value
     */
    public abstract byte[] mac(byte[] key, byte[] data);

    /**
     * Pads data using ISO 9797-1 Method 2 (0x80 followed by zeros).
     *
     * @param data the data to pad
     * @return padded data (block-aligned)
     */
    public byte[] pad(byte[] data) {
        return CryptoUtil.pad80(data, blockSize);
    }

    /**
     * Removes ISO 9797-1 Method 2 padding.
     *
     * @param data the padded data
     * @return unpadded data
     */
    public byte[] unpad(byte[] data) {
        return CryptoUtil.unpad80(data);
    }
}
