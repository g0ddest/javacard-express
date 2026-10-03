package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMKeys;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * MRZ-based key material for BAC and PACE, and the ICAO key derivation function (ICAO Doc 9303-11).
 *
 * <p>The MRZ password is the {@code MRZ_information}: document number, date of birth and date of expiry, each
 * followed by its check digit (4.3.2, App. D.2). Its SHA-1 hash is the PACE password encoding
 * {@code K = f(pi)} (9.7.3, Table 14); its first 16 bytes are the BAC key seed (App. D.2 step 4).</p>
 */
public final class PaceMrz {

    private static final int[] WEIGHT = {7, 3, 1};
    private static final int DOCUMENT_NUMBER_FIELD = 9;
    private static final int DATE_FIELD = 6;
    private static final int BAC_KEY_SEED_LENGTH = 16;

    private PaceMrz() {
    }

    /**
     * Builds the {@code MRZ_information} (ICAO Doc 9303-11, 4.3.2 step 1 and App. D.2).
     *
     * <p>A document number shorter than the 9-character MRZ field is padded with the filler {@code '<'}, as
     * printed in the MRZ ({@code "L898902C"} becomes {@code "L898902C<"}); a longer one is used completely
     * (9.7.3 Note: TD1 numbers continued in the optional data, without filler). Trailing fillers given by the
     * caller are normalised the same way.</p>
     *
     * @param documentNumber the document number ({@code 0-9}, {@code A-Z}, optional trailing {@code '<'})
     * @param dateOfBirth    the date of birth (YYMMDD, {@code '<'} for unknown parts)
     * @param dateOfExpiry   the date of expiry (YYMMDD)
     * @return the MRZ_information, e.g. {@code "L898902C<369080619406236"}
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if a field contains invalid characters or has a wrong length
     */
    public static String mrzInformation(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        String number = documentNumberField(documentNumber);
        String birth = dateField(dateOfBirth, "dateOfBirth");
        String expiry = dateField(dateOfExpiry, "dateOfExpiry");
        return number + checkDigit(number) + birth + checkDigit(birth) + expiry + checkDigit(expiry);
    }

