package name.velikodniy.jcexpress;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated test class or method only on the given backends ({@code jcx.backend}, see
 * {@link JavaCardTest}); on any other backend it is reported as skipped. For checks that need a
 * GlobalPlatform card (card content, secure channel) or a behaviour the simulator does not have.
 *
 * <pre>
 * {@literal @}Test
 * {@literal @}EnabledOnBackend({Mode.SIMULATED_GP, Mode.LIVECARD})
 * void theIsdListsTheApplet(LiveCard live, SmartCardSession card) { ... }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(BackendCondition.class)
public @interface EnabledOnBackend {

    /**
     * The backends the test runs on.
     *
     * @return the backends
     */
    Mode[] value();

    /**
     * Why the test is limited to these backends; part of the skip message.
     *
     * @return the reason, or empty
     */
    String reason() default "";
}
