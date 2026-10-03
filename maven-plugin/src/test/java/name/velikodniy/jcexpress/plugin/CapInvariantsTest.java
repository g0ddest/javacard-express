package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.CapFile;
import name.velikodniy.jcexpress.plugin.testing.CapInvariants;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.Samples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plugin's CAP files obey the structural rules of {@link CapInvariants}, and the checker does
 * catch the defects the plugin used to produce (so a passing check means something).
 */
class CapInvariantsTest {

    @TempDir
    Path dir;

    @Test
    void pluginOutputsObeyTheRules() throws Exception {
        JavaSources[] samples = {Samples.walletPackage(), Samples.libraryPackage(),
                Samples.shareableInterfacePackage(), Samples.helloApplet()};
        for (int i = 0; i < samples.length; i++) {
            Path project = dir.resolve("sample" + i);
            MojoRunner.forProject(project, samples[i].compile(project)).execute();

            assertThat(CapInvariants.violations(CapFile.read(project.resolve("target/sample-applet-1.0.cap"))))
                    .isEmpty();
        }
    }

    @Test
    void checkerDetectsAnAppletAidWithAnotherRidAndAnInstallOffsetAtAConstructor() throws Exception {
        MojoRunner.forProject(dir, Samples.walletPackage().compile(dir)).execute();
        byte[] valid = Files.readAllBytes(dir.resolve("target/sample-applet-1.0.cap"));
        int constructor = CapFile.read(valid).classDescriptors().stream().flatMap(c -> c.methods().stream())
                .filter(m -> (m.flags() & CapFile.MethodDescriptor.ACC_INIT) != 0)
                .findFirst().orElseThrow().methodOffset();
        // The defects the plugin used to produce (an applet AID with another RID than the package, the
        // install_method_offset of the abstract base applet's constructor), patched into a valid CAP file
        // because the converter now refuses to write them. Applet component (JCVM 3.1 6.6): tag u1,
        // size u2, count u1, then AID_length u1, AID, install_method_offset u2.
        byte[] broken = patchComponent(valid, "Applet.cap", applet -> {
            applet[5] ^= 0x50; // first RID byte of the first applet AID
            int offsetAt = 5 + (applet[4] & 0xFF);
            applet[offsetAt] = (byte) (constructor >> 8);
            applet[offsetAt + 1] = (byte) constructor;
        });

        assertThat(CapInvariants.violations(CapFile.read(broken)))
                .anyMatch(p -> p.startsWith("4.2.2.2/6.6"))
                .anyMatch(p -> p.startsWith("6.6: install_method_offset"));
    }

    /** Copies a CAP file (a JAR), letting {@code patch} change the bytes of one component. */
    private static byte[] patchComponent(byte[] cap, String component, Consumer<byte[]> patch) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(cap));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                byte[] data = in.readAllBytes();
                if (entry.getName().endsWith("/" + component)) {
                    patch.accept(data);
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(data);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
