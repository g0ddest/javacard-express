package name.velikodniy.jcexpress.backend;

import name.velikodniy.jcexpress.AID;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * How the Maven plugin built a package: the build descriptor {@code META-INF/javacard/<package>.properties} that
 * {@code javacard-express-maven-plugin} writes into the classes directory, and so into the jar, of the module that
 * holds the package. The card test backends that convert applets (the simulated GlobalPlatform card and the real
 * card) use its settings, so that a test on a card runs the package as the build converted it; only the AIDs are
 * those of the run ({@link AidScheme}). Its {@link #project()} gives the run's AID prefix
 * ({@link AidScheme#forProject(String)}).
 *
 * @param packageName     the package, e.g. {@code com.example.wallet}
 * @param packageAid      the package AID of the build
 * @param packageVersion  the package version {@code <major>.<minor>}
 * @param javaCardVersion the target platform of the build, e.g. {@code 3.0.5}
 * @param supportInt32    whether the build converted with int support
 * @param applets         the applet AIDs of the build, by class name
 * @param project         {@code groupId:artifactId} of the module that built the package
 */
public record BuildDescriptor(String packageName, AID packageAid, String packageVersion, String javaCardVersion,
                              boolean supportInt32, Map<String, AID> applets, String project) {

    /** Where the descriptors are in a classes directory or jar. */
    public static final String DIRECTORY = "META-INF/javacard/";

    private static final ClassValue<Optional<BuildDescriptor>> BY_CLASS = new ClassValue<>() {
        @Override
        protected Optional<BuildDescriptor> computeValue(Class<?> type) {
            return classPathEntry(type).flatMap(entry -> read(entry, type.getPackageName()));
        }
    };

    /**
     * Copies the applets.
     *
     * @param packageName     the package
     * @param packageAid      the package AID of the build
     * @param packageVersion  the package version
     * @param javaCardVersion the target platform of the build
     * @param supportInt32    whether the build converted with int support
     * @param applets         the applet AIDs of the build
     * @param project         {@code groupId:artifactId}
     */
    public BuildDescriptor {
        applets = Map.copyOf(applets);
    }

    /**
     * Returns the descriptor of a class's package, read from where the class was loaded from (the classes directory
     * or jar of its module).
     *
     * @param type an applet class
     * @return the descriptor, or empty if the plugin did not build the package (applets in test sources, a build
     *         without the plugin or with {@code classesOutput} off)
     * @throws IllegalStateException if the descriptor exists but cannot be read or is invalid
     */
    public static Optional<BuildDescriptor> of(Class<?> type) {
        return BY_CLASS.get(type);
    }

    /**
     * Reads the descriptor of a package from a class path entry.
     *
     * @param classPathEntry a classes directory or jar
     * @param packageName    the package
     * @return the descriptor, or empty if the entry has none for the package
     * @throws IllegalStateException if the descriptor cannot be read or is invalid
     */
    public static Optional<BuildDescriptor> read(Path classPathEntry, String packageName) {
        String resource = DIRECTORY + packageName + ".properties";
        String source = classPathEntry + (Files.isDirectory(classPathEntry) ? "/" : "!/") + resource;
        try {
            Optional<Properties> properties = Files.isDirectory(classPathEntry)
                    ? load(classPathEntry.resolve(resource)) : loadFromJar(classPathEntry, resource);
            return properties.map(p -> parse(p, packageName, source));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the build descriptor " + source + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns the major version of the package.
     *
     * @return the number before the dot of {@link #packageVersion()}
     */
    public int majorVersion() {
        return Integer.parseInt(packageVersion.substring(0, packageVersion.indexOf('.')));
    }

    /**
     * Returns the minor version of the package.
     *
     * @return the number after the dot of {@link #packageVersion()}
     */
    public int minorVersion() {
        return Integer.parseInt(packageVersion.substring(packageVersion.indexOf('.') + 1));
    }

    private static Optional<Properties> load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties properties = new Properties();
            properties.load(in);
            return Optional.of(properties);
        }
    }

    private static Optional<Properties> loadFromJar(Path jar, String resource) throws IOException {
        if (!Files.isRegularFile(jar)) {
            return Optional.empty();
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getEntry(resource);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = file.getInputStream(entry)) {
                Properties properties = new Properties();
                properties.load(in);
                return Optional.of(properties);
            }
        }
    }

    private static BuildDescriptor parse(Properties p, String packageName, String source) {
        try {
            if (!packageName.equals(p.getProperty("package"))) {
                throw new IllegalArgumentException("it describes package " + p.getProperty("package"));
            }
            Map<String, AID> applets = new LinkedHashMap<>();
            p.stringPropertyNames().stream().filter(key -> key.startsWith("applet.")).sorted()
                    .forEach(key -> applets.put(key.substring("applet.".length()), AID.fromHex(p.getProperty(key))));
            String version = required(p, "packageVersion");
            if (!version.matches("\\d{1,3}\\.\\d{1,3}")) {
                throw new IllegalArgumentException("packageVersion " + version + " is not <major>.<minor>");
            }
            return new BuildDescriptor(packageName, AID.fromHex(required(p, "packageAid")), version,
                    required(p, "javaCardVersion"), Boolean.parseBoolean(required(p, "supportInt32")), applets,
                    required(p, "project"));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid build descriptor " + source + ": " + e.getMessage()
                    + ". Rebuild the module (mvn process-classes or later).", e);
        }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is missing");
        }
        return value.strip();
    }

    private static Optional<Path> classPathEntry(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
