package name.velikodniy.jcexpress.embedded;

import javacard.framework.ISOException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.fakes.ParametersRequiredApplet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * An applet whose install method fails: jCardSim reports any failure as a bare {@code SystemException}; the session
 * names the applet, its instance AID, the install parameters and where the applet finds them (Java Card API
 * {@code Applet.install}: {@code [Li][instance AID][Lc][control info][La][parameters]}).
 */
class FailingInstallTest {

    private static final AID AID_1 = AID.fromHex("F0000000010101");

    @Test
    void anIsoExceptionOfTheInstallMethodIsReportedWithTheAppletItsAidAndItsParameters() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            InstallException failure = catchThrowableOfType(InstallException.class,
                    () -> card.install(ParametersRequiredApplet.class, AID_1));

            assertThat(failure).hasMessageContaining("Installing " + ParametersRequiredApplet.class.getName()
                            + " as F0000000010101 with install parameters (none) failed")
                    .hasMessageContaining("its install method threw ISOException with reason 6A80")
                    .hasMessageContaining("[Li][instance AID][Lc][control info][La][install parameters]")
                    .hasMessageContaining("@InstallApplet(params = ")
                    .hasCauseInstanceOf(ISOException.class);
            assertThat(failure.appletClass()).isEqualTo(ParametersRequiredApplet.class.getName());
            assertThat(failure.aid()).isEqualTo(AID_1);
            assertThat(failure.sw()).isZero();
            assertThat(card.history().transcript()).contains("install failed");
        }
    }

    @Test
    void anotherExceptionIsReportedAsOneThatJCardSimDoesNotPassOn() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            InstallException failure = catchThrowableOfType(InstallException.class,
                    () -> card.install(ParametersRequiredApplet.class, AID_1, new byte[]{0x00}));

            assertThat(failure).hasMessageContaining("with install parameters 00 failed")
                    .hasMessageContaining("without an ISOException")
                    .hasMessageContaining("register()");
        }
    }

    @Test
    void theAidOfAFailedInstallCanBeInstalledAgain() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            catchThrowableOfType(InstallException.class, () -> card.install(ParametersRequiredApplet.class, AID_1));

            card.install(ParametersRequiredApplet.class, AID_1, new byte[]{0x01});

            assertThat(card.send(0x80, 0x10).sw()).isEqualTo(0x6D00);
        }
    }
}
