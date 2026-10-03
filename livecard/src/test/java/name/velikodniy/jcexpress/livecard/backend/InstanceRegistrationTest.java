package name.velikodniy.jcexpress.livecard.backend;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import name.velikodniy.jcexpress.livecard.model.applet.ModelApplet;
import name.velikodniy.jcexpress.livecard.model.applet.OtherApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalPlatform requires an applet to register with the instance AID of INSTALL [for install]:
 * {@code register(bArray, (short) (bOffset + 1), bArray[bOffset])} (GPCS v2.3.1 A.1, "Installation"); the method
 * {@code register()} without arguments assigns the applet's own AID (Java Card API, {@code Applet.register()}).
 * jCardSim registers an instance under the AID of its installation either way, so the GlobalPlatform backends note
 * an instance whose AID differs from its applet's own AID when the applet registers without arguments: a failed
 * test and {@code -Djcx.log=true} show the note.
 */
class InstanceRegistrationTest {

    private static final String NOTE = "calls register() without arguments";

    @TempDir
    Path transcripts;

    private TestCard open(Class<? extends Applet> declared) {
        Map<String, String> settings = Map.of("jcx.livecard.transcriptDir", transcripts.toString());
        return new SimulatedGpBackend().open(new CardRequest(InstanceRegistrationTest.class,
                key -> Optional.ofNullable(settings.get(key)), List.of(declared)));
    }

    private static AppletDeclaration declaration(TestCard card, Class<? extends Applet> applet, String suffix) {
        return AppletDeclaration.of(card.aids(), applet, suffix == null ? null : card.aids().aid(suffix), null,
                Isolation.PER_TEST);
    }

    @Test
    void anAppletThatRegistersWithoutArgumentsIsRecognised_gpcs231_A_1() {
        assertThat(InstanceRegistration.callsRegisterWithoutArguments(OtherApplet.class)).isTrue();
        assertThat(InstanceRegistration.callsRegisterWithoutArguments(ModelApplet.class)).isFalse();
    }

    @Test
    void anInstanceAidOtherThanTheAppletsOwnIsNotedForSuchAnApplet_gpcs231_A_1() {
        try (TestCard card = open(OtherApplet.class)) {
            AppletDeclaration own = declaration(card, OtherApplet.class, null);
            AppletDeclaration second = declaration(card, OtherApplet.class, "0301");

            card.install(own, List.of(own));
            assertThat(card.session().history().transcript()).doesNotContain(NOTE);
            card.install(second, List.of(own, second));

            assertThat(card.session().history().transcript())
                    .contains("# " + OtherApplet.class.getName() + " " + NOTE + ", which registers an instance under"
                            + " the applet's own AID " + own.moduleAid().toHex())
                    .contains("register(bArray, (short) (bOffset + 1), bArray[bOffset])")
                    .contains("GPCS v2.3.1 A.1")
                    .contains("jCardSim accepts the instance AID " + second.instanceAid().toHex());
        }
    }

    @Test
    void anAppletThatRegistersWithTheInstanceAidIsNotNoted() {
        try (TestCard card = open(ModelApplet.class)) {
            AppletDeclaration own = declaration(card, ModelApplet.class, null);
            AppletDeclaration second = declaration(card, ModelApplet.class, "0302");

            card.install(own, List.of(own));
            card.install(second, List.of(own, second));

            assertThat(card.session().history().transcript()).doesNotContain(NOTE);
        }
    }
}
