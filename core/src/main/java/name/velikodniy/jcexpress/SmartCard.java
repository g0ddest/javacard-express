package name.velikodniy.jcexpress;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link SmartCardSession} field for automatic injection by {@link JavaCardExtension}.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@literal @}ExtendWith(JavaCardExtension.class)
 * class MyAppletTest {
 *     {@literal @}SmartCard
 *     SmartCardSession card;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartCard {
    /** The backend mode. */
    Mode mode() default Mode.EMBEDDED;

    /** EEPROM size in bytes. */
    int persistentMemory() default 32768;

    /** Docker image for container mode. Empty string means build from local docker/ directory. */
    String image() default "";

    /** Enable APDU logging. */
    boolean log() default false;
}
