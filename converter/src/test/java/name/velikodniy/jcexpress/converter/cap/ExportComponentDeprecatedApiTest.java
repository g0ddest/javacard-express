package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.token.TokenMap.ClassEntry;
import name.velikodniy.jcexpress.converter.token.TokenMap.FieldEntry;
import name.velikodniy.jcexpress.converter.token.TokenMap.MethodEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The deprecated {@code ExportComponent.generate(TokenMap, int[], int[], Map, Map)} fails instead of
 * writing a wrong component.
 *
 * <p>JCVM 3.1 §6.13: {@code class_offset} is the offset of the class in the Class component,
 * {@code static_field_offsets} and {@code static_method_offsets} are offsets into the static field image
 * and the Method component, and an index into {@code class_exports} is the class token. Offset 0 is a
 * real location in each of these (the first class, the first static field, the start of the Method
 * component info), so an element whose location the caller did not supply must not be written as 0: other
 * packages would link to the wrong element on the card.
 */
@SuppressWarnings("deprecation")
class ExportComponentDeprecatedApiTest {

    private static final String CLASS = "p/A";
    private static final TokenMap TOKENS = new TokenMap("p", List.of(new ClassEntry(CLASS, 0, List.of(),
            List.of(new MethodEntry("make", "()V", 0)), List.of(), List.of(new FieldEntry("count", "S", 0)))));
    private static final Map<String, Integer> METHOD_INDEX = Map.of("p/A:make:()V", 0);
    private static final Map<String, Integer> FIELD_OFFSETS = Map.of("p/A:count", 4);

    @Test
    void writesTheSuppliedOffsets_6_13() {
        byte[] component = ExportComponent.generate(TOKENS, new int[]{0x21}, new int[]{0x10}, METHOD_INDEX,
                FIELD_OFFSETS);

        // tag 10, size 9, class_count 1, class_offset 0x0010, 1 field, 1 method, field 0x0004, method 0x0021
        assertThat(component).containsExactly(0x0A, 0x00, 0x09, 0x01, 0x00, 0x10, 0x01, 0x01, 0x00, 0x04,
                0x00, 0x21);
    }

    @Test
    void missingClassOffsetIsAnError_6_13() {
        assertThatThrownBy(() -> ExportComponent.generate(TOKENS, new int[]{0x21}, new int[0], METHOD_INDEX,
                FIELD_OFFSETS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CLASS)
                .hasMessageContaining("6.13");
    }

    @Test
    void missingStaticMethodIsAnError_6_13() {
        assertThatThrownBy(() -> ExportComponent.generate(TOKENS, new int[]{0x21}, new int[]{0x10}, Map.of(),
                FIELD_OFFSETS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p/A:make:()V");
    }

    @Test
    void methodIndexWithoutOffsetIsAnError_6_13() {
        assertThatThrownBy(() -> ExportComponent.generate(TOKENS, new int[]{0x21}, new int[]{0x10},
                Map.of("p/A:make:()V", 1), FIELD_OFFSETS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p/A:make:()V");
    }

    @Test
    void missingStaticFieldIsAnError_6_13() {
        assertThatThrownBy(() -> ExportComponent.generate(TOKENS, new int[]{0x21}, new int[]{0x10}, METHOD_INDEX,
                Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p/A:count");
    }

    @Test
    void classTokensMustBeTheIndexesOfTheClassExports_6_13() {
        TokenMap gap = new TokenMap("p", List.of(new ClassEntry(CLASS, 1, List.of(), List.of(), List.of(),
                List.of())));

        assertThatThrownBy(() -> ExportComponent.generate(gap, new int[0], new int[]{0x10}, Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CLASS)
                .hasMessageContaining("class token 1");
    }
}
