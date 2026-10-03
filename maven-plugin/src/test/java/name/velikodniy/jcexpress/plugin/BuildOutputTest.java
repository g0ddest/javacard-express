package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.PluginDescriptor;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.RecordingProjectHelper.Attachment;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the goal writes, where, what it attaches to the project, and when it does nothing.
 */
class BuildOutputTest {

    @TempDir
    Path dir;

    @Test
    void capFileIsNamedAfterTheFinalNameAndAttached() throws Exception {
        MojoRunner runner = runner();
        runner.execute();

        File cap = dir.resolve("target/sample-applet-1.0.cap").toFile();
        assertThat(cap).isNotEmpty();
        assertThat(runner.attachments()).containsExactly(new Attachment("cap", null, cap));
    }

    @Test
    void exportFileIsAttachedWhenThePackageIsExported() throws Exception {
        MojoRunner runner = MojoRunner.forProject(dir, Samples.libraryPackage().compile(dir))
                .configure("packageAid", "A00000006230");
        runner.execute();

        assertThat(runner.attachments()).containsExactly(
                new Attachment("cap", null, dir.resolve("target/sample-applet-1.0.cap").toFile()),
                new Attachment("exp", null, dir.resolve("target/sample-applet-1.0.exp").toFile()));
    }

    @Test
    void attachFalseOnlyWritesTheFiles() throws Exception {
        MojoRunner runner = runner().configure("attach", false);
        runner.execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
        assertThat(runner.attachments()).isEmpty();
    }

    @Test
    void classifierNamesAndAttachesTheFiles() throws Exception {
        MojoRunner runner = runner().configure("classifier", "hello");
        runner.execute();

        File cap = dir.resolve("target/sample-applet-1.0-hello.cap").toFile();
        assertThat(cap).isNotEmpty();
        assertThat(runner.attachments()).containsExactly(new Attachment("cap", "hello", cap));
    }

    @Test
    void outputDirectoryAndFinalNameAreConfigurable() throws Exception {
        runner().configure("outputDirectory", dir.resolve("out").toFile())
                .configure("finalName", "hello").execute();

        assertThat(dir.resolve("out/hello.cap")).isNotEmptyFile();
    }

    @Test
    void outputDirectoryPropertyIsHonoured() throws Exception {
        runner().property("javacard.outputDirectory", dir.resolve("cap-out").toString()).execute();

        assertThat(dir.resolve("cap-out/sample-applet-1.0.cap")).isNotEmptyFile();
    }

    @Test
    void twoExecutionsThatWriteTheSameFileFail() throws Exception {
        // Two targets without classifiers: the second CAP file silently replaced the first
        MojoRunner runner = runner().execution("jc304").configure("javaCardVersion", "3.0.4");
        runner.execute();
        runner.execution("jc305").configure("javaCardVersion", "3.0.5");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'jc304'").hasMessageContaining("'jc305'")
                .hasMessageContaining(dir.resolve("target/sample-applet-1.0.cap").toString())
                .hasMessageContaining("<classifier>");
    }

    @Test
    void twoExecutionsThatAttachTheSameClassifierFail() throws Exception {
        MojoRunner runner = runner().execution("first").configure("classifier", "card");
        runner.execute();
        runner.execution("second").configure("finalName", "other");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'first'").hasMessageContaining("'second'")
                .hasMessageContaining("classifier card");
    }

    @Test
    void executionsWithTheirOwnClassifiersBuildTheirOwnFiles() throws Exception {
        MojoRunner runner = runner().execution("jc304").configure("javaCardVersion", "3.0.4")
                .configure("classifier", "jc304");
        runner.execute();
        runner.execution("jc305").configure("javaCardVersion", "3.0.5").configure("classifier", "jc305");
        runner.execute();

        assertThat(dir.resolve("target/sample-applet-1.0-jc304.cap")).isNotEmptyFile();
        assertThat(dir.resolve("target/sample-applet-1.0-jc305.cap")).isNotEmptyFile();
    }

    @Test
    void aCommandLineRunAfterTheBoundExecutionIsNoConflict() throws Exception {
        // mvn package javacard-express:build: the goal runs twice with the same configuration
        MojoRunner runner = runner();
        runner.execute();
        runner.fromCommandLine().execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
    }

    @Test
    void theCapFileAndTheBuildDescriptorAreWrittenIntoTheClassesDirectory() throws Exception {
        Path classes = Samples.helloApplet().compile(dir);
        MojoRunner.forProject(dir, classes).configure("packageAid", "A00000006212").execute();

        assertThat(classes.resolve("com/example/hello/javacard/hello.cap"))
                .hasBinaryContent(Files.readAllBytes(dir.resolve("target/sample-applet-1.0.cap")));
        assertThat(classes.resolve("com/example/hello/javacard/hello.exp")).doesNotExist();
        assertThat(classes.resolve("META-INF/javacard/com.example.hello.properties")).content().isEqualTo("""
                # How javacard-express-maven-plugin built package com.example.hello
                package=com.example.hello
                packageAid=A00000006212
                packageVersion=1.0
                javaCardVersion=3.0.5
                supportInt32=false
                export=false
                cap=com/example/hello/javacard/hello.cap
                applet.com.example.hello.HelloApplet=A0000000621201
                project=com.example:sample-applet
                pluginVersion=%s
                """.formatted(PluginDescriptor.pluginElement("version").orElseThrow()));
    }

