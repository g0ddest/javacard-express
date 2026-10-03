package name.velikodniy.jcexpress.converter.translate;

import javacard.framework.Applet;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Test helper that compiles Java Card fixture sources at test time with an explicit
 * {@code javac --release N}, against the project's clean-room API stubs.
 *
 * <p>Used for behaviour that depends on the class file version produced by javac (for example
 * nestmate private access, JEP 181, which javac emits from release 11 on) and for the
 * javac release matrix of the converter regression fixtures.
 */
public final class FixtureCompiler {

    /** Root of the converter test sources (tests run with the module directory as cwd). */
    public static final Path TEST_SOURCES = Path.of("src/test/java");

    private FixtureCompiler() {}

    /**
     * Compiles all {@code .java} files of one fixture package.
     *
     * @param packageName fixture package in dot notation (e.g. {@code com.example.bytecode.nest})
     * @param release     javac {@code --release} value (8, 11, 17, 21, 25, ...)
     * @param outDir      class output directory (created if missing)
     * @return {@code outDir}, for chaining into {@code Converter.builder().classesDirectory(...)}
     */
    public static Path compilePackage(String packageName, int release, Path outDir) throws IOException {
        Path dir = TEST_SOURCES.resolve(packageName.replace('.', '/'));
        List<Path> sources;
        try (Stream<Path> files = Files.list(dir)) {
            sources = files.filter(p -> p.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder()).toList();
        }
        return compile(sources, release, outDir);
    }

    /**
     * Writes the given source text to a temporary source tree and compiles it.
     *
     * @param fqcn    fully qualified class name of the single compilation unit
     * @param source  Java source text
     * @param release javac {@code --release} value
     * @param outDir  class output directory
     * @return {@code outDir}
     */
    public static Path compileSource(String fqcn, String source, int release, Path outDir)
            throws IOException {
        Path srcRoot = Files.createTempDirectory("jcx-fixture-src");
        Path file = srcRoot.resolve(fqcn.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return compile(List.of(file), release, outDir);
    }

    /**
     * Writes several compilation units to a temporary source tree and compiles them together.
     *
     * @param sources fully qualified class name to source text
     * @param release javac {@code --release} value
     * @param outDir  class output directory
     * @return {@code outDir}
     */
    public static Path compileSources(Map<String, String> sources, int release, Path outDir) throws IOException {
        Path srcRoot = Files.createTempDirectory("jcx-fixture-src");
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> s : sources.entrySet()) {
            Path file = srcRoot.resolve(s.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, s.getValue(), StandardCharsets.UTF_8);
            files.add(file);
        }
        return compile(files, release, outDir);
    }

    private static Path compile(List<Path> sources, int release, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of(
                "--release", String.valueOf(release), "-g", "-nowarn", "-Xlint:-options",
                "-cp", apiStubsLocation().toString(), "-d", outDir.toString()));
        sources.forEach(p -> args.add(p.toString()));
        var err = new ByteArrayOutputStream();
        int rc = javac.run(null, new PrintStream(err), new PrintStream(err), args.toArray(String[]::new));
        if (rc != 0) {
            throw new IllegalStateException("javac --release " + release + " failed:\n"
                    + err.toString(StandardCharsets.UTF_8));
        }
        return outDir;
    }

    private static Path apiStubsLocation() {
        try {
            return Path.of(Applet.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the javacard-api stubs", e);
        }
    }
}
