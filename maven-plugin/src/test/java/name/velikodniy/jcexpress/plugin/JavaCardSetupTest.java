package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.javac.CheckOptions;
import name.velikodniy.jcexpress.javac.JavaCardCheckProcessor;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The build extension adds to a project that declares the plugin with {@code <extensions>true</extensions>} what the
 * project does not configure itself, and never changes what it configures.
 */
class JavaCardSetupTest {

    private static final String VERSION = "1.2.3";
    private static final File BASEDIR = new File("/work/app");
    private static final String CHECK_PATH = "name.velikodniy:javacard-express-javac-plugin:" + VERSION;
    private static final String PLUGIN = """
            <plugin>
                <groupId>name.velikodniy</groupId>
                <artifactId>javacard-express-maven-plugin</artifactId>
                <version>1.2.3</version>
                <extensions>true</extensions>
                <configuration>
                    <packageAid>A00000006212</packageAid>
                    %s
                </configuration>
            </plugin>
            """;

    private final Properties user = new Properties();

    @Test
    void aProjectWithOnlyThePluginGetsTheWholeSetup() {
        MavenProject project = project(plugin(""), "", "");

        setUp(project);

        assertThat(buildExecutions(project)).singleElement().satisfies(execution -> {
            assertThat(execution.getPhase()).isEqualTo("process-classes");
            assertThat(text(execution.getConfiguration(), "packageAid")).isEqualTo("A00000006212");
        });
        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId, Dependency::getScope,
                Dependency::getVersion).containsExactly(tuple("javacard-express-core", "test", VERSION),
                tuple("javacard-express-livecard", "test", VERSION), tuple("javacard-express-api", "provided", VERSION));
        assertThat(values(surefire(project).getConfiguration(), "classpathDependencyExcludes"))
                .containsExactly("name.velikodniy:javacard-express-api");
        assertThat(project.getProperties()).containsEntry("maven.compiler.release", "8")
                .containsEntry("maven.compiler.testRelease", "25");
        Xpp3Dom compile = compileConfiguration(project);
        assertThat(values(compile, "compilerArgs")).containsExactly("-Xlint:-options", "-Ajavacard.version=3.0.5",
                "-Ajavacard.supportInt32=false");
        assertThat(processorPath(compile)).containsExactly("name.velikodniy:javacard-express-javac-plugin:" + VERSION);
        assertThat(project.getContextValue(ProjectsSetup.CONTEXT_KEY)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void whatTheProjectConfiguresWins() {
        MavenProject project = project(plugin("") + """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.15.0</version>
                    <configuration>
                        <source>1.8</source>
                        <target>1.8</target>
                        <compilerArgs><arg>-Ajavacard.version=3.0.4</arg></compilerArgs>
                        <annotationProcessorPaths>
                            <path><groupId>org.example</groupId><artifactId>own-processor</artifactId><version>1</version></path>
                        </annotationProcessorPaths>
                        <annotationProcessors><annotationProcessor>org.example.Own</annotationProcessor></annotationProcessors>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.4</version>
                    <configuration>
                        <classpathDependencyExcludes>
                            <classpathDependencyExclude>org.example:other</classpathDependencyExclude>
                        </classpathDependencyExcludes>
                    </configuration>
                </plugin>
                """, """
                <dependency><groupId>name.velikodniy</groupId><artifactId>javacard-express-api</artifactId><version>1.2.3</version><scope>provided</scope></dependency>
                <dependency><groupId>name.velikodniy</groupId><artifactId>javacard-express-core</artifactId><version>1.2.3</version><scope>test</scope></dependency>
                """, "");

        setUp(project);

        assertThat(project.getProperties()).doesNotContainKey("maven.compiler.release")
                .containsEntry("maven.compiler.testRelease", "25");
        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId).containsExactly(
                "javacard-express-livecard", "javacard-express-api", "javacard-express-core");
        Xpp3Dom compile = compileConfiguration(project);
        assertThat(values(compile, "compilerArgs")).containsExactly("-Ajavacard.version=3.0.4",
                "-Ajavacard.supportInt32=false");
        assertThat(processorPath(compile)).containsExactly("org.example:own-processor:1",
                "name.velikodniy:javacard-express-javac-plugin:" + VERSION);
        assertThat(values(compile, "annotationProcessors")).containsExactly("org.example.Own",
                JavaCardCheckProcessor.class.getName());
        assertThat(values(surefire(project).getConfiguration(), "classpathDependencyExcludes"))
                .containsExactly("org.example:other", "name.velikodniy:javacard-express-api");
    }

