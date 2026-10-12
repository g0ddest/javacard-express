package name.velikodniy.jcexpress.javac;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * javac's processing environment behind the one a processor is given. javac's API for processors
 * ({@code JavacTask.instance}, {@code Trees.instance}) accepts only javac's own environment, but build tools that run
 * processors through a wrapper of their own hand them a proxy of it: IntelliJ IDEA's build does, and its javac runner
 * has a method that returns what a wrapper wraps ({@code org.jetbrains.jps.javac.APIWrappers.unwrap}). Other wrappers
 * are recognised by the field that holds the wrapped environment.
 */
final class JavacEnvironment {

    private static final String JAVAC = "com.sun.tools.javac.processing.JavacProcessingEnvironment";
    private static final String JPS_WRAPPERS = "org.jetbrains.jps.javac.APIWrappers";
    /** How many wrappers around each other are unwrapped. */
    private static final int DEPTH = 4;

    private JavacEnvironment() {
    }

    /**
     * Finds javac's processing environment.
     *
     * @param environment the environment the processor was given
     * @return javac's environment; empty when none is behind it (another compiler, or a wrapper not recognised)
     */
    static Optional<ProcessingEnvironment> of(ProcessingEnvironment environment) {
        ProcessingEnvironment current = environment;
        for (int depth = 0; current != null && depth <= DEPTH; depth++) {
            if (current.getClass().getName().equals(JAVAC)) {
                return Optional.of(current);
            }
            ProcessingEnvironment wrapper = current;
            current = unwrappedByJps(wrapper).or(() -> wrapped(wrapper)).orElse(null);
        }
        return Optional.empty();
    }

    /** IntelliJ IDEA's javac runner unwraps its own wrappers. */
    private static Optional<ProcessingEnvironment> unwrappedByJps(ProcessingEnvironment wrapper) {
        for (ClassLoader loader : loaders(wrapper)) {
            try {
                Method unwrap = Class.forName(JPS_WRAPPERS, false, loader).getMethod("unwrap", Class.class,
                        Object.class);
                if (unwrap.invoke(null, ProcessingEnvironment.class, wrapper) instanceof ProcessingEnvironment inner
                        && inner != wrapper) {
                    return Optional.of(inner);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                // not IntelliJ IDEA's runner, or one without that method: try the next class loader
            }
        }
        return Optional.empty();
    }

    /** The environment a wrapper keeps in a field, of itself or of its proxy's invocation handler. */
    private static Optional<ProcessingEnvironment> wrapped(ProcessingEnvironment wrapper) {
        Object holder = Proxy.isProxyClass(wrapper.getClass()) ? Proxy.getInvocationHandler(wrapper) : wrapper;
        for (Class<?> type = holder.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Optional<ProcessingEnvironment> inner = environmentIn(field, holder);
                if (inner.isPresent() && inner.get() != wrapper) {
                    return inner;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<ProcessingEnvironment> environmentIn(Field field, Object holder) {
        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
            return Optional.empty();
        }
        try {
            field.setAccessible(true);
            return field.get(holder) instanceof ProcessingEnvironment inner ? Optional.of(inner) : Optional.empty();
        } catch (RuntimeException | IllegalAccessException e) {
            // a field of a module that does not open it
            return Optional.empty();
        }
    }

    private static Set<ClassLoader> loaders(ProcessingEnvironment wrapper) {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(wrapper.getClass().getClassLoader());
        if (Proxy.isProxyClass(wrapper.getClass())) {
            loaders.add(Proxy.getInvocationHandler(wrapper).getClass().getClassLoader());
        }
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.remove(null);
        return loaders;
    }
}
