package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.sim.SimulatedTerminal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transcript header line on '61XX' and '6CXX' (ISO/IEC 7816-4:2005 5.1.3): below a PC/SC reader the JDK's
 * provider completes them itself, out of the transcript's sight; below the simulated card nothing does, and the
 * card of a {@code @JavaCardTest} class completes them in {@code send()}, so the exchanges are in the transcript.
 */
class HarnessTextsTest {

    @Test
    void aReaderGetsTheNoteOnTheJdksOwnExchanges_iso7816_4_5_1_3() {
        assertThat(HarnessTexts.exchangesNote("ACS ACR39U ICC Reader 00 00"))
                .startsWith("javax.smartcardio: t0GetResponse=")
                .contains("the JDK itself answers 61XX with GET RESPONSE and repeats a command after 6CXX");
    }

    @Test
    void theSimulatedCardSaysThatTheExchangesAreInTheTranscript_iso7816_4_5_1_3() {
        assertThat(HarnessTexts.exchangesNote(SimulatedTerminal.READER))
                .startsWith("simulated card: nothing answers 61XX or 6CXX below this transcript")
                .contains("send()")
                .doesNotContain("t0GetResponse");
    }
}
