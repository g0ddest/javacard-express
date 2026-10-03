package name.velikodniy.jcexpress.model;

import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCommand;
import name.velikodniy.jcexpress.fakes.ParametersRequiredApplet;
import name.velikodniy.jcexpress.fakes.RefusingSelectApplet;
import name.velikodniy.jcexpress.fakes.ThrowingApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/**
 * User-style test classes about the lifetime of the card of a {@code @JavaCardTest} class, run by
 * {@link TestLifecycleTest} through the JUnit Platform test kit: GET RESPONSE and Le correction, which applet is
 * selected when, which instance a class names, what a failure in a lifecycle method carries, and what
 * {@code history()} holds. They record what they saw in {@link #SEEN}.
 */
final class LifecycleScenarios {

    /** Observations by scenario. */
    static final Map<String, List<String>> SEEN = new ConcurrentHashMap<>();

    private LifecycleScenarios() {
    }

    static void seen(String scenario, String observation) {
        SEEN.computeIfAbsent(scenario, key -> new CopyOnWriteArrayList<>()).add(observation);
    }

    /** The select() and deselect() counts of {@link CountingApplet}: "selects=1 deselects=0". */
    static String counts(SmartCardSession card) {
        byte[] counts = card.send(0x80, 0x10, 0, 0, null, 4).data();
        return "selects=" + ((counts[0] << 8) | (counts[1] & 0xFF)) + " deselects="
                + ((counts[2] << 8) | (counts[3] & 0xFF));
    }

    /** The CLEAR_ON_DESELECT byte of {@link CountingApplet}. */
    static String transientByte(SmartCardSession card) {
        return card.send(0x80, 0x12, 0, 0, null, 1).dataAsHex();
    }

    /** The AID of the selected {@link CountingApplet} instance. */
    static String selectedAid(SmartCardSession card) {
        return card.send(0x80, 0x16, 0, 0, null, 256).dataAsHex();
    }

