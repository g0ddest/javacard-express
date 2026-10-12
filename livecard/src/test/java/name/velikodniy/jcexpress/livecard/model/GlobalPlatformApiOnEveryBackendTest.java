package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.livecard.model.GlobalPlatformApiScenarios.EncryptedCommandData;
import name.velikodniy.jcexpress.livecard.model.GlobalPlatformApiScenarios.RegistryOfTheSimulatedCard;
import name.velikodniy.jcexpress.livecard.model.GlobalPlatformApiScenarios.SecureChannelThroughTheApplet;
import name.velikodniy.jcexpress.livecard.model.GlobalPlatformApiScenarios.UnprotectedCommandInASession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An applet that uses the GlobalPlatform Card API ({@code org.globalplatform}) behaves the same on jCardSim and on
 * the simulated GlobalPlatform card: there the package is converted with the converter's built-in linking data,
 * loaded and installed through the Issuer Security Domain, and the secure channel the applet opens through
 * {@code GPSystem.getSecureChannel()} is the ISD's, so INITIALIZE UPDATE and EXTERNAL AUTHENTICATE sent to the applet
 * pass the APDU guard under its rules for authentications (the card cryptogram is verified independently before
 * EXTERNAL AUTHENTICATE is let through).
 */
class GlobalPlatformApiOnEveryBackendTest {

    @BeforeEach
    void clear() {
        BackendScenarios.SEEN.clear();
    }

    private static List<String> seen(Class<?> scenario, String backend) {
        assertThat(GpBackendParityTest.failures(scenario, backend, Map.of())).as("failures on " + backend).isEmpty();
        return List.copyOf(BackendScenarios.SEEN.getOrDefault(scenario.getSimpleName(), List.of()));
    }

    /** GPCS v2.3.1 7.3.1: the application relies on its Security Domain for the session and for unwrapping. */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void theHostOpensTheSecureChannelThroughTheApplet_gpcs231_7_3_1(String backend) {
        assertThat(seen(SecureChannelThroughTheApplet.class, backend)).containsExactly("state 07", "plain put 6982",
                "scp 3 level 1", "put 9000", "get 0A0B0C0D", "state 0F");
    }

    /** Amendment D 6.2.6: command data encrypted with S-ENC reach the applet in the clear. */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void commandDataEncryptedByTheHostAreDecryptedForTheApplet_amdD_6_2_6(String backend) {
        assertThat(seen(EncryptedCommandData.class, backend)).containsExactly("put 9000", "get 0A0B0C0D");
    }

    /**
     * Amendment D 5.6: a command whose security does not match the Current Security Level is rejected with a
     * security error and aborts the session, which stays aborted ('6985') until it is terminated; GPCS v2.3.1
     * 10.2.3: selecting the application again terminates it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void anUnprotectedCommandAbortsTheSessionUntilTheNextSelection_amdD_5_6(String backend) {
        assertThat(seen(UnprotectedCommandInASession.class, backend)).containsExactly("put 9000", "plain put 6982",
                "put after abort 6985", "plain put after select 6982", "value kept 0A0B0C0D");
    }

    /**
     * The registry of the simulated card is the one the applet changes through GPSystem (GPCS v2.3.1 5.3.1: an
     * application manages its application specific life cycle states), and the authentication the applet passed to
     * its Security Domain is counted there.
     */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void theTestSeesTheRegistryEntryAndTheAuthenticationsOfTheSimulatedCard_gpcs231_5_3_1(String backend) {
        assertThat(seen(RegistryOfTheSimulatedCard.class, backend)).containsExactly("before 07", "after 0F",
                "authentications 1 failed 0");
    }
}
