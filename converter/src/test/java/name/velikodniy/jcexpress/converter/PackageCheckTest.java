package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.PackageCheck.Problem;
import name.velikodniy.jcexpress.converter.PackageCheck.Settings;
import name.velikodniy.jcexpress.converter.exp.ExportFileWriter;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.converter.resolve.ImportLoader;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The checks of the scan stage without a conversion: every problem the conversion would report, each with the class,
 * member and source line it comes from, so that a compiler plugin can report it at its place.
 */
class PackageCheckTest {

    private static final Settings DEFAULT = new Settings(JavaCardVersion.defaultVersion(), false, null);

    @TempDir
    Path classes;

    @Test
    void anAppletInsideTheSubsetHasNoProblems_jcvm31_2_2() throws Exception {
        compile("p.Ok", """
                package p;
                import javacard.framework.*;
                public class Ok extends Applet {
                    private short counter;
                    public static void install(byte[] b, short o, byte l) { new Ok().register(); }
                    public void process(APDU apdu) { counter++; }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).isEmpty();
    }

    @Test
    void everySubsetViolationComesWithItsMemberAndLine_jcvm31_2_2_1_3() throws Exception {
        compile("p.Bad", """
                package p;
                public class Bad {
                    long big;
                    void work() {
                        float f = 1.5f;
                        Object o = "text";
                    }
                }
                """);

        List<Problem> problems = PackageCheck.check("p", classFiles("p"), DEFAULT);

        assertThat(problems).extracting(Problem::className, Problem::member, Problem::line, Problem::declaration)
                .contains(tuple("p/Bad", "field big", -1, true), tuple("p/Bad", "work()V", 5, false),
                        tuple("p/Bad", "work()V", 6, false));
        assertThat(problems).filteredOn(p -> p.line() == 6).extracting(Problem::message)
                .anySatisfy(message -> assertThat(message).contains("String constants are not supported"));
    }

    @Test
    void theJavaSeLibraryIsALinkProblemAtTheLineOfTheCall_jcvm31_2_2_1_4() throws Exception {
        compile("p.Fill", """
                package p;
                public class Fill {
                    void work(byte[] buffer) {
                        java.util.Arrays.fill(buffer, (byte) 0);
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement().satisfies(problem -> {
            assertThat(problem.member()).isEqualTo("work([B)V");
            assertThat(problem.line()).isEqualTo(4);
            assertThat(problem.declaration()).isFalse();
            assertThat(problem.message()).contains("java.util", "not part of the Java Card platform");
        });
    }

    @Test
    void linkProblemsWaitForTheSubset_asInTheConversion() throws Exception {
        compile("p.Both", """
                package p;
                public class Both {
                    long big;
                    void work(byte[] buffer) {
                        java.util.Arrays.fill(buffer, (byte) 0);
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement()
                .extracting(Problem::member).isEqualTo("field big");
    }

    @Test
    void theApiOfALaterPlatformIsALinkProblem_jcvm31_4_5_2() throws Exception {
        compile("p.Later", """
                package p;
                import javacard.framework.Util;
                public class Later {
                    void clear(byte[] buffer) {
                        Util.arrayFill(buffer, (short) 0, (short) 4, (byte) 0);
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4, false, null)))
                .singleElement().satisfies(problem -> {
                    assertThat(problem.line()).isEqualTo(5);
                    assertThat(problem.message()).contains("arrayFill", "Java Card 3.0.4",
                            "introduced in Java Card 3.0.5");
                });
        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).isEmpty();
    }

    @Test
    void referencesIntoOtherPackagesAreLeftToTheConversion_jcvm31_4_3_3() throws Exception {
        compile(Map.of("lib.Helper", """
                package lib;
                public class Helper {
                    public static short twice(short s) { return (short) (s * 2); }
                }
                """, "p.User", """
                package p;
                public class User {
                    short use() { return lib.Helper.twice((short) 2); }
                }
                """));

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).isEmpty();
    }

    @Test
    void intNeedsIntSupport_jcvm31_2_2_3_1() throws Exception {
        compile("p.Wide", """
                package p;
                public class Wide {
                    int total;
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement()
                .extracting(Problem::member).isEqualTo("field total");
        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.defaultVersion(), true,
                null))).isEmpty();
    }

    @Test
    void aFixedGlobalPlatformApiVersionIsTheOneLinkedAgainst_jcvm31_4_5_2() throws Exception {
        compile("p.Registry", """
                package p;
                import javacard.framework.AID;
                import org.globalplatform.GPSystem;
                public class Registry {
                    Object entry(AID aid) {
                        return GPSystem.getRegistryEntry(aid);
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4, false, "1.0")))
                .singleElement().satisfies(problem -> {
                    assertThat(problem.line()).isEqualTo(6);
                    assertThat(problem.message()).contains("getRegistryEntry", "introduced in version 1.1");
                });
        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4, false, null)))
                .isEmpty();
    }

    @Test
    void settingsNameOnlyVersionsOfTheBuiltInData() {
        assertThatThrownBy(() -> new Settings(JavaCardVersion.V3_0_5, false, "0.9"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Settings(null, false, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theDefaultPlatformIsTheOneOfTheConverter() {
        assertThat(JavaCardVersion.defaultVersion()).isEqualTo(JavaCardVersion.V3_0_5);
        assertThat(JavaCardVersion.defaultVersion().specVersion()).isEqualTo(JavaCardVersion.DEFAULT_VERSION);
    }

    @Test
    void aMethodInheritedThroughASubInterfaceFromAnotherPackageIsLeftToTheConversion_jcvm31_4_3_3()
            throws Exception {
        compile(Map.of("lib.Wallet", """
                package lib;
                public interface Wallet extends javacard.framework.Shareable {
                    void debit(short amount);
                }
                """, "app.BonusWallet", """
                package app;
                public interface BonusWallet extends lib.Wallet {
                    void bonus();
                }
                """, "app.Client", """
                package app;
                import javacard.framework.*;
                public class Client extends Applet {
                    public static void install(byte[] b, short o, byte l) { new Client().register(); }
                    public void process(APDU apdu) {
                        BonusWallet w = (BonusWallet) JCSystem.getAppletShareableInterfaceObject(null, (byte) 0);
                        w.debit((short) 5);
                    }
                }
                """));

        assertThat(PackageCheck.check("app", classFiles("app"), DEFAULT)).isEmpty();
    }

    @Test
    void aMethodAnAbstractClassInheritsFromAnotherPackageIsLeftToTheConversion_jcvm31_4_3_5() throws Exception {
        compile(Map.of("lib.Wallet", """
                package lib;
                public interface Wallet extends javacard.framework.Shareable {
                    void debit(short amount);
                }
                """, "app.Base", """
                package app;
                import javacard.framework.*;
                public abstract class Base extends Applet implements lib.Wallet {
                    public void process(APDU apdu) {
                        debit((short) 1);
                    }
                }
                """, "app.Impl", """
                package app;
                public class Impl extends Base {
                    private short balance;
                    public static void install(byte[] b, short o, byte l) { new Impl().register(); }
                    public void debit(short amount) { balance -= amount; }
                }
                """));

        assertThat(PackageCheck.check("app", classFiles("app"), DEFAULT)).isEmpty();
    }

    @Test
    void anExportFileTheBuildSuppliesReplacesTheBuiltInData_jcvm31_4_3_3(@TempDir Path exports) throws Exception {
        compile("p.Later", """
                package p;
                import javacard.framework.Util;
                public class Later {
                    void clear(byte[] buffer) {
                        Util.arrayFill(buffer, (short) 0, (short) 4, (byte) 0);
                    }
                }
                """);
        Path framework305 = Files.write(exports.resolve("framework.exp"), ExportFileWriter.write(
                BuiltinExports.getExport("javacard/framework", JavaCardVersion.V3_0_5)));

        // a newer API's export file supplied for an older target: the conversion links against it, so must the check
        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4, false, null,
                List.of(framework305), List.of()))).isEmpty();
    }

    @Test
    void anExportFileOnTheExportPathReplacesTheBuiltInData_jcvm31_5_2(@TempDir Path exports) throws Exception {
        compile("p.Registry", """
                package p;
                import javacard.framework.AID;
                import org.globalplatform.GPSystem;
                public class Registry {
                    Object entry(AID aid) {
                        return GPSystem.getRegistryEntry(aid);
                    }
                }
                """);
        Path file = exports.resolve(ImportLoader.exportFileEntry(GlobalPlatformApi.PACKAGE_NAME));
        Files.createDirectories(file.getParent());
        Files.write(file, GlobalPlatformApi.exportFileBytes("1.0", JavaCardVersion.V3_0_4));

        // the built-in data would link version 1.1; the supplied file is version 1.0, which lacks the method
        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4, false, null)))
                .isEmpty();
        assertThat(PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4, false, null,
                List.of(), List.of(exports)))).singleElement().satisfies(problem -> {
                    assertThat(problem.line()).isEqualTo(6);
                    assertThat(problem.message()).contains("getRegistryEntry", "org.globalplatform 1.0");
                });
    }

    @Test
    void anUnreadableExportFileIsReportedAsByTheConversion_jcvm31_5_1(@TempDir Path exports) throws Exception {
        compile("p.Plain", """
                package p;
                public class Plain {
                    short work(short s) { return s; }
                }
                """);
        Path broken = Files.write(exports.resolve("broken.exp"), new byte[] {1, 2, 3});

        assertThatThrownBy(() -> PackageCheck.check("p", classFiles("p"), new Settings(JavaCardVersion.V3_0_4,
                false, null, List.of(broken), List.of()))).isInstanceOf(ConverterException.class)
                .hasMessageContaining("broken.exp");
    }

    @Test
    void aCatchClauseIsAtTheLineOfItsHandler_jcvm31_2_2_1_1_11() throws Exception {
        compile("p.Guarded", """
                package p;
                public class Guarded {
                    short work(short s) {
                        short r = 0;
                        try {
                            r = (short) (s / 2);
                        } catch (AssertionError e) {
                            r = 1;
                        }
                        return r;
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement().satisfies(problem -> {
            assertThat(problem.message()).startsWith("catch clause: java/lang/AssertionError");
            assertThat(problem.line()).isEqualTo(7);
            assertThat(problem.declaration()).isFalse();
        });
    }

    @Test
    void aLinkProblemOfACatchClauseIsAtTheLineOfItsHandler_jcvm31_4_3_5() throws Exception {
        compile("p.Parse", """
                package p;
                public class Parse {
                    short work(short s) {
                        short r = 0;
                        try {
                            r = (short) (s / 2);
                        } catch (NumberFormatException e) {
                            r = 1;
                        }
                        return r;
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement().satisfies(problem -> {
            assertThat(problem.message()).contains("java.lang.NumberFormatException");
            assertThat(problem.line()).isEqualTo(7);
            assertThat(problem.declaration()).isFalse();
        });
    }

    @Test
    void aProblemTheConversionReportsOnceIsOneProblem_jcvm31_4_3_5() throws Exception {
        // new and the constructor call are two references with one message at one place
        compile("p.Twice", """
                package p;
                public class Twice {
                    Object work() {
                        return new java.util.Random();
                    }
                }
                """);

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement()
                .extracting(Problem::line).isEqualTo(4);
    }

    @Test
    void anIncrementalCompilationLinksTheClassesItCompiled_jcvm31_4_3_5() throws Exception {
        compile(Map.of("p.Old", """
                package p;
                public class Old {
                    long big;
                }
                """, "p.New", """
                package p;
                public class New {
                    void work(byte[] buffer) {
                        java.util.Arrays.fill(buffer, (byte) 0);
                    }
                }
                """));

        // the conversion stops at Old's subset problem; a compilation of New alone still learns about its call
        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT, "p/New"::equals))
                .extracting(Problem::className, Problem::member)
                .containsExactlyInAnyOrder(tuple("p/Old", "field big"), tuple("p/New", "work([B)V"));
        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).extracting(Problem::className)
                .containsExactly("p/Old");
    }

    private void compile(String className, String source) {
        compile(Map.of(className, source));
    }

    private void compile(Map<String, String> sources) {
        JavaSources.compile(classes, sources);
    }

    private List<byte[]> classFiles(String packageName) throws Exception {
        List<byte[]> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(classes.resolve(packageName.replace('.', '/')))) {
            for (Path file : list.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                files.add(Files.readAllBytes(file));
            }
        }
        return files;
    }
}
