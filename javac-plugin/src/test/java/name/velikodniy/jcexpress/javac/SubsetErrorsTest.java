package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Code outside the Java Card language subset (JCVM 3.1 §2.2) is a compile error at its line, with the converter's
 * message; code inside it compiles without a diagnostic.
 */
class SubsetErrorsTest {

    private static final String APPLET = "p/Wallet.java";

    @TempDir
    Path project;

    @Test
    void anAppletInsideTheSubsetCompilesWithoutDiagnostics() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                import javacard.framework.APDU;
                import javacard.framework.Applet;

                public class Wallet extends Applet {
                    private short balance;

                    public static void install(byte[] bArray, short bOffset, byte bLength) {
                        new Wallet().register();
                    }

                    public void process(APDU apdu) {
                        balance++;
                    }
                }
                """));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.hasClassFile("p.Wallet")).isTrue();
    }

    @Test
    void longFieldIsAnErrorAtTheField_jcvm31_2_2_1_3() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    private long balance;
                }
                """));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).containsExactly(
                "Wallet.java:4:18: Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)");
    }

    @Test
    void floatDoubleAndCharInCodeAreErrorsAtTheirLines_jcvm31_2_2_1_3() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    short work(short s) {
                        float f = s;
                        double d = s;
                        char c = (char) s;
                        return (short) (f + d + c);
                    }
                }
                """));

        assertThat(result.errors()).anyMatch(e -> e.startsWith("Wallet.java:5:") && e.contains("float"))
                .anyMatch(e -> e.startsWith("Wallet.java:6:") && e.contains("double"))
                .anyMatch(e -> e.startsWith("Wallet.java:7:") && e.contains("char"))
                .allMatch(e -> e.contains("Java Card: "));
    }

    @Test
    void stringLiteralIsAnErrorAtItsLine_jcvm31_2_2_1_4() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    Object name() {
                        return "wallet";
                    }
                }
                """));

        assertThat(result.errors()).containsExactly(
                "Wallet.java:5:9: Java Card: String constants are not supported (JCVM 3.1 §2.2.1.4)");
    }

    @Test
    void synchronizedIsAnErrorForTheMethodAndForTheBlock_jcvm31_2_2_1_2() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    synchronized void pay() {
                    }

                    void refund() {
                        synchronized (this) {
                            pay();
                        }
                    }
                }
                """));

        assertThat(result.errors()).anyMatch(e -> e.startsWith("Wallet.java:4:") && e.contains("synchronized"))
                .anyMatch(e -> e.startsWith("Wallet.java:8:") && e.contains("synchronized"));
    }

    @Test
    void lambdaIsAnErrorAtItsLine_jcvm31_2_2() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    interface Rule {
                        short apply(short amount);
                    }

                    Rule rule() {
                        return amount -> amount;
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Wallet.java:9:").contains("invokedynamic not supported"));
    }

    @Test
    void enumIsAnErrorAtItsDeclaration_jcvm31_2_2_1_1_7() {
        Result result = Javac.compile(project, Map.of("p/State.java", """
                package p;

                public enum State {
                    OPEN, CLOSED
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e)
                .startsWith("State.java:3:").contains("enum types are not supported"));
    }

    @Test
    void multiDimensionalArrayIsAnError_jcvm31_2_2_1_3() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    byte[][] grid() {
                        return new byte[2][2];
                    }
                }
                """));

        assertThat(result.errors()).isNotEmpty().allMatch(e -> e.contains("more than one dimension"));
    }

    @Test
    void intNeedsIntSupport_jcvm31_2_2_3_1() {
        Map<String, String> sources = Map.of(APPLET, """
                package p;

                public class Wallet {
                    private int total;
                }
                """);

        assertThat(Javac.compile(project.resolve("a"), sources).errors()).singleElement()
                .satisfies(e -> assertThat(e).startsWith("Wallet.java:4:").contains("needs int support"));
        assertThat(Javac.compile(project.resolve("b"), sources, "-Ajavacard.supportInt32=true").success()).isTrue();
    }

    @Test
    void interfaceMethodWithABodyIsAnError_jcvm31_6_10() {
        Result result = Javac.compile(project, Map.of("p/Rule.java", """
                package p;

                public interface Rule {
                    default short limit() {
                        return 0;
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e)
                .startsWith("Rule.java:4:").contains("interface method with a body"));
    }

    @Test
    void staticFinalFieldThatIsNoConstantIsAnErrorAtItsAssignment_jcvm31_2_2_4_6() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    static final short LIMIT;

                    static {
                        LIMIT = limit();
                    }

                    static short limit() {
                        return 5;
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Wallet.java:7:"));
    }

    @Test
    void nestedAndAnonymousClassesAreReportedAtTheirOwnLines_jcvm31_2_2_1_3() {
        Result result = Javac.compile(project, Map.of(APPLET, """
                package p;

                public class Wallet {
                    static class Ledger {
                        long entries;
                    }

                    Object audit() {
                        return new Object() {
                            float rate = 1;
                        };
                    }
                }
                """));

        assertThat(result.errors()).hasSize(2)
                .anyMatch(e -> e.startsWith("Wallet.java:5:") && e.contains("long"))
                .anyMatch(e -> e.startsWith("Wallet.java:10:") && e.contains("float"));
    }
}
