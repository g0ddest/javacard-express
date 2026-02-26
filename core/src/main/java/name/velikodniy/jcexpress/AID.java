package name.velikodniy.jcexpress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Application Identifier (AID) for JavaCard applets.
 *
 * <p>Provides convenient factory methods for creating AIDs from hex strings,
 * byte values, or automatically from applet class names.</p>
 */
public final class AID {

    private final byte[] bytes;

    private AID(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    private static void validateLength(byte[] bytes) {
        if (bytes.length < 5 || bytes.length > 16) {
            throw new IllegalArgumentException(
                    "AID must be 5-16 bytes (ISO 7816-4), got " + bytes.length);
        }
    }

    /**
     * Creates an AID from a hex string.
     *
     * @param hex hex-encoded AID (e.g., "A0000000031010")
     * @return the AID
     */
    public static AID fromHex(String hex) {
        byte[] decoded = Hex.decode(hex);
        validateLength(decoded);
        return new AID(decoded);
    }

    /**
     * Creates an AID from individual byte values (as ints for convenience).
     *
     * @param bytes the AID bytes (each value 0x00–0xFF)
     * @return the AID
     */
    public static AID of(int... bytes) {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            b[i] = (byte) bytes[i];
        }
        validateLength(b);
        return new AID(b);
    }

    /**
     * Generates a deterministic AID from an applet class name.
     *
     * <p>Algorithm: SHA-1 of the fully qualified class name, take first 7 bytes,
     * prefix with 0xF0. Result is an 8-byte AID in the proprietary range.</p>
     *
     * @param appletClass the applet class
     * @return a deterministic AID
     */
    public static AID auto(Class<?> appletClass) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(appletClass.getName().getBytes(StandardCharsets.UTF_8));
            byte[] aid = new byte[8];
            aid[0] = (byte) 0xF0;
            System.arraycopy(hash, 0, aid, 1, 7);
            return new AID(aid);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    /**
     * Returns the AID as a byte array.
     *
     * @return a copy of the AID bytes
     */
    public byte[] toBytes() {
        return bytes.clone();
    }

    /**
     * Returns the AID as an uppercase hex string.
     *
     * @return hex representation of the AID
     */
    public String toHex() {
        return Hex.encode(bytes);
    }

    /**
     * Returns true if this AID starts with the given prefix AID.
     *
     * @param prefix the prefix to check
     * @return true if this AID begins with the prefix bytes
     */
    public boolean startsWith(AID prefix) {
        byte[] other = prefix.bytes;
        if (other.length > bytes.length) return false;
        return Arrays.equals(bytes, 0, other.length, other, 0, other.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AID aid)) return false;
        return Arrays.equals(bytes, aid.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "AID[" + toHex() + "]";
    }
}
