package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static name.velikodniy.jcexpress.fakes.Transcripts.TIMED_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sessions time their exchanges: the history and the log entries carry when the command was sent and how long
 * the exchange took, and the transcripts show the time on the response lines.
 */
class SessionTimingTest {

    @Test
    void theEmbeddedSessionTimesEveryExchange() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(HelloWorldApplet.class);
            long before = System.currentTimeMillis();

            card.send(0x80, 0x01);

            APDULogEntry entry = card.history().last();
            assertThat(entry.ins()).isEqualTo(0x01);
            assertThat(entry.timestampMs()).isBetween(before, System.currentTimeMillis());
            assertThat(entry.duration()).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
            assertThat(card.history().entries()).allSatisfy(exchange -> assertThat(exchange.duration()).isNotNull());
            assertThat(card.history().transcript().lines().filter(line -> line.startsWith("R: ")))
                    .isNotEmpty().allSatisfy(line -> assertThat(line).matches(TIMED_RESPONSE));
        }
    }

    @Test
    void thePcscSessionTimesTheTransmissionFromBeforeTheCommandIsSent() {
        AtomicLong cardReceived = new AtomicLong();
        ContractCardTerminal reader = new ContractCardTerminal("T=1", (channel, apdu) -> {
            cardReceived.set(System.currentTimeMillis());
            pause(20);
            return Hex.decode("01029000");
        });

        try (PcscSession card = PcscSession.open(reader, PcscSession.Options.defaults())) {
            card.send(0x80, 0xCA, 0x00, 0x66, null, 256);

            APDULogEntry entry = card.history().last();
            assertThat(entry.timestampMs()).isLessThanOrEqualTo(cardReceived.get());
            assertThat(entry.duration()).isGreaterThanOrEqualTo(Duration.ofMillis(20));
            assertThat(card.history().transcript()).containsPattern(TIMED_RESPONSE);
        }
    }

    @Test
    void aLoggingSessionTimesItsOwnEntriesAndKeepsTheTimesOfTheWrappedSession() {
        try (EmbeddedSession embedded = new EmbeddedSession()) {
            LoggingSession card = LoggingSession.wrap(embedded);
            card.install(HelloWorldApplet.class);
            long before = System.currentTimeMillis();

            card.send(0x80, 0x01);

            assertThat(card.entries()).isNotEmpty()
                    .allSatisfy(entry -> assertThat(entry.duration()).isNotNull());
            assertThat(card.lastEntry().timestampMs()).isBetween(before, System.currentTimeMillis());
            assertThat(card.dump().lines().filter(line -> line.startsWith("R: ")))
                    .isNotEmpty().allSatisfy(line -> assertThat(line).matches(TIMED_RESPONSE));
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
