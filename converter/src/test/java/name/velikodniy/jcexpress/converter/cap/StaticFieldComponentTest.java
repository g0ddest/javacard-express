package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StaticFieldComponent} (CAP component tag 8).
 * Validates static field image generation including field segmentation,
 * offset tracking, and binary format.
 */
class StaticFieldComponentTest {

    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_PUBLIC = 0x0001;

    @Test
    void emptyClassList_shouldProduceEmptyImage() {
        var result = StaticFieldComponent.generate(List.of());

        assertThat(result.imageSize()).isZero();
        assertThat(result.fieldOffsetMap()).isEmpty();
        assertThat(result.bytes()).isNotEmpty(); // still has tag+size header
        assertThat(result.bytes()[0]).isEqualTo((byte) StaticFieldComponent.TAG);
    }

    @Test
    void tagShouldBe8() {
        assertThat(8).isEqualTo(StaticFieldComponent.TAG);
    }

    @Test
    void classWithNoFields_shouldProduceEmptyImage() {
        ClassInfo ci = new ClassInfo("com/example/Empty", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of());

        var result = StaticFieldComponent.generate(List.of(ci));

        assertThat(result.imageSize()).isZero();
        assertThat(result.fieldOffsetMap()).isEmpty();
    }

    @Test
    void instanceField_shouldBeIgnored() {
        FieldInfo instanceField = new FieldInfo("value", "S", ACC_PUBLIC, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(instanceField));

        var result = StaticFieldComponent.generate(List.of(ci));

        assertThat(result.imageSize()).isZero();
        assertThat(result.fieldOffsetMap()).isEmpty();
    }

    @Test
    void compileTimeConstant_shouldBeExcluded() {
        // static final byte with ConstantValue => compile-time constant, excluded
        FieldInfo constField = new FieldInfo("CONST", "B",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL, (int) 42);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(constField));

        var result = StaticFieldComponent.generate(List.of(ci));

