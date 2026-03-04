package name.velikodniy.jcexpress.converter.token;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TokenMap} and its nested record types.
 */
class TokenMapTest {

    private static TokenMap.ClassEntry classEntry(String name, int token) {
        return new TokenMap.ClassEntry(name, token,
                List.of(), List.of(), List.of(), List.of());
    }

    private static TokenMap.ClassEntry classEntryWithMethods(String name, int token,
            List<TokenMap.MethodEntry> virtualMethods,
            List<TokenMap.MethodEntry> staticMethods) {
        return new TokenMap.ClassEntry(name, token,
                virtualMethods, staticMethods, List.of(), List.of());
    }

    private static TokenMap.ClassEntry classEntryWithFields(String name, int token,
            List<TokenMap.FieldEntry> instanceFields,
            List<TokenMap.FieldEntry> staticFields) {
        return new TokenMap.ClassEntry(name, token,
                List.of(), List.of(), instanceFields, staticFields);
    }

    // ── TokenMap tests ──

    @Test
    void findClass_shouldReturnMatchingEntry() {
        var entry = classEntry("com/example/MyApplet", 0);
        var map = new TokenMap("com.example", List.of(entry));

        assertThat(map.findClass("com/example/MyApplet")).isSameAs(entry);
    }

    @Test
    void findClass_shouldThrowForMissingClass() {
        var map = new TokenMap("com.example", List.of(classEntry("com/example/A", 0)));

        assertThatThrownBy(() -> map.findClass("com/example/Missing"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("com/example/Missing");
    }

    @Test
    void classToken_shouldReturnTokenValue() {
        var map = new TokenMap("pkg",
                List.of(classEntry("pkg/A", 0), classEntry("pkg/B", 1)));

        assertThat(map.classToken("pkg/A")).isZero();
        assertThat(map.classToken("pkg/B")).isEqualTo(1);
    }

    @Test
    void classToken_shouldThrowForMissingClass() {
        var map = new TokenMap("pkg", List.of());

        assertThatThrownBy(() -> map.classToken("pkg/Missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void classCount_shouldReturnNumberOfClasses() {
        var map = new TokenMap("pkg",
                List.of(classEntry("pkg/A", 0), classEntry("pkg/B", 1), classEntry("pkg/C", 2)));

        assertThat(map.classCount()).isEqualTo(3);
    }

    @Test
    void classCount_shouldReturnZeroForEmptyMap() {
        var map = new TokenMap("pkg", List.of());

        assertThat(map.classCount()).isZero();
    }

    @Test
    void packageName_shouldBeAccessible() {
        var map = new TokenMap("com.example.wallet", List.of());

        assertThat(map.packageName()).isEqualTo("com.example.wallet");
    }

    // ── ClassEntry tests ──

    @Test
    void findVirtualMethod_shouldReturnMatch() {
        var method = new TokenMap.MethodEntry("process", "(Ljavacard/framework/APDU;)V", 7);
        var entry = classEntryWithMethods("com/example/App", 0,
                List.of(method), List.of());

        assertThat(entry.findVirtualMethod("process", "(Ljavacard/framework/APDU;)V"))
                .isSameAs(method);
    }

    @Test
    void findVirtualMethod_shouldMatchBothNameAndDescriptor() {
        var m1 = new TokenMap.MethodEntry("equals", "(Ljava/lang/Object;)Z", 0);
        var m2 = new TokenMap.MethodEntry("equals", "([BSB)Z", 2);
        var entry = classEntryWithMethods("pkg/A", 0, List.of(m1, m2), List.of());

        assertThat(entry.findVirtualMethod("equals", "([BSB)Z")).isSameAs(m2);
    }

    @Test
    void findVirtualMethod_shouldThrowForMissing() {
        var entry = classEntryWithMethods("pkg/A", 0, List.of(), List.of());

        assertThatThrownBy(() -> entry.findVirtualMethod("noSuchMethod", "()V"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("noSuchMethod");
    }

    @Test
    void findStaticMethod_shouldReturnMatch() {
        var method = new TokenMap.MethodEntry("install", "([BSB)V", 0);
        var entry = classEntryWithMethods("com/example/App", 0,
                List.of(), List.of(method));

        assertThat(entry.findStaticMethod("install", "([BSB)V")).isSameAs(method);
    }

    @Test
    void findStaticMethod_shouldThrowForMissing() {
        var entry = classEntryWithMethods("pkg/A", 0, List.of(), List.of());

        assertThatThrownBy(() -> entry.findStaticMethod("missing", "()V"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void findInstanceField_shouldReturnMatch() {
        var field = new TokenMap.FieldEntry("balance", "S", 0);
        var entry = classEntryWithFields("pkg/A", 0, List.of(field), List.of());

        assertThat(entry.findInstanceField("balance")).isSameAs(field);
    }

    @Test
    void findInstanceField_shouldThrowForMissing() {
        var entry = classEntryWithFields("pkg/A", 0, List.of(), List.of());

        assertThatThrownBy(() -> entry.findInstanceField("noField"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("noField");
    }

    @Test
    void findStaticField_shouldReturnMatch() {
        var field = new TokenMap.FieldEntry("counter", "I", 0);
        var entry = classEntryWithFields("pkg/A", 0, List.of(), List.of(field));

        assertThat(entry.findStaticField("counter")).isSameAs(field);
    }

    @Test
    void findStaticField_shouldThrowForMissing() {
        var entry = classEntryWithFields("pkg/A", 0, List.of(), List.of());

        assertThatThrownBy(() -> entry.findStaticField("noField"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("noField");
    }

    // ── MethodEntry record tests ──

    @Test
    void methodEntry_shouldExposeFields() {
        var me = new TokenMap.MethodEntry("process", "(Ljavacard/framework/APDU;)V", 7);

        assertThat(me.name()).isEqualTo("process");
        assertThat(me.descriptor()).isEqualTo("(Ljavacard/framework/APDU;)V");
        assertThat(me.token()).isEqualTo(7);
    }

    // ── FieldEntry record tests ──

    @Test
    void fieldEntry_shouldExposeFields() {
        var fe = new TokenMap.FieldEntry("storage", "[B", 0);

        assertThat(fe.name()).isEqualTo("storage");
        assertThat(fe.descriptor()).isEqualTo("[B");
        assertThat(fe.token()).isZero();
    }

    // ── Multiple classes with find ──

    @Test
    void findClass_withMultipleClasses_shouldFindEach() {
        var a = classEntry("pkg/Alpha", 0);
        var b = classEntry("pkg/Beta", 1);
        var c = classEntry("pkg/Gamma", 2);
        var map = new TokenMap("pkg", List.of(a, b, c));

        assertThat(map.findClass("pkg/Alpha")).isSameAs(a);
        assertThat(map.findClass("pkg/Beta")).isSameAs(b);
        assertThat(map.findClass("pkg/Gamma")).isSameAs(c);
    }
}
