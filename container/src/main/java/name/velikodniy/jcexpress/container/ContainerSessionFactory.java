package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates the {@link ContainerSession} for {@code @SmartCard(mode = CONTAINER)} (called reflectively by
 * {@code JavaCardExtension}).
 *
 * <p>Isolated in its own class to keep Testcontainers imports lazy: if a test never uses container mode, these
 * classes are never loaded. The simulator comes from {@link SimulatorSource#resolve}: by default the server bundled
 * in this artifact, so container mode works in any project.</p>
 */
public final class ContainerSessionFactory {

    private ContainerSessionFactory() {
    }

    /**
     * Starts a simulator container that requires a random access token and connects a session to it; closing the
     * session stops the container.
     *
     * @param annotation the field annotation
     * @return the connected session
     */
    public static SmartCardSession create(SmartCard annotation) {
        SmartCardContainer container = SimulatorSource.resolve(annotation.image(), simulatorProperties())
                .newContainer()
                .withAccessToken();
        try {
            container.start();
            return new ContainerSession(container.getHost(), container.getPort(), container,
                    ContainerSession.configuredTimeout(), container.accessToken());
        } catch (IOException e) {
            container.close();
            throw new UncheckedIOException("Failed to connect to the simulator container", e);
        } catch (RuntimeException e) {
            container.close();
            throw e;
        }
    }

    private static Map<String, String> simulatorProperties() {
        Map<String, String> properties = new HashMap<>();
        for (String key : new String[]{SimulatorSource.IMAGE_PROPERTY, SimulatorSource.DOCKER_DIR_PROPERTY}) {
            String value = System.getProperty(key);
            if (value != null) {
                properties.put(key, value);
            }
        }
        return properties;
    }
}
