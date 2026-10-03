package name.velikodniy.jcexpress.converter.translate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The int analysis ({@link IntFlow}, {@link IntRules}) and the translation ({@link MethodTranslator})
 * must not depend on the {@code StackMapTable} attribute.
 *
 * <p>JCVM 3.1 §2.3.1.2.7 does not list {@code StackMapTable} among the class file attributes the
 * conversion relies on, and ProGuard ({@code -dontpreverify}) or ASM ({@code COMPUTE_MAXS}) write class
 * files of version 51 and later without it. For such a method the JDK ClassFile API reports branch
 * targets only through the instructions ({@code BranchInstruction.target()}), not as {@code Label}
 * elements of the element stream; the positions must then come from
 * {@code CodeAttribute.labelToBci}. Every method is parsed afresh for each check, so no earlier
 * {@code target()} call has bound the labels already.
 */
class StackMapIndependentTranslationTest {

    private static final Path FIXTURES = Path.of("target/test-classes/com/example");
    private static final ClassDesc MERGE = ClassDesc.of("probe.Merge");

    static Stream<Path> fixtureClasses() throws IOException {
        try (Stream<Path> files = Files.walk(FIXTURES)) {
            List<Path> classes = files.filter(p -> p.toString().endsWith(".class")).sorted().toList();
            return classes.stream();
        }
    }

