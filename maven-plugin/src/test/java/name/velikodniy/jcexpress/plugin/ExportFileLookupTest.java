package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Export files of imported packages. The converter needs the export file of every package the
 * converted package references, other than the Java Card API packages it knows (JCVM 3.1
 * &sect;4.1.1, chapter 5). Export files are found in {@code <importExportFiles>}, in
 * {@code <exportPath>} directories or jars, in dependencies of type {@code exp}, and inside
 * dependency jars at the location JCVM 3.1 &sect;5.2 defines for export files in a JAR file:
 * {@code <package directory>/javacard/<last package name component>.exp}.
 */
class ExportFileLookupTest {

    /** The library package AID; the CAP of the client package must import it (JCVM 3.1 &sect;6.7). */
    private static final String LIBRARY_AID = "A00000006230";

    @TempDir
    Path dir;

    private Path libraryClasses;
    private Path libraryExp;

    @BeforeEach
    void buildLibraryPackage() throws Exception {
        Path lib = dir.resolve("lib");
        libraryClasses = Samples.libraryPackage().compile(lib);
        MojoRunner.forProject(lib, libraryClasses).configure("packageAid", LIBRARY_AID).execute();
        libraryExp = lib.resolve("target/sample-applet-1.0.exp");
        assertThat(libraryExp).isNotEmptyFile();
    }

    @Test
    void importExportFilesAreUsed() throws Exception {
        CapFile cap = build(client().configure("importExportFiles", List.of(libraryExp.toFile())));

        assertThat(cap.imports()).extracting(CapFile.ImportedPackage::aid).contains(LIBRARY_AID);
    }

    @Test
    void exportPathDirectoryIsSearchedAtTheSpecLocation() throws Exception {
        Path exports = dir.resolve("exports");
        copy(libraryExp, exports.resolve("com/example/lib/javacard/lib.exp"));

        CapFile cap = build(client().configure("exportPath", List.of(exports.toFile())));

        assertThat(cap.imports()).extracting(CapFile.ImportedPackage::aid).contains(LIBRARY_AID);
    }

    @Test
    void exportPathJarIsSearchedAtTheSpecLocation() throws Exception {
        Path jar = jar(dir.resolve("exports.jar"), Map.entry("com/example/lib/javacard/lib.exp", libraryExp));

        CapFile cap = build(client().configure("exportPath", List.of(jar.toFile())));

        assertThat(cap.imports()).extracting(CapFile.ImportedPackage::aid).contains(LIBRARY_AID);
    }

    @Test
    void exportFileInsideADependencyJarIsFound() throws Exception {
        Path jar = jar(dir.resolve("lib.jar"),
                Map.entry("com/example/lib/ShortMath.class", libraryClasses.resolve("com/example/lib/ShortMath.class")),
                Map.entry("com/example/lib/javacard/lib.exp", libraryExp));

        CapFile cap = build(client().artifacts(Set.of(artifact("lib", "jar", jar))));

        assertThat(cap.imports()).extracting(CapFile.ImportedPackage::aid).contains(LIBRARY_AID);
    }

    @Test
    void dependencyOfTypeExpIsUsed() throws Exception {
        CapFile cap = build(client().artifacts(Set.of(artifact("lib", "exp", libraryExp))));

        assertThat(cap.imports()).extracting(CapFile.ImportedPackage::aid).contains(LIBRARY_AID);
    }

    @Test
    void missingExportFileOfAReferencedPackageFailsWithTheReferenceAndAHint() {
        MojoRunner runner = client();

        assertThatThrownBy(runner::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("com.example.lib")
                .hasMessageContaining("CalcApplet.java:")
                .hasMessageContaining("<exportPath>");
    }

    @Test
    void missingImportExportFileFails() {
        MojoRunner runner = client().configure("importExportFiles", List.of(new File(dir.toFile(), "missing/lib.exp")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Export file not found").hasMessageContaining("lib.exp");
    }

    @Test
    void missingExportPathEntryFails() {
        MojoRunner runner = client().configure("exportPath", List.of(new File(dir.toFile(), "no-such-dir")));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("exportPath").hasMessageContaining("no-such-dir");
    }

    /** An applet package that calls the library package. */
    private MojoRunner client() {
        Path app = dir.resolve("app");
        Path classes = JavaSources.create().classpath(libraryClasses)
                .add("com/example/app/CalcApplet.java", """
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
                            }
                        }
                        """)
                .compile(app);
        return MojoRunner.forProject(app, classes).configure("packageAid", "A00000006231");
    }

    private CapFile build(MojoRunner runner) throws Exception {
        runner.execute();
        return CapFile.read(dir.resolve("app/target/sample-applet-1.0.cap"));
    }

    private static Artifact artifact(String artifactId, String type, Path file) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
        handler.setAddedToClasspath("jar".equals(type));
        DefaultArtifact artifact = new DefaultArtifact("com.example", artifactId, "1.0", Artifact.SCOPE_PROVIDED,
                type, null, handler);
        artifact.setFile(file.toFile());
        return artifact;
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.copy(from, to);
    }

    @SafeVarargs
    private static Path jar(Path jarFile, Map.Entry<String, Path>... entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(jarFile); JarOutputStream jar = new JarOutputStream(out)) {
            for (Map.Entry<String, Path> entry : entries) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(Files.readAllBytes(entry.getValue()));
                jar.closeEntry();
            }
        }
        return jarFile;
    }
}