    @Test
    void aLibraryHasItsExportFileWhereImportingPackagesLookForIt() throws Exception {
        Path classes = Samples.libraryPackage().compile(dir);
        MojoRunner.forProject(dir, classes).configure("packageAid", "A00000006230").configure("supportInt32", true)
                .configure("javaCardVersion", "3.0.4").execute();

        assertThat(classes.resolve(ExportFileLookup.location("com.example.lib")))
                .hasBinaryContent(Files.readAllBytes(dir.resolve("target/sample-applet-1.0.exp")));
        assertThat(classes.resolve("META-INF/javacard/com.example.lib.properties")).content()
                .contains("javaCardVersion=3.0.4\nsupportInt32=true\nexport=true\n").doesNotContain("applet.");
    }

    @Test
    void classesOutputFalseLeavesTheClassesDirectoryAlone() throws Exception {
        Path classes = Samples.helloApplet().compile(dir);
        MojoRunner.forProject(dir, classes).configure("packageAid", "A00000006212").configure("classesOutput", false)
                .execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).isNotEmptyFile();
        assertThat(classes.resolve("com/example/hello/javacard")).doesNotExist();
        assertThat(classes.resolve("META-INF/javacard")).doesNotExist();
    }

    @Test
    void theFirstExecutionOfAPackageWritesTheClassesDirectory() throws Exception {
        Path classes = Samples.helloApplet().compile(dir);
        MojoRunner runner = MojoRunner.forProject(dir, classes).configure("packageAid", "A00000006212")
                .execution("jc304").configure("javaCardVersion", "3.0.4").configure("classifier", "jc304");
        runner.execute();
        runner.execution("jc305").configure("javaCardVersion", "3.0.5").configure("classifier", "jc305").execute();

        assertThat(classes.resolve("META-INF/javacard/com.example.hello.properties")).content()
                .contains("javaCardVersion=3.0.4");
        assertThat(runner.log().messages(Level.INFO))
                .anyMatch(m -> m.contains("execution 'jc304' already wrote the files of package com.example.hello"));
    }

    @Test
    void anExportFileOfAnEarlierBuildIsDeletedWhenThePackageIsNoLongerExported() throws Exception {
        Path classes = Samples.helloApplet().compile(dir);
        Path stale = classes.resolve("com/example/hello/javacard/hello.exp");
        Files.createDirectories(stale.getParent());
        Files.write(stale, new byte[] {1});
        MojoRunner.forProject(dir, classes).configure("packageAid", "A00000006212").execute();

        assertThat(stale).doesNotExist();
    }

    @Test
    void theGoalRunsAfterCompilationBeforeTheTests() {
        assertThat(PluginDescriptor.buildGoal().mojoElement("phase")).contains("process-classes");
    }

    @Test
    void goalIsThreadSafe() {
        assertThat(PluginDescriptor.buildGoal().mojoElement("threadSafe")).contains("true");
    }

    @Test
    void runsOnEveryMaven39AndRequiresJava25() throws Exception {
        // Only stable Maven 3 APIs are used; the converter needs java.lang.classfile (JDK 25).
        // Checked by hand: the README Quick Start project (tests + CAP) builds with Maven 3.9.0.
        assertThat(PluginDescriptor.pluginElement("requiredMavenVersion")).contains("3.9.0");
        assertThat(PluginDescriptor.pluginElement("requiredJavaVersion")).contains("25");
        assertThat(Files.readString(Path.of("README.md")))
                .contains("Maven 3.9.0 or newer").contains("JDK 25 or newer");
    }

    @Test
    void skipParameterSkipsTheBuild() throws Exception {
        MojoRunner runner = runner().configure("skip", true);
        runner.execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).doesNotExist();
        assertThat(runner.attachments()).isEmpty();
        assertThat(runner.log().messages(Level.INFO)).anyMatch(m -> m.startsWith("Skipping"));
    }

    @Test
    void skipPropertySkipsTheBuild() throws Exception {
        runner().property("javacard.skip", "true").execute();

        assertThat(dir.resolve("target/sample-applet-1.0.cap")).doesNotExist();
    }

    private MojoRunner runner() {
        return MojoRunner.forProject(dir, Samples.helloApplet().compile(dir)).configure("packageAid", "A00000006212");
    }
}
