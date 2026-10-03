package name.velikodniy.jcexpress.converter.translate;

import java.util.List;

/**
 * One instruction of symbolic JCVM code, before the final layout.
 *
 * <p>The encoded size of some instructions is only known once the constant pool order and the
 * distance to branch targets are final: branches have a 1-byte and a 2-byte offset form
 * (JCVM 3.1 §7.5.24/§7.5.25, §7.5.35-§7.5.44) and instance field instructions have a 1-byte and a
 * 2-byte constant pool index form (§7.5.20-§7.5.22, §7.5.75-§7.5.77). {@link JcvmAssembler}
 * chooses those forms; everything else is fixed here.
 */
sealed interface JcvmInsn {

    /**
     * An instruction whose operands contain neither a constant pool index nor a branch offset.
     *
     * @param opcode   JCVM opcode
     * @param operands operand bytes following the opcode
     */
    record Plain(int opcode, byte[] operands) implements JcvmInsn {}

    /**
     * An instruction with a 2-byte constant pool index operand.
     *
     * @param opcode  JCVM opcode
     * @param prefix  operand bytes between the opcode and the index (e.g. the atype of checkcast)
     * @param cpIndex constant pool index before the final constant pool reordering
     * @param suffix  operand bytes after the index (e.g. the method token of invokeinterface)
     */
    record CpRef(int opcode, byte[] prefix, int cpIndex, byte[] suffix) implements JcvmInsn {}

    /**
     * An instance field instruction. The 1-byte index form is used when the final constant pool
     * index is at most 255, otherwise the {@code _w} form. The {@code _this} forms have no wide
     * variant: they become {@code aload_0} plus {@code getfield_<t>_w}, or {@code putfield_<t>_w}
     * with the {@code aload_0} emitted at the matching {@link ThisAnchor} before the value push.
     *
     * @param op      field operation
     * @param type    JCVM field type index: 0 = a, 1 = b, 2 = s, 3 = i
     * @param cpIndex constant pool index of the CONSTANT_InstanceFieldref
     * @param anchor  id of the {@link ThisAnchor} of a {@code putfield_<t>_this}, otherwise -1
     */
    record FieldRef(FieldOp op, int type, int cpIndex, int anchor) implements JcvmInsn {}

    /**
     * A conditional or unconditional branch with a 1-byte offset form ({@code opcode}) and a
     * 2-byte offset form ({@code opcode + 0x38}, e.g. {@code goto} 0x70 / {@code goto_w} 0xA8).
     *
     * @param opcode narrow branch opcode
     * @param target branch target
     */
    record Branch(int opcode, JcvmLabel target) implements JcvmInsn {}

    /**
     * {@code stableswitch} / {@code itableswitch} (§7.5.106, §7.5.66).
     *
     * @param opcode        switch opcode
     * @param defaultTarget default target
     * @param low           lowest key
     * @param high          highest key
     * @param targets       one target per key from {@code low} to {@code high}
     */
    record TableSwitch(int opcode, JcvmLabel defaultTarget, int low, int high,
                       List<JcvmLabel> targets) implements JcvmInsn {}

    /**
     * {@code slookupswitch} / {@code ilookupswitch} (§7.5.94, §7.5.50).
     *
     * @param opcode        switch opcode
     * @param defaultTarget default target
     * @param keys          match keys, sorted ascending
     * @param targets       one target per key
     */
    record LookupSwitch(int opcode, JcvmLabel defaultTarget, int[] keys,
                        List<JcvmLabel> targets) implements JcvmInsn {}

    /**
     * Binds a label to the position of the next instruction.
     *
     * @param label the label
     */
    record Mark(JcvmLabel label) implements JcvmInsn {}

    /**
     * Position before the value push of a {@code putfield_<t>_this}; emits {@code aload_0} only
     * when the field instruction with the same anchor id needs the wide form.
     *
     * @param id anchor id
     */
    record ThisAnchor(int id) implements JcvmInsn {}

    /** Instance field operations with their narrow, wide and {@code _this} opcodes. */
    enum FieldOp {
        /** getfield_&lt;t&gt; (§7.5.20) / getfield_&lt;t&gt;_w (§7.5.22). */
        GET(JcvmOpcode.GETFIELD_A, JcvmOpcode.GETFIELD_A_W),
        /** putfield_&lt;t&gt; (§7.5.75) / putfield_&lt;t&gt;_w (§7.5.77). */
        PUT(JcvmOpcode.PUTFIELD_A, JcvmOpcode.PUTFIELD_A_W),
        /** getfield_&lt;t&gt;_this (§7.5.21); wide: aload_0 + getfield_&lt;t&gt;_w. */
        GET_THIS(JcvmOpcode.GETFIELD_A_THIS, JcvmOpcode.GETFIELD_A_W),
        /** putfield_&lt;t&gt;_this (§7.5.76); wide: aload_0 at the anchor + putfield_&lt;t&gt;_w. */
        PUT_THIS(JcvmOpcode.PUTFIELD_A_THIS, JcvmOpcode.PUTFIELD_A_W);

        private final int narrowBase;
        private final int wideBase;

        FieldOp(int narrowBase, int wideBase) {
            this.narrowBase = narrowBase;
            this.wideBase = wideBase;
        }

        int narrowOpcode(int type) {
            return narrowBase + type;
        }

        int wideOpcode(int type) {
            return wideBase + type;
        }
    }
}
