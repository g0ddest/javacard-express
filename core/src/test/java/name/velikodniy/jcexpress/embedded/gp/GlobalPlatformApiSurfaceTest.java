package name.velikodniy.jcexpress.embedded.gp;

import org.globalplatform.GPSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classes of {@code org.globalplatform} in this module have exactly the public surface of version 1.8 of the
 * GlobalPlatform Card API: applets are compiled against stubs of that API, javac copies the constants into them,
 * and they link against names and descriptors, so the classes the simulated card runs must agree in all of them.
 * The expected surface is the resource {@code org-globalplatform-1.8-surface.txt}, a list of facts of the published
 * API.
 */
class GlobalPlatformApiSurfaceTest {

    private static final String RESOURCE = "org-globalplatform-1.8-surface.txt";

    @Test
    void theRuntimeClassesHaveTheSurfaceOfApiVersion1_8() throws IOException {
        List<String> expected;
        try (InputStream in = GlobalPlatformApiSurfaceTest.class.getResourceAsStream(RESOURCE)) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.startsWith("#") && !line.isBlank()).sorted().toList();
        }

        List<String> actual = new ArrayList<>();
        for (Path classFile : classFilesOfThePackage()) {
            describe(ClassFile.of().parse(Files.readAllBytes(classFile)), actual);
        }

        assertThat(actual.stream().sorted().toList()).containsExactlyElementsOf(expected);
    }

    @Test
    void thePackageHasThe15TypesOfTheApiAndNoOther() {
        assertThat(classFilesOfThePackage()).extracting(path -> path.getFileName().toString()).containsExactlyInAnyOrder(
                "AdminSessionListener.class", "AdministrationSession.class", "Application.class", "Authority.class",
                "AuthoritySignature.class", "CVM.class", "GPRegistryEntry.class", "GPSystem.class",
                "GlobalService.class", "HTTPAdministration.class", "HTTPReportListener.class",
                "Personalization.class", "SecureChannel.class", "SecureChannelx.class", "SecureChannelx2.class");
    }

    /** The class files next to GPSystem in the classes directory of this module. */
    private static List<Path> classFilesOfThePackage() {
        try {
            Path root = Path.of(GPSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (Stream<Path> files = Files.list(root.resolve("org/globalplatform"))) {
                return files.filter(file -> file.toString().endsWith(".class")).sorted().toList();
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void describe(ClassModel model, List<String> lines) {
        String name = model.thisClass().asInternalName();
        boolean isInterface = (model.flags().flagsMask() & ClassFile.ACC_INTERFACE) != 0;
        assertThat(model.flags().flagsMask() & ClassFile.ACC_PUBLIC).as(name + " is public").isNotZero();
        String supers = isInterface
                ? model.interfaces().stream().map(ClassEntry::asInternalName).sorted().collect(Collectors.joining(","))
                : model.superclass().map(ClassEntry::asInternalName).orElse("");
        lines.add("type " + name + (isInterface ? " interface" : " class") + " supers=" + supers);
        for (FieldModel field : model.fields()) {
            int flags = field.flags().flagsMask();
            if ((flags & ClassFile.ACC_PUBLIC) != 0) {
                assertThat(flags & (ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)).as(name + "." + field.fieldName())
                        .isEqualTo(ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
                Object value = field.findAttribute(Attributes.constantValue())
                        .map(ConstantValueAttribute::constant).map(constant -> (Object) constant.constantValue())
                        .orElse("no constant");
                lines.add("field " + name + " " + field.fieldName().stringValue() + " "
                        + field.fieldType().stringValue() + " " + value);
            }
        }
        for (MethodModel method : model.methods()) {
            describe(name, method, lines);
        }
    }

    private static void describe(String type, MethodModel method, List<String> lines) {
        int flags = method.flags().flagsMask();
        if ((flags & ClassFile.ACC_PUBLIC) == 0 || (flags & ClassFile.ACC_SYNTHETIC) != 0) {
            return;
        }
        String modifiers = "public" + ((flags & ClassFile.ACC_ABSTRACT) != 0 ? " abstract" : "")
                + ((flags & ClassFile.ACC_STATIC) != 0 ? " static" : "");
        String exceptions = method.findAttribute(Attributes.exceptions()).stream()
                .flatMap(attribute -> attribute.exceptions().stream()).map(ClassEntry::asInternalName)
                .collect(Collectors.toCollection(TreeSet::new)).stream().collect(Collectors.joining(","));
        lines.add("method " + type + " " + method.methodName().stringValue() + method.methodType().stringValue()
                + " " + modifiers + " throws=" + exceptions);
    }
}
