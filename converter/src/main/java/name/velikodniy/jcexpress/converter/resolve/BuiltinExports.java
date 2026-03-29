package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides built-in export file data for the standard JavaCard API packages, used during
 * <strong>Stage 4: Reference Resolution</strong> of the converter pipeline.
 *
 * <p>When the converter encounters references to standard JavaCard API classes (such as
 * {@code javacard.framework.Applet} or {@code javacard.security.MessageDigest}), it needs
 * to know the numeric token assigned to each class, method, and field. These tokens are
 * defined by the platform and must match exactly for binary compatibility with real
 * JavaCard cards.
 *
 * <p>This class eliminates the need to ship or parse Oracle SDK {@code .exp} files by
 * hard-coding the token assignments for all standard packages:
 * <ul>
 *   <li>{@code java.lang} -- JavaCard subset of the Java SE base classes (Object,
 *       Throwable, exception hierarchy)</li>
 *   <li>{@code javacard.framework} -- core applet API (Applet, APDU, AID, ISO7816,
 *       JCSystem, Util, OwnerPIN, exception classes, etc.)</li>
 *   <li>{@code javacard.security} -- cryptography API (Key, MessageDigest, Signature,
 *       Cipher keys, RandomData, KeyBuilder, etc.)</li>
 *   <li>{@code javacardx.crypto} -- extended crypto API (Cipher, KeyEncryption)</li>
 * </ul>
 *
 * <h2>Token Sources</h2>
 * <p>All token assignments are extracted from the Oracle JC 3.0.5u4 SDK export files
 * using the {@code exp2text} tool. The class tokens, method tokens, and field tokens
 * in these export files define the encoding used in the JCVM constant pool for external
 * references (JCVM spec 6.8).
 *
 * <h2>Version Support</h2>
 * <p>API version numbers vary across all 8 supported JavaCard specification versions:
 * 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, and 3.2.0. The
 * {@link #allBuiltinImports(int, JavaCardVersion)} method selects appropriate version
 * numbers based on the target specification.
 *
 * @see ImportedPackage
 * @see ReferenceResolver
 * @see name.velikodniy.jcexpress.converter.token.ExportFile
 * @see <a href="https://docs.oracle.com/javacard/3.0.5/JCVM/jcvm-spec-3_0_5.pdf">JCVM 3.0.5 spec, section 6.8 (Constant Pool Component)</a>
 */
@SuppressWarnings("java:S1192") // Intentional declarative data with repeated string literals for API token tables
public final class BuiltinExports {

    /** Utility class -- not instantiable. */
    private BuiltinExports() {}

    /** JavaCard framework package AID: A0 00 00 00 62 01 01 */
    private static final byte[] FRAMEWORK_AID = {
            (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x01
    };

    /** JavaCard security package AID: A0 00 00 00 62 01 02 */
    private static final byte[] SECURITY_AID = {
            (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x02
    };

    /** JavaCardX crypto package AID: A0 00 00 00 62 02 01 */
    private static final byte[] CRYPTO_AID = {
            (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x02, 0x01
    };

    /** java.lang package AID: A0 00 00 00 62 00 01 */
    private static final byte[] JAVA_LANG_AID = {
            (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x00, 0x01
    };

    /**
     * Returns built-in export file for a known JavaCard API package.
     *
     * @param packageName internal name (slash notation, e.g. "javacard/framework")
     * @return export file, or null if not a known built-in package
     */
    public static ExportFile getExport(String packageName) {
        return switch (packageName) {
            case "java/lang" -> javaLangExport();
            case "javacard/framework" -> frameworkExport();
            case "javacard/security" -> securityExport();
            case "javacardx/crypto" -> cryptoExport();
            default -> null;
        };
    }

    /**
     * Returns all built-in export files as ImportedPackage list.
     * Token assignment starts from the given base token.
     * Uses JC 3.0.5 API version numbers (default).
     *
     * @param baseToken starting token for package numbering
     * @return list of imported packages for all built-in APIs
     */
    public static List<ImportedPackage> allBuiltinImports(int baseToken) {
        return allBuiltinImports(baseToken, JavaCardVersion.V3_0_5);
    }

    /**
     * Returns all built-in export files as ImportedPackage list.
     * Token assignment starts from the given base token.
     * <p>
     * Order matches Oracle's convention: javacard.framework first, then java.lang,
     * then optional security/crypto. This ensures compatible package token
     * assignments in external constant pool references.
     * <p>
     * API version numbers vary by JavaCard specification version:
     * <ul>
     *   <li>JC 2.1.2: framework 1.0, java.lang 1.0, security 1.1, crypto 1.1</li>
     *   <li>JC 2.2.1: framework 1.2, java.lang 1.0, security 1.2, crypto 1.2</li>
     *   <li>JC 2.2.2: framework 1.3, java.lang 1.0, security 1.3, crypto 1.3</li>
     *   <li>JC 3.0.3: framework 1.4, java.lang 1.0, security 1.4, crypto 1.4</li>
     *   <li>JC 3.0.4: framework 1.5, java.lang 1.0, security 1.5, crypto 1.5</li>
     *   <li>JC 3.0.5: framework 1.6, java.lang 1.0, security 1.6, crypto 1.6</li>
     *   <li>JC 3.1.0: framework 1.8, java.lang 1.0, security 1.8, crypto 1.6</li>
     *   <li>JC 3.2.0: framework 1.8, java.lang 1.0, security 1.8, crypto 1.6</li>
     * </ul>
     *
     * @param baseToken starting token for package numbering
     * @param version   target JavaCard specification version
     * @return list of imported packages for all built-in APIs
     */
    public static List<ImportedPackage> allBuiltinImports(int baseToken, JavaCardVersion version) {
        List<ImportedPackage> result = new ArrayList<>();
        int token = baseToken;

        // API version numbers per JavaCard specification version
        // Source: Oracle SDK export files for each version; see research.md R-008
        int fwMinor;
        int secMinor;
        int cryptoMinor;
        switch (version) {
            case V2_1_2 -> { fwMinor = 0; secMinor = 1; cryptoMinor = 1; }
            case V2_2_1 -> { fwMinor = 2; secMinor = 2; cryptoMinor = 2; }
            case V2_2_2 -> { fwMinor = 3; secMinor = 3; cryptoMinor = 3; }
            case V3_0_3 -> { fwMinor = 4; secMinor = 4; cryptoMinor = 4; }
            case V3_0_4 -> { fwMinor = 5; secMinor = 5; cryptoMinor = 5; }
            case V3_1_0 -> { fwMinor = 8; secMinor = 8; cryptoMinor = 6; }
            case V3_2_0 -> { fwMinor = 9; secMinor = 8; cryptoMinor = 6; }
            default -> { fwMinor = 6; secMinor = 6; cryptoMinor = 6; } // V3_0_5
        }

        result.add(new ImportedPackage(token++, FRAMEWORK_AID, 1, fwMinor, frameworkExport()));
        result.add(new ImportedPackage(token++, JAVA_LANG_AID, 1, 0, javaLangExport()));
        result.add(new ImportedPackage(token++, SECURITY_AID, 1, secMinor, securityExport()));
        result.add(new ImportedPackage(token, CRYPTO_AID, 1, cryptoMinor, cryptoExport()));

        return result;
    }

    // ── java.lang (JavaCard subset) ──
    // Tokens from Oracle JC 3.0.5u4 SDK: java/lang/javacard/lang.exp

    private static ExportFile javaLangExport() {
        List<ExportFile.ClassExport> classes = new ArrayList<>();

        classes.add(new ExportFile.ClassExport("Object", 0, 0x0001, List.of(
                method("<init>", "()V", 0, 0x0001),
                method("equals", "(Ljava/lang/Object;)Z", 0)
        ), List.of()));

        classes.add(new ExportFile.ClassExport("Throwable", 1, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("Exception", 2, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("RuntimeException", 3, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("IndexOutOfBoundsException", 4, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("ArrayIndexOutOfBoundsException", 5, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("NegativeArraySizeException", 6, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("NullPointerException", 7, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("ClassCastException", 8, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("ArithmeticException", 9, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("SecurityException", 10, 0x0001, List.of(), List.of()));
        classes.add(new ExportFile.ClassExport("ArrayStoreException", 11, 0x0001, List.of(), List.of()));

        return new ExportFile("java/lang", JAVA_LANG_AID, 1, 0, classes);
    }

    // ── javacard.framework ──
    // Tokens from Oracle JC 3.0.5u4 SDK: javacard/framework/javacard/framework.exp

    private static ExportFile frameworkExport() {
        List<ExportFile.ClassExport> classes = new ArrayList<>();

        // ISO7816 (interface, class token 0)
        classes.add(new ExportFile.ClassExport("ISO7816", 0, 0x0201, List.of(), List.of()));

        // PIN (interface, class token 1)
        classes.add(new ExportFile.ClassExport("PIN", 1, 0x0201, List.of(
                method("check", "([BSB)Z", 0),
                method("getTriesRemaining", "()B", 1),
                method("isValidated", "()Z", 2),
                method("reset", "()V", 3)
        ), List.of()));

        // Shareable (interface, class token 2)
        classes.add(new ExportFile.ClassExport("Shareable", 2, 0x0201, List.of(), List.of()));

        // Applet (class token 3)
        // Static method tokens: 0 = <init>, 1 = install, 2 = reSelectingApplet
        // Virtual method tokens: 0-7 (equals through process)
        classes.add(new ExportFile.ClassExport("Applet", 3, 0x0401, List.of(
                // Static methods (including constructor)
                method("<init>", "()V", 0, 0x0001),
                method("install", "([BSB)V", 1, 0x0009),
                method("reSelectingApplet", "()Z", 2, 0x0009),
                // Virtual methods (token 0 = equals from Object)
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("register", "()V", 1),
                method("register", "([BSB)V", 2),
                method("selectingApplet", "()Z", 3),
                method("deselect", "()V", 4),
                method("getShareableInterfaceObject", "(Ljavacard/framework/AID;B)Ljavacard/framework/Shareable;", 5),
                method("select", "()Z", 6),
                method("process", "(Ljavacard/framework/APDU;)V", 7)
        ), List.of()));

        // CardException (class token 4)
        // Static method token 0 = <init>(S)V
        classes.add(new ExportFile.ClassExport("CardException", 4, 0x0001, List.of(
                method("<init>", "(S)V", 0, 0x0001),
                method("throwIt", "(S)V", 1, 0x0009),
                method("getReason", "()S", 1),
                method("setReason", "(S)V", 2)
        ), List.of()));

        // CardRuntimeException (class token 5)
        // Static method token 0 = <init>(S)V
        classes.add(new ExportFile.ClassExport("CardRuntimeException", 5, 0x0001, List.of(
                method("<init>", "(S)V", 0, 0x0001),
                method("throwIt", "(S)V", 1, 0x0009),
                method("getReason", "()S", 1),
                method("setReason", "(S)V", 2)
        ), List.of()));

        // AID (class token 6)
        // Static method token 0 = <init>([BSB)V
        classes.add(new ExportFile.ClassExport("AID", 6, 0x0001, List.of(
                method("<init>", "([BSB)V", 0, 0x0001),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("RIDEquals", "(Ljavacard/framework/AID;)Z", 1),
                method("equals", "([BSB)Z", 2),
                method("getBytes", "([BS)B", 3),
                method("partialEquals", "([BSB)Z", 4),
                method("getPartialBytes", "(S[BSB)B", 5)
        ), List.of()));

        // ISOException (class token 7)
        classes.add(new ExportFile.ClassExport("ISOException", 7, 0x0001, List.of(
                method("throwIt", "(S)V", 1, 0x0009),
                method("getReason", "()S", 1),
                method("setReason", "(S)V", 2)
        ), List.of()));

        // JCSystem (class token 8)
        classes.add(new ExportFile.ClassExport("JCSystem", 8, 0x0001, List.of(
                method("abortTransaction", "()V", 0, 0x0009),
                method("beginTransaction", "()V", 1, 0x0009),
                method("commitTransaction", "()V", 2, 0x0009),
                method("getAID", "()Ljavacard/framework/AID;", 3, 0x0009),
                method("getMaxCommitCapacity", "()S", 5, 0x0009),
                method("getPreviousContextAID", "()Ljavacard/framework/AID;", 6, 0x0009),
                method("getTransactionDepth", "()B", 7, 0x0009),
                method("getVersion", "()S", 9, 0x0009),
                method("isTransient", "(Ljava/lang/Object;)B", 10, 0x0009),
                method("lookupAID", "([BSB)Ljavacard/framework/AID;", 11, 0x0009),
                method("makeTransientBooleanArray", "(SB)[Z", 12, 0x0009),
                method("makeTransientByteArray", "(SB)[B", 13, 0x0009),
                method("makeTransientObjectArray", "(SB)[Ljava/lang/Object;", 14, 0x0009),
                method("makeTransientShortArray", "(SB)[S", 15, 0x0009),
                method("getAvailableMemory", "(B)S", 16, 0x0009),
                method("isObjectDeletionSupported", "()Z", 17, 0x0009),
                method("requestObjectDeletion", "()V", 18, 0x0009),
                method("getAssignedChannel", "()B", 19, 0x0009),
                method("isAppletActive", "(Ljavacard/framework/AID;)Z", 20, 0x0009),
                method("makeGlobalArray", "(BS)Ljava/lang/Object;", 21, 0x0009),
                method("getAvailableMemory", "([SSB)V", 22, 0x0009),
                method("getAppletShareableInterfaceObject", "(Ljavacard/framework/AID;B)Ljavacard/framework/Shareable;", 4, 0x0009),
                method("getUnusedCommitCapacity", "()S", 8, 0x0009)
        ), List.of()));

        // OwnerPIN (class token 9)
        classes.add(new ExportFile.ClassExport("OwnerPIN", 9, 0x0001, List.of(
                method("<init>", "(BB)V", 0, 0x0001),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("check", "([BSB)Z", 1),
                method("getTriesRemaining", "()B", 2),
                method("getValidatedFlag", "()Z", 3, 0x0004),
                method("isValidated", "()Z", 4),
                method("reset", "()V", 5),
                method("resetAndUnblock", "()V", 6),
                method("setValidatedFlag", "(Z)V", 7, 0x0004),
                method("update", "([BSB)V", 8)
        ), List.of()));

        // APDU (class token 10)
        classes.add(new ExportFile.ClassExport("APDU", 10, 0x0001, List.of(
                // Static methods
                method("getInBlockSize", "()S", 0, 0x0009),
                method("getOutBlockSize", "()S", 1, 0x0009),
                method("getProtocol", "()B", 2, 0x0009),
                method("waitExtension", "()V", 3, 0x0009),
                method("getCurrentAPDU", "()Ljavacard/framework/APDU;", 4, 0x0009),
                method("getCurrentAPDUBuffer", "()[B", 5, 0x0009),
                method("getCLAChannel", "()B", 6, 0x0009),
                // Virtual methods (token 0 = equals from Object)
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("getBuffer", "()[B", 1),
                method("getNAD", "()B", 2),
                method("receiveBytes", "(S)S", 3),
                method("sendBytes", "(SS)V", 4),
                method("sendBytesLong", "([BSS)V", 5),
                method("setIncomingAndReceive", "()S", 6),
                method("setOutgoing", "()S", 7),
                method("setOutgoingAndSend", "(SS)V", 8),
                method("setOutgoingLength", "(S)V", 9),
                method("setOutgoingNoChaining", "()S", 10),
                method("getCurrentState", "()B", 11),
                method("isCommandChainingCLA", "()Z", 12),
                method("isSecureMessagingCLA", "()Z", 13),
                method("isISOInterindustryCLA", "()Z", 14),
                method("getIncomingLength", "()S", 15),
                method("getOffsetCdata", "()S", 16),
                method("isValidCLA", "()Z", 17)
        ), List.of()));

        // PINException (class token 11)
        classes.add(new ExportFile.ClassExport("PINException", 11, 0x0001, List.of(
                method("throwIt", "(S)V", 1, 0x0009)
        ), List.of()));

        // APDUException (class token 12)
        classes.add(new ExportFile.ClassExport("APDUException", 12, 0x0001, List.of(
                method("throwIt", "(S)V", 1, 0x0009)
        ), List.of()));

        // SystemException (class token 13)
        classes.add(new ExportFile.ClassExport("SystemException", 13, 0x0001, List.of(
                method("throwIt", "(S)V", 1, 0x0009)
        ), List.of()));

        // TransactionException (class token 14)
        classes.add(new ExportFile.ClassExport("TransactionException", 14, 0x0001, List.of(
                method("throwIt", "(S)V", 1, 0x0009)
        ), List.of()));

        // UserException (class token 15)
        classes.add(new ExportFile.ClassExport("UserException", 15, 0x0001, List.of(
                method("throwIt", "(S)V", 2, 0x0009)
        ), List.of()));

        // Util (class token 16)
        classes.add(new ExportFile.ClassExport("Util", 16, 0x0001, List.of(
                method("arrayCompare", "([BS[BSS)B", 0, 0x0009),
                method("arrayCopy", "([BS[BSS)S", 1, 0x0009),
                method("arrayCopyNonAtomic", "([BS[BSS)S", 2, 0x0009),
                method("arrayFillNonAtomic", "([BSSB)S", 3, 0x0009),
                method("getShort", "([BS)S", 4, 0x0009),
                method("makeShort", "(BB)S", 5, 0x0009),
                method("setShort", "([BSS)S", 6, 0x0009),
                method("arrayFill", "([BSSB)S", 7, 0x0009)
        ), List.of()));

        // MultiSelectable (interface, class token 17)
        classes.add(new ExportFile.ClassExport("MultiSelectable", 17, 0x0201, List.of(
                method("select", "(Z)Z", 0),
                method("deselect", "(Z)V", 1)
        ), List.of()));

        // AppletEvent (interface, class token 18)
        classes.add(new ExportFile.ClassExport("AppletEvent", 18, 0x0201, List.of(
                method("uninstall", "()V", 0)
        ), List.of()));

        // OwnerPINx (interface, class token 19)
        classes.add(new ExportFile.ClassExport("OwnerPINx", 19, 0x0201, List.of(
                method("check", "([BSB)Z", 0),
                method("getTriesRemaining", "()B", 1),
                method("isValidated", "()Z", 2),
                method("reset", "()V", 3),
                method("update", "([BSB)V", 4),
                method("getTryLimit", "()B", 5),
                method("setTryLimit", "(B)V", 6),
                method("setTriesRemaining", "(B)V", 7)
        ), List.of()));

        // OwnerPINxWithPredecrement (interface, class token 20)
        classes.add(new ExportFile.ClassExport("OwnerPINxWithPredecrement", 20, 0x0201, List.of(
                method("check", "([BSB)Z", 0),
                method("getTriesRemaining", "()B", 1),
                method("isValidated", "()Z", 2),
                method("reset", "()V", 3),
                method("update", "([BSB)V", 4),
                method("getTryLimit", "()B", 5),
                method("setTryLimit", "(B)V", 6),
                method("setTriesRemaining", "(B)V", 7),
                method("decrementTriesRemaining", "()B", 8)
        ), List.of()));

        // OwnerPINBuilder (class token 21)
        classes.add(new ExportFile.ClassExport("OwnerPINBuilder", 21, 0x0001, List.of(
                method("buildOwnerPIN", "(BBB)Ljavacard/framework/PIN;", 0, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0)
        ), List.of()));

        // SensitiveArrays (class token 22)
        classes.add(new ExportFile.ClassExport("SensitiveArrays", 22, 0x0001, List.of(
                method("assertIntegrity", "(Ljava/lang/Object;)V", 0, 0x0009),
                method("isIntegritySensitive", "(Ljava/lang/Object;)Z", 1, 0x0009),
                method("isIntegritySensitiveArraysSupported", "()Z", 2, 0x0009),
                method("makeIntegritySensitiveArray", "(BBS)Ljava/lang/Object;", 3, 0x0009),
                method("clearArray", "(Ljava/lang/Object;)S", 4, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0)
        ), List.of()));

        return new ExportFile("javacard/framework", FRAMEWORK_AID, 1, 6, classes);
    }

    // ── javacard.security ──
    // Tokens from Oracle JC 3.0.5u4 SDK: javacard/security/javacard/security.exp

    private static ExportFile securityExport() {
        List<ExportFile.ClassExport> classes = new ArrayList<>();

        // Key (interface, class token 0)
        classes.add(new ExportFile.ClassExport("Key", 0, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3)
        ), List.of()));

        // DSAKey (interface, class token 1)
        classes.add(new ExportFile.ClassExport("DSAKey", 1, 0x0201, List.of(
                method("getG", "([BS)S", 0),
                method("getP", "([BS)S", 1),
                method("getQ", "([BS)S", 2),
                method("setG", "([BSS)V", 3),
                method("setP", "([BSS)V", 4),
                method("setQ", "([BSS)V", 5)
        ), List.of()));

        // PrivateKey (interface, class token 2)
        classes.add(new ExportFile.ClassExport("PrivateKey", 2, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3)
        ), List.of()));

        // PublicKey (interface, class token 3)
        classes.add(new ExportFile.ClassExport("PublicKey", 3, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3)
        ), List.of()));

        // SecretKey (interface, class token 4)
        classes.add(new ExportFile.ClassExport("SecretKey", 4, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3)
        ), List.of()));

        // DSAPrivateKey (interface, class token 5)
        classes.add(new ExportFile.ClassExport("DSAPrivateKey", 5, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("getG", "([BS)S", 4),
                method("getP", "([BS)S", 5),
                method("getQ", "([BS)S", 6),
                method("setG", "([BSS)V", 7),
                method("setP", "([BSS)V", 8),
                method("setQ", "([BSS)V", 9),
                method("getX", "([BS)S", 10),
                method("setX", "([BSS)V", 11)
        ), List.of()));

        // DSAPublicKey (interface, class token 6)
        classes.add(new ExportFile.ClassExport("DSAPublicKey", 6, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("getG", "([BS)S", 4),
                method("getP", "([BS)S", 5),
                method("getQ", "([BS)S", 6),
                method("setG", "([BSS)V", 7),
                method("setP", "([BSS)V", 8),
                method("setQ", "([BSS)V", 9),
                method("getY", "([BS)S", 10),
                method("setY", "([BSS)V", 11)
        ), List.of()));

        // RSAPrivateCrtKey (interface, class token 7)
        classes.add(new ExportFile.ClassExport("RSAPrivateCrtKey", 7, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("getDP1", "([BS)S", 4),
                method("getDQ1", "([BS)S", 5),
                method("getP", "([BS)S", 6),
                method("getPQ", "([BS)S", 7),
                method("getQ", "([BS)S", 8),
                method("setDP1", "([BSS)V", 9),
                method("setDQ1", "([BSS)V", 10),
                method("setP", "([BSS)V", 11),
                method("setPQ", "([BSS)V", 12),
                method("setQ", "([BSS)V", 13)
        ), List.of()));

        // RSAPrivateKey (interface, class token 8)
        classes.add(new ExportFile.ClassExport("RSAPrivateKey", 8, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("getExponent", "([BS)S", 4),
                method("getModulus", "([BS)S", 5),
                method("setExponent", "([BSS)V", 6),
                method("setModulus", "([BSS)V", 7)
        ), List.of()));

        // RSAPublicKey (interface, class token 9)
        classes.add(new ExportFile.ClassExport("RSAPublicKey", 9, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("getExponent", "([BS)S", 4),
                method("getModulus", "([BS)S", 5),
                method("setExponent", "([BSS)V", 6),
                method("setModulus", "([BSS)V", 7)
        ), List.of()));

        // DESKey (interface, class token 10)
        classes.add(new ExportFile.ClassExport("DESKey", 10, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("getKey", "([BS)B", 4),
                method("setKey", "([BS)V", 5)
        ), List.of()));

        // MessageDigest (class token 11)
        classes.add(new ExportFile.ClassExport("MessageDigest", 11, 0x0401, List.of(
                method("getInstance", "(BZ)Ljavacard/security/MessageDigest;", 0, 0x0009),
                method("getInitializedMessageDigestInstance", "(BZ)Ljavacard/security/InitializedMessageDigest;", 2, 0x0009),
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("getLength", "()B", 3),
                method("reset", "()V", 4),
                method("update", "([BSS)V", 5)
        ), List.of()));

        // CryptoException (class token 12)
        classes.add(new ExportFile.ClassExport("CryptoException", 12, 0x0001, List.of(
                method("throwIt", "(S)V", 1, 0x0009),
                method("getReason", "()S", 1),
                method("setReason", "(S)V", 2)
        ), List.of()));

        // KeyBuilder (class token 13)
        classes.add(new ExportFile.ClassExport("KeyBuilder", 13, 0x0001, List.of(
                method("buildKey", "(BSZ)Ljavacard/security/Key;", 0, 0x0009),
                method("buildKey", "(BBSZ)Ljavacard/security/Key;", 1, 0x0009),
                method("buildKeyWithSharedDomain", "(BBLjavacard/security/Key;Z)Ljavacard/security/Key;", 2, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0)
        ), List.of()));

        // RandomData (class token 14)
        classes.add(new ExportFile.ClassExport("RandomData", 14, 0x0401, List.of(
                method("getInstance", "(B)Ljavacard/security/RandomData;", 0, 0x0009),
                method("generateData", "([BSS)V", 1),
                method("setSeed", "([BSS)V", 2),
                method("nextBytes", "([BSS)S", 3),
                method("getAlgorithm", "()B", 4)
        ), List.of()));

        // Signature (class token 15)
        classes.add(new ExportFile.ClassExport("Signature", 15, 0x0401, List.of(
                method("getInstance", "(BZ)Ljavacard/security/Signature;", 0, 0x0009),
                method("getInstance", "(BBBZ)Ljavacard/security/Signature;", 2, 0x0009),
                method("getAlgorithm", "()B", 1),
                method("getLength", "()S", 2),
                method("init", "(Ljavacard/security/Key;B)V", 3),
                method("init", "(Ljavacard/security/Key;B[BSS)V", 4),
                method("sign", "([BSS[BS)S", 5),
                method("update", "([BSS)V", 6),
                method("verify", "([BSS[BSS)Z", 7),
                method("setInitialDigest", "([BSS[BSS)V", 8),
                method("signPreComputedHash", "([BSS[BS)S", 9),
                method("verifyPreComputedHash", "([BSS[BSS)Z", 10),
                method("getMessageDigestAlgorithm", "()B", 11),
                method("getCipherAlgorithm", "()B", 12),
                method("getPaddingAlgorithm", "()B", 13)
        ), List.of()));

        // KeyPair (class token 16)
        classes.add(new ExportFile.ClassExport("KeyPair", 16, 0x0001, List.of(
                method("<init>", "(BS)V", 0, 0x0001),
                method("<init>", "(Ljavacard/security/PublicKey;Ljavacard/security/PrivateKey;)V", 1, 0x0001),
                method("genKeyPair", "()V", 1),
                method("getPrivate", "()Ljavacard/security/PrivateKey;", 2),
                method("getPublic", "()Ljavacard/security/PublicKey;", 3)
        ), List.of()));

        // ECKey (interface, class token 17)
        classes.add(new ExportFile.ClassExport("ECKey", 17, 0x0201, List.of(
                method("setFieldFP", "([BSS)V", 0),
                method("setFieldF2M", "(S)V", 1),
                method("setFieldF2M", "(SSS)V", 2),
                method("setA", "([BSS)V", 3),
                method("setB", "([BSS)V", 4),
                method("setG", "([BSS)V", 5),
                method("setR", "([BSS)V", 6),
                method("setK", "(S)V", 7),
                method("getField", "([BS)S", 8),
                method("getA", "([BS)S", 9),
                method("getB", "([BS)S", 10),
                method("getG", "([BS)S", 11),
                method("getR", "([BS)S", 12),
                method("getK", "()S", 13),
                method("copyDomainParametersFrom", "(Ljavacard/security/ECKey;)V", 14)
        ), List.of()));

        // ECPrivateKey (interface, class token 18)
        classes.add(new ExportFile.ClassExport("ECPrivateKey", 18, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setFieldFP", "([BSS)V", 4),
                method("setFieldF2M", "(S)V", 5),
                method("setFieldF2M", "(SSS)V", 6),
                method("setA", "([BSS)V", 7),
                method("setB", "([BSS)V", 8),
                method("setG", "([BSS)V", 9),
                method("setR", "([BSS)V", 10),
                method("setK", "(S)V", 11),
                method("getField", "([BS)S", 12),
                method("getA", "([BS)S", 13),
                method("getB", "([BS)S", 14),
                method("getG", "([BS)S", 15),
                method("getR", "([BS)S", 16),
                method("getK", "()S", 17),
                method("setS", "([BSS)V", 18),
                method("getS", "([BS)S", 19),
                method("copyDomainParametersFrom", "(Ljavacard/security/ECKey;)V", 20)
        ), List.of()));

        // ECPublicKey (interface, class token 19)
        classes.add(new ExportFile.ClassExport("ECPublicKey", 19, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setFieldFP", "([BSS)V", 4),
                method("setFieldF2M", "(S)V", 5),
                method("setFieldF2M", "(SSS)V", 6),
                method("setA", "([BSS)V", 7),
                method("setB", "([BSS)V", 8),
                method("setG", "([BSS)V", 9),
                method("setR", "([BSS)V", 10),
                method("setK", "(S)V", 11),
                method("getField", "([BS)S", 12),
                method("getA", "([BS)S", 13),
                method("getB", "([BS)S", 14),
                method("getG", "([BS)S", 15),
                method("getR", "([BS)S", 16),
                method("getK", "()S", 17),
                method("setW", "([BSS)V", 18),
                method("getW", "([BS)S", 19),
                method("copyDomainParametersFrom", "(Ljavacard/security/ECKey;)V", 20)
        ), List.of()));

        // AESKey (interface, class token 20)
        classes.add(new ExportFile.ClassExport("AESKey", 20, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setKey", "([BS)V", 4),
                method("getKey", "([BS)B", 5)
        ), List.of()));

        // Checksum (class token 21)
        classes.add(new ExportFile.ClassExport("Checksum", 21, 0x0401, List.of(
                method("getInstance", "(BZ)Ljavacard/security/Checksum;", 0, 0x0009),
                method("init", "([BSS)V", 1),
                method("getAlgorithm", "()B", 2),
                method("doFinal", "([BSS[BS)S", 3),
                method("update", "([BSS)V", 4)
        ), List.of()));

        // KeyAgreement (class token 22)
        classes.add(new ExportFile.ClassExport("KeyAgreement", 22, 0x0401, List.of(
                method("getInstance", "(BZ)Ljavacard/security/KeyAgreement;", 0, 0x0009),
                method("init", "(Ljavacard/security/PrivateKey;)V", 1),
                method("getAlgorithm", "()B", 2),
                method("generateSecret", "([BSS[BS)S", 3)
        ), List.of()));

        // HMACKey (interface, class token 23)
        classes.add(new ExportFile.ClassExport("HMACKey", 23, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setKey", "([BSS)V", 4),
                method("getKey", "([BS)B", 5)
        ), List.of()));

        // KoreanSEEDKey (interface, class token 24)
        classes.add(new ExportFile.ClassExport("KoreanSEEDKey", 24, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setKey", "([BS)V", 4),
                method("getKey", "([BS)B", 5)
        ), List.of()));

        // SignatureMessageRecovery (interface, class token 25)
        classes.add(new ExportFile.ClassExport("SignatureMessageRecovery", 25, 0x0201, List.of(
                method("init", "(Ljavacard/security/Key;B)V", 0),
                method("beginVerify", "([BSS)S", 1),
                method("sign", "([BSS[BS[SS)S", 2),
                method("verify", "([BSS)Z", 3),
                method("getAlgorithm", "()B", 4),
                method("getLength", "()S", 5),
                method("update", "([BSS)V", 6)
        ), List.of()));

        // InitializedMessageDigest (class token 26)
        classes.add(new ExportFile.ClassExport("InitializedMessageDigest", 26, 0x0401, List.of(
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("getLength", "()B", 3),
                method("reset", "()V", 4),
                method("update", "([BSS)V", 5),
                method("setInitialDigest", "([BSS[BSS)V", 6)
        ), List.of()));

        // DHKey (interface, class token 27)
        classes.add(new ExportFile.ClassExport("DHKey", 27, 0x0201, List.of(
                method("setP", "([BSS)V", 0),
                method("setQ", "([BSS)V", 1),
                method("setG", "([BSS)V", 2),
                method("getP", "([BS)S", 3),
                method("getQ", "([BS)S", 4),
                method("getG", "([BS)S", 5)
        ), List.of()));

        // DHPrivateKey (interface, class token 28)
        classes.add(new ExportFile.ClassExport("DHPrivateKey", 28, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setP", "([BSS)V", 4),
                method("setQ", "([BSS)V", 5),
                method("setG", "([BSS)V", 6),
                method("getP", "([BS)S", 7),
                method("getQ", "([BS)S", 8),
                method("getG", "([BS)S", 9),
                method("setX", "([BSS)V", 10),
                method("getX", "([BS)S", 11)
        ), List.of()));

        // DHPublicKey (interface, class token 29)
        classes.add(new ExportFile.ClassExport("DHPublicKey", 29, 0x0201, List.of(
                method("clearKey", "()V", 0),
                method("getSize", "()S", 1),
                method("getType", "()B", 2),
                method("isInitialized", "()Z", 3),
                method("setP", "([BSS)V", 4),
                method("setQ", "([BSS)V", 5),
                method("setG", "([BSS)V", 6),
                method("getP", "([BS)S", 7),
                method("getQ", "([BS)S", 8),
                method("getG", "([BS)S", 9),
                method("setY", "([BSS)V", 10),
                method("getY", "([BS)S", 11)
        ), List.of()));

        // MessageDigest$OneShot (final class, class token 30)
        classes.add(new ExportFile.ClassExport("MessageDigest$OneShot", 30, 0x0011, List.of(
                method("open", "(B)Ljavacard/security/MessageDigest$OneShot;", 0, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("getLength", "()B", 3),
                method("reset", "()V", 4),
                method("update", "([BSS)V", 5),
                method("close", "()V", 6)
        ), List.of()));

        // InitializedMessageDigest$OneShot (final class, class token 31)
        classes.add(new ExportFile.ClassExport("InitializedMessageDigest$OneShot", 31, 0x0011, List.of(
                method("open", "(B)Ljavacard/security/InitializedMessageDigest$OneShot;", 0, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("getLength", "()B", 3),
                method("reset", "()V", 4),
                method("update", "([BSS)V", 5),
                method("setInitialDigest", "([BSS[BSS)V", 6),
                method("close", "()V", 7)
        ), List.of()));

        // RandomData$OneShot (final class, class token 32)
        classes.add(new ExportFile.ClassExport("RandomData$OneShot", 32, 0x0011, List.of(
                method("open", "(B)Ljavacard/security/RandomData$OneShot;", 0, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("generateData", "([BSS)V", 1),
                method("setSeed", "([BSS)V", 2),
                method("nextBytes", "([BSS)S", 3),
                method("getAlgorithm", "()B", 4),
                method("close", "()V", 5)
        ), List.of()));

        // Signature$OneShot (final class, class token 33)
        classes.add(new ExportFile.ClassExport("Signature$OneShot", 33, 0x0011, List.of(
                method("open", "(BBB)Ljavacard/security/Signature$OneShot;", 0, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("getAlgorithm", "()B", 1),
                method("getLength", "()S", 2),
                method("init", "(Ljavacard/security/Key;B)V", 3),
                method("init", "(Ljavacard/security/Key;B[BSS)V", 4),
                method("sign", "([BSS[BS)S", 5),
                method("update", "([BSS)V", 6),
                method("verify", "([BSS[BSS)Z", 7),
                method("setInitialDigest", "([BSS[BSS)V", 8),
                method("signPreComputedHash", "([BSS[BS)S", 9),
                method("verifyPreComputedHash", "([BSS[BSS)Z", 10),
                method("getMessageDigestAlgorithm", "()B", 11),
                method("getCipherAlgorithm", "()B", 12),
                method("getPaddingAlgorithm", "()B", 13),
                method("close", "()V", 14)
        ), List.of()));

        return new ExportFile("javacard/security", SECURITY_AID, 1, 6, classes);
    }

    // ── javacardx.crypto ──
    // Tokens from Oracle JC 3.0.5u4 SDK: javacardx/crypto/javacard/crypto.exp

    private static ExportFile cryptoExport() {
        List<ExportFile.ClassExport> classes = new ArrayList<>();

        // KeyEncryption (interface, class token 0)
        classes.add(new ExportFile.ClassExport("KeyEncryption", 0, 0x0201, List.of(
                method("getKeyCipher", "()Ljavacardx/crypto/Cipher;", 0),
                method("setKeyCipher", "(Ljavacardx/crypto/Cipher;)V", 1)
        ), List.of()));

        // Cipher (abstract class, class token 1)
        // Virtual token order: equals(0), doFinal(1), getAlgorithm(2), init(3,4), update(5)
        classes.add(new ExportFile.ClassExport("Cipher", 1, 0x0401, List.of(
                method("getInstance", "(BZ)Ljavacardx/crypto/Cipher;", 0, 0x0009),
                method("getInstance", "(BBZ)Ljavacardx/crypto/Cipher;", 2, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("init", "(Ljavacard/security/Key;B)V", 3),
                method("init", "(Ljavacard/security/Key;B[BSS)V", 4),
                method("update", "([BSS[BS)S", 5),
                method("getCipherAlgorithm", "()B", 6),
                method("getPaddingAlgorithm", "()B", 7)
        ), List.of()));

        // Cipher$OneShot (final class, class token 2)
        classes.add(new ExportFile.ClassExport("Cipher$OneShot", 2, 0x0011, List.of(
                method("open", "(BB)Ljavacardx/crypto/Cipher$OneShot;", 0, 0x0009),
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("init", "(Ljavacard/security/Key;B)V", 3),
                method("init", "(Ljavacard/security/Key;B[BSS)V", 4),
                method("update", "([BSS[BS)S", 5),
                method("getCipherAlgorithm", "()B", 6),
                method("getPaddingAlgorithm", "()B", 7),
                method("close", "()V", 8)
        ), List.of()));

        // AEADCipher (abstract class, class token 3)
        classes.add(new ExportFile.ClassExport("AEADCipher", 3, 0x0401, List.of(
                method("equals", "(Ljava/lang/Object;)Z", 0),
                method("doFinal", "([BSS[BS)S", 1),
                method("getAlgorithm", "()B", 2),
                method("init", "(Ljavacard/security/Key;B)V", 3),
                method("init", "(Ljavacard/security/Key;B[BSS)V", 4),
                method("update", "([BSS[BS)S", 5),
                method("getCipherAlgorithm", "()B", 6),
                method("getPaddingAlgorithm", "()B", 7),
                method("init", "(Ljavacard/security/Key;B[BSSSSS)V", 8),
                method("updateAAD", "([BSS)V", 9),
                method("retrieveTag", "([BSS)S", 10),
                method("verifyTag", "([BSSS)Z", 11)
        ), List.of()));

        return new ExportFile("javacardx/crypto", CRYPTO_AID, 1, 6, classes);
    }

    // ── Helpers ──

    private static ExportFile.MethodExport method(String name, String descriptor, int token) {
        return new ExportFile.MethodExport(name, descriptor, token, 0x0001);
    }

    private static ExportFile.MethodExport method(String name, String descriptor, int token, int flags) {
        return new ExportFile.MethodExport(name, descriptor, token, flags);
    }
}
