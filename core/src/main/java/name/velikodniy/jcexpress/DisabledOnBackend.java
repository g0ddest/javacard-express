package name.velikodniy.jcexpress;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skips the annotated test class or method on the given backends ({@code jcx.backend}, see
 * {@link JavaCardTest}), for example where jCardSim deviates from the Java Card specification. Both
 * {@link Mode#EMBEDDED} and {@link Mode#SIMULATED_GP} run the applet classes on jCardSim, so a jCardSim deviation
 * names both:
 *
 * <pre>
 * {@literal @}Test
 * {@literal @}DisabledOnBackend(value = {Mode.EMBEDDED, Mode.SIMULATED_GP},
 *         reason = "jCardSim does not roll back JCSystem.abortTransaction()")
 * void abortedTransactionKeepsTheCounter(SmartCardSession card) { ... }
 * </pre>
 *
 * <p>{@code @EnabledOnBackend(Mode.LIVECARD)} says the same for such a test.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(BackendCondition.class)
public @interface DisabledOnBackend {

    /**
     * The backends the test is skipped on.
     *
     * @return the backends
     */
    Mode[] value();

    /**
     * Why the test is skipped there; part of the skip message.
     *
     * @return the reason, or empty
     */
    String reason() default "";
}
