package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.LiveCardConfig.Setting;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Where live-card settings come from, highest priority first:
 * <ol>
 *   <li>system properties {@code jcx.livecard.<setting>} (e.g. {@code -Djcx.livecard.reader=ACR});</li>
 *   <li>environment variables {@code JCX_LIVECARD_<SETTING>} (e.g. {@code JCX_LIVECARD_AID_PREFIX});</li>
 *   <li>{@code livecard.properties} in the working directory and in each parent directory that belongs to the same
 *       build (it holds a {@code pom.xml}, {@code build.gradle(.kts)} or {@code settings.gradle(.kts)}), nearest
 *       first, so a module of a multi-module build finds the file of the project root; then the Maven project root
 *       when the build passes it as system property {@code maven.multiModuleProjectDirectory}. The search ends at
 *       the first directory outside the build;</li>
 *   <li>{@code ~/.jcx/livecard.properties};</li>
 *   <li>the defaults of {@link Setting}.</li>
 * </ol>
 * <p>Property files use the plain setting names ({@code reader=ACR}); an unknown name in a file is an error,
 * so a typo cannot silently fall back to a default. Live-card mode is switched on for one run, and only by the JVM
 * system property {@code jcx.livecard.enabled=true}: {@code enabled=true} in a settings file and the environment
 * variable {@code JCX_LIVECARD_ENABLED} with any value but {@code false} are refused, since they would arm every
 * run in that place, also IDE runs. The CI override {@code allowCi} never comes from a file; card keys never come
 * from a system property (Surefire's reports list the test JVM's system properties).</p>
 *
 * @param systemProperties the system properties
 * @param environment      the environment variables
 * @param files            the property files in priority order (missing files are skipped)
 */
public record ConfigSources(Map<String, String> systemProperties, Map<String, String> environment, List<Path> files) {

    /** File name of the settings file. */
    public static final String FILE_NAME = "livecard.properties";

    /** The command line option that switches live-card mode on for one run. */
    private static final String ENABLE_PROPERTY = "-D" + Setting.ENABLED.systemProperty() + "=true";

    /** The backend switch of {@code @JavaCardTest} classes; {@code livecard} switches live-card mode on. */
    static final String BACKEND_PROPERTY = "jcx.backend";

    /** Spellings of the real-card backend in {@value #BACKEND_PROPERTY}. */
    private static final List<String> LIVECARD_BACKENDS = List.of("livecard", "live-card");

    /** Files that make a directory part of a build: Maven and Gradle. */
    private static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts");

    /**
     * A resolved setting value and where it came from.
     *
     * @param text   the raw value
     * @param origin the source, for messages (e.g. "system property jcx.livecard.kvn")
     */
    public record Value(String text, String origin) {
    }

    /**
     * Copies the sources.
     */
    public ConfigSources {
        systemProperties = Map.copyOf(systemProperties);
        environment = Map.copyOf(environment);
        files = List.copyOf(files);
    }

    /**
     * Returns the standard sources of this JVM: its system properties and environment, the settings files of
     * {@link #settingsFiles(Path, String, Path)} for the working directory, and {@code ~/.jcx/livecard.properties}.
     *
     * @return the standard sources
     */
    public static ConfigSources standard() {
        Map<String, String> properties = new HashMap<>();
        System.getProperties().forEach((key, value) -> properties.put(String.valueOf(key), String.valueOf(value)));
        List<Path> files = settingsFiles(Path.of(""), properties.get("maven.multiModuleProjectDirectory"),
                Path.of(System.getProperty("user.home")));
        return new ConfigSources(properties, System.getenv(), files);
    }

    /**
     * Returns the settings files to read, in priority order: {@value #FILE_NAME} in the working directory and in
     * each of its parent directories that belongs to the same build (it holds a {@code pom.xml},
     * {@code build.gradle(.kts)} or {@code settings.gradle(.kts)}), nearest first; then in the project root, if
     * given and not among them; last {@code ~/.jcx/livecard.properties}. The parent directories end at the first one
     * outside the build, so that no settings file above the project is read.
     *
     * @param workingDirectory the working directory of the tests
     * @param projectRoot      the Maven project root ({@code maven.multiModuleProjectDirectory}), or null
     * @param home             the user's home directory
     * @return the files (missing ones are skipped when reading)
     */
    static List<Path> settingsFiles(Path workingDirectory, String projectRoot, Path home) {
        Set<Path> files = new LinkedHashSet<>();
        Path directory = workingDirectory.toAbsolutePath().normalize();
        files.add(directory.resolve(FILE_NAME));
        for (Path parent = directory.getParent(); parent != null && inBuild(parent); parent = parent.getParent()) {
            files.add(parent.resolve(FILE_NAME));
        }
        if (projectRoot != null && !projectRoot.isBlank()) {
            files.add(Path.of(projectRoot).toAbsolutePath().normalize().resolve(FILE_NAME));
        }
        files.add(home.toAbsolutePath().normalize().resolve(".jcx").resolve(FILE_NAME));
        return List.copyOf(files);
    }

