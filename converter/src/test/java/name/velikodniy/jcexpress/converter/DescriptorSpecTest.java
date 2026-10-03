package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.Handler;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.FieldDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Descriptor component items (JCVM 3.1 §6.14) that the off-card verifier cross-checks against
 * the Class, Method, Static Field and Constant Pool components.
 */
class DescriptorSpecTest {

    private static CapImage convert(String label) throws ConverterException {
        return CapImage.parse(TestFixtures.applet(label).convert().capFile());
    }

    @Test
    void packageVisibleClassHasToken0xFF_6_14_2() throws Exception {
        DescriptorView d = convert("HelperApplet").descriptor(); // Counter, HelperApplet

        assertThat(d.classes()).extracting(ClassDescriptor::isPublic, ClassDescriptor::token)
                .containsExactly(tuple(false, 0xFF),
                        tuple(true, 0));
    }

    @Test
    void exceptionHandlerIndexPointsAtTheMethodsFirstHandler_6_14_4() throws Exception {
        CapImage cap = convert("CryptoApplet");
        List<Handler> handlers = cap.exceptionHandlers();
        byte[] methodComponent = cap.requireBody(CapImage.TAG_METHOD);
        List<MethodDescriptor> withHandlers = cap.descriptor().classes().stream()
                .flatMap(cd -> cd.methods().stream()).filter(m -> m.handlerCount() > 0).toList();

        assertThat(withHandlers).as("CryptoApplet has several methods with try/catch").hasSizeGreaterThan(1);
        assertThat(withHandlers).extracting(MethodDescriptor::handlerIndex).doesNotHaveDuplicates();
        for (MethodDescriptor m : withHandlers) {
            int header = (methodComponent[m.methodOffset()] & 0x80) != 0 ? 4 : 2;
            int start = m.methodOffset() + header;
            for (int i = m.handlerIndex(); i < m.handlerIndex() + m.handlerCount(); i++) {
                assertThat(handlers.get(i).startOffset()).as("handler %d of method @%d", i, m.methodOffset())
                        .isBetween(start, start + m.bytecodeCount() - 1);
            }
        }
    }

    @Test
    void staticFieldRefsAreDistinctImageOffsets_6_14_3() throws Exception {
        CapImage cap = convert("StaticsApplet");
        List<Integer> offsets = cap.descriptor().classes().stream().flatMap(cd -> cd.fields().stream())
                .filter(FieldDescriptor::isStatic).map(FieldDescriptor::staticOffset).toList();

        assertThat(offsets).hasSizeGreaterThan(1).doesNotHaveDuplicates()
                .allMatch(o -> o < cap.staticField().imageSize());
    }

    @Test
    void interfaceDescriptorListsInheritedMethodsWithoutCode_6_14_2_and_6_14_4() throws Exception {
        DescriptorView d = convert("ShareApplet").descriptor(); // AStatus, IBase, IExt, ShareApplet
        ClassDescriptor iExt = d.classes().get(2);

        assertThat(iExt.isInterface()).isTrue();
        assertThat(iExt.interfaces()).as("interface_count of an interface is 0").isEmpty();
        assertThat(iExt.fields()).isEmpty();
        assertThat(iExt.methods()).as("inherited get() and declared set(short)")
                .extracting(MethodDescriptor::token, MethodDescriptor::methodOffset, MethodDescriptor::bytecodeCount)
                .containsExactly(tuple(0, 0, 0),
                        tuple(1, 0, 0));
    }

    @Test
    void packageVisibleVirtualMethodsCarryPrivateTokens_6_14_4() throws Exception {
        ClassDescriptor applet = convert("HelperApplet").descriptor().classes().get(1);

        // handleIncrement() and handleRead(APDU, byte[]) are package-visible: 0x80 and 0x81
        assertThat(applet.methods()).extracting(MethodDescriptor::token).contains(0x80, 0x81);
    }
}
