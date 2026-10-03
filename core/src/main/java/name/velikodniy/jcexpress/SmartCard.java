package name.velikodniy.jcexpress;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link SmartCardSession} field for automatic injection by {@link JavaCardExtension}; the annotation
 * registers the extension itself, so {@code @ExtendWith} is optional.
 *
 * <p>Example usage:</p>
 * <pre>
 * class MyAppletTest {
 *     {@literal @}SmartCard
 *     SmartCardSession card;
 * }
 * </pre>
 *
 * <p>See {@link JavaCardExtension} for the lifetime of the injected session (per test method, per test
 * class for static fields and {@code PER_CLASS} test instances) and the accepted field types. In a
 * {@link JavaCardTest} class the field receives the card of the class run, and its attributes must keep their
 * defaults (the backend is the {@code jcx.backend} setting).</p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(JavaCardExtension.class)
public @interface SmartCard {
    /**
     * The backend mode: {@link Mode#EMBEDDED} or, for a field, {@link Mode#CONTAINER}. {@link Mode#SIMULATED_GP}
     * and {@link Mode#LIVECARD} are rejected: they run {@link JavaCardTest} classes, and the run chooses them with
     * {@code -Djcx.backend}. A parameter always receives an embedded session.
     *
     * @return the backend that simulates the card
     */
    Mode mode() default Mode.EMBEDDED;

    /**
     * EEPROM size in bytes.
     *
     * @return the requested persistent memory size
     * @deprecated not supported: the embedded and container backends run jCardSim, which does not model
     *             the size of persistent memory. {@link JavaCardExtension} rejects values other than the
     *             default instead of silently ignoring them.
     */
    @Deprecated
    int persistentMemory() default 32768;

    /**
     * Pre-built simulator image for {@link Mode#CONTAINER}; ignored by the embedded backend.
     *
     * <p>Empty (the default) leaves the choice to the container backend: the {@code jcx.simulator.image}
     * system property, then {@code jcx.docker.dir}, then the simulator server bundled with
     * {@code javacard-express-container} (see that module's documentation).</p>
     *
     * @return the image name, or empty for the container backend's default
     */
    String image() default "";

    /**
     * Wraps the session in a {@link LoggingSession} that also prints every exchange, one line each on standard
     * output (through {@code java.util.logging}, see {@link LoggingSession}); the field must be able to hold a
     * {@link LoggingSession}.
     *
     * @return true to log APDUs
     */
    boolean log() default false;
}
