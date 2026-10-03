package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.SCP02;
import name.velikodniy.jcexpress.scp.SCP03;
import name.velikodniy.jcexpress.scp.SCPException;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests of {@link GPSession#open()}: explicit secure channel initiation (GPCS v2.3.1 E.1.2.1, E.5;
 * Amendment D 5.2, 7.1) against public real-card handshakes, and the authentication safety rules: explicit
 * keys, fresh host challenges, no EXTERNAL AUTHENTICATE after a card cryptogram mismatch, no retries.
 */
class GPSessionAuthenticationTest {

    private static final SCPKeys TEST_KEYS = SCPKeys.defaultKeys();

    @Test
    void openAuthenticatesWithTheRealScp02CardHandshake() {
        ScriptedCard card = ScriptedCard.scp02();

        GPSession gp = card.open();

        assertThat(gp.isOpen()).isTrue();
        assertThat(gp.secureChannel()).isInstanceOf(SCP02.class);
        assertThat(gp.cardInfo().scpVersion()).isEqualTo(2);
        assertThat(gp.cardInfo().implementationOption()).isEqualTo(-1);
        assertThat(card.sent()).hasSize(2);
    }

    @Test
    void openAuthenticatesWithTheRealScp03CardHandshake() {
        ScriptedCard card = ScriptedCard.scp03();

        GPSession gp = card.open();

        assertThat(gp.secureChannel()).isInstanceOf(SCP03.class);
        assertThat(gp.cardInfo().implementationOption()).isEqualTo(0x70);
        assertThat(gp.cardInfo().keyVersion()).isEqualTo(0x01);
    }

    @Test
    void table_e_7_initializeUpdateIsCase4WithLe00() {
        ScriptedCard card = ScriptedCard.scp02();
        card.open();

        assertThat(Hex.encode(card.sent().getFirst())).isEqualTo("8050000008B39EE4A7F2A745BE00");
    }

    @Test
    void openWithoutKeysFailsBeforeAnythingIsSent() {
        ScriptedCard card = ScriptedCard.scp02();

        assertThatThrownBy(() -> GPSession.on(card).open())
                .isInstanceOf(GPException.class)
                .hasMessageContaining("No keys configured");
        assertThat(card.sent()).isEmpty();
    }

    @Test
    void e_1_2_cardCryptogramMismatchNeverSendsExternalAuthenticate() {
        ScriptedCard card = ScriptedCard.scp02();
        SCPKeys wrongKeys = SCPKeys.fromMasterKey(Hex.decode("404142434445464748494A4B4C4D4E40"));

        assertThatThrownBy(() -> card.session().keys(wrongKeys).open())
                .isInstanceOf(SCPException.class)
                .hasMessageContaining("Card cryptogram verification failed");
        assertThat(card.sent()).as("only INITIALIZE UPDATE").hasSize(1);
    }

    @Test
    void amdD_6_2_2_2_scp03CardCryptogramMismatchNeverSendsExternalAuthenticate() {
        ScriptedCard card = ScriptedCard.scp03();
        SCPKeys wrongKeys = SCPKeys.fromMasterKey(Hex.decode("404142434445464748494A4B4C4D4E40"));

        assertThatThrownBy(() -> card.session().keys(wrongKeys).open())
                .isInstanceOf(SCPException.class)
                .hasMessageContaining("Card cryptogram verification failed");
        assertThat(card.sent()).as("only INITIALIZE UPDATE").hasSize(1);
    }

    /**
     * A '6Cxx' answer must not make any layer re-send EXTERNAL AUTHENTICATE with a corrected Le (the ISO 7816-4
     * 5.1.3 rule for case 2 commands): the authentication is transmitted exactly once and the answer reported.
     */
    @Test
    void externalAuthenticateIsTransmittedExactlyOnceWhateverTheCardAnswers() {
        RecordingCard card = new RecordingCard(ScriptedCard.scp02Handshake(), "6C10", "9000");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).hostChallenge(ScriptedCard.scp02Handshake().hostChallenge());

