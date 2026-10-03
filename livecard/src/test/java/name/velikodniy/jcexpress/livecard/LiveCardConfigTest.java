package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Settings: defaults, the precedence of the sources, validation and that keys never leak into text.
 */
class LiveCardConfigTest {

    private static final String KEY = "00112233445566778899AABBCCDDEEFF";

    @TempDir
    Path tmp;

    private Path file(String name, String content) throws IOException {
        Path file = tmp.resolve(name).resolve(ConfigSources.FILE_NAME);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    @Test
    void defaultsMatchTheValidatedDevelopmentCard() {
        LiveCardConfig config = LiveCardConfig.of(Map.of());

        assertThat(config.enabled()).isFalse();
        assertThat(config.reader()).isNull();
        assertThat(config.keys().isTestKeys()).isTrue();
        assertThat(config.keyVersion()).isZero();
        assertThat(config.securityLevel()).isEqualTo(0x01);
        assertThat(config.securityLevels()).as("level 00 is opt-in").containsExactly(0x01, 0x03);
        assertThat(config.aidPrefix()).isEqualTo("F04A4358");
        assertThat(config.javaCardVersion()).isEqualTo(JavaCardVersion.V3_0_4);
        assertThat(config.isd()).isEqualTo("A000000151000000");
        assertThat(config.verifierSdk()).isNull();
        assertThat(config.transcriptDir()).isEqualTo(Path.of("target/livecard-transcripts"));
        assertThat(config.maxAuthFailures()).isEqualTo(1);
    }

    @Nested
    class Precedence {

        @Test
        void systemPropertyBeatsEnvironmentBeatsFilesBeatDefault() throws IOException {
            Path work = file("work", "reader=from-working-directory\n");
            Path home = file("home", "reader=from-home\nkvn=30\n");
            Map<String, String> properties = Map.of("jcx.livecard.reader", "from-property");
            Map<String, String> environment = Map.of("JCX_LIVECARD_READER", "from-environment");
            List<Path> files = List.of(work, home);

            assertThat(load(properties, environment, files).reader()).isEqualTo("from-property");
            assertThat(load(Map.of(), environment, files).reader()).isEqualTo("from-environment");
            assertThat(load(Map.of(), Map.of(), files).reader()).isEqualTo("from-working-directory");
            assertThat(load(Map.of(), Map.of(), List.of(home)).reader()).isEqualTo("from-home");
            assertThat(load(Map.of(), Map.of(), List.of()).reader()).isNull();
            assertThat(load(Map.of(), Map.of(), files).keyVersion()).as("other settings still from home")
                    .isEqualTo(0x30);
        }

        @Test
        void environmentVariablesUseUpperSnakeCase() {
            Map<String, String> environment = Map.of("JCX_LIVECARD_AID_PREFIX", "F0010203",
                    "JCX_LIVECARD_SECURITY_LEVEL", "03", "JCX_LIVECARD_JAVA_CARD_VERSION", "3.0.5",
                    "JCX_LIVECARD_MAX_AUTH_FAILURES", "2", "JCX_LIVECARD_ENABLED", "false");

            LiveCardConfig config = load(Map.of(), environment, List.of());

            assertThat(config.aidPrefix()).isEqualTo("F0010203");
            assertThat(config.securityLevel()).isEqualTo(0x03);
            assertThat(config.javaCardVersion()).isEqualTo(JavaCardVersion.V3_0_5);
            assertThat(config.maxAuthFailures()).isEqualTo(2);
            assertThat(config.enabled()).isFalse();
        }

        /**
         * Live-card mode is switched on only by the JVM system property, for one run: an environment variable would
         * arm every run of every project in that shell, also IDE runs, so it is refused (but for {@code false}).
         */
        @ParameterizedTest(name = "JCX_LIVECARD_ENABLED={0}")
        @CsvSource({"true", "TRUE", "yes", "1"})
        void onlyTheSystemPropertySwitchesLiveModeOn(String value) {
            Map<String, String> environment = Map.of("JCX_LIVECARD_ENABLED", value);

            assertThatThrownBy(() -> load(Map.of(), environment, List.of()))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("JCX_LIVECARD_ENABLED=" + value + " is refused")
                    .hasMessageContaining("-Djcx.livecard.enabled=true");
            assertThatThrownBy(() -> load(Map.of("jcx.livecard.enabled", "true"), environment, List.of()))
                    .as("also next to the system property").isInstanceOf(LiveCardException.class);
            assertThat(load(Map.of("jcx.livecard.enabled", "true"), Map.of(), List.of()).enabled()).isTrue();
            assertThat(load(Map.of("jcx.livecard.enabled", "true"), Map.of("JCX_LIVECARD_ENABLED", "false"),
                    List.of()).enabled()).as("the system property decides").isTrue();
        }

        /**
         * The backend switch of {@code @JavaCardTest} classes, {@code -Djcx.backend=livecard}, is the same one-run
         * opt-in: a JVM system property, never the environment.
         */
        @Test
        void theLivecardBackendSystemPropertySwitchesLiveModeOn() {
            assertThat(load(Map.of("jcx.backend", "livecard"), Map.of(), List.of()).enabled()).isTrue();
            assertThat(load(Map.of("jcx.backend", "LIVE-CARD"), Map.of(), List.of()).enabled()).isTrue();
            assertThat(load(Map.of("jcx.backend", "simulated-gp"), Map.of(), List.of()).enabled()).isFalse();
            assertThat(load(Map.of(), Map.of("JCX_BACKEND", "livecard"), List.of()).enabled())
                    .as("not from the environment").isFalse();
        }

        @Test
        void unknownSettingInAFileIsAnError() throws IOException {
            Path work = file("work", "key=" + KEY + "\n");

            assertThatThrownBy(() -> load(Map.of(), Map.of(), List.of(work)))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("Unknown live-card setting 'key'")
                    .hasMessageContaining(work.toString())
                    .hasMessageNotContaining(KEY);
        }

        @Test
        void standardSourcesAreTheWorkingDirectoryTheProjectRootAndTheHomeDirectory() {
            List<Path> files = ConfigSources.standard().files();

            assertThat(files.getFirst()).isEqualTo(Path.of("").toAbsolutePath().resolve("livecard.properties"));
            assertThat(files.getLast())
                    .isEqualTo(Path.of(System.getProperty("user.home"), ".jcx", "livecard.properties"));
        }

        /**
         * A module of a multi-module build finds the settings file of the project root: the working directory and
         * every parent directory of the same build (one with a build file), nearest first. The search ends at the
         * first directory outside the build, so a settings file further up is never read.
         */
        @Test
        void settingsFilesAreLookedUpInTheParentDirectoriesOfTheBuild() throws IOException {
            Path outside = Files.createDirectories(tmp.resolve("outside"));
            Path root = Files.createDirectories(outside.resolve("wallet"));
            Path module = Files.createDirectories(root.resolve("applet"));
            Files.writeString(root.resolve("pom.xml"), "<project/>");
            Files.writeString(module.resolve("pom.xml"), "<project/>");
            Path home = tmp.resolve("home");

            assertThat(ConfigSources.settingsFiles(module, null, home)).containsExactly(
                    module.resolve("livecard.properties"), root.resolve("livecard.properties"),
                    home.resolve(".jcx").resolve("livecard.properties"));
        }

        @Test
        void gradleBuildsCountAsBuildsAndTheMavenProjectRootIsReadOnce() throws IOException {
            Path root = Files.createDirectories(tmp.resolve("gradle-root"));
            Path module = Files.createDirectories(root.resolve("applet"));
            Files.writeString(root.resolve("settings.gradle.kts"), "");
            Files.writeString(module.resolve("build.gradle.kts"), "");
            Path elsewhere = Files.createDirectories(tmp.resolve("maven-root"));
            Path home = tmp.resolve("home");

            assertThat(ConfigSources.settingsFiles(module, root.toString(), home)).containsExactly(
                    module.resolve("livecard.properties"), root.resolve("livecard.properties"),
                    home.resolve(".jcx").resolve("livecard.properties"));
            assertThat(ConfigSources.settingsFiles(module, elsewhere.toString(), home))
                    .as("maven.multiModuleProjectDirectory outside the parent chain").containsExactly(
                            module.resolve("livecard.properties"), root.resolve("livecard.properties"),
                            elsewhere.resolve("livecard.properties"), home.resolve(".jcx").resolve("livecard.properties"));
        }

        @Test
        void theModulesSettingsFileWinsOverTheProjectRootsFile() throws IOException {
            Path root = Files.createDirectories(tmp.resolve("root"));
            Path module = Files.createDirectories(root.resolve("applet"));
            Files.writeString(root.resolve("pom.xml"), "<project/>");
            Files.writeString(module.resolve("pom.xml"), "<project/>");
            Files.writeString(root.resolve("livecard.properties"), "reader=from-root\nkvn=30\n");
            Files.writeString(module.resolve("livecard.properties"), "reader=from-module\n");

            LiveCardConfig config = load(Map.of(), Map.of(), ConfigSources.settingsFiles(module, null, tmp.resolve("h")));

            assertThat(config.reader()).isEqualTo("from-module");
            assertThat(config.keyVersion()).as("other settings from the project root").isEqualTo(0x30);
        }

        private LiveCardConfig load(Map<String, String> properties, Map<String, String> environment, List<Path> files) {
            return LiveCardConfig.load(new ConfigSources(properties, environment, files));
        }
    }

    @Nested
    class Keys {

        @Test
        void oneKeyIsUsedForEncMacAndDek() {
            LiveCardConfig config = LiveCardConfig.of(Map.of("keys", KEY));

            assertThat(config.keys().isTestKeys()).isFalse();
            assertThat(config.keys().mac()).isEqualTo(HexFormat.of().parseHex(KEY));
            assertThat(config.keys().toScpKeys().dek()).isEqualTo(HexFormat.of().parseHex(KEY));
        }

        @Test
        void threeKeysAreEncMacDek() {
            String enc = "01".repeat(16);
            String mac = "02".repeat(16);
            String dek = "03".repeat(16);

            CardKeys keys = LiveCardConfig.of(Map.of("keys", enc + ", " + mac + "," + dek)).keys();

            assertThat(keys.mac()).isEqualTo(HexFormat.of().parseHex(mac));
            assertThat(keys.toScpKeys().enc()).isEqualTo(HexFormat.of().parseHex(enc));
            assertThat(keys.toScpKeys().dek()).isEqualTo(HexFormat.of().parseHex(dek));
        }

        /** A malformed keys setting is described by its shape only, never echoed (also not in a cause). */
        @ParameterizedTest(name = "keys={0}")
        @CsvSource({
            "'" + KEY + "," + KEY + "', 2 comma-separated parts of 16 and 16 bytes",
            "'" + KEY + "," + KEY + ",0011', 3 comma-separated parts of 16 and 16 and 2 bytes",
            "'" + KEY + "AA', 17 bytes",
            "'" + KEY + "ZZ', 34 characters (not hex)",
        })
        void malformedKeysAreDescribedByTheirShapeOnly(String keys, String shape) {
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of("keys", keys)))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("value not shown: " + shape)
                    .hasMessageNotContaining(KEY)
                    .hasNoCause();
        }

