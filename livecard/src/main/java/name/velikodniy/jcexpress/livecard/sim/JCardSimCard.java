package name.velikodniy.jcexpress.livecard.sim;

import com.licel.jcardsim.base.LoadFile;
import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.SystemException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.AppletInstallParameters;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.embedded.gp.CardApplication;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformCard;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformHost;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One jCardSim card for the applets of a class path: its own class loader (fresh
 * static fields, see {@link AppletClassLoader}), install with the Java Card install parameters, selection,
 * deselection through an {@link IdleApplet}, card reset and delete. The live tests compare the real card with
 * it, and the {@link SimulatedCard} runs the applets of linked load files on one of these.
 *
 * <p>An AID can be installed again after it was deleted, as on a card (GlobalPlatform Card Specification v2.3.1
 * 11.2, 11.5): a new instance, of the same or another applet class. jCardSim's own runtime keeps the load file it
 * generated for an AID after the applet is deleted and refuses that AID from then on; {@link ReusableAidRuntime}
 * forgets it.</p>
 *
 * <p>The applets reach the GlobalPlatform environment of their card through {@code org.globalplatform.GPSystem}
 * ({@link GlobalPlatformCard}): the one of the {@link SimulatedCard} this card belongs to, shared by the jCardSim
 * cards of all its load files, or one of its own with the GlobalPlatform test keys. The runtime reports installs,
 * deletions, deselections and resets to it.</p>
 */
public final class JCardSimCard implements AutoCloseable {

    /** AID of the {@link IdleApplet}; it exists only inside the simulator. */
    static final String IDLE_AID = "A000000000FFFF";
    private final List<Path> classPath;
    private final AppletClassLoader loader;
    private final GlobalPlatformCard globalPlatform;
    private final ReusableAidRuntime runtime;
    private final CardSimulator simulator;

    /**
     * Creates an empty card (only the idle applet is installed) with a GlobalPlatform environment of its own,
     * whose Security Domain has the GlobalPlatform test keys.
     *
     * @param classPath where the applet classes are loaded from (each card loads them itself)
     */
    public JCardSimCard(List<Path> classPath) {
        this(classPath, GlobalPlatformCard.withTestKeys());
    }

    /**
     * Creates an empty card (only the idle applet is installed) that belongs to a GlobalPlatform card.
     *
     * @param classPath      where the applet classes are loaded from (each card loads them itself)
     * @param globalPlatform the GlobalPlatform environment the applets of this card see; the jCardSim cards of one
     *                       simulated card share it
     */
    public JCardSimCard(List<Path> classPath, GlobalPlatformCard globalPlatform) {
        this.classPath = List.copyOf(classPath);
        this.loader = new AppletClassLoader(this.classPath, JCardSimCard.class.getClassLoader());
        this.globalPlatform = globalPlatform;
        this.runtime = new ReusableAidRuntime(globalPlatform);
        this.simulator = new CardSimulator(runtime);
        simulator.installApplet(AIDUtil.create(IDLE_AID), IdleApplet.class);
    }

    /**
     * Returns the GlobalPlatform environment the applets of this card see.
     *
     * @return the card's GlobalPlatform environment
     */
    public GlobalPlatformCard globalPlatform() {
        return globalPlatform;
    }

    /**
     * Installs an applet the way INSTALL [for install and make selectable] does:
     * {@code [Li AID][00][La parameters]}, life cycle state SELECTABLE.
     *
     * @param className  the applet class, loaded by this card's class loader
     * @param aid        the instance AID
     * @param parameters the application specific parameters ('C9' value)
     * @throws InstallException if the applet's install method fails; no instance is created and the AID stays free
     */
    public void install(String className, AID aid, byte[] parameters) {
        install(className, aid, parameters, CardApplication.SELECTABLE);
    }

