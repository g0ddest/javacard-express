package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.gp.CardStateApplet;
import org.globalplatform.GPRegistryEntry;
import org.globalplatform.GPSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What an applet on the embedded card gets from the state, registry and service methods of
 * {@code org.globalplatform.GPSystem} (GlobalPlatform Card API 1.8, class GPSystem and interface GPRegistryEntry;
 * life cycle rules of GlobalPlatform Card Specification v2.3.1 chapter 5, coding of 11.1.1 and 11.1.2).
 */
class CardStateAppletTest {

    private static final AID APPLET = AID.fromHex("F0000000020101");
    private static final AID OTHER = AID.fromHex("F0000000020102");

    private final EmbeddedSession card = new EmbeddedSession();
    private GlobalPlatformCard gp;
    private CardApplication applet;

    @BeforeEach
    void installTwoApplets() {
        card.install(CardStateApplet.class, OTHER);
        card.install(CardStateApplet.class, APPLET);
        gp = card.globalPlatform();
        applet = gp.application(APPLET).orElseThrow();
    }

    @AfterEach
    void closeCard() {
        card.close();
    }

    private int answer(int ins, int p1) {
        return card.send(0x80, ins, p1, 0x00).requireSuccess().u8(0);
    }

    @Test
    void anApplicationIsInstalledWhileItsInstallMethodRunsAndSelectableAfterwards_gpcs_5_3_1() {
        APDUResponse seenAtInstall = card.send(0x80, 0x4E).requireSuccess();

        assertThat(seenAtInstall.u8(0)).as("getCardState() in the constructor").isEqualTo(GlobalPlatformCard.INITIALIZED);
        assertThat(seenAtInstall.u8(1)).as("getCardContentState() after register()").isEqualTo(0x03);
        assertThat(answer(0x30, 0)).as("getCardContentState() in process").isEqualTo(0x07);
        assertThat(gp.applications()).extracting(CardApplication::aid).containsExactly(OTHER, APPLET);
        assertThat(applet.state()).isEqualTo(CardApplication.SELECTABLE);
    }

    @ParameterizedTest
    @CsvSource({"07,0F,0F", "0F,7F,7F", "7F,07,07", "07,17,17", "0F,80,8F", "07,FF,87"})
    void anApplicationMovesBetweenApplicationSpecificStatesOrLocksItself_gpapi_GPSystem_setCardContentState(
            String from, String requested, String expected) {
        applet.state(Integer.parseInt(from, 16));

        assertThat(answer(0x32, Integer.parseInt(requested, 16))).isEqualTo(1);
        assertThat(applet.state()).isEqualTo(Integer.parseInt(expected, 16));
    }

    @ParameterizedTest
    @CsvSource({"07,03", "07,00", "07,0B", "07,05", "03,07", "03,0F", "87,07", "87,80", "8F,0F"})
    void otherTransitionsAreRejected_gpapi_GPSystem_setCardContentState(String from, String requested) {
        applet.state(Integer.parseInt(from, 16));

        assertThat(answer(0x32, Integer.parseInt(requested, 16))).isZero();
        assertThat(applet.state()).isEqualTo(Integer.parseInt(from, 16));
    }

    @Test
    void theApplicationKeepsItsStateAcrossSelectionsAndResets() {
        answer(0x32, 0x1F);

        card.select(OTHER);
        card.reset();
        card.select(APPLET);

        assertThat(answer(0x30, 0)).isEqualTo(0x1F);
        assertThat(gp.application(OTHER).orElseThrow().state()).as("the other application").isEqualTo(0x07);
    }

