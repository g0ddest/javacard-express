package name.velikodniy.jcexpress.livecard.junit;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the APDU transcripts of live-card tests go, below the configured transcript directory, and how they reach
 * the test report.
 *
 * <ul>
 *   <li>A directory per test class, named by the fully qualified (binary) name of the top-level class, with a
 *       directory per {@code @Nested} class below it: {@code com.example.WalletLiveTest/Credit/}.</li>
 *   <li>In it {@code before-all.txt} (connection and class set-up), {@code <test method>.txt} per test
 *       ({@code <method>-<n>.txt} per invocation of a parameterized or repeated test) and {@code after-all.txt}
 *       (between and after the tests, cleanup).</li>
 *   <li>Each test's file is published with the test's result ({@code apdu-transcript.txt},
 *       {@link ExtensionContext#publishFile}); the class's files with the class's.</li>
 * </ul>
 */
final class TranscriptFiles {

    /** The set-up transcript of a class. */
    static final String BEFORE_ALL = "before-all.txt";
    /** The transcript between and after the tests of a class, cleanup included. */
    static final String AFTER_ALL = "after-all.txt";

    private static final Logger LOG = Logger.getLogger(TranscriptFiles.class.getName());
    private static final Pattern INVOCATION = Pattern.compile("test-template-invocation:#(\\d+)");

    private TranscriptFiles() {
    }

    /**
     * Returns the transcript directory of the class of a context, relative to the transcript directory.
     *
     * @param context a class or method context
     * @return the top-level class's binary name, then the simple name of each nested class
     */
    static Path classDirectory(ExtensionContext context) {
        Deque<Class<?>> classes = new ArrayDeque<>();
        for (Optional<ExtensionContext> c = Optional.of(context); c.isPresent(); c = c.get().getParent()) {
            Optional<Class<?>> type = c.get().getTestClass();
            if (c.get().getTestMethod().isEmpty() && type.isPresent() && type.get() != classes.peekFirst()) {
                classes.addFirst(type.get());
            }
        }
        Path directory = Path.of(classes.removeFirst().getName());
        for (Class<?> nested : classes) {
            directory = directory.resolve(nested.getSimpleName());
        }
        return directory;
    }

    /**
     * Returns the transcript file of a test, relative to the transcript directory.
     *
     * @param context the context of a test method (or of one invocation of it)
     * @return {@code <class directory>/<method>.txt}, with {@code -<n>} for the n-th invocation
     */
    static Path testFile(ExtensionContext context) {
        String method = context.getRequiredTestMethod().getName();
        Matcher invocation = INVOCATION.matcher(context.getUniqueId());
        String name = invocation.find() ? method + "-" + invocation.group(1) : method;
        return classDirectory(context).resolve(name + ".txt");
    }

    /**
     * Attaches a transcript file to the report of a test or class. Attaching is a convenience: a launcher that
     * writes no outputs (the JUnit test kit by default) or a JUnit older than 5.14 leaves the file where it is.
     *
     * @param context the test or class
     * @param file    the transcript file; nothing happens if it does not exist
     * @param name    the name in the report
     */
    static void publish(ExtensionContext context, Path file, String name) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            context.publishFile(name, MediaType.TEXT_PLAIN_UTF_8,
                    target -> Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING));
        } catch (RuntimeException | LinkageError e) {
            LOG.log(Level.FINE, e, () -> "APDU transcript " + file + " not attached to the report");
        }
    }
}
