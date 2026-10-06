package name.velikodniy.jcexpress.embedded;

import javacard.framework.Applet;
import javacard.framework.SystemException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.HelloWorldApplet;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.fakes.RecordingSession;
import name.velikodniy.jcexpress.fakes.isolation.LedgerApplet;
import name.velikodniy.jcexpress.fakes.isolation.LedgerClientApplet;
import name.velikodniy.jcexpress.fakes.isolation.LibraryUserApplet;
import name.velikodniy.jcexpress.fakes.isolation.StaticCounterApplet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.fakes.Transcripts.withoutTimes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applet code in {@link EmbeddedSession}: every session loads the applet classes itself, so a new session is a
 * card with freshly loaded packages (static fields start again), while the applets of one session share their
 * static fields like the applets of one package on a card. Deleted instances can be installed again, and a class
 * the applet code needs but the test class path lacks is named.
 */
class EmbeddedSessionIsolationTest {

    private static final AID FIRST = AID.fromHex("F000000001");
    private static final AID SECOND = AID.fromHex("F000000002");

    @Nested
    class FreshStatics {

        @Test
        void everySessionStartsWithFreshStaticFields() {
            try (EmbeddedSession first = new EmbeddedSession(); EmbeddedSession second = new EmbeddedSession()) {
                first.install(StaticCounterApplet.class, FIRST);
                assertThat(first.send(0x80, 0x01)).dataEquals(0x00, 0x01, 0x00, 0x01);
                assertThat(first.send(0x80, 0x01)).dataEquals(0x00, 0x02, 0x00, 0x02);

                second.install(StaticCounterApplet.class, FIRST);
                assertThat(second.send(0x80, 0x01)).dataEquals(0x00, 0x01, 0x00, 0x01);
            }
        }

        @Test
        void theAppletsOfOneSessionShareStaticFieldsLikeTheAppletsOfOnePackage() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(StaticCounterApplet.class, FIRST);
                card.install(StaticCounterApplet.class, SECOND);
                card.select(FIRST);
                assertThat(card.send(0x80, 0x01)).dataEquals(0x00, 0x01, 0x00, 0x01);

