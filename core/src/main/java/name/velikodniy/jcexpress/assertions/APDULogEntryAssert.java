package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.TranscriptFormat;
import org.assertj.core.api.AbstractAssert;

import java.time.Duration;
import java.util.Objects;

/**
 * Assertions on a recorded APDU exchange ({@link APDULogEntry}), such as the newest exchange of a session's history:
 * how long it took and its status word. Failure messages show the exchange in the transcript format.
 *
 * <pre>
 * card.send(GET_BALANCE);
 * assertThat(card.history().last()).isSuccess().tookAtMost(Duration.ofMillis(50));
 * </pre>
 *
 * <p>The time is what the session measured around the exchange (see {@link APDULogEntry}): on a card in a reader it
 * includes the driver and the reader, on jCardSim it is the simulator's time.</p>
 */
public class APDULogEntryAssert extends AbstractAssert<APDULogEntryAssert, APDULogEntry> {

    /**
     * Creates an assertion for a recorded exchange.
     *
     * @param actual the exchange to assert on
     */
    public APDULogEntryAssert(APDULogEntry actual) {
        super(actual, APDULogEntryAssert.class);
    }

    /**
     * Verifies that the exchange took at most the given time.
     *
     * @param max the longest acceptable duration
     * @return this assertion for chaining
     * @throws NullPointerException if {@code max} is null
     */
    public APDULogEntryAssert tookAtMost(Duration max) {
        Objects.requireNonNull(max, "max");
        isNotNull();
        Duration took = actual.duration();
        if (took == null) {
            failWithMessage("Expected the exchange to take at most %s, but its time was not measured (the session that"
                    + " recorded it does not measure time):%n%s", TranscriptFormat.milliseconds(max), exchange());
        } else if (took.compareTo(max) > 0) {
            failWithMessage("Expected the exchange to take at most %s, but it took %s:%n%s",
                    TranscriptFormat.milliseconds(max), TranscriptFormat.milliseconds(took), exchange());
        }
        return this;
    }

    /**
     * Verifies that the response of the exchange has the status word {@code 9000}.
     *
     * @return this assertion for chaining
     */
    public APDULogEntryAssert isSuccess() {
        isNotNull();
        response().isSuccess();
        return this;
    }

    /**
     * Verifies the status word of the response, as {@link APDUResponseAssert#hasStatusWord(int)} does.
     *
     * @param expectedSw the expected status word (an int, an {@link name.velikodniy.jcexpress.SW} constant or a
     *                   Java Card {@code short} constant)
     * @return this assertion for chaining
     */
    public APDULogEntryAssert hasStatusWord(int expectedSw) {
        isNotNull();
        response().hasStatusWord(expectedSw);
        return this;
    }

    private APDUResponseAssert response() {
        return new APDUResponseAssert(actual.response()).as("%s", exchange());
    }

    private String exchange() {
        return actual.transcript();
    }
}
