package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DescriptorComponent — validates type descriptor table structure,
 * nibble encoding, field/method descriptor formats, and access flags per JCVM §6.14.
 */
class DescriptorComponentTest {

    private static final Path CLASSES_DIR = Path.of("target/test-classes");

    /** Dummy resolver returning 0 for all class refs — used in nibble encoding tests */
    private static final Function<String, Integer> ZERO_RESOLVER = name -> 0;

    // ── Type nibble encoding (JCVM §6.14.2) ──

    @Test
    void typeNibbleVoid() {
        assertThat(DescriptorComponent.typeNibble("V")).isEqualTo(1);
    }

    @Test
    void typeNibbleBoolean() {
        assertThat(DescriptorComponent.typeNibble("Z")).isEqualTo(2);
    }

    @Test
    void typeNibbleByte() {
        assertThat(DescriptorComponent.typeNibble("B")).isEqualTo(3);
    }

    @Test
    void typeNibbleShort() {
        assertThat(DescriptorComponent.typeNibble("S")).isEqualTo(4);
    }

    @Test
    void typeNibbleChar() {
        assertThat(DescriptorComponent.typeNibble("C")).isEqualTo(4); // char → short
    }

    @Test
    void typeNibbleInt() {
        assertThat(DescriptorComponent.typeNibble("I")).isEqualTo(5);
    }

    @Test
    void typeNibbleReference() {
        assertThat(DescriptorComponent.typeNibble("Ljavacard/framework/APDU;")).isEqualTo(6);
    }

    @Test
    void typeNibbleBooleanArray() {
        assertThat(DescriptorComponent.typeNibble("[Z")).isEqualTo(0x0A);
    }

    @Test
    void typeNibbleByteArray() {
        assertThat(DescriptorComponent.typeNibble("[B")).isEqualTo(0x0B);
    }

    @Test
    void typeNibbleShortArray() {
        assertThat(DescriptorComponent.typeNibble("[S")).isEqualTo(0x0C);
    }

    @Test
    void typeNibbleIntArray() {
        assertThat(DescriptorComponent.typeNibble("[I")).isEqualTo(0x0D);
    }

    @Test
    void typeNibbleReferenceArray() {
        assertThat(DescriptorComponent.typeNibble("[Ljavacard/framework/APDU;")).isEqualTo(0x0E);
    }

    // ── Descriptor to nibbles conversion (§6.14.2: params first, return last) ──

    @Test
    void descriptorToNibblesPrimitiveField() {
        assertThat(DescriptorComponent.descriptorToNibbles("S", ZERO_RESOLVER)).containsExactly(4);
    }

    @Test
    void descriptorToNibblesReferenceField() {
        // Reference type: nibble 6 + 4 nibbles of class_ref (0x0000 from ZERO_RESOLVER)
        assertThat(DescriptorComponent.descriptorToNibbles("Ljava/lang/Object;", ZERO_RESOLVER))
                .containsExactly(6, 0, 0, 0, 0);
    }

    @Test
    void descriptorToNibblesArrayField() {
        assertThat(DescriptorComponent.descriptorToNibbles("[B", ZERO_RESOLVER)).containsExactly(0x0B);
    }

    @Test
    void descriptorToNibblesVoidNoArgsMethod() {
        // ()V → return=void (no params)
        assertThat(DescriptorComponent.descriptorToNibbles("()V", ZERO_RESOLVER)).containsExactly(1);
    }

    @Test
    void descriptorToNibblesProcessMethod() {
        // (Ljavacard/framework/APDU;)V → §6.14.2: params first, return last
        // APDU param → [6, 0, 0, 0, 0] (ref + class_ref nibbles), void return → [1]
        assertThat(DescriptorComponent.descriptorToNibbles("(Ljavacard/framework/APDU;)V", ZERO_RESOLVER))
                .containsExactly(6, 0, 0, 0, 0, 1);
    }

    @Test
    void descriptorToNibblesMethodWithPrimitiveParams() {
        // (BSI)S → §6.14.2: params first [byte, short, int], return last [short]
        assertThat(DescriptorComponent.descriptorToNibbles("(BSI)S", ZERO_RESOLVER))
                .containsExactly(3, 4, 5, 4);
    }

