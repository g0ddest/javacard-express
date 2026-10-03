package name.velikodniy.jcexpress.converter.clinit;

import name.velikodniy.jcexpress.converter.check.Violation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluation of {@code <clinit>} at conversion time (JCVM 3.1 §2.2.4.6, §6.11): the supported
 * subset becomes static field initial values, anything else is reported.
 */
class ClinitInterpreterTest {

    private static final Path CLASSES = Path.of("target/test-classes");
    private static final ClassDesc SELF = ClassDesc.of("p.C");

    private final List<Violation> violations = new ArrayList<>();

    private static ClassModel compiled(String internalName) throws IOException {
        return ClassFile.of().parse(Files.readAllBytes(CLASSES.resolve(internalName + ".class")));
    }

    /** A class p.C with the given static fields and a {@code <clinit>} built by {@code code}. */
    private static ClassModel synthetic(boolean isInterface, Map<String, ClassDesc> fields,
                                        Consumer<CodeBuilder> code) {
        byte[] bytes = ClassFile.of().build(SELF, cb -> {
            cb.withFlags(isInterface
                    ? new AccessFlag[] {AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT}
                    : new AccessFlag[] {AccessFlag.PUBLIC});
            fields.forEach((name, type) -> cb.withField(name, type, ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC));
            addClinit(cb, code);
        });
        return ClassFile.of().parse(bytes);
    }

    private static void addClinit(ClassBuilder cb, Consumer<CodeBuilder> code) {
        cb.withMethodBody("<clinit>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_STATIC, b -> {
            code.accept(b);
            b.return_();
        });
    }

    @Test
    void arrayInitializersAndNonDefaultValuesOfJavacOutput_2_2_4_6() throws IOException {
        Map<String, StaticValue> values = ClinitInterpreter.interpret(
                compiled("com/example/arrinit/ArrInitApplet"), false, violations);

        assertThat(violations).isEmpty();
        assertThat(values).containsExactly(
                Map.entry("HELLO", new StaticValue.PrimitiveArray(3, new int[] {'H', 'e', 'l', 'l', 'o'})),
                Map.entry("SHORTS", new StaticValue.PrimitiveArray(4, new int[] {1, -2, 0x7FFF})),
                Map.entry("flags", new StaticValue.PrimitiveArray(2, new int[] {1, 0, 1})),
                Map.entry("ZEROS", new StaticValue.PrimitiveArray(3, new int[] {0, 0, 0})),
                Map.entry("counter", new StaticValue.Primitive(5)));
    }

    @Test
    void methodCallInStaticInitializerIsRejectedWithSourceLine_2_2_4_6() throws IOException {
        Map<String, StaticValue> values = ClinitInterpreter.interpret(
                compiled("com/example/badclinit/BadClinitApplet"), false, violations);

        assertThat(values).isEmpty();
        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.className()).isEqualTo("com/example/badclinit/BadClinitApplet");
            assertThat(v.context()).matches("<clinit> \\(line \\d+\\)");
            assertThat(v.message()).contains("invokestatic").contains("2.2.4.6");
        });
    }

    @Test
    void libraryPackageMayNotInitializeArrays_2_2_4_6() {
        ClassModel model = synthetic(false, Map.of("A", ConstantDescs.CD_byte.arrayType()), b -> b
                .iconst_2().newarray(TypeKind.BYTE).putstatic(SELF, "A", ConstantDescs.CD_byte.arrayType()));

        assertThat(ClinitInterpreter.interpret(model, true, violations)).isEmpty();
        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("library package"));
    }

    @Test
    void interfaceMayNotInitializeArrays_2_2_4_6() {
        ClassModel model = synthetic(true, Map.of("A", ConstantDescs.CD_short.arrayType()), b -> b
                .iconst_1().newarray(TypeKind.SHORT).putstatic(SELF, "A", ConstantDescs.CD_short.arrayType()));

        ClinitInterpreter.interpret(model, false, violations);

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("interface"));
    }

    @Test
    void interfaceMayOnlyDeclareCompileTimeConstants_2_2_4_6() {
        ClassModel model = synthetic(true, Map.of("O", ConstantDescs.CD_Object, "S", ConstantDescs.CD_short), b -> b
                .aconst_null().putstatic(SELF, "O", ConstantDescs.CD_Object)
                .iconst_3().putstatic(SELF, "S", ConstantDescs.CD_short));

        assertThat(ClinitInterpreter.interpret(model, false, violations)).isEmpty();
        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("interface").contains("p/C.O"));
    }

    @Test
    void onlyFieldsOfTheOwnClassMayBeInitialized_2_2_4_6() {
        ClassModel model = synthetic(false, Map.of("X", ConstantDescs.CD_short), b -> b
                .iconst_1().putstatic(ClassDesc.of("p.Other"), "X", ConstantDescs.CD_short));

        ClinitInterpreter.interpret(model, false, violations);

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("p/Other.X"));
    }

    @Test
    void oneArrayAssignedToTwoFieldsCannotBeRepresented_6_11() {
        ClassDesc bytes = ConstantDescs.CD_byte.arrayType();
        ClassModel model = synthetic(false, Map.of("A", bytes, "B", bytes), b -> b
                .iconst_1().newarray(TypeKind.BYTE).dup().putstatic(SELF, "A", bytes).putstatic(SELF, "B", bytes));

        ClinitInterpreter.interpret(model, false, violations);

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("same array"));
    }

    @Test
    void valuesAreNarrowedToTheFieldTypeAndNullIsKept() {
        ClassModel model = synthetic(false, Map.of("B", ConstantDescs.CD_byte, "O", ConstantDescs.CD_Object), b -> b
                .sipush(200).putstatic(SELF, "B", ConstantDescs.CD_byte)
                .aconst_null().putstatic(SELF, "O", ConstantDescs.CD_Object));

        Map<String, StaticValue> values = ClinitInterpreter.interpret(model, false, violations);

        assertThat(violations).isEmpty();
        assertThat(values).containsEntry("B", new StaticValue.Primitive((byte) 200))
                .containsEntry("O", new StaticValue.Null());
    }

    @Test
    void classWithoutStaticInitializerHasNoValues() throws IOException {
        assertThat(ClinitInterpreter.interpret(compiled("com/example/TestApplet"), false, violations)).isEmpty();
        assertThat(violations).isEmpty();
    }
}
