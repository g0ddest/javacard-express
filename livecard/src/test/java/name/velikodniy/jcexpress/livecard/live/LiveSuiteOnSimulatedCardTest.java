package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.livecard.junit.LiveCardExtension;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCard;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import name.velikodniy.jcexpress.livecard.thirdparty.ThirdPartyApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole live-card suite offline against a {@link SimulatedCard} (the validated card's ISD behaviour,
 * applets in jCardSim) through the real extension, guard, {@code PcscSession}, GlobalPlatform module and
 * converter. This checks the live tests themselves (expected values, ordering, cleanup) before they meet the
 * real card. It cannot find converter errors that only show in the CAP file (the simulator runs the classes);
 * {@code TestAppletsVerifierTest} covers those.
 */
class LiveSuiteOnSimulatedCardTest {

    private static final List<Class<?>> SUITE = List.of(CardIdentificationLiveTest.class, SecureChannelLiveTest.class,
            PcscSessionLiveTest.class, AppletLifecycleLiveTest.class, InstallAndStatusLiveTest.class,
            ConverterFeaturesLiveTest.class, OpenSourceAppletsLiveTest.class, CryptoApiLiveTest.class);

    @TempDir
    Path tmp;

    /** Runs classes with the given extra settings lines against the simulated card. */
    private EngineExecutionResults run(Path transcripts, String settingsLines, List<Class<?>> classes)
            throws IOException {
        Path settings = Files.writeString(tmp.resolve("livecard.properties"),
                "transcriptDir=" + transcripts.toString().replace("\\", "\\\\") + "\nverifierSdk=none\n"
                        + settingsLines);
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter(LiveCardExtension.ENABLED_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.CONNECTOR_PARAMETER, SimulatedCardConnector.class.getName())
                .configurationParameter(LiveCardExtension.ISOLATED_RUN_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.SETTINGS_FILE_PARAMETER, settings.toString())
                .selectors(classes.stream().map(DiscoverySelectors::selectClass).toArray(DiscoverySelector[]::new))
                .execute();
    }

    @Test
    void wholeSuitePassesAgainstTheSimulatedCardAndLeavesItClean() throws IOException {
        SimulatedCard card = SimulatedCardConnector.insertNewCard();
        Path transcripts = tmp.resolve("transcripts");

        EngineExecutionResults results = run(transcripts, "", SUITE);

        assertOutcome(results);
        assertThat(card.applications()).as("cleanup").isEmpty();
        assertThat(card.failedAuthentications()).isZero();
        assertTranscripts(transcripts);
    }

    /**
     * Without the submodules (a checkout without them, as on CI) the third-party cases abort with the command that
     * initializes them; nothing fails.
     */
    @Test
    void openSourceAppletsAbortWithoutTheirSubmodules() throws IOException {
        SimulatedCardConnector.insertNewCard();
        String submodules = System.getProperty(ThirdPartyApplet.ROOT_PROPERTY);
        System.setProperty(ThirdPartyApplet.ROOT_PROPERTY, Files.createDirectories(tmp.resolve("none")).toString());
        try {
            EngineExecutionResults results = run(tmp.resolve("oss"), "", List.of(OpenSourceAppletsLiveTest.class));

            assertThat(results.allEvents().failed().count()).isZero();
            assertThat(results.testEvents().aborted().stream().map(LiveSuiteOnSimulatedCardTest::describe)).hasSize(2)
                    .anyMatch(event -> event.contains(ThirdPartyApplet.PIV.initCommand()))
                    .anyMatch(event -> event.contains(ThirdPartyApplet.SMART_PGP.initCommand()));
        } finally {
            if (submodules == null) {
                System.clearProperty(ThirdPartyApplet.ROOT_PROPERTY);
            } else {
                System.setProperty(ThirdPartyApplet.ROOT_PROPERTY, submodules);
            }
        }
    }

