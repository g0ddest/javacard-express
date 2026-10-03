package name.velikodniy.jcexpress.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Reading and writing of protocol frames (see {@link Protocol}).
 */
final class Frames {

    /**
     * Once the first byte of a request has arrived, the rest of the frame must follow within this time; a client
     * that stalls in the middle of a frame is disconnected instead of holding its session thread forever.
     */
    static final int FRAME_TIMEOUT_MILLIS = 30_000;

    private Frames() {
    }

    /**
     * A request frame.
     *
     * @param command the command code
     * @param payload the payload
     */
    record Request(byte command, byte[] payload) {
    }

    /**
     * Reads the next request. Waiting for the first byte has no time limit (an idle session is legitimate);
     * the remainder of the frame is read with {@link #FRAME_TIMEOUT_MILLIS}.
     *
     * @param socket the connection (for the read timeout)
     * @param in     the buffered input of the connection
     * @return the request, or {@code null} at end of stream
     * @throws ProtocolException if the payload length is outside {@code 0..MAX_PAYLOAD}
     * @throws IOException       on I/O errors, a truncated frame or a frame timeout
     */
    static Request readRequest(Socket socket, DataInputStream in) throws IOException {
        int command = in.read();
        if (command < 0) {
            return null;
        }
        socket.setSoTimeout(FRAME_TIMEOUT_MILLIS);
        try {
            int length = in.readInt();
            if (length < 0 || length > Protocol.MAX_PAYLOAD) {
                throw new ProtocolException("Invalid payload length " + length + " for command "
                        + Protocol.commandName(command) + " (allowed 0.." + Protocol.MAX_PAYLOAD + ")");
            }
            byte[] payload = new byte[length];
            in.readFully(payload);
            return new Request((byte) command, payload);
        } finally {
            socket.setSoTimeout(0);
        }
    }

    /**
     * Writes a success reply.
     *
     * @param out     the buffered output of the connection
     * @param payload the reply payload
     * @throws IOException on I/O errors
     */
    static void writeOk(DataOutputStream out, byte[] payload) throws IOException {
        write(out, Protocol.STATUS_OK, payload);
    }

    /**
     * Writes an error reply.
     *
     * @param out    the buffered output of the connection
     * @param report the error report text (see {@link ErrorReport})
     * @throws IOException on I/O errors
     */
    static void writeError(DataOutputStream out, String report) throws IOException {
        write(out, Protocol.STATUS_ERROR, report.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(DataOutputStream out, byte status, byte[] payload) throws IOException {
        out.writeByte(status);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }
}
