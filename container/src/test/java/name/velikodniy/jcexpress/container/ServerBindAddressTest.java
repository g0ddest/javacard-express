package name.velikodniy.jcexpress.container;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Listen address of the simulator server (audit finding "unauth-rce-bind-all-interfaces-root"): the server executes
 * the class files any client sends, so outside a container it must listen on the loopback interface unless told
 * otherwise. Container images set {@code JCX_BIND_ADDRESS=0.0.0.0}, because the port Docker publishes must reach it.
 */
class ServerBindAddressTest {

    @Test
    void listensOnLoopbackOnlyByDefault() {
        try (LocalSimulatorServer server = LocalSimulatorServer.start(Map.of(), List.of())) {
            String listening = "listening on 127.0.0.1:" + server.port();
            assertThat(server.awaitLog(listening)).contains(listening);
            nonLoopbackAddress().ifPresent(address -> assertThatThrownBy(() -> connect(address, server.port()))
                    .as("connection via %s", address)
                    .isInstanceOf(IOException.class));
        }
    }

    @Test
    void bindAddressCanBeSetThroughTheEnvironmentAsContainerImagesDo() {
        try (LocalSimulatorServer server = LocalSimulatorServer.start(Map.of("JCX_BIND_ADDRESS", "0.0.0.0"),
                List.of())) {
            String listening = "listening on 0.0.0.0:" + server.port();
            assertThat(server.awaitLog(listening)).contains(listening);
            assertThat(server.answersPing()).isTrue();
        }
    }

    private static void connect(InetAddress address, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 2_000);
        }
    }

    private static Optional<InetAddress> nonLoopbackAddress() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(ServerBindAddressTest::isUp)
                    .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                    .filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress())
                    .findFirst();
        } catch (SocketException e) {
            return Optional.empty();
        }
    }

    private static boolean isUp(NetworkInterface nic) {
        try {
            return nic.isUp() && !nic.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }
}
