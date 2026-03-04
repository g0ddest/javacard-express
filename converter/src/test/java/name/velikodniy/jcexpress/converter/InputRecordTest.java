package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for input package records: ClassInfo, FieldInfo, MethodInfo,
 * ExceptionHandlerInfo, PackageInfo -- using real compiled test classes.
 */
class InputRecordTest {

    private static final Path CLASSES_DIR = Path.of("target/test-classes");

    // ── ClassInfo from real class file ──

    @Test
    void readTestApplet_shouldParseAllFields() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        assertThat(ci.thisClass()).isEqualTo("com/example/TestApplet");
        assertThat(ci.superClass()).isEqualTo("javacard/framework/Applet");
        assertThat(ci.interfaces()).isEmpty();
        assertThat(ci.isInterface()).isFalse();
        assertThat(ci.isAbstract()).isFalse();
        assertThat(ci.simpleName()).isEqualTo("TestApplet");
        assertThat(ci.packageName()).isEqualTo("com/example");
    }

    @Test
    void readTestApplet_shouldHaveMethods() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        assertThat(ci.methods()).isNotEmpty();

        // Should have <init>, install, process
        assertThat(ci.methods())
                .anyMatch(m -> m.isConstructor())
                .anyMatch(m -> m.name().equals("install") && m.isStatic())
                .anyMatch(m -> m.name().equals("process") && !m.isStatic());
    }

    @Test
    void readTestApplet_shouldHaveFields() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        assertThat(ci.fields()).isNotEmpty();

        // Should have at least storage (byte[]) and dataLen (short)
        assertThat(ci.fields())
                .anyMatch(f -> f.name().equals("storage") && f.descriptor().equals("[B"))
                .anyMatch(f -> f.name().equals("dataLen") && f.descriptor().equals("S"));
    }

    @Test
    void readTestApplet_compileTimeConstants() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        // INS_GET and INS_PUT should be compile-time constants (static final byte)
        assertThat(ci.fields())
                .filteredOn(FieldInfo::isCompileTimeConstant)
                .extracting(FieldInfo::name)
                .contains("INS_GET", "INS_PUT");
    }

    // ── FieldInfo from StaticsApplet ──

    @Test
    void readStaticsApplet_shouldHaveAllFieldTypes() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/statics/StaticsApplet.class"));

        // Compile-time constants (excluded from static image)
        assertThat(ci.fields())
                .filteredOn(f -> f.name().equals("CONST_BYTE"))
                .allMatch(FieldInfo::isCompileTimeConstant);

        // Reference fields
        assertThat(ci.fields())
                .filteredOn(f -> f.name().equals("staticBuffer"))
                .allMatch(f -> f.isStatic() && f.descriptor().equals("[B"));

        // Non-default primitives
        assertThat(ci.fields())
                .filteredOn(f -> f.name().equals("initByte"))
                .allMatch(f -> f.isStatic() && !f.isCompileTimeConstant());
    }

    // ── MethodInfo flags ──

    @Test
    void methodFlags_process_shouldBeVirtual() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        MethodInfo process = ci.methods().stream()
                .filter(m -> m.name().equals("process"))
                .findFirst().orElseThrow();

        assertThat(process.isStatic()).isFalse();
        assertThat(process.isAbstract()).isFalse();
        assertThat(process.isNative()).isFalse();
        assertThat(process.isConstructor()).isFalse();
        assertThat(process.isStaticInitializer()).isFalse();
        assertThat(process.bytecode()).isNotEmpty();
        assertThat(process.maxStack()).isGreaterThan(0);
        assertThat(process.maxLocals()).isGreaterThan(0);
    }

    @Test
    void methodFlags_install_shouldBeStatic() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        MethodInfo install = ci.methods().stream()
                .filter(m -> m.name().equals("install"))
                .findFirst().orElseThrow();

        assertThat(install.isStatic()).isTrue();
        assertThat(install.isConstructor()).isFalse();
    }

    @Test
    void methodFlags_init_shouldBeConstructor() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/TestApplet.class"));

        MethodInfo init = ci.methods().stream()
                .filter(MethodInfo::isConstructor)
                .findFirst().orElseThrow();

        assertThat(init.name()).isEqualTo("<init>");
        assertThat(init.isStatic()).isFalse();
    }

    // ── ExceptionHandlerInfo from ExceptionApplet ──

    @Test
    void exceptionApplet_shouldHaveHandlers() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/exception/ExceptionApplet.class"));

        // Find a method with exception handlers
        boolean hasHandlers = ci.methods().stream()
                .anyMatch(m -> !m.exceptionHandlers().isEmpty());
        assertThat(hasHandlers).isTrue();
    }

    @Test
    void exceptionHandler_shouldHaveValidOffsets() throws IOException {
        ClassInfo ci = ClassFileReader.readFile(
                CLASSES_DIR.resolve("com/example/exception/ExceptionApplet.class"));

        ci.methods().stream()
                .flatMap(m -> m.exceptionHandlers().stream())
                .forEach(h -> {
                    assertThat(h.startPc()).isGreaterThanOrEqualTo(0);
                    assertThat(h.endPc()).isGreaterThan(h.startPc());
                    assertThat(h.handlerPc()).isGreaterThanOrEqualTo(0);
                });
    }

    // ── PackageInfo ──

    @Test
    void packageInfo_fromScan() throws IOException {
        PackageInfo pi = PackageScanner.scan(CLASSES_DIR, "com.example");

        assertThat(pi.packageName()).isEqualTo("com.example");
        assertThat(pi.internalName()).isEqualTo("com/example");
        assertThat(pi.classes()).isNotEmpty();
        // Should contain TestApplet
        assertThat(pi.classes())
                .anyMatch(c -> c.thisClass().equals("com/example/TestApplet"));
    }

    @Test
    void packageInfo_internalName_multiSegment() {
        var pi = new PackageInfo("com.example.wallet", List.of());
        assertThat(pi.internalName()).isEqualTo("com/example/wallet");
    }

    @Test
    void packageInfo_internalName_singleSegment() {
        var pi = new PackageInfo("wallet", List.of());
        assertThat(pi.internalName()).isEqualTo("wallet");
    }

    // ── FieldInfo edge cases ──

    @Test
    void fieldInfo_staticNotFinal_notCompileTimeConstant() {
        var f = new FieldInfo("x", "I", 0x0008, (int) 42);
        // static but not final
        assertThat(f.isCompileTimeConstant()).isFalse();
    }

    @Test
    void fieldInfo_staticFinalArray_notCompileTimeConstant() {
        var f = new FieldInfo("arr", "[B", 0x0018, null);
        assertThat(f.isCompileTimeConstant()).isFalse();
    }

    @Test
    void fieldInfo_instanceFinal_notCompileTimeConstant() {
        var f = new FieldInfo("x", "S", 0x0010, (int) 10);
        assertThat(f.isStatic()).isFalse();
        assertThat(f.isCompileTimeConstant()).isFalse();
    }

    @Test
    void fieldInfo_shortDescriptor() {
        var f = new FieldInfo("val", "S", 0x0008, null);
        assertThat(f.descriptor()).isEqualTo("S");
        assertThat(f.isStatic()).isTrue();
        assertThat(f.isFinal()).isFalse();
    }
}
