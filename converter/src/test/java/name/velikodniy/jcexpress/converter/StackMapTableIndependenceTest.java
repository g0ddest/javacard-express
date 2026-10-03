package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conversion must not depend on the {@code StackMapTable} attribute.
 *
 * <p>JCVM 3.1 §2.3.1.2.7 lists the class file attributes the Java Card platform supports (Code,
 * ConstantValue, Exceptions, LocalVariableTable, ...); {@code StackMapTable} is not among them, and
 * tools such as ProGuard with {@code -dontpreverify} or ASM with {@code COMPUTE_MAXS} write class files
 * of version 51 and later without it. The JDK ClassFile API reports the branch targets of such a
 * method only through the instructions, not as label positions in the element stream. The same classes
 * with and without the attribute must give byte-identical CAP and export files, and a branch into the
 * middle of an {@code aload_0; getfield} or {@code aload_0; <push>; putfield} sequence must not be folded
 * into {@code getfield_<t>_this} / {@code putfield_<t>_this} (§7.5.21, §7.5.76).
 */
class StackMapTableIndependenceTest {

    private static final ClassDesc MERGE = ClassDesc.of("probe.Merge");

    static Stream<TestFixtures.Fixture> fixtures() {
        return Stream.concat(TestFixtures.APPLETS.stream(), Stream.of(TestFixtures.LIBRARY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixturesWithoutStackMapTableConvertToTheSameFiles(TestFixtures.Fixture fixture, @TempDir Path dir)
            throws Exception {
        Path stripped = withoutStackMaps(TestFixtures.CLASSES_DIR.resolve("com/example"),
                dir.resolve("com/example"));
        assertThat(stackMapCount(TestFixtures.CLASSES_DIR.resolve("com/example"))).isPositive();
        assertThat(stackMapCount(stripped)).isZero();

        ConverterResult original = fixture.builder(JavaCardVersion.V3_0_5).build().convert();
        ConverterResult withoutMaps = fixture.builder(JavaCardVersion.V3_0_5).classesDirectory(dir).build().convert();

        assertThat(withoutMaps.capFile()).isEqualTo(original.capFile());
        assertThat(withoutMaps.exportFile()).isEqualTo(original.exportFile());
    }

    /** {@code aload_0} followed by a branch target that is a {@code getfield}: no {@code getfield_s_this}. */
    @Test
    void branchTargetBeforeGetfieldIsKept_7_5_21(@TempDir Path dir) throws Exception {
        assertSameCapWithAndWithoutStackMaps(dir, (b, other) -> {
            Label self = b.newLabel();
            Label get = b.newLabel();
            b.iload(1).ifeq(self)
                    .aload(other).goto_(get)
                    .labelBinding(self).aload(0)
                    .labelBinding(get).getfield(MERGE, "f", ConstantDescs.CD_short)
                    .ireturn();
        });
    }

    /** {@code aload_0} followed by a branch target that pushes the value: no {@code putfield_s_this}. */
    @Test
    void branchTargetBeforeThePushOfAPutfieldIsKept_7_5_76(@TempDir Path dir) throws Exception {
        assertSameCapWithAndWithoutStackMaps(dir, (b, other) -> {
            Label self = b.newLabel();
            Label value = b.newLabel();
            b.iload(1).ifeq(self)
                    .aload(other).goto_(value)
                    .labelBinding(self).aload(0)
                    .labelBinding(value).iconst_1().putfield(MERGE, "f", ConstantDescs.CD_short)
                    .iconst_0().ireturn();
        });
    }

    /** Body of {@code short pick(boolean, Merge)}; the second argument is the local of the other object. */
    private interface PickBody {
        void emit(CodeBuilder b, int other);
    }

    private static void assertSameCapWithAndWithoutStackMaps(Path dir, PickBody body) throws Exception {
        Path withMaps = writeMergeClass(dir.resolve("with"), ClassFile.StackMapsOption.GENERATE_STACK_MAPS, body);
        Path withoutMaps = writeMergeClass(dir.resolve("without"), ClassFile.StackMapsOption.DROP_STACK_MAPS, body);
        assertThat(stackMapCount(withMaps)).isPositive();
        assertThat(stackMapCount(withoutMaps)).isZero();

        assertThat(convertProbe(withoutMaps).capFile()).isEqualTo(convertProbe(withMaps).capFile());
    }

    private static ConverterResult convertProbe(Path classes) throws ConverterException {
        return Converter.builder().classesDirectory(classes).packageName("probe")
                .packageAid("F04A435846").packageVersion(1, 0).build().convert();
    }

    /** Writes {@code probe.Merge} (Java 8 class file) with or without a StackMapTable. */
    private static Path writeMergeClass(Path classes, ClassFile.StackMapsOption stackMaps, PickBody body)
            throws IOException {
        ClassHierarchyResolver hierarchy = ClassHierarchyResolver.defaultResolver()
                .orElse(ClassHierarchyResolver.of(List.of(), Map.of(MERGE, ConstantDescs.CD_Object)));
        Consumer<CodeBuilder> pick = b -> body.emit(b, 2);
        byte[] bytes = ClassFile.of(stackMaps, ClassFile.ClassHierarchyResolverOption.of(hierarchy))
                .build(MERGE, cb -> cb.withVersion(ClassFile.JAVA_8_VERSION, 0)
                        .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                        .withField("f", ConstantDescs.CD_short, 0)
                        .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                                b -> b.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                        ConstantDescs.MTD_void).return_())
                        .withMethodBody("pick", MethodTypeDesc.of(ConstantDescs.CD_short, ConstantDescs.CD_boolean,
                                MERGE), ClassFile.ACC_PUBLIC, pick));
        Path file = classes.resolve("probe/Merge.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return classes;
    }

    /** Copies the class files below {@code from} to {@code to}, dropping every StackMapTable. */
    private static Path withoutStackMaps(Path from, Path to) throws IOException {
        ClassFile cf = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassModel model = cf.parse(file);
                byte[] bytes = cf.transformClass(model, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL));
                Path target = to.resolve(from.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            }
        }
        return to;
    }

    private static long stackMapCount(Path classes) throws IOException {
        try (Stream<Path> files = Files.walk(classes)) {
            long count = 0;
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                for (MethodModel m : ClassFile.of().parse(file).methods()) {
                    count += m.code().flatMap(c -> c.findAttribute(Attributes.stackMapTable()))
                            .map(StackMapTableAttribute::entries).map(List::size).orElse(0);
                }
            }
            return count;
        }
    }
}
