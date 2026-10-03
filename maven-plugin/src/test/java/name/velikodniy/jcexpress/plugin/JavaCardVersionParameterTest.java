package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The target platform must reach the converter through every documented way of configuring it:
 * the {@code <javaCardVersion>} element, the {@code javacard.version} user property, and the old
 * element name {@code <javaCardVersionStr>}. The platform shows in the CAP file as the Header
 * format version (JCVM 3.1 &sect;6.4) and the imported API package versions (&sect;6.7).
 */
class JavaCardVersionParameterTest {

    @TempDir
    Path dir;

    @Test
    void javaCardVersionElementSelectsTheTargetPlatform() throws Exception {
        CapFile cap = build(runner().configure("javaCardVersion", "2.2.2"));

        assertThat(cap.imports()).isEqualTo(converterImports(JavaCardVersion.V2_2_2));
        assertThat(cap.imports()).isNotEqualTo(converterImports(JavaCardVersion.V3_0_5));
    }

    @Test
    void javacardVersionUserPropertySelectsTheTargetPlatform() throws Exception {
        CapFile cap = build(runner().property("javacard.version", "3.1.0"));

        assertThat(cap.header().formatMinor()).isEqualTo(3);
        assertThat(cap.imports()).isEqualTo(converterImports(JavaCardVersion.V3_1_0));
    }

    @Test
    void commandLineVersionThatThePomOverridesIsReported() throws Exception {
        // Maven gives the POM's <javaCardVersion> precedence over -Djavacard.version, so the command
        // line value has no effect; that must not pass silently
        MojoRunner runner = runner().configure("javaCardVersion", "3.0.5").property("javacard.version", "3.0.4");
        CapFile cap = build(runner);

        assertThat(cap.imports()).isEqualTo(converterImports(JavaCardVersion.V3_0_5));
        assertThat(runner.log().messages(Level.WARNING)).anySatisfy(warning -> assertThat(warning)
                .contains("-Djavacard.version=3.0.4").contains("<javaCardVersion>3.0.5</javaCardVersion>")
                .contains("${javacard.version}"));
    }

    @Test
    void commandLineVersionEqualToThePomIsNotReported() throws Exception {
        MojoRunner runner = runner().configure("javaCardVersion", "3.0.4").property("javacard.version", "3.0.4");
        build(runner);

        assertThat(runner.log().messages(Level.WARNING)).noneMatch(m -> m.contains("javacard.version"));
    }

    @Test
    void oldElementNameJavaCardVersionStrIsStillAccepted() throws Exception {
        CapFile cap = build(runner().configure("javaCardVersionStr", "2.2.2"));

        assertThat(cap.imports()).isEqualTo(converterImports(JavaCardVersion.V2_2_2));
    }

    @Test
    void defaultTargetIsJavaCard305() throws Exception {
        CapFile cap = build(runner());

        assertThat(cap.imports()).isEqualTo(converterImports(JavaCardVersion.V3_0_5));
    }

    private MojoRunner runner() {
        Path classes = Samples.helloApplet().compile(dir);
        return MojoRunner.forProject(dir, classes).configure("packageAid", "A00000006212");
    }

    private CapFile build(MojoRunner runner) throws Exception {
        runner.execute();
        return CapFile.read(dir.resolve("target/sample-applet-1.0.cap"));
    }

    /** The Import component the converter itself produces for the given platform. */
    private List<CapFile.ImportedPackage> converterImports(JavaCardVersion version) throws Exception {
        byte[] cap = Converter.builder()
                .classesDirectory(dir.resolve("classes"))
                .packageName("com.example.hello")
                .packageAid("A00000006212")
                .applet("com.example.hello.HelloApplet", "A0000000621201")
                .javaCardVersion(version)
                .build()
                .convert()
                .capFile();
        return CapFile.read(cap).imports();
    }
}
