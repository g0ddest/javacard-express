package name.velikodniy.jcexpress.server;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.Applet;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles a single client connection, processing protocol commands.
 */
final class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger("jcx-server");

    private final Socket socket;

    ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket;
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            CardSimulator simulator = new CardSimulator();
            ByteClassLoader classLoader = new ByteClassLoader(getClass().getClassLoader());

            LOG.info("Client connected: " + socket.getRemoteSocketAddress());

            while (!socket.isClosed()) {
                byte cmd;
                try {
                    cmd = in.readByte();
                } catch (EOFException e) {
                    break;
                }

                int payloadLen = in.readInt();
                byte[] payload = new byte[payloadLen];
                if (payloadLen > 0) {
                    in.readFully(payload);
                }

                try {
                    byte[] response = handleCommand(cmd, payload, simulator, classLoader);
                    out.writeByte(Protocol.STATUS_OK);
                    out.writeInt(response.length);
                    if (response.length > 0) {
                        out.write(response);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Command failed", e);
                    byte[] errorMsg = e.getMessage() != null
                            ? e.getMessage().getBytes()
                            : "Unknown error".getBytes();
                    out.writeByte(Protocol.STATUS_ERROR);
                    out.writeInt(errorMsg.length);
                    out.write(errorMsg);
                }
                out.flush();
            }

            LOG.info("Client disconnected");

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Connection error", e);
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] handleCommand(byte cmd, byte[] payload,
                                 CardSimulator simulator,
                                 ByteClassLoader classLoader) throws Exception {
        switch (cmd) {
            case Protocol.CMD_INSTALL: {
                return handleInstall(payload, simulator, classLoader);
            }

            case Protocol.CMD_SELECT: {
                javacard.framework.AID jcAid = AIDUtil.create(payload);
                simulator.selectApplet(jcAid);
                LOG.info("Selected applet");
                return new byte[0];
            }

            case Protocol.CMD_TRANSMIT: {
                CommandAPDU cmdApdu = new CommandAPDU(payload);
                ResponseAPDU response = simulator.transmitCommand(cmdApdu);
                return response.getBytes();
            }

            case Protocol.CMD_RESET: {
                simulator.resetRuntime();
                LOG.info("Card reset");
                return new byte[0];
            }

            case Protocol.CMD_PING: {
                return new byte[]{0x01};
            }

            default:
                throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] handleInstall(byte[] payload, CardSimulator simulator,
                                 ByteClassLoader classLoader) throws Exception {
        int offset = 0;

        // AID
        int aidLen = payload[offset++] & 0xFF;
        byte[] aidBytes = new byte[aidLen];
        System.arraycopy(payload, offset, aidBytes, 0, aidLen);
        offset += aidLen;

        // Main class name
        int nameLen = ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
        offset += 2;
        String className = new String(payload, offset, nameLen);
        offset += nameLen;

        // Main class bytes
        int classLen = readInt(payload, offset);
        offset += 4;
        byte[] classBytes = new byte[classLen];
        System.arraycopy(payload, offset, classBytes, 0, classLen);
        offset += classLen;

        // Extra classes (inner classes, dependencies)
        int numExtra = readInt(payload, offset);
        offset += 4;

        classLoader.addClass(className, classBytes);
        for (int i = 0; i < numExtra; i++) {
            int extraNameLen = ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
            offset += 2;
            String extraName = new String(payload, offset, extraNameLen);
            offset += extraNameLen;

            int extraLen = readInt(payload, offset);
            offset += 4;
            byte[] extraBytes = new byte[extraLen];
            System.arraycopy(payload, offset, extraBytes, 0, extraLen);
            offset += extraLen;

            classLoader.addClass(extraName, extraBytes);
        }

        // Install parameters (optional — may be absent for older clients)
        byte[] installParams = new byte[0];
        if (offset < payload.length) {
            int paramsLen = ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
            offset += 2;
            if (paramsLen > 0) {
                installParams = new byte[paramsLen];
                System.arraycopy(payload, offset, installParams, 0, paramsLen);
                offset += paramsLen;
            }
        }

        Class<?> loaded = classLoader.loadClass(className);
        Class<? extends Applet> appletClass = loaded.asSubclass(Applet.class);

        javacard.framework.AID jcAid = AIDUtil.create(aidBytes);
        simulator.installApplet(jcAid, appletClass, installParams, (short) 0, (byte) installParams.length);
        simulator.selectApplet(jcAid);

        LOG.info("Installed " + className);
        return new byte[0];
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
