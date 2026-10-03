package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Which package is converted. A CAP file in compact format holds exactly one package, stored in
 * the directory of that package (JCVM 3.1 &sect;4.1.2, &sect;4.1.3), so the plugin never guesses:
 * with classes in several packages, {@code <packageName>} must say which one, and classes in the
 * unnamed (default) package cannot be converted at all.
 */
class PackageSelectionTest {

    @TempDir
    Path dir;

    @Test
    void theOnlyPackageIsSelected() throws Exception {
        MojoRunner runner = runner(Samples.walletPackage());
        runner.execute();

        assertThat(runner.log().messages(Level.INFO)).contains("Package: com.example.wallet");
        assertThat(capFile()).exists();
    }

    @Test
    void severalPackagesWithoutPackageNameFailAndListThePackages() {
        MojoRunner runner = runner(Samples.walletPackage().add("com/example/wallet/extra/ExtraApplet.java",
                applet("com.example.wallet.extra", "ExtraApplet")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.wallet (").hasMessageContaining("com.example.wallet.extra (")
                .hasMessageContaining("<packageName>").hasMessageContaining("one package");
        assertThat(capFile()).doesNotExist();
    }

    @Test
    void aHelperPackageThatTheAppletUsesIsToBeMovedIntoItsPackageFirst() {
        // <packageName> alone builds the applet package and then fails to link to the helper package, which
        // needs its own CAP file and export file; moving the helper is the simple way out
        MojoRunner runner = runner(JavaSources.create()
                .add("com/example/counter/CounterApplet.java", """
                        package com.example.counter;

                        import com.example.counter.util.Bytes;

                        public class CounterApplet extends javacard.framework.Applet {
                            public static void install(byte[] bArray, short bOffset, byte bLength) {
                                new CounterApplet().register();
                            }

                            public void process(javacard.framework.APDU apdu) {
                                Bytes.clear(apdu.getBuffer());
                            }
                        }
                        """)
                .add("com/example/counter/util/Bytes.java", """
                        package com.example.counter.util;

                        public final class Bytes {
                            public static void clear(byte[] buffer) {
                                buffer[0] = 0;
                            }
                        }
                        """));

        Throwable failure = catchThrowable(runner::execute);

        assertThat(failure).isInstanceOf(MojoExecutionException.class);
        String message = failure.getMessage();
        assertThat(message).contains("com.example.counter uses com.example.counter.util")
                .contains("move the classes of com.example.counter.util into com.example.counter")
                .contains("<importExportFiles>");
        assertThat(message.indexOf("move the classes")).isLessThan(message.indexOf("<packageName>"));
    }

    @Test
    void independentPackagesAreToBeBuiltOneExecutionEach() {
        MojoRunner runner = runner(Samples.walletPackage().add("com/example/wallet/extra/ExtraApplet.java",
                applet("com.example.wallet.extra", "ExtraApplet")));

        Throwable failure = catchThrowable(runner::execute);

        assertThat(failure.getMessage()).contains("one plugin execution per package").contains("<classifier>")
                .doesNotContain(" uses ");
    }

    @Test
    void configuredPackageIsBuiltAndClassesOfOtherPackagesAreReported() throws Exception {
        MojoRunner runner = runner(Samples.walletPackage().add("com/example/wallet/extra/ExtraApplet.java",
                applet("com.example.wallet.extra", "ExtraApplet")))
                .configure("packageName", "com.example.wallet");
        runner.execute();

        assertThat(CapFile.read(capFile()).applets()).hasSize(2);
        assertThat(runner.log().messages(Level.WARNING)).anyMatch(m -> m.contains("com.example.wallet.extra")
                && m.contains("com.example.wallet.extra.ExtraApplet") && m.contains("not part of this CAP file"));
    }

    @Test
    void configuredPackageWithoutClassesFails() {
        MojoRunner runner = runner(Samples.walletPackage()).configure("packageName", "com.example.walet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.walet").hasMessageContaining("no classes")
                .hasMessageContaining("com.example.wallet");
    }

    @Test
    void packageNameInInternalFormIsAccepted() throws Exception {
        MojoRunner runner = runner(Samples.walletPackage()).configure("packageName", "com/example/wallet");
        runner.execute();

        assertThat(runner.log().messages(Level.INFO)).contains("Package: com.example.wallet");
    }

    @Test
    void malformedPackageNameFails() {
        MojoRunner runner = runner(Samples.walletPackage()).configure("packageName", "com.example..wallet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'com.example..wallet'").hasMessageContaining("package name");
    }

    @Test
    void classesInTheDefaultPackageFail() {
        MojoRunner runner = runner(JavaSources.create().add("RootApplet.java", applet(null, "RootApplet")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("default package").hasMessageContaining("RootApplet");
        assertThat(capFile()).doesNotExist();
    }

    @Test
    void defaultPackageClassesNextToANamedPackageAreNotSilentlyDropped() {
        MojoRunner runner = runner(Samples.walletPackage().add("RootApplet.java", applet(null, "RootApplet")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("(default package)").hasMessageContaining("com.example.wallet");
    }

    @Test
    void emptyClassesDirectoryFails() throws Exception {
        Path classes = Files.createDirectories(dir.resolve("classes"));
        MojoRunner runner = MojoRunner.forProject(dir, classes);

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("No class files");
    }

    @Test
    void missingClassesDirectoryIsSkippedWithAWarning() throws Exception {
        MojoRunner runner = MojoRunner.forProject(dir, dir.resolve("classes"));
        runner.execute();

        assertThat(runner.log().messages(Level.WARNING)).anyMatch(m -> m.contains("does not exist"));
        assertThat(capFile()).doesNotExist();
    }

    private MojoRunner runner(JavaSources sources) {
        return MojoRunner.forProject(dir, sources.compile(dir)).configure("packageAid", "A00000006212");
    }

    private Path capFile() {
        return dir.resolve("target/sample-applet-1.0.cap");
    }

    private static String applet(String packageName, String simpleName) {
        return (packageName == null ? "" : "package " + packageName + ";\n\n") + """
                public class %1$s extends javacard.framework.Applet {
                    public static void install(byte[] bArray, short bOffset, byte bLength) {
                        new %1$s().register();
                    }

                    public void process(javacard.framework.APDU apdu) {
                    }
                }
                """.formatted(simpleName);
    }
}
