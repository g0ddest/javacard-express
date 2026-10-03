package name.velikodniy.jcexpress.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * The AIDs of a card test run: every AID starts with the run's prefix, and the package and applet (module) AIDs
 * are derived from the package and class names, so the same test addresses the same AIDs on every backend.
 *
 * <ul>
 *   <li>package AID: prefix + the first 2 bytes of SHA-256 of the package name;</li>
 *   <li>module (applet) AID: package AID + the first 2 bytes of SHA-256 of the class's binary name, so it keeps
 *       the package AID's RID (JCVM 3.1 section 4.2.2.2: applet AIDs share the RID of their package);</li>
 *   <li>instance AID: the module AID, or prefix + an explicit suffix ({@link #aid(String)}).</li>
 * </ul>
 *
 * <p>The prefix is 4 to 10 bytes, so every derived AID stays within the 5 to 16 bytes of ISO/IEC 7816-5. A run
 * takes it from the setting {@value #PREFIX_SETTING}; without it, the project's own prefix
 * ({@link #forProject(String)}) when the applets come from a module built by the Maven plugin
 * ({@link BuildDescriptor}), otherwise {@value #DEFAULT_PREFIX}. On a real card it must be one the live-card harness
 * accepts.</p>
 */
public final class AidScheme {

    /** Setting (system property or JUnit configuration parameter) that gives the AID prefix as hex. */
    public static final String PREFIX_SETTING = "jcx.aidPrefix";

    /** The default prefix, the same as the live-card harness's default {@code aidPrefix}. */
    public static final String DEFAULT_PREFIX = "F04A4358";

    private static final int MIN_PREFIX = 4;
    private static final int MAX_PREFIX = 10;
    private static final int HASH_BYTES = 2;
    private static final int PROJECT_HASH_BYTES = 4;
    /** First byte of a project prefix: a proprietary, unregistered AID (ISO/IEC 7816-5). */
    private static final byte PROPRIETARY = (byte) 0xF0;

    private final byte[] prefix;

    private AidScheme(byte[] prefix) {
        this.prefix = prefix;
    }

    /**
     * Creates the scheme for a prefix given as hex.
     *
     * @param prefixHex the prefix (hex, 4 to 10 bytes)
     * @return the scheme
     * @throws IllegalArgumentException if the prefix is not hex or has another length
     */
    public static AidScheme of(String prefixHex) {
        byte[] prefix = Hex.decode(prefixHex);
        if (prefix.length < MIN_PREFIX || prefix.length > MAX_PREFIX) {
            throw new IllegalArgumentException("The AID prefix must be " + MIN_PREFIX + " to " + MAX_PREFIX
                    + " bytes, got " + prefix.length + " (" + prefixHex + ")");
        }
        return new AidScheme(prefix);
    }

    /**
     * Creates the scheme of a project's tests: {@code F0} and the first 4 bytes of SHA-256 of
     * {@code "project " + groupId:artifactId}. The prefix is a proprietary AID (ISO/IEC 7816-5) of the project's
     * own, so the tests of different projects on one development card never touch each other's applets, and the
     * leftovers of a project's interrupted run are its own.
     *
     * @param project {@code groupId:artifactId}, as in {@link BuildDescriptor#project()}
     * @return the scheme
     */
    public static AidScheme forProject(String project) {
        byte[] prefix = new byte[1 + PROJECT_HASH_BYTES];
        prefix[0] = PROPRIETARY;
        System.arraycopy(digest("project " + project), 0, prefix, 1, PROJECT_HASH_BYTES);
        return new AidScheme(prefix);
    }

    /**
     * Returns the prefix.
     *
     * @return the AID prefix as upper-case hex
     */
    public String prefix() {
        return Hex.encode(prefix);
    }

    /**
     * Returns the package (load file) AID of a Java package.
     *
     * @param packageName the package name, for example {@code com.example.wallet}
     * @return prefix + 2 bytes derived from the name
     */
    public AID packageAid(String packageName) {
        return concat(prefix, hash("package " + packageName));
    }

    /**
     * Returns the module (applet) AID of an applet class.
     *
     * @param appletClass the applet class
     * @return the package AID of its package + 2 bytes derived from its binary name
     */
    public AID moduleAid(Class<?> appletClass) {
        return concat(packageAid(appletClass.getPackageName()).toBytes(), hash("class " + appletClass.getName()));
    }

    /**
     * Returns prefix + suffix.
     *
     * @param suffixHex the suffix (hex, at least 1 byte)
     * @return the AID
     * @throws IllegalArgumentException if the suffix is not hex or the AID would be longer than 16 bytes
     */
    public AID aid(String suffixHex) {
        byte[] suffix = Hex.decode(suffixHex);
        if (suffix.length == 0) {
            throw new IllegalArgumentException("An AID suffix needs at least one byte");
        }
        return concat(prefix, suffix);
    }

    /**
     * Returns the instance AID: the module AID, or prefix + the suffix when one is given.
     *
     * @param appletClass the applet class
     * @param suffixHex   the instance suffix (hex), or empty
     * @return the instance AID
     */
    public AID instanceAid(Class<?> appletClass, String suffixHex) {
        return suffixHex.isEmpty() ? moduleAid(appletClass) : aid(suffixHex);
    }

    private static AID concat(byte[] head, byte[] tail) {
        byte[] aid = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, aid, head.length, tail.length);
        if (aid.length > 16) {
            throw new IllegalArgumentException("AID " + Hex.encode(aid) + " is longer than 16 bytes");
        }
        return AID.fromHex(Hex.encode(aid));
    }

    private static byte[] hash(String text) {
        return Arrays.copyOf(digest(text), HASH_BYTES);
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    @Override
    public String toString() {
        return "AidScheme[prefix=" + prefix() + "]";
    }
}