    @Test
    void anExecutionOfTheBuildGoalIsKeptAndItsSettingsAreTheCheck() {
        MavenProject project = project("""
                <plugin>
                    <groupId>name.velikodniy</groupId>
                    <artifactId>javacard-express-maven-plugin</artifactId>
                    <version>1.2.3</version>
                    <extensions>true</extensions>
                    <executions>
                        <execution>
                            <id>wallet</id>
                            <goals><goal>build</goal></goals>
                            <configuration>
                                <javaCardVersion>3.0.4</javaCardVersion>
                                <supportInt32>true</supportInt32>
                                <globalPlatformApi>1.5</globalPlatformApi>
                                <packageName>com.example.wallet</packageName>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                """, "", "");

        setUp(project);

        assertThat(buildExecutions(project)).extracting(PluginExecution::getId).containsExactly("wallet");
        assertThat(values(compileConfiguration(project), "compilerArgs")).contains("-Ajavacard.version=3.0.4",
                "-Ajavacard.supportInt32=true", "-Ajavacard.globalPlatformApi=1.5",
                "-Ajavacard.packageName=com.example.wallet");
    }

    @Test
    void severalPackagesAreCheckedWithTheSettingsThatAcceptTheMost() {
        MavenProject project = project("""
                <plugin>
                    <groupId>name.velikodniy</groupId>
                    <artifactId>javacard-express-maven-plugin</artifactId>
                    <version>1.2.3</version>
                    <extensions>true</extensions>
                    <executions>
                        <execution>
                            <id>lib</id>
                            <goals><goal>build</goal></goals>
                            <configuration><packageName>com.example.lib</packageName><javaCardVersion>3.0.4</javaCardVersion></configuration>
                        </execution>
                        <execution>
                            <id>wallet</id>
                            <goals><goal>build</goal></goals>
                            <configuration><packageName>com.example.wallet</packageName><supportInt32>true</supportInt32></configuration>
                        </execution>
                    </executions>
                </plugin>
                """, "", "");

        setUp(project);

        assertThat(values(compileConfiguration(project), "compilerArgs")).contains("-Ajavacard.version=3.0.5",
                "-Ajavacard.supportInt32=true", "-Ajavacard.packageName=com.example.lib,com.example.wallet");
    }

    @Test
    void propertiesSetTheCheckAsTheyDoTheGoal() {
        user.setProperty(CheckOptions.VERSION, "3.0.4");
        MavenProject project = project(plugin(""), "", "<javacard.version>3.1.0</javacard.version>"
                + "<javacard.supportInt32>true</javacard.supportInt32>");

        setUp(project);

        assertThat(values(compileConfiguration(project), "compilerArgs")).contains("-Ajavacard.version=3.0.4",
                "-Ajavacard.supportInt32=true");
    }

    @Test
    void checkFalseLeavesOnlyTheCheckOut() {
        MavenProject project = project(plugin("<check>false</check>"), "", "");

        List<String> done = apply(project);

        assertThat(processorPath(compileConfiguration(project))).isEmpty();
        assertThat(values(compileConfiguration(project), "compilerArgs")).containsExactly("-Xlint:-options");
        assertThat(project.getDependencies()).hasSize(3);
        assertThat(done).anySatisfy(line -> assertThat(line).contains("no javac check", "javacard.check=false"));
    }

    @Test
    void setupFalseLeavesTheProjectAsItIs() {
        user.setProperty("javacard.setup", "false");
        MavenProject project = project(plugin(""), "", "");

        setUp(project);

        assertThat(project.getDependencies()).isEmpty();
        assertThat(buildExecutions(project)).isEmpty();
        assertThat(project.getContextValue(ProjectsSetup.CONTEXT_KEY)).isNull();
    }

    @Test
    void withoutTheExtensionsFlagThePluginIsAPlainPlugin() {
        MavenProject project = project(plugin("").replace("<extensions>true</extensions>", ""), "", "");

        setUp(project);

        assertThat(project.getDependencies()).isEmpty();
        assertThat(project.getContextValue(ProjectsSetup.CONTEXT_KEY)).isNull();
    }

