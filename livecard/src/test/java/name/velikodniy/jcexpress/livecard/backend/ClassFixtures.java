package name.velikodniy.jcexpress.livecard.backend;

import javacard.framework.Applet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Class path layouts of a build for the backend tests: the class files of test classes copied into directories
 * (a split package: target/classes and target/test-classes) or into a jar (a reactor build at package or later, a
 * repository artifact), and a class loader that loads them from there, so that their code source is that entry.
 */
final class ClassFixtures {

    private ClassFixtures() {
    }

    /**
     * Copies the class files of classes into a classes directory.
     *
     * @param root    the classes directory
     * @param classes the classes, compiled into this module's test classes
     */
    static void copy(Path root, Class<?>... classes) {
        for (Class<?> type : classes) {
            String file = type.getName().replace('.', '/') + ".class";
            try {
                Path source = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve(file);
                write(root, type.getName(), Files.readAllBytes(source));
            } catch (IOException | URISyntaxException e) {
                throw new IllegalStateException("Cannot copy " + file, e);
            }
        }
    }

    /**
     * Writes a class file into a classes directory.
     *
     * @param root       the classes directory
     * @param binaryName the class name
     * @param bytes      the class file
     */
    static void write(Path root, String binaryName, byte[] bytes) {
        Path file = root.resolve(binaryName.replace('.', '/') + ".class");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Generates a class outside the Java Card subset, like a JUnit test class next to an applet: a static method
     * that returns a {@code String}.
     *
     * @param binaryName the class name
     * @return the class file
     */
    static byte[] notJavaCard(String binaryName) {
        return ClassFile.of().build(ClassDesc.of(binaryName), clazz -> clazz
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                .withMethodBody("describe", MethodTypeDesc.of(ConstantDescs.CD_String),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.ldc("not Java Card").areturn()));
    }

    /**
     * Packs a classes directory into a jar, with directory entries as the Maven jar plugin writes them.
     *
     * @param jar  the jar to write
     * @param root the classes directory
     * @return the jar
     */
    static Path jar(Path jar, Path root) {
        try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file);
             Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (name.isEmpty()) {
                    continue;
                }
                boolean directory = Files.isDirectory(path);
                out.putNextEntry(new JarEntry(directory ? name + "/" : name));
                if (!directory) {
                    out.write(Files.readAllBytes(path));
                }
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return jar;
    }

    /**
     * Returns a loader that loads the classes and resources of the entries itself (child first) and everything else
     * (jCardSim, the JDK) from this module's class loader. A resource the entries have hides the parent's, so the
     * package of the copied classes is found only in the entries, as in a build where only they hold it.
     *
     * @param entries classes directories and jars
     * @return the loader
     */
    static URLClassLoader childFirst(Path... entries) {
        URL[] urls = Stream.of(entries).map(ClassFixtures::url).toArray(URL[]::new);
        return new URLClassLoader(urls, ClassFixtures.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (findResource(name.replace('.', '/') + ".class") == null) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    return loaded != null ? loaded : findClass(name);
                }
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                Enumeration<URL> own = findResources(name);
                return own.hasMoreElements() ? own : super.getResources(name);
            }
        };
    }

    /**
     * Loads an applet class through a loader.
     *
     * @param loader the loader
     * @param type   the class compiled into this module's test classes
     * @return the class of the loader
     */
    static Class<? extends Applet> applet(ClassLoader loader, Class<?> type) {
        return applet(loader, type.getName());
    }

    /**
     * Loads an applet class through a loader.
     *
     * @param loader     the loader
     * @param binaryName the class name
     * @return the class of the loader
     */
    static Class<? extends Applet> applet(ClassLoader loader, String binaryName) {
        try {
            return Class.forName(binaryName, false, loader).asSubclass(Applet.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Generates an applet whose install method calls a static method {@code describe()} of another class (see
     * {@link #notJavaCard(String)}) before it registers a new instance.
     *
     * @param binaryName the applet class name
     * @param helper     the class it calls
     * @return the class file
     */
    static byte[] appletCalling(String binaryName, String helper) {
        ClassDesc self = ClassDesc.of(binaryName);
        ClassDesc applet = ClassDesc.of("javacard.framework.Applet");
        MethodTypeDesc noArguments = MethodTypeDesc.of(ConstantDescs.CD_void);
        return ClassFile.of().build(self, clazz -> clazz
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                .withSuperclass(applet)
                .withMethodBody(ConstantDescs.INIT_NAME, noArguments, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(applet, ConstantDescs.INIT_NAME, noArguments).return_())
                .withMethodBody("install", MethodTypeDesc.ofDescriptor("([BSB)V"),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code
                                .invokestatic(ClassDesc.of(helper), "describe",
                                        MethodTypeDesc.of(ConstantDescs.CD_String))
                                .pop()
                                .new_(self).dup().invokespecial(self, ConstantDescs.INIT_NAME, noArguments)
                                .invokevirtual(self, "register", noArguments).return_())
                .withMethodBody("process", MethodTypeDesc.ofDescriptor("(Ljavacard/framework/APDU;)V"),
                        ClassFile.ACC_PUBLIC, code -> code.return_()));
    }

    private static URL url(Path entry) {
        try {
            return entry.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(entry.toString(), e);
        }
    }
}
