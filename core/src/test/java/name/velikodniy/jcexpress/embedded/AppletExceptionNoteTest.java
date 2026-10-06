package name.velikodniy.jcexpress.embedded;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.fakes.ParametersRequiredApplet;
import name.velikodniy.jcexpress.fakes.ThrowingApplet;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.fakes.Transcripts.withoutTimes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * An exception that escapes the applet's {@code process}, {@code select} or {@code install} method: a Java Card
 * runtime answers it with '6F00' (JCRE 3.0.5 chapter 3, the applet's methods) and jCardSim does not pass it on, so
 * the session notes it in the history right after the exchange, with the first frame of the applet's package, where
 * a failure transcript and {@code -Djcx.log=true} show it next to the '6F00'.
 */
class AppletExceptionNoteTest {

    private static final AID AID_1 = AID.fromHex("F0000000010101");
    private static final String APPLET = ThrowingApplet.class.getName();

    /** The transcript from the command on, without the times of the exchanges. */
    private static String transcriptAfter(int ins) {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(ThrowingApplet.class, AID_1);
            card.send(0x80, ins);
            String transcript = withoutTimes(card.history().transcript());
            return transcript.substring(transcript.indexOf(String.format("C: 80%02X0000", ins)));
        }
    }

    @Test
    void anExceptionOfProcessIsNotedAfterThe6f00WithTheAppletsLine() {
        assertThat(transcriptAfter(0x01)).startsWith("C: 80010000\nR: 6F00\n# applet threw"
                + " java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 4 at " + APPLET
                + ".process(ThrowingApplet.java:");
    }

    @Test
    void theFrameIsTheFirstOneInTheAppletsPackage() {
        assertThat(transcriptAfter(0x02)).startsWith("C: 80020000\nR: 6F00\n# applet threw"
                + " java.lang.NullPointerException").contains(" at " + APPLET + ".fillCache(ThrowingApplet.java:");
        assertThat(transcriptAfter(0x04)).startsWith("C: 80040000\nR: 6F00\n# applet threw"
                + " java.lang.ArrayIndexOutOfBoundsException")
                .contains(" at " + APPLET + ".process(ThrowingApplet.java:");
    }

    @Test
    void anIsoExceptionIsAStatusWordAndNotNoted() {
        assertThat(transcriptAfter(0x03)).isEqualTo("C: 80030000\nR: 6A80\n");
        assertThat(transcriptAfter(0x05)).isEqualTo("C: 80050000\nR: 9000\n");
    }

    @Test
    void anExceptionOfTheInstallMethodIsNotedAndNamedInTheFailure() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            InstallException failure = catchThrowableOfType(InstallException.class,
                    () -> card.install(ParametersRequiredApplet.class, AID_1, new byte[]{0x00}));

            String frame = " at " + ParametersRequiredApplet.class.getName()
                    + ".install(ParametersRequiredApplet.java:";
            assertThat(card.history().transcript()).contains("# applet threw java.lang.NullPointerException")
                    .contains(frame);
            assertThat(failure).hasMessageContaining("it threw java.lang.NullPointerException")
                    .hasMessageContaining(frame).hasRootCauseInstanceOf(NullPointerException.class);
        }
    }
}