        @Test
        void malformedKeysFromASettingsFileAreNotEchoedEither() throws IOException {
            Path work = file("work", "keys=" + KEY + "," + KEY + "\n");

            assertThatThrownBy(() -> LiveCardConfig.load(new ConfigSources(Map.of(), Map.of(), List.of(work))))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining(work.toString())
                    .hasMessageNotContaining(KEY);
        }

        /**
         * Maven passes -D properties to the test JVM, and Surefire writes the test JVM's system properties into its
         * XML reports: card keys are accepted only from the environment or a settings file.
         */
        @Test
        void keysAreNotAcceptedAsASystemProperty() {
            Map<String, String> properties = Map.of("jcx.livecard.keys", KEY);

            assertThatThrownBy(() -> LiveCardConfig.load(new ConfigSources(properties, Map.of(), List.of())))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("jcx.livecard.keys")
                    .hasMessageContaining("JCX_LIVECARD_KEYS")
                    .hasMessageNotContaining(KEY);
            assertThat(LiveCardConfig.load(new ConfigSources(Map.of("jcx.livecard.keys", "test"), Map.of(),
                    List.of())).keys().isTestKeys()).as("the public test keys").isTrue();
            assertThat(LiveCardConfig.load(new ConfigSources(Map.of(), Map.of("JCX_LIVECARD_KEYS", KEY), List.of()))
                    .keys().isTestKeys()).as("from the environment").isFalse();
        }