                card.select(SECOND);
                assertThat(card.send(0x80, 0x01)).dataEquals(0x00, 0x02, 0x00, 0x01);
            }
        }

        /** The applet runs in the session's copy of its class; the test's copy is untouched. */
        @Test
        void theTestSeesItsOwnCopyOfTheAppletClass() {
            short before = StaticCounterApplet.shared;
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(StaticCounterApplet.class, FIRST);
                card.send(0x80, 0x01);
            }
            assertThat(StaticCounterApplet.shared).isEqualTo(before);
        }

        @Test
        void appletsOfOneSessionShareObjectsThroughShareableInterfaces() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(LedgerApplet.class, FIRST);
                card.install(LedgerClientApplet.class, SECOND);

                assertThat(card.send(0x80, 0x01, 0x00, 0x00, FIRST.toBytes(), 256)).isSuccess().dataEquals(0x12, 0x34);
            }
        }
    }

    /** -Djcx.embedded.sharedStatics=true restores the earlier behaviour for one more release. */
    @Nested
    class SharedStaticsOptOut {

        @Test
        void theSystemPropertyMakesAllSessionsShareTheStaticFieldsOfTheTestsClasses() {
            String previous = System.setProperty(EmbeddedSession.SHARED_STATICS_PROPERTY, "true");
            short before = StaticCounterApplet.shared;
            StaticCounterApplet.shared = 0;
            try (EmbeddedSession first = new EmbeddedSession(); EmbeddedSession second = new EmbeddedSession()) {
                first.install(StaticCounterApplet.class, FIRST);
                first.send(0x80, 0x01);
                second.install(StaticCounterApplet.class, FIRST);

                assertThat(second.send(0x80, 0x01)).dataEquals(0x00, 0x02, 0x00, 0x01);
                assertThat(StaticCounterApplet.shared).isEqualTo((short) 2);
            } finally {
                restore(previous);
                StaticCounterApplet.shared = before;
            }
        }
    }

    /** SmartCardSession.delete: the instance goes, the package (and its static fields) stays loaded. */
    @Nested
    class Delete {

        @Test
        void aDeletedAidCanBeInstalledAgainWithAFreshInstance() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(StaticCounterApplet.class, FIRST);
                assertThat(card.send(0x80, 0x01)).dataEquals(0x00, 0x01, 0x00, 0x01);

                card.delete(FIRST);
                card.install(StaticCounterApplet.class, FIRST);

                assertThat(card.send(0x80, 0x01)).dataEquals(0x00, 0x02, 0x00, 0x01);
            }
        }

        @Test
        void anotherClassCanBeInstalledUnderADeletedAid() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(HelloWorldApplet.class, FIRST);
                card.delete(FIRST);
                card.install(StaticCounterApplet.class, FIRST);

                assertThat(card.send(0x80, 0x01)).dataEquals(0x00, 0x01, 0x00, 0x01);
            }
        }

        /** jCardSim numbers the load files it creates by count, which repeats after a delete. */
        @Test
        void installsAndDeletesInAnyOrderKeepWorking() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                AID third = AID.fromHex("F000000003");
                for (int round = 0; round < 5; round++) {
                    card.install(StaticCounterApplet.class, FIRST);
                    card.install(StaticCounterApplet.class, SECOND);
                    card.delete(FIRST);
                    card.install(StaticCounterApplet.class, third);
                    card.delete(SECOND);
                    card.delete(third);
                }
                card.install(HelloWorldApplet.class, FIRST);
                assertThat(card.send(0x80, 0x01)).isSuccess().dataAsString().isEqualTo("Hello");
            }
        }

        @Test
        void afterDeletingTheSelectedAppletNoAppletIsSelected() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(HelloWorldApplet.class, FIRST);
                card.delete(FIRST);

                assertThat(card.send(0x80, 0x01)).statusWord(0x6986);
            }
        }

        @Test
        void deletingAnAidThatIsNotInstalledFails() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(HelloWorldApplet.class, FIRST);

                assertThatThrownBy(() -> card.delete(SECOND))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("F000000002")
                        .hasMessageContaining("installed: name.velikodniy.jcexpress.HelloWorldApplet as F000000001");
            }
        }

        @Test
        void deletesAreRecordedInTheHistory() {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(HelloWorldApplet.class, FIRST);
                card.delete(FIRST);

                assertThat(card.history().transcript()).endsWith("# delete F000000001\n");
            }
        }

        @Test
        void sessionsWithoutDeleteSupportSaySo() {
            assertThatThrownBy(() -> new RecordingSession().delete(FIRST))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("RecordingSession")
                    .hasMessageContaining("delete");
        }
    }

    /**
     * jCardSim reports a failure inside {@code Applet.install} only as {@code SystemException}; the session names
     * a class the applet code needed but the class path lacks.
     */
    @Nested
    class MissingClasses {

        @Test
        void aClassMissingFromTheClassPathIsNamedWithTheDependencyHint(@TempDir Path dir) throws Exception {
            Class<? extends Applet> applet = withoutLibrary(dir);
            try (EmbeddedSession card = new EmbeddedSession()) {
                assertThatThrownBy(() -> card.install(applet, FIRST))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("LibraryUserApplet")
                        .hasMessageContaining("name.velikodniy.jcexpress.fakes.library.Counter")
                        .hasMessageContaining("provided")
                        .hasMessageContaining("not transitive")
                        .hasCauseInstanceOf(SystemException.class);
            }
        }

        @Test
        void aFailedInstallLeavesTheAidFree(@TempDir Path dir) throws Exception {
            Class<? extends Applet> applet = withoutLibrary(dir);
            try (EmbeddedSession card = new EmbeddedSession()) {
                assertThatThrownBy(() -> card.install(applet, FIRST)).isInstanceOf(IllegalStateException.class);

                card.install(StaticCounterApplet.class, FIRST);
                assertThat(card.send(0x80, 0x01)).isSuccess();
            }
        }

        @Test
        void withSharedStaticsTheFailedInstallCannotNameTheMissingClass(@TempDir Path dir) throws Exception {
            Class<? extends Applet> applet = withoutLibrary(dir);
            String previous = System.setProperty(EmbeddedSession.SHARED_STATICS_PROPERTY, "true");
            try (EmbeddedSession card = new EmbeddedSession()) {
                assertThatThrownBy(() -> card.install(applet, FIRST)).isInstanceOf(InstallException.class)
                        .hasMessageContaining("without an ISOException")
                        .hasMessageNotContaining("name.velikodniy.jcexpress.fakes.library.Counter")
                        .hasCauseInstanceOf(SystemException.class);
            } finally {
                restore(previous);
            }
        }

        @Test
        void aClassMissingWhileACommandRunsIsNotedInTheHistory(@TempDir Path dir) throws Exception {
            Class<? extends Applet> applet = withoutLibrary(dir, "LazyLibraryUserApplet");
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.install(applet, FIRST);

                assertThat(card.send(0x80, 0x01)).statusWord(0x6F00);
                assertThat(withoutTimes(card.history().transcript())).endsWith("""
                        R: 6F00
                        # the applet code needs name.velikodniy.jcexpress.fakes.library.Counter, which is not on\
                         the test class path
                        """);
            }
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty(EmbeddedSession.SHARED_STATICS_PROPERTY);
        } else {
            System.setProperty(EmbeddedSession.SHARED_STATICS_PROPERTY, previous);
        }
    }

    private static Class<? extends Applet> withoutLibrary(Path dir) throws Exception {
        return withoutLibrary(dir, "LibraryUserApplet");
    }

    /**
     * Loads an applet of the fakes.isolation package through a class path that holds only the named classes of
     * that package plus the JDK, jCardSim and the Java Card API: the library package is missing, as in a test
     * module that lacks a transitive (provided-scope) dependency.
     */
    private static Class<? extends Applet> withoutLibrary(Path dir, String... classes) throws Exception {
        Path target = dir.resolve("name/velikodniy/jcexpress/fakes/isolation");
        Files.createDirectories(target);
        for (String name : classes) {
            Files.copy(testClasses().resolve("name/velikodniy/jcexpress/fakes/isolation/" + name + ".class"),
                    target.resolve(name + ".class"));
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, new SimulatorOnly());
        return loader.loadClass("name.velikodniy.jcexpress.fakes.isolation." + classes[classes.length - 1])
                .asSubclass(Applet.class);
    }

    private static Path testClasses() throws URISyntaxException {
        return Path.of(StaticCounterApplet.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** Sees the JDK, jCardSim and its Java Card API (the test class path's copies), nothing else. */
    private static final class SimulatorOnly extends ClassLoader {
        SimulatorOnly() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.startsWith("javacard.") || name.startsWith("javacardx.") || name.startsWith("com.licel.")) {
                return EmbeddedSessionIsolationTest.class.getClassLoader().loadClass(name);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        protected URL findResource(String name) {
            return null;
        }
    }
}
