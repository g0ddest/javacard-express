package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the check looks at: the package the conversion would convert, in the compilation of the main sources, with the
 * whole package even when a build compiles only some of its classes.
 */
class CheckScopeTest {

    private static final String APPLET = """
            package p;

            import javacard.framework.APDU;
            import javacard.framework.Applet;

            public class Wallet extends Applet {
                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new Wallet().register();
                }

                public void process(APDU apdu) {
                }
            }
            """;
    private static final String HOST = """
            package host;

            public class Tool {
                long started = System.currentTimeMillis();
            }
            """;

    @TempDir
    Path project;

    @Test
    void hostCodeInAnotherPackageThanTheAppletIsNotChecked_jcvm31_4_1_2() {
        Result result = Javac.compile(project, Map.of("p/Wallet.java", APPLET, "host/Tool.java", HOST));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void theConfiguredPackageIsCheckedWhicheverItIs() {
        Result result = Javac.compile(project, Map.of("p/Wallet.java", APPLET, "host/Tool.java", HOST),
                "-Ajavacard.packageName=host");

        assertThat(result.errors()).isNotEmpty().allMatch(e -> e.startsWith("Tool.java:4:"));
    }

    @Test
    void theOnePackageOfALibraryIsChecked() {
        Result result = Javac.compile(project, Map.of("host/Tool.java", HOST));

        assertThat(result.errors()).isNotEmpty().allMatch(e -> e.startsWith("Tool.java:4:"));
    }

    @Test
    void aTestCompilationIsNotChecked() {
        Result result = Javac.compileInto(project.resolve("target/test-classes"), project,
                Map.of("host/Tool.java", HOST));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void checkFalseTurnsTheCheckOff() {
        Result result = Javac.compile(project, Map.of("host/Tool.java", HOST), "-Ajavacard.check=false");

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void anIncrementalCompilationSeesTheOtherClassesOfThePackage_jcvm31_2_2_1_1_6() {
        Javac.compile(project, Map.of("p/Secret.java", """
                package p;

                class Secret {
                }
                """));

        // only Api.java is compiled now; Secret.class is in the class output from before
        Result result = Javac.compile(project, Map.of("p/Api.java", """
                package p;

                public class Api {
                    public Secret secret() {
                        return null;
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Api.java:4:")
                .contains("package-visible"));
    }

    @Test
    void aProblemOfAClassCompiledBeforeDoesNotHideTheCallsOfTheClassCompiledNow_jcvm31_4_3_5() {
        Javac.compile(project, Map.of("p/Old.java", """
                package p;

                class Old {
                    long total;
                }
                """), "-Ajavacard.check=false");

        // only New.java is compiled now; Old.class, with its long field, is in the class output from before
        Result result = Javac.compile(project, Map.of("p/New.java", """
                package p;

                class New {
                    void clear(byte[] entries) {
                        java.util.Arrays.fill(entries, (byte) 0);
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("New.java:5:")
                .contains("java.util"));
        assertThat(result.diagnostics()).filteredOn(d -> d.getKind() == javax.tools.Diagnostic.Kind.NOTE)
                .singleElement().satisfies(note -> assertThat(note.getMessage(java.util.Locale.ROOT))
                        .isEqualTo("Java Card: the conversion also reports problems in classes this compilation"
                                + " did not compile: p.Old"));
    }

    @Test
    void withoutAClassOutputDirectoryTheCheckSaysThatItDidNotRun() {
        Result result = Javac.compileWithoutClassOutput(project, Map.of("host/Tool.java", HOST));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(note -> {
            assertThat(note.getKind()).isEqualTo(javax.tools.Diagnostic.Kind.NOTE);
            assertThat(note.getMessage(java.util.Locale.ROOT)).startsWith("Java Card: the check did not run: ");
        });
        assertThat(result.hasClassFile("host.Tool")).isTrue();
    }

    @Test
    void theClassFilesOfASourceWithErrorsAreRemoved() {
        Result result = Javac.compile(project, Map.of("p/Wallet.java", APPLET, "p/Ledger.java", """
                package p;

                public class Ledger {
                    long total;

                    static class Entry {
                    }
                }
                """));

        assertThat(result.success()).isFalse();
        assertThat(result.hasClassFile("p.Wallet")).isTrue();
        assertThat(result.hasClassFile("p.Ledger")).isFalse();
        assertThat(result.hasClassFile("p.Ledger$Entry")).isFalse();
    }

    @Test
    void commandLineJavacPrintsTheErrorWithFileAndLine() {
        Map.Entry<Integer, String> run = Javac.commandLine(project, Map.of("p/Ledger.java", """
                package p;

                public class Ledger {
                    long total;
                }
                """));

        assertThat(run.getKey()).isEqualTo(1);
        assertThat(run.getValue()).contains("Ledger.java:4: error: Java Card: long type not supported in JavaCard");
    }
}
