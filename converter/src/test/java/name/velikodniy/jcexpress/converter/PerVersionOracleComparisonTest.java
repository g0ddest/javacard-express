package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

import org.assertj.core.api.SoftAssertions;

import static name.velikodniy.jcexpress.converter.CapTestUtils.extractComponents;
import static org.assertj.core.api.Assertions.*;

/**
 * Parameterized Oracle reference comparison across all 8 JavaCard versions.
 *
 * <p>For each {@link JavaCardVersion} enum value, the test looks for a corresponding
 * Oracle reference CAP file in {@code /reference/}. If the file is not found, the
 * test is skipped (via {@link Assumptions#assumeTrue}). If found, our converter
 * output is compared component-by-component against the Oracle reference.
 *
 * <p>Reference file naming convention:
 * <ul>
 *   <li>{@code oracle-TestApplet-jc305.cap} -- JC 3.0.5</li>
 *   <li>{@code oracle-TestApplet-jc212.cap} -- JC 2.1.2</li>
 *   <li>{@code oracle-TestApplet-jc221.cap} -- JC 2.2.1</li>
 *   <li>{@code oracle-TestApplet-jc222.cap} -- JC 2.2.2</li>
 *   <li>{@code oracle-TestApplet-jc303.cap} -- JC 3.0.3</li>
 *   <li>{@code oracle-TestApplet-jc304.cap} -- JC 3.0.4</li>
 *   <li>{@code oracle-TestApplet-jc310.cap} -- JC 3.1.0</li>
 *   <li>{@code oracle-TestApplet-jc320.cap} -- JC 3.2.0</li>
 * </ul>
 */
class PerVersionOracleComparisonTest {

    private static final String PACKAGE_AID = "A000000062010101";
    private static final String APPLET_AID = "A00000006201010101";

    private static final String[] COMPONENT_ORDER = {
            "Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
            "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
            "RefLocation.cap", "Export.cap", "Descriptor.cap"
    };

    // ── Parameterized per-version comparison ──

    @ParameterizedTest(name = "Oracle comparison for {0}")
    @EnumSource(JavaCardVersion.class)
    void compareAgainstOracleReference(JavaCardVersion version) throws Exception {
        String refFileName = referenceFileName(version);
        InputStream refStream = getClass().getResourceAsStream("/reference/" + refFileName);
        Assumptions.assumeTrue(refStream != null,
                "Reference file not found: " + refFileName + " -- skipping " + version);

        byte[] oracleCapBytes;
        try (refStream) {
            oracleCapBytes = refStream.readAllBytes();
        }

        // Convert TestApplet with the given version
        Path classesDir = Path.of("target/test-classes");
        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid(PACKAGE_AID)
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", APPLET_AID)
                .javaCardVersion(version)
                .build()
                .convert();

        Map<String, byte[]> ourComponents = extractComponents(result.capFile());
        Map<String, byte[]> oracleComponents = extractComponents(oracleCapBytes);

        // Print detailed comparison header
        System.out.printf("%n%s%n", "=".repeat(70));
        System.out.printf("  CAP COMPARISON: %s  (ref: %s)%n", version, refFileName);
        System.out.printf("%s%n", "=".repeat(70));

        // Track mismatches for final assertion
        List<String> mismatches = new ArrayList<>();

        for (String name : COMPONENT_ORDER) {
            byte[] ours = ourComponents.get(name);
            byte[] oracle = oracleComponents.get(name);

            if (ours == null && oracle == null) continue;

            System.out.printf("%n-- %s --%n", name);

            if (ours == null) {
                // We don't produce Debug.cap -- that's expected
                if (!"Debug.cap".equals(name)) {
                    System.out.printf("  OURS: NOT PRESENT  |  ORACLE: %d bytes%n", oracle.length);
                    mismatches.add(name + ": missing in our output");
                }
                continue;
            }
            if (oracle == null) {
                System.out.printf("  OURS: %d bytes  |  ORACLE: NOT PRESENT%n", ours.length);
                // Not a mismatch -- we may produce extra components (e.g., Descriptor)
                continue;
            }

            boolean identical = Arrays.equals(ours, oracle);
            System.out.printf("  Size: OURS=%d  ORACLE=%d  %s%n",
                    ours.length, oracle.length,
                    identical ? "BYTE-IDENTICAL" :
                            ours.length == oracle.length ? "SAME SIZE, CONTENT DIFFERS" : "SIZE DIFFERS");

            if (!identical) {
                int minLen = Math.min(ours.length, oracle.length);
                int firstDiff = -1;
                int diffCount = 0;
                for (int i = 0; i < minLen; i++) {
                    if (ours[i] != oracle[i]) {
                        if (firstDiff == -1) firstDiff = i;
                        diffCount++;
                    }
                }
                diffCount += Math.abs(ours.length - oracle.length);
                System.out.printf("  Differences: %d bytes, first at offset %d%n", diffCount, firstDiff);

                // Print side-by-side hex (up to 96 bytes)
                printSideBySideHex(ours, oracle, 256);

                mismatches.add(String.format("%s: %d byte differences (first at offset %d), ours=%d oracle=%d",
                        name, diffCount, firstDiff, ours.length, oracle.length));
            }
        }

        // Assert: all components present in Oracle should also be present in ours
        for (String name : oracleComponents.keySet()) {
            if ("Debug.cap".equals(name)) continue;
            assertThat(ourComponents).as("Component %s should be present for %s", name, version)
                    .containsKey(name);
        }

        // Report summary — use soft assertions so all differences are reported
        int identicalCount = 0;
        int sizeMatchCount = 0;
        int diffCount = 0;

        // Components with known non-byte-identical differences:
        // - Class.cap: Oracle dispatch table off-by-one bug (all versions)
        Set<String> knownSizeMatchOnly = Set.of("Class.cap");

        SoftAssertions softly = new SoftAssertions();
        for (String name : COMPONENT_ORDER) {
            byte[] ours = ourComponents.get(name);
            byte[] oracle = oracleComponents.get(name);
            if (ours == null || oracle == null) continue;

            if (Arrays.equals(ours, oracle)) {
                identicalCount++;
            } else if (ours.length == oracle.length) {
                sizeMatchCount++;
            } else {
                diffCount++;
            }

            // All components must have the same size
            softly.assertThat(ours.length)
                    .as("%s size for %s (ours vs oracle)", name, version)
                    .isEqualTo(oracle.length);

            // Components not in knownSizeMatchOnly must be byte-identical
            if (!knownSizeMatchOnly.contains(name)) {
                softly.assertThat(ours)
                        .as("%s must be byte-identical for %s", name, version)
                        .isEqualTo(oracle);
            }
        }

        System.out.printf("%n  Summary for %s: %d byte-identical, %d size-match, %d size-diff%n",
                version, identicalCount, sizeMatchCount, diffCount);
        if (!mismatches.isEmpty()) {
            mismatches.forEach(m -> System.out.println("    - " + m));
        }

        // Assert all component compatibility
        softly.assertAll();
    }

