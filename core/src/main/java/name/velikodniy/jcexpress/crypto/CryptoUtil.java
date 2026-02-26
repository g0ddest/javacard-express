package name.velikodniy.jcexpress.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Low-level cryptographic operations used across JavaCard Express modules.
 *
 * <p>Provides static methods for:</p>
 * <ul>
 *   <li>3DES CBC encryption/MAC</li>
 *   <li>AES CBC encryption and AES-CMAC</li>
 *   <li>Key derivation helpers (SCP02, SCP03)</li>
 *   <li>ISO/IEC 9797-1 padding (Method 2)</li>
 *   <li>SHA-1 and SHA-256 hashing</li>
 * </ul>
 *
 * <p>All methods wrap checked JCE exceptions into unchecked {@link CryptoException}
 * since algorithm unavailability indicates a broken JVM, not a runtime condition.</p>
 */
public final class CryptoUtil {

    private static final byte[] ZERO_IV_8 = new byte[8];
    private static final byte[] ZERO_IV_16 = new byte[16];

    private CryptoUtil() {
    }

    // ── 3DES ──

    /**
     * Encrypts data with 3DES in CBC mode using a zero IV.
     *
     * <p>The key is expanded from 16 to 24 bytes by repeating the first 8 bytes
     * (2-key 3DES as specified in SCP02).</p>
     *
     * @param key  the 16-byte 3DES key
     * @param data the data to encrypt (must be a multiple of 8 bytes)
     * @return encrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] des3CbcEncrypt(byte[] key, byte[] data) {
        return des3CbcEncrypt(key, data, ZERO_IV_8);
    }

    /**
     * Encrypts data with 3DES in CBC mode.
     *
     * @param key  the 16-byte 3DES key
     * @param data the data to encrypt (must be a multiple of 8 bytes)
     * @param iv   the 8-byte initialization vector
     * @return encrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] des3CbcEncrypt(byte[] key, byte[] data, byte[] iv) {
        try {
            byte[] key24 = expand3DESKey(key);
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key24, "DESede"),
                    new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new CryptoException("3DES CBC encrypt failed", e);
        }
    }

    /**
     * Decrypts data with 3DES in CBC mode using a zero IV.
     *
     * @param key  the 16-byte 3DES key
     * @param data the data to decrypt (must be a multiple of 8 bytes)
     * @return decrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] des3CbcDecrypt(byte[] key, byte[] data) {
        return des3CbcDecrypt(key, data, ZERO_IV_8);
    }

    /**
     * Decrypts data with 3DES in CBC mode.
     *
     * @param key  the 16-byte 3DES key
     * @param data the data to decrypt (must be a multiple of 8 bytes)
     * @param iv   the 8-byte initialization vector
     * @return decrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] des3CbcDecrypt(byte[] key, byte[] data, byte[] iv) {
        try {
            byte[] key24 = expand3DESKey(key);
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key24, "DESede"),
                    new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new CryptoException("3DES CBC decrypt failed", e);
        }
    }

    /**
     * Computes a full 3DES MAC (ISO/IEC 9797-1 Algorithm 1, using 3DES CBC).
     *
     * <p>Returns the last 8-byte block of the 3DES CBC encryption result.</p>
     *
     * @param key  the 16-byte 3DES MAC key
     * @param data the data to MAC (must be padded to a multiple of 8 bytes)
     * @param iv   the 8-byte chaining value (zero for first command)
     * @return 8-byte MAC value
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] des3Mac(byte[] key, byte[] data, byte[] iv) {
        byte[] encrypted = des3CbcEncrypt(key, data, iv);
        return Arrays.copyOfRange(encrypted, encrypted.length - 8, encrypted.length);
    }

    /**
     * Computes a single DES MAC followed by final 3DES encryption.
     *
     * <p>This is the "Retail MAC" (ISO/IEC 9797-1 Algorithm 3):
     * single DES CBC over all blocks except the last, then 3DES on the last block.</p>
     *
     * @param key  the 16-byte MAC key
     * @param data the data to MAC (must be padded to a multiple of 8 bytes)
     * @param iv   the 8-byte chaining value
     * @return 8-byte MAC value
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] retailMac(byte[] key, byte[] data, byte[] iv) {
        try {
            byte[] keyLeft = Arrays.copyOfRange(key, 0, 8);

            if (data.length > 8) {
                // Single DES CBC on all blocks except the last
                Cipher desCipher = Cipher.getInstance("DES/CBC/NoPadding");
                desCipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(keyLeft, "DES"),
                        new IvParameterSpec(iv));
                byte[] intermediate = desCipher.doFinal(data, 0, data.length - 8);
                iv = Arrays.copyOfRange(intermediate, intermediate.length - 8, intermediate.length);
            }

            // Final block with full 3DES
            return des3CbcEncrypt(key, Arrays.copyOfRange(data, data.length - 8, data.length), iv);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new CryptoException("Retail MAC failed", e);
        }
    }

    /**
     * Encrypts data with 3DES in ECB mode (no IV, no chaining).
     *
     * <p>Used for computing DES3 Key Check Values (KCV) by encrypting
     * 8 zero bytes with the key.</p>
     *
     * @param key  the 16-byte 3DES key
     * @param data the data to encrypt (must be a multiple of 8 bytes)
     * @return encrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] des3EcbEncrypt(byte[] key, byte[] data) {
        try {
            byte[] key24 = expand3DESKey(key);
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException("3DES ECB encrypt failed", e);
        }
    }

    // ── AES ──

    /**
     * Encrypts data with AES in ECB mode (no IV, no chaining).
     *
     * <p>Used for computing AES Key Check Values (KCV) by encrypting
     * 16 bytes of {@code 0x01} with the key.</p>
     *
     * @param key  the 16 or 32-byte AES key
     * @param data the data to encrypt (must be a multiple of 16 bytes)
     * @return encrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] aesEcbEncrypt(byte[] key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException("AES ECB encrypt failed", e);
        }
    }

    /**
     * Encrypts data with AES in CBC mode using a zero IV.
     *
     * @param key  the 16 or 32-byte AES key
     * @param data the data to encrypt (must be a multiple of 16 bytes)
     * @return encrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] aesCbcEncrypt(byte[] key, byte[] data) {
        return aesCbcEncrypt(key, data, ZERO_IV_16);
    }

    /**
     * Encrypts data with AES in CBC mode.
     *
     * @param key  the 16 or 32-byte AES key
     * @param data the data to encrypt (must be a multiple of 16 bytes)
     * @param iv   the 16-byte initialization vector
     * @return encrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] aesCbcEncrypt(byte[] key, byte[] data, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new CryptoException("AES CBC encrypt failed", e);
        }
    }

    /**
     * Decrypts data with AES in CBC mode using a zero IV.
     *
     * @param key  the 16 or 32-byte AES key
     * @param data the data to decrypt (must be a multiple of 16 bytes)
     * @return decrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] aesCbcDecrypt(byte[] key, byte[] data) {
        return aesCbcDecrypt(key, data, ZERO_IV_16);
    }

    /**
     * Decrypts data with AES in CBC mode.
     *
     * @param key  the 16 or 32-byte AES key
     * @param data the data to decrypt (must be a multiple of 16 bytes)
     * @param iv   the 16-byte initialization vector
     * @return decrypted data
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] aesCbcDecrypt(byte[] key, byte[] data, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new CryptoException("AES CBC decrypt failed", e);
        }
    }

    /**
     * Computes AES-CMAC (NIST SP 800-38B / RFC 4493).
     *
     * <p>Uses the JCE "AESCMAC" provider if available, otherwise falls back to
     * a manual implementation.</p>
     *
     * @param key  the 16 or 32-byte AES key
     * @param data the data to MAC (any length)
     * @return 16-byte CMAC value
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] aesCmac(byte[] key, byte[] data) {
        try {
            // Try JCE provider first (available in most modern JVMs)
            Mac mac = Mac.getInstance("AESCMAC");
            mac.init(new SecretKeySpec(key, "AES"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to manual CMAC implementation
            return aesCmacManual(key, data);
        } catch (InvalidKeyException e) {
            throw new CryptoException("AES-CMAC failed", e);
        }
    }

    // ── Padding ──

    /**
     * Applies ISO/IEC 9797-1 Method 2 padding (0x80 followed by zeros).
     *
     * <p>The data is padded to a multiple of the specified block size.
     * Padding is always applied (even if data is already block-aligned).</p>
     *
     * @param data      the data to pad
     * @param blockSize the block size (8 for 3DES, 16 for AES)
     * @return padded data (always larger than input)
     */
    public static byte[] pad80(byte[] data, int blockSize) {
        int paddedLength = data.length + 1;
        int remainder = paddedLength % blockSize;
        if (remainder != 0) {
            paddedLength += blockSize - remainder;
        }
        byte[] padded = Arrays.copyOf(data, paddedLength);
        padded[data.length] = (byte) 0x80;
        // Remaining bytes are already 0x00 from Arrays.copyOf
        return padded;
    }

