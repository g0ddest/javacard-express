package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.javac.JavaCardCheckProcessor;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The compiler settings of an applet project, added by the build extension where the project has none: applet code
 * compiled for Java 8 and test code for Java 25, as the applet parent does, and the javac check of the Java Card
 * subset in the compilation of the main sources ({@code default-compile}; the test compilation is never touched).
 */
final class CompilerSetup {

    static final String COMPILER = "org.apache.maven.plugins:maven-compiler-plugin";
    /** Applets are compiled for Java 8 class files, which every Java Card tool reads. */
    static final String APPLET_RELEASE = "8";
    /** Tests run on JDK 25 with the toolkit, whose class files are Java 25. */
    static final String TEST_RELEASE = "25";
    static final String RELEASE_PROPERTY = "maven.compiler.release";
    static final String TEST_RELEASE_PROPERTY = "maven.compiler.testRelease";
    /** javac 21+ warns that release 8 is obsolete; the old target is intentional. */
    static final String NO_OPTIONS_WARNING = "-Xlint:-options";
    static final String CHECK_ARTIFACT = "javacard-express-javac-plugin";
    static final String ENCODING_PROPERTY = "project.build.sourceEncoding";
    static final String ENCODING = "UTF-8";
    static final String PROC_PROPERTY = "maven.compiler.proc";
    /** How the log line of the setup starts a reason for leaving the check out. */
    static final String NO_CHECK = "no javac check: ";
    static final String TOOLCHAINS = "org.apache.maven.plugins:maven-toolchains-plugin";
    /** Compiler arguments that make javac run processors of the class path. */
    static final List<String> PROCESSOR_ARGUMENTS = List.of("-processorpath", "--processor-path", "-proc:full",
            "-proc:only", "--processor-module-path");

    private final MavenProject project;
    private final MavenSession session;

    CompilerSetup(MavenProject project, MavenSession session) {
        this.project = project;
        this.session = session;
    }

    /**
     * Sets the encoding of the sources to {@value #ENCODING} when the project sets none (Maven would use the
     * platform's and warn that the build depends on it).
     *
     * @return what was set, for the log
     */
    Optional<String> encoding() {
        if (PomEdits.hasProperty(project, session, ENCODING_PROPERTY)) {
            return Optional.empty();
        }
        project.getProperties().setProperty(ENCODING_PROPERTY, ENCODING);
        return Optional.of("sources in " + ENCODING);
    }

    /**
     * Sets the Java releases the project leaves open: {@value #APPLET_RELEASE} for the applet code when neither a
     * release nor a source or target version is configured, {@value #TEST_RELEASE} for the tests when no test
     * release, source or target is.
     *
     * @return what was set, for the log
     */
    List<String> releases() {
        List<String> done = new ArrayList<>();
        Plugin compiler = project.getPlugin(COMPILER);
        if (compiler == null) {
            return done;
        }
        if (!PomEdits.hasProperty(project, session, RELEASE_PROPERTY, "maven.compiler.source", "maven.compiler.target")
                && !PomEdits.configures(configurations(compiler, "default-compile"), "release", "source", "target")) {
            project.getProperties().setProperty(RELEASE_PROPERTY, APPLET_RELEASE);
            execution(compiler, "default-compile").ifPresent(compile ->
                    PomEdits.append(PomEdits.child(PomEdits.configuration(compile), "compilerArgs"), "arg",
                            NO_OPTIONS_WARNING));
            done.add("applet code for Java " + APPLET_RELEASE);
        }
        if (!PomEdits.hasProperty(project, session, TEST_RELEASE_PROPERTY, "maven.compiler.testSource",
                "maven.compiler.testTarget") && !PomEdits.configures(configurations(compiler, "default-testCompile"),
                "testRelease", "testSource", "testTarget")) {
            project.getProperties().setProperty(TEST_RELEASE_PROPERTY, TEST_RELEASE);
            done.add("tests for Java " + TEST_RELEASE);
        }
        return done;
    }

    /**
     * Adds the javac check to the compilation of the main sources, where it cannot displace anything: next to the
     * annotation processor path the project has (the check's jar after it, its processor after the processors the
     * project names), or as the only processor of a project that runs none. The settings of the {@code build} goal
     * become processor options, except those the project passes itself. Left out, with the reason for the log: a
     * project that runs processors from its class path (a processor path would hide them from javac), a compilation
     * with a javac of another JDK (the check is Java 25 code), and a project that turns annotation processing off.
     * A project that names its processors with {@code -processor} and has a processor path gets the check's jar on
     * it, and the log line says that its processor must join the {@code -processor} list to run.
     *
     * @param version the plugin's version
     * @param options the {@code -A} options
     * @return what was done, for the log; empty when there is no main compilation
     */
    Optional<String> check(String version, List<String> options) {
        Plugin compiler = project.getPlugin(COMPILER);
        Optional<PluginExecution> compile = compiler == null ? Optional.empty()
                : execution(compiler, "default-compile");
        if (compile.isEmpty()) {
            return Optional.empty();
        }
        Xpp3Dom configuration = PomEdits.configuration(compile.get());
        String proc = proc(configuration);
        if ("none".equals(proc) || arguments(configuration).contains("-proc:none")) {
            return Optional.of(NO_CHECK + "annotation processing is off (proc none)");
        }
        Optional<String> reason = otherJavac(configuration).or(() -> processorsOfTheProject(configuration, proc));
        if (reason.isPresent()) {
            return Optional.of(NO_CHECK + reason.get());
        }
        addCheck(configuration, version, options);
        if (arguments(configuration).contains("-processor") && !namesTheCheck(arguments(configuration))) {
            return Optional.of("the jar of the javac check on the processor path of the main compilation; the"
                    + " project names its annotation processors with -processor, so add "
                    + JavaCardCheckProcessor.class.getName() + " there to run the check");
        }
        return Optional.of("the javac check of the Java Card subset in the main compilation");
    }

