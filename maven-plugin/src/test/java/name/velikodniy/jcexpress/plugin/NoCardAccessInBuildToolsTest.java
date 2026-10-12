package name.velikodniy.jcexpress.plugin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the build tools away from cards: the Maven plugin, whose build extension runs inside every applet build, and
 * the javac check never reach a reader and never switch live-card mode on. Their compiled classes, main and test, do
 * not name {@code javax.smartcardio}, the PC/SC session or the live-card harness (not even in a descriptor), and their
 * main classes contain no {@code jcx.} setting at all; their tests set none of the live-mode properties.
 *
 * <p>livecard's {@code PcscAccessGuardTest} scans the modules the reactor builds before livecard. This module is built
 * after it (its integration tests need this build's livecard), so a clean build would leave it unscanned there: it
 * scans itself and the javac check here, and both must be built in this run.</p>
 */
class NoCardAccessInBuildToolsTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path ROOT = Path.of("..");
    private static final List<String> SCANNED = List.of("maven-plugin/target/classes",
            "maven-plugin/target/test-classes", "javac-plugin/target/classes", "javac-plugin/target/test-classes");
    /** Names a class of the build tools must not contain, as internal names or in descriptors. */
    private static final List<String> CARD_ACCESS = List.of("javax/smartcardio/", "name/velikodniy/jcexpress/pcsc/",
            "name/velikodniy/jcexpress/livecard/", "name/velikodniy/jcexpress/gp/RootReadmeSnippets");
    /** The properties that switch live-card mode on (livecard's LiveCardExtension and ConfigSources read them). */
    private static final Set<String> LIVE_MODE = Set.of("jcx.livecard.enabled", "jcx.livecard.allowCi", "jcx.backend");

    private static final String SELF = NoCardAccessInBuildToolsTest.class.getName().replace('.', '/');

    private static List<String> violations;
    private static int classes;

    @BeforeAll
    static void scan() throws IOException {
        violations = new ArrayList<>();
        classes = 0;
        for (String directory : SCANNED) {
            Path path = ROOT.resolve(directory);
            assertThat(path).as("%s: build the javac check and this module in the same run"
                    + " (./mvnw verify -pl maven-plugin -am)", directory).isDirectory();
            try (Stream<Path> files = Files.walk(path)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                    classes++;
                    violations.addAll(violations(ClassFile.of().parse(file), directory.endsWith("/classes")));
                }
            }
        }
        System.out.println("NoCardAccessInBuildToolsTest: scanned " + classes + " classes in " + SCANNED);
    }

    @Test
    void theBuildToolsNeitherReachACardNorSwitchLiveModeOn() {
        assertThat(classes).as("classes scanned (guards against scanning nothing)").isGreaterThan(50);
        assertThat(violations).as("tests against a real card belong in the livecard module (LIVE_CARD_TESTING.md)")
                .isEmpty();
    }

    /** Positive control: the scan sees a call to a reader, a descriptor with a card type and a live-mode setting. */
    @Test
    void theScanSeesCardAccessAndLiveModeSettings() {
        ClassDesc factory = ClassDesc.of("javax.smartcardio.TerminalFactory");
        byte[] probe = ClassFile.of().build(ClassDesc.of("probe.Probe"), type -> type
                .withMethodBody("open", MethodTypeDesc.of(factory), ClassFile.ACC_STATIC, code -> code
                        .ldc("jcx.backend").pop()
                        .invokestatic(factory, "getDefault", MethodTypeDesc.of(factory))
                        .areturn())
                .withMethodBody("card", MethodTypeDesc.of(ConstantDescs.CD_void,
                        ClassDesc.of("name.velikodniy.jcexpress.livecard.LiveCard")), ClassFile.ACC_STATIC,
                        code -> code.return_()));

        List<String> main = violations(ClassFile.of().parse(probe), true);
        List<String> test = violations(ClassFile.of().parse(probe), false);

        assertThat(main).anySatisfy(v -> assertThat(v).contains("javax/smartcardio/TerminalFactory"))
                .anySatisfy(v -> assertThat(v).contains("name/velikodniy/jcexpress/livecard/LiveCard"))
                .anySatisfy(v -> assertThat(v).contains("jcx.backend"));
        assertThat(test).anySatisfy(v -> assertThat(v).contains("jcx.backend"));
    }

    /**
     * What a class must not contain; a main class also no {@code jcx.} string at all. This test itself names what it
     * looks for, and is left out.
     */
    private static List<String> violations(ClassModel model, boolean main) {
        String type = model.thisClass().asInternalName();
        List<String> found = new ArrayList<>();
        if (type.equals(SELF) || type.startsWith(SELF + "$")) {
            return found;
        }
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof Utf8Entry text) {
                CARD_ACCESS.stream().filter(text.stringValue()::contains)
                        .forEach(name -> found.add(type + " names " + text.stringValue()));
            } else if (entry instanceof StringEntry constant) {
                String value = constant.stringValue();
                if (LIVE_MODE.contains(value) || main && value.startsWith("jcx.")) {
                    found.add(type + " has the string constant " + value);
                }
            }
        }
        return found;
    }
}
