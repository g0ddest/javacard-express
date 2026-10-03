package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCommand;
import name.velikodniy.jcexpress.livecard.model.chunked.ChunkedApplet;
import name.velikodniy.jcexpress.livecard.model.counting.CountingApplet;
import name.velikodniy.jcexpress.livecard.model.failing.ParametersRequiredApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
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
 * Card tests about the lifetime of the card of a {@code @JavaCardTest} class, written once and run on jCardSim and
 * on the simulated GlobalPlatform card: GET RESPONSE and Le correction, the SELECT response, which applet is
 * selected when and how often its {@code select()} and {@code deselect()} run, what {@code history()} holds, and
 * what a failure carries. They record what they saw; the observations must be the same on both backends.
 */
final class LifecycleBackendScenarios {

    /** Observations by scenario. */
    static final Map<String, List<String>> SEEN = new ConcurrentHashMap<>();

    private LifecycleBackendScenarios() {
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

    /** The commands of history entries, header only: "00A40400 80130000". */
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
            String aid = card.send(0x80, 0x16, 0, 0, null, 256).dataAsHex();
            seen("PerClassBeforeAll", "beforeAll " + aid.equals(card.aid("01").toHex()) + " " + counts(card));
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

    /**
     * ADV-5: a PER_CLASS instance next to a PER_TEST one. Installing and deleting the PER_TEST instance deselects the
     * PER_CLASS one on every backend, as card content management through the Issuer Security Domain does on a card.
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, aid = "01", isolation = Isolation.PER_CLASS)
    @InstallApplet(value = CountingApplet.class, aid = "02")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Mixed {
        @BeforeAll
        static void personalise(SmartCardSession card) {
            card.send(0x80, 0x11, 0x5A, 0);
        }

        @Test
        @Order(1)
        void first(SmartCardSession card) {
            seen("Mixed", "first " + counts(card) + " transient " + transientByte(card));
            card.send(0x80, 0x11, 0x6B, 0);
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            seen("Mixed", "second " + counts(card) + " transient " + transientByte(card));
        }
    }

    /** API-3 and API-2: a SELECT the test sends, a reset and a deselect decide whether the next test selects. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = CountingApplet.class, isolation = Isolation.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Tracking {
        @Test
        @Order(1)
        void selectsWithACommand(SmartCardSession card) {
            APDUResponse fci = card.send(APDUCommand.select(card.aid(CountingApplet.class)));
            assertThat(fci).isSuccess().tlv().tag(0x6F).tag(0x84).hasValue(card.aid(CountingApplet.class).toBytes());
            seen("Tracking", "fci " + fci.dataAsHex().startsWith("6F"));
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

    /** API-2: the answer to SELECT, asserted like any response. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(CountingApplet.class)
    static class SelectResponse {
        @Test
        void theAnswerToSelectIsAsserted(SmartCardSession card) {
            byte[] aid = card.aid(CountingApplet.class).toBytes();
            APDUResponse fci = card.send(APDUCommand.select(card.aid(CountingApplet.class)));
            assertThat(fci).isSuccess().tlv().tag(0x6F).tag(0x84).hasValue(aid);
            seen("SelectResponse", "fci " + fci.dataAsHex().equals(String.format("6F%02X84%02X", aid.length + 2,
                    aid.length) + Hex.encode(aid)));
            seen("SelectResponse", "then " + counts(card));
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
        }

        @AfterAll
        static void afterAll(SmartCardSession card) {
            seen("HistoryScope", "afterAll " + card.history().entries().stream()
                    .filter(entry -> entry.ins() == 0x13).count());
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
}
