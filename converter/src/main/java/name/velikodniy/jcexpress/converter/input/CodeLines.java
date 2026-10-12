package name.velikodniy.jcexpress.converter.input;

import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source lines of the labels of a method's code (JVMS §4.7.12 LineNumberTable). An exception handler is known by its
 * label (JVMS §4.7.3), and javac gives the first instruction of a handler the line of its catch clause, so this is
 * where a report about the class a catch clause names points to.
 */
public final class CodeLines {

    private CodeLines() {
    }

    /**
     * Returns the source line of each label of the code: the line of the first instruction at the label.
     *
     * @param code the code of a method
     * @return the line by label; a label without a line in the LineNumberTable is absent
     */
    public static Map<Label, Integer> atLabels(CodeModel code) {
        Map<Label, Integer> lines = new HashMap<>();
        List<Label> pending = new ArrayList<>();
        int line = -1;
        for (CodeElement element : code) {
            if (element instanceof LineNumber number) {
                line = number.line();
            } else if (element instanceof LabelTarget target) {
                pending.add(target.label());
            } else if (element instanceof Instruction) {
                if (line >= 0) {
                    for (Label label : pending) {
                        lines.put(label, line);
                    }
                }
                pending.clear();
            }
        }
        return lines;
    }
}
