package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.LiveCardRun;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.OutputDirectoryCreator;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.reporting.FileEntry;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APDU transcripts of live-card classes: one directory per test class, named by its fully qualified (binary) name,
 * with a directory per {@code @Nested} class below it; every file starts anew in each run and continues within it;
 * every test's transcript is published with the test's result ({@code ExtensionContext.publishFile}), and the
 * class's set-up and cleanup transcripts with the class's.
 */
class LiveCardTranscriptsTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void insertCard() {
        SimulatedCardConnector.insertNewCard();
    }

    private EngineExecutionResults run(Class<?> testClass) throws IOException {
        Path settings = Files.writeString(tmp.resolve("livecard.properties"), "transcriptDir=" + transcripts()
                .toString().replace("\\", "\\\\") + "\nverifierSdk=none\n");
        Path reports = tmp.resolve("reports");
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter(LiveCardExtension.ENABLED_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.CONNECTOR_PARAMETER, SimulatedCardConnector.class.getName())
                .configurationParameter(LiveCardExtension.ISOLATED_RUN_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.SETTINGS_FILE_PARAMETER, settings.toString())
                .outputDirectoryCreator(new OutputDirectoryCreator() {
                    @Override
                    public Path getRootDirectory() {
                        return reports;
                    }

                    @Override
                    public Path createOutputDirectory(TestDescriptor descriptor) throws IOException {
                        return Files.createDirectories(reports.resolve(Integer.toHexString(
                                descriptor.getUniqueId().toString().hashCode())));
                    }
                })
                .selectors(Stream.of(testClass).map(DiscoverySelectors::selectClass).toArray(DiscoverySelector[]::new))
                .execute();
    }

    private Path transcripts() {
        return tmp.resolve("t");
    }

    @Test
    void transcriptsAreKeyedByTheFullyQualifiedClassName() throws IOException {
        run(LiveCardExtensionTest.FailsAfterDeploying.class);

        Path directory = transcripts().resolve(LiveCardExtensionTest.FailsAfterDeploying.class.getName());
        assertThat(directory.resolve("before-all.txt")).content().contains("C: 84E602");
        assertThat(directory.resolve("failsAfterUsingTheApplet.txt")).content().contains("C: 8001000000");
        assertThat(directory.resolve("after-all.txt")).content().contains("cleanup verified with GET STATUS");
        assertThat(transcripts().resolve("FailsAfterDeploying")).doesNotExist();
    }

    @Test
    void nestedClassesHaveTheirOwnDirectoryBelowTheirEnclosingClass() throws IOException {
        run(LiveCardExtensionTest.WithNestedClass.class);

        Path outer = transcripts().resolve(LiveCardExtensionTest.WithNestedClass.class.getName());
        assertThat(outer.resolve("deploys.txt")).content().contains("C: 84E602");
        assertThat(outer.resolve("Inner").resolve("seesTheSameCard.txt")).exists();
        assertThat(outer.resolve("Inner").resolve("before-all.txt")).exists();
        assertThat(outer.resolve("after-all.txt")).content().contains("cleanup verified with GET STATUS");
        assertThat(transcripts().resolve("Inner")).doesNotExist();
    }

    @Test
    void eachRunStartsTheTranscriptsAnew() throws IOException {
        run(LiveCardExtensionTest.FailsAfterDeploying.class);
        SimulatedCardConnector.insertNewCard();

        run(LiveCardExtensionTest.FailsAfterDeploying.class);

        Path test = transcripts().resolve(LiveCardExtensionTest.FailsAfterDeploying.class.getName())
                .resolve("failsAfterUsingTheApplet.txt");
        assertThat(Files.readString(test).split("C: 8001000000", -1)).as("one run's command").hasSize(2);
    }

    @Test
    void everyTestsTranscriptIsPublishedWithItsResult() throws IOException {
        EngineExecutionResults results = run(LiveCardExtensionTest.FailsAfterDeploying.class);

        Map<String, String> published = published(results);
        String testClass = "LiveCardExtensionTest$FailsAfterDeploying";
        assertThat(published).containsOnlyKeys("failsAfterUsingTheApplet(LiveCard) apdu-transcript.txt",
                testClass + " apdu-transcript-before-all.txt", testClass + " apdu-transcript-after-all.txt");
        assertThat(published.get("failsAfterUsingTheApplet(LiveCard) apdu-transcript.txt")).contains("C: 8001000000")
                .doesNotContain("C: 84E602");
        assertThat(published.get(testClass + " apdu-transcript-before-all.txt")).contains("C: 84E602");
        assertThat(published.get(testClass + " apdu-transcript-after-all.txt"))
                .contains("cleanup verified with GET STATUS");
    }

    /** {@link LiveCard#transcriptTo} outside JUnit: a file starts anew at its first use in a run. */
    @Test
    void aTranscriptFileStartsAnewInEachRunAndContinuesWithinIt() throws IOException {
        LiveCardConfig config = LiveCardConfig.of(Map.of("transcriptDir", transcripts().toString(), "verifierSdk",
                "none"));
        Path file = Files.createDirectories(transcripts()).resolve("run.txt");
        Files.writeString(file, "FROM AN EARLIER RUN\n");
        LiveCardRun run = new LiveCardRun();
        try (LiveCard card = LiveCard.connect(config, new SimulatedCardConnector(), run)) {
            card.transcriptTo(Path.of("run.txt"), "first part");
            card.transcriptTo(Path.of("other.txt"), "elsewhere");
            card.transcriptTo(Path.of("run.txt"), "second part");
        }
        assertThat(file).content().doesNotContain("FROM AN EARLIER RUN").contains("## first part", "## second part");

        try (LiveCard card = LiveCard.connect(config, new SimulatedCardConnector(), new LiveCardRun())) {
            card.transcriptTo(Path.of("run.txt"), "next run");
        }
        assertThat(file).content().contains("## next run").doesNotContain("## first part");
    }

    /** The published files by "test display name file name", with their content. */
    private static Map<String, String> published(EngineExecutionResults results) {
        return results.allEvents().fileEntryPublished().stream().collect(Collectors.toMap(
                event -> event.getTestDescriptor().getDisplayName() + " " + file(event).getPath().getFileName(),
                event -> read(file(event).getPath())));
    }

    private static FileEntry file(Event event) {
        return event.getRequiredPayload(FileEntry.class);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
