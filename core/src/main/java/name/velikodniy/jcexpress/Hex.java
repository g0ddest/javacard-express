package name.velikodniy.jcexpress;

/**
 * Hex encoding and decoding utilities.
 */
public final class Hex {

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    private Hex() {
    }

    /**
     * Encodes a byte array to an uppercase hex string.
     *
     * @param bytes the bytes to encode
     * @return uppercase hex string
     */
    public static String encode(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_CHARS[v >>> 4];
            chars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Encodes a byte array to an uppercase hex string with spaces between bytes.
     *
     * @param bytes the bytes to encode
     * @return spaced hex string (e.g., "80 01 00 00")
     */
    public static String encodeSpaced(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        if (bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3 - 1);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            int v = bytes[i] & 0xFF;
            sb.append(HEX_CHARS[v >>> 4]);
            sb.append(HEX_CHARS[v & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * Decodes a hex string to a byte array. Spaces are ignored.
     *
     * @param hex the hex string to decode (case-insensitive, spaces allowed)
     * @return decoded bytes
     */
    public static byte[] decode(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex must not be null");
        }
        String clean = hex.replace(" ", "");
        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length, got: " + clean.length());
        }
        byte[] bytes = new byte[clean.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = Character.digit(clean.charAt(i * 2), 16);
            int lo = Character.digit(clean.charAt(i * 2 + 1), 16);
            if (hi == -1 || lo == -1) {
                throw new IllegalArgumentException("Invalid hex character in: " + hex);
            }
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        return bytes;
    }
}
