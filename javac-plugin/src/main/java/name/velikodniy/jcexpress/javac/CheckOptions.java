package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.PackageCheck;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The options of the compile-time check, given to javac as annotation processor options ({@code -Aname=value}). The
 * names of the settings are the user properties of the Maven plugin's settings, so that a build passes the same values
 * to the check and to the conversion; {@value #EXPORT_FILES} and {@value #EXPORT_PATH} are options of the check only
 * (the plugin's {@code importExportFiles} and {@code exportPath} have no user property). Without an option the check
 * uses the default of the conversion.
 *
 * @param enabled      whether the check runs ({@value #CHECK})
 * @param settings     the conversion settings the check stands for ({@value #VERSION}, {@value #SUPPORT_INT32},
 *                     {@value #GLOBAL_PLATFORM_API}, and of {@value #EXPORT_FILES} and {@value #EXPORT_PATH} the
 *                     entries that are there); {@code null} when the check is off
 * @param packageNames the packages to check in dot notation ({@value #PACKAGE_NAME}); empty for the package the
 *                     conversion would choose
 * @param notSupplied  entries of {@value #EXPORT_FILES} and {@value #EXPORT_PATH} that are not there when javac runs
 *                     (another module may write them later, when it converts) or that hold a Maven expression nothing
 *                     resolved: the check does not link the package then, and leaves it to the conversion
 */
public record CheckOptions(boolean enabled, PackageCheck.Settings settings, List<String> packageNames,
                           List<String> notSupplied) {

    /** {@code false} turns the check off. */
    public static final String CHECK = "javacard.check";
    /** The target Java Card platform, e.g. {@code 3.0.4}. */
    public static final String VERSION = "javacard.version";
    /** {@code true} when the target supports the int type (JCVM 3.1 §2.2.3.1). */
    public static final String SUPPORT_INT32 = "javacard.supportInt32";
    /** The version of {@code org.globalplatform} the conversion links against, e.g. {@code 1.5}. */
    public static final String GLOBAL_PLATFORM_API = "javacard.globalPlatformApi";
    /** The packages to check, comma-separated, when the module has classes in several packages. */
    public static final String PACKAGE_NAME = "javacard.packageName";
    /**
     * Export files the conversion is given for imported packages (JCVM 3.1 §4.3.3), absolute paths separated by the
     * platform's path separator; each replaces the built-in data of its package.
     */
    public static final String EXPORT_FILES = "javacard.exportFiles";
    /**
     * Export path entries the conversion searches for the export files of imported packages: directories or JAR files
     * with {@code <package>/javacard/<name>.exp} (JCVM 3.1 §5.2), absolute paths separated by the platform's path
     * separator.
     */
    public static final String EXPORT_PATH = "javacard.exportPath";
    /** The default of {@value #VERSION}, the conversion's. */
    public static final String DEFAULT_VERSION = JavaCardVersion.DEFAULT_VERSION;
    /** The default of {@value #SUPPORT_INT32}, the conversion's. */
    public static final String DEFAULT_SUPPORT_INT32 = "false";

    /** Every option the check reads. */
    public static final Set<String> NAMES = Set.of(CHECK, VERSION, SUPPORT_INT32, GLOBAL_PLATFORM_API, PACKAGE_NAME,
            EXPORT_FILES, EXPORT_PATH);

    private static final String IDENTIFIER = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern PACKAGE = Pattern.compile(IDENTIFIER + "(\\." + IDENTIFIER + ")*");

    /**
     * Copies the lists.
     */
    public CheckOptions {
        packageNames = List.copyOf(packageNames);
        notSupplied = List.copyOf(notSupplied);
    }

    /**
     * Reads the options. A missing or blank value, or a Maven expression that was not resolved ({@code ${...}}), is
     * not set. An export file or export path entry that is not there, or that holds an unresolved expression, is not
     * supplied ({@link #notSupplied()}): the check leaves the linking to the conversion, which names what is missing.
     *
     * @param options the processor options of the compilation
     * @return the options
     * @throws IllegalArgumentException if a value is invalid; the message names the option
     */
    public static CheckOptions parse(Map<String, String> options) {
        boolean enabled = bool(options, CHECK, "true");
        if (!enabled) {
            // nothing of the converter: a check that is off needs none of its classes
            return new CheckOptions(false, null, List.of(), List.of());
        }
        String version = value(options, VERSION, DEFAULT_VERSION);
        JavaCardVersion platform = JavaCardVersion.parse(version).orElseThrow(() -> invalid(VERSION, version,
                "not a Java Card version the converter supports (" + supportedVersions() + ")"));
        List<String> notSupplied = new ArrayList<>();
        List<Path> exportFiles = paths(options, EXPORT_FILES, Files::isRegularFile, notSupplied);
        List<Path> exportPath = paths(options, EXPORT_PATH, Files::exists, notSupplied);
        return new CheckOptions(true, new PackageCheck.Settings(platform, bool(options, SUPPORT_INT32,
                DEFAULT_SUPPORT_INT32), globalPlatformApi(options), exportFiles, exportPath), packages(options),
                notSupplied);
    }

    /** The entries of a list of paths that are there; the others are added to {@code notSupplied}. */
    private static List<Path> paths(Map<String, String> options, String name, Predicate<Path> there,
                                    List<String> notSupplied) {
        String value = value(options, name, null);
        List<Path> paths = new ArrayList<>();
        for (String entry : value == null ? new String[0] : value.split(Pattern.quote(File.pathSeparator))) {
            String text = entry.strip();
            if (text.isEmpty()) {
                continue;
            }
            Path path;
            try {
                path = Path.of(text);
            } catch (InvalidPathException e) {
                throw invalid(name, value, "'" + text + "' is not a path");
            }
            if (text.contains("${") || !there.test(path)) {
                notSupplied.add(text);
            } else {
                paths.add(path);
            }
        }
        return paths;
    }

    private static String globalPlatformApi(Map<String, String> options) {
        String version = value(options, GLOBAL_PLATFORM_API, null);
        if (version == null) {
            return null;
        }
        try {
            return new PackageCheck.Settings(JavaCardVersion.defaultVersion(), false, version).globalPlatformApi();
        } catch (IllegalArgumentException e) {
            throw invalid(GLOBAL_PLATFORM_API, version, e.getMessage());
        }
    }

    private static List<String> packages(Map<String, String> options) {
        String names = value(options, PACKAGE_NAME, null);
        List<String> packages = new ArrayList<>();
        for (String name : names == null ? new String[0] : names.split(",")) {
            String dotted = name.strip().replace('/', '.');
            if (!PACKAGE.matcher(dotted).matches()) {
                throw invalid(PACKAGE_NAME, names, "'" + name.strip() + "' is not a Java package name (dot notation,"
                        + " several names separated by commas)");
            }
            packages.add(dotted);
        }
        return packages;
    }

    private static boolean bool(Map<String, String> options, String name, String defaultValue) {
        String text = value(options, name, defaultValue);
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw invalid(name, text, "expected true or false");
        };
    }

    private static String value(Map<String, String> options, String name, String defaultValue) {
        String text = options.get(name);
        if (text == null || text.isBlank() || (text.strip().startsWith("${") && text.strip().endsWith("}"))) {
            return defaultValue;
        }
        return text.strip();
    }

    private static IllegalArgumentException invalid(String name, String value, String reason) {
        return new IllegalArgumentException("-A" + name + "=" + value + ": " + reason);
    }

    private static String supportedVersions() {
        return Arrays.stream(JavaCardVersion.values()).map(JavaCardVersion::specVersion)
                .collect(Collectors.joining(", "));
    }
}
