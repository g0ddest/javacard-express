package name.velikodniy.jcexpress.converter.input;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans a compiled classes directory and collects all {@code .class} files
 * belonging to a given Java package into a {@link PackageInfo} model.
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>This is the primary entry point for the Load stage of the converter pipeline.
 * It bridges the filesystem representation (a directory of {@code .class} files) and
 * the in-memory model ({@link PackageInfo} containing {@link ClassInfo} records)
 * that subsequent pipeline stages operate on.
 *
 * <p>The scanner resolves the package name to a directory path, enumerates all
 * {@code .class} files within that directory (non-recursively), and delegates
 * parsing of each file to {@link ClassFileReader}. The result is a single
 * {@link PackageInfo} that aggregates every class in the package.
 *
 * <p>Per the JCVM 3.0.5 specification, a CAP file represents exactly one Java
 * package. This class enforces that boundary by scanning only the classes that
 * reside in the specified package directory.
 *
 * <p>Private members that other classes of the package access directly (nestmate access of
 * javac 11 and later) are made package-visible, see {@link NestmateAccess}.
 *
 * @see ClassFileReader
 * @see PackageInfo
 * @see name.velikodniy.jcexpress.converter.Converter
 */
public final class PackageScanner {

    private PackageScanner() {}

    /**
     * Scans the given classes directory for all {@code .class} files in the specified package.
     *
     * <p>The package name is converted from dot notation to a directory path
     * (e.g. {@code "com.example"} becomes {@code "com/example"}) and resolved
     * relative to {@code classesDir}. Each {@code .class} file found in that
     * directory is parsed via {@link ClassFileReader#readFile(java.nio.file.Path)}
     * and included in the returned {@link PackageInfo}.
     *
     * @param classesDir  root of compiled classes (e.g. {@code "target/classes"})
     * @param packageName dot-separated package name (e.g. {@code "com.example"})
     * @return a {@link PackageInfo} containing all parsed classes in the package
     * @throws IOException if the package directory does not exist or reading any
     *                     {@code .class} file fails
     */
    public static PackageInfo scan(Path classesDir, String packageName) throws IOException {
        return read(packageName, classFiles(classesDir, packageName).values());
    }

    /**
     * Reads the {@code .class} files of a package.
     *
     * @param classesDir  root of compiled classes
     * @param packageName dot-separated package name
     * @return class file bytes by internal class name, in directory listing order
     * @throws IOException if the package directory does not exist, a file cannot be read, or two
     *                     files declare the same class
     */
    public static Map<String, byte[]> classFiles(Path classesDir, String packageName) throws IOException {
        Path packageDir = classesDir.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDir)) {
            throw new IOException("Package directory not found: " + packageDir);
        }
        Map<String, byte[]> classFiles = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(packageDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                byte[] bytes = Files.readAllBytes(file);
                String name = ClassFile.of().parse(bytes).thisClass().asInternalName();
                if (classFiles.put(name, bytes) != null) {
                    throw new IOException("Two class files in " + packageDir + " declare " + name);
                }
            }
        }
        return classFiles;
    }

    /**
     * Builds the package model from class files already read.
     *
     * @param packageName dot-separated package name
     * @param classFiles  the class files of the package, in the order the classes are listed
     * @return the package with its classes in the given order
     */
    public static PackageInfo read(String packageName, Collection<byte[]> classFiles) {
        List<ClassInfo> classes = new ArrayList<>();
        for (byte[] bytes : classFiles) {
            classes.add(ClassFileReader.read(bytes));
        }
        // javac 11+ nestmate access to private members becomes package access (JCVM 3.1 §2.2.1.1.6)
        return new PackageInfo(packageName, NestmateAccess.widen(List.copyOf(classes)));
    }
}
