package name.velikodniy.jcexpress.plugin.testing;

import org.apache.maven.plugin.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * A Maven {@link Log} that records every message with its level, for assertions.
 */
public final class RecordingLog implements Log {

    /** Log levels as printed by Maven. */
    public enum Level { DEBUG, INFO, WARNING, ERROR }

    /**
     * A recorded log line.
     *
     * @param level   the level
     * @param message the message text
     */
    public record Entry(Level level, String message) {
        @Override
        public String toString() {
            return "[" + level + "] " + message;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** @return all recorded entries in order */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * @param level the level
     * @return the messages logged at that level
     */
    public List<String> messages(Level level) {
        return entries.stream().filter(e -> e.level() == level).map(Entry::message).toList();
    }

    /** @return the whole log as Maven would print it */
    public String text() {
        StringBuilder sb = new StringBuilder();
        entries.forEach(e -> sb.append(e).append('\n'));
        return sb.toString();
    }

    private void add(Level level, CharSequence content, Throwable error) {
        String text = content == null ? "" : content.toString();
        if (error != null) {
            text = text.isEmpty() ? error.toString() : text + " (" + error + ")";
        }
        entries.add(new Entry(level, text));
    }

    @Override public boolean isDebugEnabled() { return true; }
    @Override public void debug(CharSequence content) { add(Level.DEBUG, content, null); }
    @Override public void debug(CharSequence content, Throwable error) { add(Level.DEBUG, content, error); }
    @Override public void debug(Throwable error) { add(Level.DEBUG, null, error); }
    @Override public boolean isInfoEnabled() { return true; }
    @Override public void info(CharSequence content) { add(Level.INFO, content, null); }
    @Override public void info(CharSequence content, Throwable error) { add(Level.INFO, content, error); }
    @Override public void info(Throwable error) { add(Level.INFO, null, error); }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public void warn(CharSequence content) { add(Level.WARNING, content, null); }
    @Override public void warn(CharSequence content, Throwable error) { add(Level.WARNING, content, error); }
    @Override public void warn(Throwable error) { add(Level.WARNING, null, error); }
    @Override public boolean isErrorEnabled() { return true; }
    @Override public void error(CharSequence content) { add(Level.ERROR, content, null); }
    @Override public void error(CharSequence content, Throwable error) { add(Level.ERROR, content, error); }
    @Override public void error(Throwable error) { add(Level.ERROR, null, error); }
}
