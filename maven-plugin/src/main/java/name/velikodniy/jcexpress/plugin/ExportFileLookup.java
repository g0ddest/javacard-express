package name.velikodniy.jcexpress.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Collects the export files the converter needs for the packages the converted package imports
 * (JCVM 3.1 &sect;4.1.1, chapter 5). Sources, in this order:
 * <ol>
 *   <li>{@code <importExportFiles>}: export files given one by one;</li>
 *   <li>dependencies of type {@code exp} (the export files this plugin attaches);</li>
 *   <li>{@code <exportPath>}: directories and jars searched for the export file of each
 *       referenced package;</li>
 *   <li>the compile class path (dependency jars and directories), for referenced packages outside
 *       the Java Card API ({@code java.*}, {@code javacard.*}, {@code javacardx.*}, which the
 *       converter knows).</li>
 * </ol>
 * Searches look where JCVM 3.1 &sect;5.1 and &sect;5.2 put an export file: in the
 * {@code javacard} subdirectory of the package directory, named after the last package name
 * component, e.g. {@code com/example/lib/javacard/lib.exp}. Export files inside jars are copied
 * to a working directory first.
 */
final class ExportFileLookup {

    private static final List<String> API_PREFIXES = List.of("java.", "javacard.", "javacardx.");

    private final Path workDirectory;
    private final Log log;

    /**
     * @param workDirectory where export files found inside jars are extracted
     * @param log           receives the export files used
     */
    ExportFileLookup(Path workDirectory, Log log) {
        this.workDirectory = workDirectory;
        this.log = log;
    }

    /**
     * Collects the export files.
     *
     * @param importExportFiles explicitly configured export files (may be {@code null})
     * @param artifacts         the resolved dependencies of the project
     * @param exportPath        configured directories and jars to search (may be {@code null})
     * @param classPath         the compile class path entries
     * @param packages          packages referenced by the converted package (dot notation)
     * @return the export files, without duplicates, in lookup order
     * @throws MojoExecutionException if a configured file or path entry does not exist, or a jar
     *                                cannot be read
     */
    List<Path> collect(List<File> importExportFiles, Collection<Artifact> artifacts, List<File> exportPath,
                       List<Path> classPath, Set<String> packages) throws MojoExecutionException {
        Set<Path> found = new LinkedHashSet<>(configuredFiles(importExportFiles));
        for (Artifact artifact : artifacts) {
            if (BuildOutputs.EXP_TYPE.equals(artifact.getType()) && artifact.getFile() != null) {
                found.add(artifact.getFile().toPath());
            }
        }
        List<Path> searchPath = configuredPath(exportPath);
        for (String pkg : packages) {
            search(pkg, searchPath).or(() -> isApi(pkg) ? Optional.empty() : searchQuietly(pkg, classPath))
                    .ifPresent(found::add);
        }
        found.forEach(file -> log.debug("Export file: " + file));
        return List.copyOf(found);
    }

    private static List<Path> configuredFiles(List<File> files) throws MojoExecutionException {
        List<Path> result = new ArrayList<>();
        for (File file : files == null ? List.<File>of() : files) {
            if (!file.isFile()) {
                throw new MojoExecutionException("Export file not found: " + file.getAbsolutePath()
                        + " (configured in <importExportFiles>)");
            }
            result.add(file.toPath());
        }
        return result;
    }

    private static List<Path> configuredPath(List<File> entries) throws MojoExecutionException {
        List<Path> result = new ArrayList<>();
        for (File entry : entries == null ? List.<File>of() : entries) {
            if (!entry.exists()) {
                throw new MojoExecutionException("<exportPath> entry not found: " + entry.getAbsolutePath());
            }
            result.add(entry.toPath());
        }
        return result;
    }

    private Optional<Path> search(String pkg, List<Path> entries) throws MojoExecutionException {
        String resource = location(pkg);
        for (Path entry : entries) {
            Optional<Path> file = Files.isDirectory(entry) ? existing(entry.resolve(resource)) : extract(entry, resource);
            if (file.isPresent()) {
                return file;
            }
        }
        return Optional.empty();
    }

    private Optional<Path> searchQuietly(String pkg, List<Path> classPath) {
        try {
            return search(pkg, classPath.stream().filter(Files::exists).toList());
        } catch (MojoExecutionException e) {
            log.debug("Cannot search the class path for the export file of " + pkg + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Path> extract(Path jar, String resource) throws MojoExecutionException {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getEntry(resource);
            if (entry == null) {
                return Optional.empty();
            }
            Path target = workDirectory.resolve(resource);
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream(entry)) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(target);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read " + resource + " from " + jar + ": " + e.getMessage(), e);
        }
    }

    private static Optional<Path> existing(Path file) {
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /**
     * @param pkg package name in dot notation
     * @return the path of its export file inside a directory or jar (JCVM 3.1 &sect;5.1, &sect;5.2)
     */
    static String location(String pkg) {
        String last = pkg.substring(pkg.lastIndexOf('.') + 1);
        return pkg.replace('.', '/') + "/javacard/" + last + ".exp";
    }

    private static boolean isApi(String pkg) {
        return API_PREFIXES.stream().anyMatch(pkg::startsWith);
    }
}
