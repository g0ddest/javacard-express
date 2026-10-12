package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.thirdparty.AppletCompiler;
import org.globalplatform.GPSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An applet is compiled against the stubs of {@code org.globalplatform} ({@code javacard-express-api}) and runs,
 * on the simulated cards, against the classes of that package in {@code javacard-express-core}. javac copies the
 * constants of the stubs into the applet (JLS 13.1) and the applet's references name methods by name and
 * descriptor, so the two sets of classes must declare the same types, constants and methods. The class files are
 * read as data; the stubs are not on this module's test class path.
 */
class GlobalPlatformStubsAndSimulatorTest {

    private static final String PACKAGE = "org/globalplatform";

    @Test
    void stubsAndSimulatorDeclareTheSameApi() {
        List<String> stubs = surface(AppletCompiler.apiStubs());
        List<String> simulator = surface(simulatorClasses());

        assertThat(simulator).as("the 15 types of the API with their members").hasSizeGreaterThan(150);
        assertThat(stubs).containsExactlyElementsOf(simulator);
    }

    /** Where the simulator's classes of the package are: the jar or classes directory of the core module. */
    private static Path simulatorClasses() {
        try {
            return Path.of(GPSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One line per public type, constant and method of the package in a jar or classes directory, sorted. */
    private static List<String> surface(Path location) {
        try {
            if (Files.isDirectory(location)) {
                return describe(location.resolve(PACKAGE));
            }
            try (FileSystem jar = FileSystems.newFileSystem(location)) {
                return describe(jar.getPath("/" + PACKAGE));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the classes of " + PACKAGE + " in " + location, e);
        }
    }

    private static List<String> describe(Path packageDirectory) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<Path> files = Files.list(packageDirectory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".class")).toList()) {
                ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
                if ((model.flags().flagsMask() & ClassFile.ACC_PUBLIC) != 0) {
                    describe(model, lines);
                }
            }
        }
        return lines.stream().sorted().toList();
    }

    private static void describe(ClassModel model, List<String> lines) {
        String name = model.thisClass().asInternalName();
        int typeFlags = model.flags().flagsMask() & (ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT
                | ClassFile.ACC_FINAL);
        lines.add(String.format("type %s flags=%04X super=%s interfaces=%s", name, typeFlags,
                model.superclass().map(ClassEntry::asInternalName).orElse(""), names(model.interfaces())));
        for (FieldModel field : model.fields()) {
            int flags = field.flags().flagsMask();
            if ((flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0) {
                Object value = field.findAttribute(Attributes.constantValue()).map(ConstantValueAttribute::constant)
                        .map(constant -> (Object) constant.constantValue()).orElse("no constant");
                lines.add(String.format("field %s.%s %s flags=%04X value=%s", name, field.fieldName().stringValue(),
                        field.fieldType().stringValue(), flags, value));
            }
        }
        for (MethodModel method : model.methods()) {
            int flags = method.flags().flagsMask();
            if ((flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0 && (flags & ClassFile.ACC_SYNTHETIC) == 0) {
                List<ClassEntry> exceptions = method.findAttribute(Attributes.exceptions())
                        .map(attribute -> attribute.exceptions()).orElse(List.of());
                lines.add(String.format("method %s.%s%s flags=%04X throws=%s", name, method.methodName().stringValue(),
                        method.methodType().stringValue(), flags, names(exceptions)));
            }
        }
    }

    private static String names(List<ClassEntry> classes) {
        return classes.stream().map(ClassEntry::asInternalName).collect(Collectors.toCollection(TreeSet::new))
                .stream().collect(Collectors.joining(","));
    }
}
