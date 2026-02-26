package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

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
}
