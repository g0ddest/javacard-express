package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.UnexpectedStatusWordError;
import name.velikodniy.jcexpress.livecard.guard.GuardViolationException;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The history of the guarded session (what a failed {@code @JavaCardTest} test shows and {@code -Djcx.log=true}
 * prints) labels the card content management of the harness, collapses the LOAD commands of a load file into one
 * note (GPCS v2.3.1 11.6) and shows commands the guard blocked; the transcript file keeps every command. Responses
 * know their command, and a failed SELECT says what its status word means (ISO/IEC 7816-4:2005 5.1.3). Every exchange
 * is timed, in the history and in the transcript file.
 */
class LiveCardHistoryTest {

    /** A response line with the time of its exchange: {@code R: 9000  (12.3 ms)}. */
    private static final String TIMED_RESPONSE = "R: [0-9A-F]+ {2}\\(\\d+\\.\\d ms\\)";

    @TempDir
    Path transcripts;

    @BeforeEach
    void insertCard() {
        SimulatedCardConnector.insertNewCard();
    }

    private LiveCard connect() {
        LiveCardConfig config = LiveCardConfig.of(Map.of("transcriptDir", transcripts.toString(), "verifierSdk",
                "none"));
        LiveCard card = LiveCard.connect(config, new SimulatedCardConnector(), new LiveCardRun(() -> { }));
        card.transcriptTo(Path.of("card.txt"), "history");
        return card;
    }

    @Test
    void theCardContentManagementIsLabelledAndTheLoadBlocksAreOneNote_gpcs231_11_6() throws Exception {
        try (LiveCard card = connect()) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            card.delete(TestApplet.HELLO.moduleAid(card.config()), false);

            String history = card.session().history().transcript();
            assertThat(history).contains("# secure channel to the Issuer Security Domain A000000151000000 at"
                            + " security level 01")
                    .contains("# load com.jcx.livecard.hello as F04A43580101 (CAP file of ")
                    .containsPattern("# \\d+ LOAD blocks? \\(\\d+ bytes of commands\\), the last answered 9000;"
                            + " every block is in the transcript card.txt")
                    .contains("C: 84E60200").contains("C: 84E60C00")
                    .contains("# delete F04A4358010101")
                    .doesNotContain("C: 84E8");
            assertThat(Files.readString(transcripts.resolve("card.txt"))).contains("C: 84E8");
        }
    }

    @Test
    void cleanupNotesTheDeletionOfEveryLoadFile_gpcs231_11_2() {
        try (LiveCard card = connect()) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            card.cleanup();

            String history = card.session().history().transcript();
            int note = history.indexOf("# delete F04A43580101 with its applications");
            assertThat(note).isNotNegative();
            assertThat(history.indexOf("C: 84E40080", note)).isGreaterThan(note);
        }
    }

    @Test
    void aBlockedCommandIsNotedWhereItWouldHaveBeenSent() {
        try (LiveCard card = connect()) {
            assertThatThrownBy(() -> card.session().send(0x80, 0xD8, 0x01, 0x81, new byte[16], 0))
                    .isInstanceOf(GuardViolationException.class);

            assertThat(card.session().history().transcript())
                    .startsWith("# blocked by the APDU guard, not sent: 80D8018110" + "00".repeat(16))
                    .contains(" (INS D8 is not on the allow-list");
        }
    }

    @Test
    void aResponseKnowsItsCommandAndAFailedSelectExplainsItsStatusWord() {
        try (LiveCard card = connect()) {
            APDUResponse response = card.session().send(0x80, 0xCA, 0x00, 0x66, null, 256);
            assertThat(response.isSuccess()).isTrue();

            assertThatThrownBy(() -> card.session().send(0x80, 0xCA, 0x01, 0x02, null, 256).requireSuccess())
                    .isInstanceOf(UnexpectedStatusWordError.class).hasMessageContaining("C: 80CA010200");
            assertThatThrownBy(() -> card.session().select(AID.fromHex("F04A4358FFFF01")))
                    .isInstanceOf(SelectException.class)
                    .hasMessage("SELECT F04A4358FFFF01 failed: SW=6A82 (file or application not found)");
        }
    }

    @Test
    void everyExchangeIsTimedInTheHistoryAndInTheTranscriptFile() throws Exception {
        try (LiveCard card = connect()) {
            long before = System.currentTimeMillis();
            card.session().send(0x80, 0xCA, 0x00, 0x66, null, 256);

            APDULogEntry entry = card.session().history().last();
            assertThat(entry.ins()).isEqualTo(0xCA);
            assertThat(entry.timestampMs()).isBetween(before, System.currentTimeMillis());
            assertThat(entry.duration()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
            assertThat(card.session().history().transcript().lines().filter(line -> line.startsWith("R: ")))
                    .isNotEmpty().allSatisfy(line -> assertThat(line).matches(TIMED_RESPONSE));
        }
        assertThat(Files.readAllLines(transcripts.resolve("card.txt"))).filteredOn(line -> line.startsWith("R: "))
                .isNotEmpty().allSatisfy(line -> assertThat(line).matches(TIMED_RESPONSE));
    }
}
