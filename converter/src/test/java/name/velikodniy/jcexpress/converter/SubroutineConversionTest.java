package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.SubroutineShapes.Shape;
import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import name.velikodniy.jcexpress.converter.capcheck.CapInvariants;
import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.translate.CapView;
import name.velikodniy.jcexpress.converter.translate.JcvmDisassembler;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.Label;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Packages whose class files contain {@code jsr}/{@code ret} subroutines (JCVM 3.1 §2.3.2.2 lists both
 * among the supported bytecodes; javac 1.3 and ECJ with {@code -target} 1.4 or lower compile {@code finally}
 * blocks into them) convert into CAP files without subroutines, which Oracle's verifier accepts.
 */
class SubroutineConversionTest {

    private static final int JAVA_1_2 = 46;
    private static final int JAVA_1_4 = 48;
    private static final int JAVA_5 = 49;

    @Test
    void finallySubroutinesConvertIntoCodeWithoutSubroutines_2_3_2_2(@TempDir Path dir) throws Exception {
        byte[] cap = convert(dir, JAVA_1_2).capFile();

        CapView view = CapView.parse(cap);
        assertThat(view.methods()).hasSizeGreaterThan(SubroutineShapes.JAVA_CARD.size());
        for (CapView.MethodBody method : view.methods()) {
            assertThat(JcvmDisassembler.mnemonics(method.code())).doesNotContain("jsr", "ret");
        }
        assertThat(CapInvariants.check(CapImage.parse(cap))).isEmpty();
    }

    @Test
    void classFileVersionsUpTo49GiveTheSameCapFile(@TempDir Path dir) throws Exception {
        byte[] java12 = convert(dir.resolve("a"), JAVA_1_2).capFile();

        assertThat(convert(dir.resolve("b"), JAVA_1_4).capFile()).isEqualTo(java12);
        assertThat(convert(dir.resolve("c"), JAVA_5).capFile()).isEqualTo(java12);
    }

    @Test
    void oracleVerifierAcceptsTheInlinedCode(@TempDir Path dir) throws Exception {
        assumeTrue(OracleVerifier.available(), "Oracle SDK not installed (build/oracle-sdks/jc305u3_kit)");
        ConverterResult result = convert(dir, JAVA_1_2);
        Path exp = dir.resolve("exp/probe/javacard/probe.exp");
        Files.createDirectories(exp.getParent());
        Files.write(exp, result.exportFile());

        assertThat(OracleVerifier.verify(result.capFile(), exp)).contains("0 errors").contains("0 warnings");
    }

    @Test
    void subroutineThatCannotBeInlinedIsAConversionError(@TempDir Path dir) throws IOException {
        Shape outerReturn = new Shape("outerReturn", b -> {
            Label outer = b.newLabel();
            Label inner = b.newLabel();
            SubroutineShapes.jsr(b, outer);
            b.iconst_0().ireturn().labelBinding(outer).astore(1);
            SubroutineShapes.jsr(b, inner);
            SubroutineShapes.ret(b, 1);
            b.labelBinding(inner).lineNumber(17).astore(2);
            SubroutineShapes.ret(b, 1);
        });
        write(dir, SubroutineShapes.finClass(JAVA_1_2, List.of(SubroutineShapes.JAVA_CARD.getFirst(), outerReturn)));

        assertThatThrownBy(() -> library(dir).convert())
                .isInstanceOf(ConverterException.class)
                .satisfies(e -> assertThat(((ConverterException) e).violations()).singleElement()
                        .satisfies(v -> assertThat(v).extracting(Violation::className, Violation::context,
                                Violation::bci, Violation::line).containsExactly("probe/Fin", "outerReturn(S)S", 12, 17)))
                .hasMessageContaining("Fin.java:17")
                .hasMessageContaining("JCVM 3.1 §2.3.2.2");
    }

    private static ConverterResult convert(Path dir, int version) throws Exception {
        write(dir, SubroutineShapes.finClass(version, SubroutineShapes.JAVA_CARD));
        return library(dir).convert();
    }

    private static Converter library(Path dir) {
        return Converter.builder().classesDirectory(dir).packageName("probe").packageAid("F04A435847")
                .packageVersion(1, 0).build();
    }

    private static void write(Path dir, byte[] classFile) throws IOException {
        Path file = dir.resolve("probe/Fin.class");
        Files.createDirectories(file.getParent());
        Files.write(file, classFile);
    }
}