        assertThatThrownBy(gp::open)
                .isInstanceOf(GPException.class)
                .hasMessageContaining("EXTERNAL AUTHENTICATE failed")
                .satisfies(e -> assertThat(((GPException) e).statusWord()).isEqualTo(0x6C10));
        assertThat(card.sent()).as("INITIALIZE UPDATE and one EXTERNAL AUTHENTICATE").hasSize(2);
        assertThat(gp.isOpen()).isFalse();
    }

    @Test
    void table_e_12_rejectedExternalAuthenticateIsReportedAndNeverRetried() {
        RecordingCard card = new RecordingCard(ScriptedCard.scp02Handshake(), "6300");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).hostChallenge(ScriptedCard.scp02Handshake().hostChallenge());

        assertThatThrownBy(gp::open)
                .isInstanceOf(GPException.class)
                .hasMessageContaining("EXTERNAL AUTHENTICATE failed")
                .hasMessageContaining("not retried");
        assertThat(card.sent()).hasSize(2);
        assertThat(gp.isOpen()).isFalse();
    }

    @Test
    void unsupportedSecurityLevelIsRejectedBeforeExternalAuthenticate() {
        ScriptedCard card = ScriptedCard.scp02();

        assertThatThrownBy(() -> card.session().securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC).open())
                .isInstanceOf(SCPException.class)
                .hasMessageContaining("security level 33");
        assertThat(card.sent()).hasSize(1);
    }

    @Test
    void e_5_1_5_everyOpenUsesAFreshRandomHostChallenge() {
        RecordingCard card = new RecordingCard(null, "6A88");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS);
        List<String> challenges = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(gp::open).isInstanceOf(GPException.class).hasMessageContaining("INITIALIZE UPDATE");
            byte[] command = card.sent().getLast();
            assertThat(command).hasSize(14);
            challenges.add(Hex.encode(Arrays.copyOfRange(command, 5, 13)));
        }

        assertThat(challenges).doesNotHaveDuplicates();
    }

    @Test
    void fixedTestHostChallengeIsUsedForOneOpenOnly() {
        RecordingCard card = new RecordingCard(null, "6A88");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).hostChallenge(Hex.decode("0102030405060708"));

        assertThatThrownBy(gp::open).isInstanceOf(GPException.class);
        assertThatThrownBy(gp::open).isInstanceOf(GPException.class);

        assertThat(Hex.encode(card.sent().get(0))).contains("0102030405060708");
        assertThat(Hex.encode(card.sent().get(1))).doesNotContain("0102030405060708");
    }

    @Test
    void amdD_v1_2_s16SessionsSendA16ByteHostChallenge() {
        RecordingCard card = new RecordingCard(null, "6A88");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).scp03S16(true);

        assertThatThrownBy(gp::open).isInstanceOf(GPException.class);

        assertThat(Hex.encode(card.sent().getFirst())).startsWith("8050000010").hasSize(2 * 22);
    }

    @Test
    void securityDomainIsSelectedBeforeInitializeUpdate() {
        RecordingCard card = new RecordingCard(null, "9000", "6A88");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).securityDomain("A000000151000000");

        assertThatThrownBy(gp::open).isInstanceOf(GPException.class);

        assertThat(Hex.encode(card.sent().getFirst())).isEqualTo("00A4040008A00000015100000000");
        assertThat(Hex.encode(card.sent().get(1))).startsWith("80500000");
    }

    @Test
    void failedSecurityDomainSelectionStopsTheAuthentication() {
        RecordingCard card = new RecordingCard(null, "6A82");
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).securityDomain("A000000151000000");

        assertThatThrownBy(gp::open).isInstanceOf(GPException.class).hasMessageContaining("SELECT");
        assertThat(card.sent()).hasSize(1);
    }

    @Test
    void openTwiceIsRejectedButCloseAllowsANewAuthentication() {
        ScriptedCard card = ScriptedCard.scp02();
        GPSession gp = card.open();

        assertThatThrownBy(gp::open).isInstanceOf(GPException.class).hasMessageContaining("already open");

        gp.close();
        assertThat(gp.isOpen()).isFalse();
        assertThat(gp.secureChannel()).isNull();
        assertThat(gp.cardInfo()).isNull();
        assertThatThrownBy(() -> gp.getData(0x00, 0x66)).isInstanceOf(GPException.class).hasMessageContaining("not open");
    }

    /**
     * When the transport fails after a wrapped command was handed to it, the card may or may not have verified
     * that C-MAC, so the MAC chaining value is unknown (GPCS v2.3.1 E.4.4): the session is closed (keys
     * zeroized) and must be opened again.
     */
    @Test
    void transportFailureDuringACommandClosesTheSession() {
        FailingCard card = new FailingCard(ScriptedCard.scp02(), 2);
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).hostChallenge(ScriptedCard.scp02Handshake().hostChallenge())
                .open();
        SCP02 channel = (SCP02) gp.secureChannel();

        assertThatThrownBy(gp::getStatus).isInstanceOf(IllegalStateException.class).hasMessageContaining("reader");

        assertThat(gp.isOpen()).isFalse();
        assertThat(channel.sessionMacKey()).containsOnly(0);
        assertThatThrownBy(gp::getStatus).isInstanceOf(GPException.class).hasMessageContaining("not open");
    }

    @Test
    void transportFailureDuringExternalAuthenticateDestroysTheCandidateChannel() {
        FailingCard card = new FailingCard(ScriptedCard.scp02(), 1);
        GPSession gp = GPSession.on(card).keys(TEST_KEYS).hostChallenge(ScriptedCard.scp02Handshake().hostChallenge());

        assertThatThrownBy(gp::open).isInstanceOf(IllegalStateException.class);

        assertThat(gp.isOpen()).isFalse();
        assertThat(gp.secureChannel()).isNull();
        assertThat(card.sent()).hasSize(2);
    }

    @Test
    void closeDestroysTheSessionKeys() {
        GPSession gp = ScriptedCard.scp02().open();
        SCP02 channel = (SCP02) gp.secureChannel();

        gp.close();

        assertThat(channel.sessionMacKey()).containsOnly(0);
    }

    @Test
    void diversifiedKeysAreDerivedFromTheCardsDiversificationData() {
        ScriptedCard card = ScriptedCard.scp02();
        List<byte[]> seen = new ArrayList<>();

        card.session().keys(SCPKeys.fromMasterKey(new byte[16]))
                .diversification((master, kdd) -> {
                    seen.add(kdd);
                    return TEST_KEYS;
                })
                .open();

        assertThat(Hex.encode(seen.getFirst())).isEqualTo("00005300000105000078");
    }

    /** A card that behaves like the given stand-in until the reader fails on the command with the given index. */
    static final class FailingCard extends StubSession {
        private final StubSession card;
        private final int failingCommand;
        private int step;

        FailingCard(StubSession card, int failingCommand) {
            this.card = card;
            this.failingCommand = failingCommand;
        }

        @Override
        protected byte[] respond(byte[] apdu) {
            if (step++ == failingCommand) {
                throw new IllegalStateException("reader removed during the command");
            }
            return card.transmit(apdu);
        }
    }

    /** A card that answers a fixed sequence of status words and records all commands. */
    static final class RecordingCard extends StubSession {
        private final ScpTranscript handshake;
        private final List<String> answers;
        private int step;

        RecordingCard(ScpTranscript handshake, String... answers) {
            this.handshake = handshake;
            this.answers = List.of(answers);
        }

        @Override
        protected byte[] respond(byte[] apdu) {
            int current = step++;
            if (handshake != null && current == 0) {
                byte[] iu = handshake.initUpdate();
                byte[] response = Arrays.copyOf(iu, iu.length + 2);
                response[iu.length] = (byte) 0x90;
                return response;
            }
            int index = Math.min(handshake != null ? current - 1 : current, answers.size() - 1);
            return Hex.decode(answers.get(index));
        }
    }
}
