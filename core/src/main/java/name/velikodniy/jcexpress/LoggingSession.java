package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.apdu.APDUCodec;
import javacard.framework.Applet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A decorator that records all APDU exchanges for programmatic access and debugging.
 *
 * <p>Wraps any {@link SmartCardSession} and intercepts all {@code send}/{@code transmit}
 * calls, recording each exchange as an {@link APDULogEntry}. Optionally prints to
 * java.util.logging for human-readable output.</p>
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
 * // APDUs are printed to java.util.logging as they are sent
 * </pre>
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
     * @param printToLog true to also print APDUs to java.util.logging
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
     * Returns a human-readable dump of all recorded APDU exchanges.
     *
     * @return multi-line text dump
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            APDULogEntry e = entries.get(i);
            sb.append('[').append(i).append("] ");
            sb.append(">> ").append(e.commandHex()).append('\n');
            sb.append('[').append(i).append("] ");
            sb.append("<< ");
            if (e.response().data().length > 0) {
                sb.append(Hex.encodeSpaced(e.response().data())).append(' ');
            }
            sb.append('[').append(String.format("%04X", e.response().sw())).append(']');
            sb.append(' ').append(e.response().data().length).append(" bytes");
            sb.append('\n');
        }
        return sb.toString();
    }

    // ── SmartCardSession delegation ──

    @Override
    public void install(Class<? extends Applet> appletClass) {
        delegate.install(appletClass);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        delegate.install(appletClass, aid);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        delegate.install(appletClass, aid, installParams);
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        delegate.select(appletClass);
    }

    @Override
    public void select(AID aid) {
        delegate.select(aid);
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0, null, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] command = buildCommandBytes(cla, ins, p1, p2, data, le);

        if (printToLog) {
            LOG.info("[JCX] >> " + Hex.encodeSpaced(command));
        }

        APDUResponse response = delegate.send(cla, ins, p1, p2, data, le);

        if (printToLog) {
            logResponse(response);
        }

        entries.add(new APDULogEntry(command, response, System.currentTimeMillis()));
        return response;
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        if (printToLog) {
            LOG.info("[JCX] >> " + Hex.encodeSpaced(rawApdu));
        }

        byte[] rawResponse = delegate.transmit(rawApdu);
        APDUResponse response = new APDUResponse(rawResponse);

        if (printToLog) {
            logResponse(response);
        }

        entries.add(new APDULogEntry(rawApdu.clone(), response, System.currentTimeMillis()));
        return rawResponse;
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ── Internal ──

    private void logResponse(APDUResponse response) {
        StringBuilder sb = new StringBuilder("[JCX] << ");
        if (response.data().length > 0) {
            sb.append(Hex.encodeSpaced(response.data())).append(' ');
        }
        sb.append('[').append(String.format("%04X", response.sw())).append("] ");
        sb.append(response.data().length).append(" bytes");
        LOG.info(sb.toString());
    }

    private static byte[] buildCommandBytes(int cla, int ins, int p1, int p2,
                                             byte[] data, int le) {
        return APDUCodec.encode(cla, ins, p1, p2, data, le);
    }
}
