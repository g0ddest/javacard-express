package name.velikodniy.jcexpress.container;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Test client that speaks the simulator wire protocol byte by byte, so tests can send what
 * {@link ContainerSession} never would (bad lengths, truncated payloads, unknown commands, foreign classes).
 */
final class RawClient implements AutoCloseable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    RawClient(int port) throws IOException {
        this(InetAddress.getLoopbackAddress().getHostAddress(), port);
    }

    RawClient(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5_000);
        socket.setSoTimeout(10_000);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    /**
     * A reply frame.
     *
     * @param status  status byte
     * @param payload payload
     */
    record Reply(int status, byte[] payload) {
        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    Reply exchange(byte command, byte[] payload) throws IOException {
        send(command, payload.length, payload);
        return read();
    }

    /** Sends a frame header with an arbitrary declared length followed by the given bytes. */
    void send(byte command, int declaredLength, byte[] bytes) throws IOException {
        out.writeByte(command);
        out.writeInt(declaredLength);
        out.write(bytes);
        out.flush();
    }

    Reply read() throws IOException {
        int status = in.readUnsignedByte();
        int length = in.readInt();
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new Reply(status, payload);
    }

    /** @return whether the server closed the connection (read hits end of stream) */
    boolean closedByServer() {
        try {
            return in.read() < 0;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    /**
     * Encodes an INSTALL payload in the wire layout {@link ContainerSession} uses.
     *
     * @param aid         instance AID
     * @param appletClass binary name of the applet class
     * @param classes     class files by binary name; must contain {@code appletClass}
     * @param params      install parameters
     * @return the payload
     */
    static byte[] installPayload(byte[] aid, String appletClass, Map<String, byte[]> classes, byte[] params)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream p = new DataOutputStream(bytes);
        p.writeByte(aid.length);
        p.write(aid);
        writeClass(p, appletClass, classes.get(appletClass));
        p.writeInt(classes.size() - 1);
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            if (!e.getKey().equals(appletClass)) {
                writeClass(p, e.getKey(), e.getValue());
            }
        }
        p.writeShort(params.length);
        p.write(params);
        return bytes.toByteArray();
    }

    /**
     * Reads the class file of a test class from the test class path.
     *
     * @param type the class
     * @return its class file bytes
     */
    static byte[] classFile(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream is = type.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("No class file for " + type.getName());
            }
            return is.readAllBytes();
        }
    }

    private static void writeClass(DataOutputStream p, String name, byte[] classFile) throws IOException {
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        p.writeShort(n.length);
        p.write(n);
        p.writeInt(classFile.length);
        p.write(classFile);
    }
}
