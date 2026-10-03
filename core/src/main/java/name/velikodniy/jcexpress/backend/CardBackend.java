package name.velikodniy.jcexpress.backend;

import name.velikodniy.jcexpress.Mode;

import java.util.Set;

/**
 * A backend of {@link name.velikodniy.jcexpress.JavaCardTest} classes, found with {@link java.util.ServiceLoader}:
 * core provides {@link Mode#EMBEDDED}; {@code javacard-express-livecard} provides {@link Mode#SIMULATED_GP} and
 * {@link Mode#LIVECARD}.
 */
public interface CardBackend {

    /**
     * Returns the mode this backend implements.
     *
     * @return the mode
     */
    Mode mode();

    /**
     * Returns the parameter types besides {@link name.velikodniy.jcexpress.SmartCardSession} that tests can declare
     * on this backend ({@link TestCard#resolve(Class)} provides them).
     *
     * @return the types, empty by default
     */
    default Set<Class<?>> parameterTypes() {
        return Set.of();
    }

    /**
     * Opens a card for a test class run.
     *
     * @param request the test class and the run's settings
     * @return the card
     */
    TestCard open(CardRequest request);
}
