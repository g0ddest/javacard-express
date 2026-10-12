package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.converter.PackageCheck;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Compiles sample applets in a test as a build does: javac for Java 8 class files, the Java Card API stubs on the
 * class path, the check on the annotation processor path (the module's classes and the converter's), the class files
 * in a {@code target/classes} directory unless the test names another one.
 */
final class Javac {

    /**
     * What a compilation gave.
     *
     * @param success     whether javac succeeded
     * @param diagnostics everything javac reported
     * @param classes     the class output directory
     */
    record Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path classes) {

        /** @return the errors as {@code File.java:line:column: message} */
        List<String> errors() {
            return diagnostics.stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> (d.getSource() == null ? "" : Path.of(d.getSource().toUri()).getFileName() + ":"
                            + d.getLineNumber() + ":" + d.getColumnNumber() + ": ") + d.getMessage(Locale.ROOT))
                    .toList();
        }

        /** @return whether the class file of a class exists */
        boolean hasClassFile(String binaryName) {
            return Files.isRegularFile(classes.resolve(binaryName.replace('.', '/') + ".class"));
        }
    }

    private Javac() {
    }

    /**
     * Compiles sources into {@code root/target/classes}.
     *
     * @param root    the project directory
     * @param sources source text by path below {@code src/main/java}
     * @param options further javac options, e.g. {@code -Ajavacard.version=3.0.4}
     * @return the result
     */
    static Result compile(Path root, Map<String, String> sources, String... options) {
        return compileInto(root.resolve("target/classes"), root, sources, options);
    }

    /**
     * Compiles sources into a class output directory.
     *
     * @param classes the class output directory
     * @param root    the project directory
     * @param sources source text by path below {@code src/main/java}
     * @param options further javac options
     * @return the result
     */
    static Result compileInto(Path classes, Path root, Map<String, String> sources, String... options) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            Files.createDirectories(classes);
            List<File> written = write(root.resolve("src/main/java"), sources);
            List<String> arguments = new ArrayList<>(List.of("--release", "8", "-Xlint:-options", "-d",
                    classes.toString(), "-classpath", classPath(classes), "-processorpath", processorPath()));
            arguments.addAll(List.of(options));
            boolean ok = javac.getTask(null, files, diagnostics, arguments, null,
                    files.getJavaFileObjectsFromFiles(written)).call();
            return new Result(ok, diagnostics.getDiagnostics(), classes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Compiles sources into {@code root/target/classes} with a processor object instead of the processor path, as a
     * build tool that runs processors itself does.
     *
     * @param root      the project directory
     * @param sources   source text by path below {@code src/main/java}
     * @param processor the processor
     * @param options   further javac options
     * @return the result
     */
    static Result compileWithProcessor(Path root, Map<String, String> sources, Processor processor,
                                       String... options) {
        return run(root, sources, files -> files, List.of(processor), options);
    }

    /**
     * Compiles sources into {@code root/target/classes} through another file manager, with the check on the
     * processor path.
     *
     * @param root        the project directory
     * @param sources     source text by path below {@code src/main/java}
     * @param fileManager makes the file manager javac uses from the standard one
     * @param options     further javac options
     * @return the result
     */
    static Result compileWithFileManager(Path root, Map<String, String> sources,
                                         UnaryOperator<JavaFileManager> fileManager, String... options) {
        List<String> withProcessorPath = new ArrayList<>(List.of("-processorpath", processorPath()));
        withProcessorPath.addAll(List.of(options));
        return run(root, sources, fileManager, null, withProcessorPath.toArray(String[]::new));
    }

    private static Result run(Path root, Map<String, String> sources, UnaryOperator<JavaFileManager> fileManager,
                              List<Processor> processors, String... options) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path classes = root.resolve("target/classes");
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            Files.createDirectories(classes);
            List<File> written = write(root.resolve("src/main/java"), sources);
            List<String> arguments = new ArrayList<>(List.of("--release", "8", "-Xlint:-options", "-d",
                    classes.toString(), "-classpath", classPath(classes)));
            arguments.addAll(List.of(options));
            JavaCompiler.CompilationTask task = javac.getTask(null, fileManager.apply(files), diagnostics, arguments,
                    null, files.getJavaFileObjectsFromFiles(written));
            if (processors != null) {
                task.setProcessors(processors);
            }
            return new Result(task.call(), diagnostics.getDiagnostics(), classes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Compiles sources without a class output directory: javac writes each class file next to its source.
     *
     * @param root    the project directory
     * @param sources source text by path below {@code src/main/java}
     * @return the result; its class output is the source directory
     */
    static Result compileWithoutClassOutput(Path root, Map<String, String> sources) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            List<File> written = write(root.resolve("src/main/java"), sources);
            List<String> arguments = List.of("--release", "8", "-Xlint:-options", "-classpath",
                    location(javacard.framework.Applet.class), "-processorpath", processorPath());
            boolean ok = javac.getTask(null, files, diagnostics, arguments, null,
                    files.getJavaFileObjectsFromFiles(written)).call();
            return new Result(ok, diagnostics.getDiagnostics(), root.resolve("src/main/java"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Runs javac as its command line does, with the same class path and processor path.
     *
     * @param root    the project directory
     * @param sources source text by path below {@code src/main/java}
     * @return the exit code and everything javac printed
     */
    static Map.Entry<Integer, String> commandLine(Path root, Map<String, String> sources) {
        try {
            Path classes = Files.createDirectories(root.resolve("target/classes"));
            List<String> arguments = new ArrayList<>(List.of("--release", "8", "-Xlint:-options", "-d",
                    classes.toString(), "-classpath", classPath(classes), "-processorpath", processorPath()));
            write(root.resolve("src/main/java"), sources).forEach(file -> arguments.add(file.toString()));
            StringWriter out = new StringWriter();
            int exit = java.util.spi.ToolProvider.findFirst("javac").orElseThrow()
                    .run(new PrintWriter(out), new PrintWriter(out), arguments.toArray(String[]::new));
            return Map.entry(exit, out.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Runs the {@code javac} of the running JDK in a process of its own, as a build that forks the compiler does: its
     * annotation processors see only the processor path, nothing of this test's class path.
     *
     * @param root          the project directory
     * @param sources       source text by path below {@code src/main/java}
     * @param processorPath the processor path
     * @return the exit code and everything javac printed
     */
    static Map.Entry<Integer, String> forked(Path root, Map<String, String> sources, String processorPath,
                                             String... options) {
        try {
            Path classes = Files.createDirectories(root.resolve("target/classes"));
            List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "javac")
                    .toString(), "--release", "8", "-Xlint:-options", "-d", classes.toString(), "-classpath",
                    classPath(classes), "-processorpath", processorPath));
            command.addAll(List.of(options));
            write(root.resolve("src/main/java"), sources).forEach(file -> command.add(file.toString()));
            Process javac = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(javac.getInputStream().readAllBytes());
            return Map.entry(javac.waitFor(), out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * The classes of this module alone, without the converter.
     *
     * @return the processor path entry
     */
    static String checkWithoutConverter() {
        return location(JavaCardCheckProcessor.class);
    }

    private static List<File> write(Path sourceRoot, Map<String, String> sources) throws IOException {
        List<File> written = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceRoot.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            written.add(file.toFile());
        }
        return written;
    }

    /** The stubs, and the class output as Maven puts it on the class path for incremental builds. */
    private static String classPath(Path classes) {
        return location(javacard.framework.Applet.class) + File.pathSeparator + classes;
    }

    private static String processorPath() {
        return location(JavaCardCheckProcessor.class) + File.pathSeparator + location(PackageCheck.class);
    }

    private static String location(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
