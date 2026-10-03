package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.BuildDescriptor;
import name.velikodniy.jcexpress.livecard.AppletPackage;
import name.velikodniy.jcexpress.livecard.LiveCardException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A Java package as the GlobalPlatform backends convert and load it: every applet of the package (not only the
 * declared ones, so that any of them can be installed once the package is loaded, also imperatively with computed
 * parameters), each under its module AID of the run's {@link AidScheme}, and the class files those applets need,
 * read from every class path entry that holds the package ({@link PackageContents}) and written to a temporary
 * classes directory for the converter, which {@link #close()} deletes.
 *
 * <p>A package that uses packages outside the Java Card API is refused before anything is converted: the backends
 * convert without the export files of other packages and do not load them first (JCVM 3.1 §4.3.3, §6.7).</p>
 */
final class PackageLoad implements AutoCloseable {

    private final AppletPackage pkg;
    private final Map<String, AID> applets;
    private final List<Path> classPath;
    private final Path classesDirectory;
    private final String converted;
    private final Optional<BuildDescriptor> build;

    private PackageLoad(AppletPackage pkg, Map<String, AID> applets, List<Path> classPath, Path classesDirectory,
                        String converted, Optional<BuildDescriptor> build) {
        this.pkg = pkg;
        this.applets = Map.copyOf(applets);
        this.classPath = List.copyOf(classPath);
        this.classesDirectory = classesDirectory;
        this.converted = converted;
        this.build = build;
    }

    /**
     * Plans the load of the package of an applet.
     *
     * @param applet      the applet whose install needs the package
     * @param declaredRun everything the test class run declares
     * @param aids        the run's AIDs
     * @param intSupport  whether to convert with int support, if the run says so (otherwise as the build did)
     * @return the planned load; close it when the package is loaded
     * @throws LiveCardException if the package uses packages outside the Java Card API
     */
    static PackageLoad of(AppletDeclaration applet, Collection<AppletDeclaration> declaredRun, AidScheme aids,
                          Optional<Boolean> intSupport) {
        Set<AppletDeclaration> declared = new LinkedHashSet<>();
        declaredRun.stream().filter(other -> other.packageName().equals(applet.packageName())).forEach(declared::add);
        declared.add(applet);
        ClassLoader loader = applet.appletClass().getClassLoader();
        PackageContents contents = PackageContents.read(applet.packageName(), loader,
                declared.stream().map(AppletDeclaration::classPathEntry).toList());
        List<String> names = contents.applets(declared.stream().map(d -> d.appletClass().getName()).toList());
        Map<String, byte[]> classes = contents.closure(names);
        requireOnlyApiImports(applet.packageName(), contents.foreignPackages(classes));
        Map<String, AID> applets = new LinkedHashMap<>();
        names.forEach(name -> applets.put(name, moduleAid(name, loader, aids)));
        Optional<BuildDescriptor> build = BuildDescriptor.of(applet.appletClass());
        Path directory = classesDirectory(applet.packageName(), classes);
        AppletPackage pkg = AppletPackage.of(directory, applet.packageName(), applet.packageAid());
        for (Map.Entry<String, AID> module : applets.entrySet()) {
            pkg = pkg.withApplet(module.getKey(), module.getValue());
        }
        if (build.isPresent()) {
            pkg = pkg.withVersion(build.get().majorVersion(), build.get().minorVersion());
        }
        boolean withInt = intSupport.orElse(build.map(BuildDescriptor::supportInt32).orElse(false));
        String converted = "applets " + String.join(", ", names) + "; classes " + contents.describe(classes);
        return new PackageLoad(withInt ? pkg.withIntSupport() : pkg, applets, contents.entries(), directory,
                converted, build);
    }

    /**
     * Returns the package to deploy.
     *
     * @return the package, with every applet and its module AID
     */
    AppletPackage pkg() {
        return pkg;
    }

    /**
     * Returns the applets of the package.
     *
     * @return module AIDs by applet class name
     */
    Map<String, AID> applets() {
        return applets;
    }

    /**
     * Returns where the package's classes are: what the simulated card loads them from.
     *
     * @return the class path entries that hold the package
     */
    List<Path> classPath() {
        return classPath;
    }

    /**
     * Returns what is converted, for the transcript and messages.
     *
     * @return e.g. {@code applets com.example.A; classes com.example.A, com.example.B (from target/classes)}
     */
    String converted() {
        return converted;
    }

    /**
     * Returns the build descriptor of the package, if the Maven plugin built it.
     *
     * @return the descriptor
     */
    Optional<BuildDescriptor> build() {
        return build;
    }

    /** Deletes the temporary classes directory. */
    @Override
    public void close() {
        delete(classesDirectory);
    }

    private static void requireOnlyApiImports(String packageName, Map<String, String> foreign) {
        if (foreign.isEmpty()) {
            return;
        }
        throw new LiveCardException("Package " + packageName + " uses classes of other packages outside the Java Card"
                + " API: " + String.join("; ", foreign.values()) + ". The GlobalPlatform backends (simulated-gp,"
                + " livecard) cannot load a package that depends on other packages yet: its conversion needs their"
                + " export files (JCVM 3.1 §4.3.3) and a card links it only against packages already loaded (§4.5.2),"
                + " and these backends neither convert nor load the imported packages. Run such tests on the embedded"
                + " backend: annotate the test class with @EnabledOnBackend(Mode.EMBEDDED), or run without"
                + " -Djcx.backend. Nothing was converted or loaded");
    }

    private static AID moduleAid(String className, ClassLoader loader, AidScheme aids) {
        try {
            return aids.moduleAid(Class.forName(className, false, loader));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load applet class " + className, e);
        }
    }

    /** A temporary classes directory with the class files to convert; deleted again if they cannot be written. */
    private static Path classesDirectory(String packageName, Map<String, byte[]> classes) {
        Path directory;
        try {
            directory = Files.createTempDirectory("jcx-" + packageName + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create a directory for the classes of " + packageName, e);
        }
        try {
            PackageContents.write(directory, classes);
        } catch (UncheckedIOException e) {
            try {
                delete(directory);
            } catch (UncheckedIOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        return directory;
    }

    private static void delete(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the temporary classes " + directory, e);
        }
    }
}
