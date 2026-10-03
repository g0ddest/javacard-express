package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invalid parameter values stop the build with a message that names the parameter, the value and
 * what is allowed, instead of silently building something else.
 *
 * <p>The package version is the pair of u1 items {@code major_version} and {@code minor_version}
 * of the Header component's package_info (JCVM 3.1 &sect;6.4, &sect;4.5), so each number is 0 to 255.
 */
class ParameterValidationTest {

    @TempDir
    Path dir;

    @Test
    void unknownJavaCardVersionFailsAndListsTheSupportedVersions() {
        MojoRunner runner = runner().configure("javaCardVersion", "3.3");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("javaCardVersion").hasMessageContaining("'3.3'")
                .hasMessageContaining("2.1.2").hasMessageContaining("3.0.5").hasMessageContaining("3.2.0");
        assertThat(capFile()).doesNotExist();
    }

    @Test
    void unknownJavaCardVersionGivenAsPropertyFails() {
        MojoRunner runner = runner().property("javacard.version", "3.0.6");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'3.0.6'");
    }

    @Test
    void resolvedTargetPlatformIsLogged() throws Exception {
        MojoRunner runner = runner().configure("javaCardVersion", "3.1");
        runner.execute();

        assertThat(runner.log().messages(Level.INFO)).contains("Target: Java Card 3.1.0 (CAP format 2.3)");
    }

    @Test
    void packageVersionGoesIntoTheHeader() throws Exception {
        runner().configure("packageVersion", "2.3").execute();

        CapFile.Header header = CapFile.read(capFile()).header();
        assertThat(header.packageMajor()).isEqualTo(2);
        assertThat(header.packageMinor()).isEqualTo(3);
    }

    @Test
    void packageVersionPropertyGoesIntoTheHeader() throws Exception {
        runner().property("javacard.packageVersion", "255.0").execute();

        CapFile.Header header = CapFile.read(capFile()).header();
        assertThat(header.packageMajor()).isEqualTo(255);
        assertThat(header.packageMinor()).isZero();
    }

    @Test
    void defaultPackageVersionIs10() throws Exception {
        runner().execute();

        CapFile.Header header = CapFile.read(capFile()).header();
        assertThat(header.packageMajor()).isEqualTo(1);
        assertThat(header.packageMinor()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "1.300", "256.0", "1.0.0", "v1.1", "1.-1", "1.", ".1", "1,0"})
    void malformedPackageVersionFails(String version) {
        MojoRunner runner = runner().configure("packageVersion", version);

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("packageVersion").hasMessageContaining("'" + version + "'")
                .hasMessageContaining("0 to 255");
        assertThat(capFile()).doesNotExist();
    }

    private MojoRunner runner() {
        return MojoRunner.forProject(dir, Samples.helloApplet().compile(dir)).configure("packageAid", "A00000006212");
    }

    private Path capFile() {
        return dir.resolve("target/sample-applet-1.0.cap");
    }
}
