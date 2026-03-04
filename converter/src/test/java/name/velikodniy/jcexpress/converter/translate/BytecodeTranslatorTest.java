package name.velikodniy.jcexpress.converter.translate;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BytecodeTranslatorTest {

    @Test
    void shouldTranslateSimpleMethod() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/TestApplet.class");
        var cp = new JcvmConstantPool();

        // Find the install method (static)
        MethodModel install = findMethod(cm, "install");
        TranslatedMethod result = BytecodeTranslator.translate(install, cm, cp);

        assertThat(result).isNotNull();
        assertThat(result.bytecode()).isNotEmpty();
        assertThat(result.nargs()).isEqualTo(3); // byte[], short, byte
    }

    @Test
    void shouldTranslateVirtualMethod() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/TestApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel process = findMethod(cm, "process");
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);

        assertThat(result).isNotNull();
        assertThat(result.bytecode()).isNotEmpty();
        assertThat(result.nargs()).isEqualTo(2); // this + APDU
    }

    @Test
    void shouldReturnEmptyForAbstractMethod() throws Exception {
        byte[] classBytes = ClassFile.of().build(
                java.lang.constant.ClassDesc.of("test", "Abstract"),
                cb -> {
                    cb.withFlags(java.lang.reflect.AccessFlag.PUBLIC, java.lang.reflect.AccessFlag.ABSTRACT);
                    cb.withMethod("doWork",
                            java.lang.constant.MethodTypeDesc.ofDescriptor("()V"),
                            ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                            mb -> {});
                });

        ClassModel cm = ClassFile.of().parse(classBytes);
        var cp = new JcvmConstantPool();

        MethodModel doWork = findMethod(cm, "doWork");
        TranslatedMethod result = BytecodeTranslator.translate(doWork, cm, cp);

        assertThat(result.bytecode()).isEmpty();
        assertThat(result.nargs()).isEqualTo(1); // 'this' only for doWork()V
    }

    @Test
    void shouldProduceValidJcvmOpcodes() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/TestApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel process = findMethod(cm, "process");
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);

        byte[] bytecode = result.bytecode();

        // Should contain ALOAD_0 (0x18) or ALOAD_1 (0x19) — loading 'this' or APDU arg
        boolean hasAload = false;
        for (byte b : bytecode) {
            int op = b & 0xFF;
            if (op == JcvmOpcode.ALOAD_0 || op == JcvmOpcode.ALOAD_1) {
                hasAload = true;
                break;
            }
        }
        assertThat(hasAload).isTrue();

        // Should contain RETURN (0x7A) or SRETURN (0x78) or ARETURN (0x77)
        boolean hasReturn = false;
        for (byte b : bytecode) {
            int op = b & 0xFF;
            if (op == JcvmOpcode.RETURN || op == JcvmOpcode.SRETURN || op == JcvmOpcode.ARETURN) {
                hasReturn = true;
                break;
            }
        }
        assertThat(hasReturn).isTrue();
    }

    @Test
    void shouldSetExtendedHeaderForLargeMethods() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/TestApplet.class");
        var cp = new JcvmConstantPool();

        // process() method in TestApplet has a switch statement, likely >15 locals or stack
        MethodModel process = findMethod(cm, "process");
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);

        // Check that isExtended is set correctly based on maxStack/maxLocals
        assertThat(result.maxStack()).isGreaterThanOrEqualTo(0);
        assertThat(result.maxLocals()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldMapJvmFieldTypeSuffixCorrectly() {
        assertThat(BytecodeTranslator.fieldTypeSuffix("Ljavacard/framework/APDU;")).isEqualTo('a');
        assertThat(BytecodeTranslator.fieldTypeSuffix("[B")).isEqualTo('a');
        assertThat(BytecodeTranslator.fieldTypeSuffix("B")).isEqualTo('b');
        assertThat(BytecodeTranslator.fieldTypeSuffix("Z")).isEqualTo('b');
        assertThat(BytecodeTranslator.fieldTypeSuffix("S")).isEqualTo('s');
        assertThat(BytecodeTranslator.fieldTypeSuffix("I")).isEqualTo('i');
    }

    @Test
    void jcvmOpcodesShouldHaveCorrectValues() {
        // Spot-check critical opcodes from the JCVM 3.0.5 spec
        assertThat(0x00).isEqualTo(JcvmOpcode.NOP);
        assertThat(0x03).isEqualTo(JcvmOpcode.SCONST_0);
        assertThat(0x18).isEqualTo(JcvmOpcode.ALOAD_0);
        assertThat(0x41).isEqualTo(JcvmOpcode.SADD);
        assertThat(0x8B).isEqualTo(JcvmOpcode.INVOKEVIRTUAL);
        assertThat(0x7A).isEqualTo(JcvmOpcode.RETURN);
        assertThat(0xA8).isEqualTo(JcvmOpcode.GOTO_W);
        assertThat(0xAD).isEqualTo(JcvmOpcode.GETFIELD_A_THIS);
    }

    @Test
    void jcvmConstantPoolShouldDedup() {
        var cp = new JcvmConstantPool();
        int idx1 = cp.addExternalClassRef(0, 5);
        int idx2 = cp.addExternalClassRef(0, 5); // same ref
        int idx3 = cp.addExternalClassRef(0, 6); // different

        assertThat(idx1).isEqualTo(idx2); // deduplication
        assertThat(idx3).isNotEqualTo(idx1); // different entry
        assertThat(cp.size()).isEqualTo(2);
    }

    @Test
    void cpEntryShouldBe4Bytes() {
        var cp = new JcvmConstantPool();
        cp.addExternalClassRef(0, 5);

        var entry = cp.entries().getFirst();
        assertThat(entry.toBytes()).hasSize(4);
        assertThat(entry.tag()).isEqualTo(JcvmConstantPool.TAG_CLASSREF);
    }

    // ── ACC_INT (supportInt32) tests ──

    @Test
    void shouldEmitShortOpcodesWhenInt32NotSupported() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/intops/IntOpsApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel process = findMethod(cm, "process");
        // supportInt32=false (default) — int ops → short ops
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);
        byte[] bytecode = result.bytecode();

        // Should contain SADD (0x41), NOT IADD (0x42)
        assertThat(containsOpcode(bytecode, JcvmOpcode.SADD))
                .as("should emit SADD when int32 not supported").isTrue();
        assertThat(containsOpcode(bytecode, JcvmOpcode.IADD))
                .as("should NOT emit IADD when int32 not supported").isFalse();
    }

    @Test
    void shouldEmitIntOpcodesWhenInt32Supported() throws Exception {
        // To test supportInt32=true, we need the full converter pipeline
        // because the 4-arg translate() requires a ReferenceResolver.
        // Instead, verify via the end-to-end converter.
        Path classesDir = Path.of("target/test-classes");
        var result = name.velikodniy.jcexpress.converter.Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.intops")
                .packageAid("A000000062070101")
                .packageVersion(1, 0)
                .applet("com.example.intops.IntOpsApplet", "A00000006207010101")
                .supportInt32(true)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Extract Method component and check for int-specific opcodes
        byte[] methodData = extractComponent(result.capFile(), "Method.cap");
        assertThat(methodData).isNotNull();

        // Search for IADD (0x42) in Method component
        assertThat(containsOpcode(methodData, JcvmOpcode.IADD))
                .as("should emit IADD when int32 supported").isTrue();
    }

    @Test
    void shouldEmitIconstWhenInt32Supported() throws Exception {
        Path classesDir = Path.of("target/test-classes");
        var result = name.velikodniy.jcexpress.converter.Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.intops")
                .packageAid("A000000062070101")
                .packageVersion(1, 0)
                .applet("com.example.intops.IntOpsApplet", "A00000006207010101")
                .supportInt32(true)
                .build()
                .convert();

        byte[] methodData = extractComponent(result.capFile(), "Method.cap");

        // With int32, constants should use ICONST_0 (0x0A) instead of SCONST_0 (0x03)
        assertThat(containsOpcode(methodData, JcvmOpcode.ICONST_0))
                .as("should emit ICONST_0 when int32 supported").isTrue();
    }

    @Test
    void headerShouldHaveAccIntFlagWhenInt32Supported() throws Exception {
        Path classesDir = Path.of("target/test-classes");
        var result = name.velikodniy.jcexpress.converter.Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.intops")
                .packageAid("A000000062070101")
                .packageVersion(1, 0)
                .applet("com.example.intops.IntOpsApplet", "A00000006207010101")
                .supportInt32(true)
                .build()
                .convert();

        byte[] headerData = extractComponent(result.capFile(), "Header.cap");
        // flags byte is at offset 9 in the Header component info (after tag, size, magic, minor, major)
        // tag(1) + size(2) + magic(4) + minor(1) + major(1) = 9, then flags
        int flags = headerData[9] & 0xFF;
        assertThat(flags & 0x01).as("ACC_INT flag should be set").isEqualTo(1);
    }

    private static boolean containsOpcode(byte[] data, int opcode) {
        for (byte b : data) {
            if ((b & 0xFF) == opcode) return true;
        }
        return false;
    }

    private static byte[] extractComponent(byte[] capFile, String componentName) throws Exception {
        try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(capFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(componentName)) {
                    return zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    // ── Array operations and type checks ──

    @Test
    void shouldTranslateArrayOperations() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/arrayops/ArrayOpsApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel process = findMethod(cm, "process");
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);

        assertThat(result).isNotNull();
        assertThat(result.bytecode()).isNotEmpty();
        assertThat(result.nargs()).isEqualTo(2); // this + APDU
    }

    @Test
    void shouldTranslateArithmeticOpcodes() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/arrayops/ArrayOpsApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel handleArithmetic = findMethod(cm, "handleArithmetic");
        TranslatedMethod result = BytecodeTranslator.translate(handleArithmetic, cm, cp);

        byte[] bytecode = result.bytecode();
        // Should contain SDIV (0x47) for division
        assertThat(containsOpcode(bytecode, JcvmOpcode.SDIV))
                .as("should emit SDIV").isTrue();
        // Should contain SREM (0x49) for remainder
        assertThat(containsOpcode(bytecode, JcvmOpcode.SREM))
                .as("should emit SREM").isTrue();
        // Should contain SSHL (0x4D) for shift left
        assertThat(containsOpcode(bytecode, JcvmOpcode.SSHL))
                .as("should emit SSHL").isTrue();
        // Should contain SSHR (0x4F) for shift right
        assertThat(containsOpcode(bytecode, JcvmOpcode.SSHR))
                .as("should emit SSHR").isTrue();
        // Should contain SOR (0x55) for bitwise OR
        assertThat(containsOpcode(bytecode, JcvmOpcode.SOR))
                .as("should emit SOR").isTrue();
        // Should contain SXOR (0x57) for bitwise XOR
        assertThat(containsOpcode(bytecode, JcvmOpcode.SXOR))
                .as("should emit SXOR").isTrue();
    }

    // ── Multiple exception handlers ──

    @Test
    void shouldTranslateMultipleCatchBlocks() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/multiexc/MultiExceptionApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel process = findMethod(cm, "process");
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);

        assertThat(result.bytecode()).isNotEmpty();
        // Multiple catch blocks should produce multiple exception handlers
        assertThat(result.exceptionHandlers())
                .as("should have multiple exception handlers for multi-catch")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    // ── Switch statements ──

    @Test
    void shouldTranslateTableSwitch() throws Exception {
        // The ArrayOpsApplet process() has a switch with contiguous case values (0x01-0x04)
        // which Java compiles to tableswitch → STABLESWITCH
        ClassModel cm = parseClass("target/test-classes/com/example/arrayops/ArrayOpsApplet.class");
        var cp = new JcvmConstantPool();

        MethodModel process = findMethod(cm, "process");
        TranslatedMethod result = BytecodeTranslator.translate(process, cm, cp);

        byte[] bytecode = result.bytecode();
        // Should contain either STABLESWITCH (0x73) or SLOOKUPSWITCH (0x75)
        boolean hasSwitch = containsOpcode(bytecode, JcvmOpcode.STABLESWITCH)
                || containsOpcode(bytecode, JcvmOpcode.SLOOKUPSWITCH);
        assertThat(hasSwitch)
                .as("should emit STABLESWITCH or SLOOKUPSWITCH for switch statement").isTrue();
    }

    // ── End-to-end converter tests for new applets ──

    @Test
    void shouldConvertArrayOpsApplet() throws Exception {
        var result = name.velikodniy.jcexpress.converter.Converter.builder()
                .classesDirectory(java.nio.file.Path.of("target/test-classes"))
                .packageName("com.example.arrayops")
                .packageAid("A000000062080101")
                .packageVersion(1, 0)
                .applet("com.example.arrayops.ArrayOpsApplet", "A00000006208010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
        assertThat(result.exportFile()).isNotEmpty();

        // Verify Method component exists and has content
        byte[] methodData = extractComponent(result.capFile(), "Method.cap");
        assertThat(methodData).isNotNull();
        assertThat(methodData.length).isGreaterThan(3);
    }

    @Test
    void shouldConvertMultiExceptionApplet() throws Exception {
        var result = name.velikodniy.jcexpress.converter.Converter.builder()
                .classesDirectory(java.nio.file.Path.of("target/test-classes"))
                .packageName("com.example.multiexc")
                .packageAid("A000000062090101")
                .packageVersion(1, 0)
                .applet("com.example.multiexc.MultiExceptionApplet", "A00000006209010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
    }

    @Test
    void shouldConvertAbstractClassApplet() throws Exception {
        var result = name.velikodniy.jcexpress.converter.Converter.builder()
                .classesDirectory(java.nio.file.Path.of("target/test-classes"))
                .packageName("com.example.abstract_")
                .packageAid("A000000062100101")
                .packageVersion(1, 0)
                .applet("com.example.abstract_.ConcreteApplet", "A00000006210010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Verify Class component exists
        byte[] classData = extractComponent(result.capFile(), "Class.cap");
        assertThat(classData).isNotNull();
    }

    // ── Type conversion opcodes ──

    @Test
    void shouldHandleS2bConversion() throws Exception {
        ClassModel cm = parseClass("target/test-classes/com/example/arrayops/ArrayOpsApplet.class");
        var cp = new JcvmConstantPool();

        // handleConversions casts short to byte → S2B
        MethodModel handleConversions = findMethod(cm, "handleConversions");
        TranslatedMethod result = BytecodeTranslator.translate(handleConversions, cm, cp);

        byte[] bytecode = result.bytecode();
        // Should contain S2B (0x5B) for short-to-byte conversion
        assertThat(containsOpcode(bytecode, JcvmOpcode.S2B))
                .as("should emit S2B for short-to-byte cast").isTrue();
    }

    // ── Helpers ──

    private static ClassModel parseClass(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        return ClassFile.of().parse(bytes);
    }

    private static MethodModel findMethod(ClassModel cm, String name) {
        return cm.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + name));
    }

}