    /**
     * Removes ISO/IEC 9797-1 Method 2 padding (0x80 followed by zeros).
     *
     * <p>Scans from the end of the data, skipping 0x00 bytes until 0x80 is found,
     * then returns everything before the 0x80 marker.</p>
     *
     * @param data the padded data
     * @return unpadded data
     * @throws CryptoException if no 0x80 padding marker is found
     */
    public static byte[] unpad80(byte[] data) {
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == (byte) 0x80) {
                return Arrays.copyOf(data, i);
            }
            if (data[i] != 0x00) {
                throw new CryptoException("Invalid ISO 9797-1 Method 2 padding: "
                        + "expected 0x00 or 0x80, found 0x"
                        + String.format("%02X", data[i] & 0xFF)
                        + " at position " + i);
            }
        }
        throw new CryptoException("Invalid ISO 9797-1 Method 2 padding: 0x80 marker not found");
    }

    // ── Hashing ──

    /**
     * Computes SHA-1 hash.
     *
     * @param data the data to hash
     * @return 20-byte SHA-1 digest
     * @throws CryptoException if SHA-1 is not available
     */
    public static byte[] sha1(byte[] data) {
        return hash("SHA-1", data);
    }

    /**
     * Computes SHA-256 hash.
     *
     * @param data the data to hash
     * @return 32-byte SHA-256 digest
     * @throws CryptoException if SHA-256 is not available
     */
    public static byte[] sha256(byte[] data) {
        return hash("SHA-256", data);
    }

    private static byte[] hash(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(algorithm + " not available", e);
        }
    }

    // ── Key derivation ──

    /**
     * Derives SCP02 session keys using the sequence counter and derivation constants.
     *
     * <p>Session key = 3DES-CBC(staticKey, derivationData) where derivationData
     * is constructed from the constant and sequence counter per GP spec.</p>
     *
     * @param staticKey       the 16-byte static key (ENC, MAC, or DEK)
     * @param sequenceCounter the 2-byte sequence counter from INITIALIZE UPDATE response
     * @param constant        the 2-byte derivation constant
     * @return the 16-byte derived session key
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] deriveSCP02SessionKey(byte[] staticKey, byte[] sequenceCounter, byte[] constant) {
        // Derivation data: 2-byte constant || 2-byte sequence counter || 12 bytes of 0x00
        byte[] derivationData = new byte[16];
        System.arraycopy(constant, 0, derivationData, 0, 2);
        System.arraycopy(sequenceCounter, 0, derivationData, 2, 2);
        // Remaining 12 bytes are 0x00

        return des3CbcEncrypt(staticKey, derivationData);
    }

    /**
     * Derives SCP03 session keys using AES-CMAC-based KDF.
     *
     * <p>The key derivation function is defined in GP Card Specification v2.3,
     * Amendment D (SCP03).</p>
     *
     * @param staticKey   the 16 or 32-byte static key
     * @param context     the derivation context (host challenge || card challenge)
     * @param constant    the 1-byte derivation constant
     * @param keyLengthBits the output key length in bits (128 or 256)
     * @return the derived session key
     * @throws CryptoException if the crypto operation fails
     */
    public static byte[] deriveSCP03SessionKey(byte[] staticKey, byte[] context,
                                                byte constant, int keyLengthBits) {
        byte[] derivationData = new byte[32];
        Arrays.fill(derivationData, (byte) 0);
        derivationData[11] = constant;
        derivationData[13] = (byte) ((keyLengthBits >> 8) & 0xFF);
        derivationData[14] = (byte) (keyLengthBits & 0xFF);

        int totalLength = 15 + 1 + context.length;
        byte[] fullData = new byte[totalLength];
        System.arraycopy(derivationData, 0, fullData, 0, 15);
        fullData[15] = 0x01; // counter
        System.arraycopy(context, 0, fullData, 16, context.length);

        byte[] cmac = aesCmac(staticKey, fullData);

        if (keyLengthBits <= 128) {
            return Arrays.copyOf(cmac, 16);
        } else {
            fullData[15] = 0x02;
            byte[] cmac2 = aesCmac(staticKey, fullData);
            byte[] result = new byte[32];
            System.arraycopy(cmac, 0, result, 0, 16);
            System.arraycopy(cmac2, 0, result, 16, 16);
            return result;
        }
    }

    // ── Internal helpers ──

    /**
     * Expands a 16-byte 2-key 3DES key to 24 bytes (K1||K2||K1).
     */
    static byte[] expand3DESKey(byte[] key16) {
        if (key16.length == 24) {
            return key16.clone();
        }
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        return key24;
    }

    /**
     * Manual AES-CMAC implementation (RFC 4493) for JVMs without AESCMAC provider.
     */
    private static byte[] aesCmacManual(byte[] key, byte[] data) {
        try {
            // Step 1: Generate subkeys
            byte[] zeroBlock = new byte[16];
            byte[] L = aesCbcEncrypt(key, zeroBlock);
            byte[] K1 = generateSubkey(L);
            byte[] K2 = generateSubkey(K1);

            // Step 2: Determine number of blocks
            int n = (data.length + 15) / 16;
            boolean lastBlockComplete;
            if (n == 0) {
                n = 1;
                lastBlockComplete = false;
            } else {
                lastBlockComplete = (data.length % 16 == 0);
            }

            // Step 3: XOR last block with subkey
            byte[] lastBlock = new byte[16];
            int lastBlockStart = (n - 1) * 16;
            if (lastBlockComplete) {
                System.arraycopy(data, lastBlockStart, lastBlock, 0, 16);
                xorInPlace(lastBlock, K1);
            } else {
                int remaining = data.length - lastBlockStart;
                System.arraycopy(data, lastBlockStart, lastBlock, 0, remaining);
                lastBlock[remaining] = (byte) 0x80;
                // Rest is zeros
                xorInPlace(lastBlock, K2);
            }

            // Step 4: CBC-MAC
            byte[] x = new byte[16];
            for (int i = 0; i < n - 1; i++) {
                byte[] block = Arrays.copyOfRange(data, i * 16, (i + 1) * 16);
                xorInPlace(x, block);
                x = aesCbcEncrypt(key, x);
            }
            xorInPlace(x, lastBlock);
            return aesCbcEncrypt(key, x);
        } catch (Exception e) {
            throw new CryptoException("AES-CMAC manual computation failed", e);
        }
    }

    /**
     * Generates a CMAC subkey by left-shifting and conditional XOR.
     */
    private static byte[] generateSubkey(byte[] input) {
        byte[] output = new byte[16];
        boolean carry = (input[0] & 0x80) != 0;

        for (int i = 0; i < 15; i++) {
            output[i] = (byte) ((input[i] << 1) | ((input[i + 1] & 0x80) >>> 7));
        }
        output[15] = (byte) (input[15] << 1);

        if (carry) {
            output[15] ^= (byte) 0x87; // R_128 constant for AES
        }
        return output;
    }

    /**
     * XORs b into a (in place): a[i] ^= b[i].
     */
    static void xorInPlace(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] ^= b[i];
        }
    }
}
