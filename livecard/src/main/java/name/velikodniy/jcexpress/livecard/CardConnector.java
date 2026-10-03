package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.SmartCardSession;

/**
 * Finds and connects the card a live-card run talks to. {@link PcscConnector} is the real one; tests of this
 * module plug in a simulated card through the JUnit configuration parameter {@code jcx.livecard.connector}.
 */
public interface CardConnector {

    /**
     * Looks for a card without connecting to it and without sending any command.
     *
     * @param config the run's settings
     * @return whether a card is present, and a description (reader name or why not)
     */
    Presence probe(LiveCardConfig config);

    /**
     * Connects to the card with exclusive access.
     *
     * @param config the run's settings
     * @return the connection
     * @throws LiveCardException if no card is available or the connection fails
     */
    Connection connect(LiveCardConfig config);

    /**
     * Result of {@link #probe(LiveCardConfig)}.
     *
     * @param present     whether a card is present
     * @param description the reader with the card, or why there is none
     */
    record Presence(boolean present, String description) {
    }

    /**
     * An open connection to a card.
     *
     * @param transport the unguarded session that transmits commands ({@link LiveCard} puts the guard in front)
     * @param reader    the reader name
     * @param atr       the Answer To Reset, hex
     * @param protocol  the transmission protocol, e.g. {@code T=1}
     */
    record Connection(SmartCardSession transport, String reader, String atr, String protocol) {
    }
}
