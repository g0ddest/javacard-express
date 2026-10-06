package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link APDULogEntry}.
 */
class APDULogEntryTest {

    @Test
    void shouldStoreCommandAndResponse() {
        byte[] command = Hex.decode("00A40400");
        APDUResponse response = new APDUResponse(Hex.decode("48656C6C6F"), 0x9000);

        APDULogEntry entry = new APDULogEntry(command, response, 12345L);

        assertThat(entry.command()).isEqualTo(command);
        assertThat(entry.response()).isEqualTo(response);
        assertThat(entry.timestampMs()).isEqualTo(12345L);
        assertThat(entry.isSuccess()).isTrue();
    }

    @Test
    void insAndClaShouldExtractFromCommand() {
        byte[] command = Hex.decode("80CA00CF");
        APDUResponse response = new APDUResponse(new byte[0], 0x9000);

        APDULogEntry entry = new APDULogEntry(command, response, 0L);

        assertThat(entry.cla()).isEqualTo(0x80);
        assertThat(entry.ins()).isEqualTo(0xCA);
    }

    @Test
    void toStringShouldContainHex() {
        byte[] command = Hex.decode("00A40400");
        APDUResponse response = new APDUResponse(new byte[0], 0x6A82);

        APDULogEntry entry = new APDULogEntry(command, response, 0L);

        assertThat(entry.toString()).contains("00 A4 04 00");
        assertThat(entry.toString()).contains("6A82");
        assertThat(entry.isSuccess()).isFalse();
    }

    @Test
    void anEntryCreatedWithoutADurationHasNone() {
        APDULogEntry entry = new APDULogEntry(Hex.decode("80010000"), new APDUResponse(new byte[0], 0x9000), 7L);

        assertThat(entry.duration()).isNull();
        assertThat(entry.toString()).doesNotContain("ms");
    }

    @Test
    void anEntryKeepsTheDurationOfItsExchange() {
        APDULogEntry entry = new APDULogEntry(Hex.decode("80010000"), new APDUResponse(new byte[0], 0x9000), 7L,
                Duration.ofMillis(12).plusNanos(300_000));

        assertThat(entry.timestampMs()).isEqualTo(7L);
        assertThat(entry.duration()).isEqualTo(Duration.ofNanos(12_300_000));
        assertThat(entry.toString()).endsWith("[9000] (12.3 ms)");
    }

    @Test
    void theTranscriptOfAnEntryShowsTheExchangeAndItsTime() {
        APDULogEntry timed = new APDULogEntry(Hex.decode("8052000002"), new APDUResponse(Hex.decode("0064"), 0x9000),
                7L, Duration.ofNanos(12_300_000));
        APDULogEntry untimed = new APDULogEntry(Hex.decode("8052000002"), new APDUResponse(Hex.decode("0064"), 0x9000),
                7L);

        assertThat(timed.transcript()).isEqualTo("C: 8052000002\nR: 00649000  (12.3 ms)");
        assertThat(untimed.transcript()).isEqualTo("C: 8052000002\nR: 00649000");
    }
}
