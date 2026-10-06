package name.velikodniy.jcexpress.pcsc;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.fakes.Transcripts;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static name.velikodniy.jcexpress.fakes.Transcripts.withoutTimes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PcscSession}.
 *
 * <p>{@link ContractCardTerminal} implements the documented {@code javax.smartcardio} contract (the same Card
 * for a reader while connected, IllegalStateException after disconnect, thread-bound exclusive access);
 * the simple mock cards check command encoding. No test here touches a physical reader
 * ({@link NoPhysicalReaderInTestsTest}); {@code PcscSession} on a real card is tested by the livecard module
 * ({@code PcscSessionLiveTest}, run only by {@code ./mvnw -Plivecard verify -pl livecard -am}).</p>
 */
class PcscSessionTest {

    private static final byte[] OK = {(byte) 0x90, 0x00};

    @Nested
    class Commands {

        @Test
        void installIsNotSupported() {
            var session = new PcscSession(new SimpleMockCard(OK));
            assertThatThrownBy(() -> session.install(Applet.class))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("GlobalPlatform");
            assertThatThrownBy(() -> session.install(Applet.class, AID.fromHex("A000000001")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> session.install(Applet.class, AID.fromHex("A000000001"), new byte[0]))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void deleteIsNotSupported() {
            var session = new PcscSession(new SimpleMockCard(OK));
            assertThatThrownBy(() -> session.delete(AID.fromHex("A000000001")))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("GPSession.deleteAid");
        }

        @Test
        void sendReturnsDataAndStatusWord() {
            var session = new PcscSession(new SimpleMockCard(Hex.decode("01029000")));
            APDUResponse response = session.send(0x80, 0x01);
            assertThat(response.sw()).isEqualTo(0x9000);
            assertThat(response.data()).containsExactly(0x01, 0x02);
        }

        @Test
        void sendEncodesWithTheLeContract() {
            var card = new CapturingMockCard();
            var session = new PcscSession(card);

            session.send(0x80, 0x02, 0x00, 0x00, new byte[]{0x01, 0x02, 0x03});
            assertThat(Hex.encodeSpaced(card.lastCommand.getBytes())).isEqualTo("80 02 00 00 03 01 02 03");
            session.send(0x80, 0xCA, 0x00, 0x66, null, 256);
            assertThat(Hex.encodeSpaced(card.lastCommand.getBytes())).isEqualTo("80 CA 00 66 00");
        }

        @Test
        void transmitReturnsRawBytes() {
            var session = new PcscSession(new SimpleMockCard(Hex.decode("0A0B9000")));
            assertThat(session.transmit(Hex.decode("00A40400"))).containsExactly(0x0A, 0x0B, 0x90, 0x00);
        }

        @Test
        void transmitRejectsBytesThatAreNotACommandApdu() {
            var session = new PcscSession(new SimpleMockCard(OK));
            assertThatThrownBy(() -> session.transmit(new byte[]{0x00, (byte) 0xA4}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** GlobalPlatform Card Specification 2.3.1 Table 11-79: SELECT [by name] carries Le '00'. */
        @Test
        void selectSendsSelectByNameWithLe00() {
            var card = new CapturingMockCard();
            var session = new PcscSession(card);

            session.select(AID.fromHex("A00000006207010101"));

            assertThat(Hex.encodeSpaced(card.lastCommand.getBytes()))
                    .isEqualTo("00 A4 04 00 09 A0 00 00 00 62 07 01 01 01 00");
        }

        @Test
        void selectFailureThrowsSelectExceptionWithTheStatusWord() {
            var session = new PcscSession(new SimpleMockCard(Hex.decode("6A82")));
            assertThatThrownBy(() -> session.select(AID.fromHex("A000000001")))
                    .isInstanceOfSatisfying(SelectException.class, e -> {
                        assertThat(e.sw()).isEqualTo(0x6A82);
                        assertThat(e.aid()).isEqualTo(AID.fromHex("A000000001"));
                    })
                    .hasMessageContaining("6A82");
        }

        @Test
        void cardExceptionIsReportedAsPcscException() {
            var session = new PcscSession(new FailingMockCard());
            assertThatThrownBy(() -> session.send(0x80, 0x01))
                    .isInstanceOf(PcscException.class)
                    .hasMessageContaining("Transmit failed")
                    .hasCauseInstanceOf(CardException.class);
        }

        @Test
        void atrAndProtocolOfTheConnection() {
            var session = new PcscSession(new SimpleMockCard(OK));
            assertThat(session.getATR()).containsExactly(0x3B, 0xF0, 0x11, 0x00, 0xFF, 0x01);
            assertThat(session.getProtocol()).isEqualTo("T=1");
        }

        @Test
        void loggingDecoratorRecordsTheExchange() {
            var logged = new PcscSession(new SimpleMockCard(OK)).logged();
            assertThat(logged.send(0x80, 0x01, 0x00, 0x00, new byte[]{0x01}).isSuccess()).isTrue();
            assertThat(logged.entries()).hasSize(1);
        }
    }

    @Nested
    class Lifecycle {

        /** SmartCardSession.reset(): "equivalent to removing and reinserting the card" - the session stays usable. */
        @Test
        void resetResetsTheCardAndReconnects() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader);
            ContractCardTerminal.FakeCard before = reader.currentCard();

            card.reset();

            assertThat(reader.resets()).isEqualTo(1);
            assertThat(reader.connects()).isEqualTo(2);
            assertThat(before.isDisposed()).isTrue();
            assertThat(card.send(0x00, 0xB0, 0x00, 0x00).sw()).isEqualTo(0x9000);
            assertThat(card.getATR()).isNotEmpty();
        }

        /**
         * Card.disconnect(true) asks PC/SC for SCARD_RESET_CARD, but while the connection holds a transaction
         * (Card.beginExclusive) macOS PC/SC does not reset the card (observed on a JCOP 4 card: the applet stayed
         * selected, CLEAR_ON_RESET memory kept its value). The session ends exclusive access first.
         */
        @Test
        void resetEndsExclusiveAccessBeforeTheResettingDisconnect() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader);

            card.reset();

            assertThat(reader.events()).containsExactly("connect", "beginExclusive", "endExclusive",
                    "disconnect(reset)", "connect", "beginExclusive");
            assertThat(reader.resets()).isEqualTo(1);
        }

        @Test
        void resetOfASharedSessionDisconnectsWithReset() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader, PcscSession.Options.defaults().shared());

