package name.velikodniy.jcexpress.livecard.sim;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.model.failing.ParametersRequiredApplet;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A jCardSim card of the simulated GlobalPlatform card installs an AID again after deleting it, as a card does
 * after DELETE of an application (GlobalPlatform Card Specification v2.3.1 11.2, 11.5): a new instance, of the same
 * or of another applet class. jCardSim's own simulator refuses that AID for good.
 */
class JCardSimCardTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final AID INSTANCE = AID.fromHex("F04A4358010101");
    private static final String COUNTER = "8005000002";

    @Test
    void deletedAidIsInstalledAgainAsANewInstance() {
        try (JCardSimCard card = new JCardSimCard(TestClassPath.applets())) {
            card.install(TestApplet.HELLO.className(), INSTANCE, new byte[0]);
            card.select(INSTANCE);
            card.transmit(HEX.parseHex(COUNTER));
            assertThat(HEX.formatHex(card.transmit(HEX.parseHex(COUNTER)))).isEqualTo("00029000");

            card.delete(INSTANCE);
            card.install(TestApplet.HELLO.className(), INSTANCE, new byte[0]);

            assertThat(HEX.formatHex(card.select(INSTANCE))).isEqualTo("9000");
            assertThat(HEX.formatHex(card.transmit(HEX.parseHex(COUNTER)))).as("the counter of a new instance")
                    .isEqualTo("00019000");
        }
    }

    @Test
    void deletedAidIsInstalledAgainFromAnotherAppletClass() {
        try (JCardSimCard card = new JCardSimCard(TestClassPath.applets())) {
            card.install(TestApplet.HELLO.className(), INSTANCE, new byte[0]);
            card.delete(INSTANCE);

            card.install(TestApplet.PARAMS.className(), INSTANCE, new byte[]{0x11, 0x22});

            card.select(INSTANCE);
            assertThat(HEX.formatHex(card.transmit(HEX.parseHex("8001000000")))).as("ParamsApplet's parameters")
                    .isEqualTo("11229000");
            assertThat(HEX.formatHex(card.transmit(HEX.parseHex("8002000000")))).as("its own AID")
                    .isEqualTo("F04A43580101019000");
        }
    }

    @Test
    void deleteAndInstallManyTimes() {
        try (JCardSimCard card = new JCardSimCard(TestClassPath.applets())) {
            for (int round = 0; round < 5; round++) {
                card.install(TestApplet.HELLO.className(), INSTANCE, new byte[0]);
                card.install(TestApplet.PARAMS.className(), AID.fromHex("F04A4358010201"), new byte[0]);
                card.delete(INSTANCE);
                card.delete(AID.fromHex("F04A4358010201"));
            }
            card.install(TestApplet.HELLO.className(), INSTANCE, new byte[0]);

            assertThat(HEX.formatHex(card.select(INSTANCE))).isEqualTo("9000");
        }
    }

    /** The simulated ISD answers a failed install with a status word, and a card can install the AID later. */
    @Test
    void anInstallMethodThatFailsIsReportedAndItsAidStaysFree() throws URISyntaxException {
        Path testClasses = Path.of(ParametersRequiredApplet.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        try (JCardSimCard card = new JCardSimCard(List.of(testClasses))) {
            InstallException failure = catchThrowableOfType(InstallException.class,
                    () -> card.install(ParametersRequiredApplet.class.getName(), INSTANCE, new byte[0]));

            assertThat(failure).hasMessageContaining("as F04A4358010101 with install parameters (none) failed: its"
                    + " install method threw ISOException with reason 6A80");
            assertThat(failure.sw()).isZero();

            card.install(ParametersRequiredApplet.class.getName(), INSTANCE, new byte[]{0x01});
            assertThat(HEX.formatHex(card.select(INSTANCE))).isEqualTo("9000");
        }
    }
}
