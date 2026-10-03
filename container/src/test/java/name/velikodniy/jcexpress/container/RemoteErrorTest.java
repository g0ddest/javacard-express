package name.velikodniy.jcexpress.container;

import javacard.framework.ISOException;
import javacard.framework.SystemException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decoding of error replies and their conversion into the exceptions a {@link ContainerSession} throws (audit
 * finding "unhelpful-error-propagation").
 */
class RemoteErrorTest {

    @Test
    void parsesEveryKeyOfTheReport() {
        RemoteError error = parse("""
                type: javacard.framework.SystemException
                reason: 0x6444
                message: install failed
                cause: java.lang.NoClassDefFoundError: a/B
                cause: java.lang.ClassNotFoundException: a.B
                missing-class: a.B
                future-key: ignored
                """);

        assertThat(error.type()).isEqualTo("javacard.framework.SystemException");
        assertThat(error.reason()).isEqualTo(0x6444);
        assertThat(error.message()).isEqualTo("install failed");
        assertThat(error.causes()).containsExactly(
                "java.lang.NoClassDefFoundError: a/B", "java.lang.ClassNotFoundException: a.B");
        assertThat(error.missingClasses()).containsExactly("a.B");
    }

    @Test
    void plainTextOfOlderServersBecomesTheMessage() {
        RemoteError error = parse("Unknown error");

        assertThat(error.type()).isNull();
        assertThat(error.message()).isEqualTo("Unknown error");
        assertThat(error.toException("install applet X"))
                .isExactlyInstanceOf(SimulatorException.class)
                .hasMessage("Failed to install applet X: error: Unknown error");
    }

    @Test
    void javaCardExceptionsAreRethrownWithTheirReasonAndTheRemoteDescriptionAsCause() {
        RuntimeException thrown = parse("type: javacard.framework.ISOException\nreason: 0x6A80\n")
                .toException("transmit APDU");

        assertThat(thrown).isInstanceOf(ISOException.class);
        assertThat(((ISOException) thrown).getReason()).isEqualTo((short) 0x6A80);
        assertThat(thrown.getCause())
                .isInstanceOf(SimulatorException.class)
                .hasMessage("Failed to transmit APDU: javacard.framework.ISOException (reason 0x6A80)");
        assertThat(((SimulatorException) thrown.getCause()).remoteType()).isEqualTo("javacard.framework.ISOException");
    }

    @Test
    void javaRuntimeExceptionsKeepTheirTypeAndMessage() {
        RuntimeException thrown = parse("type: java.lang.IllegalStateException\nmessage: busy\n")
                .toException("install applet X");

        assertThat(thrown).isExactlyInstanceOf(IllegalStateException.class).hasMessage("busy");
    }

    @Test
    void otherFailuresBecomeSimulatorExceptionsNamingTheRemoteTypeCausesAndMissingClasses() {
        RuntimeException thrown = parse("""
                type: java.lang.NoClassDefFoundError
                message: a/B
                cause: java.lang.ClassNotFoundException: a.B
                missing-class: a.B
                """).toException("install applet a.A");

        assertThat(thrown).isExactlyInstanceOf(SimulatorException.class)
                .hasMessage("Failed to install applet a.A: java.lang.NoClassDefFoundError: a/B"
                        + " <- caused by java.lang.ClassNotFoundException: a.B"
                        + " [the applet needed classes that were not sent to the simulator: a.B]");
        assertThat(((SimulatorException) thrown).remoteType()).isEqualTo("java.lang.NoClassDefFoundError");
    }

    @Test
    void reasonWithoutCardRuntimeExceptionTypeIsNotTurnedIntoOne() {
        RuntimeException thrown = parse("type: com.example.Odd\nreason: 0x0001\n").toException("select");

        assertThat(thrown).isExactlyInstanceOf(SimulatorException.class);
        assertThat(thrown).isNotInstanceOf(SystemException.class);
    }

    private static RemoteError parse(String text) {
        return RemoteError.parse(text.getBytes(StandardCharsets.UTF_8));
    }
}
