package name.velikodniy.jcexpress.server;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP server that runs Java Card applets in jCardSim for {@code ContainerSession} clients.
 *
 * <p>Usage: {@code java -jar server.jar [port [bindAddress]]} (see {@link ServerConfig}). Every connection is
 * served on its own thread with its own simulated card, so a slow, idle or stuck client (for example an applet in an
 * endless loop) never blocks other clients. At most {@link ServerConfig#maxSessions()} connections are served at
 * once; a further client waits up to two seconds for a free slot and then gets an error reply. Nothing a client sends
 * can terminate the accept loop.</p>
 */
public final class JcxServer implements Closeable {

    private static final Logger LOG = Logger.getLogger("jcx-server");

    /** How long a rejected client may take to send its first request before it is disconnected. */
    private static final int REJECT_DRAIN_MILLIS = 5_000;

    /**
     * How long a new client waits for a free session slot before it is rejected (a client that just disconnected
     * frees its slot only once the server has noticed the end of its stream).
     */
    private static final long ADMISSION_WAIT_MILLIS = 2_000;

    private final ServerSocket serverSocket;
    private final Semaphore sessions;
    private final int maxSessions;
    private final String token;
    private final AtomicInteger connectionCount = new AtomicInteger();

    private JcxServer(ServerSocket serverSocket, ServerConfig config) {
        this.serverSocket = serverSocket;
        this.maxSessions = config.maxSessions();
        this.sessions = new Semaphore(maxSessions);
        this.token = config.token();
    }

    /**
     * Entry point.
     *
     * @param args optional port and bind address
     * @throws IOException if the server socket cannot be opened
     */
    public static void main(String[] args) throws IOException {
        ServerConfig config = ServerConfig.parse(args, System.getenv());
        try (JcxServer server = start(config)) {
            server.serve();
        }
    }

    /**
     * Opens the server socket.
     *
     * @param config the configuration
     * @return the server, ready for {@link #serve()}
     * @throws IOException if the socket cannot be bound
     */
    static JcxServer start(ServerConfig config) throws IOException {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.bindAddress(), config.port()), 50);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        JcxServer server = new JcxServer(socket, config);
        LOG.info("JCX Simulator " + version() + " listening on " + config.bindAddress().getHostAddress() + ":"
                + socket.getLocalPort() + " (max " + config.maxSessions() + " sessions, "
                + (config.token() == null ? "no access token" : "access token required") + ")");
        return server;
    }

    /**
     * Accepts clients until the server is closed.
     */
    void serve() {
        while (!serverSocket.isClosed()) {
            Socket client;
            try {
                client = serverSocket.accept();
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    return;
                }
                LOG.log(Level.WARNING, "accept() failed", e);
                pause();
                continue;
            }
            dispatch(client);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the bound port (useful when started on port 0).
     *
     * @return the local port
     */
    int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    private void dispatch(Socket client) {
        String name = "jcx-session-" + connectionCount.incrementAndGet();
        try {
            Thread thread = new Thread(() -> admit(client), name);
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Cannot start " + name, t);
            closeQuietly(client);
        }
    }

    /** Serves the client once a session slot is free, or rejects it if none frees up in time. */
    private void admit(Socket client) {
        boolean admitted;
        try {
            admitted = sessions.tryAcquire(ADMISSION_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            admitted = false;
        }
        if (admitted) {
            runSession(client);
        } else {
            reject(client);
        }
    }

    private void runSession(Socket client) {
        try {
            new ClientHandler(client, token).run();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Session crashed", t);
        } finally {
            closeQuietly(client);
            sessions.release();
        }
    }

    /** Answers a client over the session limit with an error reply (its first request is drained, not run). */
    private void reject(Socket client) {
        String message = "Simulator busy: " + maxSessions + " sessions are open (limit "
                + ServerConfig.MAX_SESSIONS_ENV + "=" + maxSessions + ")";
        LOG.warning(message + "; rejecting " + client.getRemoteSocketAddress());
        try (client) {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
            Frames.writeError(out, ErrorReport.describe(IllegalStateException.class, message));
            client.shutdownOutput();
            client.setSoTimeout(REJECT_DRAIN_MILLIS);
            InputStream in = client.getInputStream();
            byte[] sink = new byte[8192];
            while (in.read(sink) >= 0) {
                // drain until the client closes, so that closing does not reset the connection before it read
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Rejected client gone", e);
        }
    }

    private static String version() {
        String version = JcxServer.class.getPackage().getImplementationVersion();
        return version != null ? version : "(development build)";
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "close failed", e);
        }
    }
}
