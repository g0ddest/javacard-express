package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.HelloWorldApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression test for the published simulator image recipe ({@code docker/Dockerfile}).
 *
 * <p>The release workflow builds this Dockerfile with {@code docker/} as the build context and pushes it to
 * GHCR. Every image published up to 0.3.0 was empty because the {@code COPY} wildcard matched no file, so the
 * container exited immediately with "Unable to access jarfile server.jar"; it also ran the server as root. This test
 * builds the image exactly like the release does, starts it and runs a real session against it.</p>
 */
class DockerfileImageTest {

    private static final Path DOCKER_DIR =
            Paths.get(System.getProperty("user.dir")).resolve("../docker").normalize();
    private static final String TOKEN = "dockerfile-image-test-token";

    private static GenericContainer<?> container;

    @BeforeAll
    static void startImage() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        ImageFromDockerfile image = new ImageFromDockerfile("localhost/jcx-simulator-dockerfile-test", true)
                .withDockerfile(DOCKER_DIR.resolve("Dockerfile"));
        container = new GenericContainer<>(image)
                .withExposedPorts(Protocol.PORT)
                .withEnv(SmartCardContainer.TOKEN_ENV, TOKEN)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(90));
        container.start();
    }

    @AfterAll
    static void stopImage() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void imageRunsTheSimulatorServer() throws Exception {
        try (ContainerSession session = new ContainerSession(container.getHost(),
                container.getMappedPort(Protocol.PORT), null, ContainerSession.DEFAULT_TIMEOUT, TOKEN)) {
            session.install(HelloWorldApplet.class);
            assertThat(session.send(0x80, 0x01)).dataAsString().isEqualTo("Hello");
        }
    }

    @Test
    void serverRunsAsAnUnprivilegedUser() throws Exception {
        assertThat(container.execInContainer("id", "-u").getStdout().strip()).isEqualTo("65534");
    }

    @Test
    void serverHonoursTheAccessToken() throws Exception {
        try (RawClient stranger = new RawClient(container.getHost(), container.getMappedPort(Protocol.PORT))) {
            RawClient.Reply reply = stranger.exchange(Protocol.CMD_PING, new byte[0]);

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Access token required");
        }
    }
}
