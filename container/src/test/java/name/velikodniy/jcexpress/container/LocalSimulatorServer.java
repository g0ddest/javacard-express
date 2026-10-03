package name.velikodniy.jcexpress.container;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the simulator server jar bundled in this artifact as a separate JVM on 127.0.0.1.
 *
 * <p>Docker-free but faithful to the container: the server has its own class path (server + jCardSim only), so
 * applet classes are reachable only through the bytes {@link ContainerSession} ships, and a failure that escapes
 * the server terminates the process exactly as it would terminate the container.</p>
 */
final class LocalSimulatorServer implements AutoCloseable {

    private static Path serverJar;

    /** Startup line of the server, e.g. "JCX Simulator 0.3.0 listening on 127.0.0.1:50123 (...)". */
    private static final Pattern LISTENING = Pattern.compile("listening on (\\S+):(\\d+) ");

    private final Process process;
    private final String token;
    private final StringBuffer log = new StringBuffer();
    private volatile int port;

    private LocalSimulatorServer(Process process, String token) {
        this.process = process;
        this.token = token;
        Thread pump = new Thread(this::pumpOutput, "local-simulator-log");
        pump.setDaemon(true);
        pump.start();
    }

    /**
     * Starts a server with a small heap (so that unbounded allocations fail fast).
     *
     * @return the running server
     */
    static LocalSimulatorServer start() {
        return start(Map.of());
    }

    /**
     * Starts a server listening on 127.0.0.1 with extra environment variables.
     *
     * @param env environment for the server process
     * @return the running server
     */
    static LocalSimulatorServer start(Map<String, String> env) {
        return start(env, List.of("127.0.0.1"));
    }

    /**
     * Starts a server on a free port (port 0: the server picks one and logs it).
     *
     * @param env       environment for the server process (replaces inherited {@code JCX_*} variables)
     * @param arguments command-line arguments after the port (e.g. the bind address), may be empty
     * @return the running server
     */
    static LocalSimulatorServer start(Map<String, String> env, List<String> arguments) {
        try {
            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx128m",
                    "-jar", serverJar().toString(), "0"));
            command.addAll(arguments);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().keySet().removeIf(name -> name.startsWith("JCX_"));
            builder.environment().putAll(env);
            LocalSimulatorServer server = new LocalSimulatorServer(builder.start(), env.get("JCX_TOKEN"));
            server.awaitReady();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    int port() {
        return port;
    }

    /** @return whether the server process is still running */
    boolean isAlive() {
        return process.isAlive();
    }

    /** @return everything the server logged so far */
    String log() {
        return log.toString();
    }

    /**
     * Waits (up to 10 s) until the server output contains a text.
     *
     * @param fragment the text to wait for
     * @return the server output at that time (which does not contain the text after a timeout)
     */
    String awaitLog(String fragment) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!log.toString().contains(fragment) && System.nanoTime() < deadline) {
            sleep(50);
        }
        return log.toString();
    }

    /**
     * Opens a session with the default timeout (authenticated if the server has a {@code JCX_TOKEN}).
     *
     * @return a connected session (not owning the server)
     * @throws IOException if the connection fails
     */
    ContainerSession newSession() throws IOException {
        return new ContainerSession("127.0.0.1", port, null, ContainerSession.configuredTimeout(), token);
    }

    /**
     * Checks liveness the way a new client would: fresh connection, PING.
     *
     * @return whether the server answered
     */
    boolean answersPing() {
        try (RawClient client = new RawClient(port)) {
            if (token != null && client.exchange(Protocol.CMD_HELLO, token.getBytes(StandardCharsets.UTF_8))
                    .status() != 0) {
                return false;
            }
            RawClient.Reply reply = client.exchange(Protocol.CMD_PING, new byte[0]);
            return reply.status() == 0 && reply.payload().length == 1 && reply.payload()[0] == 1;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void close() {
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitReady() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Simulator server exited: " + log);
            }
            if (port == 0) {
                Matcher listening = LISTENING.matcher(log);
                if (listening.find()) {
                    port = Integer.parseInt(listening.group(2));
                }
            } else if (answersPing()) {
                return;
            }
            sleep(100);
        }
        close();
        throw new IllegalStateException("Simulator server did not start within 30 s: " + log);
    }

    private void pumpOutput() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append('\n');
            }
        } catch (IOException e) {
            log.append("[log pump stopped: ").append(e).append("]\n");
        }
    }

    private static synchronized Path serverJar() throws IOException {
        if (serverJar == null) {
            Path jar = Files.createTempFile("jcx-simulator-server", ".jar");
            jar.toFile().deleteOnExit();
            try (InputStream in = SimulatorSource.Bundled.openServerJar()) {
                Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
            }
            serverJar = jar;
        }
        return serverJar;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
