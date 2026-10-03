package name.velikodniy.jcexpress.livecard.thirdparty;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles applet sources the way an applet project builds them: {@code javac --release 8} against the Java Card
 * API stubs of this project ({@code javacard-api}), with {@code javax.tools} in the test JVM.
 */
final class AppletCompiler {

    /** System property set by the build: the API stubs jar (or classes directory). */
    static final String STUBS_PROPERTY = "jcx.livecard.test.apiStubs";

    private static final String APPLET_CLASS = "javacard/framework/Applet.class";

    private AppletCompiler() {
    }

    /**
     * Compiles every {@code .java} file below a directory.
     *
     * @param sources the source root
     * @param classes the output directory
     * @throws IOException           if the files cannot be listed
     * @throws IllegalStateException if javac reports errors or no compiler is available
     */
    static void compile(Path sources, Path classes) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("No Java compiler (javax.tools) in this runtime; run the tests on a JDK");
        }
        Files.createDirectories(classes);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = List.of("--release", "8", "-classpath", apiStubs().toString(), "-d",
                classes.toString(), "-encoding", "UTF-8", "-proc:none", "-nowarn", "-Xlint:-options");
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            Boolean compiled = javac.getTask(new StringWriter(), files, diagnostics, options, null,
                    files.getJavaFileObjectsFromPaths(javaFiles(sources))).call();
            if (!Boolean.TRUE.equals(compiled)) {
                throw new IllegalStateException("javac --release 8 failed for " + sources + ":\n"
                        + diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .map(d -> d.getSource() + ":" + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT))
                        .collect(Collectors.joining("\n")));
            }
        }
    }

    /**
     * Copies a directory tree.
     *
     * @param source the directory to copy
     * @param target the copy
     * @throws IOException if a file cannot be copied
     */
    static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path copy = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy);
                }
            }
        }
    }

    /**
     * Deletes a directory tree if it exists.
     *
     * @param directory the directory
     * @throws IOException if a file cannot be deleted
     */
    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static List<Path> javaFiles(Path sources) throws IOException {
        try (Stream<Path> walk = Files.walk(sources)) {
            return walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static Path apiStubs() {
        return firstWithTheApi(candidates(System.getProperty(STUBS_PROPERTY),
                System.getProperty("maven.multiModuleProjectDirectory"), System.getProperty("java.class.path", "")));
    }

    /**
     * Where the API stubs may be: the build's setting (Maven sets the jar; an IDE that imports the setting may
     * resolve it to something else, IntelliJ IDEA to the module's pom.xml), the classes directory of the
     * {@code javacard-api} module next to this one or under the project root, and class path entries of that module.
     * jCardSim, which holds {@code javacard.*} classes as well, is never a candidate.
     *
     * @param configured the setting {@value #STUBS_PROPERTY}, or null
     * @param root       the project root ({@code maven.multiModuleProjectDirectory}), or null
     * @param classPath  the test class path
     * @return the candidates, in this order
     */
    static List<Path> candidates(String configured, String root, String classPath) {
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured));
        }
        candidates.add(Path.of("../javacard-api/target/classes"));
        if (root != null && !root.isBlank()) {
            candidates.add(Path.of(root, "javacard-api", "target", "classes"));
        }
        for (String entry : classPath.split(File.pathSeparator)) {
            if (entry.contains("javacard-express-api") || entry.contains("javacard-api")) {
                candidates.add(Path.of(entry));
            }
        }
        return candidates;
    }

    /**
     * Returns the first candidate that holds the API: a jar or a classes directory with
     * {@code javacard/framework/Applet.class}.
     *
     * @param candidates the candidates
     * @return the stubs
     * @throws IllegalStateException if none holds them
     */
    static Path firstWithTheApi(List<Path> candidates) {
        return candidates.stream().filter(AppletCompiler::holdsTheApi).findFirst()
                .orElseThrow(() -> new IllegalStateException("The Java Card API stubs were not found (tried "
                        + candidates + "): build the javacard-api module first (mvn test-compile -pl livecard -am),"
                        + " or set " + STUBS_PROPERTY + " to its jar or classes directory"));
    }

    private static boolean holdsTheApi(Path candidate) {
        if (Files.isDirectory(candidate)) {
            return Files.isRegularFile(candidate.resolve(APPLET_CLASS));
        }
        if (!Files.isRegularFile(candidate) || !candidate.toString().endsWith(".jar")) {
            return false;
        }
        try (JarFile jar = new JarFile(candidate.toFile())) {
            return jar.getEntry(APPLET_CLASS) != null;
        } catch (IOException e) {
            return false;
        }
    }
}