    @Test
    void anotherVersionOfThePluginIsLeftToItsOwnExtension() {
        MavenProject project = project(plugin("").replace("<version>1.2.3</version>", "<version>9.9.9</version>"), "",
                "");

        setUp(project);

        assertThat(project.getDependencies()).isEmpty();
    }

    @Test
    void annotationProcessingTurnedOffStaysOff() {
        MavenProject project = project(plugin("") + """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.15.0</version>
                    <configuration><proc>none</proc></configuration>
                </plugin>
                """, "", "");

        List<String> done = apply(project);

        assertThat(processorPath(compileConfiguration(project))).isEmpty();
        assertThat(done).anySatisfy(line -> assertThat(line).contains("no javac check", "annotation processing is off"));
    }

    // ---- annotation processors of the project are never displaced (the check is added only next to them) ----

    @Test
    void aNamedProcessorFromTheClassPathKeepsRunningAndTheCheckIsLeftOut() {
        MavenProject project = project(plugin("") + compiler("""
                <annotationProcessors><annotationProcessor>org.example.Own</annotationProcessor></annotationProcessors>
                """), "", "");

        List<String> done = apply(project);

        Xpp3Dom compile = compileConfiguration(project);
        assertThat(processorPath(compile)).isEmpty();
        assertThat(values(compile, "annotationProcessors")).containsExactly("org.example.Own");
        assertThat(values(compile, "compilerArgs")).noneMatch(arg -> arg.startsWith("-Ajavacard."));
        assertThat(done).anySatisfy(line -> assertThat(line).contains("no javac check")
                .contains("annotationProcessorPaths"));
    }

    @Test
    void processorDiscoveryOnTheClassPathKeepsRunningAndTheCheckIsLeftOut() {
        MavenProject project = project(plugin("") + compiler("<proc>full</proc>"), "", "");

        apply(project);

        assertThat(processorPath(compileConfiguration(project))).isEmpty();
    }

    @Test
    void compilerArgumentsThatChooseProcessorsLeaveTheCheckOut() {
        for (String arg : List.of("-processor", "-processorpath", "--processor-path", "-proc:full", "-proc:only")) {
            MavenProject project = project(plugin("") + compiler(
                    "<compilerArgs><arg>" + arg + "</arg><arg>x</arg></compilerArgs>"), "", "");

            apply(project);

            assertThat(processorPath(compileConfiguration(project))).as(arg).isEmpty();
        }
    }

    /**
     * A project that names its processors with {@code -processor} and has a processor path of its own: the check's
     * jar joins that path (it changes nothing while the processor is not named), and the log line tells how to run
     * it: add its processor to the {@code -processor} list.
     */
    @Test
    void namedProcessorsWithAProcessorPathGetTheJarAndTheWayToRunTheCheck() {
        MavenProject project = project(plugin("") + compiler("""
                <annotationProcessorPaths>
                    <path><groupId>org.example</groupId><artifactId>own-processor</artifactId><version>1</version></path>
                </annotationProcessorPaths>
                <compilerArgs><arg>-processor</arg><arg>org.example.Own</arg></compilerArgs>
                """), "", "");

        List<String> done = apply(project);

        Xpp3Dom compile = compileConfiguration(project);
        assertThat(processorPath(compile)).containsExactly("org.example:own-processor:1", CHECK_PATH);
        assertThat(values(compile, "compilerArgs")).startsWith("-processor", "org.example.Own")
                .contains("-Ajavacard.version=3.0.5");
        assertThat(done).anySatisfy(line -> assertThat(line).contains(JavaCardCheckProcessor.class.getName())
                .contains("-processor"));
    }

    /** A project that already names the check's processor in its {@code -processor} list needs no advice. */
    @Test
    void aProcessorListThatNamesTheCheckGetsNoAdvice() {
        MavenProject project = project(plugin("") + compiler("""
                <annotationProcessorPaths>
                    <path><groupId>org.example</groupId><artifactId>own-processor</artifactId><version>1</version></path>
                </annotationProcessorPaths>
                <compilerArgs>
                    <arg>-processor</arg><arg>org.example.Own,name.velikodniy.jcexpress.javac.JavaCardCheckProcessor</arg>
                </compilerArgs>
                """), "", "");

        List<String> done = apply(project);

        assertThat(processorPath(compileConfiguration(project)))
                .containsExactly("org.example:own-processor:1", CHECK_PATH);
        assertThat(done).contains("the javac check of the Java Card subset in the main compilation");
    }

