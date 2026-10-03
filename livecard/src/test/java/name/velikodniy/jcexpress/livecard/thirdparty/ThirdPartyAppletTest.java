package name.velikodniy.jcexpress.livecard.thirdparty;

import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The third-party applets: submodule registration, abort with the init command when a submodule is missing, AIDs,
 * and (with the submodules initialized) the build into {@code target/third-party}.
 */
class ThirdPartyAppletTest {

    private final LiveCardConfig config = LiveCardConfig.of(Map.of());

    @ParameterizedTest(name = "{0}")
    @EnumSource(ThirdPartyApplet.class)
    void missingSubmoduleAbortsWithTheCommandThatInitializesIt(ThirdPartyApplet applet, @TempDir Path empty) {
        assertThatThrownBy(() -> applet.requireInitializedIn(empty)).isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("is a git submodule that is not initialized")
                .hasMessageEndingWith(applet.initCommand());
    }

    /**
     * Only the pinned upstream code was validated: before a case loads an applet onto a card, its submodule must be
     * at the pinned commit without local changes (an untracked source file would be compiled and loaded too).
     */
    @Test
    void anotherCommitInTheSubmoduleAbortsTheCase(@TempDir Path root) throws Exception {
        assumeTrue(ThirdPartyApplet.git(root, "--version").isPresent(), "git is not available");
        Path repository = Files.createDirectories(root.resolve("PivApplet"));
        git(repository, "init", "-q");
        Files.writeString(repository.resolve("README"), "not the pinned commit\n");
        git(repository, "add", "README");
        git(repository, "-c", "user.name=t", "-c", "user.email=t@localhost", "commit", "-q", "-m", "other");

        assertThatThrownBy(() -> ThirdPartyApplet.PIV.requirePinnedCommitIn(root))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("not at the pinned commit 5cb14a9e8d16e92fbad73dcad86a219a9210554f")
                .hasMessageContaining("git submodule update livecard/third_party/PivApplet");
    }

    @Test
    void localChangesAreFound(@TempDir Path repository) throws Exception {
        assumeTrue(ThirdPartyApplet.git(repository, "--version").isPresent(), "git is not available");
        git(repository, "init", "-q");
        Files.writeString(repository.resolve("A.java"), "class A {}\n");
        git(repository, "add", "A.java");
        git(repository, "-c", "user.name=t", "-c", "user.email=t@localhost", "commit", "-q", "-m", "a");
        assertThat(ThirdPartyApplet.localChanges(repository)).isEmpty();

        Files.writeString(repository.resolve("B.java"), "class B {}\n");
        assertThat(ThirdPartyApplet.localChanges(repository)).contains("?? B.java");
        Files.writeString(repository.resolve("A.java"), "class A { int x; }\n");
        assertThat(ThirdPartyApplet.localChanges(repository)).contains(" M A.java");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ThirdPartyApplet.class)
    void initializedSubmoduleIsAtItsPinnedCommit(ThirdPartyApplet applet) {
        applet.requireInitialized();

        applet.requirePinnedCommit();
    }

    private static void git(Path directory, String... arguments) {
        assertThat(ThirdPartyApplet.git(directory, arguments)).as("git %s", String.join(" ", arguments)).isPresent();
    }

    @Test
    void initCommandsAndAids() {
        assertThat(ThirdPartyApplet.PIV.initCommand())
                .isEqualTo("git submodule update --init livecard/third_party/PivApplet");
        assertThat(ThirdPartyApplet.SMART_PGP.initCommand())
                .isEqualTo("git submodule update --init livecard/third_party/SmartPGP");
        assertThat(ThirdPartyApplet.PIV.packageAid(config).toHex()).isEqualTo("F04A43580122");
        assertThat(ThirdPartyApplet.PIV.moduleAid(config).toHex()).isEqualTo("F04A4358012201");
        assertThat(ThirdPartyApplet.SMART_PGP.packageAid(config).toHex()).isEqualTo("F04A43580123");
        assertThat(ThirdPartyApplet.SMART_PGP.moduleAid(config).toHex()).isEqualTo("F04A4358012301");
        assertThat(ThirdPartyApplet.byModuleAid("F04A4358012301")).contains(ThirdPartyApplet.SMART_PGP);
        assertThat(ThirdPartyApplet.byModuleAid("F04A4358010101")).isEmpty();
    }

    /** The submodules point at the upstream repositories; their code is never part of this repository. */
    @Test
    void submodulesAreRegisteredWithTheUpstreamUrls() throws IOException {
        Path root = Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".."));
        assumeTrue(Files.isRegularFile(root.resolve(".gitmodules")), "no .gitmodules in " + root.toAbsolutePath());

        assertThat(Files.readString(root.resolve(".gitmodules")))
                .contains("path = livecard/third_party/PivApplet", "url = https://github.com/arekinath/PivApplet")
                .contains("path = livecard/third_party/SmartPGP", "url = https://github.com/ANSSI-FR/SmartPGP");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ThirdPartyApplet.class)
    void initializedSubmoduleIsCompiledForJava8(ThirdPartyApplet applet) throws IOException {
        applet.requireInitialized();

        Path classFile = applet.classes().resolve(applet.className().replace('.', '/') + ".class");

        assertThat(classFile).isRegularFile();
        byte[] header = Files.readAllBytes(classFile);
        assertThat(((header[6] & 0xFF) << 8) | (header[7] & 0xFF)).as("class file major version").isEqualTo(52);
    }

    /** The preprocessed PivApplet keeps the upstream line numbers. */
    @Test
    void preprocessedPivAppletKeepsItsLineNumbers() throws IOException {
        ThirdPartyApplet.PIV.requireInitialized();
        String source = "net/cooperi/pivapplet/PivApplet.java";

        Path preprocessed = ThirdPartyApplet.PIV.classes().resolveSibling("src").resolve(source);

        assertThat(Files.readAllLines(preprocessed))
                .hasSameSizeAs(Files.readAllLines(ThirdPartyApplet.PIV.submodule().resolve("src").resolve(source)));
    }
}
