package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The GlobalPlatform Card API package {@code org.globalplatform} needs no export file: the converter has its
 * linking data. The CAP file records the lowest version of the package that has everything the converted package
 * uses, or the version of {@code <globalPlatformApi>}; a card loads the CAP file only if it holds the package in
 * that version or a later one (JCVM 3.1 &sect;4.5.2, &sect;6.7). An export file of the package still takes
 * precedence: one named in the configuration always, one inside a dependency jar when no version is fixed.
 *
 * <p>The applets compile against stand-in sources of the API in a jar of their own, as a project would compile
 * against an API jar with scope {@code provided}.</p>
 */
class GlobalPlatformApiParameterTest {

    private static final String GP_AID = "A00000015100";
    private static final String EXPORT_FILE = "org/globalplatform/javacard/globalplatform.exp";

    private static final String SECURE_CHANNEL_APPLET = """
            package com.example.secure;

            import javacard.framework.APDU;
            import javacard.framework.Applet;
            import javacard.framework.ISO7816;
            import org.globalplatform.GPSystem;
            import org.globalplatform.SecureChannel;

            public class SecureApplet extends Applet {
                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new SecureApplet().register();
                }

                public void process(APDU apdu) {
                    if (selectingApplet()) {
                        return;
                    }
                    SecureChannel channel = GPSystem.getSecureChannel();
                    apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, channel.processSecurity(apdu));
                }
            }
            """;

    private static final String PERSONALIZATION_APPLET = """
            package com.example.secure;

            import javacard.framework.APDU;
            import javacard.framework.Applet;
            import org.globalplatform.Personalization;

            public class SecureApplet extends Applet implements Personalization {
                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new SecureApplet().register();
                }

                public void process(APDU apdu) {
                }

                public short processData(byte[] in, short inOffset, short inLength, byte[] out, short outOffset) {
                    return 0;
                }
            }
            """;

    @TempDir
    Path dir;

    private Path apiClasses;

    @BeforeEach
    void compileTheStandInApi() {
        apiClasses = JavaSources.create()
                .add("org/globalplatform/SecureChannel.java", """
                        package org.globalplatform;
                        public interface SecureChannel extends javacard.framework.Shareable {
                            short processSecurity(javacard.framework.APDU apdu) throws javacard.framework.ISOException;
                        }
                        """)
                .add("org/globalplatform/Personalization.java", """
                        package org.globalplatform;
                        public interface Personalization extends javacard.framework.Shareable {
                            short processData(byte[] in, short inOffset, short inLength, byte[] out, short outOffset);
                        }
                        """)
                .add("org/globalplatform/GPSystem.java", """
                        package org.globalplatform;
                        public class GPSystem {
                            public static SecureChannel getSecureChannel() { return null; }
                        }
                        """)
                .compile(dir.resolve("api"));
    }

    @Test
    void lowestSufficientVersionIsImportedWithoutAnyExportFile() throws Exception {
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar(null));

