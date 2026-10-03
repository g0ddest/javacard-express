package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.apdu.APDUCodec;
import javacard.framework.Applet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * A decorator that records all APDU exchanges for programmatic access and debugging.
 *
 * <p>Wraps any {@link SmartCardSession} and intercepts all {@code send}/{@code transmit}
 * calls, recording each exchange as an {@link APDULogEntry}. Optionally prints the exchanges to standard output,
 * one line each.</p>
 *
 * <h2>Usage — programmatic access:</h2>
 * <pre>
 * LoggingSession logged = LoggingSession.wrap(card);
 * logged.send(0x80, 0x01);
 * logged.send(0x00, 0xA4, 0x04, 0x00, aid);
 *
 * // Query APDU history
 * List&lt;APDULogEntry&gt; all = logged.entries();
 * List&lt;APDULogEntry&gt; selects = logged.entries(0xA4);
 * String dump = logged.dump();  // human-readable text
 * </pre>
 *
 * <h2>Usage — with console logging:</h2>
 * <pre>
 * LoggingSession logged = LoggingSession.wrap(card, true);
 * // printed to standard output as the exchanges happen, one line each:
 * // [JCX] C: 80010000
 * // [JCX] R: 9000
 * </pre>
 *
 * <p>The lines go through java.util.logging: logger {@code name.velikodniy.jcexpress}, level INFO, message
 * {@code [JCX] } followed by a transcript line. When the first line is printed and that logger has no handler of
 * its own, JavaCard Express gives it one that prints the message alone on standard output and turns its parent
 * handlers off (so the lines appear once, without the timestamp line of the JDK's default format); records of the
 * loggers below it keep going to the parent handlers. To route the lines elsewhere, give the logger a handler of
 * your own before the first exchange: {@code name.velikodniy.jcexpress.handlers = ...} in a
 * {@code logging.properties} file, or {@code Logger.getLogger("name.velikodniy.jcexpress").addHandler(...)} (for
 * example SLF4J's {@code SLF4JBridgeHandler}); JavaCard Express then adds none and leaves the parent handlers as
 * configured. A level above INFO on the logger silences the lines.</p>
 *
 * <p>The log holds the commands sent through this session, the SELECT commands of {@link #select(AID)},
 * {@link #select(Class)} and the install methods included (taken from the wrapped session's {@link #history()};
 * a wrapped session that keeps no history contributes only what passes through {@code send} and
 * {@code transmit}). {@link #dump()} and the printed lines use the transcript format of {@link APDUHistory}; the
 * printed lines also show the notes the wrapped session records during an exchange, after its response (for
 * example {@code # applet threw java.lang.NullPointerException at ...} on jCardSim).</p>
 *
 * @see APDULogEntry
 * @see SmartCardSession
 */
public final class LoggingSession implements SmartCardSession {

    private static final Logger LOG = Logger.getLogger("name.velikodniy.jcexpress");

    private final SmartCardSession delegate;
    private final boolean printToLog;
    private final List<APDULogEntry> entries = new ArrayList<>();

    private LoggingSession(SmartCardSession delegate, boolean printToLog) {
        this.delegate = delegate;
        this.printToLog = printToLog;
    }

    /**
     * Wraps a session with APDU logging (no console output).
     *
     * @param session the session to wrap
     * @return a logging session
     */
    public static LoggingSession wrap(SmartCardSession session) {
        return wrap(session, false);
    }

    /**
     * Wraps a session with APDU logging.
     *
     * @param session    the session to wrap
     * @param printToLog true to also print the exchanges, one line each on standard output (see the class
     *                   documentation)
     * @return a logging session
     */
    public static LoggingSession wrap(SmartCardSession session, boolean printToLog) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        return new LoggingSession(session, printToLog);
    }

    /**
     * Returns the underlying (unwrapped) session.
     *
     * @return the delegate session
     */
    public SmartCardSession delegate() {
        return delegate;
    }

    // ── Log access ──

    /**
     * Returns all recorded APDU log entries (unmodifiable).
     *
     * @return list of log entries in chronological order
     */
    public List<APDULogEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns log entries filtered by INS byte.
     *
     * @param ins the INS byte to filter by
     * @return filtered list of entries
     */
    public List<APDULogEntry> entries(int ins) {
        return entries.stream()
                .filter(e -> e.ins() == ins)
                .toList();
    }

    /**
     * Returns the number of recorded entries.
     *
     * @return entry count
     */
    public int entryCount() {
        return entries.size();
    }

    /**
     * Returns the most recent log entry.
     *
     * @return the last entry
     * @throws IllegalStateException if no entries have been recorded
     */
    public APDULogEntry lastEntry() {
        if (entries.isEmpty()) {
            throw new IllegalStateException("No APDU log entries recorded");
        }
        return entries.get(entries.size() - 1);
    }

    /**
     * Clears all recorded log entries.
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Returns the recorded exchanges in the transcript format of {@link APDUHistory}: a {@code C:} line with the
     * command and an {@code R:} line with the response (data and SW1 SW2) per exchange, as upper-case hex.
     *
     * @return multi-line text dump, every line ended by a line break
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (APDULogEntry entry : entries) {
            sb.append(TranscriptFormat.command(entry.command())).append('\n');
            sb.append(TranscriptFormat.response(entry.response().toBytes())).append('\n');
        }
        return sb.toString();
    }

    // ── SmartCardSession delegation ──

    @Override
    public void install(Class<? extends Applet> appletClass) {
        logDelegated(() -> delegate.install(appletClass));
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        logDelegated(() -> delegate.install(appletClass, aid));
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        logDelegated(() -> delegate.install(appletClass, aid, installParams));
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        logDelegated(() -> delegate.select(appletClass));
    }

    @Override
    public void select(AID aid) {
        logDelegated(() -> delegate.select(aid));
    }

    @Override
    public void reset() {
        logDelegated(delegate::reset);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deletes through the wrapped session.</p>
     */
    @Override
    public void delete(AID aid) {
        logDelegated(() -> delegate.delete(aid));
    }

    /**
     * {@inheritDoc}
     *
     * @return the history of the wrapped session
     */
    @Override
    public APDUHistory history() {
        return delegate.history();
    }

    @Override
    public APDUResponse send(int cla, int ins) {
        byte[] command = APDUCodec.encode(cla, ins, 0, 0, null, NO_LE);
        return exchange(command, () -> delegate.send(cla, ins));
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, null, NO_LE);
        return exchange(command, () -> delegate.send(cla, ins, p1, p2));
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, data, NO_LE);
        return exchange(command, () -> delegate.send(cla, ins, p1, p2, data));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The log entry holds the command as encoded by
     * {@link APDUCodec#encode(int, int, int, int, byte[], int)}, which is what every backend shipped with
     * JavaCard Express transmits.</p>
     */
    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, data, le);
        return exchange(command, () -> delegate.send(cla, ins, p1, p2, data, le));
    }

    /**
     * Forwards each overload to the same overload of the delegate (so a delegate never receives an
     * {@code le} it did not get from the caller) and records the encoded command.
     */
    private APDUResponse exchange(byte[] command, Supplier<APDUResponse> forward) {
        if (printToLog) {
            print(TranscriptFormat.command(command));
        }
        long start = delegate.history().position();
        APDUResponse response = forward.get().inReplyTo(command);
        if (printToLog) {
            logResponse(response);
            printNotesSince(start);
        }
        entries.add(new APDULogEntry(command, response, System.currentTimeMillis()));
        return response;
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        if (printToLog) {
            print(TranscriptFormat.command(rawApdu));
        }
        long start = delegate.history().position();
        byte[] rawResponse = delegate.transmit(rawApdu);
        APDUResponse response = new APDUResponse(rawResponse);

        if (printToLog) {
            logResponse(response);
            printNotesSince(start);
        }

        entries.add(new APDULogEntry(rawApdu.clone(), response, System.currentTimeMillis()));
        return rawResponse;
    }

    /**
     * Prints the notes the wrapped session recorded during an exchange after its response, such as what an applet
     * on jCardSim threw ({@code # applet threw ...}) next to the '6F00'.
     */
    private void printNotesSince(long start) {
        delegate.history().notesSince(start).forEach(note -> print(TranscriptFormat.note(note)));
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ── Internal ──

    /**
     * Prints a transcript line through java.util.logging (logger {@code name.velikodniy.jcexpress}, level INFO, prefix
     * {@code [JCX]}): the channel of {@code logged(true)} and of {@value JavaCardExtension#LOG_PARAMETER}. Unless the
     * logger has a handler of its own, it gets {@link ConsoleLines} first: one line on standard output per call.
     *
     * @param line a line in the transcript format
     */
    static void print(String line) {
        ConsoleLines.installUnlessConfigured(LOG);
        LOG.info("[JCX] " + line);
    }

    private void logResponse(APDUResponse response) {
        print(TranscriptFormat.response(response.toBytes()));
    }

    /**
     * Runs an operation of the wrapped session that sends commands of its own (SELECT) and logs the exchanges the
     * wrapped session recorded in its history meanwhile.
     */
    private void logDelegated(Runnable operation) {
        APDUHistory history = delegate.history();
        long start = history.position();
        try {
            operation.run();
        } finally {
            for (APDULogEntry entry : history.entriesSince(start)) {
                if (printToLog) {
                    print(TranscriptFormat.command(entry.command()));
                    logResponse(entry.response());
                }
                entries.add(entry);
            }
        }
    }

}
