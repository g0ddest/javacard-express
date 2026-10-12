package name.velikodniy.jcexpress.converter.resolve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The converter's built-in API data is read from its jar while other class loaders over the same jar are closed. javac
 * loads an annotation processor (the compile-time check, which runs the converter) in a class loader of its own for
 * each compilation and closes it when the compilation ends; builds compile several modules at the same time in one
 * JVM (Maven with {@code -T}). A resource read through the JVM's shared cache of open jar files would fail whenever
 * another loader of the same jar closes that cached file under it.
 */
class BuiltinDataLoadingTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path MAIN_CLASSES = Path.of("target/classes");
    private static final int LOADERS = 400;
    private static final int THREADS = 8;

    @TempDir
    Path tmp;

    @Test
    void theApiTablesLoadWhileOtherClassLoadersOfTheSameJarClose() throws Exception {
        URL jar = jarOf(MAIN_CLASSES).toUri().toURL();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < LOADERS; i++) {
                results.add(pool.submit(() -> loadAndClose(jar)));
            }
            List<String> failures = new ArrayList<>();
            for (Future<String> result : results) {
                String failure = result.get();
                if (failure != null) {
                    failures.add(failure);
                }
            }

            assertThat(failures).as("%d of %d class loaders could not load the built-in data", failures.size(),
                    LOADERS).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    /** One class loader as javac makes it for a compilation: it loads the data, then it is closed. */
    private static String loadAndClose(URL jar) throws IOException {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar}, ClassLoader.getPlatformClassLoader())) {
            Class<?> exports = Class.forName(BuiltinExports.class.getName(), true, loader);
            Class.forName(GlobalPlatformApi.class.getName(), true, loader);
            Object framework = exports.getMethod("getExport", String.class).invoke(null, "javacard/framework");
            return framework == null ? "no data for javacard/framework" : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            StringBuilder chain = new StringBuilder();
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                chain.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append("; ");
            }
            return chain.toString();
        }
    }

    /** The converter's classes and resources as the jar a build puts on the processor path. */
    private Path jarOf(Path classes) throws IOException {
        Path jar = tmp.resolve("converter.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out);
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return jar;
    }
}
