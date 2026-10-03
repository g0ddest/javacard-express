package name.velikodniy.jcexpress.livecard.backend;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import name.velikodniy.jcexpress.livecard.LiveCardException;
import name.velikodniy.jcexpress.livecard.model.applet.ModelApplet;
import name.velikodniy.jcexpress.livecard.model.applet.OtherApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which classes the GlobalPlatform backends convert for a package, whatever the build's class path looks like: a jar
 * (a reactor build at {@code package} or later, a repository artifact) converts like a classes directory, a package
 * split over two entries (main and test sources) is read from both, and classes of the package that no applet needs
 * (JUnit test classes) are not converted. The CAP file holds every applet of the package (JCVM 3.1 §6.6), so any of
 * them can be installed once the package is loaded.
 */
class GpPackageLoadingTest {

    private static final String PACKAGE = ModelApplet.class.getPackageName();
    private static final String NOT_JAVA_CARD = PACKAGE + ".ModelAppletTest";
    private static final String NEEDY = PACKAGE + ".NeedyApplet";

    @TempDir
    Path work;

    private TestCard open(Class<? extends Applet> declared) {
        Map<String, String> settings = Map.of("jcx.livecard.transcriptDir", work.resolve("transcripts").toString());
        return new SimulatedGpBackend().open(new CardRequest(GpPackageLoadingTest.class,
                key -> Optional.ofNullable(settings.get(key)), List.of(declared)));
    }

    /** Installs an instance as the extension does: the run declares it and every applet declared before. */
    private static AppletDeclaration install(TestCard card, Class<? extends Applet> applet, String suffix,
                                             List<AppletDeclaration> declaredRun) {
        AppletDeclaration declaration = AppletDeclaration.of(card.aids(), applet,
                suffix == null ? null : card.aids().aid(suffix), null, Isolation.PER_TEST);
        List<AppletDeclaration> run = new ArrayList<>(declaredRun);
        run.add(declaration);
        card.install(declaration, run);
        return declaration;
    }

    private static String send(TestCard card, AppletDeclaration applet, int ins) {
        card.session().select(applet.instanceAid());
        return card.session().send(0x80, ins, 0, 0, null, 256).dataAsHex();
    }

    @Test
    void anAppletFromAJarIsConvertedLikeOneFromAClassesDirectory() throws IOException {
        Path classes = work.resolve("classes");
        ClassFixtures.copy(classes, ModelApplet.class, OtherApplet.class);
        Path jar = ClassFixtures.jar(work.resolve("applets-1.0.jar"), classes);

        try (URLClassLoader loader = ClassFixtures.childFirst(jar)) {
            Class<? extends Applet> model = ClassFixtures.applet(loader, ModelApplet.class);
            assertThat(model.getProtectionDomain().getCodeSource().getLocation().getPath()).endsWith(".jar");
            try (TestCard card = open(model)) {
                AppletDeclaration instance = install(card, model, null, List.of());

                assertThat(send(card, instance, 0x30)).isEqualTo("0001");
            }
        }
        assertThat(cardTranscript()).contains("convert " + PACKAGE + ": applets " + ModelApplet.class.getName() + ", "
                + OtherApplet.class.getName() + "; classes ").contains(jar.getFileName() + ")");
    }

    private String cardTranscript() throws IOException {
        return Files.readString(work.resolve("transcripts").resolve(GpPackageLoadingTest.class.getName())
                .resolve("card.txt"));
    }

    @Test
    void aStubInAnotherEntryOfASplitPackageIsFoundAndTestClassesAreNotConverted() throws IOException {
        Path main = work.resolve("classes");
        Path test = work.resolve("test-classes");
        ClassFixtures.copy(main, ModelApplet.class);
        ClassFixtures.copy(test, OtherApplet.class);
        ClassFixtures.write(test, NOT_JAVA_CARD, ClassFixtures.notJavaCard(NOT_JAVA_CARD));

        try (URLClassLoader loader = ClassFixtures.childFirst(test, main)) {
            Class<? extends Applet> model = ClassFixtures.applet(loader, ModelApplet.class);
            Class<? extends Applet> other = ClassFixtures.applet(loader, OtherApplet.class);
            try (TestCard card = open(model)) {
                AppletDeclaration first = install(card, model, null, List.of());
                AppletDeclaration second = install(card, other, "0301", List.of(first));

                assertThat(send(card, first, 0x30)).isEqualTo("0001");
                assertThat(send(card, second, 0x37)).isEqualTo("0F");
            }
        }
        assertThat(cardTranscript()).contains("applets " + ModelApplet.class.getName() + ", "
                + OtherApplet.class.getName()).doesNotContain(NOT_JAVA_CARD);
    }

    /** Fail closed: an applet that needs a class of its package outside the subset is not converted, nothing loaded. */
    @Test
    void anAppletThatNeedsAClassOutsideTheSubsetFailsTheConversion() throws IOException {
        Path classes = work.resolve("classes");
        ClassFixtures.write(classes, NEEDY, ClassFixtures.appletCalling(NEEDY, NOT_JAVA_CARD));
        ClassFixtures.write(classes, NOT_JAVA_CARD, ClassFixtures.notJavaCard(NOT_JAVA_CARD));

        try (URLClassLoader loader = ClassFixtures.childFirst(classes)) {
            Class<? extends Applet> needy = ClassFixtures.applet(loader, NEEDY);
            try (TestCard card = open(needy)) {
                assertThatThrownBy(() -> install(card, needy, null, List.of()))
                        .isInstanceOf(LiveCardException.class)
                        .hasMessageContaining("Conversion of " + PACKAGE + " failed; nothing was loaded")
                        .hasMessageContaining(NOT_JAVA_CARD.replace('.', '/'))
                        .hasMessageContaining("applets " + NEEDY)
                        .hasMessageContaining("classes " + NEEDY + ", " + NOT_JAVA_CARD);
            }
        }
    }

    /**
     * A classes directory or jar with a build descriptor of the Maven plugin contributes the applets the descriptor
     * names: those are loaded, and another Applet subclass of the entry is not an applet of the package.
     */
    @Test
    void theBuildDescriptorNamesTheAppletsOfItsEntry() throws IOException {
        Path classes = work.resolve("classes");
        ClassFixtures.copy(classes, ModelApplet.class, OtherApplet.class);
        Path descriptor = classes.resolve("META-INF/javacard/" + PACKAGE + ".properties");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, String.join("\n", "package=" + PACKAGE, "packageAid=A00000006277",
                "packageVersion=1.0", "javaCardVersion=3.0.4", "supportInt32=false",
                "applet." + ModelApplet.class.getName() + "=A0000000627701", "project=com.example:descriptor-test"));

        try (URLClassLoader loader = ClassFixtures.childFirst(classes)) {
            Class<? extends Applet> model = ClassFixtures.applet(loader, ModelApplet.class);
            Class<? extends Applet> other = ClassFixtures.applet(loader, OtherApplet.class);
            try (TestCard card = open(model)) {
                AppletDeclaration first = install(card, model, null, List.of());

                assertThat(send(card, first, 0x30)).isEqualTo("0001");
                assertThatThrownBy(() -> install(card, other, "0301", List.of(first)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(OtherApplet.class.getName() + " cannot be installed")
                        .hasMessageContaining("the applets " + ModelApplet.class.getName())
                        .hasMessageContaining("build descriptor");
            }
        }
    }
}
