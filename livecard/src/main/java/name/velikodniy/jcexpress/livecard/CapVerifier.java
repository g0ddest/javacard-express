package name.velikodniy.jcexpress.livecard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs the off-card verifier of an Oracle Java Card development kit on a CAP file, as a black box (the same
 * way as the converter's {@code OracleVerifycapTest}): {@code com.sun.javacard.offcardverifier.Verifier} in a
 * separate JVM with the kit's export files. Nothing of the kit is part of this project; its location is the
 * setting {@code verifierSdk}.
 *
 * <p>Supported kits are the 2.2.x/3.0.x layouts with {@code lib/*.jar} and {@code api_export_files/}, e.g.
 * {@code jc304_kit} or {@code jc305u3_kit}. A CAP passes when the verifier exits with 0 and reports
 * {@value #PASSED}.</p>
 */
public final class CapVerifier {

    /** The verifier's success line. */
    public static final String PASSED = "Verification completed with 0 warnings and 0 errors";
    private static final String MAIN_CLASS = "com.sun.javacard.offcardverifier.Verifier";
    private static final long TIMEOUT_MINUTES = 2;

    private final Path kit;
    private final List<Path> exportFiles;
    private final String classpath;

    /**
     * Result of a verification.
     *
     * @param passed whether the CAP file passed
     * @param output the verifier's output
     */
    public record Result(boolean passed, String output) {
    }

    /**
     * Prepares the verifier of a kit.
     *
     * @param kit the kit directory
     * @throws LiveCardException if the directory does not look like a supported kit
     */
    public CapVerifier(Path kit) {
        this.kit = kit.toAbsolutePath().normalize();
        this.exportFiles = files(this.kit.resolve("api_export_files"), ".exp");
        List<Path> jars = files(this.kit.resolve("lib"), ".jar");
        if (exportFiles.isEmpty() || jars.isEmpty()) {
            throw new LiveCardException("verifierSdk " + this.kit + " is not a Java Card 3.0.x development kit: it"
                    + " needs lib/*.jar and api_export_files/**/*.exp (e.g. jc304_kit, jc305u3_kit)");
        }
        this.classpath = String.join(File.pathSeparator, jars.stream().map(Path::toString).toList());
    }

    /**
     * Returns the kit directory.
     *
     * @return the absolute kit path
     */
    public Path kit() {
        return kit;
    }

    /**
     * Verifies a CAP file against all export files of the kit.
     *
     * @param cap the CAP file bytes
     * @return the result
     * @throws LiveCardException if the verifier cannot be run or does not finish within 2 minutes
     */
    public Result verify(byte[] cap) {
        return verify(cap, List.of());
    }

    /**
     * Verifies a CAP file against all export files of the kit and those below export path entries: the
     * package's own export file if the CAP file has an Export component, and those of the packages it imports.
     * The verifier rejects two export files of one package, so a file is passed only once per package path
     * ({@code <package>/javacard/<name>.exp}); the kit's files and earlier entries win.
     *
     * @param cap        the CAP file bytes
     * @param exportPath directories with export files in the layout {@code <package>/javacard/<name>.exp}
     * @return the result
     * @throws LiveCardException if the verifier cannot be run or does not finish within 2 minutes
     */
    public Result verify(byte[] cap, List<Path> exportPath) {
        try {
            Path work = Files.createTempDirectory("jcx-verifier");
            try {
                return run(Files.write(work.resolve("package.cap"), cap), work.resolve("verifier.log"),
                        withExportPath(exportPath));
            } finally {
                deleteQuietly(work);
            }
        } catch (IOException e) {
            throw new LiveCardException("Cannot run the off-card verifier of " + kit + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LiveCardException("Interrupted while verifying with " + kit, e);
        }
    }

    private List<Path> withExportPath(List<Path> exportPath) {
        Map<Path, Path> byPackage = new LinkedHashMap<>();
        Path api = kit.resolve("api_export_files");
        exportFiles.forEach(file -> byPackage.put(api.relativize(file), file));
        for (Path entry : exportPath) {
            Path root = entry.toAbsolutePath().normalize();
            files(root, ".exp").forEach(file -> byPackage.putIfAbsent(root.relativize(file), file));
        }
        return List.copyOf(byPackage.values());
    }

    private Result run(Path cap, Path log, List<Path> exports) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-Djc.home=" + kit, "-cp", classpath, MAIN_CLASS));
        exports.forEach(file -> command.add(file.toString()));
        command.add(cap.toString());
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        withoutLiveCardSettings(builder.environment());
        Process process = builder.start();
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new LiveCardException("The off-card verifier of " + kit + " did not finish within "
                    + TIMEOUT_MINUTES + " minutes");
        }
        String output = Files.readString(log, StandardCharsets.UTF_8);
        return new Result(process.exitValue() == 0 && output.contains(PASSED), output);
    }

    /**
     * Removes the live-card settings ({@code JCX_LIVECARD_*}, card keys included) from the environment of the
     * verifier process, a third-party program that has no use for them.
     *
     * @param environment the environment the process would inherit; changed in place
     */
    static void withoutLiveCardSettings(Map<String, String> environment) {
        environment.keySet().removeIf(name -> name.toUpperCase(Locale.ROOT).startsWith("JCX_LIVECARD"));
    }

    private static List<Path> files(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new LiveCardException("Cannot list " + directory + ": " + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            // a temporary directory left behind is harmless
        }
    }
}
