package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod.JcvmExceptionHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodComponentTest {

    @Test
    void shouldGenerateMethodWithStandardHeader() {
        byte[] bytecode = {0x18, 0x7A}; // aload_0, return
        var method = new TranslatedMethod(bytecode, 1, 1, 1, List.of(), false, List.of());

        MethodComponent.MethodResult result = MethodComponent.generate(List.of(method));

        // tag 7, size 5, handler_count 0, header 0x01 (flags 0|max_stack 1), 0x11 (nargs 1|max_locals 1)
        assertThat(result.bytes()).containsExactly(7, 0, 5, 0, 0x01, 0x11, 0x18, 0x7A);
        assertThat(result.offsets()).containsExactly(1);
    }

    @Test
    void shouldTrackMethodOffsets() {
        var method1 = new TranslatedMethod(new byte[]{0x7A}, 1, 1, 1, List.of(), false, List.of());
        var method2 = new TranslatedMethod(new byte[]{0x18, 0x7A}, 2, 1, 1, List.of(), false, List.of());

        MethodComponent.MethodResult result = MethodComponent.generate(List.of(method1, method2));

        assertThat(result.offsets()).containsExactly(1, 4);
    }

    @Test
    void shouldHandleAbstractMethods() {
        MethodComponent.MethodResult result = MethodComponent.generate(List.of(TranslatedMethod.EMPTY));

        // JCVM 3.1 §6.10.4: ACC_ABSTRACT (0x4 in the flags nibble), empty bytecodes
        assertThat(result.bytes()).containsExactly(7, 0, 3, 0, 0x40, 0x00);
    }

    /** JCVM 3.1 §6.10.4: nargs is a 4-bit field; more than 15 words needs the extended header. */
    @Test
    void abstractMethodWithMoreThan15ArgumentWordsUsesExtendedHeader_6_10_4() {
        var abstractMethod = new TranslatedMethod(new byte[0], 0, 0, 17, List.of(), false, List.of());

        byte[] bytes = MethodComponent.generate(List.of(abstractMethod)).bytes();

        // flags ACC_EXTENDED|ACC_ABSTRACT (0xC), padding 0, max_stack 0, nargs 17, max_locals 0
        assertThat(bytes).containsExactly(7, 0, 5, 0, 0xC0, 0, 17, 0);
    }

    /**
     * The example of JCVM 3.1 §6.10.2/§6.10.3: inner catch, outer catch, finally and a later try.
     * "the stop_bit item is set for both the third and fourth handlers".
     */
    @Test
    void stopBitFollowsActiveRangeIntersection_6_10_3() {
        byte[] code = new byte[80];
        List<JcvmExceptionHandler> handlers = List.of(
                new JcvmExceptionHandler(5, 10, 12, 1),   // inner catch (NullPointerException)
                new JcvmExceptionHandler(0, 30, 30, 2),   // outer catch (Exception)
                new JcvmExceptionHandler(0, 30, 40, 0),   // finally
                new JcvmExceptionHandler(60, 70, 72, 3)); // later try (SecurityException)
        var method = new TranslatedMethod(code, 1, 0, 0, handlers, false, List.of());

        byte[] bytes = MethodComponent.generate(List.of(method)).bytes();

        assertThat(stopBits(bytes)).containsExactly(false, false, true, true);
    }

    @Test
    void twoSequentialTryBlocksBothGetTheStopBit_6_10_3() {
        List<JcvmExceptionHandler> handlers = List.of(
                new JcvmExceptionHandler(0, 5, 10, 1),
                new JcvmExceptionHandler(12, 15, 20, 1));
        var method = new TranslatedMethod(new byte[30], 1, 0, 0, handlers, false, List.of());

        assertThat(stopBits(MethodComponent.generate(List.of(method)).bytes())).containsExactly(true, true);
    }

    /** JCVM 3.1 §6.10.1: "sorted in ascending order by the offset to the handler". */
    @Test
    void handlersAreSortedByHandlerOffset_6_10_1() {
        List<JcvmExceptionHandler> handlers = List.of(
                new JcvmExceptionHandler(20, 25, 40, 1),
                new JcvmExceptionHandler(0, 5, 10, 2));
        var method = new TranslatedMethod(new byte[50], 1, 0, 0, handlers, false, List.of());

        byte[] bytes = MethodComponent.generate(List.of(method)).bytes();

        int base = 1 + 2 * 8 + 2; // info offsets: handler_count + 2 handlers + 2-byte method header
        assertThat(handlerOffsets(bytes)).containsExactly(base + 10, base + 40);
        assertThat(stopBits(bytes)).containsExactly(true, true);
    }

    /**
     * JCVM 3.1 §6.10.1: the required handler-offset order must not change which handler the VM
     * selects; a JVM exception table whose overlapping entries are in the opposite order is
     * rejected when the method is built instead of being silently reordered.
     */
    @Test
    void reorderingThatWouldChangeHandlerSelectionIsRejected_6_10_1() {
        List<JcvmExceptionHandler> handlers = List.of(
                new JcvmExceptionHandler(0, 10, 40, 1),
                new JcvmExceptionHandler(0, 20, 30, 0));

        assertThatThrownBy(() -> new TranslatedMethod(new byte[50], 1, 0, 0, handlers, false, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("6.10.1");
    }

    /** JCVM 3.1 §6.10: handler_count is a u1 (0..255). */
    @Test
    void moreThan255HandlersAreRejected_6_10() {
        List<JcvmExceptionHandler> handlers = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            handlers.add(new JcvmExceptionHandler(0, 1, 2 + i, 0));
        }
        var method = new TranslatedMethod(new byte[300], 1, 0, 0, handlers, false, List.of());

        assertThatThrownBy(() -> MethodComponent.generate(List.of(method)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("255");
    }

    /** JCVM 3.1 §2.2.4.4: at most 32767 bytecodes per method. */
    @Test
    void methodLongerThan32767BytesIsRejected_2_2_4_4() {
        var method = new TranslatedMethod(new byte[32768], 1, 0, 0, List.of(), false, List.of());

        assertThatThrownBy(() -> MethodComponent.generate(List.of(method)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32767");
    }

    @Test
    void headerValuesAbove255AreRejected_6_10_4() {
        var method = new TranslatedMethod(new byte[]{0x7A}, 256, 0, 0, List.of(), true, List.of());

        assertThatThrownBy(() -> MethodComponent.generate(List.of(method)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max_stack");
    }

    @Test
    void componentLargerThan65535BytesIsRejected_6_10() {
        List<TranslatedMethod> methods = new ArrayList<>(
                Collections.nCopies(3, new TranslatedMethod(new byte[30000], 1, 0, 0, List.of(), false, List.of())));

        assertThatThrownBy(() -> MethodComponent.generate(methods))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("65535");
    }

    private static List<Boolean> stopBits(byte[] component) {
        List<Boolean> bits = new ArrayList<>();
        int count = component[3] & 0xFF;
        for (int i = 0; i < count; i++) {
            bits.add((component[4 + 8 * i + 2] & 0x80) != 0);
        }
        return bits;
    }

    private static List<Integer> handlerOffsets(byte[] component) {
        List<Integer> offsets = new ArrayList<>();
        int count = component[3] & 0xFF;
        for (int i = 0; i < count; i++) {
            int o = 4 + 8 * i + 4;
            offsets.add(((component[o] & 0xFF) << 8) | (component[o + 1] & 0xFF));
        }
        return offsets;
    }
}
