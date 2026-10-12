package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.EnabledOnBackend;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SecurityDomainKeys;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformCard;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.livecard.model.gpapi.VaultApplet;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.livecard.model.BackendScenarios.seen;

/**
 * Card tests of an applet that uses the GlobalPlatform Card API ({@code org.globalplatform}), run by
 * {@link GlobalPlatformApiOnEveryBackendTest} on jCardSim and on the simulated GlobalPlatform card, where every
 * command passes the APDU guard. They record what they saw in {@link BackendScenarios#SEEN}; the observations must
 * be the same on both backends.
 */
final class GlobalPlatformApiScenarios {

    private static final byte[] VALUE = {0x0A, 0x0B, 0x0C, 0x0D};

    private GlobalPlatformApiScenarios() {
    }

    /**
     * The applet accepts a value only in a secure channel session, which the host opens through the applet with the
     * keys of the applet's Security Domain (GPCS v2.3.1 7.3.1, Figure 7-2).
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(VaultApplet.class)
    static class SecureChannelThroughTheApplet {
        @Test
        void storesTheValueOnlyInASecureChannelSession(SmartCardSession card, SecurityDomainKeys keys) {
            String name = "SecureChannelThroughTheApplet";
            seen(name, "state " + card.send(0x80, 0x14, 0, 0, null, 1).dataAsHex());
            seen(name, String.format("plain put %04X", card.send(0x80, 0x10, 0, 0, VALUE).sw()));
            try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).securityLevel(GP.SECURITY_C_MAC)
                    .open()) {
                seen(name, "scp " + channel.cardInfo().scpVersion() + " level "
                        + channel.secureChannel().securityLevel());
                seen(name, String.format("put %04X", channel.send(0x80, 0x10, 0, 0, VALUE).sw()));
                seen(name, "get " + channel.send(0x80, 0x12, 0, 0, null, 256).dataAsHex());
                seen(name, "state " + channel.send(0x80, 0x14, 0, 0, null, 256).dataAsHex());
            }
        }
    }

    /** Command data encrypted by the host arrive in the clear at the applet (C-DECRYPTION, Amendment D 6.2.6). */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(VaultApplet.class)
    static class EncryptedCommandData {
        @Test
        void decryptsTheValue(SmartCardSession card, SecurityDomainKeys keys) {
            try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).securityLevel(GP.SECURITY_C_MAC_C_ENC)
                    .open()) {
                seen("EncryptedCommandData", String.format("put %04X", channel.send(0x80, 0x10, 0, 0, VALUE).sw()));
                seen("EncryptedCommandData", "get " + channel.send(0x80, 0x12, 0, 0, null, 256).dataAsHex());
            }
        }
    }

    /**
     * A command without protection in a session that requires it is rejected and aborts the session (Amendment D
     * 5.6); the session ends for good when the applet is selected again (GPCS v2.3.1 10.2.3).
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(VaultApplet.class)
    static class UnprotectedCommandInASession {
        @Test
        void abortsTheSession(SmartCardSession card, SecurityDomainKeys keys) {
            String name = "UnprotectedCommandInASession";
            try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).open()) {
                seen(name, String.format("put %04X", channel.send(0x80, 0x10, 0, 0, VALUE).sw()));
                seen(name, String.format("plain put %04X", card.send(0x80, 0x10, 0, 0, VALUE).sw()));
                seen(name, String.format("put after abort %04X", channel.send(0x80, 0x10, 0, 0, VALUE).sw()));
            }
            card.select(VaultApplet.class);
            seen(name, String.format("plain put after select %04X", card.send(0x80, 0x10, 0, 0, VALUE).sw()));
            seen(name, "value kept " + card.send(0x80, 0x12, 0, 0, null, 256).dataAsHex());
        }
    }

    /** The simulated cards show the test what the applet did to its registry entry. */
    @OnlyInTestKit
    @JavaCardTest
    @EnabledOnBackend({Mode.EMBEDDED, Mode.SIMULATED_GP})
    @InstallApplet(VaultApplet.class)
    static class RegistryOfTheSimulatedCard {
        @Test
        void showsTheStateTheAppletGaveItself(SmartCardSession card, SecurityDomainKeys keys, GlobalPlatformCard gp) {
            String name = "RegistryOfTheSimulatedCard";
            seen(name, String.format("before %02X", gp.application(card.aid(VaultApplet.class)).orElseThrow().state()));
            try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).open()) {
                channel.send(0x80, 0x10, 0, 0, VALUE).requireSuccess();
            }
            seen(name, String.format("after %02X", gp.application(card.aid(VaultApplet.class)).orElseThrow().state()));
            seen(name, "authentications " + gp.securityDomain().externalAuthentications() + " failed "
                    + gp.securityDomain().failedAuthentications());
        }
    }
}