    @Test
    void namedProcessorsWithoutAProcessorPathKeepRunningAndTheLogLineSaysHowToAddTheCheck() {
        MavenProject project = project(plugin("") + compiler(
                "<compilerArgs><arg>-processor</arg><arg>org.example.Own</arg></compilerArgs>"), "", "");

        List<String> done = apply(project);

        assertThat(processorPath(compileConfiguration(project))).isEmpty();
        assertThat(done).anySatisfy(line -> assertThat(line).contains("no javac check")
                .contains("annotationProcessorPaths").contains(CHECK_PATH.substring(0, CHECK_PATH.lastIndexOf(':')))
                .contains(JavaCardCheckProcessor.class.getName()));
    }

    /** Options the project passes in the single string {@code compilerArgument} are its own too. */
    @Test
    void optionsInTheCompilerArgumentStringWin() {
        MavenProject project = project(plugin("") + compiler(
                "<compilerArgument>-Ajavacard.version=3.0.4 -Xlint:all</compilerArgument>"), "", "");

        apply(project);

        assertThat(values(compileConfiguration(project), "compilerArgs"))
                .noneMatch(arg -> arg.startsWith("-Ajavacard.version="));
    }

    @Test
    void failsafeKeepsTheStubsOffItsClassPathToo() {
        MavenProject project = project(plugin("") + """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.5.4</version>
                    <executions><execution><id>it</id><goals><goal>integration-test</goal></goals></execution></executions>
                </plugin>
                """, "", "");

        apply(project);

        Plugin failsafe = project.getPlugin("org.apache.maven.plugins:maven-failsafe-plugin");
        assertThat(values(failsafe.getConfiguration(), "classpathDependencyExcludes"))
                .containsExactly("name.velikodniy:javacard-express-api");
        assertThat(values(failsafe.getExecutionsAsMap().get("it").getConfiguration(), "classpathDependencyExcludes"))
                .containsExactly("name.velikodniy:javacard-express-api");
    }

    @Test
    void theProcPropertyNoneTurnsTheCheckOffLikeTheProcElement() {
        MavenProject project = project(plugin(""), "", "<maven.compiler.proc>none</maven.compiler.proc>");

        List<String> done = apply(project);

        assertThat(processorPath(compileConfiguration(project))).isEmpty();
        assertThat(done).noneSatisfy(line -> assertThat(line).startsWith("the javac check"))
                .anySatisfy(line -> assertThat(line).contains("no javac check", "annotation processing is off"));
    }

    @Test
    void anEmptyProcessorListKeepsDiscoveringTheProcessorsOnThePath() {
        MavenProject project = project(plugin("") + compiler("""
                <annotationProcessorPaths>
                    <path><groupId>org.example</groupId><artifactId>own-processor</artifactId><version>1</version></path>
                </annotationProcessorPaths>
                <annotationProcessors/>
                """), "", "");

        apply(project);

        Xpp3Dom compile = compileConfiguration(project);
        assertThat(processorPath(compile)).containsExactly("org.example:own-processor:1", CHECK_PATH);
        assertThat(values(compile, "annotationProcessors")).isEmpty();
    }

    // ---- a javac other than the one Maven runs on cannot load the check (Java 25 class files) ----

    @Test
    void aForkedJavacOfAnotherJdkGetsNoCheck() {
        MavenProject project = project(plugin("") + compiler(
                "<fork>true</fork><executable>/opt/jdk21/bin/javac</executable>"), "", "");

        List<String> done = apply(project);

        assertThat(processorPath(compileConfiguration(project))).isEmpty();
        assertThat(done).anySatisfy(line -> assertThat(line).contains("no javac check").contains("JDK 25"));
    }

    @Test
    void aToolchainGetsNoCheck() {
        MavenProject withPlugin = project(plugin("") + """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-toolchains-plugin</artifactId>
                    <version>3.2.0</version>
                </plugin>
                """, "", "");
        MavenProject withJdkToolchain = project(plugin("") + compiler(
                "<jdkToolchain><version>21</version></jdkToolchain>"), "", "");

        apply(withPlugin);
        apply(withJdkToolchain);

        assertThat(processorPath(compileConfiguration(withPlugin))).isEmpty();
        assertThat(processorPath(compileConfiguration(withJdkToolchain))).isEmpty();
    }