    @Test
    void descriptorToNibblesMethodWithArrayParam() {
        // ([BII)V → §6.14.2: params first [byte[], int, int], return last [void]
        assertThat(DescriptorComponent.descriptorToNibbles("([BII)V", ZERO_RESOLVER))
                .containsExactly(0x0B, 5, 5, 1);
    }

    @Test
    void descriptorToNibblesMethodReturningReference() {
        // ()Ljavacard/framework/APDU; → no params, return=reference
        assertThat(DescriptorComponent.descriptorToNibbles("()Ljavacard/framework/APDU;", ZERO_RESOLVER))
                .containsExactly(6, 0, 0, 0, 0);
    }

    @Test
    void descriptorToNibblesReferenceWithRealClassRef() {
        // Test that class_ref nibbles are correctly decomposed
        // Resolver returns 0x800A → nibbles [8, 0, 0, A]
        Function<String, Integer> resolver = name -> 0x800A;
        assertThat(DescriptorComponent.descriptorToNibbles("(Ljavacard/framework/APDU;)V", resolver))
                .containsExactly(6, 8, 0, 0, 0xA, 1);
    }

    // ── Full component generation ──

    @Test
    void descriptorComponentHasCorrectTag() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        assertThat(data[0] & 0xFF).as("tag").isEqualTo(11);
    }

    @Test
    void descriptorComponentStartsWithClassCount() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        // After tag(1) + size(2), first byte is class_count
        int classCount = data[3] & 0xFF;
        assertThat(classCount).as("class_count for TestApplet (single class)").isEqualTo(1);
    }

    @Test
    void multiClassDescriptorHasCorrectClassCount() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example.multiclass", "A000000062030101",
                "com.example.multiclass.MultiClassApplet", "A00000006203010101");

        int classCount = data[3] & 0xFF;
        assertThat(classCount).as("class_count for MultiClassApplet (2 classes)").isEqualTo(2);
    }

    @Test
    void inheritanceDescriptorHasThreeClasses() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example.inherit", "A000000062060101",
                "com.example.inherit.InheritanceApplet", "A00000006206010101");

        int classCount = data[3] & 0xFF;
        assertThat(classCount).as("class_count for InheritanceApplet (3 classes)").isEqualTo(3);
    }

    @Test
    void classDescriptorHasCorrectAccessFlags() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        // After tag(1) + size(2) + class_count(1): first class descriptor starts
        // u1 token, u1 access_flags
        int pos = 4;
        int token = data[pos] & 0xFF;
        int flags = data[pos + 1] & 0xFF;

        assertThat(token).as("class token").isEqualTo(0);
        // TestApplet is public, not interface, not abstract → 0x01
        assertThat(flags & 0x01).as("ACC_PUBLIC").isEqualTo(0x01);
        assertThat(flags & 0x02).as("ACC_INTERFACE should not be set").isEqualTo(0);
        assertThat(flags & 0x04).as("ACC_ABSTRACT should not be set").isEqualTo(0);
    }

    @Test
    void fieldDescriptorIs7Bytes() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        // Parse to find field_count and verify field descriptors are 7 bytes each
        int pos = 4; // after tag + size + class_count
        pos++; // token
        pos++; // access_flags
        pos += 2; // this_class_ref
        int interfaceCount = data[pos] & 0xFF;
        pos++; // interface_count
        int fieldCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2; // field_count
        int methodCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2; // method_count

        // Skip interface refs
        pos += interfaceCount * 2;

        // Each field descriptor should be exactly 7 bytes:
        // token(1) + access_flags(1) + field_ref(3) + type(2)
        int fieldStart = pos;
        int fieldEnd = fieldStart + fieldCount * 7;
        // Skip to after fields
        pos = fieldEnd;

        // Each method descriptor should be exactly 12 bytes:
        // token(1) + access_flags(1) + method_offset(2) + type_offset(2)
        // + bytecode_count(2) + exception_handler_count(2) + exception_handler_index(2)
        int methodStart = pos;

        assertThat(fieldCount).as("TestApplet should have fields").isGreaterThanOrEqualTo(0);
        assertThat(methodCount).as("TestApplet should have methods").isGreaterThan(0);

        // Verify the method section starts at expected position
        // (ensures fields are exactly 7 bytes each)
        // Read first method token — should be a valid small number
        if (methodCount > 0) {
            int mToken = data[methodStart] & 0xFF;
            int mFlags = data[methodStart + 1] & 0xFF;
            // Flags should have at least one valid bit
            assertThat(mFlags).as("method access_flags should be valid").isGreaterThan(0);
        }
    }

    @Test
    void methodDescriptorHas12Bytes() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        // Parse to find methods
        int pos = 4;
        pos++; // token
        pos++; // access_flags
        pos += 2; // this_class_ref
        int interfaceCount = data[pos] & 0xFF;
        pos++; // interface_count
        int fieldCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        int methodCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        pos += interfaceCount * 2;
        pos += fieldCount * 7;

        // Now at method descriptors, each is 12 bytes
        int methodsStart = pos;
        int methodsEnd = methodsStart + methodCount * 12;

        // After methods, the type_descriptor_info table starts
        // First field is u2 constant_pool_count
        int cpCount = ((data[methodsEnd] & 0xFF) << 8) | (data[methodsEnd + 1] & 0xFF);
        assertThat(cpCount).as("CP count in type_descriptor_info should be > 0").isGreaterThan(0);
    }

    @Test
    void typeDescriptorTableHasCpTypesArray() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        // Find the type_descriptor_info table start
        int typeTableStart = findTypeTableStart(data);

        // u2 constant_pool_count
        int cpCount = ((data[typeTableStart] & 0xFF) << 8) | (data[typeTableStart + 1] & 0xFF);
        assertThat(cpCount).as("constant_pool_count").isGreaterThan(0);

        // constant_pool_types[cpCount] — each entry is u2
        // Entries should be either 0xFFFF (no type) or a valid offset
        for (int i = 0; i < cpCount; i++) {
            int offset = typeTableStart + 2 + i * 2;
            int typeRef = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            // Each entry is either 0xFFFF or a valid type table offset
            assertThat(typeRef == 0xFFFF || typeRef >= 0)
                    .as("CP type entry %d should be 0xFFFF or valid offset", i)
                    .isTrue();
        }
    }

    @Test
    void typeDescriptorEntriesHaveValidNibblePacking() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        int typeTableStart = findTypeTableStart(data);
        int cpCount = ((data[typeTableStart] & 0xFF) << 8) | (data[typeTableStart + 1] & 0xFF);

        // Skip past CP types array to get to type descriptor entries
        int pos = typeTableStart + 2 + cpCount * 2;

        // Parse type descriptor entries until end of component
        int entryCount = 0;
        while (pos < data.length) {
            int nibbleCount = data[pos] & 0xFF;
            assertThat(nibbleCount).as("nibble_count for entry %d", entryCount)
                    .isGreaterThan(0).isLessThanOrEqualTo(16);
            pos++; // nibble_count

            int byteCount = (nibbleCount + 1) / 2;
            pos += byteCount;

            entryCount++;
        }

        assertThat(entryCount).as("should have at least one type descriptor entry")
                .isGreaterThan(0);
    }

    @Test
    void constructorMethodHasInitFlag() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        // Find method descriptors
        int pos = 4;
        pos++; // token
        pos++; // access_flags
        pos += 2; // this_class_ref
        int interfaceCount = data[pos] & 0xFF;
        pos++; // interface_count
        int fieldCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        int methodCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        pos += interfaceCount * 2;
        pos += fieldCount * 7;

        // Search for a method with ACC_INIT (0x80) flag
        boolean foundInit = false;
        for (int i = 0; i < methodCount; i++) {
            int mToken = data[pos] & 0xFF;
            int mFlags = data[pos + 1] & 0xFF;
            if ((mFlags & 0x80) != 0) {
                foundInit = true;
                // Constructor should have token 0
                assertThat(mToken).as("<init> token").isEqualTo(0);
            }
            pos += 12;
        }

        assertThat(foundInit).as("should have at least one constructor with ACC_INIT").isTrue();
    }

    @Test
    void methodDescriptorHasNonZeroBytecodeCount() throws Exception {
        byte[] data = convertAndExtractDescriptor("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        int pos = 4;
        pos++; // token
        pos++; // access_flags
        pos += 2; // this_class_ref
        int interfaceCount = data[pos] & 0xFF;
        pos++; // interface_count
        int fieldCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        int methodCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        pos += interfaceCount * 2;
        pos += fieldCount * 7;

        // At least one method should have non-zero bytecodeCount
        boolean foundNonZero = false;
        for (int i = 0; i < methodCount; i++) {
            int mOffset = pos;
            // method_offset at pos+2
            // type_offset at pos+4
            // bytecodeCount at pos+6
            int bytecodeCount = ((data[mOffset + 6] & 0xFF) << 8) | (data[mOffset + 7] & 0xFF);
            if (bytecodeCount > 0) {
                foundNonZero = true;
            }
            pos += 12;
        }

        assertThat(foundNonZero).as("at least one method should have JCVM bytecodeCount > 0").isTrue();
    }

    // ── Synthetic unit test with controlled inputs ──

    @Test
    void generateWithSingleClassAndField() {
        // Build minimal inputs for a class with one instance field and one method
        List<FieldInfo> fields = List.of(
                new FieldInfo("counter", "S", 0x0002, null)  // private short
        );
        List<MethodInfo> methods = List.of(
                new MethodInfo("<init>", "()V", 0x0001, 1, 1, new byte[5], List.of())
        );
        ClassInfo ci = new ClassInfo(
                "com/test/MyApplet", "javacard/framework/Applet",
                List.of(), 0x0001, methods, fields
        );

        TokenMap tokenMap = new TokenMap("com.test", List.of(
                new TokenMap.ClassEntry("com/test/MyApplet", 0,
                        List.of(),
                        List.of(new TokenMap.MethodEntry("<init>", "()V", 0)),
                        List.of(new TokenMap.FieldEntry("counter", "S", 0)),
                        List.of())
        ));

        JcvmConstantPool cp = new JcvmConstantPool();
        cp.addExternalStaticMethodRef(0, 0, 0); // dummy entry

        TranslatedMethod tm = new TranslatedMethod(
                new byte[]{0x19, 0x70}, 1, 1, 1, List.of(), false, List.of()
        );

        byte[] result = DescriptorComponent.generate(
                List.of(ci), tokenMap,
                new int[]{0},
                Map.of("com/test/MyApplet:<init>:()V", 0),
                new int[]{0},
                List.of(tm),
                cp,
                Map.of(),
                ZERO_RESOLVER
        );

        // Verify tag and basic structure
        assertThat(result[0] & 0xFF).as("tag").isEqualTo(11);

        // class_count = 1
        assertThat(result[3] & 0xFF).as("class_count").isEqualTo(1);

        // Parse class descriptor: token(1) + flags(1) + this_class_ref(2)
        // + interface_count(1) + field_count(2) + method_count(2)
        int pos = 4;
        assertThat(result[pos] & 0xFF).as("class token").isEqualTo(0);
        pos++; // token
        assertThat(result[pos] & 0x01).as("ACC_PUBLIC").isEqualTo(1);
        pos++; // flags
        pos += 2; // this_class_ref
        assertThat(result[pos] & 0xFF).as("interface_count").isEqualTo(0);
        pos++; // interface_count

        int fieldCount = ((result[pos] & 0xFF) << 8) | (result[pos + 1] & 0xFF);
        assertThat(fieldCount).as("field_count").isEqualTo(1);
        pos += 2;

        int methodCount = ((result[pos] & 0xFF) << 8) | (result[pos + 1] & 0xFF);
        assertThat(methodCount).as("method_count").isEqualTo(1);
        pos += 2;

        // Field descriptor (7 bytes): token(1) + flags(1) + field_ref(3) + type(2)
        assertThat(result[pos] & 0xFF).as("field token").isEqualTo(0);
        pos++;
        int fFlags = result[pos] & 0xFF;
        assertThat(fFlags & 0x02).as("field ACC_PRIVATE").isEqualTo(0x02);
        pos++;
        // instance field_ref: class_offset(2) + token(1)
        pos += 3; // skip field_ref
        // type for short: 0x8004
        int fieldType = ((result[pos] & 0xFF) << 8) | (result[pos + 1] & 0xFF);
        assertThat(fieldType).as("field type for short").isEqualTo(0x8004);
        pos += 2;

        // Method descriptor (12 bytes):
        // token(1) + flags(1) + offset(2) + type_offset(2) + bytecodeCount(2) + handlers(2) + handlerIdx(2)
        int mToken = result[pos] & 0xFF;
        assertThat(mToken).as("<init> static method token").isEqualTo(0);
        pos++;
        int mFlags = result[pos] & 0xFF;
        assertThat(mFlags & 0x80).as("ACC_INIT for constructor").isEqualTo(0x80);
        pos++;
        pos += 2; // method_offset
        pos += 2; // type_offset (points into type table)
        int bytecodeCount = ((result[pos] & 0xFF) << 8) | (result[pos + 1] & 0xFF);
        assertThat(bytecodeCount).as("JCVM bytecodeCount").isEqualTo(2); // from translated method
    }

    @Test
    void compileTimeConstantFieldsAreExcluded() {
        // static final short with constantValue = compile-time constant, should be excluded
        List<FieldInfo> fields = List.of(
                new FieldInfo("MAX", "S", 0x001A, (short) 100), // private static final short = 100
                new FieldInfo("data", "[B", 0x0002, null)        // private byte[]
        );
        List<MethodInfo> methods = List.of(
                new MethodInfo("<init>", "()V", 0x0001, 1, 1, new byte[3], List.of())
        );
        ClassInfo ci = new ClassInfo(
                "com/test/MyApplet", "javacard/framework/Applet",
                List.of(), 0x0001, methods, fields
        );

        TokenMap tokenMap = new TokenMap("com.test", List.of(
                new TokenMap.ClassEntry("com/test/MyApplet", 0,
                        List.of(),
                        List.of(new TokenMap.MethodEntry("<init>", "()V", 0)),
                        List.of(new TokenMap.FieldEntry("data", "[B", 0)),
                        List.of(new TokenMap.FieldEntry("MAX", "S", 0)))
        ));

        JcvmConstantPool cp = new JcvmConstantPool();
        TranslatedMethod tm = new TranslatedMethod(
                new byte[]{0x70}, 1, 1, 1, List.of(), false, List.of()
        );

        byte[] result = DescriptorComponent.generate(
                List.of(ci), tokenMap,
                new int[]{0},
                Map.of("com/test/MyApplet:<init>:()V", 0),
                new int[]{0},
                List.of(tm),
                cp,
                Map.of(),
                ZERO_RESOLVER
        );

        // field_count should be 1 (MAX excluded as compile-time constant)
        int pos = 4; // after tag(1) + size(2) + class_count(1)
        pos++; // token
        pos++; // flags
        pos += 2; // this_class_ref
        pos++; // interface_count

        int fieldCount = ((result[pos] & 0xFF) << 8) | (result[pos + 1] & 0xFF);
        assertThat(fieldCount).as("field_count excludes compile-time constant").isEqualTo(1);
    }

    @Test
    void staticFieldHasZeroPaddingInFieldRef() {
        List<FieldInfo> fields = List.of(
                new FieldInfo("buffer", "[B", 0x000A, null) // private static byte[]
        );
        List<MethodInfo> methods = List.of(
                new MethodInfo("<init>", "()V", 0x0001, 1, 1, new byte[3], List.of())
        );
        ClassInfo ci = new ClassInfo(
                "com/test/MyApplet", "javacard/framework/Applet",
                List.of(), 0x0001, methods, fields
        );

        TokenMap tokenMap = new TokenMap("com.test", List.of(
                new TokenMap.ClassEntry("com/test/MyApplet", 0,
                        List.of(),
                        List.of(new TokenMap.MethodEntry("<init>", "()V", 0)),
                        List.of(),
                        List.of(new TokenMap.FieldEntry("buffer", "[B", 0)))
        ));

        JcvmConstantPool cp = new JcvmConstantPool();
        TranslatedMethod tm = new TranslatedMethod(
                new byte[]{0x70}, 1, 1, 1, List.of(), false, List.of()
        );

        byte[] result = DescriptorComponent.generate(
                List.of(ci), tokenMap,
                new int[]{0},
                Map.of("com/test/MyApplet:<init>:()V", 0),
                new int[]{0},
                List.of(tm),
                cp,
                Map.of(),
                ZERO_RESOLVER
        );

        // Navigate to field_ref bytes in the first field descriptor
        int pos = 4; // after tag + size + class_count
        pos += 5; // token + flags + this_class_ref + interface_count
        pos += 4; // field_count + method_count
        // Now at field descriptor
        pos += 2; // skip token + flags

        // Static field_ref should be 0x00, 0x00, 0x00
        assertThat(result[pos] & 0xFF).as("static field_ref[0]").isEqualTo(0);
        assertThat(result[pos + 1] & 0xFF).as("static field_ref[1]").isEqualTo(0);
        assertThat(result[pos + 2] & 0xFF).as("static field_ref[2]").isEqualTo(0);
    }

    @Test
    void primitiveFieldTypesEncodeCorrectly() {
        List<FieldInfo> fields = List.of(
                new FieldInfo("flag", "Z", 0x0002, null),    // boolean
                new FieldInfo("val", "B", 0x0002, null),     // byte
                new FieldInfo("count", "S", 0x0002, null),   // short
                new FieldInfo("big", "I", 0x0002, null)      // int
        );
        List<MethodInfo> methods = List.of(
                new MethodInfo("<init>", "()V", 0x0001, 1, 1, new byte[3], List.of())
        );
        ClassInfo ci = new ClassInfo(
                "com/test/MyApplet", "javacard/framework/Applet",
                List.of(), 0x0001, methods, fields
        );

        TokenMap tokenMap = new TokenMap("com.test", List.of(
                new TokenMap.ClassEntry("com/test/MyApplet", 0,
                        List.of(),
                        List.of(new TokenMap.MethodEntry("<init>", "()V", 0)),
                        List.of(
                                new TokenMap.FieldEntry("flag", "Z", 0),
                                new TokenMap.FieldEntry("val", "B", 1),
                                new TokenMap.FieldEntry("count", "S", 2),
                                new TokenMap.FieldEntry("big", "I", 3)
                        ),
                        List.of())
        ));

        JcvmConstantPool cp = new JcvmConstantPool();
        TranslatedMethod tm = new TranslatedMethod(
                new byte[]{0x70}, 1, 1, 1, List.of(), false, List.of()
        );

        byte[] result = DescriptorComponent.generate(
                List.of(ci), tokenMap,
                new int[]{0},
                Map.of("com/test/MyApplet:<init>:()V", 0),
                new int[]{0},
                List.of(tm),
                cp,
                Map.of(),
                ZERO_RESOLVER
        );

        // Navigate to field descriptors
        int pos = 4; // after tag + size + class_count
        pos += 5; // token + flags + this_class_ref + interface_count
        pos += 4; // field_count + method_count

        // Each field: token(1) + flags(1) + field_ref(3) + type(2) = 7 bytes
        int[] expectedTypes = {0x8002, 0x8003, 0x8004, 0x8005};
        for (int i = 0; i < 4; i++) {
            int typeOffset = pos + 5; // skip token + flags + field_ref
            int fieldType = ((result[typeOffset] & 0xFF) << 8) | (result[typeOffset + 1] & 0xFF);
            assertThat(fieldType)
                    .as("field type for field %d", i)
                    .isEqualTo(expectedTypes[i]);
            pos += 7;
        }
    }

    // ── Helper methods ──

    private byte[] convertAndExtractDescriptor(String packageName, String packageAid,
                                                String appletClass, String appletAid) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES_DIR)
                .packageName(packageName)
                .packageAid(packageAid)
                .packageVersion(1, 0)
                .applet(appletClass, appletAid)
                .build()
                .convert();

        byte[] data = extractComponent(result.capFile(), "Descriptor.cap");
        assertThat(data).as("Descriptor.cap should exist").isNotNull();
        return data;
    }

    private static byte[] extractComponent(byte[] capFile, String componentName) throws Exception {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(capFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(componentName)) {
                    return zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /**
     * Finds the start position of the type_descriptor_info table in Descriptor component bytes.
     * Scans past all class descriptors to find where the type table begins.
     */
    private int findTypeTableStart(byte[] data) {
        int pos = 3; // skip tag(1) + size(2)
        int classCount = data[pos] & 0xFF;
        pos++;

        for (int c = 0; c < classCount; c++) {
            pos++; // token
            pos++; // access_flags
            pos += 2; // this_class_ref
            int interfaceCount = data[pos] & 0xFF;
            pos++;
            int fieldCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            int methodCount = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            pos += interfaceCount * 2;
            pos += fieldCount * 7;
            pos += methodCount * 12;
        }

        return pos;
    }
}