    /**
     * A card that rejects the int package: without off-card verification the rejection cannot be told apart from a
     * broken CAP file, so LC-CONV-10 fails instead of being aborted as "no int support".
     */
    @Test
    void rejectedIntPackageFailsWhenTheCapFileWasNotVerified() throws IOException {
        SimulatedCardConnector.insertNewCard().refuseIntPackages();

        EngineExecutionResults results = run(tmp.resolve("no-int"), "", List.of(ConverterFeaturesLiveTest.class));

        assertThat(results.testEvents().failed().stream().map(LiveSuiteOnSimulatedCardTest::describe)).singleElement()
                .satisfies(event -> assertThat(event).startsWith("intSupport(LiveCard): ").contains("6A80"));
        assertThat(results.testEvents().aborted().stream().map(LiveSuiteOnSimulatedCardTest::describe))
                .noneMatch(event -> event.contains("no int support"));
    }

    /** Level '00' is opt-in: it runs only when the setting lists it. */
    @Test
    void securityLevel00RunsOnlyWhenListed() throws IOException {
        SimulatedCardConnector.insertNewCard();
        Path transcripts = tmp.resolve("with-00");

        EngineExecutionResults results = run(transcripts, "securityLevels=01,03,00\n",
                List.of(SecureChannelLiveTest.class));

        assertThat(results.allEvents().failed().stream().map(LiveSuiteOnSimulatedCardTest::describe)).isEmpty();
        assertThat(results.testEvents().succeeded().count()).isEqualTo(5);
        Path level00 = transcripts.resolve(SecureChannelLiveTest.class.getName())
                .resolve("authenticatesAndReadsUnderSecureMessaging-3.txt");
        assertThat(level00).content().contains("secure channel session open at security level 00");
    }

    /** Everything passes but the case jCardSim cannot run and third-party applets without their submodule. */
    private static void assertOutcome(EngineExecutionResults results) {
        assertThat(results.allEvents().failed().stream().map(LiveSuiteOnSimulatedCardTest::describe)).isEmpty();
        assertThat(results.allEvents().skipped().stream().map(LiveSuiteOnSimulatedCardTest::describe)).isEmpty();
        List<ThirdPartyApplet> missing = Arrays.stream(ThirdPartyApplet.values())
                .filter(applet -> !applet.initialized()).toList();
        List<String> aborted = results.testEvents().aborted().stream().map(LiveSuiteOnSimulatedCardTest::describe)
                .toList();
        assertThat(aborted).as("the case jCardSim cannot run, and third-party applets without their submodule")
                .hasSize(1 + missing.size())
                .anyMatch(event -> event.startsWith("abortedTransactionRestoresState(LiveCard): ")
                        && event.contains("does not roll back aborted transactions"));
        missing.forEach(applet -> assertThat(aborted).anyMatch(event -> event.contains(applet.initCommand())));
        assertThat(results.testEvents().succeeded().count()).isEqualTo(44 + 2 - missing.size());
    }

    private static void assertTranscripts(Path transcripts) throws IOException {
        assertThat(transcripts.resolve(AppletLifecycleLiveTest.class.getName()).resolve("answersExactlyLikeJCardSim.txt"))
                .content().contains("C: 8001000000", "R: 48656C6C6F2C20636172649000");
        Path level03 = transcripts.resolve(SecureChannelLiveTest.class.getName())
                .resolve("authenticatesAndReadsUnderSecureMessaging-2.txt");
        assertThat(level03).content().contains("secure channel session open at security level 03");
        if (ThirdPartyApplet.SMART_PGP.initialized()) {
            assertThat(transcripts.resolve(OpenSourceAppletsLiveTest.class.getName()).resolve("smartPgp.txt")).content()
                    .contains("deploying SmartPGP (https://github.com/ANSSI-FR/SmartPGP @", "C: 0047800002B60000");
        }
        try (Stream<Path> files = Files.walk(transcripts)) {
            assertThat(files.filter(path -> path.toString().endsWith(".txt")).map(LiveSuiteOnSimulatedCardTest::read))
                    .as("transcripts never contain key values").noneMatch(text -> text.contains("404142434445464748"));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String describe(Event event) {
        return event.getTestDescriptor().getDisplayName() + ": " + event.getPayload().map(Object::toString).orElse("");
    }
}