    // ---- skip ----

    @Test
    void skippingTheGoalSkipsTheCheck() {
        MavenProject configured = project(plugin("<skip>true</skip>"), "", "");
        MavenProject property = project(plugin(""), "", "");

        List<String> configuredDone = apply(configured);
        user.setProperty("javacard.skip", "true");
        List<String> propertyDone = apply(property);

        assertThat(processorPath(compileConfiguration(configured))).isEmpty();
        assertThat(processorPath(compileConfiguration(property))).isEmpty();
        assertThat(configuredDone).anySatisfy(line -> assertThat(line).contains("no javac check", "javacard.skip"));
        assertThat(propertyDone).anySatisfy(line -> assertThat(line).contains("no javac check", "javacard.skip"));
    }

    // ---- export files of the build ----

    @Test
    void theGoalsExportFilesAndExportPathReachTheCheckAsAbsolutePaths() {
        MavenProject project = project(plugin("""
                <importExportFiles>
                    <importExportFile>lib/wallet.exp</importExportFile>
                    <importExportFile>/opt/vendor/extra.exp</importExportFile>
                </importExportFiles>
                <exportPath><entry>kits/api_export_files</entry></exportPath>
                """), "", "");

        apply(project);

        assertThat(values(compileConfiguration(project), "compilerArgs")).contains(
                "-Ajavacard.exportFiles=" + new File(BASEDIR, "lib/wallet.exp").getAbsolutePath()
                        + File.pathSeparator + new File("/opt/vendor/extra.exp").getAbsolutePath(),
                "-Ajavacard.exportPath=" + new File(BASEDIR, "kits/api_export_files").getAbsolutePath());
    }

    /**
     * The check stands for one conversion. Executions of the goal that give different export files (one target's
     * file next to another's for the same package) would make it see two files of one package, which no conversion
     * does: then it links against the built-in data, and the log line says so.
     */
    @Test
    void exportFilesThatDifferBetweenTheExecutionsAreNotPassedToTheCheck() {
        MavenProject project = project(twoExecutions(
                "<importExportFiles><importExportFile>vendor/jc304/framework.exp</importExportFile></importExportFiles>",
                "<importExportFiles><importExportFile>vendor/jc305/framework.exp</importExportFile></importExportFiles>"),
                "", "");

        List<String> done = apply(project);

        assertThat(values(compileConfiguration(project), "compilerArgs"))
                .noneMatch(arg -> arg.startsWith("-Ajavacard.export"));
        assertThat(done).anySatisfy(line -> assertThat(line).contains("export files").contains("differ"));
    }

    @Test
    void theSameExportFilesInEveryExecutionArePassedOnce() {
        String files = "<importExportFiles><importExportFile>lib/wallet.exp</importExportFile></importExportFiles>"
                + "<exportPath><entry>kits/api_export_files</entry></exportPath>";
        MavenProject project = project(twoExecutions(files, files), "", "");

        apply(project);

        assertThat(values(compileConfiguration(project), "compilerArgs")).contains(
                "-Ajavacard.exportFiles=" + new File(BASEDIR, "lib/wallet.exp").getAbsolutePath(),
                "-Ajavacard.exportPath=" + new File(BASEDIR, "kits/api_export_files").getAbsolutePath());
    }

    /**
     * Some expressions ({@code ${session.executionRootDirectory}}) are resolved only when plugins run. The compiler
     * plugin resolves them in its arguments as well, so such an entry goes to the check as written and arrives
     * resolved; an entry that is still not there then is left to the conversion by the check itself.
     */
    @Test
    void entriesWithExpressionsMavenResolvesWhenPluginsRunArePassedAsWritten() {
        MavenProject project = project(plugin("""
                <exportPath>
                    <entry>${session.executionRootDirectory}/ep</entry>
                    <entry>kits/api_export_files</entry>
                </exportPath>
                """), "", "");

        List<String> done = apply(project);

        assertThat(values(compileConfiguration(project), "compilerArgs")).contains("-Ajavacard.exportPath="
                + "${session.executionRootDirectory}/ep" + File.pathSeparator
                + new File(BASEDIR, "kits/api_export_files").getAbsolutePath());
        assertThat(done).noneSatisfy(line -> assertThat(line).contains("left to the conversion"));
    }

