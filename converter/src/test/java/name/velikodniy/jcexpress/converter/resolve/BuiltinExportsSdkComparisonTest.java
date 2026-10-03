package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.testutil.OracleSdkExports;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Black-box verification of the built-in API data ({@link BuiltinExports}) against the API
 * export files of the locally installed Java Card Development Kits 2.1.2-3.2.0, read with the
 * project's own {@link ExportFileReader}. For every version, the reconstructed export of every
 * package must match the kit's export file exactly: package AID, version and flags (JCVM 3.1
 * §5.6.1), class tokens, flags, superclasses and interfaces (§5.7), method tokens and flags
 * (§5.9) and the tokens and flags of fields that are not compile-time constants (§5.8).
 * Skipped when the kits are not installed (nothing from the kits is stored in the repository).
 */
@EnabledIf("name.velikodniy.jcexpress.converter.testutil.OracleSdkExports#allAvailable")
class BuiltinExportsSdkComparisonTest {

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void builtinExportsMatchTheKitExportFilesExactly(JavaCardVersion version) throws IOException {
        List<ExportFile> kit = new ArrayList<>();
        for (byte[] data : OracleSdkExports.read(version).values()) {
            kit.add(ExportFileReader.read(data));
        }
        SoftAssertions soft = new SoftAssertions();
        for (ExportFile expected : kit) {
            ExportFile actual = BuiltinExports.getExport(expected.packageName(), version);
            soft.assertThat(actual).as("built-in package %s for %s", expected.packageName(), version)
                    .isNotNull();
            if (actual != null) comparePackage(soft, version, expected, actual);
        }
        Set<String> kitPackages = kit.stream().map(ExportFile::packageName).collect(Collectors.toSet());
        soft.assertThat(BuiltinExports.allBuiltinImports(0, version))
                .extracting(i -> i.exportFile().packageName())
                .as("built-in packages of %s", version)
                .containsExactlyInAnyOrderElementsOf(kitPackages);
        soft.assertAll();
    }

    private static void comparePackage(SoftAssertions soft, JavaCardVersion v, ExportFile expected,
                                       ExportFile actual) {
        String pkg = v + " " + expected.packageName();
        soft.assertThat(HexFormat.of().formatHex(actual.aid())).as(pkg + " AID")
                .isEqualTo(HexFormat.of().formatHex(expected.aid()));
        soft.assertThat(actual.majorVersion() + "." + actual.minorVersion()).as(pkg + " version")
                .isEqualTo(expected.majorVersion() + "." + expected.minorVersion());
        soft.assertThat(actual.packageFlags()).as(pkg + " flags").isEqualTo(expected.packageFlags());
        soft.assertThat(classLines(actual)).as(pkg + " classes").isEqualTo(classLines(expected));
        soft.assertThat(memberLines(actual)).as(pkg + " members").isEqualTo(memberLines(expected));
    }

    private static Set<String> classLines(ExportFile ef) {
        Set<String> lines = new TreeSet<>();
        for (ExportFile.ClassExport c : ef.classes()) {
            lines.add(c.name() + " token=" + c.token() + " flags=" + Integer.toHexString(c.accessFlags())
                    + " supers=" + new TreeSet<>(c.supers()) + " interfaces=" + new TreeSet<>(c.interfaces()));
        }
        return lines;
    }

    private static Set<String> memberLines(ExportFile ef) {
        Set<String> lines = new TreeSet<>();
        for (ExportFile.ClassExport c : ef.classes()) {
            for (ExportFile.MethodExport m : c.methods()) {
                lines.add(c.name() + "." + m.name() + m.descriptor() + " token=" + m.token()
                        + " flags=" + Integer.toHexString(m.accessFlags()));
            }
            for (ExportFile.FieldExport f : c.fields()) {
                if (f.token() == ExportFile.CONSTANT_FIELD_TOKEN) continue;
                lines.add(c.name() + "." + f.name() + ":" + f.descriptor() + " token=" + f.token()
                        + " flags=" + Integer.toHexString(f.accessFlags()));
            }
        }
        return lines;
    }
}