    private void addCheck(Xpp3Dom configuration, String version, List<String> options) {
        Xpp3Dom paths = PomEdits.child(configuration, "annotationProcessorPaths");
        if (Arrays.stream(paths.getChildren())
                .noneMatch(p -> CHECK_ARTIFACT.equals(PomEdits.text(p.getChild("artifactId"))))) {
            paths.addChild(path(version));
        }
        Xpp3Dom processors = configuration.getChild("annotationProcessors");
        if (processors != null && processors.getChildCount() > 0) {
            PomEdits.append(processors, "annotationProcessor", JavaCardCheckProcessor.class.getName());
        }
        List<String> given = arguments(configuration);
        Xpp3Dom arguments = PomEdits.child(configuration, "compilerArgs");
        for (String option : options) {
            String key = option.substring(0, option.indexOf('=') + 1);
            if (given.stream().noneMatch(a -> a.startsWith(key))) {
                PomEdits.append(arguments, "arg", option);
            }
        }
    }

    /** The {@code proc} setting: configured, else {@code maven.compiler.proc}. */
    private String proc(Xpp3Dom configuration) {
        String configured = PomEdits.text(configuration.getChild("proc"));
        return configured != null ? configured : PomEdits.property(project, session, PROC_PROPERTY);
    }

    /**
     * Why the compilation runs a javac that may be older than the check needs: a fork to another executable or a
     * toolchain.
     */
    private Optional<String> otherJavac(Xpp3Dom configuration) {
        String fork = Optional.ofNullable(PomEdits.text(configuration.getChild("fork")))
                .orElse(PomEdits.property(project, session, "maven.compiler.fork"));
        String executable = Optional.ofNullable(PomEdits.text(configuration.getChild("executable")))
                .orElse(PomEdits.property(project, session, "maven.compiler.executable"));
        String needs = "; the check runs only in javac of JDK " + ProjectsSetup.REQUIRED_JDK + " or newer (with such"
                + " a javac add it as the plugin README shows)";
        if ("true".equalsIgnoreCase(fork) && executable != null) {
            return Optional.of("the main compilation forks to " + executable + needs);
        }
        if (configuration.getChild("jdkToolchain") != null || project.getPlugin(TOOLCHAINS) != null) {
            return Optional.of("a toolchain chooses the JDK of the compilation" + needs);
        }
        return Optional.empty();
    }

    /**
     * Why a processor path would displace processors of the project: javac looks for processors on the class path
     * only as long as there is no processor path.
     */
    private Optional<String> processorsOfTheProject(Xpp3Dom configuration, String proc) {
        Xpp3Dom paths = configuration.getChild("annotationProcessorPaths");
        boolean hasPaths = paths != null && paths.getChildCount() > 0;
        List<String> arguments = arguments(configuration);
        boolean classPathProcessors = configuration.getChild("annotationProcessors") != null
                || "full".equals(proc) || "only".equals(proc) || arguments.contains("-processor")
                || arguments.stream().anyMatch(PROCESSOR_ARGUMENTS::contains);
        if (!hasPaths && classPathProcessors) {
            return Optional.of("the project runs annotation processors from its class path, which a processor path"
                    + " would hide from javac; to get the check, list them and " + ProjectSetup.GROUP_ID + ":"
                    + CHECK_ARTIFACT + " in annotationProcessorPaths of default-compile"
                    + (arguments.contains("-processor") ? ", and add " + JavaCardCheckProcessor.class.getName()
                    + " to -processor" : ""));
        }
        return Optional.empty();
    }

    /** Whether the {@code -processor} list of the arguments names the check's processor already. */
    private static boolean namesTheCheck(List<String> arguments) {
        int names = arguments.indexOf("-processor") + 1;
        return names > 0 && names < arguments.size()
                && Arrays.asList(arguments.get(names).split(",")).contains(JavaCardCheckProcessor.class.getName());
    }

    /** The compiler arguments: {@code compilerArgs} and the words of {@code compilerArgument}. */
    private static List<String> arguments(Xpp3Dom configuration) {
        List<String> arguments = new ArrayList<>();
        Xpp3Dom list = configuration.getChild("compilerArgs");
        if (list != null) {
            Arrays.stream(list.getChildren()).map(PomEdits::text).filter(Objects::nonNull).forEach(arguments::add);
        }
        String single = PomEdits.text(configuration.getChild("compilerArgument"));
        if (single != null) {
            arguments.addAll(Arrays.asList(single.split("\\s+")));
        }
        return arguments;
    }

    private static Xpp3Dom path(String version) {
        Xpp3Dom path = new Xpp3Dom("path");
        path.addChild(PomEdits.element("groupId", ProjectSetup.GROUP_ID));
        path.addChild(PomEdits.element("artifactId", CHECK_ARTIFACT));
        path.addChild(PomEdits.element("version", version));
        return path;
    }

    private static Optional<PluginExecution> execution(Plugin plugin, String id) {
        return Optional.ofNullable(plugin.getExecutionsAsMap().get(id));
    }

    /** The plugin's configuration and the execution's, which Maven merged when it read the project. */
    private static List<Xpp3Dom> configurations(Plugin plugin, String executionId) {
        List<Xpp3Dom> configurations = new ArrayList<>();
        configurations.add((Xpp3Dom) plugin.getConfiguration());
        execution(plugin, executionId).ifPresent(e -> configurations.add((Xpp3Dom) e.getConfiguration()));
        return configurations;
    }
}
