package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.CardConnector;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A connector that must never be used: it counts every call and fails it. Tests that expect live-card tests to
 * be skipped plug it in through {@link LiveCardExtension#CONNECTOR_PARAMETER}.
 */
public final class TripwireConnector implements CardConnector {

    /** Number of calls since the last {@link #reset()}. */
    static final AtomicInteger CALLS = new AtomicInteger();

    /**
     * Creates the connector (JUnit instantiates it by class name).
     */
    public TripwireConnector() {
        // stateless
    }

    static void reset() {
        CALLS.set(0);
    }

    @Override
    public Presence probe(LiveCardConfig config) {
        CALLS.incrementAndGet();
        throw new AssertionError("a skipped live-card test looked for a card");
    }

    @Override
    public Connection connect(LiveCardConfig config) {
        CALLS.incrementAndGet();
        throw new AssertionError("a skipped live-card test connected to a card");
    }
}
