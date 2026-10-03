package name.velikodniy.jcexpress.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolution of the simulator used by {@code @SmartCard(mode = CONTAINER)}.
 *
 * <p>Regression for the audit finding "annotation container mode unusable for consumers": the default
 * ({@code image = ""}) used to require a {@code docker/} directory with a Maven-built server jar next to the
 * consumer's working directory, so it failed in every project except this repository.</p>
 */
class SimulatorSourceTest {

    @Test
    void defaultIsTheBundledServerWhateverTheWorkingDirectory() {
        assertThat(SimulatorSource.resolve("", Map.of())).isInstanceOf(SimulatorSource.Bundled.class);
    }

    @Test
    void annotationImageTakesPrecedence() {
        SimulatorSource source = SimulatorSource.resolve("registry.example/jcx-simulator:1.0",
                Map.of(SimulatorSource.IMAGE_PROPERTY, "other:2"));

        assertThat(source).isEqualTo(new SimulatorSource.Image("registry.example/jcx-simulator:1.0"));
    }

    @Test
    void imageSystemPropertyAppliesWhenTheAnnotationHasNoImage() {
        SimulatorSource source = SimulatorSource.resolve("",
                Map.of(SimulatorSource.IMAGE_PROPERTY, "ghcr.io/g0ddest/jcx-simulator:1.2.3"));

        assertThat(source).isEqualTo(new SimulatorSource.Image("ghcr.io/g0ddest/jcx-simulator:1.2.3"));
    }

    @Test
    void dockerDirPropertyPointingNowhereFailsLoudly() {
        Map<String, String> props = Map.of(SimulatorSource.DOCKER_DIR_PROPERTY, "/nonexistent/typo");

        assertThatThrownBy(() -> SimulatorSource.resolve("", props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/nonexistent/typo")
                .hasMessageContaining(SimulatorSource.DOCKER_DIR_PROPERTY);
    }

    @Test
    void dockerDirWithoutBuiltServerJarFailsLoudly(@TempDir Path dir) {
        Map<String, String> props = Map.of(SimulatorSource.DOCKER_DIR_PROPERTY, dir.toString());

        assertThatThrownBy(() -> SimulatorSource.resolve("", props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(dir.toString())
                .hasMessageContaining("mvn package");
    }

    @Test
    void dockerDirWithBuiltServerJarIsUsed(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("target"));
        Files.write(dir.resolve("target/jcx-simulator.jar"), new byte[]{1});

        SimulatorSource source = SimulatorSource.resolve("",
                Map.of(SimulatorSource.DOCKER_DIR_PROPERTY, dir.toString()));

        assertThat(source).isEqualTo(new SimulatorSource.DockerProject(dir.resolve("target/jcx-simulator.jar")));
    }

    @Test
    void bundledServerJarIsARunnableJarInsideTheArtifact() throws IOException {
        try (InputStream in = SimulatorSource.Bundled.openServerJar();
             JarInputStream jar = new JarInputStream(in)) {
            Manifest manifest = jar.getManifest();

            assertThat(manifest).isNotNull();
            assertThat(manifest.getMainAttributes().getValue("Main-Class"))
                    .isEqualTo("name.velikodniy.jcexpress.server.JcxServer");
        }
    }
}
