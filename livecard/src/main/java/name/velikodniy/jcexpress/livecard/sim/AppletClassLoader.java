package name.velikodniy.jcexpress.livecard.sim;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads the applet classes of its directories (the test applets, compiled third-party applets) itself, child
 * first, and everything else (jCardSim's {@code javacard.framework}, the JDK) from its parent. A jCardSim card
 * with its own loader starts with fresh static fields, like a package that has just been loaded onto a card;
 * applets that share objects through a shareable interface must use the same loader.
 */
final class AppletClassLoader extends URLClassLoader {

    AppletClassLoader(List<Path> classesDirectories, ClassLoader parent) {
        super(classesDirectories.stream().map(AppletClassLoader::url).toArray(URL[]::new), parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (findResource(name.replace('.', '/') + ".class") == null) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = findClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    /** Closes the loader. */
    void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            // a directory class path holds no open file
        }
    }

    private static URL url(Path directory) {
        try {
            return directory.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(directory.toString(), e);
        }
    }
}
