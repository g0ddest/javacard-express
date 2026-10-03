package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.CpReference;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Lays out and encodes symbolic JCVM code.
 *
 * <p>Instance field instructions take the 1-byte index form when their final constant pool index
 * is at most 255 and the {@code _w} form otherwise (JCVM 3.1 §7.5.22, §7.5.77). Branches start in
 * the 1-byte offset form and are widened to the 2-byte form ({@code goto_w}, {@code if<cond>_w},
 * {@code if_scmp<cond>_w}, {@code if_acmp<cond>_w}, {@code ifnull_w}, {@code ifnonnull_w};
 * §7.5.25, §7.5.36, §7.5.38, §7.5.40, §7.5.42, §7.5.44) until every offset fits. Widening only
 * grows the code, so the iteration terminates. A method may have at most 32767 bytecodes
 * (§2.2.4.4).
 */
final class JcvmAssembler {

    /** JCVM 3.1 §2.2.4.4: "A method can have at most 32767 Java Card virtual machine bytecodes." */
    static final int MAX_METHOD_SIZE = 32767;
    /** Distance between a narrow branch opcode and its {@code _w} form (e.g. 0x70 / 0xA8). */
    private static final int WIDE_BRANCH_DELTA = 0x38;
    private static final int MAX_NARROW_INDEX = 0xFF;

    /**
     * Encoded code.
     *
     * @param code         bytecodes
     * @param cpReferences positions of all constant pool indices in {@code code}
     * @param labels       byte offset of every bound label
     */
    record Result(byte[] code, List<CpReference> cpReferences, Map<JcvmLabel, Integer> labels) {
        int offset(JcvmLabel label) {
            Integer offset = labels.get(label);
            if (offset == null) {
                throw new IllegalStateException("unbound label in symbolic JCVM code");
            }
            return offset;
        }
    }

    private final List<JcvmInsn> insns;
    private final IntUnaryOperator cpMap;
    private final boolean[] wideBranch;
    private final Set<Integer> wideAnchors = new HashSet<>();
    private int[] positions;
    private Map<JcvmLabel, Integer> labels;

    private JcvmAssembler(List<JcvmInsn> insns, IntUnaryOperator cpMap) {
        this.insns = insns;
        this.cpMap = cpMap;
        this.wideBranch = new boolean[insns.size()];
        for (JcvmInsn insn : insns) {
            if (insn instanceof JcvmInsn.FieldRef f && f.op() == JcvmInsn.FieldOp.PUT_THIS
                    && isWide(f)) {
                wideAnchors.add(f.anchor());
            }
        }
    }

    /**
     * Assembles symbolic code.
     *
     * @param insns symbolic instructions
     * @param cpMap maps the constant pool indices recorded in the instructions to final indices
     * @return encoded code
     * @throws IllegalStateException if the method exceeds 32767 bytes or a label is unbound
     */
    static Result assemble(List<JcvmInsn> insns, IntUnaryOperator cpMap) {
        JcvmAssembler assembler = new JcvmAssembler(insns, cpMap);
        do {
            assembler.layout();
        } while (assembler.widenOverflowingBranches());
        int size = assembler.positions[insns.size()];
        if (size > MAX_METHOD_SIZE) {
            throw new IllegalStateException("method has " + size + " bytes of JCVM bytecode;"
                    + " JCVM 3.1 §2.2.4.4 allows at most " + MAX_METHOD_SIZE);
        }
        return assembler.encode();
    }

    private boolean isWide(JcvmInsn.FieldRef f) {
        return cpMap.applyAsInt(f.cpIndex()) > MAX_NARROW_INDEX;
    }

    private void layout() {
        positions = new int[insns.size() + 1];
        labels = new IdentityHashMap<>();
        int pc = 0;
        for (int i = 0; i < insns.size(); i++) {
            positions[i] = pc;
            if (insns.get(i) instanceof JcvmInsn.Mark mark) {
                labels.put(mark.label(), pc);
            }
            pc += size(i);
        }
        positions[insns.size()] = pc;
    }

