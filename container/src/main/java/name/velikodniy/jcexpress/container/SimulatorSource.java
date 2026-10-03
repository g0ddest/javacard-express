package name.velikodniy.jcexpress.container;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

/**
 * Where the simulator server of {@code @SmartCard(mode = CONTAINER)} comes from.
 *
 * <p>Resolution order ({@link #resolve}):</p>
 * <ol>
 *   <li>{@code @SmartCard(image = "...")}: run that pre-built image;</li>
 *   <li>{@code -Djcx.simulator.image=...}: run that pre-built image;</li>
 *   <li>{@code -Djcx.docker.dir=...}: build an image from the server jar of that {@code docker/} project
 *       (it must exist and be built, otherwise resolution fails with a message saying what is missing);</li>
 *   <li>otherwise the server jar bundled in this artifact: works in any project, no checkout of javacard-express,
 *       no {@code docker/} directory and no registry access to a simulator image needed.</li>
 * </ol>
 */
sealed interface SimulatorSource {

    /** System property naming a pre-built simulator image. */
    String IMAGE_PROPERTY = "jcx.simulator.image";

    /** System property pointing to a {@code docker/} project whose {@code target/} holds the built server jar. */
    String DOCKER_DIR_PROPERTY = "jcx.docker.dir";

    /**
     * Creates a (not yet started) container for this source.
     *
     * @return the container
     */
    SmartCardContainer newContainer();

    /**
     * Resolves the simulator source.
     *
     * @param annotationImage {@code SmartCard.image()}, empty when not set
     * @param properties      system properties ({@link #IMAGE_PROPERTY}, {@link #DOCKER_DIR_PROPERTY})
     * @return the source
     * @throws IllegalStateException if {@link #DOCKER_DIR_PROPERTY} is set but does not hold a built server jar
     */
    static SimulatorSource resolve(String annotationImage, Map<String, String> properties) {
        if (annotationImage != null && !annotationImage.isBlank()) {
            return new Image(annotationImage.trim());
        }
        String image = properties.get(IMAGE_PROPERTY);
        if (image != null && !image.isBlank()) {
            return new Image(image.trim());
        }
        String dockerDir = properties.get(DOCKER_DIR_PROPERTY);
        if (dockerDir != null && !dockerDir.isBlank()) {
            return DockerProject.of(Paths.get(dockerDir.trim()));
        }
        return new Bundled();
    }

    /**
     * The runnable server jar bundled in this artifact as a class path resource.
     */
    record Bundled() implements SimulatorSource {

        /** Class path resource (relative to this package) of the bundled server jar. */
        static final String RESOURCE = "jcx-simulator-server.jar";

        /**
         * Opens the bundled server jar.
         *
         * @return the jar contents
         * @throws IllegalStateException if the resource is missing (e.g. an IDE build that skipped Maven packaging)
         */
        static InputStream openServerJar() {
            InputStream in = SimulatorSource.class.getResourceAsStream(RESOURCE);
            if (in == null) {
                throw new IllegalStateException("The simulator server bundled with javacard-express-container ("
                        + SimulatorSource.class.getPackageName().replace('.', '/') + "/" + RESOURCE
                        + ") is not on the class path. When building javacard-express from source, run"
                        + " 'mvn -pl container process-classes'; or use a pre-built image via -D"
                        + IMAGE_PROPERTY + "=<image> or @SmartCard(image = \"...\").");
            }
            return in;
        }

        /**
         * Reads the bundled server jar.
         *
         * @return the jar bytes
         */
        static byte[] serverJar() {
            try (InputStream in = openServerJar()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the bundled simulator server", e);
            }
        }

        @Override
        public SmartCardContainer newContainer() {
            return new SmartCardContainer();
        }
    }

    /**
     * A pre-built simulator image (for example {@code ghcr.io/g0ddest/jcx-simulator:<version>}).
     *
     * @param name the image name
     */
    record Image(String name) implements SimulatorSource {

        /** Validates the name. */
        public Image {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public SmartCardContainer newContainer() {
            return new SmartCardContainer(name);
        }
    }

    /**
     * The server jar built by a {@code docker/} project ({@code cd docker && mvn package}).
     *
     * @param serverJar the built jar
     */
    record DockerProject(Path serverJar) implements SimulatorSource {

        /** Validates the path. */
        public DockerProject {
            Objects.requireNonNull(serverJar, "serverJar");
        }

        static DockerProject of(Path dockerDir) {
            if (!Files.isDirectory(dockerDir)) {
                throw new IllegalStateException("-D" + DOCKER_DIR_PROPERTY + "=" + dockerDir
                        + " is not a directory. Point it to the docker/ project of javacard-express, or unset it"
                        + " to use the simulator bundled with javacard-express-container.");
            }
            return new DockerProject(SmartCardContainer.findServerJar(dockerDir.resolve("target")));
        }

        @Override
        public SmartCardContainer newContainer() {
            return SmartCardContainer.forServerJar(serverJar);
        }
    }
}
