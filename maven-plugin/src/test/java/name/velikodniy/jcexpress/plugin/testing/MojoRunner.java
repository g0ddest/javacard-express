package name.velikodniy.jcexpress.plugin.testing;

import name.velikodniy.jcexpress.plugin.JavaCardBuildMojo;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Runs {@link JavaCardBuildMojo} the way Maven configures it: every value is injected by the
 * parameter name (or alias) declared in the generated plugin descriptor, user properties are
 * resolved through the parameter expressions, and unset parameters receive their declared
 * defaults. A value for a name that is not a plugin parameter fails the test, exactly like a
 * documented-but-missing parameter would be ignored by Maven.
 */
public final class MojoRunner {

    private final PluginDescriptor descriptor = PluginDescriptor.buildGoal();
    private final Map<String, Object> configuration = new LinkedHashMap<>();
    private final Map<String, String> properties = new HashMap<>();
    private final MavenProject project;
    private final RecordingLog log = new RecordingLog();
    private final RecordingProjectHelper projectHelper = new RecordingProjectHelper();
    private String executionId = "default";
    private MojoExecution.Source source = MojoExecution.Source.LIFECYCLE;
    private String pluginVersion = PluginDescriptor.pluginElement("version").orElseThrow();

    private MojoRunner(Path basedir, Path classesDir) {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("sample-applet");
        model.setVersion("1.0");
        model.setPackaging("jar");
        Build build = new Build();
        build.setDirectory(basedir.resolve("target").toString());
        build.setOutputDirectory(classesDir.toString());
        build.setFinalName("sample-applet-1.0");
        build.setSourceDirectory(basedir.resolve("src").toString());
        model.setBuild(build);
        project = new MavenProject(model);
        project.setFile(basedir.resolve("pom.xml").toFile());
        project.addCompileSourceRoot(basedir.resolve("src").toString());
        project.setArtifacts(Set.of());
    }

    /**
     * Creates a runner for a project whose compiled classes are in {@code classesDir}.
     *
     * @param basedir    project base directory ({@code target/} is created below it)
     * @param classesDir compiled classes ({@code project.build.outputDirectory})
     * @return the runner
     */
    public static MojoRunner forProject(Path basedir, Path classesDir) {
        return new MojoRunner(basedir, classesDir);
    }

    /**
     * Sets a {@code <configuration>} element.
     *
     * @param element parameter name or alias as written in the POM
     * @param value   the value (String, Boolean, File, List ...)
     * @return this
     */
    public MojoRunner configure(String element, Object value) {
        if (descriptor.parameter(element).isEmpty()) {
            throw new AssertionError("'" + element + "' is not a parameter of the build goal; "
                    + "Maven would ignore it. Parameters: " + descriptor.parameters().keySet());
        }
        configuration.put(element, value);
        return this;
    }

    /**
     * Sets a user property ({@code -Dname=value}).
     *
     * @param name  property name
     * @param value property value
     * @return this
     */
    public MojoRunner property(String name, String value) {
        properties.put(name, value);
        return this;
    }

    /**
     * Adds resolved dependency artifacts ({@code project.getArtifacts()}).
     *
     * @param artifacts the artifacts
     * @return this
     */
    public MojoRunner artifacts(Set<Artifact> artifacts) {
        project.setArtifacts(artifacts);
        return this;
    }

    /**
     * Makes the next {@link #execute()} a lifecycle execution with this id, as for an
     * {@code <execution>} of the POM (the default is {@code default}, the id Maven gives an
     * execution without one).
     *
     * @param id execution id
     * @return this
     */
    public MojoRunner execution(String id) {
        executionId = id;
        source = MojoExecution.Source.LIFECYCLE;
        return this;
    }

    /**
     * Makes the next {@link #execute()} an invocation from the command line
     * ({@code mvn javacard-express:build}, execution id {@code default-cli}).
     *
     * @return this
     */
    public MojoRunner fromCommandLine() {
        executionId = "default-cli";
        source = MojoExecution.Source.CLI;
        return this;
    }

    /**
     * Sets the version of the plugin as Maven resolved it (the default is the version under test).
     *
     * @param version plugin version
     * @return this
     */
    public MojoRunner pluginVersion(String version) {
        pluginVersion = version;
        return this;
    }

