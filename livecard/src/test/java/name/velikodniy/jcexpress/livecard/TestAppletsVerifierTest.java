package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCard;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import name.velikodniy.jcexpress.livecard.thirdparty.ThirdPartyApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The test applets, converted for Java Card 3.0.4 as the live suite does, pass Oracle's off-card verifier of the
 * 3.0.4 development kit (black box through {@link CapVerifier}, the same gate {@code verifierSdk} enables).
 * Needs {@code build/oracle-sdks/jc304_kit} (not part of the repository); skipped otherwise.
 */
class TestAppletsVerifierTest {

    private static final Path KIT = Path.of("../build/oracle-sdks/jc304_kit");
    private static final String NO_KIT = "needs an Oracle Java Card 3.0.4 development kit in"
            + " build/oracle-sdks/jc304_kit (not part of the repository, see tools/oracle/README.md)";

    @TempDir
    Path tmp;

    static boolean kitAvailable() {
        return Files.isDirectory(KIT.resolve("lib")) && Files.isDirectory(KIT.resolve("api_export_files"));
    }

    /**
     * Each package is prepared as {@link LiveCard#deploy} prepares it: converted, verified with the kit's export
     * files plus its own (Export component) and those of the packages it imports, which are prepared first.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(TestApplet.class)
    @EnabledIf(value = "kitAvailable", disabledReason = NO_KIT)
    void convertedTestAppletPassesTheOffCardVerifier(TestApplet applet) {
        LiveCardConfig config = LiveCardConfig.of(Map.of("transcriptDir", tmp.toString()));
        CapVerifier verifier = new CapVerifier(KIT);
        AppletPackage pkg = applet.pkg(config);
        for (TestApplet imported : applet.imports()) {
            pkg = pkg.withExportPath(PreparedCap.prepare(imported.pkg(config), config, verifier).exportPath());
        }

        PreparedCap prepared = PreparedCap.prepare(pkg, config, verifier);

        assertThat(prepared.verification()).isEqualTo("passed the off-card verifier of jc304_kit");
    }

    /** Third-party applets (submodules; aborted when not initialized), prepared as the live test loads them. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ThirdPartyApplet.class)
    @EnabledIf(value = "kitAvailable", disabledReason = NO_KIT)
    void thirdPartyAppletPassesTheOffCardVerifier(ThirdPartyApplet applet) {
        applet.requireInitialized();
        LiveCardConfig config = LiveCardConfig.of(Map.of("transcriptDir", tmp.toString()));

        PreparedCap prepared = PreparedCap.prepare(applet.pkg(config), config, new CapVerifier(KIT));

        assertThat(prepared.verification()).isEqualTo("passed the off-card verifier of jc304_kit");
    }

    /** The export file lands in the export path layout; only packages with an Export component get one. */
    @Test
    @EnabledIf(value = "kitAvailable", disabledReason = NO_KIT)
    void exportFileOfAnExportingPackageIsSavedForImporters() {
        LiveCardConfig config = LiveCardConfig.of(Map.of("transcriptDir", tmp.toString()));
        CapVerifier verifier = new CapVerifier(KIT);

        PreparedCap server = PreparedCap.prepare(TestApplet.SIO_SERVER.pkg(config), config, verifier);
        PreparedCap hello = PreparedCap.prepare(TestApplet.HELLO.pkg(config), config, verifier);

        assertThat(server.exportPath()).isEqualTo(tmp.resolve("exp"));
        assertThat(server.exportPath().resolve("com/jcx/livecard/sioserver/javacard/sioserver.exp")).isNotEmptyFile();
        assertThat(hello.exportPath()).isNull();
        assertThat(tmp.resolve("exp/com/jcx/livecard/hello")).doesNotExist();
    }

    /** Without the server's export file the client cannot be converted: nothing would be loaded. */
    @Test
    void importingPackageNeedsTheExportFileOfTheImportedOne() {
        LiveCardConfig config = LiveCardConfig.of(Map.of("transcriptDir", tmp.toString()));

        assertThatThrownBy(() -> PreparedCap.prepare(TestApplet.SIO_CLIENT.pkg(config), config, null))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining("Conversion of com.jcx.livecard.sioclient failed");
    }

    @Test
    @EnabledIf(value = "kitAvailable", disabledReason = NO_KIT)
    void brokenCapFileFailsTheVerifier() throws ConverterException {
        LiveCardConfig config = LiveCardConfig.of(Map.of());
        byte[] cap = TestApplet.HELLO.pkg(config).converter(config.javaCardVersion()).build().convert().capFile();

        CapVerifier.Result verification = new CapVerifier(KIT).verify(Arrays.copyOf(cap, cap.length / 2));

        assertThat(verification.passed()).isFalse();
    }

    @Test
    @EnabledIf(value = "kitAvailable", disabledReason = NO_KIT)
    void deployVerifiesBeforeLoading() {
        SimulatedCard card = SimulatedCardConnector.insertNewCard();
        LiveCardConfig config = LiveCardConfig.of(Map.of("verifierSdk", KIT.toString(),
                "transcriptDir", tmp.toString()));

        try (LiveCard live = LiveCard.connect(config, new SimulatedCardConnector(), new LiveCardRun())) {
            Deployment deployment = live.deploy(TestApplet.HELLO.pkg(config));

            assertThat(deployment.verification()).isEqualTo("passed the off-card verifier of jc304_kit");
            assertThat(card.applications()).containsExactly(TestApplet.HELLO.moduleAid(config).toHex());
        }
    }

    @Test
    void directoryThatIsNoKitIsRejected() throws IOException {
        Files.createDirectories(tmp.resolve("lib"));

        assertThatThrownBy(() -> new CapVerifier(tmp)).isInstanceOf(LiveCardException.class)
                .hasMessageContaining("is not a Java Card 3.0.x development kit");
    }
}
