package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.Converter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Instance field instructions take a 1-byte constant pool index; the {@code _w} forms take two
 * bytes (JCVM 3.1 §7.5.20/§7.5.21 versus §7.5.22, §7.5.75/§7.5.76 versus §7.5.77). A package with
 * more than 256 distinct CONSTANT_InstanceFieldref entries must use the wide forms for indices
 * above 255 instead of truncating them to another field.
 */
class FieldIndexWidthTest {

    private static final String PKG = "com.example.bytecode.bigcp";
    private static final int CLASSES = 3;
    private static final int FIELDS = 90;

    @Test
    void instanceFieldRefsAbove255UseWideForms_7_5_22(@TempDir Path out) throws Exception {
        FixtureCompiler.compileSource(PKG + ".BigCpApplet", source(), 8, out);
        byte[] cap = Converter.builder().classesDirectory(out).packageName(PKG)
                .packageAid("F04A43584320").packageVersion(1, 0)
                .applet(PKG + ".BigCpApplet", "F04A4358432001")
                .build().convert().capFile();
        CapView view = CapView.parse(cap);

        Set<Integer> fieldIndices = new HashSet<>();
        for (List<String> method : view.disassembledMethods()) {
            for (String insn : method) {
                if (insn.startsWith("getfield_") || insn.startsWith("putfield_")) {
                    int index = Integer.parseInt(insn.substring(insn.indexOf('#') + 1));
                    fieldIndices.add(index);
                    assertThat(view.constantPool().get(index).tag()).as(insn).isEqualTo(2);
                    assertThat(insn.contains("_w ") || index <= 255)
                            .as("%s uses the 1-byte form only for indices <= 255", insn).isTrue();
                }
            }
        }
        assertThat(fieldIndices).as("every field keeps its own CONSTANT_InstanceFieldref")
                .hasSize(CLASSES * FIELDS);
        assertThat(fieldIndices).anyMatch(i -> i > 255);
    }

    private static String source() {
        StringBuilder src = new StringBuilder("package " + PKG + ";\n"
                + "import javacard.framework.APDU;\nimport javacard.framework.Applet;\n"
                + "public class BigCpApplet extends Applet {\n");
        fieldsAndTouch(src, "a");
        src.append("  public static void install(byte[] b, short o, byte l) { new BigCpApplet().register(); }\n"
                + "  public void process(APDU apdu) { byte[] buf = apdu.getBuffer(); touch(buf);"
                + " new Holder1().touch(buf); new Holder2().touch(buf); }\n}\n");
        for (int c = 1; c < CLASSES; c++) {
            src.append("class Holder").append(c).append(" {\n");
            fieldsAndTouch(src, "h" + c);
            src.append("}\n");
        }
        return src.toString();
    }

    private static void fieldsAndTouch(StringBuilder src, String prefix) {
        for (int f = 0; f < FIELDS; f++) {
            src.append("  short ").append(prefix).append(f).append(";\n");
        }
        src.append("  void touch(byte[] buf) {\n    short s = 0;\n");
        for (int f = 0; f < FIELDS; f++) {
            src.append("    s += ").append(prefix).append(f).append(";\n");
        }
        src.append("    buf[0] = (byte) s;\n  }\n");
    }
}
