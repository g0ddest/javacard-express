package name.velikodniy.jcexpress;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Prints the lines {@link LoggingSession} logs ({@code [JCX] C: 80300000020064}) on standard output, one line each,
 * without the header line that java.util.logging's default format puts before every record.
 *
 * <p>It is put on the logger {@code name.velikodniy.jcexpress} when the first line is printed, unless that logger
 * has a handler of its own (a user's configuration: {@code logging.properties} or a handler added before the
 * first exchange), and the logger's parent handlers are turned off then. Records of the loggers below it
 * ({@code name.velikodniy.jcexpress.livecard.LiveCard} and others) go on to the parent handlers, as before.
 * {@code System.out} is looked up for every line, so output capturing of test frameworks and IDEs sees it.</p>
 */
final class ConsoleLines extends Handler {

    private final Logger logger;

    private ConsoleLines(Logger logger) {
        this.logger = logger;
        setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return formatMessage(logRecord) + System.lineSeparator();
            }
        });
    }

    /**
     * Puts the handler on a logger that has no handler of its own and turns its parent handlers off.
     *
     * @param logger the logger of the transcript lines
     */
    static synchronized void installUnlessConfigured(Logger logger) {
        if (logger.getHandlers().length == 0) {
            logger.addHandler(new ConsoleLines(logger));
            logger.setUseParentHandlers(false);
        }
    }

    @Override
    public void publish(LogRecord logRecord) {
        if (logRecord == null || !isLoggable(logRecord)) {
            return;
        }
        if (logger.getName().equals(logRecord.getLoggerName())) {
            System.out.print(getFormatter().format(logRecord));
            System.out.flush();
        } else {
            toParentHandlers(logRecord);
        }
    }

    /** Passes a record of a logger below on, as java.util.logging does while parent handlers are on. */
    private void toParentHandlers(LogRecord logRecord) {
        for (Logger parent = logger.getParent(); parent != null; parent = parent.getParent()) {
            for (Handler handler : parent.getHandlers()) {
                handler.publish(logRecord);
            }
            if (!parent.getUseParentHandlers()) {
                return;
            }
        }
    }

    @Override
    public void flush() {
        System.out.flush();
    }

    /** Leaves standard output open. */
    @Override
    public void close() {
        // System.out belongs to the JVM
    }
}