    /**
     * Computes the PACE password encoding {@code K = f(pi) = SHA-1(MRZ_information)} for the MRZ password
     * (ICAO Doc 9303-11, 9.7.3, Table 14). The PACE password key is {@code K_pi = KDF(K, 3)}.
     *
     * @param documentNumber the document number
     * @param dateOfBirth    the date of birth (YYMMDD)
     * @param dateOfExpiry   the date of expiry (YYMMDD)
     * @return the 20-byte encoding K
     * @throws IllegalArgumentException if a field is invalid (see {@link #mrzInformation})
     */
    public static byte[] encodeMrzPassword(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        String mrzInformation = mrzInformation(documentNumber, dateOfBirth, dateOfExpiry);
        return CryptoUtil.sha1(mrzInformation.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Computes the BAC key seed: the most significant 16 bytes of {@code SHA-1(MRZ_information)} (ICAO Doc 9303-11,
     * 4.3.2 step 1, App. D.2 step 4). The Document Basic Access Keys are {@code KDF(Kseed, 1)} and
     * {@code KDF(Kseed, 2)} (9.7.2).
     *
     * @param documentNumber the document number
     * @param dateOfBirth    the date of birth (YYMMDD)
     * @param dateOfExpiry   the date of expiry (YYMMDD)
     * @return the 16-byte Kseed
     * @throws IllegalArgumentException if a field is invalid (see {@link #mrzInformation})
     */
    public static byte[] bacKeySeed(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        return Arrays.copyOf(encodeMrzPassword(documentNumber, dateOfBirth, dateOfExpiry), BAC_KEY_SEED_LENGTH);
    }

    /**
     * Computes {@code SHA-1(MRZ_information)}.
     *
     * @param documentNumber the document number (padded with '&lt;' to 9 characters if shorter)
     * @param dateOfBirth    the date of birth (YYMMDD)
     * @param dateOfExpiry   the date of expiry (YYMMDD)
     * @return the 20-byte hash, i.e. the PACE password encoding K (not the 16-byte BAC Kseed)
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if a field is invalid
     * @deprecated the name suggests the BAC key seed, which is only the first 16 bytes; use
     *             {@link #encodeMrzPassword} for PACE and {@link #bacKeySeed} for BAC
     */
    @Deprecated(since = "0.3.0")
    public static byte[] computeKSeed(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        return encodeMrzPassword(documentNumber, dateOfBirth, dateOfExpiry);
    }

    /**
     * Computes the ICAO 9303 check digit for an MRZ field (Doc 9303-3, 4.9).
     *
     * <p>Characters are weighted with repeating pattern {7, 3, 1}, mapped to
     * numeric values (0-9 as-is, A-Z → 10-35, '&lt;' → 0), and summed mod 10.</p>
     *
     * @param input the MRZ field string
     * @return the check digit (0-9)
     * @throws NullPointerException     if input is null
     * @throws IllegalArgumentException if input contains a character outside {@code 0-9 A-Z <}
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
     * Key Derivation Function (ICAO Doc 9303-11, 9.7.1): {@code keydata = H(K || c)} with a 32-bit big-endian
     * counter c.
     *
     * <p>SHA-1 for 3DES keys and 128-bit AES keys (key length up to 16 bytes), SHA-256 for 192- and 256-bit AES
     * keys; the key is the leading part of keydata. 3DES parity bits are not adjusted (9.7.1.1: OPTIONAL).</p>
     *
     * @param kSeed     the shared secret K
     * @param counter   the counter (1 for KEnc, 2 for KMAC, 3 for the PACE password key K_pi)
     * @param keyLength the desired key length in bytes
     * @return the derived key
     */
    public static byte[] kdf(byte[] kSeed, int counter, int keyLength) {
        byte[] input = new byte[kSeed.length + 4];
        System.arraycopy(kSeed, 0, input, 0, kSeed.length);
        input[input.length - 4] = (byte) ((counter >> 24) & 0xFF);
        input[input.length - 3] = (byte) ((counter >> 16) & 0xFF);
        input[input.length - 2] = (byte) ((counter >> 8) & 0xFF);
        input[input.length - 1] = (byte) (counter & 0xFF);

        byte[] hash = keyLength <= 16 ? CryptoUtil.sha1(input) : CryptoUtil.sha256(input);
        Arrays.fill(input, (byte) 0);
        return Arrays.copyOf(hash, keyLength);
    }

    /**
     * Derives the Secure Messaging keys {@code KSEnc = KDF(K, 1)} and {@code KSMAC = KDF(K, 2)}
     * (ICAO Doc 9303-11, 9.7.4).
     *
     * @param kSeed     the shared secret K
     * @param keyLength the desired key length in bytes (16, 24, or 32)
     * @return the derived session keys
     */
    public static SMKeys deriveKeys(byte[] kSeed, int keyLength) {
        byte[] encKey = kdf(kSeed, 1, keyLength);
        byte[] macKey = kdf(kSeed, 2, keyLength);
        return new SMKeys(encKey, macKey);
    }

    private static String documentNumberField(String documentNumber) {
        Objects.requireNonNull(documentNumber, "documentNumber");
        String number = stripTrailingFillers(documentNumber);
        if (number.isEmpty() || !number.chars().allMatch(c -> c >= '0' && c <= '9' || c >= 'A' && c <= 'Z')) {
            // The MRZ fields are the access password: never echo them in messages
            throw new IllegalArgumentException("documentNumber must consist of 0-9 and A-Z, optionally followed by"
                    + " fillers '<' (" + documentNumber.length() + " characters given)");
        }
        return number.length() >= DOCUMENT_NUMBER_FIELD ? number
                : number + "<".repeat(DOCUMENT_NUMBER_FIELD - number.length());
    }

    private static String stripTrailingFillers(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '<') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String dateField(String date, String name) {
        Objects.requireNonNull(date, name);
        if (date.length() != DATE_FIELD || !date.chars().allMatch(c -> c >= '0' && c <= '9' || c == '<')) {
            throw new IllegalArgumentException(name + " must be YYMMDD (digits, '<' for unknown parts; "
                    + date.length() + " characters given)");
        }
        return date;
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
