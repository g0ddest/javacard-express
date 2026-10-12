package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The export files a build supplies (JCVM 3.1 §4.3.3, §5.2) as the check gets them at compile time, before the build
 * converts anything: one that is not there yet is left to the conversion, and ones that the conversion would refuse
 * are an error of the package, as in the conversion.
 */
class SuppliedExportFilesTest {

    private static final Map<String, String> ARRAYS = Map.of("p/Clear.java", """
            package p;

            public class Clear {
                void clear(byte[] buffer) {
                    java.util.Arrays.fill(buffer, (byte) 0);
                }
            }
            """);

    @TempDir
    Path project;

    /**
     * An export file another module writes when it converts (process-classes) is not there when this module
     * compiles; nor is a path with a Maven expression that is only resolved when the conversion runs. The link check
     * of the package is then left to the conversion, which names what is still missing; the language subset is
     * checked as always.
     */
    @Test
    void anExportEntryThatIsNotThereYetLeavesTheLinkCheckToTheConversion_jcvm31_4_3_3() {
        Path later = project.resolve("lib/target/lib.exp");

        Result withoutIt = Javac.compile(project.resolve("a"), ARRAYS, "-Ajavacard.exportFiles=" + later);
        Result plain = Javac.compile(project.resolve("b"), ARRAYS);

        assertThat(withoutIt.success()).isTrue();
        assertThat(withoutIt.diagnostics()).singleElement().satisfies(note -> {
            assertThat(note.getKind()).isEqualTo(Diagnostic.Kind.NOTE);
            assertThat(note.getMessage(Locale.ROOT)).startsWith("Java Card: ").contains(later.toString())
                    .contains("left to the conversion");
        });
        assertThat(plain.errors()).singleElement().satisfies(e -> assertThat(e).contains("java.util"));
    }

    @Test
    void theSubsetIsCheckedWhileAnExportEntryIsNotThere_jcvm31_2_2_1_3() {
        Result result = Javac.compile(project, Map.of("p/Ledger.java", """
                package p;

                public class Ledger {
                    long total;
                }
                """), "-Ajavacard.exportPath=" + project.resolve("${session.executionRootDirectory}/ep"));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Ledger.java:4:"));
    }

    /**
     * Two export files of one package: the conversion refuses them (JCVM 3.1 §4.3.3), and so does the check, for the
     * package; the class files of the package go, so that an incremental build reports it again.
     */
    @Test
    void exportFilesTheConversionRefusesAreAnErrorAndLeaveNoClassFiles_jcvm31_4_3_3() throws IOException {
        Path exports = Files.createDirectories(project.resolve("exp"));
        Path first = Files.write(exports.resolve("gp-1.0.exp"), GlobalPlatformApi.exportFileBytes("1.0",
                JavaCardVersion.V3_0_5));
        Path second = Files.write(exports.resolve("gp-1.5.exp"), GlobalPlatformApi.exportFileBytes("1.5",
                JavaCardVersion.V3_0_5));

        Result result = Javac.compile(project, Map.of("p/Registry.java", """
                package p;

                import org.globalplatform.GPSystem;

                public class Registry {
                    byte state() {
                        return GPSystem.getCardContentState();
                    }
                }
                """), "-Ajavacard.exportFiles=" + first + File.pathSeparator + second);

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).contains("package p")
                .contains("org.globalplatform"));
        assertThat(result.hasClassFile("p.Registry")).isFalse();
    }
}
