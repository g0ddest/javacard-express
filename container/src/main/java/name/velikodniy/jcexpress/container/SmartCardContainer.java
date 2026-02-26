package name.velikodniy.jcexpress.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Testcontainers wrapper for the jCardSim TCP server.
 */
public class SmartCardContainer extends GenericContainer<SmartCardContainer> {

    private static final int SERVER_PORT = Protocol.PORT;

    public SmartCardContainer(Path dockerDir) {
        super(buildImage(dockerDir));
        addExposedPort(SERVER_PORT);
        waitingFor(Wait.forListeningPort());
    }

    public SmartCardContainer(String imageName) {
        super(imageName);
        addExposedPort(SERVER_PORT);
        waitingFor(Wait.forListeningPort());
    }

    public String getHost() {
        return super.getHost();
    }

    public int getPort() {
        return getMappedPort(SERVER_PORT);
    }

    private static ImageFromDockerfile buildImage(Path dockerDir) {
        // Find the shaded JAR in docker/target
        Path targetDir = dockerDir.resolve("target");
        Path jarFile = findServerJar(targetDir);

        // Use a simple single-stage Dockerfile with the pre-built JAR
        return new ImageFromDockerfile("jcx-simulator", false)
                .withDockerfileFromBuilder(builder -> builder
                        .from("eclipse-temurin:17-jre")
                        .workDir("/app")
                        .copy("server.jar", "server.jar")
                        .expose(SERVER_PORT)
                        .entryPoint("java", "-jar", "server.jar")
                        .build())
                .withFileFromPath("server.jar", jarFile);
    }

    private static Path findServerJar(Path targetDir) {
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalStateException(
                    "docker/target directory not found. Build the server first: cd docker && mvn package");
        }
        try (Stream<Path> files = Files.list(targetDir)) {
            return files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("jcx-simulator-")
                                && name.endsWith(".jar")
                                && !name.startsWith("original-")
                                && !name.contains("-sources");
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Server JAR not found in " + targetDir + ". Build the server first: cd docker && mvn package"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
