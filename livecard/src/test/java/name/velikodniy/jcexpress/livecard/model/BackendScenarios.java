package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.EnabledOnBackend;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.model.applet.ModelApplet;
import name.velikodniy.jcexpress.livecard.model.applet.OtherApplet;
import name.velikodniy.jcexpress.livecard.model.built.BuiltApplet;
import name.velikodniy.jcexpress.livecard.model.failing.ParametersRequiredApplet;
import org.junit.jupiter.api.AfterAll;
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

/**
 * Card tests written once, run by {@link SameTestOnEveryBackendTest} on jCardSim and on the simulated
 * GlobalPlatform card (the real-card code path: conversion, APDU guard, SCP03, LOAD/INSTALL/DELETE, cleanup). They
 * record what they saw; the observations must be the same on both backends.
 */
final class BackendScenarios {

    /** Observations by scenario. */
    static final Map<String, List<String>> SEEN = new ConcurrentHashMap<>();

    private BackendScenarios() {
    }

    static void seen(String scenario, String observation) {
        SEEN.computeIfAbsent(scenario, key -> new CopyOnWriteArrayList<>()).add(observation);
    }

    static String increment(SmartCardSession card) {
        return card.send(0x80, 0x30, 0, 0, null, 2).dataAsHex();
    }

    static String counter(SmartCardSession card) {
        return card.send(0x80, 0x31, 0, 0, null, 2).dataAsHex();
    }

    static String created(SmartCardSession card) {
        return card.send(0x80, 0x32, 0, 0, null, 2).dataAsHex();
    }

    /** Requirement 1: a fresh instance per test, deleted afterwards. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class PerTest {
        @Test
        @Order(1)
        void first(SmartCardSession card) {
            seen("PerTest", "first " + increment(card) + " " + increment(card) + " created " + created(card));
        }

        @Test
        @Order(2)
        void second(SmartCardSession card) {
            seen("PerTest", "second " + increment(card) + " created " + created(card));
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

    /** Several instances, install parameters, a method-level applet of the same package, nested classes. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(value = ModelApplet.class, aid = "0101", params = "AABBCC")
    @InstallApplet(value = ModelApplet.class, aid = "0102", params = "112233", isolation = Isolation.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Scopes {
        @Test
        @Order(1)
        void instancesHaveTheirParameters(SmartCardSession card) {
            seen("Scopes", "first " + card.send(0x80, 0x33, 0, 0, null, 256).dataAsHex());
            card.select(card.aid("0102"));
            seen("Scopes", "second " + card.send(0x80, 0x33, 0, 0, null, 256).dataAsHex());
            seen("Scopes", "aid " + card.send(0x80, 0x36, 0, 0, null, 256).dataAsHex().equals(card.aid("0102").toHex()));
        }

        @Test
        @Order(2)
        @InstallApplet(OtherApplet.class)
        void methodLevelAppletIsSelected(SmartCardSession card) {
            seen("Scopes", "other " + card.send(0x80, 0x37, 0, 0, null, 1).dataAsHex());
        }

        @Test
        @Order(3)
        void deselectClearsTransientMemory(SmartCardSession card) {
            card.send(0x80, 0x34, 0x5A, 0);
            card.deselect();
            card.select(card.aid("0101"));
            seen("Scopes", "transient " + card.send(0x80, 0x35, 0, 0, null, 1).dataAsHex());
        }

        @Nested
        @InstallApplet(value = OtherApplet.class, aid = "0201", isolation = Isolation.PER_CLASS)
        class Inner {
            @Test
            void innerSeesBoth(SmartCardSession card) {
                seen("Scopes", "inner other " + card.send(0x80, 0x37, 0, 0, null, 1).dataAsHex());
                card.select(card.aid("0102"));
                seen("Scopes", "inner per-class " + card.send(0x80, 0x33, 0, 0, null, 256).dataAsHex());
            }
        }
    }

    /** Every parameterized invocation gets a fresh instance. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Parameterized {
        @ParameterizedTest
        @ValueSource(ints = {1, 3})
        void invocation(int times, SmartCardSession card) {
            String last = "";
            for (int i = 0; i < times; i++) {
                last = increment(card);
            }
            seen("Parameterized", times + "->" + last);
        }
    }

    /** GlobalPlatform backends also provide the harness, for card-content checks. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class CardContent {
        @Test
        @EnabledOnBackend({Mode.SIMULATED_GP, Mode.LIVECARD})
        void theIsdListsTheInstance(LiveCard live, SmartCardSession card) {
            seen("CardContent", "listed " + live.content().application(card.aid(ModelApplet.class)).isPresent());
        }
    }

    /** An applet whose install method needs install parameters, declared without them: the install fails. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ParametersRequiredApplet.class)
    static class ForgottenParameters {
        @Test
        void neverRuns(SmartCardSession card) {
            seen("ForgottenParameters", "installed " + card.aid(ParametersRequiredApplet.class).toHex());
        }
    }

    /** An applet of a package the Maven plugin built: the project's AID prefix, converted with the build's settings. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(BuiltApplet.class)
    static class Built {
        @Test
        void runsUnderTheProjectsPrefix(SmartCardSession card) {
            seen("Built", card.aid(BuiltApplet.class).toHex());
            seen("Built", card.send(0x80, 0x38, 0, 0, null, 1).dataAsHex());
        }
    }
}
