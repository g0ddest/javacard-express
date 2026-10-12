package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The check never breaks javac: what it cannot read or where it cannot run, it says so in one warning or note, and
 * the compilation goes on as without it. The conversion still reports what the check could not.
 */
class RobustnessTest {

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

    @TempDir
    Path project;

    @Test
    void aFileOfThePackageThatIsNoClassFileIsAWarning_jvms_4_1() throws IOException {
        Files.write(Files.createDirectories(project.resolve("target/classes/p")).resolve("Junk.class"),
                "not a class file".getBytes());

        Result result = Javac.compile(project, Map.of("p/Wallet.java", APPLET));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(warning -> {
            assertThat(warning.getKind()).isEqualTo(Diagnostic.Kind.WARNING);
            assertThat(warning.getMessage(Locale.ROOT)).startsWith("Java Card: the check could not read p/Junk.class:");
        });
    }

    @Test
    void aClassFileOfALaterJavaVersionIsAWarning_jvms_4_1() throws IOException {
        Javac.compile(project.resolve("earlier"), Map.of("p/Old.java", """
                package p;

                class Old {
                }
                """));
        byte[] old = Files.readAllBytes(project.resolve("earlier/target/classes/p/Old.class"));
        // major version of the class file format of the next Java release (JVMS §4.1: 69 is Java 25)
        old[7] = (byte) (Runtime.version().feature() + 45);
        Files.write(Files.createDirectories(project.resolve("target/classes/p")).resolve("Old.class"), old);

        Result result = Javac.compile(project, Map.of("p/Wallet.java", APPLET));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(warning -> {
            assertThat(warning.getKind()).isEqualTo(Diagnostic.Kind.WARNING);
            assertThat(warning.getMessage(Locale.ROOT)).startsWith("Java Card: the check could not read p/Old.class:");
        });
    }

    @Test
    void classFilesKeptInMemoryLetTheCheckSayThatItDidNotRun() {
        Result result = Javac.compileWithFileManager(project, Map.of("p/Wallet.java", APPLET), InMemory::new);

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(note -> {
            assertThat(note.getKind()).isEqualTo(Diagnostic.Kind.NOTE);
            assertThat(note.getMessage(Locale.ROOT)).startsWith("Java Card: the check did not run: ");
        });
    }

    @Test
    void checkFalseDoesNotTouchTheFileManager() {
        Result off = Javac.compileWithFileManager(project.resolve("off"), Map.of("p/Wallet.java", APPLET),
                NoResources::new, "-Ajavacard.check=false");
        Result on = Javac.compileWithFileManager(project.resolve("on"), Map.of("p/Wallet.java", APPLET),
                NoResources::new);

        assertThat(off.success()).isTrue();
        assertThat(off.diagnostics()).isEmpty();
        assertThat(on.success()).isTrue();
        assertThat(on.diagnostics()).singleElement().satisfies(note ->
                assertThat(note.getMessage(Locale.ROOT)).startsWith("Java Card: the check did not run: "));
    }

    /**
     * A processor path without the converter, written by hand: the check cannot load the converter's rules, says so
     * in a warning, and javac compiles as without it.
     */
    @Test
    void withoutTheConverterTheCheckSaysThatItDidNotRun() {
        Map.Entry<Integer, String> javac = Javac.forked(project, Map.of("p/Wallet.java", APPLET),
                Javac.checkWithoutConverter());

        assertThat(javac.getKey()).as(javac.getValue()).isZero();
        assertThat(javac.getValue()).contains("warning: Java Card: the check did not run: NoClassDefFoundError");
        assertThat(project.resolve("target/classes/p/Wallet.class")).isRegularFile();
    }

    /** With the check off nothing of the converter is needed: no diagnostic at all, even without its jar. */
    @Test
    void checkFalseWithoutTheConverterIsSilent() {
        Map.Entry<Integer, String> javac = Javac.forked(project, Map.of("p/Wallet.java", APPLET),
                Javac.checkWithoutConverter(), "-Ajavacard.check=false");

        assertThat(javac.getKey()).as(javac.getValue()).isZero();
        assertThat(javac.getValue()).doesNotContain("Java Card");
        assertThat(project.resolve("target/classes/p/Wallet.class")).isRegularFile();
    }

    /** A file manager that keeps the class files in memory, as tools that compile in memory have. */
    private static final class InMemory extends ForwardingJavaFileManager<JavaFileManager> {

        InMemory(JavaFileManager files) {
            super(files);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("memory:///" + className.replace('.', '/') + kind.extension),
                    kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream();
                }
            };
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName, String relativeName,
                                           FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("memory:///" + relativeName), JavaFileObject.Kind.OTHER) {
            };
        }
    }

    /** A file manager that cannot give the filer any resource. */
    private static final class NoResources extends ForwardingJavaFileManager<JavaFileManager> {

        NoResources(JavaFileManager files) {
            super(files);
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName, String relativeName,
                                           FileObject sibling) {
            throw new UnsupportedOperationException("no resources here");
        }
    }
}
