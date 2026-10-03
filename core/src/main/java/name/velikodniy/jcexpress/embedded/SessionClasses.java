package name.velikodniy.jcexpress.embedded;

import javacard.framework.Applet;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * The applet classes of one {@link EmbeddedSession}: by default the session loads them itself
 * ({@link AppletClassLoader}, one per class loader of the classes passed to it), so their static fields belong
 * to the session. With the system property {@value EmbeddedSession#SHARED_STATICS_PROPERTY}{@code =true} the
 * session runs the classes it is given, and all sessions share their static fields (the behaviour of 0.3.0).
 */
final class SessionClasses {

    private static final Logger LOG = Logger.getLogger(EmbeddedSession.class.getName());
    private static volatile boolean warned;

    private final boolean shared;
    private final Map<ClassLoader, AppletClassLoader> loaders = new IdentityHashMap<>();

    private SessionClasses(boolean shared) {
        this.shared = shared;
    }

    /**
     * Reads {@value EmbeddedSession#SHARED_STATICS_PROPERTY}.
     *
     * @return the applet classes of a new session
     */
    static SessionClasses fromSystemProperty() {
        boolean shared = Boolean.getBoolean(EmbeddedSession.SHARED_STATICS_PROPERTY);
        if (shared && !warned) {
            warned = true;
            LOG.warning("-D" + EmbeddedSession.SHARED_STATICS_PROPERTY + "=true: embedded sessions share the static"
                    + " fields of applet classes, as in 0.3.0. This switch is deprecated and will be removed in the"
                    + " next release; keep state that tests share on the card instead.");
        }
        return new SessionClasses(shared);
    }

    /**
     * Returns the class the session installs for an applet class.
     *
     * @param appletClass the class passed to the session
     * @return the session's own copy of the class, or the class itself when statics are shared
     */
    synchronized Class<? extends Applet> load(Class<? extends Applet> appletClass) {
        ClassLoader source = appletClass.getClassLoader();
        if (shared || source == null) {
            return appletClass;
        }
        AppletClassLoader loader = loaders.computeIfAbsent(source, AppletClassLoader::new);
        try {
            return loader.loadClass(appletClass.getName()).asSubclass(Applet.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + appletClass.getName() + " for the simulated card", e);
        }
    }

    /**
     * Returns and forgets the classes the applet code needed but the test class path lacks.
     *
     * @return binary class names, sorted; empty when statics are shared (nothing is recorded then)
     */
    synchronized Set<String> takeMissingClasses() {
        Set<String> names = new TreeSet<>();
        loaders.values().forEach(loader -> names.addAll(loader.takeMissingClasses()));
        return names;
    }

    /** Drops the loaders, so that the classes of a closed session can be unloaded. */
    synchronized void close() {
        loaders.clear();
    }
}
