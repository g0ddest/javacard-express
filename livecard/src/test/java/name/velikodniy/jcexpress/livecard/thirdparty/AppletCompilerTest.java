package name.velikodniy.jcexpress.livecard.thirdparty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The third-party applets compile against the API stubs wherever the tests run: IntelliJ IDEA imports the build's
 * setting of the stubs jar as the javacard-api module's pom.xml, and its test class path holds the stubs' classes
 * directory next to jCardSim.
 */
class AppletCompilerTest {

    @TempDir
    Path dir;

    @Test
    void aSettingThatIsNoStubsFallsBackToTheModulesClassesDirectory() throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Path classes = stubsDirectory(dir.resolve("javacard-api/target/classes"));

        assertThat(AppletCompiler.firstWithTheApi(List.of(pom, dir.resolve("missing"), classes))).isEqualTo(classes);
    }

    @Test
    void aStubsJarIsTakenAsTheBuildSetsIt() throws IOException {
        Path jar = dir.resolve("javacard-express-api.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("javacard/framework/Applet.class"));
            out.closeEntry();
        }

        assertThat(AppletCompiler.firstWithTheApi(List.of(jar))).isEqualTo(jar);
    }

    @Test
    void theCandidatesNeverIncludeJCardSim() {
        String classPath = String.join(File.pathSeparator, "/repo/jcardsim-3.0.6.0.jar",
                "/project/javacard-api/target/classes", "/m2/javacard-express-api-1.0.jar");

        assertThat(AppletCompiler.candidates("/project/javacard-api/pom.xml", "/project", classPath))
                .containsExactly(Path.of("/project/javacard-api/pom.xml"), Path.of("../javacard-api/target/classes"),
                        Path.of("/project/javacard-api/target/classes"), Path.of("/project/javacard-api/target/classes"),
                        Path.of("/m2/javacard-express-api-1.0.jar"));
    }

    @Test
    void noStubsAnywhereSaysWhatToBuild() {
        assertThatThrownBy(() -> AppletCompiler.firstWithTheApi(List.of(dir.resolve("none"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("mvn test-compile -pl livecard -am")
                .hasMessageContaining(AppletCompiler.STUBS_PROPERTY);
    }

    private static Path stubsDirectory(Path classes) throws IOException {
        Path applet = classes.resolve("javacard/framework/Applet.class");
        Files.createDirectories(applet.getParent());
        Files.write(applet, new byte[] {(byte) 0xCA, (byte) 0xFE});
        return classes;
    }
}
