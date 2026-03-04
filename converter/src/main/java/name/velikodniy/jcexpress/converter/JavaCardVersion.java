package name.velikodniy.jcexpress.converter;

/**
 * Supported JavaCard specification versions with their corresponding CAP file format versions.
 *
 * <p>Each enum constant maps a JavaCard platform version to the binary CAP format version
 * written into the {@code major_version} and {@code minor_version} fields of the
 * Header component (JCVM spec Section 6.3). The CAP format version determines which
 * components and bytecodes are valid in the generated file and which API version numbers
 * are used for built-in import references (e.g., {@code javacard.framework}).
 *
 * <h2>CAP Format Version Table</h2>
 *
 * <p>The following table summarizes the mapping between JavaCard specification versions
 * and their corresponding CAP format versions, as defined in the JCVM specification
 * and verified against Oracle SDK reference output:
 *
 * <table>
 *   <caption>JavaCard specification to CAP format version mapping</caption>
 *   <tr><th>JavaCard Version</th><th>CAP Format</th><th>Notes</th></tr>
 *   <tr><td>2.1</td><td>1.0</td><td>Original format (not supported)</td></tr>
 *   <tr><td>2.1.1</td><td>2.1</td><td>Introduced format 2.x (not supported)</td></tr>
 *   <tr><td><b>2.1.2</b></td><td><b>2.1</b></td><td>{@link #V2_1_2} -- oldest supported version</td></tr>
 *   <tr><td><b>2.2.1</b></td><td><b>2.1</b></td><td>{@link #V2_2_1} -- classic 2.x line</td></tr>
 *   <tr><td><b>2.2.2</b></td><td><b>2.1</b></td><td>{@link #V2_2_2} -- widely deployed classic cards</td></tr>
 *   <tr><td><b>3.0.3</b></td><td><b>2.1</b></td><td>{@link #V3_0_3} -- first 3.0.x; ships API 3.0.1</td></tr>
 *   <tr><td><b>3.0.4</b></td><td><b>2.1</b></td><td>{@link #V3_0_4} -- Oracle SDK produces 2.1 despite spec mentioning 2.2</td></tr>
 *   <tr><td><b>3.0.5</b></td><td><b>2.1</b></td><td>{@link #V3_0_5} -- default; verified against Oracle SDK 3.0.5u3</td></tr>
 *   <tr><td><b>3.1.0</b></td><td><b>2.3</b></td><td>{@link #V3_1_0} -- verified against Oracle SDK 3.1.0</td></tr>
 *   <tr><td><b>3.2.0</b></td><td><b>2.3</b></td><td>{@link #V3_2_0} -- latest supported version</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <p>Pass the desired version to the converter builder:
 * <pre>{@code
 * Converter converter = Converter.builder()
 *     .classesDirectory(classesDir)
 *     .packageName("com.example")
 *     .javaCardVersion(JavaCardVersion.V3_0_5)
 *     .build();
 * }</pre>
 *
 * <p>The version affects:
 * <ul>
 *   <li>The {@code major_version} / {@code minor_version} fields in the CAP Header component</li>
 *   <li>The API package version numbers used for built-in import references
 *       (e.g., {@code javacard.framework} 1.3 for JC 3.0.5 vs. 1.6 for JC 3.1.0)</li>
 * </ul>
 *
 * @see Converter.Builder#javaCardVersion(JavaCardVersion)
 */
public enum JavaCardVersion {

    /**
     * JavaCard 2.1.2 -- CAP format version 2.1.
     *
     * <p>The oldest supported version. API package versions: framework 1.1,
     * security 1.1, crypto 1.1, java.lang 1.0.
     */
    V2_1_2(2, 1),

    /**
     * JavaCard 2.2.1 -- CAP format version 2.1.
     *
     * <p>Classic 2.x line. API package versions: framework 1.2,
     * security 1.2, crypto 1.2, java.lang 1.0.
     */
    V2_2_1(2, 1),

    /**
     * JavaCard 2.2.2 -- CAP format version 2.1.
     *
     * <p>The last version in the classic 2.x line, still widely deployed on
     * SIM cards and banking smart cards. API package versions: framework 1.3,
     * security 1.3, crypto 1.3, java.lang 1.0.
     */
    V2_2_2(2, 1),

    /**
     * JavaCard 3.0.3 -- CAP format version 2.1.
     *
     * <p>First 3.0.x release (ships API level 3.0.1). Shares core token
     * assignments with 3.0.4 and 3.0.5. API package versions: framework 1.4,
     * security 1.4, crypto 1.4, java.lang 1.0.
     */
    V3_0_3(2, 1),

    /**
     * JavaCard 3.0.4 -- CAP format version 2.1.
     *
     * <p>Although the JC 3.0.4 specification mentions format 2.2, the Oracle SDK 3.0.4
     * converter produces CAP files with format version 2.1. We match the Oracle behavior
     * for binary compatibility. Shares core token assignments with 3.0.3 and 3.0.5.
     * API package versions: framework 1.5, security 1.5, crypto 1.5, java.lang 1.0.
     */
    V3_0_4(2, 1),

    /**
     * JavaCard 3.0.5 -- CAP format version 2.1 (default).
     *
     * <p>The most commonly targeted version for new JavaCard development.
     * This is the default version used by the converter when none is explicitly specified.
     * Format verified against CAP files produced by Oracle SDK 3.0.5u3.
     * API package versions: framework 1.6, security 1.6, crypto 1.6, java.lang 1.0.
     */
    V3_0_5(2, 1),

    /**
     * JavaCard 3.1.0 -- CAP format version 2.3.
     *
     * <p>Introduces format 2.3 with extended API packages and updated
     * version numbers for built-in imports. Verified against Oracle SDK 3.1.0 output.
     * API package versions: framework 1.8, security 1.8, crypto 1.6, java.lang 1.0.
     */
    V3_1_0(2, 3),

    /**
     * JavaCard 3.2.0 -- CAP format version 2.3.
     *
     * <p>The latest supported specification version. Uses the same CAP format as JC 3.1.0
     * but with updated API version numbers for built-in import references.
     * API package versions: framework 1.9, security 1.8, crypto 1.6, java.lang 1.0.
     */
    V3_2_0(2, 3);

    private final int formatMajor;
    private final int formatMinor;

    JavaCardVersion(int formatMajor, int formatMinor) {
        this.formatMajor = formatMajor;
        this.formatMinor = formatMinor;
    }

    /**
     * Returns the CAP format major version number for this JavaCard version.
     *
     * <p>Written into the {@code major_version} field of the Header component
     * (JCVM spec Section 6.3).
     *
     * @return the major version (currently always {@code 2} for all supported versions)
     */
    public int formatMajor() {
        return formatMajor;
    }

    /**
     * Returns the CAP format minor version number for this JavaCard version.
     *
     * <p>Written into the {@code minor_version} field of the Header component
     * (JCVM spec Section 6.3).
     *
     * @return the minor version ({@code 1} for JC 2.1.2-3.0.3/3.0.5, {@code 2} for JC 3.0.4, {@code 3} for JC 3.1.0/3.2.0)
     */
    public int formatMinor() {
        return formatMinor;
    }
}
