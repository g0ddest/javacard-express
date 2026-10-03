package name.velikodniy.jcexpress.pcsc;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUHistory;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.apdu.APDUSequence;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.TerminalFactory;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Smart card session backed by a PC/SC card reader ({@code javax.smartcardio}).
 *
 * <p>Applet installation is not supported: load CAP files onto real cards with the GlobalPlatform
 * module.</p>
 *
 * <h2>Connection</h2>
 * <ul>
 *   <li><b>Exclusive access</b> (default): the session calls {@link Card#beginExclusive()} after connecting,
 *       so no other PC/SC client (another process, middleware, OS smart card services) can send commands
 *       between two commands of the session, e.g. a SELECT in the middle of a secure channel. The
 *       {@code javax.smartcardio} API ties exclusive access to the thread that requested it: use the session
 *       from the thread that opened it, or open it with {@link Options#shared()}.</li>
 *   <li><b>One session per reader</b>: {@link CardTerminal#connect(String)} returns the same {@link Card}
 *       for a reader while a connection exists, so two sessions would share one connection and closing one
 *       would close the other. Opening a second session on a reader that an open session uses fails with
 *       {@link PcscException}.</li>
 *   <li>{@link #reset()} resets the card ({@code Card.disconnect(true)}) and connects again through the same
 *       reader; the session stays usable. Logical channels are closed by the reset. Exclusive access is
 *       ended first: while a PC/SC transaction is open, {@code SCardDisconnect(SCARD_RESET_CARD)} does not
 *       reset the card on every platform (on macOS the card keeps its state and its selected applet).</li>
 *   <li><b>Logical channels</b>: a command goes to the channel coded in its CLA (ISO/IEC 7816-4:2005 5.1.1).
 *       MANAGE CHANNEL OPEN (P1-P2 = '0000', as sent by
 *       {@link name.velikodniy.jcexpress.LogicalChannel#open(SmartCardSession)}) and MANAGE CHANNEL CLOSE are
 *       performed through {@code Card.openLogicalChannel()} and {@code CardChannel.close()}, because
 *       {@code javax.smartcardio} does not transmit MANAGE CHANNEL itself. Channels that were not opened this
 *       way, and opening a given channel number, are not supported ({@link UnsupportedOperationException});
 *       a card that refuses to open a channel is reported as {@link PcscException}.</li>
 *   <li>{@link #close()} disconnects without resetting the card. Afterwards every operation throws
 *       {@link IllegalStateException}.</li>
 *   <li>Failures of the reader or the connection are reported as {@link PcscException}; a failed SELECT as
 *       {@link SelectException}.</li>
 * </ul>
 *
 * <h2>Transport (T=0 and T=1)</h2>
 * <p>Commands are transmitted with {@code CardChannel.transmit}. With T=0 the response of a case 2 or 4 command is
 * fetched with GET RESPONSE after '61XX', and a command is re-sent after '6CXX' with the corrected Le (ISO/IEC
 * 7816-4:2005 5.1.3). The JDK provider (SunPCSC) does both itself for T=0 and T=1 (system properties
 * {@code sun.security.smartcardio.t0GetResponse} and {@code t1GetResponse}, {@code true} by default, read once per
 * JVM), so {@link #send(int, int, int, int, byte[], int)} returns the complete response; with a provider that does
 * not, '61XX' and '6CXX' reach the caller unchanged (this session re-sends nothing) and {@link APDUSequence}
 * completes them. Re-sent bytes are wrong for a command protected by a secure channel, whose C-MAC the card has
 * verified (GPCS v2.3.1 E.4.4): start the JVM with both properties {@code false} to let {@code GPSession} protect it
 * again. SunPCSC rejects extended length commands over T=0 (ISO/IEC 7816-3), reported as {@link PcscException}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // First reader with a card, exclusive access
 * try (var card = PcscSession.open()) {
 *     card.select(AID.fromHex("A00000006207010101"));
 *     APDUResponse r = card.send(0x80, 0x40, 0x00, 0x04);
 * }
 *
 * // Reader by name, T=1, shared access
 * try (var card = PcscSession.open("ACS ACR122U", PcscSession.Options.defaults().withProtocol("T=1").shared())) {
 *     APDUResponse r = card.send(0x80, 0xCA, 0x00, 0x66, null, 256);
 * }
 * }</pre>
 *
 * @see SmartCardSession
 */
public class PcscSession implements SmartCardSession {

    /** Connections used by open sessions of this JVM (javax.smartcardio hands out one Card per reader). */
    private static final Set<Card> CARDS_IN_USE = Collections.newSetFromMap(new IdentityHashMap<>());

    private final CardTerminal terminal;
    private final Options options;
    private final APDUHistory history = new APDUHistory();
    private Card card;
    private PcscChannels channels;
    private volatile Thread exclusiveOwner;
    private boolean closed;

    /**
     * Connection options.
     *
     * @param protocol  the protocol passed to {@link CardTerminal#connect(String)}: {@code "T=0"},
     *                  {@code "T=1"} or {@code "*"} (any)
     * @param exclusive whether the session holds exclusive access to the card ({@link Card#beginExclusive()})
     *                  for its lifetime
     */
    public record Options(String protocol, boolean exclusive) {

        /**
         * Validates the options.
         *
         * @throws IllegalArgumentException if the protocol is null or blank
         */
        public Options {
            if (protocol == null || protocol.isBlank()) {
                throw new IllegalArgumentException("Protocol must be \"T=0\", \"T=1\" or \"*\", got: " + protocol);
            }
        }

        /**
         * Returns the default options: any protocol, exclusive access.
         *
         * @return {@code Options("*", true)}
         */
        public static Options defaults() {
            return new Options("*", true);
        }

        /**
         * Returns these options with another protocol.
         *
         * @param newProtocol {@code "T=0"}, {@code "T=1"} or {@code "*"}
         * @return new options
         */
        public Options withProtocol(String newProtocol) {
            return new Options(newProtocol, exclusive);
        }

        /**
         * Returns these options with or without exclusive access.
         *
         * @param exclusiveAccess whether to hold exclusive access
         * @return new options
         */
        public Options withExclusiveAccess(boolean exclusiveAccess) {
            return new Options(protocol, exclusiveAccess);
        }

        /**
         * Returns these options with shared access: other PC/SC clients may send commands between the
         * commands of the session, and the session may be used from any thread.
         *
         * @return new options
         */
        public Options shared() {
            return withExclusiveAccess(false);
        }
    }

    /**
     * Creates a session on a connection the caller established. The session does not request exclusive
     * access, cannot {@link #reset()} the card (there is no reader to connect again to) and disconnects the
     * card on {@link #close()}. Prefer {@link #open(CardTerminal, Options)}.
     *
     * @param card the connected smart card
     * @throws PcscException if another open session uses this card
     */
    public PcscSession(Card card) {
        this.terminal = null;
        this.options = new Options(card.getProtocol(), false);
        register(card, "the given Card");
        this.card = card;
        this.channels = new PcscChannels(card);
    }

    private PcscSession(CardTerminal terminal, Options options) {
        this.terminal = terminal;
        this.options = options;
    }

    /**
     * Opens a session to the first reader with a card present, with {@link Options#defaults()}.
     *
     * @return a new PC/SC session
     * @throws PcscException if no reader or card is available or the connection fails
     */
    public static PcscSession open() {
        return open((String) null, Options.defaults());
    }

    /**
     * Opens a session to the first reader with a card present.
     *
     * @param options connection options
     * @return a new PC/SC session
     * @throws PcscException if no reader or card is available or the connection fails
     */
    public static PcscSession open(Options options) {
        return open((String) null, options);
    }

    /**
     * Opens a session to the first reader whose name contains the given text and has a card present, with
     * {@link Options#defaults()}.
     *
     * @param readerNameFilter substring to match against reader names (null = any reader)
     * @return a new PC/SC session
     * @throws PcscException if no matching reader or card is available or the connection fails
     */
    public static PcscSession open(String readerNameFilter) {
        return open(readerNameFilter, Options.defaults());
    }

    /**
     * Opens a session to the first reader whose name contains the given text and has a card present.
     *
     * @param readerNameFilter substring to match against reader names (null = any reader)
     * @param options          connection options
     * @return a new PC/SC session
     * @throws PcscException if no matching reader or card is available or the connection fails
     */
    public static PcscSession open(String readerNameFilter, Options options) {
        return open(TerminalFactory.getDefault().terminals(), readerNameFilter, options);
    }

    /**
     * Opens a session to the given reader, with {@link Options#defaults()}.
     *
     * @param terminal the card reader
     * @return a new PC/SC session
     * @throws PcscException if the connection fails or another open session uses this reader
     */
    public static PcscSession open(CardTerminal terminal) {
        return open(terminal, Options.defaults());
    }

    /**
     * Opens a session to the given reader.
     *
     * @param terminal the card reader
     * @param options  connection options
     * @return a new PC/SC session
     * @throws PcscException if the connection fails or another open session uses this reader
     */
    public static PcscSession open(CardTerminal terminal, Options options) {
        PcscSession session = new PcscSession(Objects.requireNonNull(terminal, "terminal"),
                Objects.requireNonNull(options, "options"));
        session.connect();
        return session;
    }

    /** Opens the first reader matching the filter that has a card present (package-private for tests). */
    static PcscSession open(CardTerminals terminals, String readerNameFilter, Options options) {
        return open(PcscReaders.find(terminals, readerNameFilter), options);
    }

    /**
     * Returns the ATR (Answer To Reset) of the connected card.
     *
     * @return ATR bytes
     */
    public byte[] getATR() {
        ensureOpen();
        return card.getATR().getBytes();
    }

    /**
     * Returns the communication protocol of the connection ("T=0" or "T=1").
     *
     * @return protocol string
     */
    public String getProtocol() {
        ensureOpen();
        return card.getProtocol();
    }

    /**
     * Returns the options of this session.
     *
     * @return the connection options
     */
    public Options options() {
        return options;
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        throw installNotSupported();
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        throw installNotSupported();
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        throw installNotSupported();
    }

    /**
     * Not supported: delete applications on a real card with the GlobalPlatform module
     * ({@code GPSession.deleteAid}).
     *
     * @param aid the AID of the applet instance
     * @throws UnsupportedOperationException always
     */
    @Override
    public void delete(AID aid) {
        throw new UnsupportedOperationException("Cannot delete applets via PC/SC. Use the GlobalPlatform module"
                + " (GPSession.deleteAid) to delete applications.");
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        select(AID.auto(appletClass));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends SELECT by DF name with Le '00' (GlobalPlatform Card Specification 2.3.1 Table 11-79). The
     * applet is selected when the card answers '9000' (or '61XX', more response data available).</p>
     *
     * @throws SelectException if the card answers another status word
     */
    @Override
    public void select(AID aid) {
        APDUResponse response = send(0x00, 0xA4, 0x04, 0x00, aid.toBytes(), 256);
        if (response.sw() != 0x9000 && response.sw1() != 0x61) {
            throw new SelectException(aid, response.sw(),
                    String.format("SELECT %s failed: SW=%04X", aid.toHex(), response.sw()));
        }
    }

    /**
     * Resets the card and connects again: {@code Card.endExclusive()} if the session holds exclusive access
     * (see the class documentation), {@code Card.disconnect(true)} (warm reset, {@code SCARD_RESET_CARD}),
     * then {@code CardTerminal.connect} with the same protocol and exclusive access again if the options ask
     * for it. Persistent card state survives; transient memory is cleared, logical channels are closed and the
     * card selects its default application on the basic channel.
     *
     * @throws UnsupportedOperationException if the session was created from a {@link Card}
     * @throws PcscException                 if the reset or the new connection fails (the session is then
     *                                       closed)
     */
    @Override
    public void reset() {
        ensureOpen();
        if (terminal == null) {
            throw new UnsupportedOperationException("reset() connects again through the card reader, but this"
                    + " session was created from a Card; open it with PcscSession.open(...) to reset the card");
        }
        checkThread();
        history.note("card reset");
        endExclusiveAccess();
        try {
            card.disconnect(true);
        } catch (CardException | IllegalStateException e) {
            markClosed();
            throw new PcscException("Card reset failed in reader '" + terminal.getName() + "'; the session is"
                    + " closed", e);
        }
        release();
        try {
            connect();
        } catch (PcscException e) {
            closed = true;
            throw new PcscException("Card reset: connecting again to reader '" + terminal.getName() + "' failed;"
                    + " the session is closed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The command is encoded with {@link APDUCodec#encode(int, int, int, int, byte[], int)}; the response knows
     * it ({@link APDUResponse#inReplyTo(byte[])}).</p>
     *
     * @throws PcscException if the transmission fails
     */
    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, data, le);
        return new APDUResponse(transmit(command)).inReplyTo(command);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException      if the bytes are not a command APDU (ISO/IEC 7816-4:2005 5.1)
     * @throws UnsupportedOperationException if the command addresses a logical channel that was not opened
     *                                       through this session (see the class documentation)
     * @throws PcscException                 if the transmission fails
     */
    @Override
    public byte[] transmit(byte[] rawApdu) {
        ensureOpen();
        CommandAPDU command = new CommandAPDU(rawApdu);
        checkThread();
        try {
            byte[] response = channels.transmit(command);
            history.record(command.getBytes(), response);
            return response;
        } catch (CardException e) {
            throw new PcscException("Transmit failed: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw connectionLost(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Records every command transmitted through this session (SELECT included) with its response, and card
     * resets.</p>
     */
    @Override
    public APDUHistory history() {
        return history;
    }

    /**
     * Closes the logical channels opened through the session and disconnects from the card without resetting
     * it. Closing twice has no further effect.
     *
     * @throws PcscException if the disconnection fails or another thread holds exclusive access
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        checkThread();
        Card connection = card;
        channels.closeAll();
        markClosed();
        try {
            connection.disconnect(false);
        } catch (CardException e) {
            throw new PcscException("Disconnect failed", e);
        }
    }

    /** Ends the PC/SC transaction of an exclusive session; a failure is left to the disconnect that follows. */
    private void endExclusiveAccess() {
        if (exclusiveOwner != null) {
            try {
                card.endExclusive();
            } catch (CardException | IllegalStateException e) {
                // connection lost or transaction already ended: disconnect(true) reports what matters
            }
            exclusiveOwner = null;
        }
    }

    private void connect() {
        Card connected;
        try {
            connected = terminal.connect(options.protocol());
        } catch (CardException e) {
            throw new PcscException("Failed to connect to the card in reader '" + terminal.getName() + "'", e);
        }
        register(connected, "reader '" + terminal.getName() + "'");
        if (options.exclusive()) {
            try {
                connected.beginExclusive();
            } catch (CardException | IllegalStateException e) {
                unregister(connected);
                throw new PcscException("Cannot get exclusive access to the card in reader '" + terminal.getName()
                        + "'; open the session with PcscSession.Options.defaults().shared() to share it", e);
            }
            exclusiveOwner = Thread.currentThread();
        }
        card = connected;
        channels = new PcscChannels(connected);
    }

    private static void register(Card connection, String what) {
        synchronized (CARDS_IN_USE) {
            if (!CARDS_IN_USE.add(connection)) {
                throw new PcscException("The connection to " + what + " is already used by another PcscSession;"
                        + " close that session first (javax.smartcardio returns the same Card for a reader, so"
                        + " both sessions would share one connection)");
            }
        }
    }

    private static void unregister(Card connection) {
        synchronized (CARDS_IN_USE) {
            CARDS_IN_USE.remove(connection);
        }
    }

    private void markClosed() {
        closed = true;
        release();
    }

    /** Forgets the current connection: it no longer counts as used and exclusive access ended with it. */
    private void release() {
        exclusiveOwner = null;
        unregister(card);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PcscSession is closed");
        }
    }

    /** javax.smartcardio: while a thread holds exclusive access, other threads cannot use the card. */
    private void checkThread() {
        Thread owner = exclusiveOwner;
        if (owner != null && owner != Thread.currentThread()) {
            throw new PcscException("This PcscSession holds exclusive access to the card for thread '"
                    + owner.getName() + "' and javax.smartcardio lets only that thread use the card (Card"
                    + ".beginExclusive); use the session from that thread or open it with"
                    + " PcscSession.Options.defaults().shared()");
        }
    }

    private static PcscException connectionLost(IllegalStateException e) {
        return new PcscException("The connection to the card is no longer valid (card removed, or disconnected"
                + " outside this session): " + e.getMessage(), e);
    }

    private static UnsupportedOperationException installNotSupported() {
        return new UnsupportedOperationException(
                "Cannot install applets via PC/SC. Use the GlobalPlatform module to load CAP files.");
    }
}
