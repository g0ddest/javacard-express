package com.example.vault;

import name.velikodniy.jcexpress.EnabledOnBackend;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SW;
import name.velikodniy.jcexpress.SecurityDomainKeys;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformCard;
import name.velikodniy.jcexpress.embedded.gp.SecurityDomain;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/** The recipe "An applet that uses the GlobalPlatform API" of TESTING.md, in a project of its own. */
@JavaCardTest
@InstallApplet(VaultApplet.class)
class VaultAppletTest {

    static final byte[] VALUE = {0x0A, 0x0B, 0x0C, 0x0D};

    @Test
    void refusesTheValueOutsideASecureChannelSession(SmartCardSession card) {
        assertThat(card.send(0x80, 0x10, 0, 0, VALUE)).hasStatusWord(SW.SECURITY_STATUS_NOT_SATISFIED);
    }

    @Test
    void storesTheValueInASecureChannelSession(SmartCardSession card, SecurityDomainKeys keys) {
        try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).open()) {    // through the selected applet
            assertThat(channel.send(0x80, 0x10, 0, 0, VALUE)).isSuccess();
            assertThat(channel.send(0x80, 0x12, 0, 0, null, 256)).isSuccess().hasDataHex("0A 0B 0C 0D");
            assertThat(channel.send(0x80, 0x14, 0, 0, null, 256)).isSuccess().hasDataHex("0F");
        }
    }

    @Test
    @EnabledOnBackend(Mode.EMBEDDED)
    void protectsItsResponsesWhenTheHostAsksForIt(SmartCardSession card, SecurityDomainKeys keys,
                                                  GlobalPlatformCard globalPlatform) {
        globalPlatform.securityDomain().scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);

        try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys))
                .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC).open()) {
            assertThat(channel.send(0x80, 0x10, 0, 0, VALUE)).isSuccess();
            assertThat(channel.send(0x80, 0x12, 0, 0, null, 256)).isSuccess().hasDataHex("0A 0B 0C 0D");
        }
    }
}
