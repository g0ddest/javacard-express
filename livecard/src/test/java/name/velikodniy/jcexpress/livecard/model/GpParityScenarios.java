package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.EnabledOnBackend;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.LogicalChannel;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.livecard.model.anyins.AnyInsApplet;
import name.velikodniy.jcexpress.livecard.model.applet.ModelApplet;
import name.velikodniy.jcexpress.livecard.model.applet.OtherApplet;
import name.velikodniy.jcexpress.livecard.model.built.BuiltApplet;
import name.velikodniy.jcexpress.livecard.model.importer.ImporterApplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static name.velikodniy.jcexpress.livecard.model.BackendScenarios.seen;

/**
 * Card tests that the GlobalPlatform backends handled differently from jCardSim, run by {@link GpBackendParityTest}
 * on jCardSim and on the simulated GlobalPlatform card. They record what they saw in {@link BackendScenarios#SEEN}.
 */
final class GpParityScenarios {

    private GpParityScenarios() {
    }

    /**
     * The "computed install parameters" recipe: an applet of a package that a declared applet already loaded is
     * installed in {@code @BeforeEach}, with parameters only known at run time (the declared instance's AID).
     */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class ImperativePeer {
        @BeforeEach
        void installPeer(SmartCardSession card) {
            card.install(OtherApplet.class, card.aid("0301"), card.aid(ModelApplet.class).toBytes());
        }

        @Test
        void bothInstancesAnswer(SmartCardSession card) {
            seen("ImperativePeer", "other " + card.send(0x80, 0x37, 0, 0, null, 1).dataAsHex());
            card.select(ModelApplet.class);
            seen("ImperativePeer", "model " + card.send(0x80, 0x30, 0, 0, null, 2).dataAsHex());
        }
    }

    /** An applet that uses a class of another package (a library module). */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ImporterApplet.class)
    static class Importer {
        @Test
        void counts(SmartCardSession card) {
            seen("Importer", "count " + card.send(0x80, 0x39, 0, 0, null, 2).dataAsHex());
        }
    }

    /** The applet's own command with INS 70 (MANAGE CHANNEL in the inter-industry classes), then another one. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(AnyInsApplet.class)
    static class OwnInsSeventy {
        @Test
        void ownCommandThenAnother(SmartCardSession card) {
            seen("OwnInsSeventy", "70 -> " + card.send(0x80, 0x70, 0x04, 0x00, null, 1).dataAsHex());
            try {
                seen("OwnInsSeventy", "10 -> " + card.send(0x80, 0x10, 0x00, 0x00, null, 1).dataAsHex());
            } catch (RuntimeException e) {
                seen("OwnInsSeventy", "10 -> " + e.getMessage());
            }
        }
    }

    /** A logical channel opened next to the test applet (the simulated card runs applets on the basic channel). */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Channels {
        @Test
        @EnabledOnBackend(Mode.SIMULATED_GP)
        void theTestAppletOnALogicalChannel(SmartCardSession card) {
            try (LogicalChannel channel = LogicalChannel.open(card)) {
                seen("Channels", "applet " + channel.select(card.aid(ModelApplet.class)).sw());
                seen("Channels", "isd " + channel.select(AID.fromHex("A000000151000000")).sw());
            }
            seen("Channels", "basic " + card.send(0x80, 0x30, 0, 0, null, 2).dataAsHex());
            card.history().transcript().lines().filter(line -> line.startsWith("# simulated card"))
                    .forEach(line -> seen("Channels", line));
        }
    }

    /** Typical mistakes: an unknown instruction checked with requireSuccess(), a SELECT of the build's AID. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(BuiltApplet.class)
    static class Mistakes {
        @Test
        void requireSuccessShowsTheCommand(SmartCardSession card) {
            try {
                card.send(0x80, 0x7F, 0x01, 0x02).requireSuccess();
            } catch (AssertionError e) {
                seen("Mistakes", e.getMessage());
            }
        }

        @Test
        void selectOfTheBuildsAid(SmartCardSession card) {
            try {
                card.select(AID.fromHex("A0000000629901"));
            } catch (SelectException e) {
                seen("Mistakes", e.getMessage());
            }
        }
    }

    /** An instance under a fixed AID outside the run's prefix, as an applet with a hard-coded peer AID needs. */
    @OnlyInTestKit
    @JavaCardTest
    static class FixedAid {
        @Test
        void installsUnderAFixedAid(SmartCardSession card) {
            card.install(ModelApplet.class, AID.fromHex("A0000000624002"));
            seen("FixedAid", "installed " + card.send(0x80, 0x36, 0, 0, null, 256).dataAsHex());
        }
    }

    /** A test that fails after its applet answered, and one whose command the APDU guard blocks. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class Failing {
        @Test
        @Order(1)
        void fails(SmartCardSession card) {
            card.send(0x80, 0x30, 0, 0, null, 2);
            throw new AssertionError("deliberately failing after " + card.send(0x80, 0x31, 0, 0, null, 2));
        }

        @Test
        @Order(2)
        void sendsInitializeUpdateToItsApplet(SmartCardSession card) {
            card.send(0x80, 0x50, 0, 0, null, 2);
            throw new AssertionError("deliberately failing: 80 50 reached the applet");
        }
    }
}
