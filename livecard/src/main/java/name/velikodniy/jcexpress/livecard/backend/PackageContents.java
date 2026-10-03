package name.velikodniy.jcexpress.livecard.backend;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.backend.BuildDescriptor;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The class files of a Java package as the test JVM sees them, read from every class path entry that holds the
 * package: classes directories and jars (a reactor build at {@code package} or later, a repository artifact), and a
 * package split over the main and the test classes. Where two entries hold the same class, the first in class path
 * order wins, as for the class loader.
 *
 * <p>The GlobalPlatform backends convert from it what a card needs of the package: its applets
 * ({@link #applets(Collection)}) and the classes they use ({@link #closure(Collection)}), so that JUnit test classes
 * of the package are not converted. A CAP file links only against the export files of the packages it imports
 * (JCVM 3.1 §4.3.3); {@link #foreignPackages(Map)} names the packages outside the Java Card API that the classes
 * use.</p>
 */
final class PackageContents {

    /** Namespaces of the Java Card API, which the converter links against its built-in export data. */
    private static final List<String> API_NAMESPACES = List.of("java/", "javacard/", "javacardx/");
    private static final String INSTALL = "install";
    private static final String INSTALL_DESCRIPTOR = "([BSB)V";

    private final String packageName;
    private final String packagePath;
    private final ClassLoader loader;
    private final List<Path> entries;
    /** Class files by internal name, each from the first entry that holds it. */
    private final Map<String, byte[]> classes;
    /** The entry each class file was read from. */
    private final Map<String, Path> origins;

    private PackageContents(String packageName, ClassLoader loader, List<Path> entries, Map<String, byte[]> classes,
                            Map<String, Path> origins) {
        this.packageName = packageName;
        this.packagePath = packageName.replace('.', '/');
        this.loader = loader;
        this.entries = List.copyOf(entries);
        this.classes = classes;
        this.origins = origins;
    }

    /**
     * Reads a package from the entries of a class loader that hold it ({@link ClassLoader#getResources(String)} of
     * the package directory, in class path order) and from entries known to hold it (jars without directory entries).
     *
     * @param packageName  the package, e.g. {@code com.example.wallet}
     * @param loader       the class loader of the package's classes
     * @param knownEntries further entries, e.g. where the declared applets were loaded from
     * @return the contents
     * @throws UncheckedIOException if an entry cannot be read
     */
    static PackageContents read(String packageName, ClassLoader loader, Collection<Path> knownEntries) {
        String path = packageName.replace('.', '/');
        Set<Path> entries = new LinkedHashSet<>();
        try {
            Enumeration<URL> found = loader.getResources(path + "/");
            while (found.hasMoreElements()) {
                entryOf(found.nextElement(), path).ifPresent(entries::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot look up package " + packageName + ": " + e.getMessage(), e);
        }
        knownEntries.forEach(entry -> entries.add(entry.toAbsolutePath().normalize()));
        Map<String, byte[]> classes = new LinkedHashMap<>();
        Map<String, Path> origins = new LinkedHashMap<>();
        for (Path entry : entries) {
            classFiles(entry, path).forEach((name, bytes) -> {
                if (classes.putIfAbsent(name, bytes) == null) {
                    origins.put(name, entry);
                }
            });
        }
        List<Path> holding = entries.stream().filter(origins::containsValue).toList();
        return new PackageContents(packageName, loader, holding, classes, origins);
    }

    /**
     * Returns the class path entries that hold the package.
     *
     * @return directories and jars, in class path order
     */
    List<Path> entries() {
        return entries;
    }

    /**
     * Returns the applets of the package (JCVM 3.1 §6.6: classes that extend {@code javacard.framework.Applet}
     * directly or indirectly and define the static {@code install(byte[], short, byte)} method): the given ones; of
     * an entry with a build descriptor of the Maven plugin, the applets the descriptor names (those whose class file
     * is there); of every other entry, each concrete applet class.
     *
     * @param declared applet classes the test run declares (binary names), first
     * @return binary class names, the declared ones first, then the others sorted by name
     */
    List<String> applets(Collection<String> declared) {
        Set<String> applets = new LinkedHashSet<>(declared);
        for (Path entry : entries) {
            Optional<BuildDescriptor> build = BuildDescriptor.read(entry, packageName);
            if (build.isPresent()) {
                build.get().applets().keySet().stream().sorted()
                        .filter(name -> classes.containsKey(name.replace('.', '/'))).forEach(applets::add);
            } else {
                new TreeMap<>(origins).forEach((name, origin) -> {
                    if (origin.equals(entry) && isApplet(name)) {
                        applets.add(name.replace('/', '.'));
                    }
                });
            }
        }
        return List.copyOf(applets);
    }

    /**
     * Returns the class files a CAP file of the applets is converted from: the applets and every class of the package
     * they reference, transitively (superclasses, interfaces, types in descriptors, classes and members named by
     * instructions and exception handlers, as the converter links them).
     *
     * @param applets binary names of the applet classes
     * @return class files by binary class name, the applets first
     * @throws IllegalArgumentException if an applet class is not in the package
     */
    Map<String, byte[]> closure(Collection<String> applets) {
        Map<String, byte[]> closure = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String applet : applets) {
            String name = applet.replace('.', '/');
            if (!classes.containsKey(name)) {
                throw new IllegalArgumentException(applet + " is not a class of package " + packageName + " in "
                        + entries);
            }
            pending.add(name);
        }
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            byte[] bytes = classes.get(name);
            if (bytes == null || closure.containsKey(name.replace('/', '.'))) {
                continue;
            }
            closure.put(name.replace('/', '.'), bytes);
            for (ClassReferences.Reference reference : ClassReferences.scan(List.of(bytes))) {
                if (reference.ownerPackage().equals(packagePath)) {
                    pending.add(reference.owner());
                }
            }
        }
        return closure;
    }

    /**
     * Returns the packages outside the Java Card API ({@code java.*}, {@code javacard.*}, {@code javacardx.*}) that
     * class files use, each with the classes used and where the first use is.
     *
     * @param classFiles class files of the package
     * @return descriptions by package name (dotted), in the order of first use
     */
    Map<String, String> foreignPackages(Map<String, byte[]> classFiles) {
        Map<String, Set<String>> used = new LinkedHashMap<>();
        Map<String, String> firstUse = new LinkedHashMap<>();
        for (ClassReferences.Reference reference : ClassReferences.scan(List.copyOf(classFiles.values()))) {
            String owner = reference.ownerPackage();
            if (!owner.equals(packagePath) && API_NAMESPACES.stream().noneMatch(owner::startsWith)) {
                String pkg = owner.replace('/', '.');
                used.computeIfAbsent(pkg, key -> new LinkedHashSet<>())
                        .add(reference.owner().substring(owner.isEmpty() ? 0 : owner.length() + 1));
                firstUse.putIfAbsent(pkg, reference.location());
            }
        }
        Map<String, String> foreign = new LinkedHashMap<>();
        used.forEach((pkg, names) -> foreign.put(pkg, pkg + " (" + String.join(", ", names) + "; first used in "
                + firstUse.get(pkg) + ")"));
        return foreign;
    }

    /**
     * Writes class files into a classes directory, in the layout the converter reads.
     *
     * @param directory  the classes directory
     * @param classFiles class files by binary class name
     * @throws UncheckedIOException if a file cannot be written
     */
    static void write(Path directory, Map<String, byte[]> classFiles) {
        try {
            for (Map.Entry<String, byte[]> classFile : classFiles.entrySet()) {
                Path file = directory.resolve(classFile.getKey().replace('.', '/') + ".class");
                Files.createDirectories(file.getParent());
                Files.write(file, classFile.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the classes to convert to " + directory, e);
        }
    }

    /** A concrete class with {@code static install([BSB)V} that the loader's {@code Applet} is assignable from. */
    private boolean isApplet(String internalName) {
        ClassModel model = ClassFile.of().parse(classes.get(internalName));
        int flags = model.flags().flagsMask();
        if ((flags & (ClassFile.ACC_ABSTRACT | ClassFile.ACC_INTERFACE)) != 0 || !declaresInstall(model)) {
            return false;
        }
        try {
            return Applet.class.isAssignableFrom(Class.forName(internalName.replace('/', '.'), false, loader));
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean declaresInstall(ClassModel model) {
        for (MethodModel method : model.methods()) {
            if (method.methodName().equalsString(INSTALL) && method.methodType().equalsString(INSTALL_DESCRIPTOR)
                    && (method.flags().flagsMask() & ClassFile.ACC_STATIC) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The class path entry of a package directory URL: a classes directory or a jar. */
    private static Optional<Path> entryOf(URL url, String packagePath) {
        try {
            if (url.getProtocol().equals("file")) {
                Path directory = Path.of(url.toURI());
                for (int i = 0; i < packagePath.split("/").length && directory != null; i++) {
                    directory = directory.getParent();
                }
                return Optional.ofNullable(directory).map(path -> path.toAbsolutePath().normalize());
            }
            String spec = url.getFile();
            int separator = spec.indexOf("!/");
            if (url.getProtocol().equals("jar") && separator > 0) {
                return Optional.of(Path.of(new URI(spec.substring(0, separator))).toAbsolutePath().normalize());
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            // not a file system location (e.g. jrt:): not an entry a build writes
        }
        return Optional.empty();
    }

    /** The class files directly in the package directory of an entry, by internal name. */
    private static Map<String, byte[]> classFiles(Path entry, String packagePath) {
        try {
            return Files.isDirectory(entry) ? directoryClassFiles(entry.resolve(packagePath), packagePath)
                    : Files.isRegularFile(entry) ? jarClassFiles(entry, packagePath) : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the classes of " + packagePath.replace('/', '.') + " in "
                    + entry + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, byte[]> directoryClassFiles(Path directory, String packagePath) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.isDirectory(directory)) {
            return files;
        }
        try (Stream<Path> listing = Files.list(directory)) {
            for (Path file : listing.filter(Files::isRegularFile).toList()) {
                classFileName(file.getFileName().toString())
                        .ifPresent(name -> files.put(packagePath + "/" + name, read(file)));
            }
        }
        return files;
    }

    private static Map<String, byte[]> jarClassFiles(Path jar, String packagePath) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(packagePath + "/")
                        && name.indexOf('/', packagePath.length() + 1) < 0) {
                    Optional<String> simple = classFileName(name.substring(packagePath.length() + 1));
                    if (simple.isPresent()) {
                        files.put(packagePath + "/" + simple.get(), zip.getInputStream(entry).readAllBytes());
                    }
                }
            }
        }
        return files;
    }

    /** The class name of a class file name; none for other files and for package-info and module-info. */
    private static Optional<String> classFileName(String fileName) {
        if (!fileName.endsWith(".class") || fileName.contains("-")) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(0, fileName.length() - ".class".length()));
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /**
     * Describes where the classes come from, for the transcript.
     *
     * @param classFiles class files by binary class name
     * @return e.g. {@code A, B (from target/classes, target/test-classes)}
     */
    String describe(Map<String, byte[]> classFiles) {
        List<String> from = classFiles.keySet().stream().map(name -> origins.get(name.replace('.', '/'))).distinct()
                .map(Path::toString).toList();
        return String.join(", ", classFiles.keySet()) + " (from " + String.join(", ", from) + ")";
    }
}
