package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supertypes are resolved across the project classes and the compile class path, so an applet
 * whose abstract base class or a shareable interface whose super-interface comes from a
 * dependency is still recognised (JCVM 3.1 &sect;6.6: applets are "direct or indirect" subclasses
 * of {@code javacard.framework.Applet}; &sect;6.13: shareable interfaces extend
 * {@code javacard.framework.Shareable} directly or indirectly).
 */
class ClassIndexTest {

    @TempDir
    Path dir;

    private Path libraryJar;
    private Path appClasses;

    @BeforeEach
    void compileLibraryAndApplication() throws IOException {
        Path libClasses = JavaSources.create()
                .add("com/example/base/BaseApplet.java", """
                        package com.example.base;

                        public abstract class BaseApplet extends javacard.framework.Applet {
                        }
                        """)
                .add("com/example/base/Service.java", """
                        package com.example.base;

                        public interface Service extends javacard.framework.Shareable {
                        }
                        """)
                .compile(dir.resolve("lib"));
        libraryJar = jar(libClasses, dir.resolve("lib.jar"));
        appClasses = JavaSources.create().classpath(libraryJar)
                .add("com/example/app/MyApplet.java", """
                        package com.example.app;

                        public class MyApplet extends com.example.base.BaseApplet {
                            public static void install(byte[] bArray, short bOffset, byte bLength) {
                                new MyApplet().register();
                            }

                            public void process(javacard.framework.APDU apdu) {
                            }
                        }
                        """)
                .add("com/example/app/Api.java", """
                        package com.example.app;

                        public interface Api extends com.example.base.Service {
                        }
                        """)
                .add("com/example/app/AppException.java", """
                        package com.example.app;

                        public class AppException extends RuntimeException {
                        }
                        """)
                .compile(dir.resolve("app"));
    }

    @Test
    void appletWhoseBaseClassIsInADependencyJarIsAnApplet() throws Exception {
        try (ClassIndex index = ClassIndex.scan(appClasses, List.of(libraryJar))) {
            ClassSummary applet = index.find("com/example/app/MyApplet").orElseThrow();

            assertThat(index.superclassChain(applet)).isEqualTo(new ClassIndex.SuperclassChain(true, null));
            assertThat(applet.declaresInstall()).isTrue();
        }
    }

    @Test
    void missingSuperclassIsReportedInsteadOfGuessed() throws Exception {
        try (ClassIndex index = ClassIndex.scan(appClasses, List.of())) {
            ClassSummary applet = index.find("com/example/app/MyApplet").orElseThrow();

            assertThat(index.superclassChain(applet))
                    .isEqualTo(new ClassIndex.SuperclassChain(false, "com/example/base/BaseApplet"));
        }
    }

    @Test
    void javaLangSuperclassesEndTheChain() throws Exception {
        try (ClassIndex index = ClassIndex.scan(appClasses, List.of())) {
            ClassSummary exception = index.find("com/example/app/AppException").orElseThrow();

            assertThat(index.superclassChain(exception)).isEqualTo(new ClassIndex.SuperclassChain(false, null));
        }
    }

    @Test
    void interfaceExtendingASharedInterfaceOfADependencyIsShareable() throws Exception {
        try (ClassIndex index = ClassIndex.scan(appClasses, List.of(libraryJar))) {
            assertThat(index.isShareableInterface(index.find("com/example/app/Api").orElseThrow())).isTrue();
        }
    }

    @Test
    void projectClassesAreGroupedByPackage() throws Exception {
        try (ClassIndex index = ClassIndex.scan(appClasses, List.of(libraryJar))) {
            assertThat(index.packages()).containsExactly("com.example.app");
            assertThat(index.classesOf("com.example.app")).extracting(ClassSummary::javaName)
                    .containsExactly("com.example.app.Api", "com.example.app.AppException", "com.example.app.MyApplet");
        }
    }

    private static Path jar(Path classes, Path jarFile) throws IOException {
        try (OutputStream out = Files.newOutputStream(jarFile);
             JarOutputStream jar = new JarOutputStream(out);
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                jar.write(Files.readAllBytes(file));
                jar.closeEntry();
            }
        }
        return jarFile;
    }
}
