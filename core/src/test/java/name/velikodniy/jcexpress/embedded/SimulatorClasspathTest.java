package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.base.SimulatorRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The jCardSim classpath check of {@link EmbeddedSession}: Java Card API stubs that shadow jCardSim's own
 * {@code javacard.framework} classes are reported with the cause and the fix instead of jCardSim's
 * "Internal reflection error".
 */
class SimulatorClasspathTest {

    @Test
    void theTestClasspathTakesTheApiFromJCardSim() {
        assertThatCode(SimulatorClasspath::verify).doesNotThrowAnyException();
    }

    @Test
    void differentLocationsAreReportedWithBothJarsAndTheFix() throws Exception {
        URL stubs = URI.create("file:/repo/javacard-express-api-0.3.0.jar").toURL();
        URL jcardsim = URI.create("file:/repo/jcardsim-3.0.6.0.jar").toURL();

        assertThatThrownBy(() -> SimulatorClasspath.verify(stubs, jcardsim))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("javacard-express-api-0.3.0.jar")
                .hasMessageContaining("jcardsim-3.0.6.0.jar")
                .hasMessageContaining("javacard-express-core before javacard-express-api")
                .hasMessageContaining("classpathDependencyExclude");
    }

    /** IDEs build the test class path from the dependency order and do not apply Surefire's exclude. */
    @Test
    void theMessageExplainsWhatFixesRunsInAnIde() throws Exception {
        URL stubs = URI.create("file:/repo/javacard-express-api-0.3.0.jar").toURL();
        URL jcardsim = URI.create("file:/repo/jcardsim-3.0.6.0.jar").toURL();

        assertThatThrownBy(() -> SimulatorClasspath.verify(stubs, jcardsim))
                .hasMessageContaining("IDE")
                .hasMessageContaining("dependency order");
    }

    @Test
    void unknownLocationIsReported() throws Exception {
        URL jcardsim = URI.create("file:/repo/jcardsim-3.0.6.0.jar").toURL();

        assertThatThrownBy(() -> SimulatorClasspath.verify(null, jcardsim)).isInstanceOf(IllegalStateException.class);
    }

    /** A stub {@code javacard.framework.APDU} first on the classpath, as with the README's dependency order. */
    @Test
    void embeddedSessionFailsFastWhenStubsComeFirst(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("src/javacard/framework/APDU.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package javacard.framework; public final class APDU { }");
        Path stubs = Files.createDirectories(dir.resolve("stubs"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac.run(null, null, null, "-d", stubs.toString(), source.toString())).isZero();

        URL[] classpath = {stubs.toUri().toURL(), location(SimulatorRuntime.class), location(EmbeddedSession.class)};
        try (URLClassLoader loader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
            Class<?> session = loader.loadClass(EmbeddedSession.class.getName());

            assertThatThrownBy(() -> session.getConstructor().newInstance())
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(classpath[0].toExternalForm())
                    .hasMessageContaining("javacard-express-core before javacard-express-api");
        }
    }

    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
