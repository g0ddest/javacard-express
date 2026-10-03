package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-answer tests for the complete {@link GPSession} flow against transcripts that were NOT produced
 * by javacard-express (see {@link ScpTranscript}):
 * <ul>
 *   <li>INITIALIZE UPDATE with Le '00' (GPCS v2.3.1 Table E-7, Amendment D Table 7-2);</li>
 *   <li>card cryptogram verification (GPCS E.4.2.1; Amd D 6.2.2.2 with S-MAC) and the INITIALIZE UPDATE
 *       response layout (GPCS Table E-8; Amd D Table 7-3);</li>
 *   <li>EXTERNAL AUTHENTICATE with Lc '10' and never encrypted (GPCS Table E-10, E.5.2.3; Amd D Table 7-5);</li>
 *   <li>SCP02 C-MAC with ICV encryption (E.3.4), MAC-then-encrypt C-ENC (E.4.4, E.4.6) and R-MAC
 *       (E.4.5, E.3.2);</li>
 *   <li>SCP03 encryption counter (Amd D 6.2.6), R-MAC over the MAC chaining value (6.2.5), R-ENC ICV
 *       (6.2.7), no R-MAC on error status words (6.2.5), AES-128/192/256 and S8/S16 modes.</li>
 * </ul>
 */
class GPSessionKnownAnswerTest {

    static List<ScpTranscript> transcripts() {
        return ScpTranscript.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transcripts")
    void sessionMatchesKnownAnswerTranscript(ScpTranscript t) {
        TranscriptCard card = new TranscriptCard(t);
        GPSession gp = GPSession.on(card)
                .keys(t.keys())
                .keyVersion(t.keyVersion())
                .hostChallenge(t.hostChallenge())
                .securityLevel(t.level());
        if (t.option() >= 0) {
            gp.scp02Option(t.option());
        }

        gp.open();

        for (ScpTranscript.Command command : t.commands()) {
            byte[] response = gp.transmit(command.plain());
            assertThat(Hex.encode(response))
                    .as("unwrapped response to %s", command)
                    .isEqualTo(Hex.encode(command.data()) + String.format("%04X", command.sw()));
        }
        assertThat(card.mismatches()).isEmpty();
        assertThat(card.completed()).isTrue();
    }
}
