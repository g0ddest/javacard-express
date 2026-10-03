package name.velikodniy.jcexpress;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.DockerClientFactory;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code @SmartCard(mode = CONTAINER)} with the default image works without a {@code docker/} directory or a
 * pre-built image (audit finding "annotation-container-mode-unusable-for-consumers"): the simulator comes from the
 * server bundled in the container artifact.
 */
@ExtendWith(JavaCardExtension.class)
class AnnotationContainerModeTest {

    @SmartCard(mode = Mode.CONTAINER)
    SmartCardSession card;

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
    }

    @Test
    void injectedContainerSessionRunsAnApplet() {
        card.install(HelloWorldApplet.class);

        assertThat(card.send(0x80, 0x01)).isSuccess().dataAsString().isEqualTo("Hello");
    }
}
