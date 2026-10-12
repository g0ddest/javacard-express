package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SecurityDomainKeys;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.embedded.gp.SecurityDomain;
import name.velikodniy.jcexpress.gp.cookbook.VaultApplet;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.SCPException;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The host side of SCP03 ({@link GPSession}, written from Amendment D for the off-card entity) against the card
 * side behind {@code org.globalplatform.GPSystem.getSecureChannel()} on the simulated card (written from Amendment D
 * for the Security Domain, in the core module): an applet passes INITIALIZE UPDATE and EXTERNAL AUTHENTICATE to its
 * Security Domain and has it check commands and protect responses (GPCS v2.3.1 7.3.1, Figure 7-2). The two
 * implementations share only the block cipher primitives, so every protection they exchange is checked by code
 * written for the other end.
 */
class GPSessionThroughAppletTest {

    private static final AID VAULT = AID.fromHex("F0000000010201");
    private static final byte[] VALUE = {0x0A, 0x0B, 0x0C, 0x0D, 0x0E};

    private EmbeddedSession card;

    @BeforeEach
    void installTheApplet() {
        card = new EmbeddedSession();
        card.install(VaultApplet.class, VAULT);
    }

    @AfterEach
    void close() {
        card.close();
    }

    private GPSession channel(int level) {
        return GPSession.on(card).keys(SCPKeys.defaultKeys()).securityLevel(level).open();
    }

    /** Amendment D 7.1.1, 7.1.2, 6.2.4, 6.2.6: every level without response protection on a card with i = '00'. */
    @ParameterizedTest(name = "level {0}")
    @CsvSource({"0", "1", "3"})
    void opensTheChannelThroughTheSelectedAppletAndSendsProtectedCommands_amdD_6_2_4(int level) {
        try (GPSession channel = channel(level)) {
            assertThat(channel.cardInfo().scpVersion()).isEqualTo(3);
            assertThat(channel.secureChannel().securityLevel()).isEqualTo(level);
            APDUResponse put = level == 0 ? card.send(0x80, 0x10, 0, 0, VALUE) : channel.send(0x80, 0x10, 0, 0, VALUE);
            assertThat(put.sw()).isEqualTo(0x9000);
            APDUResponse get = level == 0 ? card.send(0x80, 0x12, 0, 0, null, 256)
                    : channel.send(0x80, 0x12, 0, 0, null, 256);
            assertThat(get.data()).isEqualTo(VALUE);
        }
        SecurityDomain domain = card.globalPlatform().securityDomain();
        assertThat(domain.externalAuthentications()).isEqualTo(1);
        assertThat(domain.failedAuthentications()).isZero();
    }

    /**
     * Amendment D 6.2.5, 6.2.7: with i = '60' the Security Domain adds the R-MAC, and encrypts the response data
     * first at level '33'; the host verifies and decrypts what {@code SecureChannel.wrap} produced.
     */
    @ParameterizedTest(name = "level {0}")
    @CsvSource({"17", "19", "51"})
    void protectsResponsesWhenTheSecurityDomainSupportsIt_amdD_6_2_5(int level) {
        card.globalPlatform().securityDomain().scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);

        try (GPSession channel = channel(level)) {
            assertThat(channel.secureChannel().securityLevel()).isEqualTo(level);
            assertThat(channel.send(0x80, 0x10, 0, 0, VALUE).sw()).isEqualTo(0x9000);
            assertThat(channel.send(0x80, 0x12, 0, 0, null, 256).data()).isEqualTo(VALUE);
            assertThat(channel.send(0x80, 0x14, 0, 0, null, 256).data()).containsExactly(0x0F);
        }
    }

    /** Amendment D Table 5-1: a level with R-MAC is not offered by a Security Domain with i = '00'. */
    @Test
    void refusesResponseProtectionTheSecurityDomainDoesNotSupport_amdD_table_5_1() {
        assertThatThrownBy(() -> channel(GP.SECURITY_C_MAC_R_MAC)).isInstanceOf(SCPException.class)
                .hasMessageContaining("i=00");

        assertThat(card.globalPlatform().securityDomain().externalAuthentications())
                .as("EXTERNAL AUTHENTICATE is not sent").isZero();
    }

    /** Amendment D 6.2.2.1: a pseudo-random card challenge comes with the sequence counter of the key set. */
    @Test
    void acceptsAPseudoRandomCardChallenge_amdD_6_2_2_1() {
        SecurityDomain domain = card.globalPlatform().securityDomain();
        domain.scp03Option(SecurityDomain.OPTION_PSEUDO_RANDOM_CHALLENGE | SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);

        try (GPSession channel = channel(GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC)) {
            assertThat(channel.send(0x80, 0x10, 0, 0, VALUE).sw()).isEqualTo(0x9000);
            assertThat(channel.send(0x80, 0x12, 0, 0, null, 256).data()).isEqualTo(VALUE);
        }
        assertThat(domain.sequenceCounter()).isEqualTo(1);
    }

    /**
     * Amendment D 6.2.2.2: the host verifies the card cryptogram first; with other keys than the Security Domain's
     * it does not send EXTERNAL AUTHENTICATE, so the card counts no failed authentication.
     */
    @Test
    void wrongKeysEndBeforeExternalAuthenticate_amdD_6_2_2_2() {
        SCPKeys wrong = SCPKeys.fromMasterKey(new byte[16]);

        assertThatThrownBy(() -> GPSession.on(card).keys(wrong).open()).isInstanceOf(SCPException.class)
                .hasMessageContaining("Card cryptogram verification failed");

        SecurityDomain domain = card.globalPlatform().securityDomain();
        assertThat(domain.externalAuthentications()).isZero();
        assertThat(domain.failedAuthentications()).isZero();
    }

    /** The keys a test of a JavaCardTest class gets as a parameter open the channel. */
    @Test
    void opensWithTheKeysOfTheRunsSecurityDomain() {
        SecurityDomain domain = card.globalPlatform().securityDomain();
        SecurityDomainKeys keys = new SecurityDomainKeys(domain.keyEnc(), domain.keyMac(), domain.keyDek());

        try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).open()) {
            assertThat(channel.send(0x80, 0x10, 0, 0, VALUE).sw()).isEqualTo(0x9000);
        }
        assertThat(keys).hasToString("SecurityDomainKeys[128-bit keys]");
    }

    /** GPCS v2.3.1 10.2.3: selecting the application again terminates the session; a new one can be opened. */
    @Test
    void aNewSessionFollowsTheSelectionOfTheApplet_gpcs231_10_2_3() {
        try (GPSession first = channel(GP.SECURITY_C_MAC)) {
            assertThat(first.send(0x80, 0x10, 0, 0, VALUE).sw()).isEqualTo(0x9000);
        }
        card.select(VAULT);
        assertThat(card.send(0x80, 0x10, 0, 0, VALUE).sw()).as("no session, so not authenticated").isEqualTo(0x6982);

        try (GPSession second = channel(GP.SECURITY_C_MAC_C_ENC)) {
            assertThat(second.send(0x80, 0x12, 0, 0, null, 256).data()).isEqualTo(VALUE);
        }
    }
}
