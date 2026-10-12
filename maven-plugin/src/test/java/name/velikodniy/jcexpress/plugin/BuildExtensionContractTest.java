package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.javac.CheckOptions;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.PluginDescriptor;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What ties the build extension to the goal and to Maven: the settings it hands to the javac check are the goal's
 * parameters with the same names, properties and defaults; Maven finds it in the plugin's jar; it knows the plugin's
 * version; and the goal points to it when it did not run.
 */
class BuildExtensionContractTest {

    private final PluginDescriptor descriptor = PluginDescriptor.buildGoal();

    @TempDir
    Path dir;

    @Test
    void theCheckGetsTheGoalsParametersWithTheirPropertiesAndDefaults() {
        assertParameter(BuildSettings.VERSION, CheckOptions.VERSION, CheckOptions.DEFAULT_VERSION);
        assertThat(descriptor.parameter(BuildSettings.VERSION).orElseThrow().alias())
                .isEqualTo(BuildSettings.VERSION_ALIAS);
        assertParameter(BuildSettings.SUPPORT_INT32, CheckOptions.SUPPORT_INT32, CheckOptions.DEFAULT_SUPPORT_INT32);
        assertParameter(BuildSettings.GLOBAL_PLATFORM_API, CheckOptions.GLOBAL_PLATFORM_API, null);
        assertParameter(BuildSettings.PACKAGE_NAME, CheckOptions.PACKAGE_NAME, null);
        assertParameter(BuildSettings.CHECK, CheckOptions.CHECK, BuildSettings.DEFAULT_CHECK);
        assertParameter(BuildSettings.SETUP, BuildSettings.SETUP_PROPERTY, BuildSettings.DEFAULT_SETUP);
    }

    @Test
    void mavenFindsTheExtensionThroughTheIndexOfTheJar() throws IOException {
        try (InputStream index = JavaCardSetup.class.getResourceAsStream("/META-INF/sisu/javax.inject.Named")) {
            assertThat(index).isNotNull();
            assertThat(new String(index.readAllBytes(), StandardCharsets.UTF_8).lines())
                    .contains(JavaCardSetup.class.getName());
        }
        assertThat(JavaCardSetup.class.getAnnotation(Named.class)).isNotNull();
        assertThat(JavaCardSetup.class.getAnnotation(Singleton.class)).isNotNull();
    }