    /**
     * Installs an applet the way INSTALL does: {@code [Li AID][00][La parameters]}. The application is in the
     * GlobalPlatform Registry in the state INSTALLED while its install method runs and in the given state
     * afterwards (GPCS v2.3.1 11.5.2.1).
     *
     * @param className  the applet class, loaded by this card's class loader
     * @param aid        the instance AID
     * @param parameters the application specific parameters ('C9' value)
     * @param state      the life cycle state after the installation: INSTALLED '03' or SELECTABLE '07'
     * @throws InstallException if the applet's install method fails; no instance is created and the AID stays free
     */
    public void install(String className, AID aid, byte[] parameters, int state) {
        byte[] bArray = AppletInstallParameters.encode(aid, parameters);
        javacard.framework.AID instance = AIDUtil.create(aid.toBytes());
        synchronized (runtime) {
            simulator.loadApplet(instance, load(className));
            globalPlatform.installStarted(aid, new byte[0]);
            try {
                // the runtime passes on an ISOException of the install method; the simulator would hide it
                runtime.installApplet(instance, bArray, (short) 0, (byte) bArray.length);
            } catch (ISOException | SystemException | IllegalArgumentException e) {
                globalPlatform.installFailed(aid);
                runtime.forgetLoadFile(instance);
                throw InstallException.onJCardSim(className, aid, parameters, e);
            }
            globalPlatform.installSucceeded(aid, state);
        }
    }

    /**
     * Selects an applet.
     *
     * @param aid the instance AID
     * @return the response (data and status word)
     */
    public byte[] select(AID aid) {
        return simulator.selectAppletWithResult(AIDUtil.create(aid.toBytes()));
    }

    /**
     * Deselects the selected applet by selecting the idle applet.
     */
    public void deselect() {
        simulator.selectAppletWithResult(AIDUtil.create(IDLE_AID));
    }

    /**
     * Sends a command to the selected applet (a SELECT by AID selects another one).
     *
     * @param command the command APDU
     * @return the response APDU
     */
    public byte[] transmit(byte[] command) {
        return simulator.transmitCommand(command);
    }

    /**
     * Resets the card: CLEAR_ON_RESET memory is cleared, no applet is selected, persistent state stays.
     */
    public void reset() {
        simulator.reset();
    }

    /**
     * Deletes an applet instance.
     *
     * @param aid the instance AID
     */
    public void delete(AID aid) {
        simulator.deleteApplet(AIDUtil.create(aid.toBytes()));
    }

    /** Discards the card and its classes. */
    @Override
    public void close() {
        simulator.resetRuntime();
        loader.closeQuietly();
    }


    /**
     * jCardSim's runtime, except that deleting an applet also forgets the load file jCardSim generated for its AID,
     * so that the AID can be installed again. The generated load file AIDs ({@code FFFFFF} and a counter) are never
     * reused: jCardSim numbers them by the count of generated load files, which a removal would make collide.
     *
     * <p>It gives its applets the GlobalPlatform environment of the card ({@link GlobalPlatformHost}) and reports
     * to it what ends a secure channel session (GPCS v2.3.1 10.2.3): the deselection of an applet, which jCardSim
     * also performs before it selects one, and a card reset.</p>
     */
    private static final class ReusableAidRuntime extends SimulatorRuntime implements GlobalPlatformHost {

        private final GlobalPlatformCard globalPlatform;
        private int generated;

        ReusableAidRuntime(GlobalPlatformCard globalPlatform) {
            this.globalPlatform = globalPlatform;
        }

        @Override
        public GlobalPlatformCard globalPlatform() {
            return globalPlatform;
        }

        @Override
        protected void deselect(ApplicationInstance applicationInstance) {
            try {
                super.deselect(applicationInstance);
            } finally {
                globalPlatform.applicationSessionEnded();
            }
        }

        @Override
        public void reset() {
            super.reset();
            globalPlatform.cardReset();
        }

        @Override
        public void loadApplet(javacard.framework.AID aid, Class<? extends Applet> appletClass) {
            if (generatedLoadFileAIDs.containsKey(aid)) {
                throw new SystemException(SystemException.ILLEGAL_AID);
            }
            int number = generated++;
            javacard.framework.AID loadFile = AIDUtil.create(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) (number >> 8), (byte) number});
            generatedLoadFileAIDs.put(aid, loadFile);
            loadLoadFile(new LoadFile(loadFile, loadFile, appletClass));
        }

        @Override
        protected void deleteApplet(javacard.framework.AID aid) {
            super.deleteApplet(aid);
            forgetLoadFile(aid);
            globalPlatform.unregister(GlobalPlatformCard.aidOf(aid));
        }

        /** Forgets the load file generated for an AID, so that the AID can be installed again. */
        void forgetLoadFile(javacard.framework.AID aid) {
            javacard.framework.AID loadFile = generatedLoadFileAIDs.remove(aid);
            if (loadFile != null) {
                loadFiles.remove(loadFile);
            }
        }
    }

    private Class<? extends Applet> load(String className) {
        try {
            return loader.loadClass(className).asSubclass(Applet.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(className + " is not in " + classPath, e);
        }
    }
}
