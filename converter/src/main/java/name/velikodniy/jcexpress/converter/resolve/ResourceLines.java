package name.velikodniy.jcexpress.converter.resolve;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads a text resource of the converter's jar without the JVM's shared cache of open jar files. The converter runs
 * inside javac as the compile-time check, in a class loader javac makes for each compilation and closes when the
 * compilation ends, and builds compile several modules at the same time in one JVM. A class loader closes the jar
 * files it has read resources through; with the shared cache that is a jar file other class loaders of the same jar
 * are reading at that moment. Opened without the cache, the jar file belongs to this read alone.
 */
final class ResourceLines {

    private ResourceLines() {
    }

    /**
     * Reads a resource next to a class.
     *
     * @param owner the class the resource name is relative to
     * @param name  the resource name
     * @return the lines of the resource, decoded as UTF-8
     * @throws IllegalStateException if there is no such resource
     * @throws UncheckedIOException  if it cannot be read
     */
    static List<String> read(Class<?> owner, String name) {
        URL url = owner.getResource(name);
        if (url == null) {
            throw new IllegalStateException("Missing converter resource " + name);
        }
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read converter resource " + name, e);
        }
    }
}
