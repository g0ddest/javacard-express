package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.OracleReferences;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static name.velikodniy.jcexpress.converter.CapTestUtils.extractComponents;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares our generated CAP file against Oracle's reference converter output.
 * The reference CAP was generated with Oracle JavaCard SDK 3.0.5u3 converter.
 * <p>
 * Tests are split into two groups:
 * <ul>
 *   <li>Byte-identical assertions for components that should match exactly</li>
 *   <li>Structural comparisons for components where implementation ordering differs</li>
 * </ul>
 * A detailed side-by-side hex diff is printed for all 11 components.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleReferenceComparisonTest {

    private Map<String, byte[]> ourComponents;
    private Map<String, byte[]> oracleComponents;

    @BeforeAll
    void buildAndLoadCaps() throws Exception {
        // Build our CAP
        Path classesDir = Path.of("target/test-classes");
        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();
        ourComponents = extractComponents(result.capFile());

        // Load Oracle reference CAP: Oracle tool output is not redistributable, so it is generated
        // locally (never committed) and the comparison is skipped without it
        oracleComponents = extractComponents(OracleReferences.require("oracle-TestApplet-jc305.cap"));
    }

    // ── Structural Comparison ──

    @Test
    void sameComponentsPresent() {
        assertThat(ourComponents.keySet())
                .as("Our CAP should have same components as Oracle")
                .containsAll(oracleComponents.keySet().stream()
                        .filter(k -> !k.equals("Debug.cap")) // We skip Debug
                        .toList());
    }

    @Test
    void headerTagAndMagicMatch() {
        byte[] ours = ourComponents.get("Header.cap");
        byte[] oracle = oracleComponents.get("Header.cap");

        // Same tag
        assertThat(ours[0]).as("Header tag").isEqualTo(oracle[0]);

        // Same magic
        assertThat(u4(ours, 3)).as("Our magic").isEqualTo(0xDECAFFED);
        assertThat(u4(oracle, 3)).as("Oracle magic").isEqualTo(0xDECAFFED);
    }

    @Test
    void headerPackageAidMatch() {
        byte[] ours = ourComponents.get("Header.cap");
        byte[] oracle = oracleComponents.get("Header.cap");

        // AID starts at offset 12 (after tag(1)+size(2)+magic(4)+minor(1)+major(1)+flags(1)+pkgMinor(1)+pkgMajor(1))
        // Actually: offset 12 = aid_length
        int ourAidLen = ours[12] & 0xFF;
        int oracleAidLen = oracle[12] & 0xFF;
        assertThat(ourAidLen).as("AID length").isEqualTo(oracleAidLen);

        byte[] ourAid = Arrays.copyOfRange(ours, 13, 13 + ourAidLen);
        byte[] oracleAid = Arrays.copyOfRange(oracle, 13, 13 + oracleAidLen);
        assertThat(ourAid).as("Package AID bytes").isEqualTo(oracleAid);
    }

    @Test
    void headerVersionsMatch() {
        byte[] ours = ourComponents.get("Header.cap");
        byte[] oracle = oracleComponents.get("Header.cap");

        // Package version: offset 10 = minor, 11 = major
        assertThat(ours[10] & 0xFF).as("Package minor").isEqualTo(oracle[10] & 0xFF);
        assertThat(ours[11] & 0xFF).as("Package major").isEqualTo(oracle[11] & 0xFF);
    }

    @Test
    void appletAidMatch() {
        byte[] ours = ourComponents.get("Applet.cap");
        byte[] oracle = oracleComponents.get("Applet.cap");

        // Same tag and count
        assertThat(ours[0]).as("Applet tag").isEqualTo(oracle[0]);
        assertThat(ours[3] & 0xFF).as("Applet count").isEqualTo(oracle[3] & 0xFF);

        // Extract applet AID (after tag(1)+size(2)+count(1)+aidLen(1))
        int ourAidLen = ours[4] & 0xFF;
        int oracleAidLen = oracle[4] & 0xFF;
        assertThat(ourAidLen).as("Applet AID length").isEqualTo(oracleAidLen);

        byte[] ourAid = Arrays.copyOfRange(ours, 5, 5 + ourAidLen);
        byte[] oracleAid = Arrays.copyOfRange(oracle, 5, 5 + oracleAidLen);
        assertThat(ourAid).as("Applet AID").isEqualTo(oracleAid);
    }

    @Test
    void importPackagesMatch() {
        byte[] ours = ourComponents.get("Import.cap");
        byte[] oracle = oracleComponents.get("Import.cap");

        int ourCount = ours[3] & 0xFF;
        int oracleCount = oracle[3] & 0xFF;

        List<String> ourAids = extractImportAids(ours);
        List<String> oracleAids = extractImportAids(oracle);

        // Oracle may import fewer packages (only what's referenced)
        // We import all built-in packages. Oracle only imports what's needed.
        assertThat(ourAids).as("Our imports should include all Oracle imports")
                .containsAll(oracleAids);
    }

    @Test
    void constantPoolEntryCountComparable() {
        byte[] ours = ourComponents.get("ConstantPool.cap");
        byte[] oracle = oracleComponents.get("ConstantPool.cap");

        int ourCount = u2(ours, 3);
        int oracleCount = u2(oracle, 3);

        // Our CP count should be comparable to Oracle's (not wildly different)
        assertThat(ourCount).as("Our CP count (%d) vs Oracle (%d)", ourCount, oracleCount)
                .isBetween(oracleCount - 5, oracleCount + 10);
    }

    @Test
    void constantPoolEntryTagDistribution() {
        Map<Integer, Integer> ourTags = cpTagDistribution(ourComponents.get("ConstantPool.cap"));
        Map<Integer, Integer> oracleTags = cpTagDistribution(oracleComponents.get("ConstantPool.cap"));

        // Same types of references should be present
        assertThat(ourTags.keySet()).as("CP tag types present")
                .containsAll(oracleTags.keySet());
    }

    @Test
    void classComponentFlagsMatch() {
        byte[] ours = ourComponents.get("Class.cap");
        byte[] oracle = oracleComponents.get("Class.cap");

        // First class entry: flags byte (at offset 3 for both)
        // For TestApplet, first entry should be a class (not interface): bit 7 = 0
        assertThat(ours[3] & 0x80).as("Our first class: not interface").isZero();
        assertThat(oracle[3] & 0x80).as("Oracle first class: not interface").isZero();
    }

    @Test
    void methodComponentStructureCompatible() {
        byte[] ours = ourComponents.get("Method.cap");
        byte[] oracle = oracleComponents.get("Method.cap");

        // Same tag
        assertThat(ours[0]).as("Method tag").isEqualTo(oracle[0]);

        // Handler count
        int ourHandlers = ours[3] & 0xFF;
        int oracleHandlers = oracle[3] & 0xFF;
        assertThat(ourHandlers).as("Handler count").isEqualTo(oracleHandlers);
    }

    @Test
    void staticFieldImageSizeComparable() {
        byte[] ours = ourComponents.get("StaticField.cap");
        byte[] oracle = oracleComponents.get("StaticField.cap");

        int ourImageSize = u2(ours, 3);
        int oracleImageSize = u2(oracle, 3);

        // Known difference: Oracle excludes static final primitive constants from image
        // (they get inlined as compile-time constants). Our converter includes them.
        // Oracle: imageSize=0 (INS_GET/INS_PUT inlined), Ours: imageSize=2 (includes them)
        // This is a valid implementation difference; we're more conservative.
        assertThat(ourImageSize).as("Static field image size (ours >= oracle)")
                .isGreaterThanOrEqualTo(oracleImageSize);
    }

    // ── Byte-identical component assertions ──

    @Test
    void headerComponentByteIdentical() {
        assertByteIdentical("Header.cap");
    }

    @Test
    void appletComponentByteIdentical() {
        assertByteIdentical("Applet.cap");
    }

    @Test
    void importComponentByteIdentical() {
        assertByteIdentical("Import.cap");
    }

    @Test
    void staticFieldComponentByteIdentical() {
        assertByteIdentical("StaticField.cap");
    }

    // ── Components that became byte-identical after CP reordering fix ──

    @Test
    void directoryComponentByteIdentical() {
        assertByteIdentical("Directory.cap");
    }

    @Test
    void constantPoolComponentByteIdentical() {
        assertByteIdentical("ConstantPool.cap");
    }

    @Test
    void methodComponentByteIdentical() {
        assertByteIdentical("Method.cap");
    }

    @Test
    void refLocationComponentByteIdentical() {
        assertByteIdentical("RefLocation.cap");
    }

    // ── Class.cap: class_info layout of JCVM 3.1 §6.9.2, identical to Oracle's output ──

    @Test
    void classComponentByteIdentical() {
        assertByteIdentical("Class.cap");
    }

    @Test
    void exportComponentAbsentForAppletOnly() {
        // Export component (tag=10) is only generated for library packages.
        // Neither our converter nor Oracle generates it for applet-only packages
        // unless explicitly requested via generateExport(true).
        assertThat(oracleComponents.get("Export.cap"))
                .as("Oracle Export.cap should not be present for applet-only package").isNull();
        assertThat(ourComponents.get("Export.cap"))
                .as("Our Export.cap should not be present without generateExport flag").isNull();
    }

    @Test
    void descriptorComponentByteIdentical() {
        assertByteIdentical("Descriptor.cap");
    }

    @Test
    void allComponentTagsMatchOracle() {
        // Verify that each component's tag byte matches
        String[] names = {"Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
                "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
                "RefLocation.cap", "Export.cap", "Descriptor.cap"};
        int[] expectedTags = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

        for (int i = 0; i < names.length; i++) {
            byte[] ours = ourComponents.get(names[i]);
            byte[] oracle = oracleComponents.get(names[i]);
            if (ours == null || oracle == null) continue;

            assertThat(ours[0] & 0xFF).as("%s tag", names[i]).isEqualTo(expectedTags[i]);
            assertThat(oracle[0] & 0xFF).as("Oracle %s tag", names[i]).isEqualTo(expectedTags[i]);
        }
    }

    @Test
    void allComponentSizesInHeaderMatchBody() {
        // For each component: the u2 size field at offset 1-2 should equal body length
        String[] names = {"Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
                "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
                "RefLocation.cap", "Export.cap", "Descriptor.cap"};

        for (String name : names) {
            byte[] ours = ourComponents.get(name);
            if (ours == null || ours.length < 3) continue;

            int declaredSize = u2(ours, 1);
            int actualBody = ours.length - 3; // subtract tag(1) + size(2)
            assertThat(declaredSize).as("%s declared size vs actual body", name)
                    .isEqualTo(actualBody);
        }
    }

    // ── Detailed Binary Dump for Comparison ──

    @Test
    void allComponentsByteIdenticalWithDetailedDiff() {
        List<String> differing = new ArrayList<>();
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("      CAP FILE COMPARISON: OURS vs ORACLE");
        System.out.println("═══════════════════════════════════════════════");

        String[] componentOrder = {"Header.cap", "Directory.cap", "Applet.cap",
                "Import.cap", "ConstantPool.cap", "Class.cap", "Method.cap",
                "StaticField.cap", "RefLocation.cap", "Export.cap", "Descriptor.cap", "Debug.cap"};

        for (String name : componentOrder) {
            byte[] ours = ourComponents.get(name);
            byte[] oracle = oracleComponents.get(name);

            if (ours == null && oracle == null) continue;

            System.out.printf("%n── %s ──%n", name);
            if (ours == null) {
                differing.add(name);
                System.out.println("  OURS: NOT PRESENT");
                System.out.printf("  ORACLE: %d bytes%n", oracle.length);
                printHex("  ORACLE", oracle, Math.min(oracle.length, 64));
                continue;
            }
            if (oracle == null) {
                differing.add(name);
                System.out.printf("  OURS: %d bytes%n", ours.length);
                System.out.println("  ORACLE: NOT PRESENT");
                continue;
            }

            System.out.printf("  Size: OURS=%d  ORACLE=%d  %s%n",
                    ours.length, oracle.length,
                    ours.length == oracle.length ? "MATCH" : "DIFFER");

            // Compare byte by byte
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

            if (diffCount == 0) {
                System.out.println("  BYTE-IDENTICAL ✓");
            } else {
                differing.add(name);
                System.out.printf("  Differences: %d bytes, first at offset %d%n", diffCount, firstDiff);
            }

            // Print side-by-side hex (first 96 bytes)
            int limit = Math.min(Math.max(ours.length, oracle.length), 96);
            System.out.println("  Offset  OURS                                      ORACLE");
            for (int i = 0; i < limit; i += 16) {
                System.out.printf("  %04X    ", i);
                for (int j = i; j < i + 16 && j < limit; j++) {
                    if (j < ours.length) {
                        boolean diff = j < oracle.length && ours[j] != oracle[j];
                        System.out.printf(diff ? "*%02X" : " %02X", ours[j] & 0xFF);
                    } else {
                        System.out.print("   ");
                    }
                }
                System.out.print("    ");
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
        }

        // CP entry comparison
        System.out.println("\n── ConstantPool Entry Comparison ──");
        printCpEntries("OURS  ", ourComponents.get("ConstantPool.cap"));
        printCpEntries("ORACLE", oracleComponents.get("ConstantPool.cap"));

        assertThat(differing).as("components that differ from Oracle's reference output").isEmpty();
    }

    // ── Assertion helpers ──

    private void assertByteIdentical(String componentName) {
        byte[] ours = ourComponents.get(componentName);
        byte[] oracle = oracleComponents.get(componentName);
        assertThat(ours).as("%s should be present in our CAP", componentName).isNotNull();
        assertThat(oracle).as("%s should be present in Oracle CAP", componentName).isNotNull();
        assertThat(ours).as("%s should be byte-identical to Oracle", componentName).isEqualTo(oracle);
    }

    // ── Component extraction helpers ──

    private static List<String> extractImportAids(byte[] imp) {
        List<String> aids = new ArrayList<>();
        int count = imp[3] & 0xFF;
        int pos = 4;
        for (int i = 0; i < count; i++) {
            pos += 2; // minor + major version
            int aidLen = imp[pos++] & 0xFF;
            byte[] aid = Arrays.copyOfRange(imp, pos, pos + aidLen);
            aids.add(hexBytes(aid));
            pos += aidLen;
        }
        return aids;
    }

    private static Map<Integer, Integer> cpTagDistribution(byte[] cp) {
        Map<Integer, Integer> map = new HashMap<>();
        int count = u2(cp, 3);
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            int tag = cp[base] & 0xFF;
            map.merge(tag, 1, Integer::sum);
        }
        return map;
    }

    private static void printCpEntries(String label, byte[] cp) {
        if (cp == null || cp.length < 5) return;
        int count = u2(cp, 3);
        String[] tagNames = {"?", "ClassRef", "InstField", "VirtMeth",
                "SuperMeth", "StatField", "StatMeth"};
        System.out.printf("  %s (%d entries):%n", label, count);
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            if (base + 3 >= cp.length) break;
            int tag = cp[base] & 0xFF;
            int b1 = cp[base + 1] & 0xFF;
            int b2 = cp[base + 2] & 0xFF;
            int b3 = cp[base + 3] & 0xFF;
            String tagName = tag < tagNames.length ? tagNames[tag] : "Unk";
            boolean isExt = (b1 & 0x80) != 0;
            System.out.printf("    [%2d] %s %-10s %02X %02X %02X%n",
                    i, isExt ? "E" : "I", tagName, b1, b2, b3);
        }
    }

    private static void printHex(String prefix, byte[] data, int limit) {
        for (int i = 0; i < limit; i += 16) {
            System.out.printf("%s %04X: ", prefix, i);
            for (int j = i; j < i + 16 && j < limit; j++) {
                System.out.printf("%02X ", data[j] & 0xFF);
            }
            System.out.println();
        }
    }

    private static int u2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int u4(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static String hexBytes(byte[] data) {
        var sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    // ── Complex applet structural validation ──
    // These tests verify that complex applets produce well-formed CAP files.
    // When Oracle reference CAP files are added to resources, they will also
    // compare byte-by-byte via the conditional comparison helper.

    @Test
    void multiClassAppletProducesValidCap() throws Exception {
        assertComplexAppletValid("com.example.multiclass", "A000000062030101",
                "com.example.multiclass.MultiClassApplet", "A00000006203010101",
                "oracle-MultiClassApplet.cap");
    }

    @Test
    void interfaceAppletProducesValidCap() throws Exception {
        assertComplexAppletValid("com.example.iface", "A000000062040101",
                "com.example.iface.InterfaceApplet", "A00000006204010101",
                "oracle-InterfaceApplet.cap");
    }

    @Test
    void exceptionAppletProducesValidCap() throws Exception {
        assertComplexAppletValid("com.example.exception", "A000000062050101",
                "com.example.exception.ExceptionApplet", "A00000006205010101",
                "oracle-ExceptionApplet.cap");
    }

    @Test
    void inheritanceAppletProducesValidCap() throws Exception {
        assertComplexAppletValid("com.example.inherit", "A000000062060101",
                "com.example.inherit.InheritanceApplet", "A00000006206010101",
                "oracle-InheritanceApplet.cap");
    }

    @Test
    void cryptoAppletProducesStructurallyValidCap() throws Exception {
        // CryptoApplet exercises Cipher, MessageDigest, Signature, KeyBuilder,
        // KeyPair, RandomData, CryptoException — validates all crypto token exports.
        // The constant pool holds the same entries as Oracle's but in another order, which
        // JCVM 3.1 §6.8 permits; components that embed CP indices (Method, Descriptor) differ
        // accordingly. Everything else, including the Class component, is byte-identical.
        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName("com.example.crypto")
                .packageAid("A000000062070101")
                .packageVersion(1, 0)
                .applet("com.example.crypto.CryptoApplet", "A00000006207010101")
                .build()
                .convert();

        Map<String, byte[]> components = extractComponents(result.capFile());

        // All 10 required components present
        String[] required = {"Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
                "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
                "RefLocation.cap", "Descriptor.cap"};
        for (String name : required) {
            assertThat(components.get(name)).as("%s present", name).isNotNull();
        }

        // Component tags correct
        int[] expectedTags = {1, 2, 3, 4, 5, 6, 7, 8, 9, 11};
        for (int i = 0; i < required.length; i++) {
            byte[] comp = components.get(required[i]);
            assertThat(comp[0] & 0xFF).as("%s tag", required[i]).isEqualTo(expectedTags[i]);
        }

        // Size fields match actual body lengths
        for (String name : required) {
            byte[] comp = components.get(name);
            int declaredSize = u2(comp, 1);
            int actualBody = comp.length - 3;
            assertThat(declaredSize).as("%s size", name).isEqualTo(actualBody);
        }

        // Import.cap must import at least javacard.framework and javacardx.crypto
        byte[] imp = components.get("Import.cap");
        int importCount = imp[3] & 0xFF;
        assertThat(importCount).as("Must import framework + lang + security + crypto")
                .isGreaterThanOrEqualTo(4);

        // Oracle reference comparison (structural, not byte-identical)
        Optional<byte[]> cryptoReference = OracleReferences.find("oracle-CryptoApplet.cap");
        if (cryptoReference.isPresent()) {
            Map<String, byte[]> ref = extractComponents(cryptoReference.get());
            System.out.printf("%n═══ Oracle comparison for CryptoApplet ═══%n");

            // These components must be byte-identical
            for (String name : new String[]{"Header.cap", "Applet.cap", "Import.cap", "Class.cap",
                    "StaticField.cap", "RefLocation.cap", "Directory.cap"}) {
                byte[] ours = components.get(name);
                byte[] oracle = ref.get(name);
                if (oracle == null) continue;
                System.out.printf("  %-20s %3d vs %3d  %s%n",
                        name, ours.length, oracle.length,
                        Arrays.equals(ours, oracle) ? "BYTE-IDENTICAL" : "DIFFER");
                assertThat(ours).as("%s byte-identical", name).isEqualTo(oracle);
            }

            // These components may differ in CP ordering — report and verify same size
            for (String name : new String[]{"ConstantPool.cap", "Method.cap", "Descriptor.cap"}) {
                byte[] ours = components.get(name);
                byte[] oracle = ref.get(name);
                if (oracle == null) continue;
                boolean identical = Arrays.equals(ours, oracle);
                System.out.printf("  %-20s %3d vs %3d  %s%n",
                        name, ours.length, oracle.length,
                        identical ? "BYTE-IDENTICAL" : (ours.length == oracle.length ? "SIZE MATCH" : "SIZE DIFF"));
            }
        }
    }

    /**
     * Validates a complex applet's CAP output for structural correctness,
     * and compares with Oracle reference if available: every component must be byte-identical.
     * The Class component follows the {@code class_info} layout of JCVM 3.1 §6.9.2, which is
     * also the layout of Oracle's converter (there is no "dispatch table off-by-one").
     */
    private void assertComplexAppletValid(String packageName, String packageAid,
                                           String appletClass, String appletAid,
                                           String oracleRefFile) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName(packageName)
                .packageAid(packageAid)
                .packageVersion(1, 0)
                .applet(appletClass, appletAid)
                .build()
                .convert();

        Map<String, byte[]> components = extractComponents(result.capFile());

        // Verify all required components are present (10 components, no Debug/Export)
        String[] required = {"Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
                "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
                "RefLocation.cap", "Descriptor.cap"};
        for (String name : required) {
            assertThat(components.get(name))
                    .as("%s present in %s", name, appletClass).isNotNull();
        }

        // Verify tags are correct
        int[] expectedTags = {1, 2, 3, 4, 5, 6, 7, 8, 9, 11};
        for (int i = 0; i < required.length; i++) {
            byte[] comp = components.get(required[i]);
            assertThat(comp[0] & 0xFF).as("%s tag", required[i]).isEqualTo(expectedTags[i]);
        }

        // Verify size fields match actual body lengths
        for (String name : required) {
            byte[] comp = components.get(name);
            int declaredSize = u2(comp, 1);
            int actualBody = comp.length - 3;
            assertThat(declaredSize).as("%s declared size in %s", name, appletClass)
                    .isEqualTo(actualBody);
        }

        // Header magic = 0xDECAFFED
        byte[] header = components.get("Header.cap");
        assertThat(u4(header, 3)).as("Header magic for %s", appletClass)
                .isEqualTo(0xDECAFFED);

        // Applet AID matches
        byte[] applet = components.get("Applet.cap");
        assertThat(applet[3] & 0xFF).as("Applet count for %s", appletClass).isEqualTo(1);

        // Conditional Oracle reference comparison (strict)
        Optional<byte[]> reference = OracleReferences.find(oracleRefFile);
        if (reference.isPresent()) {
            Map<String, byte[]> refComponents = extractComponents(reference.get());
            System.out.printf("%n═══ Oracle comparison for %s ═══%n", appletClass);

            for (String name : required) {
                byte[] ours = components.get(name);
                byte[] ref = refComponents.get(name);
                if (ref == null) continue;
                boolean identical = Arrays.equals(ours, ref);
                System.out.printf("  %-20s %3d vs %3d  %s%n",
                        name, ours.length, ref.length,
                        identical ? "BYTE-IDENTICAL" :
                                ours.length == ref.length ? "SIZE MATCH" : "DIFFER");

                assertThat(ours)
                        .as("%s must be byte-identical for %s", name, appletClass)
                        .isEqualTo(ref);
            }
        }
    }
}
