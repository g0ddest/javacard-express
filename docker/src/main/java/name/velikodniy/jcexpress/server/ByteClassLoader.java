package name.velikodniy.jcexpress.server;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines the applet classes a client sent as raw class files.
 *
 * <p>Delegation is parent-first: classes the server provides itself (the JDK, jCardSim with its
 * {@code javacard.*} API, the server) can never be replaced by shipped bytes. A class is defined lazily, when it is
 * first needed; definition errors ({@link ClassFormatError}, {@link UnsupportedClassVersionError},
 * {@link NoClassDefFoundError} for a class the client did not send, ...) surface as the command's error reply.</p>
 *
 * <p>The loader remembers the names of classes that were requested but neither provided by the server nor sent by
 * the client ({@link #takeMissingClasses()}): jCardSim reports a failure inside {@code Applet.install} only as
 * {@code SystemException}, so this is the only trace of a class missing from the request.</p>
 */
final class ByteClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes = new ConcurrentHashMap<>();
    private final Set<String> missing = ConcurrentHashMap.newKeySet();

    ByteClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Registers the class file of a class (replacing bytes registered earlier but not defined yet).
     *
     * @param name     binary class name
     * @param bytecode class file bytes
     */
    void addClass(String name, byte[] bytecode) {
        classes.put(name, bytecode);
        missing.remove(name);
    }

    /**
     * Returns and forgets the names of the classes that were looked up but are unknown.
     *
     * @return binary class names, sorted
     */
    Set<String> takeMissingClasses() {
        Set<String> names = new TreeSet<>(missing);
        missing.removeAll(names);
        return names;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = classes.get(name);
        if (bytecode == null) {
            missing.add(name);
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