    @Test
    void fixturesHaveStackMapTables() throws IOException {
        long withMaps = fixtureClasses().filter(StackMapIndependentTranslationTest::hasStackMap).count();
        assertThat(withMaps).as("fixture classes with a StackMapTable").isGreaterThan(20);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureClasses")
    void intRulesGiveTheSameResultWithoutStackMapTable(Path file) throws IOException {
        byte[] original = Files.readAllBytes(file);
        byte[] stripped = withoutStackMaps(original);
        for (MethodModel m : ClassFile.of().parse(original).methods()) {
            if (m.code().isEmpty()) {
                continue;
            }
            String name = m.methodName().stringValue();
            String desc = m.methodType().stringValue();
            for (boolean intSupport : new boolean[] {false, true}) {
                assertThat(outcome(() -> IntRules.check(method(stripped, name, desc), intSupport)))
                        .as("%s%s, int support %s", name, desc, intSupport)
                        .isEqualTo(outcome(() -> IntRules.check(method(original, name, desc), intSupport)));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureClasses")
    void translationIsTheSameWithoutStackMapTable(Path file) throws IOException {
        byte[] original = Files.readAllBytes(file);
        byte[] stripped = withoutStackMaps(original);
        for (MethodModel m : ClassFile.of().parse(original).methods()) {
            String name = m.methodName().stringValue();
            String desc = m.methodType().stringValue();
            for (boolean intSupport : new boolean[] {false, true}) {
                assertThat(outcome(() -> translate(stripped, name, desc, intSupport)))
                        .as("%s%s, int support %s", name, desc, intSupport)
                        .isEqualTo(outcome(() -> translate(original, name, desc, intSupport)));
            }
        }
    }

    /**
     * {@code aload_0} whose following {@code getfield} is a branch target: the two instructions are not
     * on straight-line code, so {@code getfield_<t>_this} (§7.5.21) must not replace them.
     */
    @Test
    void branchTargetBetweenAload0AndGetfieldPreventsGetfieldThis_7_5_21() throws IOException {
        byte[] bytes = mergeClass(ClassFile.StackMapsOption.DROP_STACK_MAPS, b -> {
            Label self = b.newLabel();
            Label get = b.newLabel();
            b.iload(1).ifeq(self)
                    .aload(2).goto_(get)
                    .labelBinding(self).aload(0)
                    .labelBinding(get).getfield(MERGE, "f", ConstantDescs.CD_short)
                    .ireturn();
        });
        assertThat(hasStackMap(bytes)).isFalse();

        assertThat(JcvmDisassembler.mnemonics(translate(bytes, "pick", PICK, false).bytecode()))
                .containsExactly("sload_1", "ifeq", "aload_2", "goto", "aload_0", "getfield_s", "sreturn");
    }

    /**
     * {@code aload_0} whose following push is a branch target: {@code putfield_<t>_this} (§7.5.76) must
     * not replace {@code aload_0; <push>; putfield}.
     */
    @Test
    void branchTargetAtThePushPreventsPutfieldThis_7_5_76() throws IOException {
        byte[] bytes = mergeClass(ClassFile.StackMapsOption.DROP_STACK_MAPS, b -> {
            Label self = b.newLabel();
            Label value = b.newLabel();
            b.iload(1).ifeq(self)
                    .aload(2).goto_(value)
                    .labelBinding(self).aload(0)
                    .labelBinding(value).iconst_1().putfield(MERGE, "f", ConstantDescs.CD_short)
                    .iconst_0().ireturn();
        });
        assertThat(hasStackMap(bytes)).isFalse();

        assertThat(JcvmDisassembler.mnemonics(translate(bytes, "pick", PICK, false).bytecode()))
                .containsExactly("sload_1", "ifeq", "aload_2", "goto", "aload_0", "sconst_1", "putfield_s",
                        "sconst_0", "sreturn");
    }

    /** Straight-line {@code aload_0; getfield} keeps {@code getfield_<t>_this} without a StackMapTable. */
    @Test
    void straightLineAload0GetfieldStillUsesGetfieldThis_7_5_21() throws IOException {
        byte[] bytes = mergeClass(ClassFile.StackMapsOption.DROP_STACK_MAPS, b -> {
            Label other = b.newLabel();
            b.iload(1).ifeq(other)
                    .aload(0).getfield(MERGE, "f", ConstantDescs.CD_short).ireturn()
                    .labelBinding(other).aload(2).getfield(MERGE, "f", ConstantDescs.CD_short).ireturn();
        });

        assertThat(JcvmDisassembler.mnemonics(translate(bytes, "pick", PICK, false).bytecode()))
                .containsExactly("sload_1", "ifeq", "getfield_s_this", "sreturn", "aload_2", "getfield_s",
                        "sreturn");
    }

    // ── helpers ──

    private static final MethodTypeDesc PICK_TYPE =
            MethodTypeDesc.of(ConstantDescs.CD_short, ConstantDescs.CD_boolean, MERGE);
    private static final String PICK = PICK_TYPE.descriptorString();

    /** Result of a check, or the type and message of the exception it threw. */
    private static Object outcome(Supplier<Object> check) {
        try {
            return check.get();
        } catch (RuntimeException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    /** Parses the class afresh and returns the method (no label of it has been bound yet). */
    private static MethodModel method(byte[] classBytes, String name, String desc) {
        return method(ClassFile.of().parse(classBytes), name, desc);
    }

    private static MethodModel method(ClassModel cm, String name, String desc) {
        return cm.methods().stream()
                .filter(m -> m.methodName().equalsString(name) && m.methodType().equalsString(desc))
                .findFirst().orElseThrow();
    }

    /** Translates in placeholder mode (constant pool operands 0) from a freshly parsed class. */
    private static TranslatedMethod translate(byte[] classBytes, String name, String desc, boolean intSupport) {
        ClassModel cm = ClassFile.of().parse(classBytes);
        return BytecodeTranslator.translate(method(cm, name, desc), cm, null, intSupport, true);
    }

    private static byte[] withoutStackMaps(byte[] classBytes) {
        ClassFile cf = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        byte[] stripped = cf.transformClass(cf.parse(classBytes),
                ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL));
        assertThat(hasStackMap(stripped)).isFalse();
        return stripped;
    }

    private static boolean hasStackMap(Path file) {
        try {
            return hasStackMap(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasStackMap(byte[] classBytes) {
        List<Object> maps = new ArrayList<>();
        for (MethodModel m : ClassFile.of().parse(classBytes).methods()) {
            m.code().flatMap(c -> c.findAttribute(Attributes.stackMapTable())).ifPresent(maps::add);
        }
        return !maps.isEmpty();
    }

    /**
     * {@code probe.Merge} (Java 8 class file) with a short field {@code f} and an instance method
     * {@code short pick(boolean, Merge)} whose body is {@code body}.
     */
    private static byte[] mergeClass(ClassFile.StackMapsOption stackMaps, Consumer<CodeBuilder> body) {
        ClassHierarchyResolver hierarchy = ClassHierarchyResolver.defaultResolver()
                .orElse(ClassHierarchyResolver.of(List.of(), Map.of(MERGE, ConstantDescs.CD_Object)));
        return ClassFile.of(stackMaps, ClassFile.ClassHierarchyResolverOption.of(hierarchy))
                .build(MERGE, cb -> cb.withVersion(ClassFile.JAVA_8_VERSION, 0)
                        .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                        .withField("f", ConstantDescs.CD_short, 0)
                        .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                                b -> b.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                        ConstantDescs.MTD_void).return_())
                        .withMethodBody("pick", PICK_TYPE, ClassFile.ACC_PUBLIC, body));
    }
}
