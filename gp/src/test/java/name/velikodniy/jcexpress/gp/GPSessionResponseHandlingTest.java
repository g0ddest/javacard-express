package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Responses to protected commands that are not a plain '9000': error status words in R-MAC sessions, '6CXX'
 * (wrong Le) and '61XX' (more data), replayed by strict card stand-ins ({@link TranscriptCard}: any unexpected
 * command, e.g. one carrying a C-MAC the card has already verified, is answered '6982' and recorded) from
 * spec-derived transcripts that were not produced by javacard-express (see {@link ScpTranscript}).
 */
class GPSessionResponseHandlingTest {

    private static GPSession open(SmartCardSession card, ScpTranscript t) {
        GPSession gp = GPSession.on(card)
                .keys(t.keys())
                .keyVersion(t.keyVersion())
                .hostChallenge(t.hostChallenge())
                .securityLevel(t.level());
        if (t.option() >= 0) {
            gp.scp02Option(t.option());
        }
        return gp.open();
    }

    private static String expected(ScpTranscript.Command command) {
        return Hex.encode(command.data()) + String.format("%04X", command.sw());
    }

    private static List<ScpTranscript> transport(String kind) {
        return ScpTranscript.load(ScpTranscript.TRANSPORT_VECTORS).stream()
                .filter(t -> t.name().contains(kind)).toList();
    }

    /**
     * Replays a transport transcript: one host command whose answer is '6CXX' or '61XX' (wire exchanges 1 and 2),
     * then a further protected command (exchange 3); the strict card must have seen exactly the transcript.
     */
    private static void replay(SmartCardSession session, TranscriptCard card, ScpTranscript t) {
        GPSession gp = open(session, t);
        ScpTranscript.Command first = t.commands().get(0);
        ScpTranscript.Command completed = t.commands().get(1);
        ScpTranscript.Command next = t.commands().get(2);

        assertThat(Hex.encode(gp.transmit(first.plain()))).isEqualTo(expected(completed));
        assertThat(Hex.encode(gp.transmit(next.plain()))).isEqualTo(expected(next));
        assertThat(card.mismatches()).isEmpty();
        assertThat(card.completed()).isTrue();
    }

    /**
     * GPCS v2.3.1 E.4.5: the card generates an R-MAC for every response of the session, with '00' instead of the
     * response data for an error, and keeps it as the next ICV "regardless of whether the APDU command completed
     * successfully or not"; ISO/IEC 7816-4:2005 5.1.3 lets it answer an error with the status word alone.
     */
    @Nested
    class ErrorStatusWords {

        @Test
        void e_4_5_rmacSessionContinuesAfterACommandFailsWithABareErrorStatusWord() {
            ScpTranscript t = ScpTranscript.named("SCP02_i75_level11_reviewBareError6A88");
            TranscriptCard card = new TranscriptCard(t);
            GPSession gp = open(card, t);

            assertThatThrownBy(() -> gp.deleteAid("A000000001"))
                    .isInstanceOf(GPException.class)
                    .satisfies(e -> assertThat(((GPException) e).statusWord()).isEqualTo(0x6A88));
            APDUResponse next = gp.getData(0x00, 0x66);

            assertThat(gp.isOpen()).isTrue();
            assertThat(Hex.encode(next.data())).isEqualTo("6600");
            assertThat(card.mismatches()).isEmpty();
            assertThat(card.completed()).isTrue();
        }
    }

    /**
     * ISO/IEC 7816-4:2005 5.1.3: after '6CXX' "the same command may be re-issued using SW2 ... as short Le
     * field". The card has verified the C-MAC of the command it answered (GPCS v2.3.1 E.4.4: "a verified C-MAC
     * shall never be discarded"; Amendment D 6.2.4, 6.2.6: the MAC chaining value and the encryption counter
     * moved; SCP02 R-MAC: E.4.5), so the same bytes would fail the MAC check and abort the secure channel
     * (E.1.6). The command is protected again with the corrected Le; the C-MAC does not cover Le (E.4.4,
     * Amendment D 6.2.4).
     */
    @Nested
    class WrongLe {

        static List<ScpTranscript> cases() {
            return transport("_wrongLe");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void iso7816_5_1_3_commandAnswered6CxxIsProtectedAgainWithTheCorrectedLe(ScpTranscript t) {
            TranscriptCard card = new TranscriptCard(t);

            replay(card, card, t);
        }

        @Test
        void reissuedCommandCarriesANewCmacAndTheCorrectedLe() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6C10")
                    .thenAnswer("00112233445566778899AABBCCDDEEFF9000");
            GPSession gp = card.open();

            APDUResponse response = gp.send(0x80, 0xCA, 0x00, 0x66, null, 5);

            assertThat(Hex.encode(response.data())).isEqualTo("00112233445566778899AABBCCDDEEFF");
            assertThat(card.plainCommands()).containsExactly("80CA006605", "80CA006610");
            List<String> wire = card.wrappedCommands();
            assertThat(wire.get(1).substring(10, 26)).as("C-MAC of the re-issued command")
                    .isNotEqualTo(wire.get(0).substring(10, 26));
        }

        @Test
        void aSecondWrongLeAnswerIsReturnedToTheCaller() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6C10").thenAnswer("6C08");
            GPSession gp = card.open();

            APDUResponse response = gp.send(0x80, 0xCA, 0x00, 0x66, null, 5);

            assertThat(response.sw()).isEqualTo(0x6C08);
            assertThat(card.commandCount()).isEqualTo(2);
            assertThat(gp.isOpen()).isTrue();
        }
    }

    /**
     * ISO/IEC 7816-4:2005 5.1.3 and GPCS v2.3.1 11.1.5.2: '61XX' is completed with GET RESPONSE commands; "the
     * R-MAC (if requested) shall be computed over the non-segmented response data and shall be appended only to
     * the response of the last GET RESPONSE command", likewise response encryption. GET RESPONSE belongs to the
     * transmission, not to the secure channel: it is sent in plain (no C-MAC, no counter) with the class byte of
     * the protected command, and the reassembled response is unwrapped once.
     */
    @Nested
    class GetResponse {

        static List<ScpTranscript> cases() {
            return transport("_getResponse");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void gpcs_11_1_5_2_responseFetchedWithGetResponseIsUnwrappedAsAWhole(ScpTranscript t) {
            TranscriptCard card = new TranscriptCard(t);

            replay(card, card, t);
        }
    }

    /**
     * The same exchanges through {@link PcscSession}, whose {@code javax.smartcardio} provider hands '6CXX' and
     * '61XX' up unchanged, as the JDK's SunPCSC does with {@code sun.security.smartcardio.t0GetResponse=false} and
     * {@code t1GetResponse=false}. PcscSession re-sends nothing itself; with the default properties SunPCSC
     * re-sends after '6CXX' below the session, out of GPSession's reach (see the PcscSession javadoc).
     */
    @Nested
    class OverPcscSession {

        static List<ScpTranscript> cases() {
            return ScpTranscript.load(ScpTranscript.TRANSPORT_VECTORS);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("cases")
        void pcscSessionPassesTheStatusWordsUpAndGPSessionCompletesTheCommand(ScpTranscript t) {
            TranscriptCard card = new TranscriptCard(t);
            try (PcscSession pcsc = new PcscSession(new PassThroughPcscCard(card))) {
                replay(pcsc, card, t);
            }
        }
    }
}
