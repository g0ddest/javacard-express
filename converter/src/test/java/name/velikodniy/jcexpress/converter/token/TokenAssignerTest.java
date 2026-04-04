package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenAssignerTest {

    @Test
    void shouldAssignClassTokens() throws IOException {
        PackageInfo pkg = PackageScanner.scan(Path.of("target/test-classes"), "com.example");
        TokenMap map = TokenAssigner.assign(pkg);

        assertThat(map.packageName()).isEqualTo("com.example");
        assertThat(map.classCount()).isGreaterThanOrEqualTo(1); // TestApplet

        // Every class gets a unique token
        List<Integer> tokens = map.classes().stream()
                .map(TokenMap.ClassEntry::token)
                .toList();
        assertThat(tokens).doesNotHaveDuplicates();
    }

    @Test
    void shouldBeDeterministic() throws IOException {
        PackageInfo pkg = PackageScanner.scan(Path.of("target/test-classes"), "com.example");

        TokenMap first = TokenAssigner.assign(pkg);
        TokenMap second = TokenAssigner.assign(pkg);

        assertThat(first.classCount()).isEqualTo(second.classCount());
        for (int i = 0; i < first.classCount(); i++) {
            assertThat(first.classes().get(i).internalName())
                    .isEqualTo(second.classes().get(i).internalName());
            assertThat(first.classes().get(i).token())
                    .isEqualTo(second.classes().get(i).token());
        }
    }

    @Test
    void shouldAssignMethodTokens() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));

        TokenMap map = TokenAssigner.assign(pkg);
        TokenMap.ClassEntry entry = map.findClass("com/example/TestApplet");

        // TestApplet has virtual method: process(APDU)V
        assertThat(entry.virtualMethods()).isNotEmpty();
        assertThat(entry.virtualMethods())
                .extracting(TokenMap.MethodEntry::name)
                .contains("process");

        // TestApplet has static methods: <init> (constructor) and install([BSB)V
        assertThat(entry.staticMethods()).isNotEmpty();
        assertThat(entry.staticMethods())
                .extracting(TokenMap.MethodEntry::name)
                .contains("<init>", "install");

        // Virtual and static tokens start from 0
        assertThat(entry.virtualMethods().getFirst().token()).isZero();
        assertThat(entry.staticMethods().getFirst().token()).isZero();
    }

    @Test
    void shouldAssignFieldTokens() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));

        TokenMap map = TokenAssigner.assign(pkg);
        TokenMap.ClassEntry entry = map.findClass("com/example/TestApplet");

        // TestApplet has instance fields: storage ([B), dataLen (S)
        assertThat(entry.instanceFields()).isNotEmpty();
        assertThat(entry.instanceFields())
                .extracting(TokenMap.FieldEntry::name)
                .contains("storage", "dataLen");

        // TestApplet has private static final byte fields (INS_GET, INS_PUT) —
        // these are compile-time constants and private, so excluded from token map
        assertThat(entry.staticFields()).isEmpty();
    }

    @Test
    void constructorsGetStaticTokens() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));

        TokenMap map = TokenAssigner.assign(pkg);
        TokenMap.ClassEntry entry = map.findClass("com/example/TestApplet");

        // Per JCVM spec §4.3.7.8: constructors are invoked via invokespecial
        // and use StaticMethodRef entries — they get static method tokens.
        // <init> should be in static methods with token 0, install gets token 1.
        assertThat(entry.staticMethods())
                .extracting(TokenMap.MethodEntry::name)
                .contains("<init>", "install");
        assertThat(entry.staticMethods())
                .extracting(TokenMap.MethodEntry::name)
                .doesNotContain("<clinit>"); // static initializers still excluded

        // Constructors must NOT appear in virtual methods
        assertThat(entry.virtualMethods())
                .extracting(TokenMap.MethodEntry::name)
                .doesNotContain("<init>");

        // <init> gets token 0, install gets token 1
        TokenMap.MethodEntry initMethod = entry.staticMethods().stream()
                .filter(m -> "<init>".equals(m.name())).findFirst().orElseThrow();
        TokenMap.MethodEntry installMethod = entry.staticMethods().stream()
                .filter(m -> "install".equals(m.name())).findFirst().orElseThrow();
        assertThat(initMethod.token()).isEqualTo(0);
        assertThat(installMethod.token()).isEqualTo(1);
    }

    @Test
    void shouldLookupByClassToken() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));

        TokenMap map = TokenAssigner.assign(pkg);

        int token = map.classToken("com/example/TestApplet");
        assertThat(token).isZero(); // only class, gets token 0
    }
}