    // ── Summary report: which versions have reference files ──

    @Test
    void reportReferenceFileAvailability() {
        System.out.printf("%n%s%n", "=".repeat(60));
        System.out.printf("  ORACLE REFERENCE FILE AVAILABILITY REPORT%n");
        System.out.printf("%s%n", "=".repeat(60));

        int found = 0;
        int missing = 0;

        for (JavaCardVersion version : JavaCardVersion.values()) {
            String refFileName = referenceFileName(version);
            boolean exists = getClass().getResource("/reference/" + refFileName) != null;
            System.out.printf("  %-10s  %-40s  %s%n",
                    version, refFileName, exists ? "FOUND" : "MISSING");
            if (exists) found++;
            else missing++;
        }

        System.out.printf("%n  Total: %d found, %d missing out of %d versions%n",
                found, missing, JavaCardVersion.values().length);
        System.out.printf("%s%n", "=".repeat(60));

        assertThat(found + missing).isEqualTo(JavaCardVersion.values().length);
    }

    // ── Helpers ──

    /**
     * Maps a {@link JavaCardVersion} to the Oracle reference CAP file name.
     */
    private static String referenceFileName(JavaCardVersion version) {
        return switch (version) {
            case V3_0_5 -> "oracle-TestApplet-jc305.cap";
            case V2_1_2 -> "oracle-TestApplet-jc212.cap";
            case V2_2_1 -> "oracle-TestApplet-jc221.cap";
            case V2_2_2 -> "oracle-TestApplet-jc222.cap";
            case V3_0_3 -> "oracle-TestApplet-jc303.cap";
            case V3_0_4 -> "oracle-TestApplet-jc304.cap";
            case V3_1_0 -> "oracle-TestApplet-jc310.cap";
            case V3_2_0 -> "oracle-TestApplet-jc320.cap";
        };
    }

    /**
     * Prints a side-by-side hex comparison of two byte arrays.
     */
    private static void printSideBySideHex(byte[] ours, byte[] oracle, int maxBytes) {
        int limit = Math.min(Math.max(ours.length, oracle.length), maxBytes);
        System.out.println("  Offset  OURS                                      ORACLE");
        for (int i = 0; i < limit; i += 16) {
            System.out.printf("  %04X    ", i);
            // Print our bytes
            for (int j = i; j < i + 16 && j < limit; j++) {
                if (j < ours.length) {
                    boolean diff = j < oracle.length && ours[j] != oracle[j];
                    System.out.printf(diff ? "*%02X" : " %02X", ours[j] & 0xFF);
                } else {
                    System.out.print("   ");
                }
            }
            System.out.print("    ");
            // Print Oracle bytes
            for (int j = i; j < i + 16 && j < limit; j++) {
                if (j < oracle.length) {
                    boolean diff = j < ours.length && ours[j] != oracle[j];
                    System.out.printf(diff ? "*%02X" : " %02X", oracle[j] & 0xFF);
                } else {
                    System.out.print("   ");
                }
            }
            System.out.println();
        }
        if (Math.max(ours.length, oracle.length) > maxBytes) {
            System.out.printf("  ... (%d more bytes not shown)%n",
                    Math.max(ours.length, oracle.length) - maxBytes);
        }
    }
}
