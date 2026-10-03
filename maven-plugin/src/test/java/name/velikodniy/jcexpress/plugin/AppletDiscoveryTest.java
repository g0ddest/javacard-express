package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applets found without {@code <applets>} configuration. JCVM 3.1 &sect;6.6: "Applets are defined
 * by implementing a non-abstract subclass, direct or indirect, of the javacard.framework.Applet
 * class", and each Applet component entry points at "the static install(byte[],short,byte) method
 * of the applet".
 */
class AppletDiscoveryTest {

    /** type_descriptor nibbles of {@code ([BSB)V}: byte[], short, byte, void (JCVM 3.1 &sect;6.14.5). */
    private static final String INSTALL_SIGNATURE = "B431";

    @TempDir
    Path dir;

    @Test
    void indirectAppletSubclassesAreDiscoveredAndAbstractBaseAppletsAreNot() throws Exception {
        MojoRunner runner = runner(Samples.walletPackage());
        CapFile cap = build(runner);

        // LoyaltyApplet extends Applet, WalletApplet extends the abstract BaseApplet; the Applet
        // component order is the converter's choice (JCVM 3.1 6.6 does not prescribe one)
        assertThat(cap.applets()).extracting(CapFile.Applet::aid)
                .containsExactlyInAnyOrder("A0000000621201", "A0000000621202");
        assertThat(runner.log().messages(Level.INFO))
                .contains("Applet: com.example.wallet.LoyaltyApplet (AID A0000000621201, derived from the package AID)",
                        "Applet: com.example.wallet.WalletApplet (AID A0000000621202, derived from the package AID)");
        assertThat(runner.log().text()).doesNotContain("Applet: com.example.wallet.BaseApplet");
    }

    @Test
    void everyAppletEntryPointsAtAStaticInstallMethod() throws Exception {
        CapFile cap = build(runner(Samples.walletPackage()));

        assertThat(cap.applets()).hasSize(2).allSatisfy(applet -> {
            CapFile.MethodLocation install = cap.methodAt(applet.installMethodOffset()).orElseThrow(
                    () -> new AssertionError("no method_info at install offset " + applet.installMethodOffset()));
            assertThat(install.method().flags() & CapFile.MethodDescriptor.ACC_STATIC)
                    .as("install method of %s is static", applet.aid()).isNotZero();
            assertThat(install.method().flags() & CapFile.MethodDescriptor.ACC_INIT)
                    .as("install method of %s is not a constructor", applet.aid()).isZero();
            assertThat(cap.typeNibbles(install.method().typeOffset())).isEqualTo(INSTALL_SIGNATURE);
        });
        assertThat(cap.applets()).extracting(CapFile.Applet::installMethodOffset).doesNotHaveDuplicates();
    }

    @Test
    void appletSubclassesWithoutAStaticInstallMethodAreSkippedWithAWarning() throws Exception {
        MojoRunner runner = runner(Samples.incompleteApplets());
        CapFile cap = build(runner);

        assertThat(cap.applets()).extracting(CapFile.Applet::aid).containsExactly("A0000000621201");
        assertThat(runner.log().messages(Level.INFO)).anyMatch(m -> m.startsWith("Applet: com.example.partial.GoodApplet"));
        assertThat(runner.log().messages(Level.WARNING))
                .anyMatch(m -> m.contains("com.example.partial.NoInstallApplet") && m.contains("install(byte[], short, byte)"))
                .anyMatch(m -> m.contains("com.example.partial.WrongInstallApplet") && m.contains("install(byte[], short, byte)"));
    }

    @Test
    void packageWithoutAppletsIsALibraryPackage() throws Exception {
        MojoRunner runner = runner(Samples.libraryPackage());
        CapFile cap = build(runner);

        assertThat(cap.has("Applet")).isFalse();
        assertThat(cap.header().flags() & CapFile.ACC_APPLET).isZero();
        assertThat(runner.log().messages(Level.WARNING)).noneMatch(m -> m.contains("applet"));
        assertThat(runner.log().messages(Level.INFO)).anyMatch(m -> m.contains("library package"));
    }

    private MojoRunner runner(JavaSources sources) {
        return MojoRunner.forProject(dir, sources.compile(dir)).configure("packageAid", "A00000006212");
    }

    private CapFile build(MojoRunner runner) throws Exception {
        runner.execute();
        return CapFile.read(dir.resolve("target/sample-applet-1.0.cap"));
    }
}
