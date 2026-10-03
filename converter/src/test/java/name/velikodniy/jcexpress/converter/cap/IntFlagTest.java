package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.translate.FixtureCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * JCVM 3.1 §6.4 ACC_INT: "The ACC_INT flag has the value of one if the Java int type is used by
 * at least one of the packages in this CAP file. The int type is used if one or more of the
 * following is present: a parameter to a method of type int [or int array], a local variable of
 * type int [or int array], a field of type int [or int array], an instruction of type int [or int
 * array]. Otherwise the ACC_INT flag has the value of 0." The flag reflects the use of int, and a
 * package that uses int cannot be converted for a target without int support (§2.2.3.1).
 */
class IntFlagTest {

    @TempDir
    Path out;

    @Test
    void intFieldIsAUseOfInt() {
        ClassInfo ci = new ClassInfo("p/C", "java/lang/Object", List.of(), 0x0001, List.of(),
                List.of(new FieldInfo("total", "I", 0x0002, null)));
        assertThat(HeaderComponent.usesInt(List.of(ci), List.of())).isTrue();
    }

    @Test
    void intArrayParameterIsAUseOfInt() {
        ClassInfo ci = new ClassInfo("p/C", "java/lang/Object", List.of(), 0x0001,
                List.of(new MethodInfo("sum", "(S[I)S", 0x0009, 0, 0, null, List.of())), List.of());
        assertThat(HeaderComponent.usesInt(List.of(ci), List.of())).isTrue();
    }

    @Test
    void intLocalVariableIsAUseOfInt() throws Exception {
        FixtureCompiler.compileSource("p.L", "package p;\npublic class L {\n"
                + "  static short f(short a) { int n = a; return (short) n; }\n}\n", 8, out);
        ClassInfo ci = ClassFileReader.readFile(out.resolve("p/L.class"));
        assertThat(HeaderComponent.usesInt(List.of(ci), List.of())).isTrue();
    }

    @Test
    void shortOnlyPackageDoesNotUseInt() throws Exception {
        FixtureCompiler.compileSource("p.S", "package p;\npublic class S {\n  short v;\n"
                + "  short f(byte[] b, short i) { return (short) (b[i] + v); }\n}\n", 8, out);
        ClassInfo ci = ClassFileReader.readFile(out.resolve("p/S.class"));
        assertThat(HeaderComponent.usesInt(List.of(ci), List.of())).isFalse();
    }

    /**
     * Before: the int field and int arithmetic of IntOpsApplet were silently narrowed to 16 bits
     * and the CAP file was produced with ACC_INT = 0 (JCVM 3.1 §2.2.3.1 requires rejection).
     */
    @Test
    void intUseWithoutIntSupportIsRejected_2_2_3_1() {
        Converter converter = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName("com.example.intops")
                .packageAid("A000000062070101")
                .packageVersion(1, 0)
                .applet("com.example.intops.IntOpsApplet", "A00000006207010101")
                .build();

        ConverterException e = catchThrowableOfType(ConverterException.class, converter::convert);

        assertThat(e).isNotNull();
        assertThat(e.violations()).extracting(v -> v.context() + ": " + v.message())
                .anyMatch(m -> m.startsWith("field counter") && m.contains("int support"))
                .anyMatch(m -> m.contains("process") && m.contains("2.2.3.1"));
    }
}
