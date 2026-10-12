package name.velikodniy.jcexpress.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the build extension adds to one applet project, each part only where the project has not configured it:
 * the binding of the {@code build} goal, the toolkit's dependencies, the API stubs kept off the test class path,
 * the Java releases, the source encoding, and the javac check. {@link #apply()} returns what it added, for one
 * line in the build log.
 */
final class ProjectSetup {

    static final String GROUP_ID = "name.velikodniy";
    static final String PLUGIN_KEY = GROUP_ID + ":javacard-express-maven-plugin";
    static final String API = "javacard-express-api";
    static final String CORE = "javacard-express-core";
    static final String LIVECARD = "javacard-express-livecard";
    static final String STUBS = GROUP_ID + ":" + API;
    static final List<String> TEST_PLUGINS = List.of("org.apache.maven.plugins:maven-surefire-plugin",
            "org.apache.maven.plugins:maven-failsafe-plugin");
    private static final String PROCESS_CLASSES = "process-classes";

    private final MavenProject project;
    private final Plugin plugin;
    private final BuildSettings settings;
    private final CompilerSetup compiler;
    private final String version;
    private final List<String> warnings = new ArrayList<>();

    ProjectSetup(MavenProject project, MavenSession session, Plugin plugin, String version) {
        this.project = project;
        this.plugin = plugin;
        this.settings = new BuildSettings(project, session, plugin);
        this.compiler = new CompilerSetup(project, session);
        this.version = version;
    }

    /**
     * Sets the project up.
     *
     * @return what was added, empty when the project has everything
     */
    List<String> apply() {
        warnings.clear();
        List<String> done = new ArrayList<>();
        bindBuildGoal().ifPresent(done::add);
        done.addAll(dependencies());
        keepStubsOffTestRuntime().ifPresent(done::add);
        List<String> releases = compiler.releases();
        if (!releases.isEmpty()) {
            done.add(String.join(", ", releases));
        }
        compiler.encoding().ifPresent(done::add);
        done.addAll(check());
        return done;
    }

    /**
     * Adds the javac check, or says why there is none: turned off, nothing to check, or what the project's compiler
     * settings leave no room for. When it is added, the notes say which export files of the goal it does not get.
     */
    private List<String> check() {
        Optional<String> off = settings.checkOff();
        if (off.isPresent()) {
            return List.of(CompilerSetup.NO_CHECK + off.get());
        }
        Optional<String> check = compiler.check(version, settings.checkOptions());
        if (check.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(List.of(check.get()));
        if (!check.get().startsWith(CompilerSetup.NO_CHECK)) {
            lines.addAll(settings.checkNotes());
        }
        return lines;
    }

    /**
     * Returns what the last {@link #apply()} left out that the project will miss, for warnings in the build log.
     *
     * @return the warnings, empty when nothing is missing
     */
    List<String> warnings() {
        return List.copyOf(warnings);
    }

    /**
     * Binds the {@code build} goal to {@code process-classes} when no execution has it, with the plugin's
     * configuration (Maven gives an execution added now no part of it otherwise).
     */
    private Optional<String> bindBuildGoal() {
        if (plugin.getExecutions().stream().anyMatch(e -> e.getGoals().contains(BuildSettings.GOAL))) {
            return Optional.empty();
        }
        PluginExecution execution = new PluginExecution();
        execution.setId(plugin.getExecutionsAsMap().containsKey("default") ? "javacard-express-build" : "default");
        execution.setPhase(PROCESS_CLASSES);
        execution.addGoal(BuildSettings.GOAL);
        if (plugin.getConfiguration() != null) {
            execution.setConfiguration(new Xpp3Dom((Xpp3Dom) plugin.getConfiguration()));
        }
        plugin.addExecution(execution);
        plugin.flushExecutionMap();
        return Optional.of("the build goal in " + PROCESS_CLASSES);
    }

    /**
     * Adds the toolkit's dependencies the project does not declare: the test runtime
     * (javacard-express-core with jCardSim, the card test backends of javacard-express-livecard) before the API
     * stubs, as IDE test runners follow the order of the POM. A version the project manages (a BOM import) wins over
     * the plugin's, so that the goal's version check sees what the project chose. A project that tests with TestNG
     * or has its own jCardSim gets no test runtime, and a warning: the JUnit Platform that comes with it would
     * replace TestNG as the test runner, and a second jCardSim would compete with the project's. JUnit 4 is no
     * such stack: Surefire runs it on the JUnit Platform through the vintage engine.
     */
    private List<String> dependencies() {
        List<Dependency> declared = project.getModel().getDependencies();
        Optional<String> ownStack = ownTestStack(declared);
        List<Dependency> testRuntime = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> left = new ArrayList<>();
        for (String artifactId : List.of(CORE, LIVECARD)) {
            if (indexOf(declared, artifactId) >= 0) {
                continue;
            }
            if (ownStack.isPresent()) {
                left.add(artifactId);
            } else {
                testRuntime.add(dependency(artifactId, "test"));
                added.add(artifactId + " (test)");
            }
        }
        int stubs = indexOf(declared, API);
        declared.addAll(stubs < 0 ? declared.size() : stubs, testRuntime);
        if (stubs < 0) {
            declared.add(dependency(API, "provided"));
            added.add(API + " (provided)");
        }
        ownStack.filter(stack -> !left.isEmpty()).ifPresent(stack -> warnings.add("did not add "
                + String.join(" and ", left) + " to " + project.getGroupId() + ":" + project.getArtifactId()
                + ": the project " + stack + "; declare them to use the toolkit's test runtime"));
        return added.isEmpty() ? List.of() : List.of("dependencies " + String.join(", ", added));
    }

    /**
     * The test stack the project brings itself, which the toolkit's test runtime would displace: TestNG without
     * JUnit Jupiter (with Jupiter the tests are on the JUnit Platform already), or a jCardSim of its own
     * (published under several group ids: {@code com.klinec}, {@code com.licel}, {@code com.github.licel}).
     */
    private static Optional<String> ownTestStack(List<Dependency> declared) {
        boolean jupiter = declared.stream().anyMatch(d -> "org.junit.jupiter".equals(d.getGroupId()));
        if (!jupiter && declared.stream().anyMatch(d -> "org.testng".equals(d.getGroupId())
                && "testng".equals(d.getArtifactId()))) {
            return Optional.of("tests with TestNG (the JUnit Platform that comes with them would replace it)");
        }
        return declared.stream().filter(d -> "jcardsim".equals(d.getArtifactId())).findFirst()
                .map(d -> "has its own jCardSim (" + d.getGroupId()
                        + ":jcardsim; the toolkit's would compete with it)");
    }

    /**
     * Keeps the API stubs off the test class path of Surefire and Failsafe: the stubs and jCardSim define the same
     * {@code javacard.*} classes, and only jCardSim's can run.
     */
    private Optional<String> keepStubsOffTestRuntime() {
        List<String> plugins = new ArrayList<>();
        for (String key : TEST_PLUGINS) {
            Plugin testPlugin = project.getPlugin(key);
            if (testPlugin == null) {
                continue;
            }
            boolean added = exclude(PomEdits.configuration(testPlugin));
            for (PluginExecution execution : testPlugin.getExecutions()) {
                added |= exclude(PomEdits.configuration(execution));
            }
            if (added) {
                plugins.add(testPlugin.getArtifactId());
            }
        }
        return plugins.isEmpty() ? Optional.empty()
                : Optional.of(API + " off the test class path of " + String.join(" and ", plugins));
    }

    private static boolean exclude(Xpp3Dom configuration) {
        return PomEdits.append(PomEdits.child(configuration, "classpathDependencyExcludes"),
                "classpathDependencyExclude", STUBS);
    }

    private Dependency dependency(String artifactId, String scope) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(GROUP_ID);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(managedVersion(artifactId).orElse(version));
        dependency.setScope(scope);
        return dependency;
    }

    /** The version the project's dependency management (with imported BOMs) gives a toolkit artifact. */
    private Optional<String> managedVersion(String artifactId) {
        DependencyManagement management = project.getModel().getDependencyManagement();
        return management == null ? Optional.empty() : management.getDependencies().stream()
                .filter(d -> GROUP_ID.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()))
                .filter(d -> d.getType() == null || "jar".equals(d.getType()))
                .map(Dependency::getVersion).filter(v -> v != null && !v.isBlank()).findFirst();
    }

    private static int indexOf(List<Dependency> dependencies, String artifactId) {
        for (int i = 0; i < dependencies.size(); i++) {
            if (GROUP_ID.equals(dependencies.get(i).getGroupId())
                    && artifactId.equals(dependencies.get(i).getArtifactId())) {
                return i;
            }
        }
        return -1;
    }
}
