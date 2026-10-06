package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.TranscriptFormat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * APDU transcript of a live-card run, in the line format of {@link TranscriptFormat}: every command before it is sent
 * ({@code C:}), every response ({@code R:}, with the time the exchange took) and notes ({@code #}), such as the
 * guard's decisions. The output file can be switched, which gives one transcript per test, and a file can be started
 * anew or continued. Key values are never written.
 */
public final class Transcript implements AutoCloseable {

    private PrintWriter out = new PrintWriter(Writer.nullWriter());
    private Path file;

    /**
     * Creates a transcript that discards everything until {@link #switchTo(Path, String)} is called.
     */
    public Transcript() {
        // starts discarding
    }

    /**
     * Continues the transcript in another file (appending if it exists).
     *
     * @param target the file; its directory is created
     * @param title  the first line written
     * @throws UncheckedIOException if the file cannot be opened
     */
    public synchronized void switchTo(Path target, String title) {
        switchTo(target, title, true);
    }

    /**
     * Continues the transcript in another file.
     *
     * @param target the file; its directory is created
     * @param title  the first line written
     * @param append true to append to the file if it exists, false to start it anew
     * @throws UncheckedIOException if the file cannot be opened
     */
    public synchronized void switchTo(Path target, String title, boolean append) {
        out.close();
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            out = new PrintWriter(Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING), true);
        } catch (IOException e) {
            out = new PrintWriter(Writer.nullWriter());
            throw new UncheckedIOException("Cannot write the APDU transcript " + target, e);
        }
        file = target;
        out.println(TranscriptFormat.title(title + "  ("
                + OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + ")"));
    }

    /**
     * Returns the current file.
     *
     * @return the file, or null while discarding
     */
    public synchronized Path file() {
        return file;
    }

    /**
     * Records a command that is about to be sent.
     *
     * @param command the command APDU
     */
    public synchronized void command(byte[] command) {
        out.println(TranscriptFormat.command(command));
    }

    /**
     * Records a response with the time of the exchange, in the line format of
     * {@link TranscriptFormat#response(byte[], Duration)}, e.g. {@code R: 9000  (12.3 ms)}.
     *
     * @param response the response APDU (data and SW1-SW2)
     * @param elapsed  how long the exchange took
     */
    public synchronized void response(byte[] response, Duration elapsed) {
        out.println(TranscriptFormat.response(response, elapsed));
    }

    /**
     * Records a note.
     *
     * @param message the note
     */
    public synchronized void note(String message) {
        out.println(TranscriptFormat.note(message));
    }

    /**
     * Closes the current file; later output is discarded.
     */
    @Override
    public synchronized void close() {
        out.close();
        out = new PrintWriter(Writer.nullWriter());
        file = null;
    }
}