    @Test
    void withoutExportFilesTheCheckGetsNoSuchOption() {
        MavenProject project = project(plugin(""), "", "");

        apply(project);

        assertThat(values(compileConfiguration(project), "compilerArgs"))
                .noneMatch(arg -> arg.startsWith("-Ajavacard.export"));
    }

    // ---- the project's own test stack ----

    @Test
    void aTestNgProjectKeepsItsTestRunner() {
        MavenProject project = project(plugin(""),
                "<dependency><groupId>org.testng</groupId><artifactId>testng</artifactId><version>7.8.0</version><scope>test</scope></dependency>",
                "");

        ProjectSetup setup = setupOf(project);

        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId)
                .containsExactly("testng", "javacard-express-api");
        assertThat(setup.warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains("javacard-express-core", "javacard-express-livecard", "TestNG"));
    }

    /** TestNG next to JUnit Jupiter: Jupiter already puts the tests on the JUnit Platform, so the toolkit comes. */
    @Test
    void testNgNextToJupiterGetsTheToolkit() {
        MavenProject project = project(plugin(""),
                "<dependency><groupId>org.testng</groupId><artifactId>testng</artifactId><version>7.8.0</version><scope>test</scope></dependency>"
                        + "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-api</artifactId><version>5.14.2</version><scope>test</scope></dependency>",
                "");

        ProjectSetup setup = setupOf(project);

        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId).containsExactly("testng",
                "junit-jupiter-api", "javacard-express-core", "javacard-express-livecard", "javacard-express-api");
        assertThat(setup.warnings()).isEmpty();
    }

    /**
     * JUnit 4 is no test stack of its own: Surefire runs JUnit 4 tests on the JUnit Platform through the vintage
     * engine, so a project (or a corporate parent) with junit:junit keeps its tests and gets the toolkit's.
     */
    @Test
    void aJUnit4ProjectGetsTheToolkitBesideItsTests() {
        MavenProject project = project(plugin(""),
                "<dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>",
                "");

        ProjectSetup setup = setupOf(project);

        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId).containsExactly("junit",
                "javacard-express-core", "javacard-express-livecard", "javacard-express-api");
        assertThat(setup.warnings()).isEmpty();
    }

    @Test
    void jUnit4NextToJupiterStillGetsTheToolkit() {
        MavenProject project = project(plugin(""), """
                <dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>
                <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.14.2</version><scope>test</scope></dependency>
                """, "");

        apply(project);

        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId).contains("javacard-express-core");
    }

    /** jCardSim is published under several group ids: each is the project's own simulator. */
    @ParameterizedTest
    @ValueSource(strings = {"com.klinec", "com.licel", "com.github.licel"})
    void aProjectWithItsOwnJcardsimKeepsIt(String groupId) {
        MavenProject project = project(plugin(""), "<dependency><groupId>" + groupId + "</groupId>"
                + "<artifactId>jcardsim</artifactId><version>3.0.5</version><scope>test</scope></dependency>", "");

        ProjectSetup setup = setupOf(project);

        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId)
                .containsExactly("jcardsim", "javacard-express-api");
        assertThat(setup.warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains(groupId + ":jcardsim"));
    }

    // ---- versions the project manages ----

    @Test
    void toolkitVersionsTheProjectManagesWin() {
        MavenProject project = project(plugin(""), "", "");
        Dependency managed = new Dependency();
        managed.setGroupId("name.velikodniy");
        managed.setArtifactId("javacard-express-core");
        managed.setVersion("0.3.0");
        project.getModel().setDependencyManagement(new DependencyManagement());
        project.getModel().getDependencyManagement().addDependency(managed);

        apply(project);

        assertThat(project.getDependencies()).extracting(Dependency::getArtifactId, Dependency::getVersion)
                .containsExactly(tuple("javacard-express-core", "0.3.0"), tuple("javacard-express-livecard", VERSION),
                        tuple("javacard-express-api", VERSION));
    }

    // ---- the owner's only card: the setup never adds anything that could reach a card ----

    /**
     * The whole setup, as Maven runs it ({@link ProjectsSetup}), leaves the session's user and system properties
     * as they are and gives the project no property whose name or value names a {@code jcx.} setting (an
     * {@code argLine} with {@code -Djcx.backend=livecard} would reach the test JVM), no profile, and no test JVM
     * settings in any plugin.
     */
    @Test
    void theSetupNeverAddsCardOrTestRuntimeSettings() throws MavenExecutionException {
        MavenProject project = project(plugin("") + """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.5.4</version>
                    <executions><execution><goals><goal>integration-test</goal></goals></execution></executions>
                </plugin>
                """, "", "");
        MavenSession session = session(project);
        Properties userBefore = copy(session.getUserProperties());
        Properties systemBefore = copy(session.getSystemProperties());

        new ProjectsSetup(VERSION).afterProjectsRead(session);

        assertThat(project.getContextValue(ProjectsSetup.CONTEXT_KEY)).as("the setup ran").isEqualTo(Boolean.TRUE);
        assertThat(session.getUserProperties()).isEqualTo(userBefore);
        assertThat(session.getSystemProperties()).isEqualTo(systemBefore);
        assertThat(project.getProperties().stringPropertyNames()).noneMatch(name -> name.startsWith("jcx."))
                .doesNotContain("argLine");
        assertThat(project.getProperties().values()).noneMatch(value -> String.valueOf(value).contains("jcx."));
        assertThat(project.getModel().getProfiles()).isEmpty();
        for (Plugin plugin : project.getBuildPlugins()) {
            for (Object configuration : configurations(plugin)) {
                String xml = String.valueOf(configuration);
                assertThat(xml).as(plugin.getKey()).doesNotContain("jcx").doesNotContain("<argLine")
                        .doesNotContain("<systemPropertyVariables").doesNotContain("<environmentVariables")
                        .doesNotContain("<groups").doesNotContain("<excludedGroups");
            }
        }
    }

    /** A model the setup does not expect (here a configuration that is no XML) stops the build with the way out. */
    @Test
    void aSetupThatFailsStopsTheBuildNamingTheProjectAndTheSwitch() {
        MavenProject project = project(plugin(""), "", "");
        project.getPlugin(CompilerSetup.COMPILER).setConfiguration("not a DOM");
        project.getPlugin(CompilerSetup.COMPILER).getExecutionsAsMap().get("default-compile")
                .setConfiguration("not a DOM");

        assertThatThrownBy(() -> setUpOrFail(project)).isInstanceOf(MavenExecutionException.class)
                .hasMessageContaining("com.example:app").hasMessageContaining("-Djavacard.setup=false");
    }

    @Test
    void aPomProjectHasNothingToSetUp() {
        MavenProject project = project(plugin(""), "", "");
        project.getModel().setPackaging("pom");

        setUp(project);

        assertThat(project.getDependencies()).isEmpty();
    }

    private void setUp(MavenProject project) {
        try {
            setUpOrFail(project);
        } catch (MavenExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private void setUpOrFail(MavenProject project) throws MavenExecutionException {
        new ProjectsSetup(VERSION).afterProjectsRead(session(project));
    }

    /** A session with this test's user properties and some system properties, as Maven builds one. */
    private MavenSession session(MavenProject project) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties(user);
        Properties system = new Properties();
        system.setProperty("java.version", "25");
        request.setSystemProperties(system);
        MavenSession session = new MavenSession(null, null, request, new DefaultMavenExecutionResult());
        session.setProjects(List.of(project));
        return session;
    }

    private static Properties copy(Properties properties) {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    /** Runs the setup of one project directly and returns it, with its warnings. */
    private ProjectSetup setupOf(MavenProject project) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties(user);
        request.setSystemProperties(new Properties());
        MavenSession session = new MavenSession(null, null, request, new DefaultMavenExecutionResult());
        session.setProjects(List.of(project));
        ProjectSetup setup = new ProjectSetup(project, session, project.getPlugin(ProjectSetup.PLUGIN_KEY), VERSION);
        setup.apply();
        return setup;
    }

    /** Runs the setup of one project directly and returns its log line's parts. */
    private List<String> apply(MavenProject project) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties(user);
        request.setSystemProperties(new Properties());
        MavenSession session = new MavenSession(null, null, request, new DefaultMavenExecutionResult());
        session.setProjects(List.of(project));
        return new ProjectSetup(project, session, project.getPlugin(ProjectSetup.PLUGIN_KEY), VERSION).apply();
    }

    private static String compiler(String configuration) {
        return """
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.15.0</version>
                    <configuration>%s</configuration>
                </plugin>
                """.formatted(configuration);
    }

    private static List<Object> configurations(Plugin plugin) {
        List<Object> configurations = new ArrayList<>();
        configurations.add(plugin.getConfiguration());
        plugin.getExecutions().forEach(execution -> configurations.add(execution.getConfiguration()));
        return configurations;
    }

    /** The plugin with two executions of the goal, each with its own configuration. */
    private static String twoExecutions(String first, String second) {
        return """
                <plugin>
                    <groupId>name.velikodniy</groupId>
                    <artifactId>javacard-express-maven-plugin</artifactId>
                    <version>1.2.3</version>
                    <extensions>true</extensions>
                    <configuration><packageAid>A00000006212</packageAid></configuration>
                    <executions>
                        <execution>
                            <id>jc304</id>
                            <goals><goal>build</goal></goals>
                            <configuration><javaCardVersion>3.0.4</javaCardVersion>%s</configuration>
                        </execution>
                        <execution>
                            <id>jc305</id>
                            <goals><goal>build</goal></goals>
                            <configuration><javaCardVersion>3.0.5</javaCardVersion>%s</configuration>
                        </execution>
                    </executions>
                </plugin>
                """.formatted(first, second);
    }

    private static String plugin(String configuration) {
        return PLUGIN.formatted(configuration);
    }

    /**
     * A project as Maven hands it to the extension: the plugins of the jar lifecycle with their default executions,
     * and the plugins' configuration merged into every execution.
     */
    private static MavenProject project(String plugins, String dependencies, String properties) {
        String pom = "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId>"
                + "<artifactId>app</artifactId><version>1.0</version><properties>" + properties + "</properties>"
                + "<dependencies>" + dependencies + "</dependencies><build><plugins>" + plugins
                + "</plugins></build></project>";
        try {
            Model model = new MavenXpp3Reader().read(new StringReader(pom));
            lifecycle(model, "maven-compiler-plugin", "default-compile", "default-testCompile");
            lifecycle(model, "maven-surefire-plugin", "default-test");
            model.getBuild().flushPluginMap();
            for (Plugin plugin : model.getBuild().getPlugins()) {
                plugin.flushExecutionMap();
                for (PluginExecution execution : plugin.getExecutions()) {
                    if (plugin.getConfiguration() != null) {
                        execution.setConfiguration(Xpp3Dom.mergeXpp3Dom((Xpp3Dom) execution.getConfiguration(),
                                new Xpp3Dom((Xpp3Dom) plugin.getConfiguration())));
                    }
                }
            }
            MavenProject project = new MavenProject(model);
            project.setFile(new File(BASEDIR, "pom.xml"));
            return project;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void lifecycle(Model model, String artifactId, String... executions) {
        Plugin plugin = model.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:" + artifactId);
        if (plugin == null) {
            plugin = new Plugin();
            plugin.setArtifactId(artifactId);
            model.getBuild().addPlugin(plugin);
        }
        for (String id : executions) {
            PluginExecution execution = new PluginExecution();
            execution.setId(id);
            plugin.addExecution(execution);
        }
    }

    private static List<PluginExecution> buildExecutions(MavenProject project) {
        return project.getPlugin(ProjectSetup.PLUGIN_KEY).getExecutions().stream()
                .filter(e -> e.getGoals().contains("build")).toList();
    }

    private static Plugin surefire(MavenProject project) {
        return project.getPlugin("org.apache.maven.plugins:maven-surefire-plugin");
    }

    private static Xpp3Dom compileConfiguration(MavenProject project) {
        return (Xpp3Dom) project.getPlugin(CompilerSetup.COMPILER).getExecutionsAsMap().get("default-compile")
                .getConfiguration();
    }

    private static List<String> processorPath(Xpp3Dom configuration) {
        Xpp3Dom paths = configuration == null ? null : configuration.getChild("annotationProcessorPaths");
        return paths == null ? List.of() : Arrays.stream(paths.getChildren())
                .map(p -> text(p, "groupId") + ":" + text(p, "artifactId") + ":" + text(p, "version")).toList();
    }

    private static List<String> values(Object configuration, String list) {
        Xpp3Dom element = configuration == null ? null : ((Xpp3Dom) configuration).getChild(list);
        return element == null ? List.of() : Arrays.stream(element.getChildren()).map(Xpp3Dom::getValue).toList();
    }

    private static String text(Object configuration, String name) {
        Xpp3Dom child = ((Xpp3Dom) configuration).getChild(name);
        return child == null ? null : child.getValue();
    }
}
