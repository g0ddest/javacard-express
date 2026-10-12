package name.velikodniy.jcexpress.embedded.gp;

import java.net.URL;
import java.security.CodeSource;
import java.util.Objects;

/**
 * Checks, when applet code needs a class of {@code org.globalplatform}, that the class comes from this module,
 * whose classes of that package work on the simulated cards. A jar of compile-only GlobalPlatform API stubs ahead
 * of this module on the test class path (the stubs of {@code javacard-express-api}, or GlobalPlatform's own API
 * jar), or stand-in classes of the package among a project's test classes, would give the applet a
 * {@code GPSystem} that throws or returns null; the check names the cause and the fix instead.
 *
 * <p>The class loader of a simulated card calls {@link #verify(Class)} for the classes it takes from its parent,
 * so a setup with such a jar keeps working for applets that do not use the package. The system property
 * {@value #DISABLE_PROPERTY}{@code =false} switches the check off, as it does for the Java Card API.</p>
 */
public final class ApiClasspath {

    /** System property that disables the check when set to {@code false}. */
    static final String DISABLE_PROPERTY = "jcx.embedded.classpathCheck";

    private static final String PACKAGE = "org.globalplatform";

    private ApiClasspath() {
    }

    /**
     * Verifies a class a simulated card's class loader took from its parent for applet code: a class of the
     * package {@code org.globalplatform} must come from where this module's classes come from. Other classes,
     * those of the other GlobalPlatform packages included, are not checked.
     *
     * @param loaded the class the parent loader provided
     * @throws IllegalStateException if the class is of {@code org.globalplatform} and was loaded from elsewhere
     */
    public static void verify(Class<?> loaded) {
        if (!PACKAGE.equals(loaded.getPackageName())
                || "false".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY))) {
            return;
        }
        verify(loaded.getName(), location(loaded), location(GlobalPlatformCard.class));
    }

    /**
     * Verifies that the location of a class of the GlobalPlatform API is the location of this module.
     *
     * @param className the class of {@code org.globalplatform}
     * @param api       where the class was loaded from, or {@code null} if unknown
     * @param core      where this module's GlobalPlatform environment was loaded from, or {@code null} if unknown
     * @throws IllegalStateException if the two locations differ
     */
    static void verify(String className, URL api, URL core) {
        if (api != null && core != null && Objects.equals(api.toExternalForm(), core.toExternalForm())) {
            return;
        }
        throw new IllegalStateException(className + " was loaded from " + api
                + ", not from javacard-express-core (" + core + "). The classes of org.globalplatform must come"
                + " from javacard-express-core at test runtime, where they work on the simulated card; a jar of"
                + " compile-only GlobalPlatform API stubs (javacard-express-api, or GlobalPlatform's own API jar)"
                + " is ahead of it on the test class path. Declare javacard-express-core before that jar and"
                + " exclude the stubs from the test runtime with maven-surefire-plugin"
                + " <classpathDependencyExcludes>. An IDE builds the test class path from the dependency order of"
                + " the POM and ignores the Surefire exclude, so only the dependency order fixes runs from the IDE."
                + " If the project has stand-in classes of org.globalplatform of its own among its test classes,"
                + " remove them: the simulated card implements the package"
                + " (set -D" + DISABLE_PROPERTY + "=false to skip this check).");
    }

    private static URL location(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source == null ? null : source.getLocation();
    }
}
