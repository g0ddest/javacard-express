package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubsetCheckerTest {

    @Test
    void shouldDetectForbiddenFloat() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations)
                .anyMatch(v -> v.message().contains("float"));
    }

    @Test
    void shouldDetectForbiddenDouble() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations)
                .anyMatch(v -> v.message().contains("double"));
    }

    @Test
    void shouldDetectForbiddenLong() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations)
                .anyMatch(v -> v.message().contains("long"));
    }

    @Test
    void shouldDetectForbiddenFieldTypes() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations)
                .filteredOn(v -> v.context().startsWith("field "))
                .extracting(Violation::context)
                .contains("field floatField", "field doubleField", "field longField");
    }

    @Test
    void shouldDetectForbiddenMethodReturnTypes() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        // getFloat()F, getDouble()D, getLong()J
        assertThat(violations)
                .filteredOn(v -> v.bci() < 0) // descriptor-level, not bytecode
                .anyMatch(v -> v.context().contains("getFloat"));
        assertThat(violations)
                .filteredOn(v -> v.bci() < 0)
                .anyMatch(v -> v.context().contains("getDouble"));
        assertThat(violations)
                .filteredOn(v -> v.bci() < 0)
                .anyMatch(v -> v.context().contains("getLong"));
    }

    @Test
    void shouldDetectForbiddenOpcodes() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        // Should have bytecode-level violations (bci >= 0)
        assertThat(violations)
                .filteredOn(v -> v.bci() >= 0)
                .isNotEmpty();
    }

    @Test
    void shouldPassCleanApplet() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldProduceReadableMessages() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        // Every violation should have a non-empty toString
        for (Violation v : violations) {
            assertThat(v.toString()).isNotBlank();
            assertThat(v.className()).isEqualTo("com/example/forbidden/ForbiddenUsages");
        }
    }

    @Test
    void forbiddenOpcodesShouldReportKnownOpcodes() {
        // Spot-check a few known forbidden opcodes
        assertThat(ForbiddenOpcodes.isForbidden(0x62)).isTrue();  // fadd
        assertThat(ForbiddenOpcodes.reason(0x62)).contains("float");

        assertThat(ForbiddenOpcodes.isForbidden(0x63)).isTrue();  // dadd
        assertThat(ForbiddenOpcodes.reason(0x63)).contains("double");

        assertThat(ForbiddenOpcodes.isForbidden(0x61)).isTrue();  // ladd
        assertThat(ForbiddenOpcodes.reason(0x61)).contains("long");

        assertThat(ForbiddenOpcodes.isForbidden(0xBA)).isTrue();  // invokedynamic
        assertThat(ForbiddenOpcodes.isForbidden(0xC2)).isTrue();  // monitorenter
    }

    @Test
    void allowedOpcodesShouldNotBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x00)).isFalse(); // nop
        assertThat(ForbiddenOpcodes.isForbidden(0x60)).isFalse(); // iadd
        assertThat(ForbiddenOpcodes.isForbidden(0xB6)).isFalse(); // invokevirtual
        assertThat(ForbiddenOpcodes.isForbidden(0xB1)).isFalse(); // return
    }

    @Test
    void forbiddenTypesShouldDetectPrimitives() {
        assertThat(ForbiddenTypes.checkDescriptor("F")).contains("float");
        assertThat(ForbiddenTypes.checkDescriptor("D")).contains("double");
        assertThat(ForbiddenTypes.checkDescriptor("J")).contains("long");
        assertThat(ForbiddenTypes.checkDescriptor("I")).isNull(); // int is OK
        assertThat(ForbiddenTypes.checkDescriptor("S")).isNull(); // short is OK
        assertThat(ForbiddenTypes.checkDescriptor("B")).isNull(); // byte is OK
    }

    @Test
    void forbiddenTypesShouldDetectForbiddenClasses() {
        assertThat(ForbiddenTypes.checkDescriptor("Ljava/lang/String;"))
                .contains("java/lang/String");
        assertThat(ForbiddenTypes.checkDescriptor("Ljava/lang/Thread;"))
                .contains("java/lang/Thread");
        // Allowed classes
        assertThat(ForbiddenTypes.checkDescriptor("Ljavacard/framework/Applet;"))
                .isNull();
    }

    @Test
    void forbiddenTypesShouldDetectInMethodDescriptors() {
        // Method taking a long parameter
        assertThat(ForbiddenTypes.checkDescriptor("(JJ)I")).contains("long");
        // Method returning float
        assertThat(ForbiddenTypes.checkDescriptor("()F")).contains("float");
        // Clean method
        assertThat(ForbiddenTypes.checkDescriptor("([BSS)V")).isNull();
    }

    // ── Additional tests for 25+ coverage ──

    @Test
    void emptyClassList_shouldReturnNoViolations() {
        List<Violation> violations = SubsetChecker.check(List.of());
        assertThat(violations).isEmpty();
    }

    @Test
    void multipleClasses_shouldCheckAll() throws IOException {
        ClassInfo clean = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        ClassInfo dirty = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/forbidden/ForbiddenUsages.class"));

        List<Violation> violations = SubsetChecker.check(List.of(clean, dirty));

        // Only ForbiddenUsages should produce violations
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .allMatch(v -> v.className().equals("com/example/forbidden/ForbiddenUsages"));
    }

    @Test
    void violationList_shouldBeImmutable() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations).isUnmodifiable();
    }

    @Test
    void forbiddenOpcodes_subroutines_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0xA8)).isTrue();  // jsr
        assertThat(ForbiddenOpcodes.reason(0xA8)).contains("subroutine");
        assertThat(ForbiddenOpcodes.isForbidden(0xA9)).isTrue();  // ret
        assertThat(ForbiddenOpcodes.reason(0xA9)).contains("subroutine");
    }

    @Test
    void forbiddenOpcodes_monitorExit_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0xC3)).isTrue();  // monitorexit
        assertThat(ForbiddenOpcodes.reason(0xC3)).contains("threading");
    }

    @Test
    void forbiddenOpcodes_wideJumps_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0xC8)).isTrue();  // goto_w
        assertThat(ForbiddenOpcodes.reason(0xC8)).contains("wide");
        assertThat(ForbiddenOpcodes.isForbidden(0xC9)).isTrue();  // jsr_w
        assertThat(ForbiddenOpcodes.reason(0xC9)).contains("wide");
    }

    @Test
    void forbiddenOpcodes_ldc2w_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x14)).isTrue();  // ldc2_w
        assertThat(ForbiddenOpcodes.reason(0x14)).contains("long/double");
    }

    @Test
    void forbiddenOpcodes_longConstants_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x09)).isTrue();  // lconst_0
        assertThat(ForbiddenOpcodes.isForbidden(0x0A)).isTrue();  // lconst_1
    }

    @Test
    void forbiddenOpcodes_floatConstants_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x0B)).isTrue();  // fconst_0
        assertThat(ForbiddenOpcodes.isForbidden(0x0C)).isTrue();  // fconst_1
        assertThat(ForbiddenOpcodes.isForbidden(0x0D)).isTrue();  // fconst_2
    }

    @Test
    void forbiddenOpcodes_doubleConstants_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x0E)).isTrue();  // dconst_0
        assertThat(ForbiddenOpcodes.isForbidden(0x0F)).isTrue();  // dconst_1
    }

    @Test
    void forbiddenOpcodes_longLoadStore_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x16)).isTrue();  // lload
        assertThat(ForbiddenOpcodes.isForbidden(0x37)).isTrue();  // lstore
        assertThat(ForbiddenOpcodes.isForbidden(0x1E)).isTrue();  // lload_0
        assertThat(ForbiddenOpcodes.isForbidden(0x3F)).isTrue();  // lstore_0
    }

    @Test
    void forbiddenOpcodes_conversions_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x85)).isTrue();  // i2l
        assertThat(ForbiddenOpcodes.isForbidden(0x86)).isTrue();  // i2f
        assertThat(ForbiddenOpcodes.isForbidden(0x87)).isTrue();  // i2d
    }

    @Test
    void forbiddenOpcodes_comparisons_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0x94)).isTrue();  // lcmp
        assertThat(ForbiddenOpcodes.isForbidden(0x95)).isTrue();  // fcmpl
        assertThat(ForbiddenOpcodes.isForbidden(0x96)).isTrue();  // fcmpg
        assertThat(ForbiddenOpcodes.isForbidden(0x97)).isTrue();  // dcmpl
        assertThat(ForbiddenOpcodes.isForbidden(0x98)).isTrue();  // dcmpg
    }

    @Test
    void forbiddenOpcodes_returns_shouldBeForbidden() {
        assertThat(ForbiddenOpcodes.isForbidden(0xAD)).isTrue();  // lreturn
        assertThat(ForbiddenOpcodes.isForbidden(0xAE)).isTrue();  // freturn
        assertThat(ForbiddenOpcodes.isForbidden(0xAF)).isTrue();  // dreturn
    }

    @Test
    void instructionLength_fixedLengthOpcodes() {
        assertThat(ForbiddenOpcodes.instructionLength(0x00)).isEqualTo(1); // nop
        assertThat(ForbiddenOpcodes.instructionLength(0x10)).isEqualTo(2); // bipush
        assertThat(ForbiddenOpcodes.instructionLength(0x11)).isEqualTo(3); // sipush
        assertThat(ForbiddenOpcodes.instructionLength(0xC5)).isEqualTo(4); // multianewarray
        assertThat(ForbiddenOpcodes.instructionLength(0xB9)).isEqualTo(5); // invokeinterface
    }

    @Test
    void instructionLength_variableLength_shouldReturnZero() {
        assertThat(ForbiddenOpcodes.instructionLength(0xAA)).isZero(); // tableswitch
        assertThat(ForbiddenOpcodes.instructionLength(0xAB)).isZero(); // lookupswitch
        assertThat(ForbiddenOpcodes.instructionLength(0xC4)).isZero(); // wide
    }

    @Test
    void forbiddenTypes_boxedWrappers_shouldBeForbidden() {
        assertThat(ForbiddenTypes.checkDescriptor("Ljava/lang/Float;")).isNotNull();
        assertThat(ForbiddenTypes.checkDescriptor("Ljava/lang/Double;")).isNotNull();
        assertThat(ForbiddenTypes.checkDescriptor("Ljava/lang/Long;")).isNotNull();
    }

    @Test
    void forbiddenTypes_allowedPrimitives() {
        assertThat(ForbiddenTypes.checkDescriptor("Z")).isNull(); // boolean
        assertThat(ForbiddenTypes.checkDescriptor("C")).isNull(); // char
        assertThat(ForbiddenTypes.checkDescriptor("V")).isNull(); // void
    }

    @Test
    void forbiddenTypes_arrayOfForbidden_shouldBeDetected() {
        // Array of doubles: [D
        assertThat(ForbiddenTypes.checkDescriptor("[D")).contains("double");
        // Array of floats: [F
        assertThat(ForbiddenTypes.checkDescriptor("[F")).contains("float");
        // Array of longs: [J
        assertThat(ForbiddenTypes.checkDescriptor("[J")).contains("long");
    }

    @Test
    void forbiddenTypes_arrayOfAllowed_shouldPass() {
        assertThat(ForbiddenTypes.checkDescriptor("[B")).isNull(); // byte[]
        assertThat(ForbiddenTypes.checkDescriptor("[S")).isNull(); // short[]
        assertThat(ForbiddenTypes.checkDescriptor("[I")).isNull(); // int[]
    }

    @Test
    void forbiddenTypes_checkInternalName_allowed() {
        assertThat(ForbiddenTypes.checkInternalName("javacard/framework/Applet")).isNull();
        assertThat(ForbiddenTypes.checkInternalName("java/lang/Object")).isNull();
    }

    @Test
    void forbiddenTypes_checkInternalName_forbidden() {
        assertThat(ForbiddenTypes.checkInternalName("java/lang/String")).isNotNull();
        assertThat(ForbiddenTypes.checkInternalName("java/lang/Thread")).isNotNull();
    }

    @Test
    void syntheticCleanClass_noViolations() {
        // Create a synthetic class with only allowed features
        ClassInfo ci = new ClassInfo(
                "com/test/Clean", "java/lang/Object", List.of(),
                0x0001, // ACC_PUBLIC
                List.of(new MethodInfo("m", "(BS)V", 0x0001, 2, 3, new byte[]{(byte) 0xB1}, List.of())),
                List.of(new FieldInfo("f", "S", 0x0001, null))
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));
        assertThat(violations).isEmpty();
    }

    @Test
    void syntheticClassWithForbiddenField_shouldDetect() {
        ClassInfo ci = new ClassInfo(
                "com/test/Bad", "java/lang/Object", List.of(),
                0x0001,
                List.of(),
                List.of(new FieldInfo("badField", "D", 0x0001, null))
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).context()).isEqualTo("field badField");
        assertThat(violations.get(0).message()).contains("double");
    }

    @Test
    void syntheticClassWithForbiddenSuperclass_shouldDetect() {
        ClassInfo ci = new ClassInfo(
                "com/test/BadSuper", "java/lang/Thread", List.of(),
                0x0001,
                List.of(),
                List.of()
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).context()).isEqualTo("superclass");
    }

    @Test
    void syntheticClassWithForbiddenInterface_shouldDetect() {
        ClassInfo ci = new ClassInfo(
                "com/test/BadIface", "java/lang/Object",
                List.of("java/lang/Thread"),
                0x0001,
                List.of(),
                List.of()
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).context()).contains("interface");
    }

    @Test
    void classWithNullSuperclass_shouldNotThrow() {
        ClassInfo ci = new ClassInfo(
                "com/test/Root", null, List.of(),
                0x0001,
                List.of(),
                List.of()
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));
        assertThat(violations).isEmpty();
    }

    @Test
    void methodWithNullBytecode_shouldNotThrow() {
        ClassInfo ci = new ClassInfo(
                "com/test/Native", "java/lang/Object", List.of(),
                0x0001,
                List.of(new MethodInfo("nativeMethod", "()V", 0x0100, 0, 0, null, List.of())),
                List.of()
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));
        assertThat(violations).isEmpty();
    }

    @Test
    void methodWithEmptyBytecode_shouldNotThrow() {
        ClassInfo ci = new ClassInfo(
                "com/test/Abstract", "java/lang/Object", List.of(),
                0x0001,
                List.of(new MethodInfo("abstractMethod", "()V", 0x0400, 0, 0, new byte[0], List.of())),
                List.of()
        );

        List<Violation> violations = SubsetChecker.check(List.of(ci));
        assertThat(violations).isEmpty();
    }
}
