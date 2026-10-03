package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static name.velikodniy.jcexpress.plugin.AppletAidTest.applet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Classes listed in {@code <applets>} must be applets of the converted package: JCVM 3.1 &sect;6.6
 * requires each Applet component entry to point at "the static install(byte[],short,byte) method
 * of the applet", defined in "a non-abstract subclass, direct or indirect, of the
 * javacard.framework.Applet class". Anything else used to give BUILD SUCCESS and an entry that
 * pointed at a constructor; now the build fails before conversion and says why.
 */
class AppletValidationTest {

    @TempDir
    Path dir;

    @Test
    void misspelledAppletClassFailsAndListsTheAppletsOfThePackage() {
        MojoRunner runner = runner(Samples.walletPackage(), "com.example.wallet.WaletApplet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.wallet.WaletApplet").hasMessageContaining("not found")
                .hasMessageContaining("com.example.wallet.LoyaltyApplet")
                .hasMessageContaining("com.example.wallet.WalletApplet");
        assertThat(capFile()).doesNotExist();
    }

    @Test
    void abstractAppletClassFails() {
        MojoRunner runner = runner(Samples.walletPackage(), "com.example.wallet.BaseApplet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.wallet.BaseApplet").hasMessageContaining("abstract")
                .hasMessageContaining("6.6");
    }

    @Test
    void classThatIsNotAnAppletFails() {
        MojoRunner runner = runner(Samples.walletPackage(), "com.example.wallet.Amounts");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.wallet.Amounts")
                .hasMessageContaining("does not extend javacard.framework.Applet");
    }

    @Test
    void appletWithoutInstallMethodFails() {
        MojoRunner runner = runner(Samples.incompleteApplets(), "com.example.partial.NoInstallApplet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.partial.NoInstallApplet")
                .hasMessageContaining("static install(byte[], short, byte)").hasMessageContaining("6.6");
    }

    @Test
    void appletWithAnInstallMethodOfAnotherSignatureFails() {
        MojoRunner runner = runner(Samples.incompleteApplets(), "com.example.partial.WrongInstallApplet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.partial.WrongInstallApplet")
                .hasMessageContaining("static install(byte[], short, byte)");
    }

    @Test
    void appletOfAnotherPackageFails() {
        JavaSources sources = Samples.walletPackage().add("com/example/other/OtherApplet.java", """
                package com.example.other;

                import javacard.framework.APDU;
                import javacard.framework.Applet;

                public class OtherApplet extends Applet {
                    public static void install(byte[] bArray, short bOffset, byte bLength) {
                        new OtherApplet().register();
                    }

                    public void process(APDU apdu) {
                    }
                }
                """);
        MojoRunner runner = runner(sources, "com.example.other.OtherApplet")
                .configure("packageName", "com.example.wallet");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.other.OtherApplet")
                .hasMessageContaining("com.example.wallet");
    }

    @Test
    void appletListedTwiceFails() {
        MojoRunner runner = runner(Samples.walletPackage(), "com.example.wallet.WalletApplet")
                .configure("applets", List.of(applet("com.example.wallet.WalletApplet", null),
                        applet("com.example.wallet.WalletApplet", "A0000000621209")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("com.example.wallet.WalletApplet").hasMessageContaining("twice");
    }

    @Test
    void configuredSubsetIsBuiltAndTheUnlistedAppletsAreReported() throws Exception {
        MojoRunner runner = runner(Samples.walletPackage(), "com.example.wallet.WalletApplet");
        runner.execute();

        CapFile cap = CapFile.read(capFile());
        assertThat(cap.applets()).extracting(CapFile.Applet::aid).containsExactly("A0000000621201");
        assertThat(runner.log().messages(Level.WARNING))
                .anyMatch(m -> m.contains("com.example.wallet.LoyaltyApplet") && m.contains("<applets>"));
    }

    @Test
    void internalClassNamesAreAccepted() throws Exception {
        runner(Samples.walletPackage(), "com/example/wallet/WalletApplet").execute();

        assertThat(CapFile.read(capFile()).applets()).hasSize(1);
    }

    private MojoRunner runner(JavaSources sources, String appletClass) {
        return MojoRunner.forProject(dir, sources.compile(dir))
                .configure("packageAid", "A00000006212")
                .configure("applets", List.of(applet(appletClass, null)));
    }

    private Path capFile() {
        return dir.resolve("target/sample-applet-1.0.cap");
    }
}
