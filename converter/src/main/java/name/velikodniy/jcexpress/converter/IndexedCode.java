package name.velikodniy.jcexpress.converter;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The instructions of one method numbered from 0 in code order, with every label of the method (branch
 * targets, exception table, local variable scopes) translated into the number of the instruction it marks.
 *
 * <p>Labels are resolved through {@link CodeAttribute#labelToBci}, which does not depend on the
 * {@code StackMapTable} attribute (JCVM 3.1 §2.3.1.2.7 does not require it). The number {@link #size()}
 * stands for the end of the code (an exclusive range end).
 */
final class IndexedCode {

    /** An exception table entry (JVMS 4.7.3): instructions {@code [start, end)} are protected by {@code handler}. */
    record Catch(int start, int end, int handler, Optional<ClassEntry> catchType) {}

    /** A {@link LocalVariable} or {@link LocalVariableType} entry valid for instructions {@code [start, end)}. */
    record Scope(int start, int end, PseudoInstruction entry) {}

    private final CodeAttribute code;
    private final List<Instruction> instructions = new ArrayList<>();
    private final List<Integer> bcis = new ArrayList<>();
    private final List<Integer> lines = new ArrayList<>();
    private final Map<Integer, Integer> indexByBci = new HashMap<>();
    private final List<Catch> catches = new ArrayList<>();
    private final List<Scope> scopes = new ArrayList<>();

    IndexedCode(CodeAttribute code) {
        this.code = code;
        readInstructions();
        for (ExceptionCatch c : code.exceptionHandlers()) {
            catches.add(new Catch(index(c.tryStart()), index(c.tryEnd()), index(c.handler()), c.catchType()));
        }
        for (CodeElement e : code) {
            switch (e) {
                case LocalVariable lv -> scopes.add(new Scope(index(lv.startScope()), index(lv.endScope()), lv));
                case LocalVariableType lt -> scopes.add(new Scope(index(lt.startScope()), index(lt.endScope()), lt));
                default -> { }
            }
        }
    }

    /** Instructions with their bytecode index and the source line in effect (LineNumber precedes them). */
    private void readInstructions() {
        int bci = 0;
        int line = -1;
        for (CodeElement e : code) {
            if (e instanceof LineNumber ln) {
                line = ln.line();
            } else if (e instanceof Instruction insn) {
                indexByBci.put(bci, instructions.size());
                instructions.add(insn);
                bcis.add(bci);
                lines.add(line);
                bci += insn.sizeInBytes();
            }
        }
        indexByBci.put(bci, instructions.size());
        bcis.add(bci);
    }

    /** Number of instructions. */
    int size() {
        return instructions.size();
    }

    /** The instruction with the given number. */
    Instruction at(int index) {
        return instructions.get(index);
    }

    /** Bytecode index of an instruction ({@code size()} gives the code length). */
    int bci(int index) {
        return bcis.get(index);
    }

    /** Source line of an instruction (LineNumberTable), or -1. */
    int line(int index) {
        return index < lines.size() ? lines.get(index) : -1;
    }

    /** Number of the instruction a label marks ({@link #size()} for the end of the code). */
    int index(Label label) {
        Integer index = indexByBci.get(code.labelToBci(label));
        if (index == null) {
            throw new IllegalStateException("label inside an instruction at bci " + code.labelToBci(label));
        }
        return index;
    }

    /** Exception table in its order (the order is the handler priority, JVMS 2.10). */
    List<Catch> catches() {
        return catches;
    }

    /** Local variable (type) table entries. */
    List<Scope> scopes() {
        return scopes;
    }

    /** Handlers whose protected range contains the instruction, in exception table order. */
    List<Integer> handlersOf(int index) {
        List<Integer> handlers = new ArrayList<>();
        for (Catch c : catches) {
            if (c.start() <= index && index < c.end()) {
                handlers.add(c.handler());
            }
        }
        return handlers;
    }
}
