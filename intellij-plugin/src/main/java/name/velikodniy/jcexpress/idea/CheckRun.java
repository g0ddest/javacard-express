package name.velikodniy.jcexpress.idea;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Runs the project's javac check on the sources of one package, in this process: javac compiles the current texts
 * with the module's class path and the check on the processor path, into a temporary directory the check reads the
 * class files from, and the errors the check reports ({@value #PREFIX}...) are the result. javac's own errors are
 * left out: the IDE shows them itself.
 */
final class CheckRun {

    /** The check marks its errors with this prefix. */
    static final String PREFIX = "Java Card: ";
    /** The converter the check runs is Java 25 bytecode. */
    static final int MIN_RUNTIME = 25;

    private CheckRun() {
    }

    /**
     * Runs the check.
     *
     * @param request       the package and the settings
     * @param checkCanceled called between javac's steps; it throws to stop the run, and that exception is rethrown
     * @return the errors, or why the check could not run
     */
    static CheckReport run(CheckRequest request, Runnable checkCanceled) {
        if (Runtime.version().feature() < MIN_RUNTIME) {
            return CheckReport.notRun("the javac check needs a Java " + MIN_RUNTIME + " runtime, the IDE runs on "
                    + Runtime.version().feature());
        }
        JavaCompiler javac = javac();
        if (javac == null) {
            return CheckReport.notRun("the IDE's runtime has no javac (module jdk.compiler)");
        }
        Path output = temporaryDirectory();
        try {
            return compile(javac, request, output, checkCanceled);
        } finally {
            delete(output);
        }
    }

    /**
     * javac of this runtime. The IDE replaces the system class loader, through which {@link ToolProvider} looks for
     * javac, so the module {@code jdk.compiler} of the boot layer is asked directly too.
     */
    static JavaCompiler javac() {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac != null) {
            return javac;
        }
        for (JavaCompiler candidate : ServiceLoader.load(ModuleLayer.boot(), JavaCompiler.class)) {
            if ("jdk.compiler".equals(candidate.getClass().getModule().getName())) {
                return candidate;
            }
        }
        return null;
    }

    private static CheckReport compile(JavaCompiler javac, CheckRequest request, Path output, Runnable checkCanceled) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<Source> units = request.sources().stream().map(Source::new).toList();
        Cancellation cancellation = new Cancellation(checkCanceled);
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) javac.getTask(null, files, diagnostics, arguments(request, output), null,
                    units);
            task.addTaskListener(cancellation);
            task.call();
        } catch (IOException e) {
            return CheckReport.notRun("javac could not close its files: " + e.getMessage());
        } catch (RuntimeException e) {
            cancellation.rethrowIfCause(e);
            return CheckReport.notRun("javac stopped: " + e);
        }
        return CheckReport.of(problems(diagnostics));
    }

    /** javac's arguments: those of the main compilation, the check's options, and the temporary class output. */
    static List<String> arguments(CheckRequest request, Path output) {
        List<String> arguments = new ArrayList<>(List.of("--release", String.valueOf(request.release()), "-g",
                "-implicit:none", "-proc:full", "-Xlint:-options", "-encoding", "UTF-8", "-d", output.toString()));
        if (!request.classpath().isEmpty()) {
            arguments.add("-classpath");
            arguments.add(String.join(File.pathSeparator, request.classpath()));
        }
        arguments.add("-processorpath");
        arguments.add(String.join(File.pathSeparator, request.processorPath()));
        request.options().forEach((key, value) -> {
            if (!key.equals(ProcessorOptions.PACKAGE_NAME)) {
                arguments.add("-A" + key + "=" + value);
            }
        });
        // the package the IDE asks about, which the build checks too (CheckRequests decided that)
        arguments.add("-A" + ProcessorOptions.PACKAGE_NAME + "=" + request.packageName());
        return arguments;
    }

    private static List<CheckProblem> problems(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<CheckProblem> problems = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            String message = diagnostic.getMessage(Locale.ROOT);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR && message != null && message.startsWith(PREFIX)
                    && diagnostic.getSource() instanceof Source source) {
                long position = diagnostic.getPosition();
                long start = diagnostic.getStartPosition() == Diagnostic.NOPOS ? position
                        : diagnostic.getStartPosition();
                long end = diagnostic.getEndPosition() == Diagnostic.NOPOS ? start : diagnostic.getEndPosition();
                if (start != Diagnostic.NOPOS) {
                    problems.add(new CheckProblem(source.path, (int) start, (int) Math.max(start, end), message));
                }
            }
        }
        return problems;
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("jcx-idea-check-");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create a directory for the check's class files", e);
        }
    }

    private static void delete(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            // a leftover temporary directory is harmless; the next run uses a new one
        }
    }

    /** A source from memory: the text the editor has, under the file's own path (javac checks class names). */
    static final class Source extends SimpleJavaFileObject {

        private final String path;
        private final String text;

        Source(CheckRequest.Source source) {
            super(uri(source.path()), Kind.SOURCE);
            this.path = source.path();
            this.text = source.text();
        }

        private static URI uri(String path) {
            return Path.of(path).toAbsolutePath().toUri();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    /** Asks between javac's steps whether to stop, and remembers the exception that stops it. */
    private static final class Cancellation implements TaskListener {

        private final Runnable checkCanceled;
        private RuntimeException thrown;

        Cancellation(Runnable checkCanceled) {
            this.checkCanceled = checkCanceled;
        }

        @Override
        public void started(TaskEvent event) {
            try {
                checkCanceled.run();
            } catch (RuntimeException e) {
                thrown = e;
                throw e;
            }
        }

        /** javac wraps what a listener throws; the caller gets the original back. */
        void rethrowIfCause(RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause == thrown) {
                    throw thrown;
                }
            }
        }
    }
}