        assertThat(importedVersion(build(runner))).isEqualTo("1.0");
        assertThat(runner.log().messages(Level.INFO)).contains("GlobalPlatform API: org.globalplatform 1.0 (the"
                + " lowest version that has everything the package uses; cards with this version or a later one can"
                + " load the CAP file)");
        assertThat(descriptor()).doesNotContainKey("globalPlatformApi");
    }

    @Test
    void laterInterfaceRaisesTheImportedVersion() throws Exception {
        MojoRunner runner = applet(PERSONALIZATION_APPLET, apiJar(null));

        assertThat(importedVersion(build(runner))).isEqualTo("1.2");
        assertThat(runner.log().messages(Level.INFO)).anyMatch(m -> m.startsWith("GlobalPlatform API:"
                + " org.globalplatform 1.2 (the lowest version"));
    }

    @Test
    void globalPlatformApiElementFixesTheVersion() throws Exception {
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar(null)).configure("globalPlatformApi", "1.5");

        assertThat(importedVersion(build(runner))).isEqualTo("1.5");
        assertThat(runner.log().messages(Level.INFO)).contains("GlobalPlatform API: org.globalplatform 1.5 (fixed"
                + " with <globalPlatformApi>; cards with this version or a later one can load the CAP file)");
        assertThat(descriptor()).containsEntry("globalPlatformApi", "1.5");
    }

    @Test
    void userPropertyFixesTheVersion() throws Exception {
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar(null))
                .property("javacard.globalPlatformApi", "1.6");

        assertThat(importedVersion(build(runner))).isEqualTo("1.6");
    }

    @Test
    void unknownVersionFailsTheBuildWithTheKnownVersions() {
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar(null)).configure("globalPlatformApi", "2.3.1");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid <globalPlatformApi>2.3.1</globalPlatformApi>")
                .hasMessageContaining("1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8")
                .hasMessageContaining("Leave the parameter out");
    }

    @Test
    void fixedVersionThatLacksWhatThePackageUsesFailsAndSaysWhichVersionHasIt() {
        MojoRunner runner = applet(PERSONALIZATION_APPLET, apiJar(null)).configure("globalPlatformApi", "1.0");

        assertThatThrownBy(runner::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Cannot convert package com.example.secure")
                .hasMessageContaining("com.example.secure.SecureApplet: org.globalplatform.Personalization is not"
                        + " available in version 1.0 of the GlobalPlatform Card API (org.globalplatform 1.0); it was"
                        + " introduced in version 1.2")
                .hasMessageContaining("globalPlatformApi: set it to 1.2 or later, or leave it unset");
    }

    @Test
    void exportFileInsideAnApiJarWinsWhenNoVersionIsFixed() throws Exception {
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar("1.7"));

        assertThat(importedVersion(build(runner))).isEqualTo("1.7");
        assertThat(runner.log().messages(Level.INFO)).contains("GlobalPlatform API: org.globalplatform 1.7 (the"
                + " version of the export file found for the package; cards with this version or a later one can load"
                + " the CAP file)");
    }

    @Test
    void fixedVersionIsNotOverriddenByAnExportFileInsideAnApiJar() throws Exception {
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar("1.7")).configure("globalPlatformApi", "1.2");

        assertThat(importedVersion(build(runner))).isEqualTo("1.2");
    }

    @Test
    void configuredExportFileOfAnotherVersionThanTheFixedOneFailsTheBuild() throws Exception {
        Path exports = dir.resolve("exports");
        Files.createDirectories(exports.resolve(EXPORT_FILE).getParent());
        Files.write(exports.resolve(EXPORT_FILE), GlobalPlatformApi.exportFileBytes("1.7", JavaCardVersion.V3_0_5));
        MojoRunner runner = applet(SECURE_CHANNEL_APPLET, apiJar(null)).configure("globalPlatformApi", "1.2")
                .configure("exportPath", List.of(exports.toFile()));

        assertThatThrownBy(runner::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("globalPlatformApi fixes version 1.2 of org.globalplatform")
                .hasMessageContaining("describes version 1.7");
    }

    @Test
    void packageThatDoesNotUseTheApiIsNotToldAboutIt() throws Exception {
        Path classes = Samples.helloApplet().compile(dir.resolve("hello"));
        MojoRunner runner = MojoRunner.forProject(dir.resolve("hello"), classes)
                .configure("packageAid", "A00000006212").configure("globalPlatformApi", "1.3");

        runner.execute();

        assertThat(CapFile.read(dir.resolve("hello/target/sample-applet-1.0.cap")).imports())
                .extracting(CapFile.ImportedPackage::aid).doesNotContain(GP_AID);
        assertThat(runner.log().messages(Level.INFO)).noneMatch(m -> m.contains("GlobalPlatform"));
    }

    /** A project with the applet, compiled against the API jar, which is a provided dependency. */
    private MojoRunner applet(String source, Path apiJar) {
        Path app = dir.resolve("app");
        Path classes = JavaSources.create().classpath(apiClasses)
                .add("com/example/secure/SecureApplet.java", source).compile(app);
        return MojoRunner.forProject(app, classes).configure("packageAid", "A00000006232")
                .artifacts(Set.of(artifact(apiJar)));
    }

    /** The stand-in API classes as a jar, with an export file of the given version at the JCVM 3.1 §5.2 place. */
    private Path apiJar(String exportFileVersion) {
        Path jarFile = dir.resolve("gpapi.jar");
        try (OutputStream out = Files.newOutputStream(jarFile); JarOutputStream jar = new JarOutputStream(out);
             Stream<Path> files = Files.walk(apiClasses)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(apiClasses.relativize(file).toString().replace('\\', '/')));
                jar.write(Files.readAllBytes(file));
                jar.closeEntry();
            }
            if (exportFileVersion != null) {
                jar.putNextEntry(new JarEntry(EXPORT_FILE));
                jar.write(GlobalPlatformApi.exportFileBytes(exportFileVersion, JavaCardVersion.V3_0_5));
                jar.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return jarFile;
    }

    private static Artifact artifact(Path jar) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");
        handler.setAddedToClasspath(true);
        DefaultArtifact artifact = new DefaultArtifact("org.example", "gpapi", "1.0", Artifact.SCOPE_PROVIDED, "jar",
                null, handler);
        artifact.setFile(jar.toFile());
        return artifact;
    }

    private CapFile build(MojoRunner runner) throws Exception {
        runner.execute();
        return CapFile.read(dir.resolve("app/target/sample-applet-1.0.cap"));
    }

    private static String importedVersion(CapFile cap) {
        return cap.imports().stream().filter(p -> p.aid().equals(GP_AID)).map(p -> p.major() + "." + p.minor())
                .findFirst().orElse("not imported");
    }

    /** The build descriptor the goal wrote into the classes directory. */
    private Properties descriptor() throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(dir.resolve("app/classes/META-INF/javacard/com.example.secure.properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
