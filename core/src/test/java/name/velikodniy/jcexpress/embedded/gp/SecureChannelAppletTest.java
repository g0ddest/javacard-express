package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.gp.SecureChannelApplet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An applet on the embedded card opens and uses the secure channel of its Security Domain through
 * {@code org.globalplatform.GPSystem.getSecureChannel()}, the ways published applets do (GlobalPlatform Card
 * Specification v2.3.1 section 7.3.1, Figure 7-2; GlobalPlatform Card API 1.8, interface SecureChannel; SCP03 of
 * Amendment D). The off-card side is {@link Scp03Host}.
 */
class SecureChannelAppletTest {

    private static final AID APPLET = AID.fromHex("F0000000010101");
    private static final AID SECOND = AID.fromHex("F0000000010102");
    private static final byte[] ECHO = Hex.decode("8010000004CAFEBABE");
    private static final byte[] LE = {0x00};

    private final EmbeddedSession card = new EmbeddedSession();
    private final Scp03Host host = Scp03Host.withTestKeys();

    @AfterEach
    void closeCard() {
        card.close();
    }

    /** Installs the applet in a mode (see {@link SecureChannelApplet}) and selects it. */
    private void install(int mode) {
        card.install(SecureChannelApplet.class, APPLET, new byte[]{(byte) mode});
    }

    /** INITIALIZE UPDATE and EXTERNAL AUTHENTICATE with the selected applet (Amendment D Figure 5-1). */
    private void open(int level) {
        APDUResponse update = new APDUResponse(card.transmit(Scp03Host.concat(host.initializeUpdate(0), LE)));
        assertThat(update).isSuccess();
        assertThat(host.receive(update.data())).as("card cryptogram").isTrue();
        assertThat(new APDUResponse(card.transmit(host.externalAuthenticate(level)))).isSuccess();
    }

    /** Sends a command through the secure channel and removes the protection of the response. */
    private APDUResponse secure(byte[] command) {
        return new APDUResponse(host.unwrap(card.transmit(Scp03Host.concat(host.wrap(command), LE))));
    }