    @Test
    void theCardStateIsWhatTheCardIsIn_gpcs_Table_11_6() {
        assertThat(answer(0x34, 0)).isEqualTo(0x07);

        gp.cardState(GPSystem.CARD_SECURED);

        assertThat(answer(0x34, 0)).isEqualTo(0x0F);
        assertThatThrownBy(() -> gp.cardState(0x03)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockingTheCardNeedsTheCardLockPrivilegeAndASecuredCard_gpapi_GPSystem_lockCard_gpcs_5_1_1_4() {
        gp.cardState(GlobalPlatformCard.SECURED);
        assertThat(answer(0x36, 0)).as("without the privilege").isZero();

        applet.grant(GPRegistryEntry.PRIVILEGE_CARD_LOCK);
        gp.cardState(GlobalPlatformCard.INITIALIZED);
        assertThat(answer(0x36, 0)).as("not from INITIALIZED").isZero();

        gp.cardState(GlobalPlatformCard.SECURED);
        assertThat(answer(0x36, 0)).isEqualTo(1);
        assertThat(gp.cardState()).isEqualTo(GlobalPlatformCard.CARD_LOCKED);
        assertThat(answer(0x34, 0)).isEqualTo(0x7F);
    }

    @Test
    void terminatingTheCardNeedsTheCardTerminatePrivilege_gpapi_GPSystem_terminateCard_gpcs_5_1_1_5() {
        assertThat(answer(0x38, 0)).isZero();
        assertThat(gp.cardState()).isEqualTo(GlobalPlatformCard.INITIALIZED);

        applet.grant(GPRegistryEntry.PRIVILEGE_CARD_TERMINATE);

        assertThat(answer(0x38, 0)).isEqualTo(1);
        assertThat(gp.cardState()).isEqualTo(GlobalPlatformCard.TERMINATED);
    }

    @Test
    void historicalBytesNeedTheCardResetPrivilegeAndShowAfterTheNextReset_gpapi_GPSystem_setATRHistBytes() {
        byte[] bytes = Hex.decode("4A435831");
        assertThat(card.send(0x80, 0x3A, 0, 0, bytes).requireSuccess().u8(0)).as("without the privilege").isZero();

        applet.grant(GPRegistryEntry.PRIVILEGE_CARD_RESET);
        assertThat(card.send(0x80, 0x3A, 0, 0, new byte[16]).requireSuccess().u8(0)).as("16 bytes").isZero();
        assertThat(card.send(0x80, 0x3A, 0, 0, new byte[0]).requireSuccess().u8(0)).as("no bytes").isZero();
        assertThat(card.send(0x80, 0x3A, 0, 0, bytes).requireSuccess().u8(0)).isEqualTo(1);
        assertThat(gp.historicalBytes()).as("the ATR sent last is unchanged").isEmpty();
        assertThat(gp.historicalBytesAfterReset()).hasValue(bytes);

        card.reset();

        assertThat(gp.historicalBytes()).isEqualTo(bytes);
        assertThat(gp.historicalBytesAfterReset()).isEmpty();
    }

    @Test
    void cardInformationIsTheHistoricalBytesOrADataObjectOfTheIsd_gpapi_GPSystem_getCardInfo() {
        assertThat(card.send(0x80, 0x3C, 0x00, 0x80)).as("no historical bytes on this card").hasStatusWord(0x6A88);

        gp.historicalBytes(Hex.decode("80318065B0")).dataObject(0x66, Hex.decode("7300")).dataObject(0x9F7F, new byte[42]);

        assertThat(card.send(0x80, 0x3C, 0x00, 0x80)).isSuccess().hasDataHex("80318065B0");
        assertThat(card.send(0x80, 0x3E, 0x00, 0x80).requireSuccess().u16(0)).isEqualTo(5);
        assertThat(card.send(0x80, 0x3C, 0x00, 0x66)).isSuccess().hasDataHex("7300");
        assertThat(card.send(0x80, 0x3E, 0x9F, 0x7F).requireSuccess().u16(0)).isEqualTo(42);
        assertThat(card.send(0x80, 0x3C, 0x00, 0x81)).as("no contactless interface").hasStatusWord(0x6A88);
        assertThat(card.send(0x80, 0x3C, 0x00, 0x45)).as("no such data object").hasStatusWord(0x6A88);
        assertThat(card.send(0x80, 0x3C, 0x00, 0x1F)).as("not a BER-TLV tag").hasStatusWord(0x6A80);
        assertThat(card.send(0x80, 0x3C, 0x9F, 0x00)).as("not a BER-TLV tag").hasStatusWord(0x6A80);
    }

    @Test
    void anApplicationGetsItsOwnEntryAndAnothersOnlyWithGlobalRegistry_gpapi_GPSystem_getRegistryEntry() {
        applet.privileges(new byte[]{0x10, 0x04, 0x00});

        APDUResponse own = card.send(0x80, 0x40).requireSuccess();
        assertThat(own.data()).isEqualTo(Hex.decode("01" + "07" + "100400" + "01" + APPLET.toHex()));
        assertThat(card.send(0x80, 0x40, 0, 0, APPLET.toBytes()).requireSuccess().data()).isEqualTo(own.data());
        assertThat(card.send(0x80, 0x40, 0, 0, Hex.decode("F0000000029999")).requireSuccess().u8(0))
                .as("no such application").isZero();
        assertThat(card.send(0x80, 0x40, 0, 0, OTHER.toBytes()).requireSuccess().data())
                .as("with the Global Registry privilege").isEqualTo(Hex.decode("01" + "07" + "000000" + "01" + OTHER.toHex()));

        applet.revoke(GPRegistryEntry.PRIVILEGE_GLOBAL_REGISTRY);

        assertThat(card.send(0x80, 0x40, 0, 0, OTHER.toBytes()).requireSuccess().u8(0)).isZero();
    }

    @Test
    void lockingThroughAnEntryIsForTheApplicationItselfOrGlobalLockUnlockingForGlobalLock_gpapi_GPRegistryEntry_setState() {
        applet.grant(GPRegistryEntry.PRIVILEGE_GLOBAL_REGISTRY);
        CardApplication other = gp.application(OTHER).orElseThrow();

        assertThat(card.send(0x80, 0x44, 0x80, 0, OTHER.toBytes()).requireSuccess().u8(0)).as("no Global Lock").isZero();
        applet.grant(GPRegistryEntry.PRIVILEGE_GLOBAL_LOCK);
        assertThat(card.send(0x80, 0x44, 0x80, 0, OTHER.toBytes()).requireSuccess().u8(0)).isEqualTo(1);
        assertThat(other.state()).isEqualTo(0x87);
        assertThat(card.send(0x80, 0x44, 0x80, 0, OTHER.toBytes()).requireSuccess().u8(0)).as("already locked").isZero();
        assertThat(card.send(0x80, 0x44, 0x0F, 0, OTHER.toBytes()).requireSuccess().u8(0)).as("b8 clear: unlock").isEqualTo(1);
        assertThat(other.state()).as("the state before").isEqualTo(0x07);
        assertThat(card.send(0x80, 0x44, 0x0F, 0, OTHER.toBytes()).requireSuccess().u8(0))
                .as("only the application itself moves between its states").isZero();

        assertThat(answer(0x44, 0x1F)).as("its own application specific state").isEqualTo(1);
        assertThat(answer(0x44, 0x80)).as("locks itself").isEqualTo(1);
        assertThat(applet.state()).isEqualTo(0x9F);
        assertThat(applet.locked()).isTrue();
        assertThat(applet.selectable()).isFalse();
    }

    @Test
    void installedToSelectableIsNotMadeThroughAnEntry_gpapi_GPRegistryEntry_setState() {
        applet.state(CardApplication.INSTALLED);

        assertThat(answer(0x44, 0x07)).isZero();
        assertThat(applet.state()).isEqualTo(0x03);
    }

    @Test
    void aGlobalServicesApplicationRegistersItsServiceAndOthersFindIt_gpapi_GPSystem_getService_and_getCVM() {
        assertThat(card.send(0x80, 0x46, 0x82, 0x11)).as("without the Global Service privilege").hasStatusWord(0x6982);
        assertThat(answer(0x4C, 0x11)).as("no CVM on this card").isZero();
        applet.grant(GPRegistryEntry.PRIVILEGE_GLOBAL_SERVICE);

        assertThat(card.send(0x80, 0x46, 0x82, 0x11)).isSuccess();

        assertThat(card.send(0x80, 0x4A, 0x82, 0x11).requireSuccess().u8(0)).as("by name").isEqualTo(1);
        assertThat(card.send(0x80, 0x4A, 0x82, 0x00).requireSuccess().u8(0)).as("by family").isEqualTo(1);
        assertThat(card.send(0x80, 0x4A, 0x82, 0x12).requireSuccess().u8(0)).as("another service").isZero();
        assertThat(card.send(0x80, 0x4A, 0x82, 0x11, APPLET.toBytes()).requireSuccess().u8(0)).as("by AID").isEqualTo(1);
        assertThat(card.send(0x80, 0x4A, 0x82, 0x11, OTHER.toBytes()).requireSuccess().u8(0))
                .as("an application without the Global Service privilege").isZero();
        assertThat(card.send(0x80, 0x4C, 0x11, 0x00).requireSuccess().data()).as("getCVM(CVM_GLOBAL_PIN)")
                .isEqualTo(Hex.decode("0103"));
        card.select(OTHER);
        assertThat(card.send(0x80, 0x4C, 0x11, 0x00).requireSuccess().data()).as("for another application")
                .isEqualTo(Hex.decode("0103"));
    }

    @Test
    void aServiceNameIsUniqueUntilItIsDeregistered_gpapi_GPRegistryEntry_registerService() {
        applet.grant(GPRegistryEntry.PRIVILEGE_GLOBAL_SERVICE);
        gp.application(OTHER).orElseThrow().grant(GPRegistryEntry.PRIVILEGE_GLOBAL_SERVICE);
        assertThat(card.send(0x80, 0x46, 0x82, 0x11)).isSuccess();

        assertThat(card.send(0x80, 0x46, 0x82, 0x11)).as("registered").hasStatusWord(0x6985);
        assertThat(card.send(0x80, 0x46, 0x82, 0x00)).as("the family has a registered service").hasStatusWord(0x6985);
        card.select(OTHER);
        assertThat(card.send(0x80, 0x46, 0x82, 0x11)).as("by another application").hasStatusWord(0x6985);
        assertThat(card.send(0x80, 0x48, 0x82, 0x11)).as("not its own").hasStatusWord(0x6A88);
        assertThat(card.send(0x80, 0x46, 0x84, 0x00)).as("another family, as a whole").isSuccess();
        assertThat(card.send(0x80, 0x46, 0x84, 0x01)).as("the family is registered").hasStatusWord(0x6985);

        card.select(APPLET);
        assertThat(card.send(0x80, 0x48, 0x82, 0x11)).isSuccess();
        assertThat(card.send(0x80, 0x4A, 0x82, 0x11).requireSuccess().u8(0)).isZero();
    }

    @Test
    void aDeletedApplicationLeavesTheRegistryWithItsServices() {
        applet.grant(GPRegistryEntry.PRIVILEGE_GLOBAL_SERVICE);
        card.send(0x80, 0x46, 0x82, 0x11).requireSuccess();

        card.delete(APPLET);
        card.select(OTHER);

        assertThat(gp.application(APPLET)).isEmpty();
        assertThat(card.send(0x80, 0x4A, 0x82, 0x11).requireSuccess().u8(0)).isZero();
        assertThat(card.send(0x80, 0x40, 0, 0, APPLET.toBytes()).requireSuccess().u8(0)).isZero();
    }

    @Test
    void aFailedInstallLeavesNoEntry() {
        assertThatThrownBy(() -> card.install(name.velikodniy.jcexpress.fakes.ParametersRequiredApplet.class,
                AID.fromHex("F0000000020103"), new byte[]{0x00})).isInstanceOf(IllegalStateException.class);

        assertThat(gp.applications()).extracting(CardApplication::aid).containsExactly(OTHER, APPLET);
    }
}
