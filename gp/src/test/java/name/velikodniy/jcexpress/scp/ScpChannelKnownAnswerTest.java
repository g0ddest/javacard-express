package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.ScpTranscript;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-answer tests of the {@link SCP02} and {@link SCP03} channel classes alone (no GPSession), against
 * transcripts that were NOT produced by javacard-express (see {@link ScpTranscript}): spec-derived vectors
 * of an independent reference implementation (GPCS v2.3.1 Appendix E, Amendment D v1.1.2 and v1.2 S16)
 * and public real-card / third-party transcripts.
 *
 * <p>For every transcript: the card cryptogram verifies, EXTERNAL AUTHENTICATE is byte-identical, every
 * plain command wraps to the transcript's wire command and every card response unwraps to the expected
 * data and status word.</p>
 */
class ScpChannelKnownAnswerTest {

    static List<ScpTranscript> transcripts() {
        return ScpTranscript.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transcripts")
    void channelReproducesTranscript(ScpTranscript t) {
        SecureChannel channel = authenticate(t);

        assertThat(Hex.encode(channel.externalAuthenticate())).as("EXTERNAL AUTHENTICATE")
                .isEqualTo(Hex.encode(t.extAuth()));
        for (ScpTranscript.Command command : t.commands()) {
            assertThat(Hex.encode(channel.wrap(command.plain()))).as("wrapped %s", command)
                    .isEqualTo(Hex.encode(command.wrapped()));
            APDUResponse response = channel.unwrap(new APDUResponse(command.cardResponse()));
            assertThat(Hex.encode(response.data())).as("response data of %s", command)
                    .isEqualTo(Hex.encode(command.data()));
            assertThat(response.sw()).as("status word of %s", command).isEqualTo(command.sw());
        }
    }

    /** Creates the channel from the INITIALIZE UPDATE response and verifies the card cryptogram. */
    static SecureChannel authenticate(ScpTranscript t) {
        if (t.protocol() == 2) {
            SCP02 scp02 = SCP02.from(t.keys(), t.initUpdate(), t.level(), t.option());
            scp02.verifyCardCryptogram(t.hostChallenge());
            return scp02;
        }
        return SCP03.from(t.keys(), t.hostChallenge(), t.initUpdate(), t.level());
    }
}
