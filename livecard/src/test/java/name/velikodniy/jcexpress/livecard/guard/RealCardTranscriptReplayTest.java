package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.HEX;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the transcripts of the validated real-card runs (JCOP 4, GP test keys; card serial numbers zeroed)
 * through the guard: every command those runs sent must pass, with the same write-access scopes, every real
 * INITIALIZE UPDATE must verify with the guard's own cryptography, and the content changes must be seen.
 */
class RealCardTranscriptReplayTest {

    /** Commands of a transcript, with the write-access toggles and the card's responses. */
    private record Line(String kind, String value) {
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "step1-transcript.txt, 2, 0, 0",
        "step2-transcript.txt, 2, 2, 1",
        "step3-transcript.txt, 5, 3, 2",
    })
    void everyCommandOfTheValidatedRunPasses(String transcript, int handshakes, int created, int deletions)
            throws IOException {
        GuardHarness harness = new GuardHarness();
        ApduGuard guard = harness.guard;
        byte[] command = null;
        for (Line line : read(transcript)) {
            switch (line.kind()) {
                case "connect" -> guard.cardReset();
                case "write" -> guard.writeAccess(Boolean.parseBoolean(line.value()));
                case "C" -> {
                    command = HEX.parseHex(line.value());
                    guard.check(command);
                }
                default -> guard.observe(command, HEX.parseHex(line.value()));
            }
        }

        assertThat(guard.blockedCount()).isZero();
        assertThat(harness.budget.failures()).isZero();
        assertThat(guard.verifiedHandshakes()).isEqualTo(handshakes);
        assertThat(harness.changes).filteredOn(change -> !(change instanceof ContentChange.Deleted)).hasSize(created);
        assertThat(harness.changes).filteredOn(ContentChange.Deleted.class::isInstance).hasSize(deletions);
    }

    private static List<Line> read(String name) throws IOException {
        List<Line> lines = new ArrayList<>();
        try (InputStream in = RealCardTranscriptReplayTest.class.getResourceAsStream("/realcard/" + name)) {
            for (String text : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (text.startsWith("# reader:")) {
                    lines.add(new Line("connect", ""));
                } else if (text.startsWith("# guard write mode: ")) {
                    lines.add(new Line("write", text.substring("# guard write mode: ".length()).strip()));
                } else if (text.startsWith("C: ") || text.startsWith("R: ")) {
                    lines.add(new Line(text.substring(0, 1), text.substring(3).strip().split(" ")[0]));
                }
            }
        }
        return lines;
    }
}