        @Test
        void keyValuesNeverAppearInText() {
            LiveCardConfig config = LiveCardConfig.of(Map.of("keys", KEY));

            assertThat(config.toString()).doesNotContain(KEY).contains("custom AES-128");
            assertThat(config.describe()).doesNotContain(KEY);
            assertThat(LiveCardConfig.of(Map.of()).describe()).contains("GP test keys 40..4F")
                    .doesNotContain("404142434445464748494A4B4C4D4E4F");
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest(name = "{0}={1}")
        @CsvSource({
            "enabled, yes",
            "keys, 0011",
            "keys, '" + KEY + "," + KEY + "'",
            "keys, '" + KEY + "," + KEY + ",0011223344556677'",
            "kvn, 100",
            "kvn, XY",
            "securityLevel, 02",
            "securityLevel, 31",
            "securityLevels, 02",
            "securityLevels, '01,01'",
            "securityLevels, ''",
            "aidPrefix, F04A",
            "aidPrefix, A000000151",
            "aidPrefix, A0000000620101",
            "aidPrefix, A00000",
            "aidPrefix, A000000151000000AA",
            "registeredRid, A0000003",
            "registeredRid, XYZ",
            "isd, A0000001",
            "javaCardVersion, 9.9.9",
            "maxAuthFailures, 0",
            "maxAuthFailures, 4",
        })
        void invalidValuesAreRejectedWithTheirOrigin(String setting, String value) {
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of(setting, value)))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("from argument")
                    .hasMessageNotContaining(KEY);
        }

        @Test
        void prefixMustNotOverlapTheConfiguredIsd() {
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of("isd", "F04A435800000000")))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("overlaps the Issuer Security Domain");
        }

        @Test
        void securityLevelsKeepTheirOrderAndMayListLevel00() {
            assertThat(LiveCardConfig.of(Map.of("securityLevels", "01, 03 ,00")).securityLevels())
                    .containsExactly(0x01, 0x03, 0x00);
            assertThat(LiveCardConfig.of(Map.of("securityLevels", "03")).describe()).contains("securityLevels=03");
        }

        @Test
        void javaCardVersionAcceptsDottedAndEnumNames() {
            assertThat(LiveCardConfig.of(Map.of("javaCardVersion", "3.0.5")).javaCardVersion())
                    .isEqualTo(JavaCardVersion.V3_0_5);
            assertThat(LiveCardConfig.of(Map.of("javaCardVersion", "V3_1_0")).javaCardVersion())
                    .isEqualTo(JavaCardVersion.V3_1_0);
        }

        /** The explicit opt-out of off-card verification (JCVM 3.1 §1.3 requires it before loading). */
        @Test
        void verifierSdkNoneAllowsUnverifiedCapFiles() {
            LiveCardConfig none = LiveCardConfig.of(Map.of("verifierSdk", "None"));

            assertThat(none.verifierSdk()).isNull();
            assertThat(none.allowUnverifiedCaps()).isTrue();
            assertThat(none.describe()).contains("verifierSdk=none (CAP files are loaded unverified)");
            assertThat(LiveCardConfig.of(Map.of()).allowUnverifiedCaps()).as("default").isFalse();
            assertThat(LiveCardConfig.of(Map.of()).describe()).contains("verifierSdk=(not set: deployments are"
                    + " refused)");
        }

        @Test
        void verifierSdkMustBeADirectory() {
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of("verifierSdk", tmp.resolve("missing").toString())))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("not a directory");
            assertThat(LiveCardConfig.of(Map.of("verifierSdk", tmp.toString())).verifierSdk()).isEqualTo(tmp);
        }

        @Test
        void aidsAreBuiltUnderThePrefix() {
            LiveCardConfig config = LiveCardConfig.of(Map.of("aidPrefix", "F0010203"));

            assertThat(config.aid("0101").toHex()).isEqualTo("F00102030101");
            assertThat(config.aidPrefixBytes()).containsExactly(0xF0, 0x01, 0x02, 0x03);
        }

        /**
         * Leftover removal deletes everything under the prefix, so a prefix outside the proprietary category ('F',
         * ISO/IEC 7816-5) is refused unless the registered RID is named explicitly.
         */
        @ParameterizedTest(name = "aidPrefix={0}")
        @CsvSource({"D276000124", "A000000308", "A000000396", "A0000003", "A0000005"})
        void prefixUnderARegisteredRidNeedsTheRidNamed(String prefix) {
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of("aidPrefix", prefix)))
                    .isInstanceOf(LiveCardException.class)
                    .hasMessageContaining("from argument")
                    .hasMessageContaining("registeredRid");
        }

        @Test
        void namedRegisteredRidAllowsPrefixesUnderIt() {
            LiveCardConfig config = LiveCardConfig.of(Map.of("aidPrefix", "a00000030801", "registeredRid",
                    "A000000308"));

            assertThat(config.aidPrefix()).isEqualTo("A00000030801");
            assertThat(config.registeredRid()).isEqualTo("A000000308");
            assertThat(config.describe()).contains("aidPrefix=A00000030801", "registeredRid=A000000308");
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of("aidPrefix", "A00000000301", "registeredRid",
                    "A000000003"))).isInstanceOf(LiveCardException.class).hasMessageContaining("reserved RID");
            assertThatThrownBy(() -> LiveCardConfig.of(Map.of("registeredRid", "A000000308")))
                    .isInstanceOf(LiveCardException.class).hasMessageContaining("does not start with");
        }
    }

    /** The public record constructor holds the same rules as the settings parser (no way around them). */
    @Nested
    class RecordConstructor {

        private LiveCardConfig create(String prefix, String registeredRid, List<Integer> levels, int maxAuthFailures) {
            return new LiveCardConfig(false, null, CardKeys.testKeys(), 0, 0x01, levels, prefix, registeredRid,
                    JavaCardVersion.V3_0_4, "A000000151000000", null, false, tmp.resolve("transcripts"),
                    maxAuthFailures);
        }

        @Test
        void verifierAndItsOptOutExcludeEachOther() {
            assertThatThrownBy(() -> new LiveCardConfig(false, null, CardKeys.testKeys(), 0, 0x01, List.of(0x01),
                    "F04A4358", null, JavaCardVersion.V3_0_4, "A000000151000000", tmp, true, tmp, 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("verifierSdk");
        }

        @ParameterizedTest(name = "aidPrefix={0}")
        @CsvSource({"A0000001515350", "A000000062", "A000000003", "D276000124", "F04A", "F04A4358ZZ"})
        void prefixIsValidated(String prefix) {
            assertThatThrownBy(() -> create(prefix, null, List.of(0x01), 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void otherValuesAreValidated() {
            assertThatThrownBy(() -> create("F04A4358", null, List.of(0x02), 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("security level");
            assertThatThrownBy(() -> create("F04A4358", null, List.of(), 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("security level");
            assertThatThrownBy(() -> create("F04A4358", null, List.of(0x01), 4))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAuthFailures");
            assertThatThrownBy(() -> create("F04A4358", "A000000308", List.of(0x01), 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("registeredRid");
        }

        @Test
        void validValuesAreNormalized() {
            LiveCardConfig config = create("f04a4358", null, List.of(0x01, 0x03), 1);

            assertThat(config.aidPrefix()).isEqualTo("F04A4358");
            assertThat(config.registeredRid()).isNull();
            assertThat(config.describe()).as("the same as from the settings sources").isEqualTo(LiveCardConfig.of(
                    Map.of("transcriptDir", tmp.resolve("transcripts").toString(), "securityLevels", "01,03")).describe());
        }
    }

    /** The off-card verifier kit is found in build/oracle-sdks of the project when verifierSdk is not set. */
    @Nested
    class VerifierKitLookup {

        private LiveCardConfig loadIn(Path root, Map<String, String> properties) {
            Map<String, String> all = new java.util.HashMap<>(properties);
            all.put("maven.multiModuleProjectDirectory", root.toString());
            return LiveCardConfig.load(new ConfigSources(all, Map.of(), List.of()));
        }

        private Path kit(Path root, String name) throws IOException {
            return Files.createDirectories(root.resolve("build/oracle-sdks").resolve(name).resolve("lib")).getParent();
        }

        @Test
        void theKitOfTheConversionTargetIsTaken() throws IOException {
            Path jc304 = kit(tmp, "jc304_kit");
            Path jc305 = kit(tmp, "jc305u3_kit");

            assertThat(loadIn(tmp, Map.of()).verifierSdk()).isEqualTo(jc304.toAbsolutePath().normalize());
            assertThat(loadIn(tmp, Map.of("jcx.livecard.javaCardVersion", "3.0.5")).verifierSdk())
                    .isEqualTo(jc305.toAbsolutePath().normalize());
        }

        @Test
        void anExplicitSettingWins() throws IOException {
            kit(tmp, "jc304_kit");
            Path other = Files.createDirectories(tmp.resolve("other-kit"));

            assertThat(loadIn(tmp, Map.of("jcx.livecard.verifierSdk", other.toString())).verifierSdk())
                    .isEqualTo(other.toAbsolutePath().normalize());
            LiveCardConfig none = loadIn(tmp, Map.of("jcx.livecard.verifierSdk", "none"));
            assertThat(none.verifierSdk()).isNull();
            assertThat(none.allowUnverifiedCaps()).isTrue();
        }

        @Test
        void withoutAKitNothingIsSet() throws IOException {
            kit(tmp, "jc305u3_kit");

            assertThat(loadIn(tmp, Map.of()).verifierSdk()).as("no jc304_kit for the default 3.0.4").isNull();
            assertThat(loadIn(tmp, Map.of("jcx.livecard.javaCardVersion", "2.2.2")).verifierSdk()).isNull();
        }
    }
}
