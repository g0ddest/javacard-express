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
 */
public final class JCardSimCard implements AutoCloseable {

    /** AID of the {@link IdleApplet}; it exists only inside the simulator. */
    static final String IDLE_AID = "A000000000FFFF";
    private final List<Path> classPath;
    private final AppletClassLoader loader;
    private final ReusableAidRuntime runtime = new ReusableAidRuntime();
    private final CardSimulator simulator = new CardSimulator(runtime);

    /**
     * Creates an empty card (only the idle applet is installed).
     *
     * @param classPath where the applet classes are loaded from (each card loads them itself)
     */
    public JCardSimCard(List<Path> classPath) {
        this.classPath = List.copyOf(classPath);
        this.loader = new AppletClassLoader(this.classPath, JCardSimCard.class.getClassLoader());
        simulator.installApplet(AIDUtil.create(IDLE_AID), IdleApplet.class);
    }

    /**
     * Installs an applet the way INSTALL [for install] does: {@code [Li AID][00][La parameters]}.
     *
     * @param className  the applet class, loaded by this card's class loader
     * @param aid        the instance AID
     * @param parameters the application specific parameters ('C9' value)
     * @throws InstallException if the applet's install method fails; no instance is created and the AID stays free
     */
    public void install(String className, AID aid, byte[] parameters) {
        byte[] bArray = AppletInstallParameters.encode(aid, parameters);
        javacard.framework.AID instance = AIDUtil.create(aid.toBytes());
        synchronized (runtime) {
            simulator.loadApplet(instance, load(className));
            try {
                // the runtime passes on an ISOException of the install method; the simulator would hide it
                runtime.installApplet(instance, bArray, (short) 0, (byte) bArray.length);
            } catch (ISOException | SystemException | IllegalArgumentException e) {
                runtime.forgetLoadFile(instance);
                throw InstallException.onJCardSim(className, aid, parameters, e);
            }
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
     */
    private static final class ReusableAidRuntime extends SimulatorRuntime {

        private int generated;

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
