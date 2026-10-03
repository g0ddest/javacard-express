package name.velikodniy.jcexpress.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import name.velikodniy.jcexpress.HelloWorldApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Security posture of the simulator container (audit finding "unauth-rce-bind-all-interfaces-root"): the server runs
 * class files sent by any client that reaches its port, so the container must not run it as root, must not keep
 * Linux capabilities, must publish the port on the loopback interface only when Docker runs on this machine, and with
 * {@link SmartCardContainer#withAccessToken()} (what {@code @SmartCard(mode = CONTAINER)} uses) serve only clients
 * that present the container's token.
 */
class ContainerHardeningTest {

    private static SmartCardContainer container;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        container = new SmartCardContainer().withAccessToken();
        container.start();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void serverRunsAsAnUnprivilegedUser() throws Exception {
        Container.ExecResult id = container.execInContainer("id", "-u");

        assertThat(id.getStdout().strip()).isEqualTo("65534");
        assertThat(container.getContainerInfo().getConfig().getUser()).isEqualTo(SmartCardContainer.SERVER_USER);
    }

    @Test
    void allCapabilitiesAreDroppedAndPrivilegeEscalationIsBlocked() {
        InspectContainerResponse info = container.getContainerInfo();

        assertThat(Arrays.asList(info.getHostConfig().getCapDrop())).contains(Capability.ALL);
        assertThat(info.getHostConfig().getSecurityOpts()).contains("no-new-privileges");
    }

    @Test
    void onlyClientsPresentingTheContainersAccessTokenAreServed() throws Exception {
        try (RawClient stranger = new RawClient(container.getHost(), container.getPort())) {
            RawClient.Reply reply = stranger.exchange(Protocol.CMD_PING, new byte[0]);

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Access token required");
        }
        try (ContainerSession session = new ContainerSession(container)) {
            session.install(HelloWorldApplet.class);
            assertThat(session.send(0x80, 0x01).dataAsString()).isEqualTo("Hello");
        }
    }

    @Test
    void portIsPublishedOnTheLoopbackInterfaceOnly() {
        assumeTrue(SmartCardContainer.isLocal(container.getHost()), "Docker runs on another machine");
        Ports.Binding[] bindings = container.getContainerInfo().getHostConfig().getPortBindings()
                .getBindings().get(ExposedPort.tcp(Protocol.PORT));

        assertThat(bindings).isNotEmpty().allSatisfy(binding -> assertThat(binding.getHostIp()).isEqualTo("127.0.0.1"));
    }
}
