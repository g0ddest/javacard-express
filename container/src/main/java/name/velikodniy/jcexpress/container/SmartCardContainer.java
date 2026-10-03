package name.velikodniy.jcexpress.container;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Testcontainers container running the jCardSim simulator server.
 *
 * <p>Images built by this class (from the server jar bundled in this artifact, or from a {@code docker/} project)
 * start the server as an unprivileged user ({@value #SERVER_USER}, i.e. nobody). Every container gets all Linux
 * capabilities dropped and {@code no-new-privileges}. When Docker runs on this machine, the server port is
 * published on 127.0.0.1 only.</p>
 *
 * <p>Trust model: the server executes whatever class files its clients send. Without an access token anyone who
 * can reach the published port can run code inside the container; {@link #withAccessToken()} (always used by
 * {@code @SmartCard(mode = CONTAINER)}) restricts that to clients that know the token. The measures above keep the
 * port local and the code unprivileged, but the container is a test fixture, not a security boundary: do not run
 * applets you do not trust.</p>
 */
public class SmartCardContainer extends GenericContainer<SmartCardContainer> {

    /** System property overriding the base image of images built by this class. */
    public static final String BASE_IMAGE_PROPERTY = "jcx.simulator.baseImage";

    /** Unprivileged user (nobody:nogroup) the server runs as in images built by this class. */
    static final String SERVER_USER = "65534:65534";

    /** Environment variable through which the server receives its access token. */
    static final String TOKEN_ENV = "JCX_TOKEN";

    private static final int SERVER_PORT = Protocol.PORT;
    private static final int MIN_JAVA_FEATURE = 25;
    private static final int TOKEN_BYTES = 16;

    private String accessToken;

    /**
     * Creates a container running the simulator server bundled in this artifact. Works in any project: no
     * {@code docker/} directory and no pre-built image are needed (only Docker and the JRE base image).
     */
    public SmartCardContainer() {
        this(simulatorImage(SimulatorSource.Bundled.serverJar()));
    }

    /**
     * Creates a container from the server jar built by a {@code docker/} project
     * ({@code cd docker && mvn package}).
     *
     * @param dockerDir the {@code docker/} project directory
     * @throws IllegalStateException if {@code dockerDir/target} holds no built server jar
     */
    public SmartCardContainer(Path dockerDir) {
        this(simulatorImage(readJar(findServerJar(dockerDir.resolve("target")))));
    }

    /**
     * Creates a container from a pre-built simulator image.
     *
     * @param imageName the image name, e.g. {@code ghcr.io/g0ddest/jcx-simulator:<version>}
     */
    public SmartCardContainer(String imageName) {
        super(imageName);
        init();
    }

    private SmartCardContainer(ImageFromDockerfile image) {
        super(image);
        init();
    }

    static SmartCardContainer forServerJar(Path serverJar) {
        return new SmartCardContainer(simulatorImage(readJar(serverJar)));
    }

    /**
     * Requires clients to present a random access token, generated for this container and handed to the server in
     * the {@value #TOKEN_ENV} environment variable, before they may send commands. Other local users who can reach
     * the published port can then not run code in the container (the token is visible only to those who can inspect
     * the container, i.e. who control Docker anyway). Connect with {@link ContainerSession#ContainerSession(
     * SmartCardContainer)}; {@code @SmartCard(mode = CONTAINER)} always does this. Call before {@link #start()}.
     *
     * @return this container
     */
    public SmartCardContainer withAccessToken() {
        byte[] random = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(random);
        accessToken = HexFormat.of().formatHex(random);
        return withEnv(TOKEN_ENV, accessToken);
    }

    /**
     * Returns the access token clients must present.
     *
     * @return the token, or {@code null} if the container does not require one
     */
    String accessToken() {
        return accessToken;
    }

    /**
     * Returns the host to connect to.
     *
     * @return the Docker host address
     */
    @Override
    public String getHost() {
        return super.getHost();
    }

    /**
     * Returns the host port mapped to the server port.
     *
     * @return the mapped port
     */
    public int getPort() {
        return getMappedPort(SERVER_PORT);
    }

    private void init() {
        addExposedPort(SERVER_PORT);
        waitingFor(Wait.forListeningPort());
        withCreateContainerCmdModifier(SmartCardContainer::harden);
    }

    /** Publishes the port on the loopback interface only, unless Docker runs on another machine. */
    @Override
    protected void configure() {
        super.configure();
        if (isLocal(getHost())) {
            setPortBindings(List.of("127.0.0.1::" + SERVER_PORT));
        }
    }

    static void harden(CreateContainerCmd cmd) {
        HostConfig hostConfig = cmd.getHostConfig() != null ? cmd.getHostConfig() : HostConfig.newHostConfig();
        cmd.withHostConfig(hostConfig.withCapDrop(Capability.ALL).withSecurityOpts(List.of("no-new-privileges")));
    }

    static boolean isLocal(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Builds the simulator image recipe: JRE base image, the server jar, unprivileged user, listening on all
     * interfaces of the container (required for the published port to reach it).
     */
    static ImageFromDockerfile simulatorImage(byte[] serverJar) {
        String baseImage = baseImage(System.getProperty(BASE_IMAGE_PROPERTY), Runtime.version().feature());
        return new ImageFromDockerfile("localhost/jcx-simulator:" + contentTag(serverJar, baseImage), false)
                .withDockerfileFromBuilder(builder -> builder
                        .from(baseImage)
                        .workDir("/app")
                        .copy("server.jar", "/app/server.jar")
                        .env("JCX_BIND_ADDRESS", "0.0.0.0")
                        .user(SERVER_USER)
                        .expose(SERVER_PORT)
                        .entryPoint("java", "-jar", "/app/server.jar")
                        .build())
                .withFileFromTransferable("server.jar", Transferable.of(serverJar, 0644));
    }

    /**
     * Chooses the JRE base image: the applet classes are compiled for the test JVM, so the server JVM must be at
     * least that version (and at least {@value #MIN_JAVA_FEATURE}, the version the project targets).
     *
     * @param override    value of {@link #BASE_IMAGE_PROPERTY}, may be null
     * @param testFeature feature version of the test JVM
     * @return the base image
     */
    static String baseImage(String override, int testFeature) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return "eclipse-temurin:" + Math.max(MIN_JAVA_FEATURE, testFeature) + "-jre";
    }

    private static String contentTag(byte[] serverJar, String baseImage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(serverJar);
            digest.update(baseImage.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readJar(Path jar) {
        try {
            return Files.readAllBytes(jar);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the simulator server jar " + jar, e);
        }
    }

    static Path findServerJar(Path targetDir) {
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalStateException(targetDir + " not found: the simulator server is not built."
                    + " Build it first: cd " + targetDir.getParent() + " && mvn package");
        }
        try (Stream<Path> files = Files.list(targetDir)) {
            return files
                    .filter(p -> isServerJarName(p.getFileName().toString()))
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No server jar (jcx-simulator*.jar) in " + targetDir
                            + ". Build the server first: cd " + targetDir.getParent() + " && mvn package"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Recognises the shaded server jar of the {@code docker/} project: {@code jcx-simulator.jar} (the fixed final
     * name of current builds) or {@code jcx-simulator-<version>.jar} (older builds), but not the shade plugin's
     * {@code original-*} input jar or attached {@code -sources}/{@code -javadoc} jars.
     */
    static boolean isServerJarName(String name) {
        if (!name.endsWith(".jar") || name.startsWith("original-")
                || name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar")) {
            return false;
        }
        return name.equals("jcx-simulator.jar") || name.startsWith("jcx-simulator-");
    }
}
