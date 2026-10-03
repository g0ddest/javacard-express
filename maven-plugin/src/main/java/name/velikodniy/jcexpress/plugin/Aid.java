package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Application identifier (AID) of a Java Card package or applet.
 *
 * <p>JCVM 3.1 &sect;4.2.1: an AID is a 5-byte RID (resource identifier) followed by a PIX
 * (proprietary identifier extension) of 0 to 11 bytes, i.e. 5 to 16 bytes in total.
 * &sect;4.2.2.2 and &sect;6.6: the RID of every applet AID in a CAP file must equal the RID
 * of the package (CAP file) AID.
 *
 * @param bytes the AID bytes (5 to 16)
 */
record Aid(byte[] bytes) {

    /** RID length in bytes (JCVM 3.1 &sect;4.2.1, Table 4-1). */
    static final int RID_LENGTH = 5;

    /** Maximum AID length in bytes (JCVM 3.1 &sect;4.2.1). */
    static final int MAX_LENGTH = 16;

    private static final Pattern SEPARATORS = Pattern.compile("[\\s:]");
    private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]*");
    private static final String AID_RULE = "an AID is 5 to 16 bytes (10 to 32 hex digits): "
            + "a 5-byte RID followed by a PIX of up to 11 bytes (JCVM 3.1 §4.2.1)";

    /**
     * Creates an AID, validating its length (JCVM 3.1 &sect;4.2.1).
     *
     * @param bytes the AID bytes
     */
    Aid {
        if (bytes.length < RID_LENGTH || bytes.length > MAX_LENGTH) {
            throw new IllegalArgumentException("AID length " + bytes.length + " is invalid: " + AID_RULE);
        }
        bytes = bytes.clone();
    }

    /**
     * Parses a hex AID as written in the plugin configuration. Spaces and colons are
     * accepted as byte separators ({@code A0:00:00:00:62}).
     *
     * @param label what the AID identifies, used in error messages (e.g. {@code "packageAid"})
     * @param text  the configured value
     * @return the parsed AID
     * @throws MojoExecutionException if the value is not hex or not 5 to 16 bytes long
     */
    static Aid parse(String label, String text) throws MojoExecutionException {
        String hex = SEPARATORS.matcher(text == null ? "" : text).replaceAll("");
        String problem = null;
        if (!HEX.matcher(hex).matches()) {
            problem = "it contains characters that are not hex digits";
        } else if (hex.length() % 2 != 0) {
            problem = "it has an odd number of hex digits";
        } else if (hex.length() < 2 * RID_LENGTH || hex.length() > 2 * MAX_LENGTH) {
            problem = "it is " + hex.length() / 2 + " bytes long";
        }
        if (problem != null) {
            throw new MojoExecutionException("Invalid " + label + " '" + text + "': " + problem
                    + "; " + AID_RULE + ".");
        }
        return new Aid(HexFormat.of().parseHex(hex));
    }

    /**
     * Derives a deterministic development AID from a package name:
     * {@code F0 || SHA-1(packageName)[0..6]} (8 bytes). AIDs whose first nibble is {@code F}
     * are unregistered proprietary AIDs (ISO/IEC 7816-5); they are fine for development and
     * testing but not for production cards.
     *
     * <p>This is the package AID the converter generates when none is configured, so
     * zero-configuration builds keep their package AID.
     *
     * @param packageName the Java package name (dot notation)
     * @return the derived AID
     */
    @SuppressWarnings("java:S4790") // SHA-1 is used for a deterministic identifier, not for security
    static Aid derivedFromPackageName(String packageName) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(packageName.getBytes(StandardCharsets.UTF_8));
            byte[] aid = new byte[8];
            aid[0] = (byte) 0xF0;
            System.arraycopy(hash, 0, aid, 1, 7);
            return new Aid(aid);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    /**
     * Returns this AID extended by one PIX byte; the RID stays the same (JCVM 3.1 &sect;4.2.2.2).
     *
     * @param value the byte to append (0..255)
     * @return the extended AID
     * @throws IllegalStateException if this AID already has the maximum length
     */
    Aid withSuffix(int value) {
        if (bytes.length >= MAX_LENGTH) {
            throw new IllegalStateException("AID " + hex() + " already has the maximum length of 16 bytes");
        }
        byte[] extended = Arrays.copyOf(bytes, bytes.length + 1);
        extended[bytes.length] = (byte) value;
        return new Aid(extended);
    }

    /**
     * @param other another AID
     * @return {@code true} if both AIDs have the same 5-byte RID (JCVM 3.1 &sect;4.2.1)
     */
    boolean sameRid(Aid other) {
        return Arrays.equals(bytes, 0, RID_LENGTH, other.bytes, 0, RID_LENGTH);
    }

    /** @return the RID (first 5 bytes) as upper-case hex */
    String ridHex() {
        return HexFormat.of().withUpperCase().formatHex(bytes, 0, RID_LENGTH);
    }

    /** @return the AID as upper-case hex without separators */
    String hex() {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    /** @return {@code true} if no further PIX byte can be appended */
    boolean isMaximumLength() {
        return bytes.length == MAX_LENGTH;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Aid other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return hex();
    }
}
