package name.velikodniy.jcexpress.livecard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Build check: CI/CD never runs the live-card suite. No CI configuration ({@code .github/workflows/*.yml}, the
 * composite actions in {@code .github/actions}, the other common CI files) and no Maven configuration
 * ({@code .mvn/*.config}) activates it: neither the profile, the tag, the script, a settings file,
 * {@code jcx.livecard.enabled} with any value but {@code false} (the system property that switches live mode on), the
 * CI override, nor the property {@code jcx.livecard} of the removed core opt-in. {@code JCX_LIVECARD_ENABLED} with
 * any value but {@code false} counts too: the harness refuses it at run time, and a CI file that sets it means to
 * start the suite. In the POMs (every element is checked as {@code <tag>='<text>'} with the same patterns) only the
 * unactivated {@code livecard} profile of {@code livecard/pom.xml} selects the tag or enables live mode, nothing sets
 * the CI override, and no Surefire {@code excludedEnvironmentVariables} hides a CI variable from the test JVM; the
 * module's default Surefire run excludes the tag and disables live mode. No Maven settings file in the repository
 * activates the profile.
 */
class NoLiveCardTestsInCiTest {

    /** The override of the CI refusal: never allowed, not even in the livecard profile. */
    private static final Pattern CI_OVERRIDE = Pattern.compile("jcx\\.livecard\\.allowCi|JCX_LIVECARD_ALLOW_CI",
            Pattern.CASE_INSENSITIVE);

    /** Ways to switch the live-card suite on from a command line, a YAML file, a Maven config file or a POM. */
    private static final List<Pattern> ACTIVATIONS = List.of(
            // the profile: -Plivecard, -P release,livecard, --activate-profiles=livecard (not -P!livecard)
            Pattern.compile("(?<![\\w-])-P\\s*['\"]?[\\w,.-]*livecard"),
            Pattern.compile("--activate-profiles[=\\s]+['\"]?[\\w,.-]*livecard"),
            // jcx.livecard.enabled, and jcx.livecard of the removed core opt-in, with any value but false (a bare
            // -D flag means true)
            Pattern.compile("jcx\\.livecard(?:\\.enabled)?(?![\\w.])(?!\\s*[=:]\\s*['\"]?false\\b)",
                    Pattern.CASE_INSENSITIVE),
            // the same as environment variables
            Pattern.compile("JCX_LIVECARD(?:_ENABLED)?(?!\\w)(?!\\s*[=:]\\s*['\"]?false\\b)", Pattern.CASE_INSENSITIVE),
            CI_OVERRIDE,
            // the tag: -Dgroups=livecard, -Dgroups='fast | livecard'
            Pattern.compile("(?<![a-z])groups\\s*[=:]\\s*(?:'[^']*|\"[^\"]*|[^\\s'\"]*)livecard"),
            Pattern.compile("livecard-tests\\.sh"),
            // a settings file written by the CI job
            Pattern.compile("livecard\\.properties"),
            // the backend switch of @JavaCardTest classes: jcx.backend=livecard (also live-card, quoted, YAML)
            Pattern.compile("jcx\\.backend\\s*[=:]\\s*['\"]?live-?card", Pattern.CASE_INSENSITIVE));

    /** CI configuration files besides {@code .github/workflows}. */
    private static final List<String> OTHER_CI_FILES = List.of(".gitlab-ci.yml", "Jenkinsfile", ".circleci/config.yml",
            "azure-pipelines.yml", ".travis.yml", "bitbucket-pipelines.yml", ".mvn/maven.config", ".mvn/jvm.config");

    /** Directories without project POMs: build output, local repositories, VCS and IDE data, third-party code. */
    private static final Set<String> SKIPPED = Set.of("target", "build", ".m2repo", ".git", "node_modules", ".idea",
            "third_party");

    private static Path root;

    @BeforeAll
    static void findProjectRoot() {
        root = ProjectRoot.find();
    }

    @Test
    void noCiConfigurationActivatesTheLiveCardSuite() throws IOException {
        List<Path> files = ciFiles(root);
        assertThat(files).as("CI files under %s", root).isNotEmpty();

        assertThat(findings(root, files)).as("CI/CD must never run tests against a live card").isEmpty();
    }

    /** The workflows, their composite actions ({@code .github/actions/**}) and the other common CI files. */
    private static List<Path> ciFiles(Path base) throws IOException {
        List<Path> files = new ArrayList<>(yamlFiles(base.resolve(".github").resolve("workflows")));
        files.addAll(yamlFiles(base.resolve(".github").resolve("actions")));
        OTHER_CI_FILES.stream().map(base::resolve).filter(Files::isRegularFile).forEach(files::add);
        return files;
    }

    private static List<String> findings(Path base, List<Path> files) throws IOException {
        List<String> findings = new ArrayList<>();
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (activates(lines.get(i))) {
                    findings.add(base.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).strip());
                }
            }
        }
        return findings;
    }

    /** Maven settings files ({@code *settings*.xml}) whose active profiles include livecard. */
    private static List<String> settingsFindings(Path base) throws Exception {
        List<String> findings = new ArrayList<>();
        for (Path file : projectFiles(base, name -> name.contains("settings") && name.endsWith(".xml"))) {
            for (Element active : elements(ProjectRoot.parse(file), "activeProfile")) {
                if (active.getTextContent().contains("livecard")) {
                    findings.add(base.relativize(file) + ": <activeProfile>" + active.getTextContent().strip());
                }
            }
        }
        return findings;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "run: ./mvnw -Plivecard verify -pl livecard -am",
        "run: mvn -P release,livecard verify",
        "run: ./mvnw verify --activate-profiles=livecard",
        "run: ./mvnw test -Djcx.livecard.enabled=true",
        "JCX_LIVECARD_ENABLED: 'true'",
        "run: ./mvnw verify -Djcx.livecard.allowCi=true",
        "run: ./mvnw test -Dgroups=livecard",
        "run: ./livecard-tests.sh",
        // the legacy opt-in of core's removed @LiveCard tests, and bare -D flags (Maven sets them to true)
        "run: ./mvnw test -pl core -Djcx.livecard=true",
        "MAVEN_OPTS: -Djcx.livecard=TRUE",
        "run: ./mvnw verify '-Djcx.livecard'",
        "run: ./mvnw verify -Djcx.livecard.enabled",
        // enabling values that are not a literal false, environment variables included
        "run: ./mvnw verify -Djcx.livecard.enabled=${{ inputs.live }}",
        "JCX_LIVECARD_ENABLED: ${{ vars.LIVE_CARD }}",
        "export JCX_LIVECARD_ENABLED=TRUE",
        "JCX_LIVECARD: 'true'",
        "run: ./mvnw test -Dgroups='fast | livecard'",
        // a settings file written on CI
        "run: echo enabled=true > ~/.jcx/livecard.properties",
        // the backend switch of @JavaCardTest classes
        "run: ./mvnw verify -Djcx.backend=livecard",
        "run: mvn test '-Djcx.backend=LIVE-CARD'",
        "MAVEN_OPTS: -Djcx.backend: livecard",
    })
    void detectorRecognizesActivations(String line) {
        assertThat(activates(line)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "run: ./mvnw clean verify -pl !container -B",
        "run: ./mvnw verify -pl livecard -am",
        "run: ./mvnw clean deploy -Prelease -B",
        "run: ./mvnw verify -P!livecard",
        "run: ./mvnw test -DexcludedGroups=livecard -Djcx.livecard.enabled=false",
        "JCX_LIVECARD_ENABLED: 'false'",
        "run: ./mvnw test -Djcx.livecard=false -Djcx.livecard.test.thirdParty=livecard/third_party",
        "run: ./mvnw test -Dgroups=fast -pl livecard",
        // the offline backends are fine on CI
        "run: ./mvnw verify -Djcx.backend=simulated-gp",
        "run: ./mvnw verify -Djcx.backend=embedded",
    })
    void detectorIgnoresOrdinaryBuilds(String line) {
        assertThat(activates(line)).isFalse();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', textBlock = """
        core/pom.xml     | <argLine>-Djcx.livecard.enabled=true</argLine>
        core/pom.xml     | <properties><jcx.livecard>true</jcx.livecard></properties>
        sm/pom.xml       | <environmentVariables><JCX_LIVECARD_ENABLED>true</JCX_LIVECARD_ENABLED></environmentVariables>
        pom.xml          | <groups>fast, livecard</groups>
        livecard/pom.xml | <profiles><profile><id>livecard</id><jcx.livecard.allowCi>true</jcx.livecard.allowCi></profile></profiles>
        livecard/pom.xml | <profiles><profile><id>livecard</id><activation/></profile></profiles>
        pom.xml          | <profiles><profile><id>livecard</id><properties><jcx.backend>livecard</jcx.backend></properties></profile></profiles>
        pom.xml          | <profiles><profile><id>livecard</id><build><plugins/></build></profile></profiles>
        core/pom.xml     | <profiles><profile><id>livecard</id><properties><surefire.failIfNoSpecifiedTests>false</surefire.failIfNoSpecifiedTests></properties></profile></profiles>
        """)
    void pomDetectorRecognizesActivations(String pom, String content, @TempDir Path temp) throws Exception {
        assertThat(pomFindings(pom, xml(temp, content))).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', textBlock = """
        livecard/pom.xml | <profiles><profile><id>livecard</id><groups>livecard</groups><jcx.livecard.enabled>true</jcx.livecard.enabled></profile></profiles>
        livecard/pom.xml | <excludedGroups>livecard</excludedGroups><jcx.livecard.enabled>false</jcx.livecard.enabled>
        pom.xml          | <modules><module>livecard</module></modules>
        pom.xml          | <profiles><profile><id>livecard</id><properties><surefire.failIfNoSpecifiedTests>false</surefire.failIfNoSpecifiedTests></properties></profile></profiles>
        """)
    void pomDetectorIgnoresTheModuleAndItsProfile(String pom, String content, @TempDir Path temp) throws Exception {
        assertThat(pomFindings(pom, xml(temp, content))).isEmpty();
    }

    /** Hiding the CI variables from the test JVM would defeat the runtime refusal (ContinuousIntegration). */
    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', textBlock = """
        livecard/pom.xml | <excludedEnvironmentVariables><excludedEnvironmentVariable>GITHUB_ACTIONS</excludedEnvironmentVariable></excludedEnvironmentVariables>
        pom.xml          | <excludedEnvironmentVariables>HOME,CI</excludedEnvironmentVariables>
        core/pom.xml     | <surefire.excludedEnvironmentVariables>JENKINS_URL</surefire.excludedEnvironmentVariables>
        """)
    void pomDetectorRecognizesHiddenCiVariables(String pom, String content, @TempDir Path temp) throws Exception {
        assertThat(pomFindings(pom, xml(temp, content))).isNotEmpty();
    }

    /** Composite actions of the workflows are CI files too. */
    @Test
    void compositeActionsAreScanned(@TempDir Path temp) throws IOException {
        Path action = Files.createDirectories(temp.resolve(".github/actions/live")).resolve("action.yml");
        Files.writeString(action, "runs:\n  steps:\n    - run: ./livecard-tests.sh\n");

        assertThat(ciFiles(temp)).contains(action);
        assertThat(findings(temp, ciFiles(temp))).singleElement().asString().contains("action.yml:3");
    }

    /** A Maven settings file can activate the profile for every build that uses it. */
    @Test
    void settingsFileThatActivatesTheProfileIsFound(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("ci-settings.xml"), "<settings><activeProfiles><activeProfile>release"
                + "</activeProfile><activeProfile>livecard</activeProfile></activeProfiles></settings>");
        Files.writeString(Files.createDirectories(temp.resolve(".mvn")).resolve("settings.xml"),
                "<settings><activeProfiles><activeProfile>release</activeProfile></activeProfiles></settings>");

        assertThat(settingsFindings(temp)).singleElement().asString().contains("ci-settings.xml");
    }

    @Test
    void noMavenSettingsFileActivatesTheLivecardProfile() throws Exception {
        assertThat(settingsFindings(root)).as("settings files under %s that activate the livecard profile", root)
                .isEmpty();
    }

    @Test
    void onlyTheUnactivatedLivecardProfileSelectsLiveTests() throws Exception {
        List<Path> poms = projectPoms();
        assertThat(poms).as("POMs under %s", root).contains(root.resolve("pom.xml"), root.resolve("livecard/pom.xml"));

        List<String> findings = new ArrayList<>();
        for (Path pom : poms) {
            findings.addAll(pomFindings(root.relativize(pom).toString().replace('\\', '/'), ProjectRoot.parse(pom)));
        }
        assertThat(findings).as("POM settings that could start the live-card suite: only the unactivated livecard"
                + " profile of livecard/pom.xml may select the tag or enable live mode, nothing sets the CI override")
                .isEmpty();
    }

    @Test
    void defaultSurefireRunOfTheModuleExcludesTheTagAndDisablesLiveMode() throws Exception {
        Document pom = ProjectRoot.parse(root.resolve("livecard").resolve("pom.xml"));

        assertThat(outsideProfiles(pom, "excludedGroups")).anyMatch(value -> value.contains("livecard"));
        assertThat(outsideProfiles(pom, "jcx.livecard.enabled")).containsExactly("false");
    }

    private static boolean activates(String line) {
        return ACTIVATIONS.stream().anyMatch(pattern -> pattern.matcher(line).find());
    }

    /** The YAML files below a directory (all depths), or none if it does not exist. */
    private static List<Path> yamlFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))
                    .sorted().toList();
        }
    }

    /** Every pom.xml of the project, without build output, local repositories and third-party code. */
    private static List<Path> projectPoms() throws IOException {
        return projectFiles(root, name -> name.equals("pom.xml"));
    }

    /** Files of the project whose name matches, without build output, local repositories and third-party code. */
    private static List<Path> projectFiles(Path base, Predicate<String> name) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                boolean skipped = !directory.equals(base) && SKIPPED.contains(directory.getFileName().toString());
                return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (name.test(file.getFileName().toString())) {
                    found.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }

    /**
     * Findings in a POM: elements that activate the suite outside the livecard module's livecard profile (the CI
     * override anywhere), and livecard profiles elsewhere or with an activation.
     *
     * @param pom      the POM's path relative to the project root, with {@code /}
     * @param document the parsed POM
     */
    private static List<String> pomFindings(String pom, Document document) {
        boolean livecardModule = pom.equals("livecard/pom.xml");
        List<String> findings = new ArrayList<>();
        for (Element element : elements(document, "*")) {
            String setting = element.getTagName() + "='" + ownText(element) + "'";
            boolean profile = inLivecardProfile(element, livecardModule) && !CI_OVERRIDE.matcher(setting).find();
            if ((activates(setting) && !profile) || hidesCiVariables(element)) {
                findings.add(pom + ": <" + element.getTagName() + ">" + ownText(element) + "</...>");
            }
        }
        for (Element profile : elements(document, "profile")) {
            if (!"livecard".equals(childText(profile, "id"))) {
                continue;
            }
            boolean unactivated = elements(profile, "activation").isEmpty();
            boolean allowed = livecardModule ? unactivated
                    : unactivated && pom.equals("pom.xml") && onlyLetsTestFiltersPass(profile);
            if (!allowed) {
                findings.add(pom + ": a livecard profile that is not the module's unactivated one");
            }
        }
        return findings;
    }

    /**
     * The root POM's {@code livecard} profile may only set {@code surefire.failIfNoSpecifiedTests} (so that
     * {@code -Dtest=<live class>} passes the modules built before livecard); anything else in it is a finding.
     */
    private static boolean onlyLetsTestFiltersPass(Element profile) {
        for (Node child = profile.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && !element.getTagName().equals("id")
                    && !element.getTagName().equals("properties")) {
                return false;
            }
        }
        for (Element properties : elements(profile, "properties")) {
            for (Node child = properties.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element property
                        && !property.getTagName().equals("surefire.failIfNoSpecifiedTests")) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Surefire's {@code excludedEnvironmentVariables} naming CI or a CI platform's variable. */
    private static boolean hidesCiVariables(Element element) {
        if (!element.getTagName().contains("excludedEnvironmentVariable")) {
            return false;
        }
        List<String> names = List.of(ownText(element).split("[\\s,]+"));
        return names.contains("CI") || ContinuousIntegration.PLATFORM_MARKERS.stream().anyMatch(names::contains);
    }

    /** The element's own text, without the text of child elements and comments. */
    private static String ownText(Element element) {
        StringBuilder text = new StringBuilder();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Text part) {
                text.append(part.getData());
            }
        }
        return text.toString().strip();
    }

    private static boolean inLivecardProfile(Element element, boolean livecardModule) {
        for (Node node = element.getParentNode(); node instanceof Element parent; node = parent.getParentNode()) {
            if (parent.getTagName().equals("profile")) {
                return livecardModule && "livecard".equals(childText(parent, "id"));
            }
        }
        return false;
    }

    private static List<String> outsideProfiles(Document document, String tag) {
        return elements(document, tag).stream().filter(element -> !insideProfile(element))
                .map(element -> element.getTextContent().strip()).distinct().toList();
    }

    private static boolean insideProfile(Element element) {
        for (Node node = element.getParentNode(); node instanceof Element parent; node = parent.getParentNode()) {
            if (parent.getTagName().equals("profile")) {
                return true;
            }
        }
        return false;
    }

    private static String childText(Element element, String tag) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element named && named.getTagName().equals(tag)) {
                return named.getTextContent().strip();
            }
        }
        return null;
    }

    private static List<Element> elements(Node scope, String tag) {
        NodeList nodes = scope instanceof Document document ? document.getElementsByTagName(tag)
                : ((Element) scope).getElementsByTagName(tag);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    /** A POM with the given content inside {@code <project>}, parsed like the project's POMs. */
    private static Document xml(Path directory, String content) throws Exception {
        Path pom = directory.resolve("pom.xml");
        Files.writeString(pom, "<project>" + content + "</project>", StandardCharsets.UTF_8);
        return ProjectRoot.parse(pom);
    }
}
