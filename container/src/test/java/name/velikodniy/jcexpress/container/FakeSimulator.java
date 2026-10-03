package name.velikodniy.jcexpress.container;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * In-process stand-in for the simulator server that records requests and answers with scripted replies, for
 * client-side tests that need full control over the bytes on the wire (no JVM spawn, no jCardSim).
 */
final class FakeSimulator implements AutoCloseable {

    /**
     * A request received by the fake.
     *
     * @param command command code
     * @param payload payload
     */
    record Request(byte command, byte[] payload) {
    }

    private final ServerSocket serverSocket;
    private final Function<Request, RawClient.Reply> responder;
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    /**
     * Starts the fake.
     *
     * @param responder computes the reply of each request; returning {@code null} sends no reply at all
     */
    FakeSimulator(Function<Request, RawClient.Reply> responder) throws IOException {
        this.responder = responder;
        this.serverSocket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "fake-simulator");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** @return a responder answering every request with STATUS_OK and the given payload */
    static Function<Request, RawClient.Reply> okWith(byte[] payload) {
        return request -> new RawClient.Reply(0, payload);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    List<Request> requests() {
        return requests;
    }

    ContainerSession newSession() throws IOException {
        return new ContainerSession("127.0.0.1", port(), null);
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Thread handler = new Thread(() -> serve(socket), "fake-simulator-session");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            while (true) {
                int command = in.read();
                if (command < 0) {
                    return;
                }
                byte[] payload = new byte[in.readInt()];
                in.readFully(payload);
                Request request = new Request((byte) command, payload);
                requests.add(request);
                RawClient.Reply reply = responder.apply(request);
                if (reply != null) {
                    out.writeByte(reply.status());
                    out.writeInt(reply.payload().length);
                    out.write(reply.payload());
                    out.flush();
                }
            }
        } catch (IOException e) {
            // connection closed by the client
        }
    }
}
