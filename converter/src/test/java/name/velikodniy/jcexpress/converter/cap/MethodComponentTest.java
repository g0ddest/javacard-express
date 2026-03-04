package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodComponentTest {

    @Test
    void shouldGenerateMethodWithStandardHeader() {
        byte[] bytecode = {0x18, 0x7A}; // aload_0, return
        var method = new TranslatedMethod(bytecode, 1, 1, 1, List.of(), false, List.of());

        MethodComponent.MethodResult result = MethodComponent.generate(List.of(method));

        assertThat(result.bytes()).isNotEmpty();
        assertThat(result.bytes()[0]).isEqualTo((byte) 7); // tag
        assertThat(result.offsets()).hasSize(1);
    }

    @Test
    void shouldTrackMethodOffsets() {
        var method1 = new TranslatedMethod(new byte[]{0x7A}, 1, 1, 1, List.of(), false, List.of());
        var method2 = new TranslatedMethod(new byte[]{0x18, 0x7A}, 2, 1, 1, List.of(), false, List.of());

        MethodComponent.MethodResult result = MethodComponent.generate(List.of(method1, method2));

        assertThat(result.offsets()).hasSize(2);
        // Second method should start after first
        assertThat(result.offsets()[1]).isGreaterThan(result.offsets()[0]);
    }

    @Test
    void shouldHandleAbstractMethods() {
        MethodComponent.MethodResult result = MethodComponent.generate(List.of(TranslatedMethod.EMPTY));

        assertThat(result.bytes()).isNotEmpty();
        assertThat(result.offsets()).hasSize(1);
    }
}
