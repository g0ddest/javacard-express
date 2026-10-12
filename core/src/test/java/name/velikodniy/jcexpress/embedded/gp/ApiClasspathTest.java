package name.velikodniy.jcexpress.embedded.gp;

import org.globalplatform.GPSystem;
import org.globalplatform.SecureChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The class path check for the GlobalPlatform API: a class of {@code org.globalplatform} that applet code gets
 * must be this module's, because only these work on the simulated card; a stub of another jar is reported with
 * where it came from and the fix.
 */
class ApiClasspathTest {

    @Test
    void classesOfThisModulePass() {
        assertThatCode(() -> ApiClasspath.verify(GPSystem.class)).doesNotThrowAnyException();
        assertThatCode(() -> ApiClasspath.verify(SecureChannel.class)).doesNotThrowAnyException();
    }

    @Test
    void differentLocationsAreReportedWithBothLocationsAndTheFix() throws Exception {
        URL stubs = URI.create("file:/repo/gpapi-globalplatform.jar").toURL();
        URL core = URI.create("file:/repo/javacard-express-core-1.2.3.jar").toURL();

        assertThatCode(() -> ApiClasspath.verify("org.globalplatform.GPSystem", core, core)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ApiClasspath.verify("org.globalplatform.GPSystem", stubs, core))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.globalplatform.GPSystem was loaded from file:/repo/gpapi-globalplatform.jar")
                .hasMessageContaining("javacard-express-core-1.2.3.jar")
                .hasMessageContaining("classpathDependencyExcludes")
                .hasMessageContaining("dependency order");
        assertThatThrownBy(() -> ApiClasspath.verify("org.globalplatform.GPSystem", null, core))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A stub {@code GPSystem} of another class path entry: its methods would return null to the applet. */
    @Test
    void aStubOfThePackageIsReported(@TempDir Path dir) throws Exception {
        Path stubs = compile(dir, "org.globalplatform", "GPSystem");

        try (URLClassLoader loader = new URLClassLoader(new URL[]{stubs.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> stub = loader.loadClass("org.globalplatform.GPSystem");

            assertThatThrownBy(() -> ApiClasspath.verify(stub)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("org.globalplatform.GPSystem was loaded from " + stubs.toUri().toURL())
                    .hasMessageContaining("javacard-express-core");
        }
    }

    /** The other GlobalPlatform packages are not this module's: a project brings them itself. */
    @Test
    void classesOfOtherPackagesAreNotChecked(@TempDir Path dir) throws Exception {
        Path other = compile(dir, "org.globalplatform.upgrade", "UpgradeManager");

        try (URLClassLoader loader = new URLClassLoader(new URL[]{other.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> upgrade = loader.loadClass("org.globalplatform.upgrade.UpgradeManager");

            assertThatCode(() -> ApiClasspath.verify(upgrade)).doesNotThrowAnyException();
        }
        assertThatCode(() -> ApiClasspath.verify(String.class)).doesNotThrowAnyException();
    }

    @Test
    void theCheckCanBeSwitchedOff(@TempDir Path dir) throws Exception {
        Path stubs = compile(dir, "org.globalplatform", "GPSystem");
        String before = System.getProperty(ApiClasspath.DISABLE_PROPERTY);
        System.setProperty(ApiClasspath.DISABLE_PROPERTY, "false");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{stubs.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> stub = loader.loadClass("org.globalplatform.GPSystem");

            assertThatCode(() -> ApiClasspath.verify(stub)).doesNotThrowAnyException();
        } finally {
            if (before == null) {
                System.clearProperty(ApiClasspath.DISABLE_PROPERTY);
            } else {
                System.setProperty(ApiClasspath.DISABLE_PROPERTY, before);
            }
        }
    }

    /** Compiles an empty public class and returns the classes directory. */
    private static Path compile(Path dir, String packageName, String className) throws Exception {
        Path source = dir.resolve("src").resolve(packageName.replace('.', '/')).resolve(className + ".java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package " + packageName + "; public class " + className + " { }");
        Path classes = Files.createDirectories(dir.resolve("classes"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac.run(null, null, null, "-d", classes.toString(), source.toString())).isZero();
        return classes;
    }
}
