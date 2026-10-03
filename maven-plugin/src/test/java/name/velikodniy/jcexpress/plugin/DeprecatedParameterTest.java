package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.PluginDescriptor;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code oracleCompatibility} is kept so that existing POMs still build, but it is deprecated:
 * the Class component is written as JCVM 3.1 &sect;6.9 specifies.
 */
class DeprecatedParameterTest {

    @TempDir
    Path dir;

    @Test
    void oracleCompatibilityIsMarkedDeprecatedInThePluginDescriptor() {
        assertThat(PluginDescriptor.buildGoal().parameter("oracleCompatibility"))
                .hasValueSatisfying(p -> assertThat(p.deprecated()).isNotBlank());
    }

    @Test
    void settingOracleCompatibilityStillBuildsAndWarns() throws Exception {
        MojoRunner runner = runner().configure("oracleCompatibility", true);
        runner.execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
        assertThat(runner.log().messages(Level.WARNING))
                .anyMatch(m -> m.contains("<oracleCompatibility>") && m.contains("deprecated"));
    }

    @Test
    void defaultBuildDoesNotMentionIt() throws Exception {
        MojoRunner runner = runner();
        runner.execute();

        assertThat(runner.log().text()).doesNotContain("oracleCompatibility");
    }

    private MojoRunner runner() {
        return MojoRunner.forProject(dir, Samples.helloApplet().compile(dir)).configure("packageAid", "A00000006212");
    }
}
