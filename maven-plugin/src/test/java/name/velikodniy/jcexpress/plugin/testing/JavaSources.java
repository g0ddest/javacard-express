package name.velikodniy.jcexpress.plugin.testing;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles small Java Card applet projects inside tests with the JDK compiler, against the
 * javacard-express API stubs on the test class path. This gives the plugin tests real class
 * files (with methods, constructors, line numbers) instead of synthetic ones.
 */
public final class JavaSources {

    private final Map<String, String> sources = new LinkedHashMap<>();
    private final List<Path> classpath = new ArrayList<>();
    private String release = "8";

    private JavaSources() {
    }

    /** @return an empty source set */
    public static JavaSources create() {
        return new JavaSources();
    }

    /**
     * Adds a compilation unit.
     *
     * @param relativePath source path, e.g. {@code com/example/HelloApplet.java}
     * @param code         the source code
     * @return this
     */
    public JavaSources add(String relativePath, String code) {
        sources.put(relativePath, code);
        return this;
    }

    /**
     * Adds a class path entry (in addition to the API stubs).
     *
     * @param entry directory or jar
     * @return this
     */
    public JavaSources classpath(Path entry) {
        classpath.add(entry);
        return this;
    }

    /**
     * Sets the {@code --release} of javac (default 8, the most common setting for applets).
     *
     * @param value release number
     * @return this
     */
    public JavaSources release(int value) {
        this.release = Integer.toString(value);
        return this;
    }

    /**
     * Writes the sources below {@code root/src} and compiles them to {@code root/classes}.
     *
     * @param root project directory
     * @return the classes directory
     */
    public Path compile(Path root) {
        try {
            Path src = root.resolve("src");
            Path classes = Files.createDirectories(root.resolve("classes"));
            List<Path> files = new ArrayList<>();
            for (var e : sources.entrySet()) {
                Path file = src.resolve(e.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, e.getValue());
                files.add(file);
            }
            runJavac(files, classes);
            return classes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void runJavac(List<Path> files, Path classes) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = new ArrayList<>(List.of("--release", release, "-g", "-Xlint:-options",
                    "-d", classes.toString(), "-cp", classpathString()));
            boolean ok = javac.getTask(null, fm, diagnostics, options, null,
                    fm.getJavaFileObjectsFromPaths(files)).call();
            if (!ok) {
                throw new IllegalStateException("Test sources do not compile: " + diagnostics.getDiagnostics());
            }
        }
    }

    private String classpathString() {
        List<String> entries = new ArrayList<>();
        entries.add(apiStubsLocation().toString());
        classpath.forEach(p -> entries.add(p.toString()));
        return String.join(java.io.File.pathSeparator, entries);
    }

    /** @return the jar or directory that contains the javacard-express API stubs */
    public static Path apiStubsLocation() {
        try {
            return Path.of(javacard.framework.Applet.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
