package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applet AIDs and the package AID (JCVM 3.1 &sect;4.2): "The RID of each applet in a CAP file
 * must be the same as the RID of the CAP file AID" and no two applets of a CAP file may share
 * an AID (&sect;4.2.2.2, &sect;6.6). An AID is a 5-byte RID plus a PIX of 0 to 11 bytes (&sect;4.2.1).
 */
class AppletAidTest {

    @TempDir
    Path dir;

    @Test
    void discoveredAppletsGetThePackageAidPlusAnIndexInClassNameOrder() throws Exception {
        CapFile cap = build(runner(Samples.twoApplets()).configure("packageAid", "A00000006212"));

        assertThat(cap.header().packageAid()).isEqualTo("A00000006212");
        assertThat(cap.applets()).extracting(CapFile.Applet::aid)
                .containsExactlyInAnyOrder("A0000000621201", "A0000000621202");
    }

    @Test
    void aidsDerivedFromTheClassNameOrderOfSeveralAppletsAreReported() throws Exception {
        // Adding an applet whose name sorts before these would shift their AIDs (AlphaApplet 01 -> 02)
        MojoRunner runner = runner(Samples.twoApplets()).configure("packageAid", "F000000001");
        runner.execute();

        assertThat(runner.log().messages(Level.WARNING)).anySatisfy(warning -> assertThat(warning)
                .contains("order").contains("com.example.multi.AlphaApplet").contains("F00000000101")
                .contains("com.example.multi.BetaApplet").contains("F00000000102")
                .contains("<applets>")
                .contains("<className>com.example.multi.AlphaApplet</className>")
                .contains("<aid>F00000000101</aid>"));
    }

    @Test
    void aSingleDiscoveredAppletOrConfiguredAppletsAreNotReported() throws Exception {
        MojoRunner single = runner(Samples.helloApplet()).configure("packageAid", "F000000001");
        single.execute();
        Path configuredDir = dir.resolve("configured");
        MojoRunner configured = MojoRunner.forProject(configuredDir, Samples.twoApplets().compile(configuredDir))
                .configure("packageAid", "F000000001")
                .configure("applets", List.of(applet("com.example.multi.BetaApplet", null),
                        applet("com.example.multi.AlphaApplet", null)));
        configured.execute();

        assertThat(single.log().messages(Level.WARNING)).noneMatch(m -> m.contains("order"));
        assertThat(configured.log().messages(Level.WARNING)).noneMatch(m -> m.contains("order"));
    }

    @Test
    void zeroConfigurationKeepsAllAidsUnderOneRid() throws Exception {
        MojoRunner runner = runner(Samples.twoApplets());
        CapFile cap = build(runner);

        String packageAid = "F0" + sha1Hex("com.example.multi").substring(0, 14);
        assertThat(cap.header().packageAid()).isEqualTo(packageAid);
        assertThat(cap.applets()).extracting(CapFile.Applet::aid)
                .containsExactlyInAnyOrder(packageAid + "01", packageAid + "02");
        assertThat(runner.log().messages(Level.WARNING))
                .anyMatch(m -> m.contains("packageAid") && m.contains(packageAid));
    }

    @Test
    void configuredAppletWithoutAidGetsAnAidDerivedFromThePackageAid() throws Exception {
        CapFile cap = build(runner(Samples.helloApplet())
                .configure("packageAid", "A00000006212")
                .configure("applets", List.of(applet("com.example.hello.HelloApplet", null))));

        assertThat(cap.applets()).extracting(CapFile.Applet::aid).containsExactly("A0000000621201");
    }

    @Test
    void configuredAppletAidIsUsedVerbatim() throws Exception {
        CapFile cap = build(runner(Samples.helloApplet())
                .configure("packageAid", "A00000006212")
                .configure("applets", List.of(applet("com.example.hello.HelloApplet", "A0:00:00:00:62:12:99"))));

        assertThat(cap.applets()).extracting(CapFile.Applet::aid).containsExactly("A0000000621299");
    }

    @Test
    void appletAidWithAnotherRidThanThePackageFails() {
        MojoRunner runner = runner(Samples.helloApplet())
                .configure("packageAid", "A00000006212")
                .configure("applets", List.of(applet("com.example.hello.HelloApplet", "A0000000771201")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("A0000000771201").hasMessageContaining("RID")
                .hasMessageContaining("A000000062").hasMessageContaining("4.2.2.2");
    }

    @Test
    void twoAppletsWithTheSameAidFail() {
        MojoRunner runner = runner(Samples.twoApplets())
                .configure("packageAid", "A00000006212")
                .configure("applets", List.of(applet("com.example.multi.AlphaApplet", "A0000000621201"),
                        applet("com.example.multi.BetaApplet", "A0000000621201")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("A0000000621201").hasMessageContaining("AlphaApplet")
                .hasMessageContaining("BetaApplet");
    }

    @Test
    void malformedAidsFailWithTheReason() {
        assertThatThrownBy(runner(Samples.helloApplet()).configure("packageAid", "A0000000621")::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("packageAid").hasMessageContaining("odd number of hex digits");
        assertThatThrownBy(runner(Samples.helloApplet()).configure("packageAid", "A0000000GG")::execute)
                .isInstanceOf(MojoExecutionException.class).hasMessageContaining("not hex digits");
        assertThatThrownBy(runner(Samples.helloApplet()).configure("packageAid", "A00000")::execute)
                .isInstanceOf(MojoExecutionException.class).hasMessageContaining("5 to 16 bytes");
    }

    @Test
    void appletAidEqualToThePackageAidFails() {
        // The CAP file (package) and each applet are distinct AID-named entities (JCVM 3.1 4.2.2); the
        // converter rejects the same AID for both, so the plugin stops before converting, naming the elements.
        MojoRunner runner = runner(Samples.helloApplet())
                .configure("packageAid", "A00000006212")
                .configure("applets", List.of(applet("com.example.hello.HelloApplet", "A00000006212")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("A00000006212").hasMessageContaining("HelloApplet")
                .hasMessageContaining("same AID as the package").hasMessageContaining("<packageAid>");
    }

    @Test
    void sixteenBytePackageAidLeavesNoRoomForDerivedAppletAids() {
        MojoRunner runner = runner(Samples.helloApplet()).configure("packageAid", "A0000000621200000000000000000000");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("16 bytes").hasMessageContaining("<aid>");
    }

    private MojoRunner runner(name.velikodniy.jcexpress.plugin.testing.JavaSources sources) {
        return MojoRunner.forProject(dir, sources.compile(dir));
    }

    private CapFile build(MojoRunner runner) throws Exception {
        runner.execute();
        return CapFile.read(dir.resolve("target/sample-applet-1.0.cap"));
    }

    static AppletConfig applet(String className, String aid) {
        AppletConfig config = new AppletConfig();
        config.setClassName(className);
        config.setAid(aid);
        return config;
    }

    private static String sha1Hex(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(hash);
    }
}
