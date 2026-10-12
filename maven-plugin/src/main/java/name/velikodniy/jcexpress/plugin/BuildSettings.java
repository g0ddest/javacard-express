package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.javac.CheckOptions;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The settings of the {@code build} goal that the setup of a project needs before Maven runs the goal: the build
 * extension passes them to the javac check, which must stand for the same conversion. They are resolved as Maven
 * resolves the goal's parameters: the value configured for the goal's execution or the plugin, else the user, system
 * and project property, else the default. The names and defaults are the constants the goal's parameters are
 * declared with, so that the check and the conversion cannot drift apart.
 */
final class BuildSettings {

    static final String GOAL = "build";
    static final String VERSION = "javaCardVersion";
    static final String VERSION_ALIAS = "javaCardVersionStr";
    static final String SUPPORT_INT32 = "supportInt32";
    static final String GLOBAL_PLATFORM_API = "globalPlatformApi";
    static final String PACKAGE_NAME = "packageName";
    static final String CHECK = "check";
    static final String DEFAULT_CHECK = "true";
    static final String SETUP = "setup";
    static final String SETUP_PROPERTY = "javacard.setup";
    static final String DEFAULT_SETUP = "true";
    static final String SKIP = "skip";
    static final String SKIP_PROPERTY = "javacard.skip";
    static final String IMPORT_EXPORT_FILES = "importExportFiles";
    static final String EXPORT_PATH = "exportPath";

    /**
     * The settings of one execution of the goal.
     *
     * @param version           the target platform, if valid (an invalid one fails the goal itself)
     * @param supportInt32      int support
     * @param globalPlatformApi the fixed version of {@code org.globalplatform}, if any
     * @param packageName       the configured package, if any
     * @param skipped           whether the execution skips the conversion ({@code skip}, {@code javacard.skip})
     * @param exportFiles       the export files it is given ({@code importExportFiles}): absolute, or as written when
     *                          they hold an expression Maven resolves only when plugins run
     * @param exportPath        its export path ({@code exportPath}), the same way
     */
    record Execution(Optional<JavaCardVersion> version, boolean supportInt32, Optional<String> globalPlatformApi,
                     Optional<String> packageName, boolean skipped, List<String> exportFiles,
                     List<String> exportPath) {

        /** The export files and the export path; the check gets them when every execution has the same. */
        List<List<String>> exportFilesAndPath() {
            return List.of(exportFiles, exportPath);
        }
    }

    private final MavenProject project;
    private final MavenSession session;
    private final Plugin plugin;

    BuildSettings(MavenProject project, MavenSession session, Plugin plugin) {
        this.project = project;
        this.session = session;
        this.plugin = plugin;
    }

    /**
     * Whether the build extension sets the project up ({@code <setup>}, {@code javacard.setup}).
     *
     * @return {@code false} when it is turned off
     */
    boolean setup() {
        return !"false".equalsIgnoreCase(value(configurations(), SETUP, null, SETUP_PROPERTY, DEFAULT_SETUP));
    }

    /**
     * Whether javac checks the applet code ({@code <check>}, {@code javacard.check}): not when it is turned off, and
     * not when every execution of the goal skips the conversion ({@code javacard.skip}), whose settings the check
     * stands for.
     *
     * @return {@code false} when it is turned off or there is no conversion to check for
     */
    boolean check() {
        return checkOff().isEmpty();
    }

    /**
     * Why there is no javac check, for the log line of the setup.
     *
     * @return the reason, empty when the check is on
     */
    Optional<String> checkOff() {
        if ("false".equalsIgnoreCase(value(configurations(), CHECK, null, CheckOptions.CHECK, DEFAULT_CHECK))) {
            return Optional.of("it is turned off (javacard.check=false)");
        }
        if (executions().stream().allMatch(Execution::skipped)) {
            return Optional.of("the build goal skips the conversion (javacard.skip), whose settings it stands for");
        }
        return Optional.empty();
    }

    /**
     * Returns the settings of every execution of the goal; of the plugin's configuration when no execution has the
     * goal (the extension binds one with that configuration).
     *
     * @return one entry per execution
     */
    List<Execution> executions() {
        List<Execution> executions = new ArrayList<>();
        for (Xpp3Dom configuration : configurations()) {
            List<Xpp3Dom> one = configuration == null ? List.of() : List.of(configuration);
            executions.add(new Execution(
                    JavaCardVersion.parse(value(one, VERSION, VERSION_ALIAS, CheckOptions.VERSION,
                            CheckOptions.DEFAULT_VERSION)),
                    "true".equalsIgnoreCase(value(one, SUPPORT_INT32, null, CheckOptions.SUPPORT_INT32,
                            CheckOptions.DEFAULT_SUPPORT_INT32)),
                    Optional.ofNullable(value(one, GLOBAL_PLATFORM_API, null, CheckOptions.GLOBAL_PLATFORM_API, null)),
                    Optional.ofNullable(value(one, PACKAGE_NAME, null, CheckOptions.PACKAGE_NAME, null)),
                    "true".equalsIgnoreCase(value(one, SKIP, null, SKIP_PROPERTY, "false")),
                    files(configuration, IMPORT_EXPORT_FILES), files(configuration, EXPORT_PATH)));
        }
        return executions;
    }