    /** What an exception says, or "no exception". */
    static String message(Runnable action) {
        try {
            action.run();
            return "no exception";
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    /** The commands of a list of history entries, header only: "00A40400 80130000". */
    static String headers(List<APDULogEntry> entries) {
        return String.join(" ", entries.stream().map(entry -> Hex.encode(Arrays.copyOf(entry.command(), 4)))
                .toList());
    }

    /** API-1: an applet that answers in '61XX' parts and with '6CXX'. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ChunkedApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class GetResponse {
        @Test
        @Order(1)
        void theCardCompletesTheAnswer(SmartCardSession card) {
            record("send", () -> card.send(0x80, 0x40, 0x02, 0x58, null, 256));
            record("command", () -> card.send(APDUCommand.of(0x80, 0x40, 0x02, 0x58).le(256)));
            record("hex", () -> card.sendHex("80 40 02 58 00"));
            record("exact", () -> card.send(0x80, 0x42, 0, 0, null, 256));
            byte[] raw = card.transmit(Hex.decode("8040025800"));
            seen("GetResponse", "transmit " + raw.length + " " + Hex.encode(Arrays.copyOfRange(raw, raw.length - 2,
                    raw.length)));
        }

        @Test
        @Order(2)
        void theHistoryShowsTheGetResponseExchanges(SmartCardSession card) {
            card.send(0x80, 0x40, 0x02, 0x58, null, 256);
            seen("GetResponse", "history " + headers(card.history().entries()));
        }

        private static void record(String how, Supplier<APDUResponse> send) {
            APDUResponse response = send.get();
            byte[] data = response.data();
            boolean sequence = true;
            for (int i = 0; i < data.length; i++) {
                sequence &= data[i] == (byte) i;
            }
            seen("GetResponse", how + " " + String.format("%04X", response.sw()) + " " + data.length + " "
                    + (sequence ? "in order" : Hex.encode(data)));
        }
    }

    /** ADV-5 and API-3: two PER_CLASS instances; @BeforeAll talks to the first declared one. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, aid = "01", isolation = Isolation.PER_CLASS)
    @InstallApplet(value = CountingApplet.class, aid = "02", isolation = Isolation.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class PerClassBeforeAll {
        @BeforeAll
        static void personalise(SmartCardSession card) {
            seen("PerClassBeforeAll", "beforeAll " + selectedAid(card).endsWith("01") + " " + counts(card));
            card.send(0x80, 0x11, 0x5A, 0);
        }

        @Test
        @Order(1)
        void first(SmartCardSession card) {
            seen("PerClassBeforeAll", "first " + counts(card) + " transient " + transientByte(card));
            card.send(0x80, 0x11, 0x6B, 0);
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            seen("PerClassBeforeAll", "second " + counts(card) + " transient " + transientByte(card));
        }
    }

    /** ADV-5: a fresh instance per test is selected once before the test. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class PerTestCounts {
        @Test
        @Order(1)
        void first(SmartCardSession card) {
            seen("PerTestCounts", "first " + counts(card));
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            seen("PerTestCounts", "second " + counts(card));
        }
    }

    /** API-3: a SELECT the test sends, a reset and a deselect decide whether the next test selects again. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, isolation = Isolation.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Tracking {
        @Test
        @Order(1)
        void selectsWithACommand(SmartCardSession card) {
            assertThat(card.send(APDUCommand.select(card.aid(CountingApplet.class)))).isSuccess();
            card.send(0x80, 0x11, 0x11, 0);
        }

        @Test
        @Order(2)
        void keepsTheSelection(SmartCardSession card) {
            seen("Tracking", "kept " + counts(card) + " transient " + transientByte(card));
            card.reset();
        }

        @Test
        @Order(3)
        void selectedAgainAfterAReset(SmartCardSession card) {
            seen("Tracking", "after reset " + counts(card));
            card.deselect();
        }

        @Test
        @Order(4)
        void selectedAgainAfterADeselect(SmartCardSession card) {
            seen("Tracking", "after deselect " + counts(card));
        }
    }

    /**
     * ADV-5: the first PER_CLASS applet refuses selection; the class's tests talk to their own applet, so the class
     * runs, and the history says why nothing was selected for @BeforeAll.
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    @InstallApplet(value = RefusingSelectApplet.class, isolation = Isolation.PER_CLASS)
    static class RefusingPerClass {
        @BeforeAll
        static void beforeAll(SmartCardSession card) {
            seen("RefusingPerClass", "beforeAll " + card.history().transcript().lines()
                    .filter(line -> line.startsWith("# ")).toList());
        }

        @Test
        void testsTalkToTheirApplet(SmartCardSession card) {
            seen("RefusingPerClass", "test " + counts(card));
        }
    }

    /** API-4: two instances of one class. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, aid = "0101")
    @InstallApplet(value = CountingApplet.class, aid = "0102")
    static class TwoInstances {
        @Test
        void aClassNamesNoInstance(SmartCardSession card) {
            seen("TwoInstances", "selected " + selectedAid(card).equals(card.aid("0101").toHex()));
            seen("TwoInstances", "aid: " + message(() -> card.aid(CountingApplet.class)));
            seen("TwoInstances", "select: " + message(() -> card.select(CountingApplet.class)));
        }
    }

    /** ADV-17: aid(Class) in @BeforeAll for a PER_TEST declaration. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    static class AidInBeforeAll {
        @BeforeAll
        static void lookUp(SmartCardSession card) {
            seen("AidInBeforeAll", message(() -> card.aid(CountingApplet.class)));
        }

        @Test
        void test(SmartCardSession card) {
            seen("AidInBeforeAll", "test " + card.aid(CountingApplet.class).toHex().equals(selectedAid(card)));
        }
    }

    /** FAIL-4: an assertion in @BeforeEach fails. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    static class BeforeEachFails {
        @BeforeEach
        void personalise(SmartCardSession card) {
            assertThat(card.send(0x80, 0x7F, 0, 0)).isSuccess();
        }

        @Test
        void neverRuns() {
            seen("BeforeEachFails", "ran");
        }
    }

    /** FAIL-4: an assertion in @BeforeAll fails. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, isolation = Isolation.PER_CLASS)
    static class BeforeAllFails {
        @BeforeAll
        static void personalise(SmartCardSession card) {
            assertThat(card.send(0x80, 0x7E, 0, 0)).isSuccess();
        }

        @Test
        void neverRuns() {
            seen("BeforeAllFails", "ran");
        }
    }

    /** FAIL-4: assertions in @AfterEach and @AfterAll fail. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, isolation = Isolation.PER_CLASS)
    static class AfterMethodsFail {
        @Test
        void passes(SmartCardSession card) {
            card.send(0x80, 0x13, 0, 0, null, 2);
        }

        @AfterEach
        void check(SmartCardSession card) {
            assertThat(card.send(0x80, 0x7D, 0, 0)).isSuccess();
        }

        @AfterAll
        static void checkAll(SmartCardSession card) {
            assertThat(card.send(0x80, 0x7C, 0, 0)).isSuccess();
        }
    }

    /** FAIL-4: a declared install fails (the install parameters are missing). */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ParametersRequiredApplet.class)
    static class DeclaredInstallFails {
        @Test
        void neverRuns() {
            seen("DeclaredInstallFails", "ran");
        }
    }

    /** FAIL-6: no @InstallApplet at all. */
    @OnlyInTestKit
    @JavaCardTest
    static class NothingInstalled {
        @Test
        void sendsToNothing(SmartCardSession card) {
            assertThat(card.send(0x80, 0x52, 0, 0, null, 2)).isSuccess();
        }
    }

    /** FAIL-8: what history() holds in lifecycle methods and tests. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class HistoryScope {
        @BeforeAll
        static void beforeAll(SmartCardSession card) {
            seen("HistoryScope", "beforeAll [" + headers(card.history().entries()) + "]");
        }

        @Test
        @Order(1)
        void first(SmartCardSession card) {
            card.send(0x80, 0x13, 0, 0, null, 2);
            seen("HistoryScope", "first [" + headers(card.history().entries()) + "]");
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            card.send(0x80, 0x13, 0, 0, null, 2);
            card.send(0x80, 0x13, 0, 0, null, 2);
            seen("HistoryScope", "second [" + headers(card.history().entries()) + "]");
            seen("HistoryScope", "second transcript " + card.history().transcript().contains("80130000"));
        }

        @AfterAll
        static void afterAll(SmartCardSession card) {
            seen("HistoryScope", "afterAll " + card.history().entries().stream()
                    .filter(entry -> entry.ins() == 0x13).count());
        }
    }

    /** FAIL-2: the applet throws in process. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ThrowingApplet.class)
    static class AppletThrows {
        @Test
        void fails(SmartCardSession card) {
            assertThat(card.send(0x80, 0x01, 0, 0)).isSuccess();
        }
    }

    /** API-4 follow-up: an instance declared without an aid suffix next to a nested one with a suffix. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    static class UnsuffixedInstance {
        @Nested
        @InstallApplet(value = CountingApplet.class, aid = "0401")
        class WithASecond {
            @Test
            void aClassNamesNoInstance(SmartCardSession card) {
                seen("UnsuffixedInstance", "selected " + selectedAid(card).equals(card.aid("0401").toHex()));
                seen("UnsuffixedInstance", "aid: " + message(() -> card.aid(CountingApplet.class)));
            }
        }
    }

    /** A failure after more exchanges than a failure shows: the transcript points to the file with all of them. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    static class LongTranscript {
        @Test
        void failsAfterManyExchanges(SmartCardSession card) {
            for (int i = 0; i < 45; i++) {
                card.send(0x80, 0x10, 0, 0, null, 4);
            }
            assertThat(card.send(0x80, 0x7F, 0, 0)).isSuccess();
        }
    }
}
