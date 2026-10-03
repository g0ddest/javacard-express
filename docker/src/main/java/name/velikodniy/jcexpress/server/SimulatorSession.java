package name.velikodniy.jcexpress.server;

import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;

import javax.smartcardio.CommandAPDU;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The card of one client connection: a jCardSim simulator plus the class loader for the applet classes the
 * client shipped.
 *
 * <p>Every session owns a private {@link SimulatorRuntime}, so concurrent sessions never see each other's applets.
 * jCardSim activates a runtime per call through a thread-local, so sessions served on different threads are
 * independent.</p>
 */
final class SimulatorSession {

    private static final Logger LOG = Logger.getLogger("jcx-server");

    private final SimulatorRuntime runtime = new SimulatorRuntime();
    private final CardSimulator simulator = new CardSimulator(runtime);
    private final ByteClassLoader classLoader = new ByteClassLoader(SimulatorSession.class.getClassLoader());

    /**
     * Executes one protocol command.
     *
     * @param command the command code
     * @param payload the request payload
     * @return the reply payload
     * @throws IllegalArgumentException for an unknown command or a malformed request
     * @throws IllegalStateException    when installing under an AID that is already in use
     */
    byte[] execute(byte command, byte[] payload) {
        switch (command) {
            case Protocol.CMD_INSTALL:
                return install(InstallRequest.parse(payload));
            case Protocol.CMD_SELECT:
                return simulator.selectAppletWithResult(AIDUtil.create(payload));
            case Protocol.CMD_TRANSMIT:
                return simulator.transmitCommand(new CommandAPDU(payload)).getBytes();
            case Protocol.CMD_RESET:
                simulator.resetRuntime();
                return new byte[0];
            case Protocol.CMD_CARD_RESET:
                simulator.reset();
                return new byte[0];
            case Protocol.CMD_PING:
                return new byte[]{0x01};
            case Protocol.CMD_HELLO:
                return new byte[0]; // already authenticated, or the server requires no token
            default:
                throw new IllegalArgumentException("Unknown command " + Protocol.commandName(command));
        }
    }

    /**
     * Returns and forgets the classes the applet code looked up but the client did not send.
     *
     * @return binary class names
     */
    Set<String> takeMissingClasses() {
        return classLoader.takeMissingClasses();
    }

    private byte[] install(InstallRequest request) {
        request.classes().forEach(classLoader::addClass);
        Class<? extends Applet> appletClass = appletClass(request.appletClass());
        AID aid = AIDUtil.create(request.aid());
        if (runtime.lookupApplet(aid) != null) {
            throw new IllegalStateException("An applet with AID " + AIDUtil.toString(aid) + " is already installed"
                    + " on this card (a card reset keeps installed applets; a new session starts with a blank card)");
        }
        byte[] bArray = request.installParams();
        // InstallRequest guarantees bArray.length <= 127, so the cast to the byte bLength is lossless.
        simulator.installApplet(aid, appletClass, bArray, (short) 0, (byte) bArray.length);
        LOG.info("Installed " + request.appletClass() + " as " + AIDUtil.toString(aid));
        return simulator.selectAppletWithResult(aid);
    }

    private Class<? extends Applet> appletClass(String name) {
        Class<?> loaded;
        try {
            loaded = classLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Applet class " + name + " was not sent by the client", e);
        }
        if (!Applet.class.isAssignableFrom(loaded)) {
            throw new IllegalArgumentException(name + " does not extend javacard.framework.Applet");
        }
        return loaded.asSubclass(Applet.class);
    }
}
