package javacardx.biometry1toN;

/**
 * Factory for one-to-many biometric matchers and for the reference template data they match against. The byte
 * constants other than {@link #DEFAULT_INITPARAM} identify the biometric type.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class Bio1toNBuilder {

    /** Biometric type: facial features. */
    public static final byte FACIAL_FEATURE = 1;
    /** Biometric type: voice print. */
    public static final byte VOICE_PRINT = 2;
    /** Biometric type: fingerprint. */
    public static final byte FINGERPRINT = 3;
    /** Biometric type: iris scan. */
    public static final byte IRIS_SCAN = 4;
    /** Biometric type: retina scan. */
    public static final byte RETINA_SCAN = 5;
    /** Biometric type: hand geometry. */
    public static final byte HAND_GEOMETRY = 6;
    /** Biometric type: handwritten signature. */
    public static final byte SIGNATURE = 7;
    /** Biometric type: keystroke dynamics. */
    public static final byte KEYSTROKES = 8;
    /** Biometric type: lip movement. */
    public static final byte LIP_MOVEMENT = 9;
    /** Biometric type: thermal face image. */
    public static final byte THERMAL_FACE = 10;
    /** Biometric type: thermal hand image. */
    public static final byte THERMAL_HAND = 11;
    /** Biometric type: gait. */
    public static final byte GAIT_STYLE = 12;
    /** Biometric type: body odor. */
    public static final byte BODY_ODOR = 13;
    /** Biometric type: DNA scan. */
    public static final byte DNA_SCAN = 14;
    /** Biometric type: ear geometry. */
    public static final byte EAR_GEOMETRY = 15;
    /** Biometric type: finger geometry. */
    public static final byte FINGER_GEOMETRY = 16;
    /** Biometric type: palm geometry. */
    public static final byte PALM_GEOMETRY = 17;
    /** Biometric type: vein pattern. */
    public static final byte VEIN_PATTERN = 18;
    /** A password treated as a biometric type. */
    public static final byte PASSWORD = 31;
    /** Requests the matching algorithm's default initialization parameters. */
    public static final byte DEFAULT_INITPARAM = 0;

    private Bio1toNBuilder() {
    }

    /**
     * Creates a matcher with the default matching algorithm.
     *
     * @param maxNbOfBioTemplateData number of reference template slots
     * @param bioType                the biometric type
     * @param tryLimit               number of consecutive failed matches allowed before the matcher is blocked
     * @return the new matcher
     * @throws Bio1toNException with UNSUPPORTED_BIO_TYPE if the type is not supported, or ILLEGAL_VALUE if a
     *                          parameter is not valid
     */
    public static OwnerBioMatcher buildBioMatcher(short maxNbOfBioTemplateData, byte bioType, byte tryLimit)
            throws Bio1toNException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a matcher for a specific matching algorithm.
     *
     * @param maxNbOfBioTemplateData number of reference template slots
     * @param bioType                the biometric type
     * @param tryLimit               number of consecutive failed matches allowed before the matcher is blocked
     * @param rid                    array holding the 5-byte RID of the provider of the matching algorithm
     * @param initParam              algorithm-specific initialization parameter, or {@link #DEFAULT_INITPARAM}
     * @return the new matcher
     * @throws Bio1toNException with UNSUPPORTED_BIO_TYPE if the type or algorithm is not supported, or
     *                          ILLEGAL_VALUE if a parameter is not valid
     */
    public static OwnerBioMatcher buildBioMatcher(short maxNbOfBioTemplateData, byte bioType, byte tryLimit,
            byte[] rid, byte initParam) throws Bio1toNException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates empty reference template data with the default format.
     *
     * @param bioType the biometric type
     * @return the new template data
     * @throws Bio1toNException with UNSUPPORTED_BIO_TYPE if the type is not supported
     */
    public static OwnerBioTemplateData buildBioTemplateData(byte bioType) throws Bio1toNException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates empty reference template data for a specific matching algorithm.
     *
     * @param bioType the biometric type
     * @param rid     array holding the 5-byte RID of the provider of the matching algorithm
     * @return the new template data
     * @throws Bio1toNException with UNSUPPORTED_BIO_TYPE if the type or algorithm is not supported
     */
    public static OwnerBioTemplateData buildBioTemplateData(byte bioType, byte[] rid) throws Bio1toNException {
        throw new RuntimeException("stub");
    }
}
