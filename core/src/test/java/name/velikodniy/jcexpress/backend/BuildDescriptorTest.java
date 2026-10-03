package name.velikodniy.jcexpress.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.model.ModelApplet;
import name.velikodniy.jcexpress.model.built.BuiltApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The build descriptor the Maven plugin writes ({@code META-INF/javacard/<package>.properties}), read from a classes
 * directory or a jar, and the AID prefix of a run that it determines.
 */
class BuildDescriptorTest {

    private static final String DESCRIPTOR = """
            # How javacard-express-maven-plugin built package com.example.wallet
            package=com.example.wallet
            packageAid=A00000006212
            packageVersion=2.7
            javaCardVersion=3.0.4
            supportInt32=true
            export=false
            cap=com/example/wallet/javacard/wallet.cap
            applet.com.example.wallet.WalletApplet=A0000000621201
            applet.com.example.wallet.Wallet$Admin=A0000000621202
            project=com.example:wallet
            """;

    @TempDir
    Path dir;

    @Test
    void isReadFromAClassesDirectory() throws IOException {
        Path classes = classes(DESCRIPTOR);

        BuildDescriptor build = BuildDescriptor.read(classes, "com.example.wallet").orElseThrow();

        assertThat(build.packageAid()).isEqualTo(AID.fromHex("A00000006212"));
        assertThat(build.majorVersion()).isEqualTo(2);
        assertThat(build.minorVersion()).isEqualTo(7);
        assertThat(build.javaCardVersion()).isEqualTo("3.0.4");
        assertThat(build.supportInt32()).isTrue();
        assertThat(build.applets()).isEqualTo(Map.of("com.example.wallet.WalletApplet", AID.fromHex("A0000000621201"),
                "com.example.wallet.Wallet$Admin", AID.fromHex("A0000000621202")));
        assertThat(build.project()).isEqualTo("com.example:wallet");
    }

    @Test
    void isReadFromAJar() throws IOException {
        Path jar = dir.resolve("wallet-1.0.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/javacard/com.example.wallet.properties"));
            out.write(DESCRIPTOR.getBytes(StandardCharsets.ISO_8859_1));
            out.closeEntry();
        }

        assertThat(BuildDescriptor.read(jar, "com.example.wallet")).map(BuildDescriptor::project)
                .contains("com.example:wallet");
        assertThat(BuildDescriptor.read(jar, "com.example.other")).isEmpty();
    }

    @Test
    void aPackageWithoutDescriptorHasNone() throws IOException {
        assertThat(BuildDescriptor.read(classes(DESCRIPTOR), "com.example.other")).isEmpty();
        assertThat(BuildDescriptor.read(dir.resolve("missing"), "com.example.wallet")).isEmpty();
    }

    @Test
    void anInvalidDescriptorNamesTheFileAndTheProblem() throws IOException {
        Path wrongPackage = classes(DESCRIPTOR.replace("package=com.example.wallet", "package=com.example.x"));
        assertThatThrownBy(() -> BuildDescriptor.read(wrongPackage, "com.example.wallet"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("com.example.wallet.properties")
                .hasMessageContaining("describes package com.example.x").hasMessageContaining("Rebuild");

        Files.writeString(wrongPackage.resolve("META-INF/javacard/com.example.wallet.properties"),
                DESCRIPTOR.replace("packageAid=A00000006212\n", ""));
        assertThatThrownBy(() -> BuildDescriptor.read(wrongPackage, "com.example.wallet"))
                .hasMessageContaining("packageAid is missing");
    }

    @Test
    void anAppletClassFindsTheDescriptorWhereItWasLoadedFrom() {
        assertThat(BuildDescriptor.of(BuiltApplet.class)).map(BuildDescriptor::project)
                .contains("com.example:built-applets");
        assertThat(BuildDescriptor.of(ModelApplet.class)).isEmpty();
    }

    @Test
    void aProjectPrefixIsF0AndFourBytesOfTheProjectsHash() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("project com.example:wallet".getBytes(StandardCharsets.UTF_8));

        assertThat(AidScheme.forProject("com.example:wallet").prefix())
                .isEqualTo("F0" + HexFormat.of().withUpperCase().formatHex(digest, 0, 4));
        assertThat(AidScheme.forProject("com.example:wallet").prefix())
                .isNotEqualTo(AidScheme.forProject("com.example:loyalty").prefix());
    }

    @Test
    void theRunPrefixIsTheSettingThenTheProjectOfTheFirstBuiltAppletThenTheDefault() {
        CardRequest built = new CardRequest(BuildDescriptorTest.class, key -> Optional.empty(),
                List.of(ModelApplet.class, BuiltApplet.class));
        CardRequest set = new CardRequest(BuildDescriptorTest.class,
                key -> key.equals(AidScheme.PREFIX_SETTING) ? Optional.of("F011223344") : Optional.empty(),
                List.of(BuiltApplet.class));
        CardRequest none = new CardRequest(BuildDescriptorTest.class, key -> Optional.empty(),
                List.of(ModelApplet.class));

        assertThat(built.aidScheme().prefix()).isEqualTo(AidScheme.forProject("com.example:built-applets").prefix());
        assertThat(built.buildDescriptors()).map(BuildDescriptor::packageName)
                .containsExactly(BuiltApplet.class.getPackageName());
        assertThat(set.aidScheme().prefix()).isEqualTo("F011223344");
        assertThat(none.aidScheme().prefix()).isEqualTo(AidScheme.DEFAULT_PREFIX);
    }

    private Path classes(String descriptor) throws IOException {
        Path classes = dir.resolve("classes");
        Path file = classes.resolve("META-INF/javacard/com.example.wallet.properties");
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(descriptor.getBytes(StandardCharsets.ISO_8859_1));
        }
        return classes;
    }
}
