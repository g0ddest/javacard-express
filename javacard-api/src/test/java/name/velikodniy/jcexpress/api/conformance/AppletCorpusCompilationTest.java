package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiles a corpus of applets written against the published Java Card 3.0.5 Classic API (test resources under
 * {@code applet-corpus/corpus}, one package per API area) the way an applet project does: {@code javac --release 8}
 * with the API jar on the class path.
 *
 * <p>Each corpus package is compiled twice. Against jCardSim, an independent implementation of the same API, it
 * proves that the corpus uses nothing but published API. Against the stubs it proves that the stubs offer that
 * API with source-compatible declarations (overloads, checked exceptions, nested types, protected hooks), which
 * a class-file comparison alone cannot show.
 */
class AppletCorpusCompilationTest {

    /** Root of the corpus sources inside the test resources. */
    private static final String CORPUS_ROOT = "/applet-corpus/corpus";

    /** Class-file level that applet projects compile for; the stubs must work with every javac from 8 up. */
    private static final String APPLET_RELEASE = "8";

    static Stream<String> corpusPackages() throws IOException {
        try (Stream<Path> children = Files.list(corpusRoot())) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    /**
     * The corpus package compiles against the reference implementation, so it only uses published API.
     *
     * @param corpusPackage package below {@code corpus}, for example {@code crypto}
     * @param output        scratch directory for the class files
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusPackages")
    void corpusUsesOnlyPublishedApi(String corpusPackage, @TempDir Path output) {
        assertThat(compile(corpusPackage, ApiLocations.referenceJar(), output))
                .as("corpus package '%s' compiled against the reference API (jCardSim)", corpusPackage)
                .isEmpty();
    }

    /**
     * The corpus package compiles against the stubs.
     *
     * @param corpusPackage package below {@code corpus}, for example {@code crypto}
     * @param output        scratch directory for the class files
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusPackages")
    void corpusCompilesAgainstTheStubs(String corpusPackage, @TempDir Path output) {
        assertThat(compile(corpusPackage, ApiLocations.stubClasses(), output))
                .as("corpus package '%s' compiled against the API stubs", corpusPackage)
                .isEmpty();
    }

    /** Returns the compiler errors, formatted as {@code File.java:line: message}. */
    private static List<String> compile(String corpusPackage, Path apiClassPath, Path output) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            List<String> options = List.of("--release", APPLET_RELEASE, "-Xlint:-options", "-proc:none",
                    "-implicit:none", "-classpath", apiClassPath.toString(), "-d", output.toString());
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources(corpusPackage));
            javac.getTask(null, files, diagnostics, options, null, units).call();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(AppletCorpusCompilationTest::format)
                .toList();
    }

    private static List<Path> sources(String corpusPackage) {
        try (Stream<Path> files = Files.walk(corpusRoot().resolve(corpusPackage))) {
            List<Path> sources = files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
            assertThat(sources).as("sources of corpus package " + corpusPackage).isNotEmpty();
            return sources;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String format(Diagnostic<? extends JavaFileObject> diagnostic) {
        String file = diagnostic.getSource() == null ? "?" : Path.of(diagnostic.getSource().toUri()).getFileName()
                .toString();
        return file + ":" + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(Locale.ROOT);
    }

    private static Path corpusRoot() {
        URL root = AppletCorpusCompilationTest.class.getResource(CORPUS_ROOT);
        if (root == null) {
            throw new IllegalStateException("Corpus not found on the test class path: " + CORPUS_ROOT);
        }
        try {
            return Path.of(root.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
