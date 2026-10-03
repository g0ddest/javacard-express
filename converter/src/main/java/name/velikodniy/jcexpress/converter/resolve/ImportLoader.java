package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Determines the export file of every package a CAP file may import (JCVM 3.1 §4.3.3).
 *
 * <p>Sources, in order of precedence:
 * <ol>
 *   <li>export files given explicitly ({@code Converter.Builder.importExportFile});</li>
 *   <li>the export path: for each package referenced by the classes being converted, the first
 *       entry that contains {@code <package>/javacard/<last name component>.exp} (JCVM 3.1 §4.1.1,
 *       §4.1.3, §5.1, §5.2). An entry is a directory or a JAR/ZIP file;</li>
 *   <li>the built-in API export data of the target version ({@link BuiltinExports}).</li>
 * </ol>
 * A user-supplied export file replaces the built-in data of the same package, so a card
 * vendor's or a newer kit's API export files can be used instead of the built-in ones.
 * The package version recorded in the Import component is always the one of the export file
 * that is used (JCVM 3.1 §4.5.2, §6.7).
 */
public final class ImportLoader {

    private ImportLoader() {}

    /**
     * Loads the candidate imports.
     *
     * @param version            target Java Card version (selects the built-in API data)
     * @param exportFiles        explicitly supplied export files
     * @param exportPath         export path entries (directories or JAR files)
     * @param referencedPackages internal names of the packages referenced by the classes
     * @return candidate imports with consecutive tokens from 0; unused ones are pruned later
     * @throws ConverterException if an export file is unreadable, ambiguous or inconsistent
     */
    public static List<ImportedPackage> load(JavaCardVersion version, List<Path> exportFiles,
                                             List<Path> exportPath, Collection<String> referencedPackages)
            throws ConverterException {
        Map<String, Source> byPackage = new LinkedHashMap<>();
        for (ImportedPackage builtin : BuiltinExports.allBuiltinImports(0, version)) {
            byPackage.put(builtin.exportFile().packageName(), new Source(builtin.exportFile(), "built-in"));
        }
        Map<String, Source> supplied = new LinkedHashMap<>();
        for (Path file : exportFiles) {
            addSupplied(supplied, read(file), file.toString());
        }
        for (String pkg : referencedPackages) {
            if (supplied.containsKey(pkg)) continue;
            Optional<Source> found = findOnExportPath(pkg, exportPath);
            if (found.isPresent()) supplied.put(pkg, found.get());
        }
        byPackage.putAll(supplied);
        checkUniqueAids(byPackage.values());
        return toImports(byPackage.values());
    }

    private record Source(ExportFile exportFile, String origin) {}

    private static void addSupplied(Map<String, Source> supplied, ExportFile ef, String origin)
            throws ConverterException {
        Source previous = supplied.putIfAbsent(ef.packageName(), new Source(ef, origin));
        if (previous != null) {
            throw new ConverterException("Two export files were supplied for package "
                    + ef.packageName().replace('/', '.') + ": " + previous.origin() + " and " + origin);
        }
    }

    private static Optional<Source> findOnExportPath(String pkg, List<Path> exportPath)
            throws ConverterException {
        String entry = exportFileEntry(pkg);
        for (Path root : exportPath) {
            Optional<byte[]> data = readEntry(root, entry);
            if (data.isEmpty()) continue;
            String origin = root + "!/" + entry;
            ExportFile ef = parse(data.get(), origin);
            if (!ef.packageName().equals(pkg)) {
                throw new ConverterException("Export file " + origin + " describes package "
                        + ef.packageName().replace('/', '.') + ", expected " + pkg.replace('/', '.'));
            }
            return Optional.of(new Source(ef, origin));
        }
        return Optional.empty();
    }

    /**
     * Returns the location of a package's export file inside an export path entry:
     * {@code <package>/javacard/<last name component>.exp} (JCVM 3.1 §5.1, §5.2).
     *
     * @param pkg internal package name, e.g. {@code "javacard/framework"}
     * @return the relative path, e.g. {@code "javacard/framework/javacard/framework.exp"}
     */
    public static String exportFileEntry(String pkg) {
        return pkg + "/javacard/" + pkg.substring(pkg.lastIndexOf('/') + 1) + ".exp";
    }

    private static Optional<byte[]> readEntry(Path root, String entry) throws ConverterException {
        try {
            if (Files.isDirectory(root)) {
                Path file = root.resolve(entry);
                return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
            }
            if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".exp")) {
                throw new ConverterException("Export path entry " + root + " is an export file; pass it with"
                        + " importExportFile (export path entries are directories or JAR files that hold"
                        + " <package>/javacard/<name>.exp, JCVM 3.1 §5.2)");
            }
            if (Files.isRegularFile(root)) {
                try (ZipFile zip = new ZipFile(root.toFile())) {
                    ZipEntry ze = zip.getEntry(entry);
                    if (ze == null) return Optional.empty();
                    try (var in = zip.getInputStream(ze)) {
                        return Optional.of(in.readAllBytes());
                    }
                }
            }
            throw new ConverterException("Export path entry does not exist: " + root);
        } catch (IOException e) {
            throw new ConverterException("Cannot read export path entry " + root + ": " + e.getMessage(), e);
        }
    }

    private static ExportFile read(Path file) throws ConverterException {
        try {
            return ExportFileReader.readFile(file);
        } catch (IOException e) {
            throw new ConverterException(e.getMessage(), e);
        }
    }

    private static ExportFile parse(byte[] data, String origin) throws ConverterException {
        try {
            return ExportFileReader.read(data);
        } catch (IOException e) {
            throw new ConverterException("Cannot read export file " + origin + ": " + e.getMessage(), e);
        }
    }

    /** JCVM 3.1 §4.2.2.3: no two packages may have the same AID. */
    private static void checkUniqueAids(Collection<Source> sources) throws ConverterException {
        List<Source> seen = new ArrayList<>();
        for (Source s : sources) {
            for (Source other : seen) {
                if (Arrays.equals(other.exportFile().aid(), s.exportFile().aid())) {
                    throw new ConverterException("Packages " + other.exportFile().packageName().replace('/', '.')
                            + " (" + other.origin() + ") and " + s.exportFile().packageName().replace('/', '.')
                            + " (" + s.origin() + ") have the same AID "
                            + HexFormat.of().withUpperCase().formatHex(s.exportFile().aid()));
                }
            }
            seen.add(s);
        }
    }

    private static List<ImportedPackage> toImports(Collection<Source> sources) {
        List<ImportedPackage> result = new ArrayList<>();
        for (Source s : sources) {
            ExportFile ef = s.exportFile();
            result.add(new ImportedPackage(result.size(), ef.aid(), ef.majorVersion(), ef.minorVersion(), ef));
        }
        return result;
    }
}
