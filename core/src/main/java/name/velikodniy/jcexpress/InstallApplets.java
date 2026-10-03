package name.velikodniy.jcexpress;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container of repeated {@link InstallApplet} annotations; Java creates it when {@link InstallApplet} is used
 * more than once on the same class or method.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(JavaCardExtension.class)
@ResourceLock(providers = CardLocks.class)
public @interface InstallApplets {

    /**
     * The repeated annotations.
     *
     * @return the declared applets, in declaration order
     */
    InstallApplet[] value();
}