        assertThat(result.imageSize()).isZero();
        assertThat(result.fieldOffsetMap()).isEmpty();
    }

    @Test
    void staticFinalObjectRef_shouldNotBeCompileTimeConstant() {
        // static final Object with ConstantValue is NOT a compile-time constant
        // because it starts with 'L' (reference type)
        FieldInfo refField = new FieldInfo("REF", "Ljava/lang/Object;",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(refField));

        var result = StaticFieldComponent.generate(List.of(ci));

        // Reference field takes 2 bytes
        assertThat(result.imageSize()).isEqualTo(2);
        assertThat(result.fieldOffsetMap()).containsEntry("com/example/Test:REF", 0);
    }

    @Test
    void referenceFields_shouldBe2BytesEach() {
        FieldInfo ref1 = new FieldInfo("buf", "[B", ACC_STATIC, null);
        FieldInfo ref2 = new FieldInfo("obj", "Ljava/lang/Object;", ACC_STATIC, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(ref1, ref2));

        var result = StaticFieldComponent.generate(List.of(ci));

        // 2 reference fields * 2 bytes each = 4 bytes
        assertThat(result.imageSize()).isEqualTo(4);
        Map<String, Integer> offsets = result.fieldOffsetMap();
        assertThat(offsets).hasSize(2)
                .containsEntry("com/example/Test:buf", 0)
                .containsEntry("com/example/Test:obj", 2);
    }

    @Test
    void defaultValuePrimitives_shouldSizeCorrectlyByType() {
        FieldInfo byteFld = new FieldInfo("b", "B", ACC_STATIC, null);       // 1 byte
        FieldInfo boolFld = new FieldInfo("z", "Z", ACC_STATIC, null);       // 1 byte
        FieldInfo shortFld = new FieldInfo("s", "S", ACC_STATIC, null);      // 2 bytes
        FieldInfo charFld = new FieldInfo("c", "C", ACC_STATIC, null);       // 2 bytes
        FieldInfo intFld = new FieldInfo("i", "I", ACC_STATIC, null);        // 4 bytes
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(byteFld, boolFld, shortFld, charFld, intFld));

        var result = StaticFieldComponent.generate(List.of(ci));

        // 1 + 1 + 2 + 2 + 4 = 10 bytes
        assertThat(result.imageSize()).isEqualTo(10);
    }

    @Test
    void nonDefaultPrimitives_shouldWriteExplicitValues() {
        FieldInfo initByte = new FieldInfo("b", "B", ACC_STATIC, (int) 0x42);
        FieldInfo initShort = new FieldInfo("s", "S", ACC_STATIC, (int) 0x1234);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(initByte, initShort));

        var result = StaticFieldComponent.generate(List.of(ci));

        // byte (1) + short (2) = 3 bytes of non-default values
        assertThat(result.imageSize()).isEqualTo(3);
    }

    @Test
    void nonDefaultInt_shouldWrite4Bytes() {
        FieldInfo initInt = new FieldInfo("i", "I", ACC_STATIC, 0x12345678);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(initInt));

        var result = StaticFieldComponent.generate(List.of(ci));

        assertThat(result.imageSize()).isEqualTo(4);
    }

    @Test
    void segmentOrdering_refBeforeDefaultBeforeNonDefault() {
        FieldInfo refField = new FieldInfo("buf", "[B", ACC_STATIC, null);         // Segment 2
        FieldInfo defaultField = new FieldInfo("x", "S", ACC_STATIC, null);        // Segment 3
        FieldInfo nonDefaultField = new FieldInfo("y", "B", ACC_STATIC, (int) 10); // Segment 4
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(),
                List.of(refField, defaultField, nonDefaultField));

        var result = StaticFieldComponent.generate(List.of(ci));

        Map<String, Integer> offsets = result.fieldOffsetMap();
        int refOffset = offsets.get("com/example/Test:buf");
        int defaultOffset = offsets.get("com/example/Test:x");
        int nonDefaultOffset = offsets.get("com/example/Test:y");

        // Segment 2 (ref) at offset 0
        assertThat(refOffset).isZero();
        // Segment 3 (default) at offset 2 (after 1 ref * 2 bytes)
        assertThat(defaultOffset).isEqualTo(2);
        // Segment 4 (non-default) at offset 4 (after ref=2 + default_short=2)
        assertThat(nonDefaultOffset).isEqualTo(4);
    }

    @Test
    void multipleClasses_shouldCombineFields() {
        FieldInfo f1 = new FieldInfo("a", "B", ACC_STATIC, null);
        FieldInfo f2 = new FieldInfo("b", "S", ACC_STATIC, null);
        ClassInfo ci1 = new ClassInfo("com/example/A", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of(f1));
        ClassInfo ci2 = new ClassInfo("com/example/B", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of(f2));

        var result = StaticFieldComponent.generate(List.of(ci1, ci2));

        // byte (1) + short (2) = 3 bytes
        assertThat(result.imageSize()).isEqualTo(3);
        assertThat(result.fieldOffsetMap()).containsKey("com/example/A:a")
                .containsKey("com/example/B:b");
    }

    @Test
    void componentBytes_shouldStartWithTagAndSize() {
        FieldInfo f = new FieldInfo("x", "B", ACC_STATIC, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of(f));

        var result = StaticFieldComponent.generate(List.of(ci));
        byte[] bytes = result.bytes();

        assertThat(bytes[0]).isEqualTo((byte) 8); // tag = 8
        // Size is u2 big-endian at bytes[1..2]
        int size = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
        assertThat(size).isEqualTo(bytes.length - 3); // size = total - (tag + size field)
    }

    @Test
    void arrayInitCount_shouldBeZero() {
        var result = StaticFieldComponent.generate(List.of());
        assertThat(result.arrayInitCount()).isZero();
        assertThat(result.arrayInitSize()).isZero();
    }

    @Test
    void arrayField_shouldBeReferenceType() {
        FieldInfo arrayField = new FieldInfo("arr", "[S", ACC_STATIC, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of(arrayField));

        var result = StaticFieldComponent.generate(List.of(ci));

        // Array is a reference type, 2 bytes
        assertThat(result.imageSize()).isEqualTo(2);
    }

    @Test
    void booleanField_shouldBe1Byte() {
        FieldInfo boolField = new FieldInfo("flag", "Z", ACC_STATIC, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of(boolField));

        var result = StaticFieldComponent.generate(List.of(ci));

        assertThat(result.imageSize()).isEqualTo(1);
    }

    @Test
    void charField_shouldBe2Bytes() {
        FieldInfo charField = new FieldInfo("ch", "C", ACC_STATIC, null);
        ClassInfo ci = new ClassInfo("com/example/Test", "java/lang/Object",
                List.of(), ACC_PUBLIC, List.of(), List.of(charField));

        var result = StaticFieldComponent.generate(List.of(ci));

        assertThat(result.imageSize()).isEqualTo(2);
    }
}
