package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.gp.GPException;
import name.velikodniy.jcexpress.livecard.guard.GuardViolationException;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCard;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import name.velikodniy.jcexpress.pcsc.PcscException;
import name.velikodniy.jcexpress.scp.SCPException;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deployment, tracking and cleanup of {@link LiveCard} against a {@link SimulatedCard}: the commands pass the
 * real guard and the GlobalPlatform module, the applets run in jCardSim.
 */
class LiveCardDeploymentTest {

    @TempDir
    Path transcripts;

    private LiveCardRun run;
    private SimulatedCard simulated;

    @BeforeEach
    void insertCard() {
        run = new LiveCardRun();
        simulated = SimulatedCardConnector.insertNewCard();
    }

    /** Settings for the simulated card: no Oracle kit in the build, so CAP files are loaded unverified (opt-out). */
    private LiveCardConfig config(String... settings) {
        Map<String, String> values = new HashMap<>(Map.of("transcriptDir", transcripts.toString(), "verifierSdk",
                "none"));
        for (int i = 0; i < settings.length; i += 2) {
            values.put(settings[i], settings[i + 1]);
        }
        return LiveCardConfig.of(values);
    }

    private LiveCard connect(LiveCardConfig config) {
        LiveCard card = LiveCard.connect(config, new SimulatedCardConnector(), run);
        card.transcriptTo(Path.of("test.txt"), "test");
        return card;
    }

    @Test
    void deployConvertsLoadsAndInstallsTheApplet() {
        try (LiveCard card = connect(config())) {
            Deployment hello = card.deploy(TestApplet.HELLO.pkg(card.config()));

            AID module = TestApplet.HELLO.moduleAid(card.config());
            assertThat(hello.packageAid()).isEqualTo("F04A43580101");
            assertThat(hello.capFile()).exists();
            assertThat(card.content().application(module)).hasValueSatisfying(app -> {
                assertThat(app.lifeCycleState()).isEqualTo(0x07);
                assertThat(app.executableLoadFileAid()).isEqualTo(HexFormat.of().parseHex("F04A43580101"));
            });
            card.session().select(module);
            assertThat(card.session().send(0x80, 0x01, 0x00, 0x00, null, 256).dataAsString()).isEqualTo("Hello, card");
        }
    }

    @Test
    void cleanupDeletesEverythingCreatedThroughTheCardAndVerifiesIt() throws IOException {
        try (LiveCard card = connect(config())) {
            Deployment params = card.deploy(TestApplet.PARAMS.pkg(card.config()));
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            AID second = TestApplet.PARAMS.instanceAid(card.config(), 0x02);
            card.manage(gp -> gp.installForInstall(params.packageAid(), TestApplet.PARAMS.moduleAid(card.config())
                    .toHex(), second.toHex(), 0x00, new byte[]{1}));

            card.cleanup();

            assertThat(simulated.applications()).isEmpty();
            assertThat(card.content().aidsUnder("F04A4358")).isEmpty();
        }
        assertThat(Files.readString(transcripts.resolve("test.txt"))).contains("cleanup verified with GET STATUS");
    }

