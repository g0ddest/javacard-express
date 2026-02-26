package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import javacard.framework.Applet;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.util.HashMap;
import java.util.Map;

/**
 * Embedded smart card session backed by jCardSim (in-process simulation).
 *
 * <p>Each session creates its own {@link CardSimulator} instance,
 * providing complete isolation between tests.</p>
 */
public class EmbeddedSession implements SmartCardSession {

    private final CardSimulator simulator;
    private final int persistentMemory;
    private final Map<String, AID> classToAid = new HashMap<>();

    /**
     * Creates a new embedded session with default persistent memory (32KB).
     *
     * @param logging whether to log APDU exchanges
     */
    public EmbeddedSession(boolean logging) {
        this(logging, 32768);
    }

    /**
     * Creates a new embedded session.
     *
     * @param logging          whether to log APDU exchanges
     * @param persistentMemory EEPROM size in bytes
     */
    public EmbeddedSession(boolean logging, int persistentMemory) {
        this.persistentMemory = persistentMemory;
        this.simulator = new CardSimulator();
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        AID aid = AID.auto(appletClass);
        install(appletClass, aid);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        install(appletClass, aid, new byte[0]);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        if (installParams.length > 127) {
            throw new IllegalArgumentException(
                    "Install parameters too long: " + installParams.length + " bytes (max 127)");
        }
        classToAid.put(appletClass.getName(), aid);
        javacard.framework.AID jcAid = AIDUtil.create(aid.toBytes());
        simulator.installApplet(jcAid, appletClass, installParams, (short) 0, (byte) installParams.length);
        simulator.selectApplet(jcAid);
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        AID aid = classToAid.get(appletClass.getName());
        if (aid == null) {
            aid = AID.auto(appletClass);
        }
        select(aid);
    }

    @Override
    public void select(AID aid) {
        javacard.framework.AID jcAid = AIDUtil.create(aid.toBytes());
        simulator.selectApplet(jcAid);
    }

    @Override
    public void reset() {
        simulator.resetRuntime();
        classToAid.clear();
    }

    @Override
    public APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        CommandAPDU cmd;
        if (data != null && data.length > 0) {
            cmd = new CommandAPDU(cla, ins, p1, p2, data);
        } else {
            cmd = new CommandAPDU(cla, ins, p1, p2);
        }
        return doTransmit(cmd);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        CommandAPDU cmd;
        if (data != null && data.length > 0) {
            cmd = new CommandAPDU(cla, ins, p1, p2, data, le);
        } else {
            cmd = new CommandAPDU(cla, ins, p1, p2, le);
        }
        return doTransmit(cmd);
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        CommandAPDU cmd = new CommandAPDU(rawApdu);
        ResponseAPDU response = simulator.transmitCommand(cmd);
        return response.getBytes();
    }

    @Override
    public void close() {
        simulator.reset();
    }

    private APDUResponse doTransmit(CommandAPDU cmd) {
        ResponseAPDU response = simulator.transmitCommand(cmd);
        return new APDUResponse(response.getData(), response.getSW());
    }
}
