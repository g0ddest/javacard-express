package name.velikodniy.jcexpress.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Reads class files by name from class path entries (directories and jar files), in class path
 * order. Jar files are opened on first use and closed by {@link #close()}.
 */
final class ClassPathLookup implements AutoCloseable {

    private final List<Path> entries;
    private final Map<Path, JarFile> openJars = new LinkedHashMap<>();

    /**
     * @param entries class path entries; missing entries are ignored
     */
    ClassPathLookup(List<Path> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Reads the class file of a class.
     *
     * @param internalName internal class name, e.g. {@code javacard/framework/Applet}
     * @return the class file bytes, or empty if no entry contains the class
     * @throws IOException if an entry exists but cannot be read
     */
    Optional<byte[]> read(String internalName) throws IOException {
        String resource = internalName + ".class";
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                Path file = entry.resolve(resource);
                if (Files.isRegularFile(file)) {
                    return Optional.of(Files.readAllBytes(file));
                }
            } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
                Optional<byte[]> bytes = readFromJar(entry, resource);
                if (bytes.isPresent()) {
                    return bytes;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<byte[]> readFromJar(Path jar, String resource) throws IOException {
        JarFile file = openJars.get(jar);
        if (file == null) {
            file = new JarFile(jar.toFile());
            openJars.put(jar, file);
        }
        ZipEntry zipEntry = file.getEntry(resource);
        if (zipEntry == null) {
            return Optional.empty();
        }
        try (InputStream in = file.getInputStream(zipEntry)) {
            return Optional.of(in.readAllBytes());
        }
    }

    @Override
    public void close() {
        for (JarFile jar : openJars.values()) {
            try {
                jar.close();
            } catch (IOException ignored) {
                // read-only access; nothing to recover
            }
        }
        openJars.clear();
    }
}
