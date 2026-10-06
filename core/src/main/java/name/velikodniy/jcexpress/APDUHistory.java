package name.velikodniy.jcexpress;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * The most recent exchanges of a session, kept in a bounded buffer: every command with its response, and notes
 * such as an applet installation or a card reset.
 *
 * <p>The sessions of JavaCard Express record into their {@link SmartCardSession#history()}, SELECT commands
 * included. {@link JavaCardExtension} attaches the last entries to a failed test (and to a failed lifecycle
 * method) and publishes those of a failed test of a {@link JavaCardTest} class as its file
 * {@code apdu-transcript.txt}. The text form is the transcript format used throughout JavaCard Express
 * ({@link TranscriptFormat}), one line each: {@code C:} the command and {@code R:} the response (data and SW1 SW2)
 * as upper-case hex, with the time the exchange took when the session measured it, {@code #} a note.</p>
 *
 * <pre>
 * # install com.example.CounterApplet as F0D3ADA9A6E8B2F4
 * C: 00A4040008F0D3ADA9A6E8B2F400
 * R: 9000  (0.4 ms)
 * C: 8001000000
 * R: 000000019000  (0.2 ms)
 * </pre>
 *
 * <p>Each exchange keeps when its command was sent and how long it took ({@link APDULogEntry#timestampMs()},
 * {@link APDULogEntry#duration()}); {@link #last()} gives the newest one, so a test can check the time of a
 * command it just sent.</p>
 *
 * <p>When more than {@link #capacity()} entries were recorded, the oldest are dropped and the transcript says so.
 * The history holds the bytes as they were exchanged, so it reveals what the commands carry (secure channel
 * commands are recorded protected). Methods are thread-safe.</p>
 *
 * <p>The card of a {@link JavaCardTest} class returns a view of its session's history that starts where the current
 * test (or, in {@code @BeforeAll} and {@code @AfterAll} methods, the current class) started: it shows the entries
 * recorded from then on, also those recorded later, and what is recorded through it goes into the session's
 * history.</p>
 */
public final class APDUHistory {

    /** Number of entries (exchanges and notes) a session keeps by default. */
    public static final int DEFAULT_CAPACITY = 256;

    private static final APDUHistory NONE = new APDUHistory(0);

    /** The entries, shared with the views of this history. */
    private final Buffer buffer;
    /** The position of the first entry this history shows: 0, or where a view starts. */
    private final long start;

    /**
     * Creates a history that keeps the last {@value #DEFAULT_CAPACITY} entries.
     */
    public APDUHistory() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a history that keeps the last {@code capacity} entries.
     *
     * @param capacity number of entries to keep; {@code 0} records nothing
     * @throws IllegalArgumentException if {@code capacity} is negative
     */
    public APDUHistory(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative, got " + capacity);
        }
        this.buffer = new Buffer(capacity);
        this.start = 0;
    }

    private APDUHistory(Buffer buffer, long start) {
        this.buffer = buffer;
        this.start = start;
    }

    /**
     * Returns a history that records nothing, for sessions that keep no history.
     *
     * @return an empty history with capacity 0
     */
    public static APDUHistory none() {
        return NONE;
    }

    /**
     * Records an exchange without its time: the timestamp is the time of recording and the duration is unknown.
     * {@link #exchange(byte[], Transmission)} and {@link #record(byte[], byte[], long, Duration)} keep both.
     *
     * @param command  the command APDU as sent
     * @param response the response APDU as received (data followed by SW1 SW2)
     */
    public void record(byte[] command, byte[] response) {
        add(command, response, System.currentTimeMillis(), null);
    }

    /**
     * Records an exchange with when its command was sent and how long it took.
     *
     * @param command      the command APDU as sent
     * @param response     the response APDU as received (data followed by SW1 SW2)
     * @param sentAtMillis when the command was sent (milliseconds since the epoch)
     * @param duration     how long the exchange took, from just before sending to the response
     * @throws NullPointerException if {@code duration} is null
     */
    public void record(byte[] command, byte[] response, long sentAtMillis, Duration duration) {
        add(command, response, sentAtMillis, Objects.requireNonNull(duration, "duration"));
    }

    /**
     * Transmits a command and records the exchange with its send time and its duration, measured around the
     * transmission. A transmission that throws records nothing.
     *
     * @param command      the command APDU as sent
     * @param transmission sends the command and returns the response APDU (data followed by SW1 SW2)
     * @param <E>          the checked exception the transmission throws
     * @return the response of the transmission
     * @throws E if the transmission fails
     */
    public <E extends Exception> byte[] exchange(byte[] command, Transmission<E> transmission) throws E {
        long sentAt = System.currentTimeMillis();
        long started = System.nanoTime();
        byte[] response = transmission.transmit();
        record(command, response, sentAt, Duration.ofNanos(System.nanoTime() - started));
        return response;
    }

    private void add(byte[] command, byte[] response, long sentAt, Duration duration) {
        byte[] sent = command.clone();
        byte[] received = response.clone();
        buffer.add(position -> new Exchange(position, sent, received, sentAt, duration));
    }

    /**
     * Records a note, such as an applet installation or a card reset. Line breaks become spaces.
     *
     * @param text the note
     */
    public void note(String text) {
        String line = text.replaceAll("\\R", " ");
        buffer.add(position -> new Note(position, line));
    }

    /**
     * Returns the kept exchanges (notes excluded), oldest first. Responses know their command
     * ({@link APDUResponse#inReplyTo(byte[])}); an exchange whose response is shorter than a status word is left
     * out.
     *
     * @return a new list of log entries
     */
    public List<APDULogEntry> entries() {
        return entriesSince(start);
    }

    /**
     * Returns the newest exchange of this history (of this view, for a view such as the history of the card of a
     * {@link JavaCardTest} class inside a test), notes left aside: for example the command a test has just sent,
     * with its time.
     *
     * @return the newest exchange, as {@link #entries()} returns it
     * @throws IllegalStateException if this history holds no exchange
     */
    public APDULogEntry last() {
        if (buffer.capacity == 0) {
            throw new IllegalStateException("This session keeps no history: no exchange to return");
        }
        List<APDULogEntry> exchanges = entriesSince(start);
        if (exchanges.isEmpty()) {
            throw new IllegalStateException("The history holds no exchange yet"
                    + (start > 0 ? " since it starts (the current test, or the current class)" : ""));
        }
        return exchanges.getLast();
    }

    /**
     * Returns the kept entries as transcript text, one line each and every line ended by a line break; the first
     * line says how many earlier entries were dropped, if any.
     *
     * @return the transcript, empty if nothing was recorded
     */
    public String transcript() {
        return transcriptSince(start, buffer.capacity);
    }

    /**
     * Returns the number of entries this history keeps.
     *
     * @return the capacity
     */
    public int capacity() {
        return buffer.capacity;
    }

    /**
     * Passes the transcript lines of the kept entries and then of every entry recorded from now on to
     * {@code lines}, one call per line without the line break, while the entry is recorded: how
     * {@link JavaCardExtension} prints the exchanges of the card of a {@link JavaCardTest} class when
     * {@value JavaCardExtension#LOG_PARAMETER} is set. A history that records nothing (capacity 0, such as
     * {@link #none()}) never calls it. An exception of {@code lines} is ignored: printing must not change an
     * exchange.
     *
     * @param lines receives the lines
     */
    void printTo(Consumer<String> lines) {
        buffer.printTo(lines, start);
    }

    /** The position of the next entry; entries recorded from now on have this position or a higher one. */
    long position() {
        return buffer.position();
    }

    /** The position of the oldest kept entry: entries before it were dropped (or not recorded yet). */
    long oldestPosition() {
        return buffer.oldestPosition();
    }

    /**
     * Returns a view of this history from a position on: it shows the entries recorded at or after it (and after
     * where this history starts), also those recorded later, and records into the same buffer.
     *
     * @param position the first position the view shows, e.g. {@link #position()} before something starts
     * @return the view
     */
    APDUHistory since(long position) {
        return new APDUHistory(buffer, Math.max(start, position));
    }

    /** The kept exchanges recorded at or after {@code since}, as {@link #entries()} returns them. */
    List<APDULogEntry> entriesSince(long since) {
        return buffer.entriesSince(Math.max(start, since));
    }

    /** The texts of the kept notes recorded at or after {@code since}, oldest first. */
    List<String> notesSince(long since) {
        return buffer.notesSince(Math.max(start, since));
    }

    /**
     * Returns the last {@code maxEntries} entries recorded at or after {@code since} as transcript text, preceded
     * by a note when earlier entries since that position were dropped or are not shown.
     */
    String transcriptSince(long since, int maxEntries) {
        return buffer.transcriptSince(Math.max(start, since), maxEntries);
    }

    private static String count(long earlier) {
        return earlier + (earlier == 1 ? " earlier entry" : " earlier entries");
    }

    private static void print(Entry entry, Consumer<String> lines) {
        StringBuilder text = new StringBuilder();
        entry.appendTo(text);
        try {
            text.toString().lines().forEach(lines);
        } catch (RuntimeException e) {
            // printing must not change the outcome of an exchange (a failing log handler)
        }
    }

    /** The bounded entries of a history and of its views; one lock for all of them. */
    private static final class Buffer {
        private final int capacity;
        private final ArrayDeque<Entry> entries = new ArrayDeque<>();
        /** Number of entries recorded so far: the position of the next entry. */
        private long position;
        /** Receives the transcript lines of every entry recorded from now on; null while nothing prints. */
        private Consumer<String> printer;

        Buffer(int capacity) {
            this.capacity = capacity;
        }

        synchronized void add(LongFunction<Entry> entryAt) {
            if (capacity == 0) {
                return;
            }
            Entry entry = entryAt.apply(position);
            if (entries.size() == capacity) {
                entries.removeFirst();
            }
            entries.addLast(entry);
            position++;
            if (printer != null) {
                print(entry, printer);
            }
        }

        synchronized long position() {
            return position;
        }

        synchronized long oldestPosition() {
            return position - entries.size();
        }

        synchronized void printTo(Consumer<String> lines, long since) {
            if (capacity == 0) {
                return;
            }
            entries.stream().filter(entry -> entry.position() >= since).forEach(entry -> print(entry, lines));
            printer = printer == null ? lines : printer.andThen(lines);
        }

        synchronized List<APDULogEntry> entriesSince(long since) {
            List<APDULogEntry> list = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.position() >= since && entry instanceof Exchange exchange
                        && exchange.response().length >= 2) {
                    list.add(exchange.toLogEntry());
                }
            }
            return list;
        }

        synchronized List<String> notesSince(long since) {
            return entries.stream().filter(entry -> entry.position() >= since && entry instanceof Note)
                    .map(entry -> ((Note) entry).text()).toList();
        }

        synchronized String transcriptSince(long since, int maxEntries) {
            List<Entry> recent = entries.stream().filter(entry -> entry.position() >= since).toList();
            long firstKept = position - entries.size();
            long dropped = Math.max(0, firstKept - since);
            int shown = Math.min(maxEntries, recent.size());
            int hidden = recent.size() - shown;
            StringBuilder text = new StringBuilder();
            if (hidden > 0) {
                text.append(TranscriptFormat.note(count(dropped + hidden) + " not shown")).append('\n');
            } else if (dropped > 0) {
                text.append(TranscriptFormat.note(count(dropped) + " not kept (the history keeps the last " + capacity
                        + ")")).append('\n');
            }
            recent.subList(hidden, recent.size()).forEach(entry -> entry.appendTo(text));
            return text.toString();
        }
    }

    /**
     * A transmission of one command, for {@link #exchange(byte[], Transmission)}.
     *
     * @param <E> the checked exception the transmission throws
     */
    @FunctionalInterface
    public interface Transmission<E extends Exception> {

        /**
         * Sends the command.
         *
         * @return the response APDU (data followed by SW1 SW2)
         * @throws E if the transmission fails
         */
        byte[] transmit() throws E;
    }

    /** An entry of the history: an exchange or a note. */
    private sealed interface Entry permits Exchange, Note {
        long position();

        void appendTo(StringBuilder text);
    }

    /** An exchange; the duration is null when it was recorded without its time. */
    private record Exchange(long position, byte[] command, byte[] response, long timestampMs, Duration duration)
            implements Entry {
        @Override
        public void appendTo(StringBuilder text) {
            text.append(TranscriptFormat.command(command)).append('\n')
                    .append(TranscriptFormat.response(response, duration)).append('\n');
        }

        APDULogEntry toLogEntry() {
            byte[] sent = command.clone();
            return new APDULogEntry(sent, new APDUResponse(response).inReplyTo(sent), timestampMs, duration);
        }
    }

    private record Note(long position, String text) implements Entry {
        @Override
        public void appendTo(StringBuilder out) {
            out.append(TranscriptFormat.note(text)).append('\n');
        }
    }
}
