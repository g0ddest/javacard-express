package name.velikodniy.jcexpress.converter.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClassFileReaderTest {

    @Test
    void shouldReadOwnClassFile() throws IOException {
        byte[] bytes = readClassBytes(ClassFileReaderTest.class);
        ClassInfo info = ClassFileReader.read(bytes);

        assertThat(info.thisClass())
                .isEqualTo("name/velikodniy/jcexpress/converter/input/ClassFileReaderTest");
        assertThat(info.superClass()).isEqualTo("java/lang/Object");
        assertThat(info.interfaces()).isEmpty();
        assertThat(info.methods()).isNotEmpty();
        assertThat(info.simpleName()).isEqualTo("ClassFileReaderTest");
        assertThat(info.packageName())
                .isEqualTo("name/velikodniy/jcexpress/converter/input");
    }

    @Test
    void shouldReadAppletSuperclass() throws IOException {
        byte[] bytes = readClassBytes(com.example.TestApplet.class);
        ClassInfo info = ClassFileReader.read(bytes);

        assertThat(info.thisClass()).isEqualTo("com/example/TestApplet");
        assertThat(info.superClass()).isEqualTo("javacard/framework/Applet");
        assertThat(info.isInterface()).isFalse();
        assertThat(info.isAbstract()).isFalse();
    }

    @Test
    void shouldExtractMethods() throws IOException {
        byte[] bytes = readClassBytes(com.example.TestApplet.class);
        ClassInfo info = ClassFileReader.read(bytes);

        assertThat(info.methods())
                .extracting(MethodInfo::name)
                .contains("process", "install", "<init>");

        MethodInfo process = info.methods().stream()
                .filter(m -> "process".equals(m.name()))
                .findFirst().orElseThrow();

        assertThat(process.descriptor()).isEqualTo("(Ljavacard/framework/APDU;)V");
        assertThat(process.isAbstract()).isFalse();
        assertThat(process.isStatic()).isFalse();
        assertThat(process.bytecode()).isNotEmpty();
        assertThat(process.maxStack()).isGreaterThan(0);

        MethodInfo install = info.methods().stream()
                .filter(m -> "install".equals(m.name()))
                .findFirst().orElseThrow();

        assertThat(install.isStatic()).isTrue();
        assertThat(install.descriptor()).isEqualTo("([BSB)V");
    }

    @Test
    void shouldExtractFields() throws IOException {
        byte[] bytes = readClassBytes(com.example.TestApplet.class);
        ClassInfo info = ClassFileReader.read(bytes);

        assertThat(info.fields())
                .extracting(FieldInfo::name)
                .contains("storage", "dataLen", "INS_GET", "INS_PUT");

        FieldInfo insGet = info.fields().stream()
                .filter(f -> "INS_GET".equals(f.name()))
                .findFirst().orElseThrow();

        assertThat(insGet.isStatic()).isTrue();
        assertThat(insGet.isFinal()).isTrue();
        assertThat(insGet.descriptor()).isEqualTo("B");
    }

    @Test
    void shouldExtractConstructor() throws IOException {
        byte[] bytes = readClassBytes(com.example.TestApplet.class);
        ClassInfo info = ClassFileReader.read(bytes);

        MethodInfo ctor = info.methods().stream()
                .filter(MethodInfo::isConstructor)
                .findFirst().orElseThrow();

        assertThat(ctor.name()).isEqualTo("<init>");
        assertThat(ctor.descriptor()).isEqualTo("([BSB)V");
        assertThat(ctor.bytecode()).isNotEmpty();
        assertThat(ctor.maxLocals()).isGreaterThan(0);
    }

    @Test
    void shouldHandleInterface() throws IOException {
        byte[] bytes = readClassBytes(javacard.framework.Shareable.class);
        ClassInfo info = ClassFileReader.read(bytes);

        assertThat(info.isInterface()).isTrue();
        assertThat(info.thisClass()).isEqualTo("javacard/framework/Shareable");
    }

    @Test
    void shouldHandleAbstractClass() throws IOException {
        byte[] bytes = readClassBytes(javacard.framework.Applet.class);
        ClassInfo info = ClassFileReader.read(bytes);

        assertThat(info.isAbstract()).isTrue();

        MethodInfo process = info.methods().stream()
                .filter(m -> "process".equals(m.name()))
                .findFirst().orElseThrow();

        assertThat(process.isAbstract()).isTrue();
        assertThat(process.bytecode()).isEmpty();
    }

    private static byte[] readClassBytes(Class<?> clazz) throws IOException {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (var in = ClassFileReaderTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Class resource not found: " + resource);
            }
            return in.readAllBytes();
        }
    }
}
