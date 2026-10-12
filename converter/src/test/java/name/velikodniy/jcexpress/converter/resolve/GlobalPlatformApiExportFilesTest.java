package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.testutil.GlobalPlatformExports;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.tools.GlobalPlatformApiDataGenerator;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Black-box verification of the built-in data of {@code org.globalplatform} ({@link GlobalPlatformApi}) against
 * the export files of the package that a developer installed locally (see {@link GlobalPlatformExports}), read
 * with the project's own reader. For every version, the reconstructed export must match the file: package AID,
 * version and flags (JCVM 3.1 §5.6.1), class tokens, flags, superclasses and interfaces (§5.7), the count of
 * method tokens a CAP 2.2 subclass inherits (§5.7) and method tokens and flags (§5.9). The files' fields are all
 * compile-time constants and are not part of the built-in data. Skipped when no export file is installed (nothing
 * of the files is stored in the repository).
 */
@EnabledIf("name.velikodniy.jcexpress.converter.testutil.GlobalPlatformExports#available")
class GlobalPlatformApiExportFilesTest {

    @Test
    void builtInDataMatchesTheExportFileOfEveryInstalledVersion() {
        Map<String, ExportFile> files = GlobalPlatformExports.read();
        SoftAssertions soft = new SoftAssertions();
        files.forEach((version, expected) -> {
            soft.assertThat(GlobalPlatformApi.versions()).as("known versions").contains(version);
            if (GlobalPlatformApi.versions().contains(version)) {
                compare(soft, version, expected, GlobalPlatformApi.export(version));
            }
        });
        soft.assertAll();
    }

    @Test
    void everyFieldOfTheExportFilesIsACompileTimeConstant_jcvm31_5_8() {
        // the built-in data has no fields: this is what allows it
        GlobalPlatformExports.read().forEach((version, export) -> {
            for (ExportFile.ClassExport cls : export.classes()) {
                assertThat(cls.fields()).as("fields of %s in %s", cls.name(), version)
                        .allMatch(field -> field.token() == ExportFile.CONSTANT_FIELD_TOKEN
                                && field.constantValue() != null);
            }
        });
    }

    @Test
    void tableIsWhatTheGeneratorWritesFromAllVersions() throws IOException {
        Map<String, ExportFile> files = GlobalPlatformExports.read();
        assumeTrue(files.keySet().containsAll(GlobalPlatformApi.versions())
                && GlobalPlatformApi.versions().containsAll(files.keySet()), "every version is installed");

        try (InputStream table = GlobalPlatformApi.class.getResourceAsStream("globalplatform-api-exports.txt")) {
            assertThat(new String(table.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(GlobalPlatformApiDataGenerator.generate(files));
        }
    }

    private static void compare(SoftAssertions soft, String version, ExportFile expected, ExportFile actual) {
        String pkg = "org.globalplatform " + version;
        soft.assertThat(actual.packageName()).as(pkg + " name").isEqualTo(expected.packageName());
        soft.assertThat(HexFormat.of().formatHex(actual.aid())).as(pkg + " AID")
                .isEqualTo(HexFormat.of().formatHex(expected.aid()));
        soft.assertThat(actual.majorVersion() + "." + actual.minorVersion()).as(pkg + " version")
                .isEqualTo(expected.majorVersion() + "." + expected.minorVersion());
        soft.assertThat(actual.packageFlags()).as(pkg + " flags").isEqualTo(expected.packageFlags());
        soft.assertThat(classLines(actual)).as(pkg + " classes").isEqualTo(classLines(expected));
        soft.assertThat(methodLines(actual)).as(pkg + " methods").isEqualTo(methodLines(expected));
    }

    private static Set<String> classLines(ExportFile export) {
        Set<String> lines = new TreeSet<>();
        for (ExportFile.ClassExport c : export.classes()) {
            lines.add(c.name() + " token=" + c.token() + " flags=" + Integer.toHexString(c.accessFlags())
                    + " supers=" + c.supers() + " interfaces=" + c.interfaces()
                    + " cap22=" + c.cap22InheritableCount());
        }
        return lines;
    }

    private static Set<String> methodLines(ExportFile export) {
        Set<String> lines = new TreeSet<>();
        for (ExportFile.ClassExport c : export.classes()) {
            for (ExportFile.MethodExport m : c.methods()) {
                lines.add(c.name() + "." + m.name() + m.descriptor() + " token=" + m.token()
                        + " flags=" + Integer.toHexString(m.accessFlags()));
            }
        }
        return lines;
    }
}
