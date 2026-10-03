package name.velikodniy.jcexpress.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Server settings: {@code java -jar server.jar [port [bindAddress]]}, with environment fallbacks.
 *
 * <p>Trust model: the server executes the class files its clients send (that is its purpose). Outside a container
 * it therefore listens on the loopback interface unless told otherwise. Inside a container it must listen on all
 * interfaces of the container's network namespace, otherwise the port Docker publishes cannot reach it: the images
 * set {@value #BIND_ADDRESS_ENV}{@code =0.0.0.0}. When {@value #TOKEN_ENV} is set, a connection must authenticate
 * with that token ({@link Protocol#CMD_HELLO}) before anything else, so that other local users who can reach the
 * port cannot run code; the token is taken from the environment only, because command lines are visible to every
 * local user.</p>
 *
 * @param port        TCP port, {@code 0} = any free port (the bound port is logged)
 * @param bindAddress local address to listen on
 * @param maxSessions maximum number of simultaneously open client sessions
 * @param token       access token clients must present, or {@code null} for none
 */
record ServerConfig(int port, InetAddress bindAddress, int maxSessions, String token) {

    /** Environment variable with the listen address (used when no address argument is given). */
    static final String BIND_ADDRESS_ENV = "JCX_BIND_ADDRESS";
    /** Environment variable with the maximum number of concurrent sessions. */
    static final String MAX_SESSIONS_ENV = "JCX_MAX_SESSIONS";
    /** Environment variable with the access token (optional). */
    static final String TOKEN_ENV = "JCX_TOKEN";
    /** Default maximum number of concurrent sessions. */
    static final int DEFAULT_MAX_SESSIONS = 16;

    ServerConfig {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        if (maxSessions < 1) {
            throw new IllegalArgumentException("Max sessions must be at least 1: " + maxSessions);
        }
    }

    /**
     * Parses command-line arguments and environment.
     *
     * @param args command-line arguments: optional port, optional bind address
     * @param env  environment variables
     * @return the configuration
     * @throws UnknownHostException     if the bind address cannot be resolved
     * @throws IllegalArgumentException if a value is malformed or there are too many arguments
     */
    static ServerConfig parse(String[] args, Map<String, String> env) throws UnknownHostException {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: java -jar server.jar [port [bindAddress]]");
        }
        int port = args.length > 0 ? parseInt(args[0], "port") : Protocol.PORT;
        String address = args.length > 1 ? args[1] : env.get(BIND_ADDRESS_ENV);
        InetAddress bind = address == null || address.isBlank()
                ? InetAddress.getLoopbackAddress()
                : InetAddress.getByName(address.trim());
        String sessions = env.get(MAX_SESSIONS_ENV);
        int maxSessions = sessions == null || sessions.isBlank()
                ? DEFAULT_MAX_SESSIONS
                : parseInt(sessions, MAX_SESSIONS_ENV);
        String token = env.get(TOKEN_ENV);
        return new ServerConfig(port, bind, maxSessions, token == null || token.isBlank() ? null : token.trim());
    }

    /** Never prints the token. */
    @Override
    public String toString() {
        return "ServerConfig[port=" + port + ", bindAddress=" + bindAddress.getHostAddress() + ", maxSessions="
                + maxSessions + ", token=" + (token == null ? "none" : "set") + "]";
    }

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": '" + value + "'", e);
        }
    }
}