    private boolean widenOverflowingBranches() {
        boolean changed = false;
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i) instanceof JcvmInsn.Branch b && !wideBranch[i]) {
                int offset = target(b.target()) - positions[i];
                if (offset < Byte.MIN_VALUE || offset > Byte.MAX_VALUE) {
                    wideBranch[i] = true;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private int size(int i) {
        return switch (insns.get(i)) {
            case JcvmInsn.Plain p -> 1 + p.operands().length;
            case JcvmInsn.CpRef c -> 3 + c.prefix().length + c.suffix().length;
            case JcvmInsn.FieldRef f -> fieldSize(f);
            case JcvmInsn.Branch b -> wideBranch[i] ? 3 : 2;
            case JcvmInsn.TableSwitch t -> 3 + 2 * keySize(t.opcode()) + 2 * t.targets().size();
            case JcvmInsn.LookupSwitch l -> 5 + (keySize(l.opcode()) + 2) * l.keys().length;
            case JcvmInsn.Mark m -> 0;
            case JcvmInsn.ThisAnchor a -> wideAnchors.contains(a.id()) ? 1 : 0;
        };
    }

    private int fieldSize(JcvmInsn.FieldRef f) {
        if (!isWide(f)) {
            return 2;
        }
        return f.op() == JcvmInsn.FieldOp.GET_THIS ? 4 : 3;
    }

    private static int keySize(int switchOpcode) {
        return switchOpcode == JcvmOpcode.ITABLESWITCH || switchOpcode == JcvmOpcode.ILOOKUPSWITCH
                ? 4 : 2;
    }

    private int target(JcvmLabel label) {
        Integer offset = labels.get(label);
        if (offset == null) {
            throw new IllegalStateException("branch to an unbound label in symbolic JCVM code");
        }
        return offset;
    }

    private Result encode() {
        var out = new ByteArrayOutputStream();
        List<CpReference> refs = new ArrayList<>();
        for (int i = 0; i < insns.size(); i++) {
            encode(i, out, refs);
            if (out.size() != positions[i + 1]) {
                throw new IllegalStateException("JCVM layout mismatch at instruction " + i);
            }
        }
        return new Result(out.toByteArray(), List.copyOf(refs), Map.copyOf(labels));
    }

    private void encode(int i, ByteArrayOutputStream out, List<CpReference> refs) {
        switch (insns.get(i)) {
            case JcvmInsn.Plain p -> {
                out.write(p.opcode());
                out.writeBytes(p.operands());
            }
            case JcvmInsn.CpRef c -> encodeCpRef(c, out, refs);
            case JcvmInsn.FieldRef f -> encodeField(f, out, refs);
            case JcvmInsn.Branch b -> encodeBranch(i, b, out);
            case JcvmInsn.TableSwitch t -> encodeTableSwitch(i, t, out);
            case JcvmInsn.LookupSwitch l -> encodeLookupSwitch(i, l, out);
            case JcvmInsn.Mark m -> { /* no bytes */ }
            case JcvmInsn.ThisAnchor a -> {
                if (wideAnchors.contains(a.id())) {
                    out.write(JcvmOpcode.ALOAD_0);
                }
            }
        }
    }

    private void encodeCpRef(JcvmInsn.CpRef c, ByteArrayOutputStream out, List<CpReference> refs) {
        int index = finalIndex(c.cpIndex());
        out.write(c.opcode());
        out.writeBytes(c.prefix());
        refs.add(new CpReference(out.size(), index, 2));
        writeU2(out, index);
        out.writeBytes(c.suffix());
    }

    private void encodeField(JcvmInsn.FieldRef f, ByteArrayOutputStream out, List<CpReference> refs) {
        int index = finalIndex(f.cpIndex());
        if (index <= MAX_NARROW_INDEX) {
            out.write(f.op().narrowOpcode(f.type()));
            refs.add(new CpReference(out.size(), index, 1));
            out.write(index);
            return;
        }
        if (f.op() == JcvmInsn.FieldOp.GET_THIS) {
            out.write(JcvmOpcode.ALOAD_0);
        }
        out.write(f.op().wideOpcode(f.type()));
        refs.add(new CpReference(out.size(), index, 2));
        writeU2(out, index);
    }

    private int finalIndex(int cpIndex) {
        int index = cpMap.applyAsInt(cpIndex);
        if (index < 0 || index > 0xFFFF) {
            throw new IllegalStateException("constant pool index " + index + " out of range");
        }
        return index;
    }

    private void encodeBranch(int i, JcvmInsn.Branch b, ByteArrayOutputStream out) {
        int offset = target(b.target()) - positions[i];
        if (wideBranch[i]) {
            out.write(b.opcode() + WIDE_BRANCH_DELTA);
            writeU2(out, offset);
        } else {
            out.write(b.opcode());
            out.write(offset);
        }
    }

    private void encodeTableSwitch(int i, JcvmInsn.TableSwitch t, ByteArrayOutputStream out) {
        out.write(t.opcode());
        writeU2(out, target(t.defaultTarget()) - positions[i]);
        writeKey(out, t.opcode(), t.low());
        writeKey(out, t.opcode(), t.high());
        for (JcvmLabel target : t.targets()) {
            writeU2(out, target(target) - positions[i]);
        }
    }

    private void encodeLookupSwitch(int i, JcvmInsn.LookupSwitch l, ByteArrayOutputStream out) {
        out.write(l.opcode());
        writeU2(out, target(l.defaultTarget()) - positions[i]);
        writeU2(out, l.keys().length);
        for (int k = 0; k < l.keys().length; k++) {
            writeKey(out, l.opcode(), l.keys()[k]);
            writeU2(out, target(l.targets().get(k)) - positions[i]);
        }
    }

    private static void writeKey(ByteArrayOutputStream out, int switchOpcode, int key) {
        if (keySize(switchOpcode) == 4) {
            writeU2(out, key >> 16);
        }
        writeU2(out, key);
    }

    private static void writeU2(ByteArrayOutputStream out, int value) {
        out.write(value >> 8);
        out.write(value);
    }
}