    /**
     * Sets the packaging of the project (the default is {@code jar}).
     *
     * @param packaging e.g. {@code pom}
     * @return this
     */
    public MojoRunner packaging(String packaging) {
        project.getModel().setPackaging(packaging);
        return this;
    }

    /**
     * Declares a dependency in the POM ({@code project.getDependencies()}); it is not resolved.
     *
     * @param coordinates {@code groupId:artifactId:version}
     * @param scope       the scope
     * @return this
     */
    public MojoRunner declare(String coordinates, String scope) {
        String[] parts = coordinates.split(":");
        Dependency dependency = new Dependency();
        dependency.setGroupId(parts[0]);
        dependency.setArtifactId(parts[1]);
        dependency.setVersion(parts[2]);
        dependency.setScope(scope);
        project.getModel().addDependency(dependency);
        return this;
    }

    /** @return the Maven project the Mojo runs in */
    public MavenProject project() {
        return project;
    }

    /** @return the log the Mojo writes to */
    public RecordingLog log() {
        return log;
    }

    /** @return the attached artifacts recorded by the project helper */
    public List<RecordingProjectHelper.Attachment> attachments() {
        return projectHelper.attachments();
    }

    /**
     * Configures and executes the Mojo.
     *
     * @return this, for inspecting the log and attachments
     * @throws MojoExecutionException from the Mojo
     * @throws MojoFailureException   from the Mojo
     */
    public MojoRunner execute() throws MojoExecutionException, MojoFailureException {
        JavaCardBuildMojo mojo = newMojo();
        mojo.setLog(log);
        for (PluginDescriptor.Parameter p : descriptor.parameters().values()) {
            Object value = valueFor(p);
            if (value != null) {
                inject(mojo, p.name(), value);
            }
        }
        mojo.execute();
        return this;
    }

    private Object valueFor(PluginDescriptor.Parameter p) {
        for (var e : configuration.entrySet()) {
            if (e.getKey().equals(p.name()) || e.getKey().equals(p.alias())) {
                return e.getValue();
            }
        }
        String property = p.property().map(properties::get).orElse(null);
        if (property != null) {
            return convert(property, p.type());
        }
        return p.defaultValue() == null ? null : convert(evaluate(p.defaultValue()), p.type());
    }

    private Object evaluate(String expression) {
        return switch (expression) {
            case "${project}" -> project;
            case "${project.build.directory}" -> project.getBuild().getDirectory();
            case "${project.build.outputDirectory}" -> project.getBuild().getOutputDirectory();
            case "${project.build.finalName}" -> project.getBuild().getFinalName();
            case "${project.compileSourceRoots}" -> project.getCompileSourceRoots();
            case "${mojoExecution}" -> mojoExecution();
            case "${session.userProperties}" -> userProperties();
            default -> expression;
        };
    }

    private MojoExecution mojoExecution() {
        org.apache.maven.plugin.descriptor.PluginDescriptor plugin =
                new org.apache.maven.plugin.descriptor.PluginDescriptor();
        plugin.setGroupId("name.velikodniy");
        plugin.setArtifactId("javacard-express-maven-plugin");
        plugin.setVersion(pluginVersion);
        MojoDescriptor mojo = new MojoDescriptor();
        mojo.setGoal("build");
        mojo.setPluginDescriptor(plugin);
        return new MojoExecution(mojo, executionId, source);
    }

    /** The {@code -D} properties of the command line, as Maven's session holds them. */
    private Properties userProperties() {
        Properties user = new Properties();
        user.putAll(properties);
        return user;
    }

    private static Object convert(Object value, String type) {
        if (!(value instanceof String s)) {
            return value;
        }
        return switch (type) {
            case "boolean", "java.lang.Boolean" -> Boolean.valueOf(s);
            case "java.io.File" -> new File(s);
            default -> s;
        };
    }

    private JavaCardBuildMojo newMojo() {
        try {
            for (Constructor<?> c : JavaCardBuildMojo.class.getDeclaredConstructors()) {
                c.setAccessible(true);
                if (c.getParameterCount() == 0) {
                    return (JavaCardBuildMojo) c.newInstance();
                }
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == MavenProjectHelper.class) {
                    return (JavaCardBuildMojo) c.newInstance(projectHelper);
                }
            }
            throw new IllegalStateException("No usable constructor");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void inject(Object mojo, String fieldName, Object value) {
        try {
            Field f = JavaCardBuildMojo.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(mojo, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inject parameter " + fieldName, e);
        }
    }
}
