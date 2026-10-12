package name.velikodniy.jcexpress.embedded;

import org.globalplatform.GPSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The class loader of a simulated card takes the GlobalPlatform API from its parent, as part of the card's
 * platform, and checks that it is this module's implementation when applet code needs it: a stub of the package
 * from another class path entry is reported instead of handed to the applet.
 */
class AppletClassLoaderApiCheckTest {

    @Test
    void theGlobalPlatformApiOfThisModuleComesFromTheParent() throws Exception {
        AppletClassLoader card = new AppletClassLoader(getClass().getClassLoader());

        assertThat(card.loadClass("org.globalplatform.GPSystem")).isSameAs(GPSystem.class);
    }

    @Test
    void aStubOfTheGlobalPlatformApiIsReportedWhenAppletCodeNeedsIt(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("src/org/globalplatform/GPSystem.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package org.globalplatform; public class GPSystem { }");
        Path stubs = Files.createDirectories(dir.resolve("stubs"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac.run(null, null, null, "-d", stubs.toString(), source.toString())).isZero();

        try (URLClassLoader parent = new URLClassLoader(new URL[]{stubs.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            AppletClassLoader card = new AppletClassLoader(parent);

            assertThatThrownBy(() -> card.loadClass("org.globalplatform.GPSystem"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("org.globalplatform.GPSystem was loaded from " + stubs.toUri().toURL())
                    .hasMessageContaining("javacard-express-core");
        }
    }
}
