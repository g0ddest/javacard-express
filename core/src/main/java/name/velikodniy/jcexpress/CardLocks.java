package name.velikodniy.jcexpress;

import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLocksProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Locks of {@link JavaCardTest} classes for JUnit's parallel execution. Every test class (with its {@code @Nested}
 * classes) holds a read-write lock on its card, so its methods run one at a time in the class's thread: the card of
 * a class is one device. With {@code -Djcx.backend=livecard} all classes lock the same key {@value #CARD}, because
 * they share the card in the reader; on the other backends every class has its own card and classes still run in
 * parallel.
 */
public final class CardLocks implements ResourceLocksProvider {

    /** The lock key of the real card. */
    public static final String CARD = "jcx.card";

    /** Creates the provider (JUnit instantiates it). */
    public CardLocks() {
        // stateless
    }

    @Override
    public Set<Lock> provideForClass(Class<?> testClass) {
        return lockOf(testClass);
    }

    @Override
    public Set<Lock> provideForNestedClass(List<Class<?>> enclosingInstanceTypes, Class<?> testClass) {
        return lockOf(enclosingInstanceTypes.isEmpty() ? testClass : enclosingInstanceTypes.getFirst());
    }

    @Override
    public Set<Lock> provideForMethod(List<Class<?>> enclosingInstanceTypes, Class<?> testClass, Method testMethod) {
        return Set.of();
    }

    private static Set<Lock> lockOf(Class<?> topLevelClass) {
        String key = Backends.fromSystemProperty() == Mode.LIVECARD ? CARD : CARD + ":" + topLevelClass.getName();
        return Set.of(new Lock(key, ResourceAccessMode.READ_WRITE));
    }
}
