package name.velikodniy.jcexpress.backend;

import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SmartCardSession;

import java.util.Collection;
import java.util.Optional;

/**
 * A card opened by a {@link CardBackend} for one test class run (the class and its {@code @Nested} classes).
 * {@link name.velikodniy.jcexpress.JavaCardExtension} installs and deletes the declared applets through it; the
 * tests talk to {@link #session()}.
 */
public interface TestCard extends AutoCloseable {

    /**
     * Returns the backend of this card.
     *
     * @return the mode
     */
    Mode mode();

    /**
     * Returns the AIDs of this run.
     *
     * @return the AID scheme (its prefix is the one the card accepts)
     */
    AidScheme aids();

    /**
     * Returns the session the tests send commands through.
     *
     * @return the session
     */
    SmartCardSession session();

    /**
     * Installs an instance, loading its package first when it is not loaded yet. A backend that loads packages
     * (a GlobalPlatform card) loads the package with every applet of it (not only those the run declares), so that
     * later instances of any of them, declared or installed imperatively, need no new load.
     *
     * @param applet      the instance to install
     * @param declaredRun everything the test class run declares
     */
    void install(AppletDeclaration applet, Collection<AppletDeclaration> declaredRun);

    /**
     * Deletes an instance; its package stays loaded until the card is closed.
     *
     * @param applet the instance to delete
     */
    void delete(AppletDeclaration applet);

    /**
     * Deselects the selected applet: selects the Issuer Security Domain on a GlobalPlatform card, an empty applet
     * on jCardSim.
     */
    void deselect();

    /**
     * Provides a backend-specific object to test parameters (for example the live-card harness).
     *
     * @param type the parameter type
     * @param <T>  the type
     * @return the object, or empty when this backend has none of that type
     */
    default <T> Optional<T> resolve(Class<T> type) {
        return Optional.empty();
    }

    /**
     * Deletes everything this card created that is still there (verified where the backend can), then closes the
     * connection.
     */
    @Override
    void close();
}