    /** SELECT and MANAGE CHANNEL in a proprietary class are the selected applet's commands, on the card too. */
    @Test
    void proprietaryClassSelectAndManageChannelReachTheSelectedApplet() {
        HexFormat hex = HexFormat.of().withUpperCase();
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            card.session().select(TestApplet.HELLO.moduleAid(card.config()));

            assertThat(hex.formatHex(card.session().transmit(hex.parseHex("8070000001")))).isEqualTo("6D00");
            assertThat(hex.formatHex(card.session().transmit(hex.parseHex("80A4040008A000000151000000"))))
                    .isEqualTo("6D00");
            assertThat(card.session().send(0x80, 0x01, 0x00, 0x00, null, 256).dataAsString()).isEqualTo("Hello, card");
            assertThat(card.session().guard().blockedCount()).isZero();
        }
    }

    /** The client imports the server's package: the card refuses to delete the server first (GPCS 11.2.2.1). */
    @Test
    void cleanupDeletesAnImportingPackageBeforeTheImportedOne() {
        try (LiveCard card = connect(config())) {
            deployServerAndClient(card);

            assertThatThrownBy(() -> card.delete(TestApplet.SIO_SERVER.packageAid(card.config()), true))
                    .isInstanceOf(GPException.class).satisfies(e -> assertThat(((GPException) e).statusWord())
                            .isEqualTo(0x6985));
            card.cleanup();

            assertThat(card.content().aidsUnder("F04A4358")).isEmpty();
        }
    }

    @Test
    void leftoversOfAnImportingPackageAndTheImportedOneAreRemoved() {
        try (LiveCard card = connect(config())) {
            deployServerAndClient(card);
        }
        try (LiveCard card = LiveCard.connect(config(), new SimulatedCardConnector(), new LiveCardRun())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));

            assertThat(card.content().aidsUnder("F04A4358")).containsExactly("F04A4358010101", "F04A43580101");
        }
    }

    private static void deployServerAndClient(LiveCard card) {
        LiveCardConfig config = card.config();
        Deployment server = card.deploy(TestApplet.SIO_SERVER.pkg(config));
        card.deploy(TestApplet.SIO_CLIENT.pkg(config).withExportPath(server.exportPath()),
                AppletInstance.of(TestApplet.SIO_CLIENT.moduleAid(config))
                        .withParameters(TestApplet.SIO_SERVER.moduleAid(config).toBytes()));
    }

    @Test
    void leftoversUnderThePrefixAreRemovedBeforeTheFirstChangeOnly() {
        LiveCardConfig other = config("aidPrefix", "F0010203");
        try (LiveCard card = connect(other)) {
            card.deploy(TestApplet.HELLO.pkg(other));
        }
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
        }
        LiveCardRun nextRun = new LiveCardRun();
        try (LiveCard card = LiveCard.connect(config(), new SimulatedCardConnector(), nextRun)) {
            card.deploy(TestApplet.PARAMS.pkg(card.config()));

            assertThat(card.content().aidsUnder("F04A4358")).containsExactly("F04A4358010201", "F04A43580102");
            assertThat(card.content().aidsUnder("F0010203")).as("other prefix untouched").hasSize(2);
        }
    }

    /**
     * A load file under the prefix may hold an instance outside it that another tool created: leftover removal
     * deletes load files without related objects (GlobalPlatform Card Specification v2.3.1 11.2.2.1), so the card
     * refuses and the foreign instance stays.
     */
    @Test
    void leftoverRemovalNeverDeletesForeignInstancesOfALoadFileUnderThePrefix() {
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
        }
        simulated.addForeignApplication("A0000001234567", "F04A43580101", 0x00, 0x07);

        try (LiveCard card = LiveCard.connect(config(), new SimulatedCardConnector(), new LiveCardRun())) {
            assertThatThrownBy(() -> card.deploy(TestApplet.PARAMS.pkg(card.config())))
                    .isInstanceOf(LiveCardException.class).hasMessageContaining("could not be deleted")
                    .hasMessageContaining("F04A43580101");
        }
        assertThat(simulated.applications()).contains("A0000001234567");
        assertThat(simulated.received()).noneMatch(command -> command.startsWith("84E40080"));
    }

    /** The harness installs without privileges: a privileged application under the prefix is not its leftover. */
    @Test
    void privilegedApplicationUnderThePrefixIsNotDeletedAsALeftover() {
        simulated.addForeignApplication("F04A43580199", "A0000001515350", 0x80, 0x07);

        try (LiveCard card = connect(config())) {
            assertThatThrownBy(() -> card.deploy(TestApplet.HELLO.pkg(card.config())))
                    .isInstanceOf(LiveCardException.class).hasMessageContaining("F04A43580199")
                    .hasMessageContaining("privileges");
        }
        assertThat(simulated.applications()).contains("F04A43580199");
        assertThat(simulated.received()).noneMatch(command -> command.startsWith("84E4"));
    }

    /** A run interrupted after locking an application leaves it LOCKED: the next run unlocks it before deleting. */
    @Test
    void lockedLeftoverIsUnlockedBeforeItIsDeleted() throws IOException {
        simulated.refuseDeletingLockedApplications();
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            card.lock(TestApplet.HELLO.moduleAid(card.config()));
        }

        try (LiveCard card = LiveCard.connect(config(), new SimulatedCardConnector(), new LiveCardRun())) {
            card.transcriptTo(Path.of("next-run.txt"), "next run");
            card.deploy(TestApplet.PARAMS.pkg(card.config()));

            assertThat(card.content().aidsUnder("F04A4358")).containsExactly("F04A4358010201", "F04A43580102");
        }
        assertThat(Files.readString(transcripts.resolve("next-run.txt")))
                .contains("removing leftovers of earlier runs under F04A4358", "locked [F04A4358010101]");
    }

    @Test
    void aidOutsideThePrefixIsRejectedBeforeAnythingIsSent() {
        try (LiveCard card = connect(config())) {
            AppletPackage foreign = AppletPackage.of(TestApplet.classesDirectory(), "com.jcx.livecard.hello",
                    AID.fromHex("A00000000301")).withApplet("HelloApplet", AID.fromHex("A0000000030101"));

            assertThatThrownBy(() -> card.deploy(foreign)).isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("outside the configured prefix F04A4358");
            assertThat(simulated.received()).isEmpty();
        }
    }

    @Test
    void conversionFailureSendsNothingAndLeavesNoCapFile() throws IOException {
        Path stale = Files.createDirectories(transcripts.resolve("cap")).resolve("com.jcx.livecard.missing.cap");
        Files.write(stale, new byte[]{1, 2, 3});
        try (LiveCard card = connect(config())) {
            AppletPackage broken = AppletPackage.of(TestApplet.classesDirectory(), "com.jcx.livecard.missing",
                    card.aid("01FF")).withApplet("NoSuchApplet", card.aid("01FF01"));

            assertThatThrownBy(() -> card.deploy(broken)).isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("nothing was loaded");
            assertThat(simulated.received()).isEmpty();
            assertThat(stale).as("the CAP file of an earlier run is gone").doesNotExist();
        }
    }

    @Test
    void everyDeploymentConvertsAgain() throws IOException {
        try (LiveCard card = connect(config())) {
            Path saved = card.deploy(TestApplet.HELLO.pkg(card.config())).capFile();
            card.cleanup();
            Files.write(saved, new byte[]{1, 2, 3});

            Deployment again = card.deploy(TestApplet.HELLO.pkg(card.config()));

            assertThat(Files.readAllBytes(again.capFile())).hasSize(again.capSize());
            assertThat(simulated.applications()).containsExactly(TestApplet.HELLO.moduleAid(card.config()).toHex());
        }
    }

    /** DELETE of an instance, then INSTALL of the same AID: a new instance, as on a card (GPCS v2.3.1 11.2, 11.5). */
    @Test
    void deletedInstanceIsInstalledAgain() {
        try (LiveCard card = connect(config())) {
            Deployment hello = card.deploy(TestApplet.HELLO.pkg(card.config()));
            AID module = TestApplet.HELLO.moduleAid(card.config());
            card.session().select(module);
            counter(card);
            assertThat(card.delete(module, false).sw()).isEqualTo(0x9000);

            assertThat(card.install(hello, AppletInstance.of(module)).sw()).isEqualTo(0x9000);

            card.session().select(module);
            assertThat(counter(card)).as("the counter of a new instance").isEqualTo(1);
            card.cleanup();
            assertThat(simulated.applications()).isEmpty();
        }
    }

    /** A failure inside the simulated card is a transmission failure with its cause, not a lost connection. */
    @Test
    void failureOfTheSimulatedCardIsNotReportedAsALostConnection() {
        simulated = SimulatedCardConnector.insertNewCard(HexFormat.of().parseHex("404142434445464748494A4B4C4D4E4F"),
                module -> Optional.of("com.jcx.livecard.missing.NoSuchApplet"));
        try (LiveCard card = connect(config())) {
            assertThatThrownBy(() -> card.deploy(TestApplet.HELLO.pkg(card.config())))
                    .isInstanceOf(PcscException.class)
                    .hasMessageContaining("simulated card failed")
                    .hasMessageContaining("NoSuchApplet")
                    .hasMessageNotContaining("no longer valid");
        }
    }

    /**
     * PC/SC exclusive access belongs to the thread that connected: a command from another thread (an executor,
     * assertTimeoutPreemptively) is refused before the guard sees it, so the guard's picture of the card stays right.
     */
    @Test
    void commandsFromAnotherThreadAreRefusedBeforeTheGuardSeesThem() throws InterruptedException {
        try (LiveCard card = connect(config())) {
            ExecutorService other = Executors.newSingleThreadExecutor();
            try {
                Future<APDUResponse> sent = other.submit(() -> card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256));
                assertThatThrownBy(sent::get).isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(LiveCardException.class)
                        .rootCause().hasMessageContaining("Nothing was sent to the card");
            } finally {
                other.shutdownNow();
            }

            assertThat(simulated.received()).isEmpty();
            assertThat(card.session().guard().blockedCount()).isZero();
            assertThat(card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256).sw()).as("the connecting thread")
                    .isEqualTo(0x9000);
        }
    }

    @Test
    void wrongKeysNeverReachExternalAuthenticateAndAbortTheRun() {
        simulated = SimulatedCardConnector.insertNewCard(HexFormat.of().parseHex("0F".repeat(16)));
        try (LiveCard card = connect(config())) {
            assertThatThrownBy(card::gp).isInstanceOf(SCPException.class);

            assertThat(simulated.externalAuthentications()).isZero();
            assertThat(run.abortReason()).hasValueSatisfying(reason -> assertThat(reason)
                    .contains("does not match the configured keys"));
            assertThatThrownBy(() -> card.session().select(card.aid("010101")))
                    .isInstanceOf(GuardViolationException.class).hasMessageContaining("run aborted");
            assertThatThrownBy(() -> card.deploy(TestApplet.HELLO.pkg(card.config())))
                    .isInstanceOf(LiveCardException.class);
        }
        assertThatThrownBy(() -> connect(config())).isInstanceOf(LiveCardException.class)
                .hasMessageContaining("run aborted");
        assertThat(simulated.failedAuthentications()).isZero();
    }

    @Test
    void dangerousGlobalPlatformCommandsAreBlockedEvenInsideManage() {
        try (LiveCard card = connect(config())) {
            assertThatThrownBy(() -> card.manage(gp -> gp.putKeys(SCPKeys.defaultKeys(), 0x30)))
                    .isInstanceOf(GuardViolationException.class);
            assertThatThrownBy(() -> card.manage(gp -> gp.lockCard())).isInstanceOf(GuardViolationException.class);
            assertThatThrownBy(() -> card.manage(gp -> gp.deleteAid("A000000151000000")))
                    .isInstanceOf(GuardViolationException.class);
            assertThatThrownBy(() -> card.gp().deleteAid("F04A43580101")).as("no write access outside manage")
                    .isInstanceOf(GuardViolationException.class);

            Stream<String> dangerous = simulated.received().stream()
                    .filter(command -> command.startsWith("84D8") || command.startsWith("84F080")
                            || command.startsWith("84E4"));
            assertThat(dangerous).isEmpty();
        }
    }

    /** Collects log records. */
    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            records.add(logRecord);
        }

        @Override
        public void flush() {
            // nothing buffered
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    /**
     * JCVM 3.1 §1.3: a CAP file must be verified before it is loaded. Without a verifier and without the explicit
     * opt-out verifierSdk=none, deployment is refused before anything is converted or sent.
     */
    @Test
    void deploymentWithoutAVerifierDecisionIsRefusedBeforeAnythingIsSent() {
        LiveCardConfig undecided = LiveCardConfig.of(Map.of("transcriptDir", transcripts.toString()));
        try (LiveCard card = connect(undecided)) {
            assertThatThrownBy(() -> card.deploy(TestApplet.HELLO.pkg(card.config())))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("verifierSdk")
                    .hasMessageContaining("JCVM 3.1 §1.3")
                    .hasMessageContaining("verifierSdk=none");
            assertThat(simulated.received()).isEmpty();
        }
    }

    @Test
    void loadingWithoutVerifierWarnsOncePerRun() {
        RecordingHandler handler = new RecordingHandler();
        Logger logger = Logger.getLogger(LiveCard.class.getName());
        logger.addHandler(handler);
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            card.deploy(TestApplet.PARAMS.pkg(card.config()));
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(handler.records).singleElement().satisfies(warning -> {
            assertThat(warning.getLevel()).isEqualTo(Level.WARNING);
            assertThat(warning.getMessage()).contains("verifierSdk=none", "without an off-card verifier check");
        });
    }

    @Test
    void persistentStateSurvivesAReset() {
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            AID module = TestApplet.HELLO.moduleAid(card.config());
            card.session().select(module);
            int before = counter(card);

            card.reset();
            card.session().select(module);

            assertThat(counter(card)).isEqualTo(before + 1);
        }
    }

    @Test
    void transcriptRecordsTheExchangesButNoKeys() throws IOException {
        try (LiveCard card = connect(config())) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
        }
        String transcript = Files.readString(transcripts.resolve("test.txt"));

        assertThat(transcript).contains("C: 8050", "C: 8482", "C: 84E602", "C: 84E8", "R: ",
                "card cryptogram independently VERIFIED", "keys=GP test keys 40..4F",
                "simulated card: nothing answers 61XX or 6CXX below this transcript");
        assertThat(transcript).doesNotContain("404142434445464748494A4B4C4D4E4F");
    }

    private static int counter(LiveCard card) {
        APDUResponse response = card.session().send(0x80, 0x05, 0x00, 0x00, null, 2);
        assertThat(response.sw()).isEqualTo(0x9000);
        byte[] data = response.data();
        return ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
    }
}
