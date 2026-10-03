package name.velikodniy.jcexpress.model;

import name.velikodniy.jcexpress.DisabledOnBackend;
import name.velikodniy.jcexpress.EnabledOnBackend;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.model.built.BuiltApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User-style test classes of the declarative model, run by {@link DeclarativeModelTest} through the JUnit Platform
 * test kit. They record what they saw in {@link #SEEN}.
 */
final class ModelScenarios {

    /** Observations by scenario. */
    static final Map<String, List<String>> SEEN = new ConcurrentHashMap<>();

    private ModelScenarios() {
    }

    static void seen(String scenario, String observation) {
        SEEN.computeIfAbsent(scenario, key -> new CopyOnWriteArrayList<>()).add(observation);
    }

    static String counter(SmartCardSession card) {
        return card.send(0x80, 0x11, 0, 0, null, 2).dataAsHex();
    }

    static String increment(SmartCardSession card) {
        return card.send(0x80, 0x10, 0, 0, null, 2).dataAsHex();
    }

    /** Requirement 1: a fresh instance per test, deleted afterwards (the default). */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class PerTest {
        @Test
        @Order(1)
        void first(SmartCardSession card) {
            seen("PerTest", "first " + increment(card) + " " + increment(card));
            seen("PerTest", "created " + card.send(0x80, 0x12, 0, 0, null, 2).dataAsHex());
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            seen("PerTest", "second " + increment(card));
            seen("PerTest", "created " + card.send(0x80, 0x12, 0, 0, null, 2).dataAsHex());
        }
    }

    /** Requirement 2: one instance for the class, deleted after it. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = ModelApplet.class, isolation = Isolation.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class PerClass {
        @Test
        @Order(1)
        void first(SmartCardSession card) {
            seen("PerClass", "first " + increment(card));
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            seen("PerClass", "second " + increment(card));
        }

        @AfterAll
        static void after(SmartCardSession card) {
            seen("PerClass", "afterAll " + counter(card));
        }
    }

    /** A method-level applet exists only during its test. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class MethodLevel {
        @Test
        @Order(1)
        @InstallApplet(OtherApplet.class)
        void withOther(SmartCardSession card) {
            seen("MethodLevel", "selected " + card.send(0x80, 0x20, 0, 0, null, 1).dataAsHex());
            card.select(ModelApplet.class);
            seen("MethodLevel", "model " + counter(card));
        }

        @Test
        @Order(2)
        void withoutOther(SmartCardSession card) {
            try {
                card.aid(OtherApplet.class);
                seen("MethodLevel", "other still installed");
            } catch (IllegalStateException e) {
                seen("MethodLevel", "other gone");
            }
            seen("MethodLevel", "selected " + counter(card));
        }
    }

    /** Several instances of one applet with their own install parameters. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = ModelApplet.class, aid = "0101", params = "AABBCC")
    @InstallApplet(value = ModelApplet.class, aid = "0102", params = "112233")
    static class Instances {
        @Test
        void eachInstanceHasItsParameters(SmartCardSession card) {
            seen("Instances", "first " + card.send(0x80, 0x13, 0, 0, null, 256).dataAsHex());
            card.select(card.aid("0102"));
            seen("Instances", "second " + card.send(0x80, 0x13, 0, 0, null, 256).dataAsHex());
            seen("Instances", "aid " + card.send(0x80, 0x16, 0, 0, null, 256).dataAsHex()
                    .equals(card.aid("0102").toHex()));
        }
    }

    /** Nested classes see the enclosing instances and add their own. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = ModelApplet.class, isolation = Isolation.PER_CLASS)
    static class Outer {
        @Test
        void outerTest(SmartCardSession card) {
            seen("Nested", "outer " + increment(card));
        }

        @Nested
        @InstallApplet(value = OtherApplet.class, isolation = Isolation.PER_CLASS)
        class Inner {
            @Test
            void innerTest(SmartCardSession card) {
                seen("Nested", "inner selects other " + card.send(0x80, 0x20, 0, 0, null, 1).dataAsHex());
                card.select(ModelApplet.class);
                seen("Nested", "inner sees outer " + counter(card));
            }
        }

        @AfterAll
        static void after(SmartCardSession card) {
            try {
                card.aid(OtherApplet.class);
                seen("Nested", "other still installed");
            } catch (IllegalStateException e) {
                seen("Nested", "other deleted with the nested class");
            }
        }
    }

    /** Every parameterized invocation gets a fresh instance. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Parameterized {
        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3})
        void invocation(int times, SmartCardSession card) {
            String last = "";
            for (int i = 0; i < times; i++) {
                last = increment(card);
            }
            seen("Parameterized", times + "->" + last);
        }
    }

    /** An imperative install joins the scope it is made in. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = ModelApplet.class, isolation = Isolation.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Imperative {
        @BeforeEach
        void installOther(SmartCardSession card) {
            card.install(OtherApplet.class, card.aid("0201"), null);
        }

        @Test
        @Order(1)
        void otherIsSelected(SmartCardSession card) {
            seen("Imperative", "other " + card.send(0x80, 0x20, 0, 0, null, 1).dataAsHex());
        }

        @Test
        @Order(2)
        void otherIsFreshAgain(SmartCardSession card) {
            seen("Imperative", "aid " + card.aid(OtherApplet.class).toHex().endsWith("0201"));
        }
    }

    /** A @SmartCard field of a @JavaCardTest class receives the card of the run. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Field {
        @SmartCard
        SmartCardSession field;

        @Test
        void fieldIsTheCard(SmartCardSession card) {
            seen("Field", "same " + (field == card));
            seen("Field", "works " + increment(field));
        }
    }

    /** Deselecting clears CLEAR_ON_DESELECT memory. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Deselect {
        @Test
        void deselectClearsTransientMemory(SmartCardSession card) {
            card.send(0x80, 0x14, 0x5A, 0);
            seen("Deselect", "before " + card.send(0x80, 0x15, 0, 0, null, 1).dataAsHex());
            card.deselect();
            card.select(ModelApplet.class);
            seen("Deselect", "after " + card.send(0x80, 0x15, 0, 0, null, 1).dataAsHex());
        }
    }

    /** A failing test carries its APDU exchanges. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Failing {
        @Test
        void fails(SmartCardSession card) {
            increment(card);
            assertThat(counter(card)).isEqualTo("0002");
        }
    }

    /** Backend conditions. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Conditions {
        @Test
        @DisabledOnBackend(value = Mode.EMBEDDED, reason = "jCardSim differs here")
        void notOnJCardSim() {
            seen("Conditions", "ran notOnJCardSim");
        }

        @Test
        @EnabledOnBackend(Mode.LIVECARD)
        void onlyOnTheCard() {
            seen("Conditions", "ran onlyOnTheCard");
        }

        @Test
        void everywhere() {
            seen("Conditions", "ran everywhere");
        }
    }

    /**
     * Two declarations with the same instance AID (identical declarations collapse into one, as JUnit treats
     * repeated annotations; these differ in their parameters).
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    @InstallApplet(value = ModelApplet.class, params = "01")
    static class Duplicate {
        @Test
        void neverRuns() {
            seen("Duplicate", "ran");
        }
    }

    /** Statics persist across the PER_TEST instances of a class run. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Statics {
        @Test
        @Order(1)
        void one(SmartCardSession card) {
            seen("Statics", card.send(0x80, 0x12, 0, 0, null, 2).dataAsHex());
        }

        @Test
        @Order(2)
        void two(SmartCardSession card) {
            seen("Statics", card.send(0x80, 0x12, 0, 0, null, 2).dataAsHex());
        }
    }

    /**
     * An applet of a package the Maven plugin built: the project's AID prefix, and the build's AIDs noted when the
     * instance is installed (before the test, so the history of the class shows the note in {@code @AfterAll}).
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(BuiltApplet.class)
    static class Built {
        @Test
        void usesTheProjectsPrefix(SmartCardSession card) {
            seen("Built", card.aid(BuiltApplet.class).toHex());
            seen("Built", card.send(0x80, 0x01, 0, 0, null, 1).dataAsHex());
        }

        @AfterAll
        static void buildNote(SmartCardSession card) {
            card.history().transcript().lines().filter(line -> line.contains("build of")).forEach(
                    line -> seen("Built", line));
        }
    }

    /** Uses the hex helper so that the AID prefix setting is visible in the run. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Prefix {
        @Test
        void aidsStartWithThePrefix(SmartCardSession card) {
            seen("Prefix", Hex.encode(card.aid(ModelApplet.class).toBytes()));
        }
    }
}
