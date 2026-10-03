package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.scp.SCPException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Command encodings of {@link GPSession} against the GPCS v2.3.1 command tables (GET DATA 11.3, GET STATUS
 * 11.4, SET STATUS 11.10, STORE DATA 11.11) and the short-APDU budget of 11.1.5. The session is
 * authenticated with a public real-card handshake ({@link ScriptedCard}); the commands are compared before
 * secure messaging.
 */
class GPSessionCommandTest {

    private static final String ISD_ENTRY = "E3134F08A0000001510000009F700101C5039EFE80";
    private static final String PACKAGE_ENTRY = "E30D4F07A00000015153509F700101";
    private static final String APP_ENTRY = "E3114F08A0000000620301019F700107C50100";

    @Nested
    class GetData {

        @Test
        void table_11_27_getDataIsACase2CommandWithLe00() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6612730A06082A864886FC6B01019000");
            APDUResponse response = card.open().getData(0x00, 0x66);

            assertThat(card.plainCommands()).containsExactly("80CA006600");
            assertThat(response.dataAsHex()).isEqualTo("6612730A06082A864886FC6B0101");
        }

        @Test
        void identificationDataObjectsAreReturned() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("4201009000").thenAnswer("450800000000000000009000");
            GPSession gp = card.open();

            assertThat(Hex.encode(gp.getIIN())).isEqualTo("420100");
            assertThat(Hex.encode(gp.getCIN())).isEqualTo("45080000000000000000");
            assertThat(card.plainCommands()).containsExactly("80CA004200", "80CA004500");
        }

        @Test
        void sequenceCounterIsReadFromTheC1DataObject() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("C102009C9000");

            assertThat(card.open().getSequenceCounter()).isEqualTo(0x9C);
            assertThat(card.plainCommands()).containsExactly("80CA00C100");
        }

        @Test
        void keyInformationTemplateIsParsed() {
            ScriptedCard card = ScriptedCard.scp02()
                    .thenAnswer("E012C00401FF8010C00402FF8010C00403FF80109000");

            List<KeyInfoEntry> keys = card.open().getKeyInformation();

            assertThat(keys).hasSize(3);
            assertThat(card.plainCommands()).containsExactly("80CA00E000");
        }

        @Test
        void failedGetDataReportsTheStatusWord() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6A88");
            GPSession gp = card.open();

            assertThatThrownBy(gp::getCPLC)
                    .isInstanceOf(GPException.class)
                    .satisfies(e -> assertThat(((GPException) e).statusWord()).isEqualTo(0x6A88));
        }
    }

    @Nested
    class GetStatus {

        @Test
        void table_11_32_getStatusRequestsTheTlvFormatWithSearchCriterion4F00() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer(APP_ENTRY + "9000");

            List<AppletInfo> apps = card.open().getStatus();

            assertThat(card.plainCommands()).containsExactly("80F24002024F0000");
            assertThat(apps).singleElement().satisfies(app -> {
                assertThat(app.aidHex()).isEqualTo("A000000062030101");
                assertThat(app.lifeCycleState()).isEqualTo(0x07);
            });
        }

        @Test
        void table_11_38_moreDataIsFetchedWithGetStatusNext() {
            ScriptedCard card = ScriptedCard.scp02()
                    .thenAnswer(PACKAGE_ENTRY + "6310")
                    .thenAnswer("E30C4F06D276000124019F700101" + "9000");

            List<AppletInfo> files = card.open().getLoadFiles();

            assertThat(card.plainCommands()).containsExactly("80F22002024F0000", "80F22003024F0000");
            assertThat(files).extracting(AppletInfo::aidHex).containsExactly("A0000001515350", "D27600012401");
        }

        @Test
        void table_11_39_referencedDataNotFoundMeansNoEntries() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6A88");

            assertThat(card.open().getStatus()).isEmpty();
        }

        @Test
        void errorStatusIsReported() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6A80");
            GPSession gp = card.open();

            assertThatThrownBy(gp::getStatus).isInstanceOf(GPException.class).hasMessageContaining("GET STATUS");
        }

        @Test
        void malformedResponseIsReportedInsteadOfTruncatingTheList() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer(ISD_ENTRY + "E3104F08A0" + "9000");
            GPSession gp = card.open();

            assertThatThrownBy(gp::getStatus).isInstanceOf(GPException.class).hasMessageContaining("Malformed");
        }

        @Test
        void entryWithoutAidIsReported() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("E3049F700107" + "9000");
            GPSession gp = card.open();

            assertThatThrownBy(gp::getStatus).isInstanceOf(GPException.class).hasMessageContaining("'4F'");
        }

        @Test
        void table_11_36_allThreePrivilegeBytesAreKept() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer(ISD_ENTRY + "9000");

            AppletInfo isd = card.open().getStatus(Lifecycle.SCOPE_ISD).getFirst();

            assertThat(card.plainCommands()).containsExactly("80F28002024F0000");
            assertThat(Hex.encode(isd.privilegeBytes())).isEqualTo("9EFE80");
            assertThat(isd.privileges()).isEqualTo(0x9E);
            assertThat(isd.isSecurityDomain()).isTrue();
        }

        @Test
        void domainsAreTheApplicationsWithTheSecurityDomainPrivilege() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer(ISD_ENTRY + APP_ENTRY + "9000");

            assertThat(card.open().getDomains()).extracting(AppletInfo::aidHex).containsExactly("A000000151000000");
            assertThat(card.plainCommands()).containsExactly("80F24002024F0000");
        }
    }

    @Nested
    class SetStatus {

        @Test
        void table_11_85_lockAppSendsTheRawAidAndP2Locked() {
            ScriptedCard card = ScriptedCard.scp02();
            card.open().lockApp("A000000062030101");

            assertThat(card.plainCommands()).containsExactly("80F0408008A000000062030101");
        }

        @Test
        void gpcs_11_10_2_2_unlockAppRequestsTheTransitionBackWithB8Zero() {
            ScriptedCard card = ScriptedCard.scp02();
            card.open().unlockApp("A000000062030101");

            assertThat(card.plainCommands()).containsExactly("80F0400008A000000062030101");
        }

        @Test
        void gpcs_11_10_2_2_terminateAppIsRejectedWithoutSendingAnything() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.terminateApp("A000000062030101"))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("11.10.2.2");
            assertThat(card.commandCount()).isZero();
        }

        @Test
        void table_11_86_securityDomainAndItsApplicationsScope() {
            ScriptedCard card = ScriptedCard.scp02();
            card.open().setStatus(Lifecycle.SCOPE_SD_AND_APPS, "A000000151000000", Lifecycle.APP_LOCKED);

            assertThat(card.plainCommands()).containsExactly("80F0608008A000000151000000");
        }

        @Test
        void table_11_6_cardLifeCycleChangesCarryNoData() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            gp.lockCard();
            gp.unlockCard();
            gp.terminateCard();

            assertThat(card.plainCommands()).containsExactly("80F0807F", "80F0800F", "80F080FF");
        }

        @Test
        void rejectedSetStatusReportsTheStatusWord() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6A88");
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.lockApp("A000000062030101"))
                    .isInstanceOf(GPException.class)
                    .satisfies(e -> assertThat(((GPException) e).statusWord()).isEqualTo(0x6A88));
        }
    }

    @Nested
    class StoreData {

        @Test
        void table_11_88_storeDataIsSplitIntoNumberedCase3Blocks() {
            ScriptedCard card = ScriptedCard.scp02();
            byte[] data = new byte[500];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) i;
            }

            card.open().storeData(data);

            List<String> commands = card.plainCommands();
            assertThat(commands).hasSize(3);
            assertThat(commands.get(0)).startsWith("80E20000F7").hasSize(2 * (5 + 247));
            assertThat(commands.get(1)).startsWith("80E20001F7").hasSize(2 * (5 + 247));
            assertThat(commands.get(2)).isEqualTo("80E2800206" + Hex.encode(new byte[]{
                    (byte) 0xEE, (byte) 0xEF, (byte) 0xF0, (byte) 0xF1, (byte) 0xF2, (byte) 0xF3}));
        }

        @Test
        void gpcs_11_11_2_2_moreThan256BlocksAreRejectedBeforeSending() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.storeData(new byte[257 * 247]))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("256");
            assertThat(card.commandCount()).isZero();
        }
    }

    @Nested
    class ShortApduBudget {

        @Test
        void gpcs_11_1_5_dataThatDoesNotFitAfterSecureMessagingIsRejectedBeforeSending() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.send(0x80, 0xE2, 0x80, 0x00, new byte[248]))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("247");
            assertThat(card.commandCount()).isZero();
        }

        @Test
        void gpcs_11_1_5_extendedLengthCommandsAreRejectedBeforeSending() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.send(0x80, 0xE2, 0x80, 0x00, new byte[300]))
                    .isInstanceOf(SCPException.class);
            assertThat(card.commandCount()).isZero();
        }

        @Test
        void sendWithLe256EncodesLe00AndTransmitReturnsDataAndStatusWord() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("01029000").thenAnswer("9000");
            GPSession gp = card.open();

            APDUResponse response = gp.send(0x80, 0xCA, 0x00, 0x66, null, 256);
            byte[] raw = gp.transmit(Hex.decode("80CA9F7F00"));

            assertThat(response.dataAsHex()).isEqualTo("0102");
            assertThat(Hex.encode(raw)).isEqualTo("9000");
            assertThat(card.plainCommands()).containsExactly("80CA006600", "80CA9F7F00");
        }
    }
}