    private int level() {
        return card.send(0x80, 0x12).requireSuccess().u8(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void theAppletPassesTheInitiationToItsSecurityDomainInEveryCallingStyle_gpapi_SecureChannel_processSecurity(
            int mode) {
        install(mode);

        open(0x03);

        assertThat(level()).isEqualTo(0x83);
        assertThat(secure(ECHO)).isSuccess().dataEquals(0xCA, 0xFE, 0xBA, 0xBE);
        assertThat(secure(Hex.decode("80140000"))).as("a protected command of an authenticated session").isSuccess()
                .dataEquals('O', 'K');
        assertThat(card.globalPlatform().securityDomain().externalAuthentications()).isEqualTo(1);
    }

    @Test
    void withoutASessionTheAppletSeesNoSecurityLevel_amdD_5_5() {
        install(0);

        assertThat(level()).isZero();
        assertThat(card.send(0x80, 0x14)).as("the applet's own rule").hasStatusWord(0x6982);
        assertThat(card.transmit(ECHO)).as("an unprotected command needs no session").endsWith(Hex.decode("9000"));
    }

    @Test
    void theInitializeUpdateResponseIsTheOneOfTheSecurityDomain_amdD_Table_7_3() {
        install(0);
        card.globalPlatform().securityDomain().keyVersion(0x30).keyDiversificationData(Hex.decode("0102030405060708090A"));

        APDUResponse update = new APDUResponse(card.transmit(Scp03Host.concat(host.initializeUpdate(0x30), LE)));

        assertThat(update).isSuccess();
        assertThat(update.data()).hasSize(29);
        assertThat(Hex.encode(Arrays.copyOf(update.data(), 13))).isEqualTo("0102030405060708090A" + "300300");
        assertThat(card.transmit(Scp03Host.concat(host.initializeUpdate(0x31), LE))).as("unknown key version")
                .isEqualTo(Hex.decode("6A88"));
    }

    @Test
    void unwrapReturnsHeaderAndPlainDataAndRewritesLcAndClassByte_gpapi_SecureChannel_unwrap() {
        install(0);
        open(0x03);

        APDUResponse info = secure(Hex.decode("801A000004CAFEBABE"));

        assertThat(info).isSuccess();
        assertThat(info.u16(0)).as("header and data field").isEqualTo(9);
        assertThat(info.u8(2)).as("class byte without secure messaging").isEqualTo(0x80);
        assertThat(info.u8(3)).as("Lc").isEqualTo(4);
    }

    @Test
    void responsesAreProtectedWhenTheSecurityDomainAllowsAndTheHostAsks_amdD_6_2_5_and_6_2_7() {
        install(0);
        card.globalPlatform().securityDomain().scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);

        open(0x33);

        assertThat(host.option()).isEqualTo(0x60);
        assertThat(level()).isEqualTo(0xB3);
        byte[] raw = card.transmit(Scp03Host.concat(host.wrap(ECHO), LE));
        assertThat(raw).as("16 bytes of ciphertext, the R-MAC, the status word").hasSize(26);
        assertThat(new APDUResponse(host.unwrap(raw))).isSuccess().dataEquals(0xCA, 0xFE, 0xBA, 0xBE);
        assertThat(secure(Hex.decode("80206310"))).as("a warning is protected").hasStatusWord(0x6310).dataEquals(0xCA, 0xFE);
        assertThat(card.transmit(Scp03Host.concat(host.wrap(Hex.decode("80206A80")), LE))).as("an error is not")
                .isEqualTo(Hex.decode("6A80"));
    }

    @Test
    void aHostThatAsksForResponseProtectionTheDomainDoesNotOfferIsRefused_amdD_Table_5_1() {
        install(0);
        host.receive(new APDUResponse(card.transmit(Scp03Host.concat(host.initializeUpdate(0), LE))).data());

        assertThat(card.transmit(host.externalAuthenticate(0x33))).isEqualTo(Hex.decode("6A86"));
        assertThat(card.globalPlatform().securityDomain().failedAuthentications()).isEqualTo(1);
        assertThat(level()).isZero();
    }

    @Test
    void sensitiveDataAreDecryptedWithTheKeyDekOfTheDomain_amdD_6_2_8() {
        install(0);
        open(0x01);
        byte[] secret = Hex.decode("00112233445566778899AABBCCDDEEFF");
        byte[] encrypted = CryptoUtil.aesCbcEncrypt(card.globalPlatform().securityDomain().keyDek(), secret);

        assertThat(secure(Scp03Host.concat(Hex.decode("8016000010"), encrypted))).isSuccess().hasData(secret);
        assertThat(secure(Scp03Host.concat(Hex.decode("8018000010"), secret))).isSuccess().hasData(encrypted);
        assertThat(secure(Hex.decode("8016000003010203"))).as("not a block multiple").hasStatusWord(0x6700);
    }

    @Test
    void aWrongMacAbortsTheSessionUntilANewOneIsOpened_gpcs_10_2_3() {
        install(0);
        open(0x01);
        byte[] tampered = host.wrap(ECHO);
        tampered[tampered.length - 1] ^= 0x01;

        assertThat(card.transmit(tampered)).isEqualTo(Hex.decode("6982"));
        assertThat(level()).isZero();
        assertThat(card.transmit(host.wrap(ECHO))).as("aborted, not terminated").isEqualTo(Hex.decode("6985"));

        open(0x01);

        assertThat(secure(ECHO)).isSuccess().dataEquals(0xCA, 0xFE, 0xBA, 0xBE);
    }

    @Test
    void theAppletChangesTheCurrentLevelAndEndsTheSession_gpapi_SecureChannelx_and_resetSecurity() {
        install(0);
        card.globalPlatform().securityDomain().scp03Option(SecurityDomain.OPTION_R_MAC);
        open(0x01);

        assertThat(card.send(0x80, 0x1C, 0x91, 0x00)).isSuccess();
        assertThat(level()).isEqualTo(0x91);
        assertThat(card.send(0x80, 0x1C, 0x80, 0x00)).as("below the session level").hasStatusWord(0x6985);
        assertThat(card.send(0x80, 0x1E)).isSuccess();
        assertThat(level()).isZero();
        assertThat(card.globalPlatform().securityDomain().securityLevel()).isZero();
    }

    @Test
    void selectingAnotherApplicationEndsTheSession_gpcs_10_2_3() {
        card.install(SecureChannelApplet.class, SECOND, new byte[]{0, 1});
        card.install(SecureChannelApplet.class, APPLET, new byte[]{0, 1});
        open(0x01);
        assertThat(card.globalPlatform().securityDomain().securityLevel()).isEqualTo(0x81);

        card.select(SECOND);

        assertThat(card.globalPlatform().securityDomain().securityLevel()).as("the applets do not reset it").isZero();
        assertThat(level()).isZero();
    }

    @Test
    void selectingTheSameApplicationAgainDeselectingAndACardResetEndTheSession_gpcs_10_2_3() {
        card.install(SecureChannelApplet.class, APPLET, new byte[]{0, 1});
        SecurityDomain domain = card.globalPlatform().securityDomain();

        open(0x01);
        card.select(APPLET);
        assertThat(domain.securityLevel()).as("after re-selection").isZero();

        open(0x01);
        card.deselect();
        assertThat(domain.securityLevel()).as("after deselection").isZero();

        card.select(APPLET);
        open(0x01);
        card.reset();
        assertThat(domain.securityLevel()).as("after a card reset").isZero();
    }

    @Test
    void anAppletThatResetsTheChannelInDeselectDoesSoAsTheOwner_gpapi_SecureChannel_resetSecurity() {
        install(0);
        card.install(SecureChannelApplet.class, SECOND, new byte[]{0});
        card.select(APPLET);
        open(0x01);

        card.select(SECOND);

        assertThat(card.history().transcript()).doesNotContain("applet threw");
        assertThat(level()).isZero();
    }

    @Test
    void deletingTheApplicationEndsItsSession() {
        card.install(SecureChannelApplet.class, APPLET, new byte[]{0, 1});
        open(0x01);

        card.delete(APPLET);

        assertThat(card.globalPlatform().securityDomain().securityLevel()).isZero();
        assertThat(card.globalPlatform().application(APPLET)).isEmpty();
    }

    @Test
    void everyCardHasItsOwnSecurityDomain() {
        install(0);
        try (EmbeddedSession other = new EmbeddedSession()) {
            other.install(SecureChannelApplet.class, APPLET, new byte[]{0});
            other.globalPlatform().securityDomain().keyVersion(0x20);

            open(0x01);

            assertThat(card.globalPlatform().securityDomain().securityLevel()).isEqualTo(0x81);
            assertThat(other.globalPlatform().securityDomain().securityLevel()).isZero();
            assertThat(other.globalPlatform().securityDomain().externalAuthentications()).isZero();
            assertThat(card.globalPlatform().securityDomain().keyVersion()).isEqualTo(0xFF);
            assertThat(other.send(0x80, 0x12).requireSuccess().u8(0)).isZero();
        }
    }
}
