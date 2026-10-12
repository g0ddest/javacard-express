package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.TestFixtures;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks against each other the two things the build of an applet takes from this project for
 * {@code org.globalplatform}: the stubs javac compiles against ({@code javacard-express-api}) and the linking
 * data the converter resolves the compiled references with. What javac accepts must link, under the name and
 * descriptor javac wrote into the class file (JCVM 3.1 §4.3.7, §5.7, §5.9), and an applet built that way must
 * import the lowest sufficient version of the package (§4.5.2).
 */
class GlobalPlatformStubsTest {

    private static final String PACKAGE = GlobalPlatformApi.PACKAGE_NAME + "/";
    /** Flags of JCVM 3.1 §5.9 Table 5-5 that a class file carries with the same values (JVMS Table 4.6-A). */
    private static final int METHOD_FLAGS = ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_STATIC
            | ClassFile.ACC_FINAL | ClassFile.ACC_ABSTRACT;

    private static Map<String, ClassModel> stubs;
    private static ExportFile data;

    @TempDir
    Path exports;

    @BeforeAll
    static void readStubsAndData() {
        stubs = publicStubs();
        data = GlobalPlatformApi.export(GlobalPlatformApi.latest());
    }

    @Test
    void everyClassOfTheLinkingDataIsAPublicTypeOfTheStubs_jcvm31_5_7() {
        List<String> problems = new ArrayList<>();
        for (ExportFile.ClassExport linked : data.classes()) {
            ClassModel stub = stubs.get(linked.name());
            if (stub == null) {
                problems.add(linked.name() + " is not a public type of the stubs");
            } else if (linked.isInterface() != stub.flags().has(AccessFlag.INTERFACE)) {
                problems.add(linked.name() + ": class or interface differs");
            } else {
                stub.interfaces().stream().map(ClassEntry::asInternalName)
                        .filter(name -> !linked.interfaces().contains(name))
                        .forEach(name -> problems.add(linked.name() + ": the linking data lacks the interface " + name));
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyMethodOfTheLinkingDataIsDeclaredByTheStubsWithTheSameDescriptorAndFlags_jcvm31_5_9() {
        List<String> problems = new ArrayList<>();
        for (ExportFile.ClassExport linked : data.classes()) {
            ClassModel stub = stubs.get(linked.name());
            for (ExportFile.MethodExport method : linked.methods()) {
                String key = method.name() + method.descriptor();
                MethodModel declared = stub == null ? null : declared(stub, key);
                if (declared == null) {
                    if (!inheritedFromObject(method)) {
                        problems.add(linked.name() + "." + key + " is not declared by the stubs");
                    }
                } else if ((declared.flags().flagsMask() & METHOD_FLAGS) != method.accessFlags()) {
                    problems.add(String.format("%s.%s: flags %04X in the stubs, %04X in the linking data",
                            linked.name(), key, declared.flags().flagsMask() & METHOD_FLAGS, method.accessFlags()));
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void stubsDeclareNoTypeAndNoMethodThatCannotBeLinked_jcvm31_4_3_5() {
        List<String> problems = new ArrayList<>();
        stubs.forEach((name, stub) -> {
            ExportFile.ClassExport linked = data.classes().stream().filter(c -> c.name().equals(name)).findFirst()
                    .orElse(null);
            if (linked == null) {
                problems.add(name + " has no linking data");
                return;
            }
            for (MethodModel method : stub.methods()) {
                String key = method.methodName().stringValue() + method.methodType().stringValue();
                boolean api = (method.flags().flagsMask() & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0
                        && (method.flags().flagsMask() & ClassFile.ACC_SYNTHETIC) == 0;
                if (api && linked.methods().stream().noneMatch(m -> (m.name() + m.descriptor()).equals(key))) {
                    problems.add(name + "." + key + " has no linking data");
                }
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    void appletCompiledAgainstTheStubsImportsTheLowestSufficientVersion_jcvm31_4_5_2() throws Exception {
        ConverterResult result = TestFixtures.GLOBAL_PLATFORM.convert();

        assertThat(result.imports()).filteredOn(imported -> imported.packageName().equals("org.globalplatform"))
                .singleElement().satisfies(imported -> {
                    assertThat(imported.aid()).isEqualTo("A00000015100");
                    assertThat(imported.version()).isEqualTo("1.0");
                    assertThat(imported.builtIn()).isTrue();
                });
    }

    @Test
    @EnabledIf("name.velikodniy.jcexpress.converter.translate.OracleVerifier#available")
    void capFileOfThatAppletPassesTheVerifierWithTheGeneratedExportFile() throws Exception {
        ConverterResult result = TestFixtures.GLOBAL_PLATFORM.convert();
        Path exportFile = Files.write(exports.resolve("globalplatform.exp"),
                GlobalPlatformApi.exportFileBytes("1.0", JavaCardVersion.V3_0_5));

        String output = OracleVerifier.verify(result.capFile(), exportFile);

        assertThat(output).as("verifycap:%n%s", output)
                .contains("Verification completed with 0 warnings and 0 errors");
    }

    /**
     * Finds a method in a stub type or, for an interface, in the interfaces of the package it extends: the export
     * of an interface lists the methods of its superinterfaces too (JCVM 3.1 §4.3.7.6, §5.9).
     */
    private static MethodModel declared(ClassModel stub, String nameAndDescriptor) {
        for (MethodModel method : stub.methods()) {
            if ((method.methodName().stringValue() + method.methodType().stringValue()).equals(nameAndDescriptor)) {
                return method;
            }
        }
        for (ClassEntry extended : stub.interfaces()) {
            ClassModel parent = stubs.get(extended.asInternalName());
            MethodModel inherited = parent == null ? null : declared(parent, nameAndDescriptor);
            if (inherited != null) {
                return inherited;
            }
        }
        return null;
    }

    /** A class's export lists the public virtual methods it inherits from Object too (JCVM 3.1 §5.9). */
    private static boolean inheritedFromObject(ExportFile.MethodExport method) {
        return method.name().equals("equals") && method.descriptor().equals("(Ljava/lang/Object;)Z");
    }

    /** The public types of the stub package, by internal name, read from where the test class path has them. */
    private static Map<String, ClassModel> publicStubs() {
        URL marker = GlobalPlatformStubsTest.class.getClassLoader().getResource(PACKAGE + "GPSystem.class");
        assertThat(marker).as("javacard-express-api with org.globalplatform on the test class path").isNotNull();
        try {
            if ("jar".equals(marker.getProtocol())) {
                String jar = marker.toExternalForm();
                URI file = new URI(jar.substring("jar:".length(), jar.indexOf("!/")));
                try (FileSystem zip = FileSystems.newFileSystem(Path.of(file))) {
                    return read(zip.getPath("/" + PACKAGE));
                }
            }
            return read(Path.of(marker.toURI()).getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, ClassModel> read(Path directory) throws IOException {
        Map<String, ClassModel> types = new TreeMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".class")).toList()) {
                ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
                if (model.flags().has(AccessFlag.PUBLIC)) {
                    types.put(model.thisClass().asInternalName(), model);
                }
            }
        }
        return types;
    }
}
