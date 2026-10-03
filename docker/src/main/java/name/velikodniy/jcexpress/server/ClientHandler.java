package name.velikodniy.jcexpress.server;

import jdk.net.ExtendedSocketOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves one client connection: checks the access token if the server has one, then reads request frames, executes
 * them on the connection's own {@link SimulatorSession} and writes one reply per request.
 *
 * <p>No failure of a command can escape: every {@link Throwable} thrown while executing a command (including
 * {@link LinkageError}s such as {@link NoClassDefFoundError} or {@link UnsupportedClassVersionError} caused by the
 * shipped classes) is reported to the client as an error reply and the session continues. A framing error (invalid
 * payload length) is reported and then the connection is closed, because the byte stream can no longer be
 * interpreted.</p>
 */
final class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger("jcx-server");

    /** TCP keep-alive idle time (s) so that a vanished peer releases its session. */
    private static final int KEEPALIVE_IDLE_SECONDS = 60;

    /** Time a client has to authenticate when the server requires an access token. */
    private static final int AUTHENTICATION_TIMEOUT_MILLIS = 10_000;

    private final Socket socket;
    private final byte[] token;

    /**
     * Creates the handler.
     *
     * @param socket the accepted connection
     * @param token  access token the client must present first, or {@code null} for none
     */
    ClientHandler(Socket socket, String token) {
        this.socket = socket;
        this.token = token != null ? token.getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    public void run() {
        String peer = String.valueOf(socket.getRemoteSocketAddress());
        try (socket) {
            configure(socket);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            LOG.info("Client connected: " + peer);
            serve(in, out, new SimulatorSession());
            LOG.info("Client disconnected: " + peer);
        } catch (IOException e) {
            LOG.log(Level.INFO, "Connection " + peer + " closed: " + e);
        }
    }

    private void serve(DataInputStream in, DataOutputStream out, SimulatorSession session) throws IOException {
        if (token != null && !authenticate(in, out)) {
            return;
        }
        while (true) {
            Frames.Request request;
            try {
                request = Frames.readRequest(socket, in);
            } catch (ProtocolException e) {
                LOG.warning("Protocol error, closing connection: " + e.getMessage());
                Frames.writeError(out, ErrorReport.describe(IllegalArgumentException.class, e.getMessage()));
                return;
            }
            if (request == null) {
                return;
            }
            reply(out, session, request);
        }
    }

    /**
     * Checks that the first request is a {@link Protocol#CMD_HELLO} with the access token.
     *
     * @return whether the client may continue; otherwise an error reply has been sent
     */
    private boolean authenticate(DataInputStream in, DataOutputStream out) throws IOException {
        Frames.Request hello;
        try {
            // an unauthenticated connection must not hold a session slot for long
            socket.setSoTimeout(AUTHENTICATION_TIMEOUT_MILLIS);
            hello = Frames.readRequest(socket, in);
        } catch (ProtocolException e) {
            reject(out, e.getMessage());
            return false;
        }
        if (hello == null) {
            return false;
        }
        if (hello.command() != Protocol.CMD_HELLO) {
            reject(out, "Access token required: this simulator was started with " + ServerConfig.TOKEN_ENV
                    + "; send HELLO with the token first");
            return false;
        }
        if (!MessageDigest.isEqual(token, hello.payload())) {
            reject(out, "Wrong access token");
            return false;
        }
        Frames.writeOk(out, new byte[0]);
        return true;
    }

    private void reject(DataOutputStream out, String message) throws IOException {
        LOG.warning(message + "; closing connection " + socket.getRemoteSocketAddress());
        Frames.writeError(out, ErrorReport.describe(SecurityException.class, message));
    }

    private static void reply(DataOutputStream out, SimulatorSession session, Frames.Request request)
            throws IOException {
        session.takeMissingClasses(); // only lookups made by this command are relevant to its outcome
        byte[] result;
        try {
            result = session.execute(request.command(), request.payload());
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, Protocol.commandName(request.command()) + " failed", failure);
            Frames.writeError(out, ErrorReport.describe(failure, session.takeMissingClasses()));
            return;
        }
        Frames.writeOk(out, result);
    }

    private static void configure(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        if (socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
            socket.setOption(ExtendedSocketOptions.TCP_KEEPIDLE, KEEPALIVE_IDLE_SECONDS);
        }
    }
}
