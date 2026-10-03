package name.velikodniy.jcexpress.container;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * One TCP connection to the simulator server: request/reply framing with connect and read timeouts.
 *
 * <p>After any transport failure (timeout, broken connection, malformed reply) the connection is closed for good:
 * a late reply to a timed-out request would otherwise be taken as the reply to the next one.</p>
 */
final class SimulatorConnection implements Closeable {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String endpoint;
    private final Duration timeout;
    private volatile String closedReason;

    /**
     * A reply frame.
     *
     * @param status  {@link Protocol#STATUS_OK} or {@link Protocol#STATUS_ERROR}
     * @param payload the reply payload
     */
    record Reply(int status, byte[] payload) {
    }

    SimulatorConnection(String host, int port, Duration timeout) throws IOException {
        this.endpoint = host + ":" + port;
        this.timeout = timeout;
        this.socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(Math.toIntExact(Math.max(1, timeout.toMillis())));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Sends one request and waits for its reply.
     *
     * @param command the command code
     * @param payload the request payload
     * @return the reply
     * @throws IOException if the connection is closed or fails, or no reply arrives within the timeout
     */
    synchronized Reply call(byte command, byte[] payload) throws IOException {
        if (closedReason != null) {
            throw new IOException("The connection to the simulator at " + endpoint + " is closed (" + closedReason + ")");
        }
        try {
            out.writeByte(command);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
            return readReply();
        } catch (SocketTimeoutException e) {
            abort("no reply within " + describe(timeout));
            SocketTimeoutException timedOut = new SocketTimeoutException("No reply from the simulator at " + endpoint
                    + " within " + describe(timeout) + " (is the applet stuck in an endless loop?). The session is"
                    + " closed. For legitimately slower operations raise -D" + ContainerSession.TIMEOUT_PROPERTY
                    + "=<seconds>.");
            timedOut.initCause(e);
            throw timedOut;
        } catch (IOException e) {
            abort(e.toString());
            throw e;
        }
    }

    private Reply readReply() throws IOException {
        int status = in.readUnsignedByte();
        int length = in.readInt();
        if (length < 0 || length > Protocol.MAX_PAYLOAD) {
            throw new IOException("Invalid reply length " + length + " from the simulator at " + endpoint);
        }
        byte[] reply = new byte[length];
        in.readFully(reply);
        return new Reply(status, reply);
    }

    static String describe(Duration duration) {
        long millis = duration.toMillis();
        return millis % 1000 == 0 ? millis / 1000 + " s" : millis + " ms";
    }

    @Override
    public void close() {
        abort("closed by the client");
    }

    private void abort(String reason) {
        if (closedReason == null) {
            closedReason = reason;
        }
        try {
            socket.close();
        } catch (IOException e) {
            // nothing more to release
        }
    }
}
