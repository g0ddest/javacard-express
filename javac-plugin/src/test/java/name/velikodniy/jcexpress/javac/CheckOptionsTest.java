package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The processor options, named like the Maven plugin's properties, with the conversion's defaults. */
class CheckOptionsTest {

    @TempDir
    Path project;

    @Test
    void withoutOptionsTheCheckStandsForTheDefaultConversion() {
        CheckOptions options = CheckOptions.parse(Map.of());

        assertThat(options.enabled()).isTrue();
        assertThat(options.settings().version()).isEqualTo(JavaCardVersion.defaultVersion());
        assertThat(options.settings().supportInt32()).isFalse();
        assertThat(options.settings().globalPlatformApi()).isNull();
        assertThat(options.packageNames()).isEmpty();
    }

    @Test
    void optionsSetEverySetting() {
        CheckOptions options = CheckOptions.parse(Map.of(CheckOptions.VERSION, "3.0.4",
                CheckOptions.SUPPORT_INT32, "TRUE", CheckOptions.GLOBAL_PLATFORM_API, "1.5",
                CheckOptions.PACKAGE_NAME, "com.example.wallet, com/example/lib"));

        assertThat(options.settings().version()).isEqualTo(JavaCardVersion.V3_0_4);
        assertThat(options.settings().supportInt32()).isTrue();
        assertThat(options.settings().globalPlatformApi()).isEqualTo("1.5");
        assertThat(options.packageNames()).containsExactly("com.example.wallet", "com.example.lib");
    }

    @Test
    void anUnresolvedMavenExpressionOrABlankValueIsNotSet() {
        CheckOptions options = CheckOptions.parse(Map.of(CheckOptions.VERSION, "${javacard.version}",
                CheckOptions.SUPPORT_INT32, " ", CheckOptions.CHECK, "${javacard.check}"));

        assertThat(options.enabled()).isTrue();
        assertThat(options.settings().version()).isEqualTo(JavaCardVersion.defaultVersion());
    }

    @Test
    void anInvalidValueNamesItsOption() {
        assertThatThrownBy(() -> CheckOptions.parse(Map.of(CheckOptions.VERSION, "3.0.9")))
                .hasMessageStartingWith("-Ajavacard.version=3.0.9: not a Java Card version").hasMessageContaining("3.0.4");
        assertThatThrownBy(() -> CheckOptions.parse(Map.of(CheckOptions.SUPPORT_INT32, "yes")))
                .hasMessage("-Ajavacard.supportInt32=yes: expected true or false");
        assertThatThrownBy(() -> CheckOptions.parse(Map.of(CheckOptions.GLOBAL_PLATFORM_API, "0.9")))
                .hasMessageStartingWith("-Ajavacard.globalPlatformApi=0.9: ");
        assertThatThrownBy(() -> CheckOptions.parse(Map.of(CheckOptions.PACKAGE_NAME, "com.example.1st")))
                .hasMessageStartingWith("-Ajavacard.packageName=com.example.1st: 'com.example.1st' is not a Java"
                        + " package name");
        assertThatThrownBy(() -> CheckOptions.parse(Map.of(CheckOptions.CHECK, "off")))
                .hasMessage("-Ajavacard.check=off: expected true or false");
    }

    @Test
    void exportFilesAndTheExportPathAreListsOfPaths_jcvm31_4_3_3() throws IOException {
        Path first = Files.createFile(project.resolve("first.exp"));
        Path second = Files.createFile(project.resolve("second.exp"));
        Path directory = Files.createDirectories(project.resolve("exports"));

        CheckOptions options = CheckOptions.parse(Map.of(CheckOptions.EXPORT_FILES,
                first + File.pathSeparator + second, CheckOptions.EXPORT_PATH, directory.toString()));

        assertThat(options.settings().exportFiles()).containsExactly(first, second);
        assertThat(options.settings().exportPath()).containsExactly(directory);
    }

    @Test
    void anExportEntryThatIsNotThereOrNotResolvedIsNotSupplied_jcvm31_4_3_3() throws IOException {
        Path present = Files.writeString(project.resolve("present.exp"), "");
        Path missing = project.resolve("missing.exp");
        String unresolved = project.resolve("${session.executionRootDirectory}/ep").toString();

        CheckOptions options = CheckOptions.parse(Map.of(CheckOptions.EXPORT_FILES,
                present + File.pathSeparator + missing, CheckOptions.EXPORT_PATH, unresolved));

        // another module may write them later (process-classes); the conversion reports what is still missing
        assertThat(options.settings().exportFiles()).containsExactly(present);
        assertThat(options.settings().exportPath()).isEmpty();
        assertThat(options.notSupplied()).containsExactly(missing.toString(), unresolved);
    }

    @Test
    void theCheckOffIgnoresTheOtherOptions() {
        assertThat(CheckOptions.parse(Map.of(CheckOptions.CHECK, "false", CheckOptions.VERSION, "9")).enabled())
                .isFalse();
    }

    @Test
    void javacReportsAnInvalidOptionAsOneErrorAndNoOtherProblem() {
        Result result = Javac.compile(project, Map.of("p/Ledger.java", """
                package p;

                public class Ledger {
                    long total;
                }
                """), "-Ajavacard.version=9");

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Java Card: -Ajavacard.version=9: not a Java Card version"));
    }

    @Test
    void javacKnowsEveryOptionOfTheCheck() {
        Result result = Javac.compile(project, Map.of("p/Ok.java", """
                package p;

                public class Ok {
                }
                """), "-Ajavacard.version=3.0.4", "-Ajavacard.supportInt32=false", "-Ajavacard.globalPlatformApi=1.0",
                "-Ajavacard.packageName=p", "-Ajavacard.check=true", "-Ajavacard.exportFiles=",
                "-Ajavacard.exportPath=");

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }
}
