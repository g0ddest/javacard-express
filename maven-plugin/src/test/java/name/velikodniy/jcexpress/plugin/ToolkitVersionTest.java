package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The plugin and the toolkit artifacts of the project must have one version. The API stubs
 * ({@code javacard-express-api}) are what javac compiles the applet against, and the converter
 * built into the plugin carries its own data of the same API; {@code javacard-express-core} runs
 * the tests. A plugin declared without {@code <version>} is the newest release Maven finds, which
 * silently built CAP files with another version than the one the project's tests used.
 */
class ToolkitVersionTest {

    private static final String API = "name.velikodniy:javacard-express-api:";
    private static final String CORE = "name.velikodniy:javacard-express-core:";

    @TempDir
    Path dir;

    @Test
    void apiStubsOfAnotherVersionFailTheBuild() {
        MojoRunner runner = runner().pluginVersion("7.2.0").declare(API + "7.1.0", "provided");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("javacard-express-maven-plugin 7.2.0")
                .hasMessageContaining("javacard-express-api 7.1.0")
                .hasMessageContaining("javacard-express-bom")
                .hasMessageContaining("javacard-express-applet-parent");
        assertThat(dir.resolve("target/sample-applet-1.0.cap")).doesNotExist();
    }

    @Test
    void testToolkitOfAnotherVersionFailsTheBuild() {
        MojoRunner runner = runner().pluginVersion("7.0.0")
                .declare(CORE + "7.1.0", "test").declare(API + "7.0.0", "provided");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("javacard-express-maven-plugin 7.0.0")
                .hasMessageContaining("javacard-express-core 7.1.0")
                .hasMessageNotContaining("javacard-express-api 7.0.0");
    }

    @Test
    void oneVersionForThePluginAndTheToolkitBuilds() throws Exception {
        runner().pluginVersion("7.1.0").declare(CORE + "7.1.0", "test").declare(API + "7.1.0", "provided").execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
    }

    @Test
    void aTimestampedSnapshotIsTheSameVersionAsItsSnapshot() throws Exception {
        runner().pluginVersion("7.2.0-20261002.101010-3").declare(API + "7.2.0-SNAPSHOT", "provided").execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
    }

    @Test
    void otherToolkitArtifactsAreNotCompared() throws Exception {
        runner().pluginVersion("7.1.0").declare("name.velikodniy:javacard-express-gp:7.0.0", "test").execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
    }

    @Test
    void theCheckCanBeTurnedOff() throws Exception {
        runner().pluginVersion("7.2.0").declare(API + "7.1.0", "provided")
                .property("javacard.checkVersions", "false").execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
    }

    private MojoRunner runner() {
        return MojoRunner.forProject(dir, Samples.helloApplet().compile(dir)).configure("packageAid", "F000000001");
    }
}
