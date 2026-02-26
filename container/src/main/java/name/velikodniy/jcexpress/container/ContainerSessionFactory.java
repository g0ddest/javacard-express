package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Factory that creates {@link ContainerSession} instances.
 * Isolated in its own class to keep Testcontainers imports lazy —
 * if the user never uses container mode, the classes are never loaded.
 */
public final class ContainerSessionFactory {

    private ContainerSessionFactory() {
    }

    public static SmartCardSession create(SmartCard annotation) {
        SmartCardContainer container;

        String image = annotation.image();
        if (image.isEmpty()) {
            // Build from local Dockerfile
            Path dockerDir = findDockerDir();
            container = new SmartCardContainer(dockerDir);
        } else {
            container = new SmartCardContainer(image);
        }

        container.start();

        try {
            return new ContainerSession(
                    container.getHost(),
                    container.getPort(),
                    container,
                    annotation.log()
            );
        } catch (IOException e) {
            container.close();
            throw new RuntimeException("Failed to connect to container", e);
        }
    }

    private static Path findDockerDir() {
        // Allow explicit override via system property
        String override = System.getProperty("jcx.docker.dir");
        if (override != null && !override.isEmpty()) {
            Path dir = Paths.get(override);
            if (dir.toFile().isDirectory()) {
                return dir;
            }
        }

        // Look for docker/ directory relative to working directory
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path dockerDir = cwd.resolve("docker");
        if (dockerDir.toFile().isDirectory()) {
            return dockerDir;
        }
        // Try parent (in case running from a subdirectory)
        if (cwd.getParent() != null) {
            dockerDir = cwd.getParent().resolve("docker");
            if (dockerDir.toFile().isDirectory()) {
                return dockerDir;
            }
        }
        throw new IllegalStateException(
                "Cannot find docker/ directory. Set -Djcx.docker.dir=<path>, " +
                "ensure it exists at project root, or specify a Docker image in @SmartCard(image = \"...\")");
    }
}