    /**
     * Returns the root of the build the tests run in: the Maven project root ({@code maven.multiModuleProjectDirectory})
     * when the build passes it, else the outermost build directory around the working directory (the last
     * directory searched for a settings file before {@code ~/.jcx}).
     *
     * @return the project root, if known
     */
    public Optional<Path> projectRoot() {
        String configured = systemProperties.get("maven.multiModuleProjectDirectory");
        if (configured != null && !configured.isBlank()) {
            return Optional.of(Path.of(configured).toAbsolutePath().normalize());
        }
        return files.size() < 2 ? Optional.empty() : Optional.ofNullable(files.get(files.size() - 2).getParent());
    }

    private static boolean inBuild(Path directory) {
        return BUILD_FILES.stream().anyMatch(name -> Files.isRegularFile(directory.resolve(name)));
    }

    /**
     * Resolves every setting.
     *
     * @return the value of each setting with its origin
     * @throws LiveCardException if a settings file cannot be read or names an unknown setting
     */
    public Map<Setting, Value> resolve() {
        requireNoKeysProperty();
        requireNoEnabledVariable();
        List<Map.Entry<Path, Map<String, String>>> fileValues = new ArrayList<>();
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                fileValues.add(Map.entry(file, read(file)));
            }
        }
        Map<Setting, Value> values = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) {
            values.put(setting, resolve(setting, fileValues));
        }
        return values;
    }

    /**
     * Card keys never come from a system property: Maven passes {@code -D} properties to the test JVM, and Surefire
     * writes that JVM's system properties into its XML reports (process listings show them too). Only the public
     * GlobalPlatform test keys ({@code test}) are accepted there.
     */
    private void requireNoKeysProperty() {
        String keys = systemProperties.get(Setting.KEYS.systemProperty());
        if (keys != null && !keys.strip().equalsIgnoreCase("test")) {
            throw new LiveCardException("Card keys are not accepted as the system property "
                    + Setting.KEYS.systemProperty() + " (system properties end up in Surefire's XML reports and in"
                    + " process listings): set the environment variable " + Setting.KEYS.environmentVariable()
                    + " or keys= in a livecard.properties file that is never committed");
        }
    }

    /**
     * Live-card mode is switched on only by the JVM system property: an environment variable would arm every run in
     * that shell, of every project, also from an IDE. {@code false} changes nothing and is accepted.
     */
    private void requireNoEnabledVariable() {
        String variable = Setting.ENABLED.environmentVariable();
        String value = environment.get(variable);
        if (value != null && !value.strip().equalsIgnoreCase("false")) {
            throw new LiveCardException(variable + "=" + value.strip() + " is refused: live-card mode is switched on"
                    + " only for one run, with the JVM system property " + ENABLE_PROPERTY + " (on the Maven command"
                    + " line, or as a VM option of an IDE run), never by an environment variable, which would arm every"
                    + " run in this environment; unset " + variable);
        }
    }

    private Value resolve(Setting setting, List<Map.Entry<Path, Map<String, String>>> fileValues) {
        String property = systemProperties.get(setting.systemProperty());
        if (property != null) {
            return new Value(property, "system property " + setting.systemProperty());
        }
        if (setting == Setting.ENABLED) {
            // switched on by a system property only: the environment and files may only say false. The backend
            // switch of @JavaCardTest classes (-Djcx.backend=livecard) is such a system property too.
            String backend = systemProperties.get(BACKEND_PROPERTY);
            if (backend != null && LIVECARD_BACKENDS.contains(backend.strip().toLowerCase(java.util.Locale.ROOT))) {
                return new Value("true", "system property " + BACKEND_PROPERTY + "=" + backend.strip());
            }
            return new Value(setting.defaultValue(), "default");
        }
        String variable = environment.get(setting.environmentVariable());
        if (variable != null) {
            return new Value(variable, "environment variable " + setting.environmentVariable());
        }
        for (Map.Entry<Path, Map<String, String>> file : fileValues) {
            String value = file.getValue().get(setting.key());
            if (value != null) {
                return new Value(value, "file " + file.getKey());
            }
        }
        return new Value(setting.defaultValue(), "default");
    }

    /** The CI override and {@code enabled=true} switch a single run on, so a settings file must not hold them. */
    private static void requireNoRunSwitches(Path file, Properties properties) {
        if (properties.containsKey("allowCi")) {
            throw new LiveCardException("allowCi in " + file + " has no effect: the CI override is accepted only"
                    + " as a system property on the command line (-D" + ContinuousIntegration.ALLOW_PROPERTY
                    + "=true)");
        }
        String enabled = properties.getProperty(Setting.ENABLED.key());
        if (enabled != null && !enabled.strip().equalsIgnoreCase("false")) {
            throw new LiveCardException(Setting.ENABLED.key() + "=" + enabled.strip() + " in " + file + " is refused:"
                    + " live-card mode is switched on only for one run, with the JVM system property "
                    + ENABLE_PROPERTY + " (on the Maven command line, or as a VM option of an IDE run); in a settings"
                    + " file every run of the tests, also from an IDE, would reach the card");
        }
    }

    private static Map<String, String> read(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new LiveCardException("Cannot read live-card settings from " + file + ": " + e.getMessage(), e);
        }
        requireNoRunSwitches(file, properties);
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (Setting.byKey(name).isEmpty()) {
                throw new LiveCardException("Unknown live-card setting '" + name + "' in " + file + "; known settings: "
                        + Arrays.stream(Setting.values()).map(Setting::key).toList());
            }
            values.put(name, properties.getProperty(name));
        }
        return values;
    }
}
