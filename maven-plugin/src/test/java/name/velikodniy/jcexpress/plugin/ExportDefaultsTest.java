package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which packages get an Export component and an export file.
 *
 * <p>JCVM 3.1 &sect;6.13: "For public packages that include applets, the Export Component
 * includes entries only for all public interfaces that are shareable. For public packages that do
 * not include any applets, the Export Component contains an entry for each public class and public
 * interface", and "The value of the class_count item must be greater than zero". &sect;5.6.1: "If
 * the package is not a library package, this export file can only contain shareable interfaces."
 * So an applet package without shareable interfaces has nothing to export.
 */
class ExportDefaultsTest {

    @TempDir
    Path dir;

    @Test
    void appletPackageWithoutShareableInterfacesExportsNothingByDefault() throws Exception {
        CapFile cap = build(runner(Samples.helloApplet()));

        assertThat(cap.has("Export")).isFalse();
        assertThat(cap.header().flags() & CapFile.ACC_EXPORT).isZero();
        assertThat(expFile()).doesNotExist();
    }

    @Test
    void libraryPackageIsExportedByDefault() throws Exception {
        CapFile cap = build(runner(Samples.libraryPackage()));

        assertThat(cap.applets()).isEmpty();
        assertThat(cap.has("Export")).isTrue();
        assertThat(cap.header().flags() & CapFile.ACC_EXPORT).isEqualTo(CapFile.ACC_EXPORT);
        assertThat(expFile()).isNotEmptyFile();
    }

    @Test
    void appletPackageWithAShareableInterfaceIsExportedByDefault() throws Exception {
        CapFile cap = build(runner(Samples.shareableInterfacePackage()));

        assertThat(cap.applets()).hasSize(1);
        assertThat(cap.has("Export")).isTrue();
        assertThat(cap.exportClassCount()).hasValueSatisfying(count -> assertThat(count).isPositive());
        assertThat(expFile()).isNotEmptyFile();
    }

    @Test
    void generateExportFalseAlsoSuppressesTheExportFile() throws Exception {
        CapFile cap = build(runner(Samples.libraryPackage()).configure("generateExport", false));

        assertThat(cap.has("Export")).isFalse();
        assertThat(expFile()).doesNotExist();
    }

    @Test
    void generateExportPropertyIsHonoured() throws Exception {
        CapFile cap = build(runner(Samples.libraryPackage()).property("javacard.generateExport", "false"));

        assertThat(cap.has("Export")).isFalse();
    }

    @Test
    void forcingAnExportOfAnAppletPackageWithoutShareableInterfacesIsRefusedWithAWarning() throws Exception {
        MojoRunner runner = runner(Samples.helloApplet()).configure("generateExport", true);
        CapFile cap = build(runner);

        assertThat(cap.has("Export")).isFalse();
        assertThat(expFile()).doesNotExist();
        assertThat(runner.log().messages(Level.WARNING))
                .anyMatch(m -> m.contains("generateExport") && m.contains("shareable") && m.contains("6.13"));
    }

    @Test
    void staleExportFileOfAPreviousBuildIsRemoved() throws Exception {
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(expFile(), "stale");

        build(runner(Samples.helloApplet()));

        assertThat(expFile()).doesNotExist();
    }

    private MojoRunner runner(JavaSources sources) {
        return MojoRunner.forProject(dir, sources.compile(dir)).configure("packageAid", "A00000006212");
    }

    private CapFile build(MojoRunner runner) throws Exception {
        runner.execute();
        return CapFile.read(dir.resolve("target/sample-applet-1.0.cap"));
    }

    private Path expFile() {
        return dir.resolve("target/sample-applet-1.0.exp");
    }
}
