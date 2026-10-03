package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.HEX;
import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.TEST_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard lets EXTERNAL AUTHENTICATE through only when it verified the handshake itself, so wrong keys never
 * cost an authentication try, and every authentication problem counts against the run's budget.
 *
 * <p>Reference vectors: {@code guard-vectors.json} of the real-card harness, produced by an independent Python
 * implementation of GlobalPlatform Amendment D (embedded in {@link GuardSelfCheck#REFERENCE_HANDSHAKES}).</p>
 */
class ApduGuardHandshakeTest {

    private static final GuardSelfCheck.Handshake RIGHT_KEYS = GuardSelfCheck.REFERENCE_HANDSHAKES.get(0);
    private static final GuardSelfCheck.Handshake OTHER_KEYS = GuardSelfCheck.REFERENCE_HANDSHAKES.get(1);
    private static final byte[] HOST_CHALLENGE = HEX.parseHex("A1A2A3A4A5A6A7A8");

    private final GuardHarness harness = new GuardHarness();

    private void referenceInitializeUpdate(GuardSelfCheck.Handshake handshake) {
        harness.selectIsd();
        harness.exchange(handshake.initializeUpdate(), handshake.response());
    }

    @Test
    void referenceHandshakeWithTheRightKeysPasses() {
        referenceInitializeUpdate(RIGHT_KEYS);

        assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isTrue();
        assertThat(harness.guard.verifiedHandshakes()).isEqualTo(1);
        assertThat(harness.budget.failures()).isZero();
        assertThat(harness.notes).anyMatch(note -> note.contains("card cryptogram independently VERIFIED"));
    }

    @Test
    void referenceHandshakeWithOtherKeysIsBlockedAndAbortsTheRun() {
        referenceInitializeUpdate(OTHER_KEYS);

        assertThat(harness.notes).anyMatch(note -> note.contains("card cryptogram NOT verified"));
        assertThat(harness.allows(OTHER_KEYS.externalAuthenticate())).isFalse();
        assertThat(harness.budget.exhausted()).isTrue();
        assertThatThrownBy(() -> harness.guard.check(HEX.parseHex(GuardHarness.SELECT_ISD)))
                .isInstanceOf(GuardViolationException.class)
                .hasMessageContaining("live-card run aborted")
                .hasMessageContaining("does not match the configured keys");
    }

    @Test
    void corruptedHostCryptogramIsBlocked() {
        referenceInitializeUpdate(RIGHT_KEYS);
        byte[] command = HEX.parseHex(RIGHT_KEYS.externalAuthenticate());
        command[7] ^= 0x01;

        assertThat(harness.allows(HEX.formatHex(command))).isFalse();
        assertThat(harness.budget.failures()).isEqualTo(1);
    }

    @Test
    void corruptedCmacIsBlocked() {
        referenceInitializeUpdate(RIGHT_KEYS);
        byte[] command = HEX.parseHex(RIGHT_KEYS.externalAuthenticate());
        command[command.length - 1] ^= 0x01;

        assertThat(harness.allows(HEX.formatHex(command))).isFalse();
        assertThat(harness.notes).anyMatch(note -> note.contains("C-MAC differs"));
    }

    @Test
    void securityLevelTheCardDoesNotSupportIsBlocked() {
        referenceInitializeUpdate(RIGHT_KEYS);
        byte[] command = HEX.parseHex(RIGHT_KEYS.externalAuthenticate());
        command[2] = 0x33;

        assertThat(harness.allows(HEX.formatHex(command))).isFalse();
        assertThat(harness.notes).anyMatch(note -> note.contains("security level 33 is not allowed"));
    }

    @Test
    void externalAuthenticateWithoutInitializeUpdateIsBlocked() {
        harness.selectIsd();

        assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isFalse();
        assertThat(harness.budget.failures()).isEqualTo(1);
    }

    @Test
    void oneInitializeUpdateAuthorizesOneExternalAuthenticate() {
        GuardHarness lenient = new GuardHarness(3);
        lenient.selectIsd();
        lenient.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());

        assertThat(lenient.allows(RIGHT_KEYS.externalAuthenticate())).isTrue();
        assertThat(lenient.allows(RIGHT_KEYS.externalAuthenticate())).isFalse();
    }

    @ParameterizedTest(name = "security level {0}")
    @ValueSource(ints = {0x00, 0x01, 0x03})
    void externalAuthenticateBuiltByTheGpModuleIsRecognized(int level) {
        harness.selectIsd();
        byte[] response = harness.initializeUpdate("80", HOST_CHALLENGE, TEST_KEY, 0x00);

        String command = GuardHarness.externalAuthenticateByGp(HOST_CHALLENGE, response, level);
        harness.exchange(command, "9000");

        assertThat(harness.budget.failures()).isZero();
        assertThat(harness.notes).anyMatch(note -> note.contains(String.format("security level %02X", level)));
    }

    @Test
    void rmacLevelPassesWhenTheCardAnnouncesRmacSupport() {
        harness.selectIsd();
        byte[] response = harness.initializeUpdate("80", HOST_CHALLENGE, TEST_KEY, 0x20);

        assertThat(harness.allows(GuardHarness.externalAuthenticateByGp(HOST_CHALLENGE, response, 0x13))).isTrue();
    }

    @Test
    void s16HostChallengeIsNotSent() {
        harness.selectIsd();

        assertThat(harness.allows("8050000010" + "00".repeat(16) + "00")).isFalse();
        assertThat(harness.budget.failures()).as("nothing was sent, nothing counts").isZero();
    }

    @Test
    void rejectedInitializeUpdateCounts() {
        harness.selectIsd();
        harness.exchange("8050300008A1A2A3A4A5A6A7A800", "6A88");

        assertThat(harness.budget.exhausted()).isTrue();
        assertThat(harness.budget.abortReason()).hasValueSatisfying(reason -> assertThat(reason).contains("6A88"));
    }

    @Test
    void scp02CardIsNotVerified() {
        harness.selectIsd();
        String response = "00000000000000000000FF02" + "000A" + "010203040506" + "0102030405060708" + "9000";
        harness.exchange("8050000008A1A2A3A4A5A6A7A800", response);

        assertThat(harness.budget.abortReason()).hasValueSatisfying(reason -> assertThat(reason).contains("SCP02"));
    }

    @Test
    void externalAuthenticateRejectedByTheCardCounts() {
        GuardHarness lenient = new GuardHarness(2);
        lenient.selectIsd();
        lenient.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());
        lenient.exchange(RIGHT_KEYS.externalAuthenticate(), "6300");

        assertThat(lenient.budget.failures()).isEqualTo(1);
        assertThat(lenient.notes).anyMatch(note -> note.contains("rejected by the card with SW=6300"));
    }

    @Test
    void handshakeBelongsToItsLogicalChannel() {
        harness.exchange("0070000001", "019000");
        harness.selectIsd();
        harness.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());
        byte[] onChannelOne = HEX.parseHex(RIGHT_KEYS.externalAuthenticate());
        onChannelOne[0] = (byte) 0x85;

        assertThat(harness.allows(HEX.formatHex(onChannelOne))).isFalse();
        assertThat(harness.notes).anyMatch(note -> note.contains("without INITIALIZE UPDATE on this channel"));
    }

    @Test
    void selfCheckPasses() {
        GuardSelfCheck.verify();
    }
}
