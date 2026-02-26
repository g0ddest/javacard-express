package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMKeys;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * MRZ-based key derivation for PACE (ICAO 9303 Part 11, Section 4.3.1).
 *
 * <p>Derives the shared password K_&pi; from Machine Readable Zone (MRZ) fields:
 * document number, date of birth, and date of expiry. Also provides the
 * Key Derivation Function (KDF) for session key generation.</p>
 */
public final class PaceMrz {

    private static final int[] WEIGHT = {7, 3, 1};

    private PaceMrz() {
    }

    /**
     * Computes the MRZ key seed (K_seed) from document fields.
     *
     * <p>K_seed = SHA-1(docNumber || checkDigit || dateOfBirth || checkDigit || dateOfExpiry || checkDigit)</p>
     *
     * @param documentNumber the document number (alphanumeric, pad with '&lt;' to 9 chars if shorter)
     * @param dateOfBirth    the date of birth (YYMMDD)
     * @param dateOfExpiry   the date of expiry (YYMMDD)
     * @return the 20-byte K_seed
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if any argument is empty
     */
    public static byte[] computeKSeed(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        Objects.requireNonNull(dateOfExpiry, "dateOfExpiry");
        if (documentNumber.isEmpty()) {
            throw new IllegalArgumentException("documentNumber must not be empty");
        }

        String composite = documentNumber + checkDigit(documentNumber)
                + dateOfBirth + checkDigit(dateOfBirth)
                + dateOfExpiry + checkDigit(dateOfExpiry);

        return CryptoUtil.sha1(composite.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Computes the ICAO 9303 check digit for an MRZ field.
     *
     * <p>Characters are weighted with repeating pattern {7, 3, 1}, mapped to
     * numeric values (0-9 as-is, A-Z → 10-35, '&lt;' → 0), and summed mod 10.</p>
     *
     * @param input the MRZ field string
     * @return the check digit (0-9)
     * @throws NullPointerException if input is null
     */
    public static int checkDigit(String input) {
        Objects.requireNonNull(input, "input");
        int sum = 0;
        for (int i = 0; i < input.length(); i++) {
            sum += charValue(input.charAt(i)) * WEIGHT[i % 3];
        }
        return sum % 10;
    }

    /**
     * Key Derivation Function (ICAO 9303, Section 9.7.1).
     *
     * <p>For key lengths &le; 16 bytes: SHA-1(K_seed || counter), truncated.<br>
     * For key lengths &gt; 16 bytes: SHA-256(K_seed || counter), truncated.</p>
     *
     * @param kSeed     the key seed
     * @param counter   the derivation counter (1 for K_enc, 2 for K_mac)
     * @param keyLength the desired key length in bytes
     * @return the derived key
     */
    public static byte[] kdf(byte[] kSeed, int counter, int keyLength) {
        byte[] input = new byte[kSeed.length + 4];
        System.arraycopy(kSeed, 0, input, 0, kSeed.length);
        // counter as 4-byte big-endian
        input[input.length - 4] = (byte) ((counter >> 24) & 0xFF);
        input[input.length - 3] = (byte) ((counter >> 16) & 0xFF);
        input[input.length - 2] = (byte) ((counter >> 8) & 0xFF);
        input[input.length - 1] = (byte) (counter & 0xFF);

        byte[] hash = keyLength <= 16 ? CryptoUtil.sha1(input) : CryptoUtil.sha256(input);
        return Arrays.copyOf(hash, keyLength);
    }

    /**
     * Derives K_enc and K_mac session keys from K_seed.
     *
     * <p>K_enc = KDF(K_seed, 1, keyLength), K_mac = KDF(K_seed, 2, keyLength).</p>
     *
     * @param kSeed     the key seed
     * @param keyLength the desired key length in bytes (16, 24, or 32)
     * @return the derived session keys
     */
    public static SMKeys deriveKeys(byte[] kSeed, int keyLength) {
        byte[] encKey = kdf(kSeed, 1, keyLength);
        byte[] macKey = kdf(kSeed, 2, keyLength);
        return new SMKeys(encKey, macKey);
    }

    /**
     * Maps an MRZ character to its numeric value.
     */
    private static int charValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        if (c == '<') return 0;
        throw new IllegalArgumentException("Invalid MRZ character: " + c);
    }
}
