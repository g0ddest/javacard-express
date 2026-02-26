package name.velikodniy.jcexpress.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassLoader that loads classes from raw byte arrays received over TCP.
 */
final class ByteClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes = new ConcurrentHashMap<>();

    ByteClassLoader(ClassLoader parent) {
        super(parent);
    }

    void addClass(String name, byte[] bytecode) {
        classes.put(name, bytecode);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = classes.get(name);
        if (bytecode == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
