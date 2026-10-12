package name.velikodniy.jcexpress.javac;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Where javac writes the class files of the compilation, read through the processing environment's {@link Filer}:
 * the class files of this compilation, the other class files of a package when the output is a directory (an
 * incremental build compiles only some classes of a package, and the package rules need all of them), and the
 * removal of the class files of a source with errors.
 */
final class ClassOutput {

    private static final String CLASS = ".class";
    private static final String PROBE = "javacard-express-check.probe";
    private static final Set<String> SOURCE_SET_LANGUAGES = Set.of("java", "kotlin", "groovy", "scala");

    private final Filer filer;
    private final Path directory;

    private ClassOutput(Filer filer, Path directory) {
        this.filer = filer;
        this.directory = directory;
    }

    /**
     * A class file of the package the check cannot read: not a class file, or one of a later Java version than the
     * compiler's (JVMS §4.1).
     */
    static final class UnreadableClassFile extends RuntimeException {

        private final String file;

        UnreadableClassFile(String file, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.file = file;
        }

        /** @return the class file, relative to the class output, e.g. {@code p/Junk.class} */
        String file() {
            return file;
        }
    }

    /**
     * Locates the class output of the compilation.
     *
     * @param filer the processing environment's filer
     * @return the class output; empty if javac does not tell where it is
     */
    static Optional<ClassOutput> of(Filer filer) {
        try {
            // for an output location javac answers with the file it would write, which need not exist
            URI probe = filer.getResource(StandardLocation.CLASS_OUTPUT, "", PROBE).toUri();
            Path directory = "file".equals(probe.getScheme()) ? Path.of(probe).getParent() : null;
            return Optional.of(new ClassOutput(filer, directory));
        } catch (IOException | RuntimeException e) {
            // a file manager of a tool that keeps no files, or that gives the filer no resources
            return Optional.empty();
        }
    }

    /**
     * Whether the output is the one of test sources, which are compiled with the same options by some builds but are
     * never converted: Maven's {@code target/test-classes}, Gradle's {@code build/classes/<language>/test} and
     * IntelliJ IDEA's {@code out/test/<module>}.
     *
     * @return {@code true} for a test output
     */
    boolean isTestOutput() {
        if (directory == null || directory.getFileName() == null) {
            return false;
        }
        String name = directory.getFileName().toString();
        String parent = name(directory.getParent());
        String grandparent = directory.getParent() == null ? "" : name(directory.getParent().getParent());
        return name.equals("test-classes")
                || (name.equals("test") && SOURCE_SET_LANGUAGES.contains(parent) && grandparent.equals("classes"))
                || (parent.equals("test") && grandparent.equals("out"));
    }

    /**
     * Reads the class file javac wrote for a class of this compilation.
     *
     * @param binaryName the binary name of the class, e.g. {@code com.example.App$Inner}
     * @return the class file
     * @throws UncheckedIOException if it cannot be read
     */
    byte[] read(String binaryName) {
        try (InputStream in = file(binaryName).openInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the class file of " + binaryName, e);
        } catch (RuntimeException e) {
            // a file manager that keeps the class files in memory opens none for reading
            throw new UncheckedIOException("cannot read the class file of " + binaryName + " ("
                    + JavaCardCheckProcessor.describe(e) + ")", new IOException(e));
        }
    }

    /**
     * Returns the class files of a package: those of this compilation and, when the output is a directory, the
     * others found there.
     *
     * @param packageName the package in dot notation
     * @param compiled    the class files of this compilation in the package, by binary name
     * @return the class files of the package by file name
     */
    Map<String, byte[]> packageClasses(String packageName, Map<String, byte[]> compiled) {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        for (Path file : classFiles(packageName)) {
            classes.put(file.getFileName().toString(), bytes(file));
        }
        compiled.forEach((name, bytes) -> classes.put(simpleFileName(name), bytes));
        classes.forEach((file, bytes) -> requireReadable(packageName, file, bytes));
        return classes;
    }

    /** A class file the converter cannot parse would stop the check of the whole package. */
    private static void requireReadable(String packageName, String file, byte[] bytes) {
        try {
            ClassFile.of().parse(bytes);
        } catch (RuntimeException e) {
            throw new UnreadableClassFile((packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/") + file,
                    e);
        }
    }

    /**
     * Returns the packages that have class files in the output directory, including the unnamed package as
     * {@code ""}; empty when the output is not a directory.
     *
     * @return package names in dot notation
     */
    Set<String> packages() {
        Set<String> packages = new TreeSet<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return packages;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(f -> f.getFileName().toString().endsWith(CLASS))
                    .filter(f -> !f.getFileName().toString().equals("module-info.class"))
                    .map(directory::relativize)
                    .filter(f -> !f.startsWith("META-INF"))
                    .forEach(f -> packages.add(f.getParent() == null ? "" : f.getParent().toString()
                            .replace(f.getFileSystem().getSeparator(), ".")));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the class output " + directory, e);
        }
        return packages;
    }

    /**
     * Returns the names of the top-level classes of a package in the output directory, for those not compiled now.
     *
     * @param packageName the package in dot notation
     * @return the binary names of the classes whose names have no {@code $}
     */
    List<String> topLevelClasses(String packageName) {
        return classFiles(packageName).stream().map(f -> f.getFileName().toString())
                .map(f -> f.substring(0, f.length() - CLASS.length()))
                .filter(f -> !f.contains("$"))
                .map(f -> packageName.isEmpty() ? f : packageName + "." + f)
                .toList();
    }

    /**
     * Removes the class file of a class, as javac writes none for a source with errors: an incremental build then
     * compiles the source again and reports the errors again. The filer cannot delete what javac wrote, so this
     * deletes the file in the output directory; an output that is not a directory keeps it.
     *
     * @param binaryName the binary name of the class
     */
    void delete(String binaryName) {
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(binaryName.replace('.', '/') + CLASS));
        } catch (IOException e) {
            // the error is reported; a class file that stays only spares a later build the compilation
        }
    }

    private FileObject file(String binaryName) throws IOException {
        int dot = binaryName.lastIndexOf('.');
        String packageName = dot < 0 ? "" : binaryName.substring(0, dot);
        return filer.getResource(StandardLocation.CLASS_OUTPUT, packageName, binaryName.substring(dot + 1) + CLASS);
    }

    private List<Path> classFiles(String packageName) {
        if (directory == null) {
            return List.of();
        }
        Path packageDirectory = packageName.isEmpty() ? directory : directory.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(packageDirectory)) {
            return files.filter(f -> f.getFileName().toString().endsWith(CLASS)).filter(Files::isRegularFile)
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the class files in " + packageDirectory, e);
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static String simpleFileName(String binaryName) {
        return binaryName.substring(binaryName.lastIndexOf('.') + 1) + CLASS;
    }

    private static String name(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }
}