            card.reset();

            assertThat(reader.events()).containsExactly("connect", "disconnect(reset)", "connect");
            assertThat(reader.resets()).isEqualTo(1);
        }

        /** SunPCSC ends the transaction even when SCardEndTransaction fails; the reset goes on. */
        @Test
        void resetGoesOnWhenEndingExclusiveAccessFails() {
            ContractCardTerminal reader = new ContractCardTerminal().failingEndExclusive();
            PcscSession card = PcscSession.open(reader);

            card.reset();

            assertThat(reader.resets()).isEqualTo(1);
            assertThat(reader.currentCard().isExclusive()).isTrue();
            assertThat(card.send(0x00, 0xB0, 0x00, 0x00).sw()).isEqualTo(0x9000);
        }

        @Test
        void resetKeepsExclusiveAccess() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader);

            card.reset();

            assertThat(reader.currentCard().isExclusive()).isTrue();
        }

        @Test
        void resetIsNotSupportedForASessionWrappingACallerSuppliedCard() {
            var session = new PcscSession(new SimpleMockCard(OK));

            assertThatThrownBy(session::reset)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("PcscSession.open");
            assertThat(session.send(0x80, 0x01).sw()).isEqualTo(0x9000);
        }

        @Test
        void closeDisconnectsWithoutResetOnce() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader);

            card.close();
            card.close();

            assertThat(reader.currentCard().isDisposed()).isTrue();
            assertThat(reader.resets()).isZero();
        }

        @Test
        void aClosedSessionCannotBeUsed() {
            PcscSession card = PcscSession.open(new ContractCardTerminal());
            card.close();

            assertThatThrownBy(() -> card.send(0x00, 0xB0, 0x00, 0x00)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(card::reset).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> card.select(AID.fromHex("A000000001"))).isInstanceOf(IllegalStateException.class);
        }

        /** javax.smartcardio throws IllegalStateException for a disposed Card; callers expect PcscException. */
        @Test
        void aConnectionLostOutsideTheSessionIsReportedAsPcscException() throws CardException {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader, PcscSession.Options.defaults().shared());
            reader.currentCard().disconnect(false);

            assertThatThrownBy(() -> card.send(0x00, 0xB0, 0x00, 0x00))
                    .isInstanceOf(PcscException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class ExclusiveAccess {

        @Test
        void sessionsHoldExclusiveAccessByDefault() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader);

            assertThat(reader.currentCard().isExclusive()).isTrue();
            card.close();
        }

        @Test
        void sharedAccessCanBeRequested() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession card = PcscSession.open(reader, PcscSession.Options.defaults().shared());

            assertThat(reader.currentCard().isExclusive()).isFalse();
            assertThat(card.send(0x00, 0xB0, 0x00, 0x00).sw()).isEqualTo(0x9000);
        }

        /**
         * CardTerminal.connect "returns the same Card object" while a connection exists, so a second session on
         * the same reader would share (and close) the first session's connection: it is refused instead.
         */
        @Test
        void aSecondSessionOnTheSameReaderIsRefused() {
            ContractCardTerminal reader = new ContractCardTerminal();
            PcscSession first = PcscSession.open(reader, PcscSession.Options.defaults().shared());

            assertThatThrownBy(() -> PcscSession.open(reader, PcscSession.Options.defaults().shared()))
                    .isInstanceOf(PcscException.class)
                    .hasMessageContaining("already used by another PcscSession");
            assertThat(first.send(0x00, 0xB0, 0x00, 0x00).sw()).isEqualTo(0x9000);

            first.close();
            assertThatCode(() -> PcscSession.open(reader).close()).doesNotThrowAnyException();
        }

        @Test
        void anotherThreadGetsAClearErrorWhileExclusiveAccessIsHeld() throws InterruptedException {
            PcscSession card = PcscSession.open(new ContractCardTerminal());
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            Thread other = new Thread(() -> {
                try {
                    card.send(0x00, 0xB0, 0x00, 0x00);
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }, "other-thread");
            other.start();
            other.join();

            assertThat(thrown.get())
                    .isInstanceOf(PcscException.class)
                    .hasMessageContaining(Thread.currentThread().getName())
                    .hasMessageContaining("shared()");
            assertThat(card.send(0x00, 0xB0, 0x00, 0x00).sw()).isEqualTo(0x9000);
        }

        @Test
        void optionsValidateTheProtocol() {
            assertThat(PcscSession.Options.defaults().protocol()).isEqualTo("*");
            assertThat(PcscSession.Options.defaults().exclusive()).isTrue();
            assertThat(PcscSession.Options.defaults().withProtocol("T=0").protocol()).isEqualTo("T=0");
            assertThatThrownBy(() -> PcscSession.Options.defaults().withProtocol(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ReaderSelection {

        @Test
        void opensTheFirstReaderWithACard() {
            ContractCardTerminal empty = new ContractCardTerminal().named("Reader A").cardPresent(false);
            ContractCardTerminal full = new ContractCardTerminal().named("Reader B");

            PcscSession card = PcscSession.open(new FakeTerminals(empty, full), null, PcscSession.Options.defaults());

            assertThat(full.connects()).isEqualTo(1);
            assertThat(empty.connects()).isZero();
            card.close();
        }

        @Test
        void filtersReadersByName() {
            ContractCardTerminal first = new ContractCardTerminal().named("ACS ACR39U");
            ContractCardTerminal second = new ContractCardTerminal().named("Identiv uTrust 3700 F");

            PcscSession.open(new FakeTerminals(first, second), "uTrust", PcscSession.Options.defaults()).close();

            assertThat(first.connects()).isZero();
            assertThat(second.connects()).isEqualTo(1);
        }

        @Test
        void reportsMissingReadersAndCards() {
            PcscSession.Options options = PcscSession.Options.defaults();
            assertThatThrownBy(() -> PcscSession.open(new FakeTerminals(), null, options))
                    .isInstanceOf(PcscException.class)
                    .hasMessageContaining("No PC/SC card readers");
            FakeTerminals noCard = new FakeTerminals(new ContractCardTerminal().cardPresent(false));
            assertThatThrownBy(() -> PcscSession.open(noCard, "Contract", options))
                    .isInstanceOf(PcscException.class)
                    .hasMessageContaining("Contract");
        }
    }

    /**
     * T=0: case 2 and 4 responses are fetched with GET RESPONSE (ISO/IEC 7816-4:2005 5.1.3). The SunPCSC
     * provider does that itself; a provider that returns 61XX gets it passed through unchanged, and
     * {@link APDUSequence} completes it on the same class byte. The session itself never re-sends a command
     * after '6CXX' (for a command protected by a secure channel the card has consumed the C-MAC).
     */
    @Nested
    class TransportT0 {

        private ContractCardTerminal t0Card() {
            return new ContractCardTerminal("T=0", (channel, apdu) -> switch (apdu[1]) {
                case (byte) 0xCA -> Hex.decode("6105");
                case (byte) 0xC0 -> Hex.decode("01020304059000");
                default -> Hex.decode("6D00");
            });
        }

        @Test
        void statusWord61XXIsReturnedUnchanged() {
            PcscSession card = PcscSession.open(t0Card());

            APDUResponse r = card.send(0x80, 0xCA, 0x00, 0x66, null, 256);

            assertThat(r.sw()).isEqualTo(0x6105);
            assertThat(card.getProtocol()).isEqualTo("T=0");
        }

        @Test
        void statusWord6CXXIsReturnedUnchangedAndNothingIsResent() {
            ContractCardTerminal reader = new ContractCardTerminal("T=1", (channel, apdu) -> Hex.decode("6C10"));
            PcscSession card = PcscSession.open(reader);

            APDUResponse r = card.send(0x84, 0xCA, 0x00, 0x66, Hex.decode("1122334455667788"), 5);

            assertThat(r.sw()).isEqualTo(0x6C10);
            assertThat(reader.wire).hasSize(1);
        }

        @Test
        void apduSequenceFetchesTheRemainingBytesWithTheSameClass() {
            ContractCardTerminal reader = t0Card();
            PcscSession card = PcscSession.open(reader);

            APDUResponse r = APDUSequence.on(card).send(0x80, 0xCA, 0x00, 0x66, null, 256);

            assertThat(r.sw()).isEqualTo(0x9000);
            assertThat(r.data()).containsExactly(1, 2, 3, 4, 5);
            assertThat(Hex.encodeSpaced(reader.wire.get(1))).isEqualTo("80 C0 00 00 05");
        }
    }

    // === Fakes ===

    /** CardTerminals listing fixed terminals. */
    private static final class FakeTerminals extends CardTerminals {
        private final List<CardTerminal> terminals;

        FakeTerminals(CardTerminal... terminals) {
            this.terminals = List.of(terminals);
        }

        @Override
        public List<CardTerminal> list(State state) {
            return terminals;
        }

        @Override
        public boolean waitForChange(long timeout) {
            return false;
        }
    }

    /** Card answering every command with a fixed response. */
    private static class SimpleMockCard extends Card {
        private final byte[] response;

        SimpleMockCard(byte[] response) {
            this.response = response;
        }

        ResponseAPDU respond(CommandAPDU command) throws CardException {
            return new ResponseAPDU(response);
        }

        @Override public ATR getATR() { return new ATR(new byte[]{0x3B, (byte) 0xF0, 0x11, 0x00, (byte) 0xFF, 0x01}); }
        @Override public String getProtocol() { return "T=1"; }
        @Override public CardChannel getBasicChannel() { return new MockChannel(this); }
        @Override public CardChannel openLogicalChannel() { throw new UnsupportedOperationException(); }
        @Override public void beginExclusive() { }
        @Override public void endExclusive() { }
        @Override public byte[] transmitControlCommand(int controlCode, byte[] command) { return new byte[0]; }
        @Override public void disconnect(boolean reset) { }
    }

    /** Card that records the last command. */
    private static final class CapturingMockCard extends SimpleMockCard {
        CommandAPDU lastCommand;

        CapturingMockCard() {
            super(OK);
        }

        @Override
        ResponseAPDU respond(CommandAPDU command) {
            lastCommand = command;
            return new ResponseAPDU(OK);
        }
    }

    /** Card whose transmit fails. */
    private static final class FailingMockCard extends SimpleMockCard {
        FailingMockCard() {
            super(OK);
        }

        @Override
        ResponseAPDU respond(CommandAPDU command) throws CardException {
            throw new CardException("Mock failure");
        }
    }

    private static final class MockChannel extends CardChannel {
        private final SimpleMockCard card;

        MockChannel(SimpleMockCard card) {
            this.card = card;
        }

        @Override public Card getCard() { return card; }
        @Override public int getChannelNumber() { return 0; }
        @Override public ResponseAPDU transmit(CommandAPDU command) throws CardException { return card.respond(command); }
        @Override public int transmit(ByteBuffer command, ByteBuffer response) { throw new UnsupportedOperationException(); }
        @Override public void close() { }
    }

    /** The session records what it sends and receives, SELECT included, and notes card resets. */
    @Nested
    class History {

        @Test
        void commandsSelectsAndResetsAreRecorded() {
            ContractCardTerminal reader = new ContractCardTerminal("T=1",
                    (channel, apdu) -> apdu[1] == (byte) 0xA4 ? Hex.decode("9000") : Hex.decode("01029000"));

            try (PcscSession card = PcscSession.open(reader, PcscSession.Options.defaults())) {
                card.select(AID.fromHex("A000000151000000"));
                card.send(0x80, 0xCA, 0x00, 0x66, null, 256);
                card.reset();
                card.transmit(Hex.decode("80CA9F7F00"));

                String transcript = card.history().transcript();
                assertThat(withoutTimes(transcript)).isEqualTo("""
                        C: 00A4040008A00000015100000000
                        R: 9000
                        C: 80CA006600
                        R: 01029000
                        # card reset
                        C: 80CA9F7F00
                        R: 01029000
                        """);
                assertThat(transcript.lines().filter(line -> line.startsWith("R: ")))
                        .hasSize(3).allSatisfy(line -> assertThat(line).matches(Transcripts.TIMED_RESPONSE));
            }
        }
    }
}
