package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.BuildDescriptor;
import name.velikodniy.jcexpress.livecard.model.built.BuiltApplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a failed SELECT on a GlobalPlatform backend says about an AID the Maven plugin's build gave an applet: the
 * run's way to address an applet it installed, and how to declare one it did not install (a call such as
 * {@code card.select(OtherApplet.class)} would fail for that one).
 */
class RunAidsTest {

    private static final String PREFIX = "F04A4358";
    private static final String OTHER = "name.velikodniy.jcexpress.livecard.model.built.OtherApplet";
    private static final AID BUILT_AID = AID.fromHex("A0000000629901");
    private static final AID OTHER_AID = AID.fromHex("A0000000629902");

    private final RunAids aids = new RunAids();

    @BeforeEach
    void build() {
        Map<String, AID> applets = new LinkedHashMap<>();
        applets.put(BuiltApplet.class.getName(), BUILT_AID);
        applets.put(OTHER, OTHER_AID);
        aids.built(new BuildDescriptor("name.velikodniy.jcexpress.livecard.model.built", AID.fromHex("A00000006299"),
                "1.0", "3.0.4", false, applets, "com.example:built"));
        aids.installed(new AppletDeclaration(BuiltApplet.class, AID.fromHex("F04A4358AA"),
                AID.fromHex("F04A4358AA01"), AID.fromHex("F04A4358AA01"), null, Isolation.PER_TEST));
    }

    @Test
    void theBuildAidOfAnInstalledAppletNamesTheRunsWayAndItsRunAid() {
        assertThat(aids.explain(BUILT_AID, PREFIX))
                .contains("A0000000629901 is the AID the build gave " + BuiltApplet.class.getName())
                .contains("card.select(BuiltApplet.class)")
                .contains("F04A4358AA01");
    }

    @Test
    void theBuildAidOfAnAppletTheRunDidNotInstallSaysHowToDeclareIt() {
        assertThat(aids.explain(OTHER_AID, PREFIX))
                .contains("A0000000629902 is the AID the build gave " + OTHER)
                .contains("this run did not install it")
                .contains("@InstallApplet(OtherApplet.class)")
                .doesNotContain("card.select(OtherApplet.class)");
    }
}
