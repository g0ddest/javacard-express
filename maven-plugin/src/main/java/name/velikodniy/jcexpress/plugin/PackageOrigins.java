package name.velikodniy.jcexpress.plugin;

import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Where the classes of a package come from: the module itself, a dependency, or neither. A
 * failure to link a package can then name the dependency whose export file is missing.
 */
final class PackageOrigins {

    /** Where the classes of a package come from. */
    sealed interface Origin permits ThisModule, FromDependency, Unknown {
    }

    /** The package is compiled in this module (another package than the converted one). */
    record ThisModule() implements Origin {
    }

    /**
     * The package's classes are in a dependency.
     *
     * @param artifact the dependency
     */
    record FromDependency(Artifact artifact) implements Origin {
    }

    /** No class of the package is in this module or in a dependency. */
    record Unknown() implements Origin {
    }

    private final ClassIndex index;
    private final List<Artifact> artifacts;

    /**
     * @param index     the classes of the module
     * @param artifacts the resolved dependencies of the module
     */
    PackageOrigins(ClassIndex index, Collection<Artifact> artifacts) {
        this.index = index;
        this.artifacts = List.copyOf(artifacts);
    }

    /**
     * @param packageName a package (dot notation)
     * @return where its classes come from
     */
    Origin of(String packageName) {
        if (index.packages().contains(packageName)) {
            return new ThisModule();
        }
        String directory = packageName.replace('.', '/') + "/";
        return artifacts.stream()
                .filter(a -> a.getFile() != null && containsClasses(a.getFile(), directory))
                .findFirst()
                .<Origin>map(FromDependency::new)
                .orElseGet(Unknown::new);
    }

    private static boolean containsClasses(File file, String directory) {
        Path path = file.toPath();
        if (Files.isDirectory(path)) {
            return hasClassFile(path.resolve(directory));
        }
        if (!Files.isRegularFile(path) || !file.getName().endsWith(".jar")) {
            return false;
        }
        try (JarFile jar = new JarFile(file)) {
            return jar.stream().map(JarEntry::getName)
                    .anyMatch(n -> n.startsWith(directory) && n.endsWith(".class")
                            && n.indexOf('/', directory.length()) < 0);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasClassFile(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(f -> f.getFileName().toString().endsWith(".class"));
        } catch (IOException e) {
            return false;
        }
    }
}
