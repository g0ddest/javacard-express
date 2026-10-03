package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.base.SimulatorRuntime;

import java.net.URL;
import java.security.CodeSource;
import java.util.Objects;

/**
 * Checks, before a jCardSim runtime is created, that the Java Card API classes on the test runtime
 * classpath are jCardSim's own implementation.
 *
 * <p>jCardSim ships the {@code javacard.framework} classes in its jar and drives them by reflection.
 * When compile-only API stubs (for example {@code javacard-express-api}) come first on the classpath,
 * the stub classes are loaded instead and jCardSim fails later with an opaque
 * "Internal reflection error". This check reports the cause and the fix up front.</p>
 *
 * <p>The check compares the code source of {@code javacard.framework.APDU} with that of
 * {@code com.licel.jcardsim.base.SimulatorRuntime}; class literals are used, so no class is initialised.
 * Setups that deliberately take the API classes from another location can switch the check off with the
 * system property {@value #DISABLE_PROPERTY}{@code =false}.</p>
 */
final class SimulatorClasspath {

    /** System property that disables the check when set to {@code false}. */
    static final String DISABLE_PROPERTY = "jcx.embedded.classpathCheck";

    private SimulatorClasspath() {
    }

    /**
     * Verifies the runtime classpath.
     *
     * @throws IllegalStateException if {@code javacard.framework.APDU} is not loaded from the jCardSim jar
     */
    static void verify() {
        if ("false".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY))) {
            return;
        }
        verify(location(javacard.framework.APDU.class), location(SimulatorRuntime.class));
    }

    /**
     * Verifies that the Java Card API location is the simulator location.
     *
     * @param api       where {@code javacard.framework.APDU} was loaded from, or {@code null} if unknown
     * @param simulator where the jCardSim runtime was loaded from, or {@code null} if unknown
     * @throws IllegalStateException if the two locations differ
     */
    static void verify(URL api, URL simulator) {
        if (api != null && simulator != null && sameLocation(api, simulator)) {
            return;
        }
        throw new IllegalStateException("javacard.framework.APDU was loaded from " + api
                + ", not from jCardSim (" + simulator + "). The Java Card API classes must come from jCardSim"
                + " at test runtime; javacard-express-api contains compile-only stubs. Declare"
                + " javacard-express-core before javacard-express-api and exclude the stubs from the test"
                + " runtime with maven-surefire-plugin <classpathDependencyExcludes><classpathDependencyExclude>"
                + "name.velikodniy:javacard-express-api</classpathDependencyExclude></classpathDependencyExcludes>."
                + " An IDE (IntelliJ IDEA, Eclipse, VS Code) builds the test class path from the dependency order of"
                + " the POM and ignores the Surefire exclude, so only the dependency order fixes runs from the IDE:"
                + " reorder the dependencies and reload the Maven project"
                + " (set -D" + DISABLE_PROPERTY + "=false to skip this check).");
    }

    private static boolean sameLocation(URL api, URL simulator) {
        return Objects.equals(api.toExternalForm(), simulator.toExternalForm());
    }

    private static URL location(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source == null ? null : source.getLocation();
    }
}
