package name.velikodniy.jcexpress.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Single-threaded TCP server for jCardSim.
 * One container = one session = one client at a time.
 */
public final class JcxServer {

    private static final Logger LOG = Logger.getLogger("jcx-server");

    public static void main(String[] args) throws IOException {
        int port = Protocol.PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        LOG.info("JCX Simulator starting on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("Listening on port " + port);

            while (true) {
                Socket client = serverSocket.accept();
                // Single-threaded: handle one client at a time
                new ClientHandler(client).run();
            }
        }
    }
}
