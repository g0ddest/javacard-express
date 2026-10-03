package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.ArrayInit;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.StaticFieldView;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.FieldDescriptor;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Static field initialization: a Java Card VM never runs {@code <clinit>}, so the values it
 * assigns must be in the Static Field component (JCVM 3.1 §2.2.4.6, §6.11) and {@code <clinit>}
 * itself must not be in the Method or Descriptor component (§6.10, §6.14).
 */
class StaticInitializationTest {

    private static final HexFormat HEX = HexFormat.of();

    private static CapImage convert(String label) throws ConverterException {
        return CapImage.parse(TestFixtures.applet(label).convert().capFile());
    }

    @Test
    void staticArrayInitializersAreArrayInitEntries_6_11() throws Exception {
        StaticFieldView image = convert("ArrInitApplet").staticField();

        // HELLO, SHORTS, flags, ZEROS: arrays created by <clinit> (segment 1), in declaration order
        assertThat(image.arrayInits()).containsExactly(
                new ArrayInit(3, HEX.parseHex("48656c6c6f")),
                new ArrayInit(4, HEX.parseHex("0001fffe7fff")),
                new ArrayInit(2, HEX.parseHex("010001")),
                new ArrayInit(3, HEX.parseHex("000000")));
        assertThat(image.referenceCount()).isEqualTo(4);
    }

    @Test
    void nonDefaultPrimitiveIsInSegmentFour_6_11() throws Exception {
        StaticFieldView image = convert("ArrInitApplet").staticField();

        assertThat(image.defaultValueCount()).isZero();
        assertThat(image.nonDefaultValues()).as("static byte counter = 5").containsExactly(5);
        assertThat(image.imageSize()).isEqualTo(4 * 2 + 1);
    }

    @Test
    void descriptorStaticFieldRefsAreImageOffsets_6_14_3() throws Exception {
        ClassDescriptor applet = convert("ArrInitApplet").descriptor().classes().getFirst();

        assertThat(applet.fields()).filteredOn(FieldDescriptor::isStatic)
                .extracting(FieldDescriptor::staticOffset)
                .containsExactly(0, 2, 4, 6, 8);
    }

    @Test
    void staticInitializerIsNotAMethodOfTheCapFile_6_10() throws Exception {
        ClassDescriptor applet = convert("ArrInitApplet").descriptor().classes().getFirst();

        assertThat(applet.methods()).as("constructor, install and process only").hasSize(3);
    }

    @Test
    void unsupportedStaticInitializerIsRejected_2_2_4_6() {
        assertThatThrownBy(() -> TestFixtures.applet("BadClinitApplet").convert())
                .isInstanceOf(ConverterException.class)
                .satisfies(e -> assertThat(((ConverterException) e).violations())
                        .singleElement()
                        .satisfies(v -> {
                            assertThat(v.className()).isEqualTo("com/example/badclinit/BadClinitApplet");
                            assertThat(v.context()).startsWith("<clinit> (line ");
                            assertThat(v.message()).contains("invokestatic");
                        }));
    }

    /**
     * A blank static final primitive assigned in {@code <clinit>} was converted into a CAP file whose
     * Descriptor lists the field, which the off-card verifier rejects. It is no compile-time constant,
     * so the subset check rejects it at the assignment, naming the class, the field and the line.
     */
    @Test
    void blankStaticFinalPrimitiveIsRejected_2_2_4_6() {
        assertThatThrownBy(() -> TestFixtures.applet("BlankFinalApplet").convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com/example/blankfinal/BlankFinalApplet")
                .hasMessageContaining("(BlankFinalApplet.java:17)")
                .hasMessageContaining("static final short field BLANK")
                .satisfies(e -> assertThat(((ConverterException) e).violations())
                        .singleElement()
                        .satisfies(v -> {
                            assertThat(v.context()).isEqualTo("<clinit>()V");
                            assertThat(v.line()).isEqualTo(17);
                            assertThat(v.message()).contains("compile-time constants");
                        }));
    }

    @Test
    void interfaceFieldThatIsNotACompileTimeConstantIsRejected_2_2_4_6() {
        assertThatThrownBy(() -> TestFixtures.applet("IfStatApplet").convert())
                .isInstanceOf(ConverterException.class)
                .satisfies(e -> assertThat(((ConverterException) e).violations())
                        .singleElement()
                        .satisfies(v -> {
                            assertThat(v.className()).isEqualTo("com/example/ifstat/Config");
                            assertThat(v.context()).startsWith("<clinit> (line ");
                            assertThat(v.message()).contains("interface").contains("LOCK");
                        }));
    }

    @Test
    void libraryKeepsPrimitiveInitializers_2_2_4_6() throws Exception {
        CapImage lib = CapImage.parse(TestFixtures.LIBRARY.convert().capFile());

        // LibUtil: public static short counter = 5
        assertThat(lib.staticField().nonDefaultValues()).containsExactly(0, 5);
        assertThat(lib.staticField().arrayInits()).isEmpty();
    }

    @Test
    void cap23ImageIsTheSame() throws Exception {
        TestFixtures.Fixture fx = TestFixtures.applet("ArrInitApplet");
        StaticFieldView v305 = CapImage.parse(fx.builder(JavaCardVersion.V3_0_5).build().convert().capFile())
                .staticField();
        StaticFieldView v320 = CapImage.parse(fx.builder(JavaCardVersion.V3_2_0).build().convert().capFile())
                .staticField();

        assertThat(v320).isEqualTo(v305);
        assertThat(List.of(v320.arrayInits().size())).containsExactly(4);
    }
}
