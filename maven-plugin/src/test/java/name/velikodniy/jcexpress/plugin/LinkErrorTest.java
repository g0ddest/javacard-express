package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A package that uses another Java Card package needs that package's export file (JCVM 3.1
 * &sect;4.1.1). When it is missing, the failure names the package once, however often the code
 * refers to it, says where the references are, and says what to add: the export file of the
 * dependency that provides the package's classes, or, for a package compiled in the same module,
 * how to build it first.
 */
class LinkErrorTest {

    private static final String CLIENT = """
            package com.example.app;

            import com.example.lib.ShortMath;
            import javacard.framework.APDU;
            import javacard.framework.Applet;
            import javacard.framework.Util;

            public class CalcApplet extends Applet {
                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new CalcApplet().register();
                }

                public void process(APDU apdu) {
                    byte[] buffer = apdu.getBuffer();
                    Util.setShort(buffer, (short) 0, ShortMath.twice(buffer[0]));
                    Util.setShort(buffer, (short) 2, ShortMath.twice(buffer[1]));
                    Util.setShort(buffer, (short) 4, new ShortMath().half(buffer[2]));
                }
            }
            """;

    @TempDir
    Path dir;

    private Path libraryClasses;

    @BeforeEach
    void compileLibrary() {
        libraryClasses = Samples.libraryPackage().compile(dir.resolve("lib"));
    }

    @Test
    void aMissingExportFileIsOneErrorWithTheDependencyThatProvidesIt() throws Exception {
        Path jar = jar(dir.resolve("lib-1.0.jar"), libraryClasses, "com/example/lib/ShortMath.class");
        MojoRunner runner = client().artifacts(Set.of(artifact(jar)));

        String message = catchThrowableOfType(MojoFailureException.class, runner::execute).getMessage();

        assertThat(count(message, "no export file for package com.example.lib")).isEqualTo(1);
        assertThat(message).doesNotContain("was supplied")
                .contains("com.example.lib.ShortMath is used at " + dir.resolve("app/src/com/example/app/CalcApplet.java")
                        + ":[15] com.example.app.CalcApplet.process()")
                .contains("com.acme.jc:purse-lib:1.0.0-SNAPSHOT")
                .contains("""
                        <dependency>
                            <groupId>com.acme.jc</groupId>
                            <artifactId>purse-lib</artifactId>
                            <version>1.0.0-SNAPSHOT</version>
                            <type>exp</type>
                            <scope>provided</scope>
                        </dependency>""")
                .contains("<importExportFiles>").contains("<exportPath>");
    }

    @Test
    void aPackageNotOnTheClassPathGetsTheGeneralAdvice() {
        String message = catchThrowableOfType(MojoFailureException.class, client()::execute).getMessage();

        assertThat(count(message, "no export file for package com.example.lib")).isEqualTo(1);
        assertThat(message).contains("<importExportFiles>").contains("<exportPath>")
                .contains("<type>exp</type>").doesNotContain("<groupId>");
    }

    @Test
    void aPackageCompiledInThisModuleIsToBeMovedOrConvertedFirst() {
        // An applet with a helper in a sub-package and <packageName> set: the helper's package is not
        // part of the CAP file, and the plugin cannot link to it
        Path classes = JavaSources.create()
                .add("com/example/app/CalcApplet.java", CLIENT.replace("com.example.lib", "com.example.app.util"))
                .add("com/example/app/util/ShortMath.java", """
                        package com.example.app.util;

                        public class ShortMath {
                            public static short twice(short value) {
                                return (short) (value + value);
                            }

                            public short half(short value) {
                                return (short) (value >> 1);
                            }
                        }
                        """)
                .compile(dir.resolve("app"));
        MojoRunner runner = MojoRunner.forProject(dir.resolve("app"), classes)
                .configure("packageAid", "F000000002").configure("packageName", "com.example.app");

        String message = catchThrowableOfType(MojoFailureException.class, runner::execute).getMessage();

        assertThat(count(message, "no export file for package com.example.app.util")).isEqualTo(1);
        assertThat(message).contains("compiled in this module").contains("move")
                .contains("<importExportFiles>").doesNotContain("<type>exp</type>");
    }

    private MojoRunner client() {
        Path app = dir.resolve("app");
        Path classes = JavaSources.create().classpath(libraryClasses)
                .add("com/example/app/CalcApplet.java", CLIENT).compile(app);
        return MojoRunner.forProject(app, classes).configure("packageAid", "F000000002");
    }

    private static Artifact artifact(Path jar) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");
        handler.setAddedToClasspath(true);
        DefaultArtifact artifact = new DefaultArtifact("com.acme.jc", "purse-lib", "1.0.0-SNAPSHOT",
                Artifact.SCOPE_PROVIDED, "jar", null, handler);
        artifact.setFile(jar.toFile());
        return artifact;
    }

    private static Path jar(Path jarFile, Path classes, String... entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(jarFile); JarOutputStream jar = new JarOutputStream(out)) {
            for (String entry : entries) {
                jar.putNextEntry(new JarEntry(entry));
                jar.write(Files.readAllBytes(classes.resolve(entry)));
                jar.closeEntry();
            }
        }
        return jarFile;
    }

    private static int count(String text, String fragment) {
        Matcher m = Pattern.compile(Pattern.quote(fragment)).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
