package name.velikodniy.jcexpress.embedded.gp;

import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.AppletInstallParameters;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.fakes.gp.SecureChannelApplet;
import org.globalplatform.GPSystem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One {@link GlobalPlatformCard} behind several jCardSim runtimes, as a card simulation with one runtime per load
 * file uses it, and what {@code org.globalplatform.GPSystem} does where there is no such card.
 */
class SharedGlobalPlatformCardTest {

    private static final AID FIRST = AID.fromHex("F0000000030101");
    private static final AID SECOND = AID.fromHex("F0000000030201");
    private static final byte[] LEVEL = Hex.decode("80120000");

    /** A runtime that reports to a card it shares with other runtimes ({@link GlobalPlatformHost}). */
    static final class LoadFileRuntime extends SimulatorRuntime implements GlobalPlatformHost {
        private final GlobalPlatformCard card;

        LoadFileRuntime(GlobalPlatformCard card) {
            this.card = card;
        }

        @Override
        public GlobalPlatformCard globalPlatform() {
            return card;
        }

        @Override
        public void installApplet(javacard.framework.AID loadFile, javacard.framework.AID module,
                                  javacard.framework.AID applet, byte[] bArray, short bOffset, byte bLength) {
            AID aid = GlobalPlatformCard.aidOf(applet);
            card.installStarted(aid, new byte[0]);
            try {
                super.installApplet(loadFile, module, applet, bArray, bOffset, bLength);
            } catch (RuntimeException e) {
                card.installFailed(aid);
                throw e;
            }
            card.installSucceeded(aid, CardApplication.SELECTABLE);
        }

        @Override
        protected void deselect(ApplicationInstance applicationInstance) {
            super.deselect(applicationInstance);
            card.applicationSessionEnded();
        }
    }

    private static CardSimulator simulatorWith(GlobalPlatformCard card, AID aid) {
        CardSimulator simulator = new CardSimulator(new LoadFileRuntime(card));
        byte[] parameters = AppletInstallParameters.encode(aid, new byte[]{0, 1});
        simulator.installApplet(AIDUtil.create(aid.toBytes()), SecureChannelApplet.class, parameters, (short) 0,
                (byte) parameters.length);
        return simulator;
    }

    private static void open(CardSimulator simulator, Scp03Host host) {
        byte[] update = simulator.transmitCommand(Scp03Host.concat(host.initializeUpdate(0), new byte[1]));
        assertThat(host.receive(Arrays.copyOf(update, update.length - 2))).isTrue();
        assertThat(simulator.transmitCommand(host.externalAuthenticate(0x01))).isEqualTo(Hex.decode("9000"));
    }

    @Test
    void theApplicationsOfSeveralRuntimesShareRegistryAndSecurityDomain_gpcs_7_3_1() {
        GlobalPlatformCard card = GlobalPlatformCard.withTestKeys();
        CardSimulator first = simulatorWith(card, FIRST);
        CardSimulator second = simulatorWith(card, SECOND);
        Scp03Host host = Scp03Host.withTestKeys();
        assertThat(card.applications()).extracting(CardApplication::aid).containsExactly(FIRST, SECOND);

        first.selectApplet(AIDUtil.create(FIRST.toBytes()));
        open(first, host);
        assertThat(first.transmitCommand(LEVEL)).isEqualTo(Hex.decode("819000"));

        second.selectApplet(AIDUtil.create(SECOND.toBytes()));
        assertThat(card.securityDomain().securityLevel()).as("selecting an application ends the session").isZero();
        open(second, host);

        assertThat(second.transmitCommand(LEVEL)).isEqualTo(Hex.decode("819000"));
        assertThat(first.transmitCommand(LEVEL)).as("the session belongs to the application that opened it")
                .isEqualTo(Hex.decode("6985"));
        assertThat(card.securityDomain().externalAuthentications()).isEqualTo(2);
    }

    @Test
    void outsideACardGPSystemSaysWhereItWorks() throws Exception {
        // jCardSim activates a default runtime on the thread that initialises SimulatorSystem: do that here
        Class.forName("com.licel.jcardsim.base.SimulatorSystem");
        AtomicReference<Throwable> withoutRuntime = new AtomicReference<>();
        AtomicReference<Throwable> foreignRuntime = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            withoutRuntime.set(thrownBy(GPSystem::getCardState));
            new SimulatorRuntime() {
                {
                    activateSimulatorRuntimeInstance();
                }
            };
            foreignRuntime.set(thrownBy(GPSystem::getSecureChannel));
        });
        thread.start();
        thread.join();

        assertThat(withoutRuntime.get()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GPSystem.getCardState was called outside a card");
        assertThat(foreignRuntime.get()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GPSystem.getSecureChannel was called on a jCardSim runtime without a GlobalPlatform"
                        + " environment");
    }

    private static Throwable thrownBy(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