    /**
     * The entries of a list parameter of files, absolute as Maven makes them (relative to the project). An entry
     * with an expression Maven resolves only when plugins run ({@code ${session.executionRootDirectory}}) stays as
     * written: the compiler plugin resolves it in javac's arguments as the goal does in its parameter.
     */
    private List<String> files(Xpp3Dom configuration, String parameter) {
        Xpp3Dom list = configuration == null ? null : configuration.getChild(parameter);
        if (list == null) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        for (Xpp3Dom entry : list.getChildren()) {
            String path = PomEdits.text(entry);
            if (path == null) {
                continue;
            }
            File file = new File(path);
            files.add(path.contains("${") ? path
                    : (file.isAbsolute() ? file : new File(project.getBasedir(), path)).getAbsolutePath());
        }
        return files;
    }

    /**
     * Returns the options of the javac check: the settings of the goal's executions, combined so that the check
     * reports nothing that one of the conversions accepts (the newest platform, int support if one has it, the
     * GlobalPlatform API chosen by the converter unless every execution fixes a version, the packages when every
     * execution names one). The export files and export path entries go to the check only when every execution
     * has the same ones: the check stands for one conversion, and two executions that give one package different
     * files (one per target) would make it see two export files of that package. Entries with an expression Maven
     * resolves only when plugins run go as written (the compiler plugin resolves them), and an entry that is not
     * there when javac runs the check leaves to the conversion. The goal's other sources of export files
     * (dependencies of type {@code exp}, the search of the class path) are not passed on.
     *
     * @return {@code -A} options for javac
     */
    List<String> checkOptions() {
        List<Execution> executions = executions();
        List<String> options = new ArrayList<>();
        executions.stream().map(Execution::version).flatMap(Optional::stream).max(Comparator.naturalOrder())
                .ifPresent(v -> options.add(option(CheckOptions.VERSION, v.specVersion())));
        options.add(option(CheckOptions.SUPPORT_INT32,
                String.valueOf(executions.stream().anyMatch(Execution::supportInt32))));
        if (executions.stream().allMatch(e -> e.globalPlatformApi().isPresent())) {
            executions.stream().map(e -> e.globalPlatformApi().orElseThrow()).max(Comparator.naturalOrder())
                    .ifPresent(v -> options.add(option(CheckOptions.GLOBAL_PLATFORM_API, v)));
        }
        if (executions.stream().allMatch(e -> e.packageName().isPresent())) {
            options.add(option(CheckOptions.PACKAGE_NAME, String.join(",", executions.stream()
                    .map(e -> e.packageName().orElseThrow()).distinct().toList())));
        }
        if (sameExportFiles(executions)) {
            executions.stream().findFirst().ifPresent(first -> {
                pathOption(CheckOptions.EXPORT_FILES, first.exportFiles().stream()).ifPresent(options::add);
                pathOption(CheckOptions.EXPORT_PATH, first.exportPath().stream()).ifPresent(options::add);
            });
        }
        return options;
    }

    /**
     * What of the goal's export files the check does not get, for the log line of the setup: export files that
     * differ between the executions.
     *
     * @return the notes, empty when the check gets them all
     */
    List<String> checkNotes() {
        List<Execution> executions = executions();
        List<String> notes = new ArrayList<>();
        if (!sameExportFiles(executions)) {
            notes.add("the export files of the build goal's executions differ, so the check links against the"
                    + " built-in API data (each conversion uses its own)");
        }
        return notes;
    }

    private static boolean sameExportFiles(List<Execution> executions) {
        return executions.stream().map(Execution::exportFilesAndPath).distinct().count() <= 1;
    }

    /** One option with the distinct paths, or none when there are none. */
    private static Optional<String> pathOption(String name, Stream<String> paths) {
        List<String> distinct = paths.distinct().toList();
        return distinct.isEmpty() ? Optional.empty()
                : Optional.of(option(name, String.join(File.pathSeparator, distinct)));
    }

    /** The configurations of the goal's executions, or the plugin's own when there is none. */
    private List<Xpp3Dom> configurations() {
        List<Xpp3Dom> configurations = plugin.getExecutions().stream()
                .filter(e -> e.getGoals().contains(GOAL))
                .map(PluginExecution::getConfiguration)
                .map(c -> (Xpp3Dom) c)
                .toList();
        if (configurations.isEmpty()) {
            List<Xpp3Dom> own = new ArrayList<>();
            own.add((Xpp3Dom) plugin.getConfiguration());
            return own;
        }
        return configurations;
    }

    private String value(List<Xpp3Dom> configurations, String parameter, String alias, String property,
                         String defaultValue) {
        for (Xpp3Dom configuration : configurations) {
            String configured = configured(configuration, parameter, alias);
            if (configured != null) {
                return configured;
            }
        }
        String text = session.getUserProperties().getProperty(property);
        text = text != null ? text : session.getSystemProperties().getProperty(property);
        text = text != null ? text : project.getProperties().getProperty(property);
        return text == null || text.isBlank() ? defaultValue : text.strip();
    }

    private static String configured(Xpp3Dom configuration, String parameter, String alias) {
        if (configuration == null) {
            return null;
        }
        Xpp3Dom child = configuration.getChild(parameter);
        child = child != null || alias == null ? child : configuration.getChild(alias);
        String text = child == null ? null : child.getValue();
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String option(String name, String value) {
        return "-A" + Objects.requireNonNull(name) + "=" + value;
    }
}
