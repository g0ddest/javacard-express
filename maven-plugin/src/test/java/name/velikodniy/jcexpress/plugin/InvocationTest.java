package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How the goal behaves where it has nothing to convert: in a project of packaging {@code pom}
 * (a parent or aggregator, e.g. one that declares the plugin for its modules), after a bound
 * execution in a module that was not compiled, and when it is invoked on the command line.
 */
class InvocationTest {

    @TempDir
    Path dir;

    @Test
    void projectsOfPackagingPomAreSkipped() throws Exception {
        MojoRunner runner = MojoRunner.forProject(dir, dir.resolve("classes")).packaging("pom");
        runner.execute();

        assertThat(runner.log().messages(Level.WARNING)).isEmpty();
        assertThat(runner.log().messages(Level.INFO)).anyMatch(m -> m.startsWith("Skipping") && m.contains("pom"));
        assertThat(runner.attachments()).isEmpty();
    }

    @Test
    void projectsOfPackagingPomAreSkippedAlsoOnTheCommandLine() throws Exception {
        // mvn javacard-express:build in a multi-module root runs the goal in the root (packaging pom) as well
        MojoRunner runner = MojoRunner.forProject(dir, dir.resolve("classes")).packaging("pom").fromCommandLine();
        runner.execute();

        assertThat(runner.log().messages(Level.INFO)).anyMatch(m -> m.startsWith("Skipping"));
    }

    @Test
    void commandLineInvocationWithoutCompiledClassesFails() {
        // mvn javacard-express:build after mvn clean: there is nothing to convert, which a BUILD SUCCESS hid
        MojoRunner runner = MojoRunner.forProject(dir, dir.resolve("classes")).fromCommandLine();

        assertThatThrownBy(runner::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining(dir.resolve("classes").toString())
                .hasMessageContaining("does not exist")
                .hasMessageContaining("mvn package")
                .hasMessageContaining("mvn compile javacard-express:build");
    }

    @Test
    void boundExecutionWithoutCompiledClassesOnlyWarns() throws Exception {
        // A module without sources (or with -Dmaven.main.skip) must not break a lifecycle build
        MojoRunner runner = MojoRunner.forProject(dir, dir.resolve("classes"));
        runner.execute();

        assertThat(runner.log().messages(Level.WARNING)).anyMatch(m -> m.contains("does not exist"));
    }

    @Test
    void commandLineInvocationWithCompiledClassesBuildsTheCapFile() throws Exception {
        MojoRunner runner = MojoRunner.forProject(dir, Samples.helloApplet().compile(dir))
                .configure("packageAid", "F000000001").fromCommandLine();
        runner.execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
    }
}
