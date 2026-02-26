package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import javacard.framework.Applet;

import javax.smartcardio.CommandAPDU;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart card session backed by a remote jCardSim container over TCP.
 */
public class ContainerSession implements SmartCardSession {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final AutoCloseable container;
    private final Map<String, AID> classToAid = new HashMap<>();

    public ContainerSession(String host, int port, AutoCloseable container, boolean logging) throws IOException {
        this.container = container;
        this.socket = new Socket(host, port);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        install(appletClass, AID.auto(appletClass));
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        install(appletClass, aid, new byte[0]);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        classToAid.put(appletClass.getName(), aid);

        try {
            byte[] aidBytes = aid.toBytes();
            String className = appletClass.getName();
            byte[] classBytes = loadClassBytes(appletClass);

            // Find related classes (inner classes)
            List<String> extraNames = new ArrayList<>();
            List<byte[]> extraBytes = new ArrayList<>();
            findRelatedClasses(appletClass, extraNames, extraBytes);

            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            DataOutputStream payloadOut = new DataOutputStream(payload);

            // AID
            payloadOut.writeByte(aidBytes.length);
            payloadOut.write(aidBytes);

            // Main class name
            byte[] nameBytes = className.getBytes();
            payloadOut.writeShort(nameBytes.length);
            payloadOut.write(nameBytes);

            // Main class bytes
            payloadOut.writeInt(classBytes.length);
            payloadOut.write(classBytes);

            // Extra classes
            payloadOut.writeInt(extraNames.size());
            for (int i = 0; i < extraNames.size(); i++) {
                byte[] extraNameBytes = extraNames.get(i).getBytes();
                payloadOut.writeShort(extraNameBytes.length);
                payloadOut.write(extraNameBytes);
                payloadOut.writeInt(extraBytes.get(i).length);
                payloadOut.write(extraBytes.get(i));
            }

            // Install parameters
            payloadOut.writeShort(installParams.length);
            if (installParams.length > 0) {
                payloadOut.write(installParams);
            }

            payloadOut.flush();
            sendCommand(Protocol.CMD_INSTALL, payload.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to install applet", e);
        }
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
        try {
            sendCommand(Protocol.CMD_SELECT, aid.toBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to select applet", e);
        }
    }

    @Override
    public void reset() {
        try {
            sendCommand(Protocol.CMD_RESET, new byte[0]);
            classToAid.clear();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reset card", e);
        }
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
        try {
            return sendCommand(Protocol.CMD_TRANSMIT, rawApdu);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to transmit APDU", e);
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
        if (container != null) {
            try {
                container.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private APDUResponse doTransmit(CommandAPDU cmd) {
        byte[] responseBytes = transmit(cmd.getBytes());
        return new APDUResponse(responseBytes);
    }

    private synchronized byte[] sendCommand(byte cmd, byte[] payload) throws IOException {
        out.writeByte(cmd);
        out.writeInt(payload.length);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();

        byte status = in.readByte();
        int responseLen = in.readInt();
        byte[] response = new byte[responseLen];
        if (responseLen > 0) {
            in.readFully(response);
        }

        if (status != Protocol.STATUS_OK) {
            throw new IOException("Server error: " + new String(response));
        }
        return response;
    }

    private byte[] loadClassBytes(Class<?> clazz) throws IOException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IOException("Class resource not found: " + resourceName);
            }
            return is.readAllBytes();
        }
    }

    private void findRelatedClasses(Class<?> mainClass,
                                    List<String> names, List<byte[]> bytes) throws IOException {
        // Load declared inner/nested classes
        for (Class<?> inner : mainClass.getDeclaredClasses()) {
            names.add(inner.getName());
            bytes.add(loadClassBytes(inner));
        }
    }
}
