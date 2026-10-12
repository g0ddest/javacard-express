package name.velikodniy.jcexpress.api.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates the class-file sets compared by the conformance tests: the stub classes produced by
 * this module, the jar of the reference implementation of the Java Card API (jCardSim, a test-scoped dependency,
 * unless {@value #REFERENCE_PROPERTY} names another jar) and, when {@value #GLOBALPLATFORM_REFERENCE_PROPERTY}
 * names one, a jar of the GlobalPlatform Card API.
 */
final class ApiLocations {

    /** System property set by the surefire configuration of this module. */
    static final String STUB_CLASSES_PROPERTY = "jcx.api.classes";

    /**
     * System property naming another implementation of the same API to compare against instead of jCardSim, for
     * example the API jar of a locally installed Java Card SDK:
     * {@code ./mvnw test -pl javacard-api -Djcx.api.reference=/path/to/api.jar}. That jar is only read as data.
     */
    static final String REFERENCE_PROPERTY = "jcx.api.reference";

    /**
     * System property naming a jar of the GlobalPlatform Card API (package {@code org.globalplatform}) to compare
     * the stubs of that package with, for example the API jar of GlobalPlatform's own release:
     * {@code ./mvnw test -pl javacard-api -Djcx.gpapi.reference=/path/to/gpapi-globalplatform.jar}. That jar is only
     * read as data. Nothing provides it by default: neither jCardSim nor this build contains the package.
     */
    static final String GLOBALPLATFORM_REFERENCE_PROPERTY = "jcx.gpapi.reference";

    /** A class that exists only in the jCardSim jar, used to find that jar on the test class path. */
    private static final String REFERENCE_MARKER = "com/licel/jcardsim/base/Simulator.class";

    private ApiLocations() {
    }

    /**
     * Returns the directory holding the compiled stub classes of this module.
     *
     * @return the stub class directory
     */
    static Path stubClasses() {
        String configured = System.getProperty(STUB_CLASSES_PROPERTY);
        Path path = configured != null ? Path.of(configured) : Path.of("target", "classes");
        if (!Files.isDirectory(path.resolve("javacard/framework"))) {
            throw new IllegalStateException("Stub classes not found in " + path.toAbsolutePath()
                    + "; build the module first or set -D" + STUB_CLASSES_PROPERTY);
        }
        return path;
    }

    /**
     * Returns the jar named by {@value #REFERENCE_PROPERTY}, or else the jCardSim jar found on the test class path.
     *
     * @return path of the reference jar
     */
    static Path referenceJar() {
        if (!isJcardsimReference()) {
            Path configured = Path.of(System.getProperty(REFERENCE_PROPERTY));
            if (!Files.isRegularFile(configured)) {
                throw new IllegalStateException(REFERENCE_PROPERTY + " does not name a jar: " + configured);
            }
            return configured;
        }
        URL marker = ApiLocations.class.getClassLoader().getResource(REFERENCE_MARKER);
        if (marker == null || !"jar".equals(marker.getProtocol())) {
            throw new IllegalStateException("jCardSim jar (com.klinec:jcardsim) is not on the test class path");
        }
        try {
            JarURLConnection connection = (JarURLConnection) marker.openConnection();
            return Path.of(connection.getJarFileURL().toURI());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the jar named by {@value #GLOBALPLATFORM_REFERENCE_PROPERTY}.
     *
     * @return the jar of the GlobalPlatform Card API to compare with, or empty when the property is not set
     * @throws IllegalStateException if the property names something that is not a file
     */
    static Optional<Path> globalPlatformReferenceJar() {
        String configured = System.getProperty(GLOBALPLATFORM_REFERENCE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path jar = Path.of(configured);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(GLOBALPLATFORM_REFERENCE_PROPERTY + " does not name a jar: " + jar);
        }
        return Optional.of(jar);
    }

    /**
     * Tells whether the reference is jCardSim, whose known deviations from the specification are corrected by
     * {@link ReferenceCorrections} before comparing.
     *
     * @return {@code true} unless {@value #REFERENCE_PROPERTY} is set
     */
    static boolean isJcardsimReference() {
        String configured = System.getProperty(REFERENCE_PROPERTY);
        return configured == null || configured.isBlank();
    }
}
