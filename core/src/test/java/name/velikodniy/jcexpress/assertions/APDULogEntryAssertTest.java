package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SW;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThatThrownBy;

/**
 * Assertions on a recorded exchange: how long it took, and its status word.
 */
class APDULogEntryAssertTest {

    private static final byte[] COMMAND = Hex.decode("8052000002");

    private static APDULogEntry entry(int sw, Duration duration) {
        return new APDULogEntry(COMMAND, new APDUResponse(Hex.decode("0064"), sw), 1L, duration);
    }

    @Test
    void anExchangeWithinTheLimitPasses() {
        assertThat(entry(0x9000, Duration.ofMillis(12))).tookAtMost(Duration.ofMillis(12))
                .tookAtMost(Duration.ofMillis(50)).isSuccess().hasStatusWord(SW.NO_ERROR);
    }

    @Test
    void aSlowExchangeFailsWithTheExchangeAndBothTimes() {
        assertThatThrownBy(() -> assertThat(entry(0x9000, Duration.ofNanos(73_400_000)))
                .tookAtMost(Duration.ofMillis(50)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("at most 50.0 ms")
                .hasMessageContaining("took 73.4 ms")
                .hasMessageContaining("C: 8052000002")
                .hasMessageContaining("R: 00649000  (73.4 ms)");
    }

    @Test
    void anExchangeWithoutAMeasuredTimeFailsTheTimeCheckWithTheReason() {
        assertThatThrownBy(() -> assertThat(entry(0x9000, null)).tookAtMost(Duration.ofSeconds(1)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was not measured")
                .hasMessageContaining("C: 8052000002");
    }

    @Test
    void theStatusWordChecksNameTheExchange() {
        assertThatThrownBy(() -> assertThat(entry(0x6982, Duration.ofMillis(3))).isSuccess())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("6982 (security status not satisfied)")
                .hasMessageContaining("C: 8052000002");
        assertThatThrownBy(() -> assertThat(entry(0x9000, Duration.ofMillis(3))).hasStatusWord(0x6985))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("6985")
                .hasMessageContaining("9000 (success)");
    }
}