    /**
     * Maven 3.9.0 to 3.9.9 read the classes listed in the index with a class scanner that knows class files up to
     * Java 11 (major version 55) and silently skip a newer one, and Maven 3.9 runs on JDK 8, which cannot define a
     * class newer than Java 8 (major version 52): the extension would silently not exist there, instead of stopping
     * the build with the JDK it needs.
     */
    @Test
    void everyClassMavenScansForTheExtensionIsReadableByMaven390OnJdk8() throws IOException {
        List<String> indexed;
        try (InputStream index = JavaCardSetup.class.getResourceAsStream("/META-INF/sisu/javax.inject.Named")) {
            indexed = new String(index.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
                    .filter(line -> !line.isEmpty()).toList();
        }

        assertThat(indexed).isNotEmpty().allSatisfy(name -> assertThat(majorVersion(name))
                .as("class file version of %s", name).isLessThanOrEqualTo(JAVA_8));
    }

    @Test
    void onAJdkBefore25TheExtensionStopsTheBuildWithTheReason() {
        assertThatThrownBy(() -> JavaCardSetup.requireJdk(21)).isInstanceOf(MavenExecutionException.class)
                .hasMessageContaining("JDK 25").hasMessageContaining("JDK 21");
        assertThatThrownBy(() -> JavaCardSetup.requireJdk(8)).isInstanceOf(MavenExecutionException.class)
                .hasMessageContaining("JDK 25").hasMessageContaining("JDK 8");
    }

    /** {@code java.specification.version} is {@code 1.8} on JDK 8 and the feature release from JDK 9 on. */
    @Test
    void theJdkIsReadFromTheSpecificationVersionAsJdk8NamesIt() {
        assertThat(JavaCardSetup.feature("1.8")).isEqualTo(8);
        assertThat(JavaCardSetup.feature("11")).isEqualTo(11);
        assertThat(JavaCardSetup.feature("25")).isEqualTo(25);
        assertThat(JavaCardSetup.feature(System.getProperty("java.specification.version")))
                .isEqualTo(Runtime.version().feature());
    }

    /**
     * {@code -Djavacard.setup=false} lets every Maven command run on any JDK, as the README promises: the JDK check
     * comes after it, and on an older JDK the setup could not run anyway.
     */
    @Test
    void setupFalseAsAUserOrSystemPropertyComesBeforeTheJdkCheck() throws MavenExecutionException {
        Properties user = new Properties();
        user.setProperty(BuildSettings.SETUP_PROPERTY, "false");
        Properties system = new Properties();
        system.setProperty(BuildSettings.SETUP_PROPERTY, "false");

        new JavaCardSetup().run(session(user, new Properties()), "1.8");
        new JavaCardSetup().run(session(new Properties(), system), "21");
        assertThatThrownBy(() -> new JavaCardSetup().run(session(new Properties(), new Properties()), "1.8"))
                .isInstanceOf(MavenExecutionException.class).hasMessageContaining("JDK 8");
    }

    @Test
    void theEntryPointAndTheSetupNeedTheSameJdk() {
        assertThat(JavaCardSetup.REQUIRED_JDK).isEqualTo(ProjectsSetup.REQUIRED_JDK);
    }

    @Test
    void onJdk25TheExtensionRuns() throws MavenExecutionException {
        JavaCardSetup.requireJdk(25);
        JavaCardSetup.requireJdk(Runtime.version().feature());
    }

    @Test
    void theExtensionKnowsThePluginVersion() {
        assertThat(ProjectsSetup.ownVersion()).isEqualTo(PluginDescriptor.pluginElement("version"));
    }

    @Test
    void declaredAsAnExtensionThatDidNotRunTheGoalWarns() throws Exception {
        MojoRunner runner = runner();
        Plugin plugin = new Plugin();
        plugin.setGroupId("name.velikodniy");
        plugin.setArtifactId("javacard-express-maven-plugin");
        plugin.setExtensions(true);
        runner.project().getBuild().addPlugin(plugin);

        runner.execute();

        assertThat(runner.log().messages(Level.WARNING)).anySatisfy(warning -> assertThat(warning)
                .contains("<extensions>true</extensions>", "did not set", "Maven ", "JDK "));
        assertThat(runner.log().messages(Level.INFO)).noneSatisfy(info -> assertThat(info)
                .contains("<extensions>true</extensions>"));
    }

    @Test
    void withoutTheExtensionTheGoalPointsToIt() throws Exception {
        MojoRunner runner = runner();

        runner.execute();

        assertThat(runner.log().messages(Level.INFO)).anySatisfy(info -> assertThat(info)
                .contains("<extensions>true</extensions>", "javacard.setup=false"));
    }

    @Test
    void afterTheExtensionOrWithSetupOffTheGoalSaysNothingAboutIt() throws Exception {
        MojoRunner setUp = runner();
        setUp.project().setContextValue(ProjectsSetup.CONTEXT_KEY, Boolean.TRUE);
        MojoRunner off = runner().property(BuildSettings.SETUP_PROPERTY, "false");

        setUp.execute();
        off.execute();

        assertThat(setUp.log().text()).doesNotContain("<extensions>true</extensions>");
        assertThat(off.log().text()).doesNotContain("<extensions>true</extensions>");
    }

    private static final int JAVA_8 = 52;

    private static int majorVersion(String className) throws IOException {
        try (InputStream in = JavaCardSetup.class.getResourceAsStream("/" + className.replace('.', '/') + ".class");
             DataInputStream data = new DataInputStream(in)) {
            data.readInt();
            data.readUnsignedShort();
            return data.readUnsignedShort();
        }
    }

    /**
     * A project that turns its setup off in the POM ({@code <javacard.setup>false</javacard.setup>} or the plugin's
     * {@code <setup>false</setup>}) builds on any JDK too, when every project that declares the extension does so.
     */
    @Test
    void setupFalseInEveryDeclaringProjectComesBeforeTheJdkCheck() throws MavenExecutionException {
        MavenSession byProperty = session(new Properties(), new Properties());
        byProperty.setProjects(List.of(declaring("<properties><javacard.setup>false</javacard.setup></properties>",
                "")));
        MavenSession byConfiguration = session(new Properties(), new Properties());
        byConfiguration.setProjects(List.of(declaring("", "<configuration><setup>false</setup></configuration>")));
        MavenSession oneOn = session(new Properties(), new Properties());
        oneOn.setProjects(List.of(declaring("", "<configuration><setup>false</setup></configuration>"),
                declaring("", "")));

        new JavaCardSetup().run(byProperty, "17");
        new JavaCardSetup().run(byConfiguration, "17");
        assertThatThrownBy(() -> new JavaCardSetup().run(oneOn, "17")).isInstanceOf(MavenExecutionException.class)
                .hasMessageContaining("JDK 17");
    }

    private static MavenProject declaring(String properties, String configuration) {
        String pom = "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId>"
                + "<artifactId>app</artifactId><version>1.0</version>" + properties
                + "<build><plugins><plugin><groupId>name.velikodniy</groupId>"
                + "<artifactId>javacard-express-maven-plugin</artifactId><version>1.2.3</version>"
                + "<extensions>true</extensions>" + configuration + "</plugin></plugins></build></project>";
        try {
            return new MavenProject(new MavenXpp3Reader().read(new StringReader(pom)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MavenSession session(Properties user, Properties system) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties(user);
        request.setSystemProperties(system);
        MavenSession session = new MavenSession(null, null, request, new DefaultMavenExecutionResult());
        session.setProjects(List.of());
        return session;
    }

    private MojoRunner runner() {
        Path project = dir.resolve(String.valueOf(System.nanoTime()));
        Path classes = Samples.helloApplet().compile(project);
        return MojoRunner.forProject(project, classes).configure("packageAid", "A00000006212");
    }

    private void assertParameter(String name, String property, String defaultValue) {
        PluginDescriptor.Parameter parameter = descriptor.parameter(name).orElseThrow();
        assertThat(parameter.property()).as("property of <%s>", name).contains(property);
        assertThat(parameter.defaultValue()).as("default of <%s>", name).isEqualTo(defaultValue);
    }
}
