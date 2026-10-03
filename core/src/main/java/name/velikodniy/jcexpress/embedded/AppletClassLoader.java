package name.velikodniy.jcexpress.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the applet code of one simulated card: every class the applet classes need is defined again by this
 * loader (child first), from the class files its parent finds, except the platform the card provides, which comes
 * from the parent: the JDK, the Java Card API and jCardSim ({@code java.}, {@code javax.}, {@code javacard.},
 * {@code javacardx.}, {@code com.licel.}, and any class the parent reads from the run-time image).
 *
 * <p>So a new card starts with fresh static fields in every package of the applet code, as a card on which the
 * packages were just loaded, while the applets of one card share their packages. Classes keep their code source,
 * so coverage agents instrument them as usual.</p>
 *
 * <p>The loader remembers the classes the applet code asked for but nobody could provide
 * ({@link #takeMissingClasses()}): jCardSim reports a failure inside {@code Applet.install} only as a
 * {@code SystemException}, so this is the trace of a library missing from the test class path.</p>
 */
final class AppletClassLoader extends ClassLoader {

    /** Packages of the card's platform, always taken from the parent. */
    private static final List<String> PLATFORM = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.",
            "javacard.", "javacardx.", "com.licel.");

    static {
        registerAsParallelCapable();
    }

    private final Set<String> missing = ConcurrentHashMap.newKeySet();
    private final Map<String, ProtectionDomain> domains = new ConcurrentHashMap<>();

    /**
     * Creates a loader for the applet classes that {@code parent} can see.
     *
     * @param parent the class loader of the applet classes passed to the session
     */
    AppletClassLoader(ClassLoader parent) {
        super("jcx-card", parent);
    }

    /**
     * Returns and forgets the names of the classes the applet code needed but neither the class path nor the
     * platform provided.
     *
     * @return binary class names, sorted
     */
    Set<String> takeMissingClasses() {
        Set<String> names = new TreeSet<>(missing);
        missing.removeAll(names);
        return names;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isPlatform(name)) {
            return fromParent(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                URL classFile = getParent().getResource(name.replace('.', '/') + ".class");
                if (classFile == null || "jrt".equals(classFile.getProtocol())) {
                    return fromParent(name, resolve);
                }
                loaded = define(name, classFile);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> fromParent(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            missing.add(name);
            throw e;
        }
    }

    private Class<?> define(String name, URL classFile) throws ClassNotFoundException {
        byte[] bytes;
        try (InputStream in = classFile.openStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(name + " (cannot read " + classFile + ")", e);
        }
        return defineClass(name, bytes, 0, bytes.length, domain(classFile, name));
    }

    /** The protection domain of the class path entry that holds the class file (a directory or a JAR). */
    private ProtectionDomain domain(URL classFile, String name) {
        String text = classFile.toExternalForm();
        String path = name.replace('.', '/') + ".class";
        String location;
        if (text.startsWith("jar:") && text.contains("!/")) {
            location = text.substring("jar:".length(), text.indexOf("!/"));
        } else if (text.endsWith(path)) {
            location = text.substring(0, text.length() - path.length());
        } else {
            location = text;
        }
        return domains.computeIfAbsent(location, this::newDomain);
    }

    private ProtectionDomain newDomain(String location) {
        URL url;
        try {
            url = URI.create(location).toURL();
        } catch (IllegalArgumentException | IOException e) {
            url = null;
        }
        return new ProtectionDomain(new CodeSource(url, (Certificate[]) null), null, this, null);
    }

    private static boolean isPlatform(String name) {
        for (String prefix : PLATFORM) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
