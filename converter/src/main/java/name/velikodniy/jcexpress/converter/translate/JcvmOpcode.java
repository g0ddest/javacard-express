package name.velikodniy.jcexpress.converter.translate;

/**
 * Constants for all JCVM instruction opcodes as defined in
 * <i>Java Card Virtual Machine Specification 3.0.5, Chapter 7 (JCVM Instruction Set)</i>.
 *
 * <p>This class is part of <b>Stage 5: Bytecode Translation</b> of the converter pipeline.
 * {@link BytecodeTranslator} uses these constants when emitting translated bytecode, and
 * {@link name.velikodniy.jcexpress.converter.cap.MethodComponent} uses the {@link #length(int)}
 * method to walk the bytecode stream during CAP assembly.
 *
 * <h2>Opcode Categories</h2>
 * <ul>
 *   <li><b>Short ({@code s*}) opcodes</b>: operate on 16-bit values. These are the default
 *       target when translating JVM int operations for cards without 32-bit support.</li>
 *   <li><b>Int ({@code i*}) opcodes</b>: operate on 32-bit values. Emitted only when the
 *       ACC_INT flag is set in the CAP header, indicating the card supports the optional
 *       integer extension (JCVM spec 7.2).</li>
 *   <li><b>Wide ({@code _w}) variants</b>: instructions with 2-byte operands for branch
 *       offsets or CP indices that exceed the narrow 1-byte range.</li>
 *   <li><b>{@code _THIS} variants</b>: optimized field access opcodes that implicitly use
 *       {@code local[0]} (i.e. {@code this}) as the object reference, saving one byte
 *       compared to an explicit {@code aload_0} followed by a regular field opcode
 *       (JCVM spec 7.5.8-9).</li>
 * </ul>
 *
 * <h2>Instruction Length</h2>
 * <p>The static method {@link #length(int)} returns the total byte length (opcode + operands)
 * for fixed-length instructions, or 0 for variable-length instructions ({@code stableswitch},
 * {@code slookupswitch}, and their int variants). This is used during CAP serialization to
 * compute method sizes and to locate embedded constant pool references.
 *
 * <p>This class is a non-instantiable utility; all members are static constants.
 *
 * @see BytecodeTranslator
 * @see name.velikodniy.jcexpress.converter.cap.MethodComponent
 */
public final class JcvmOpcode {

    private JcvmOpcode() {}

    // ── Constants ──
    public static final int NOP           = 0x00;
    public static final int ACONST_NULL   = 0x01;
    public static final int SCONST_M1     = 0x02;
    public static final int SCONST_0      = 0x03;
    public static final int SCONST_1      = 0x04;
    public static final int SCONST_2      = 0x05;
    public static final int SCONST_3      = 0x06;
    public static final int SCONST_4      = 0x07;
    public static final int SCONST_5      = 0x08;
    public static final int ICONST_M1     = 0x09;
    public static final int ICONST_0      = 0x0A;
    public static final int ICONST_1      = 0x0B;
    public static final int ICONST_2      = 0x0C;
    public static final int ICONST_3      = 0x0D;
    public static final int ICONST_4      = 0x0E;
    public static final int ICONST_5      = 0x0F;
    public static final int BSPUSH        = 0x10;
    public static final int SSPUSH        = 0x11;
    public static final int BIPUSH        = 0x12;
    public static final int SIPUSH        = 0x13;
    public static final int IIPUSH        = 0x14;

    // ── Loads ──
    public static final int ALOAD         = 0x15;
    public static final int SLOAD         = 0x16;
    public static final int ILOAD         = 0x17;
    public static final int ALOAD_0       = 0x18;
    public static final int ALOAD_1       = 0x19;
    public static final int ALOAD_2       = 0x1A;
    public static final int ALOAD_3       = 0x1B;
    public static final int SLOAD_0       = 0x1C;
    public static final int SLOAD_1       = 0x1D;
    public static final int SLOAD_2       = 0x1E;
    public static final int SLOAD_3       = 0x1F;
    public static final int ILOAD_0       = 0x20;
    public static final int ILOAD_1       = 0x21;
    public static final int ILOAD_2       = 0x22;
    public static final int ILOAD_3       = 0x23;

    // ── Array loads ──
    public static final int AALOAD        = 0x24;
    public static final int BALOAD        = 0x25;
    public static final int SALOAD        = 0x26;
    public static final int IALOAD        = 0x27;

    // ── Stores ──
    public static final int ASTORE        = 0x28;
    public static final int SSTORE        = 0x29;
    public static final int ISTORE        = 0x2A;
    public static final int ASTORE_0      = 0x2B;
    public static final int ASTORE_1      = 0x2C;
    public static final int ASTORE_2      = 0x2D;
    public static final int ASTORE_3      = 0x2E;
    public static final int SSTORE_0      = 0x2F;
    public static final int SSTORE_1      = 0x30;
    public static final int SSTORE_2      = 0x31;
    public static final int SSTORE_3      = 0x32;
    public static final int ISTORE_0      = 0x33;
    public static final int ISTORE_1      = 0x34;
    public static final int ISTORE_2      = 0x35;
    public static final int ISTORE_3      = 0x36;

    // ── Array stores ──
    public static final int AASTORE       = 0x37;
    public static final int BASTORE       = 0x38;
    public static final int SASTORE       = 0x39;
    public static final int IASTORE       = 0x3A;

    // ── Stack ──
    public static final int POP           = 0x3B;
    public static final int POP2          = 0x3C;
    public static final int DUP           = 0x3D;
    public static final int DUP2          = 0x3E;
    public static final int DUP_X         = 0x3F;
    public static final int SWAP_X        = 0x40;

    // ── Short arithmetic ──
    public static final int SADD          = 0x41;
    public static final int IADD          = 0x42;
    public static final int SSUB          = 0x43;
    public static final int ISUB          = 0x44;
    public static final int SMUL          = 0x45;
    public static final int IMUL          = 0x46;
    public static final int SDIV          = 0x47;
    public static final int IDIV          = 0x48;
    public static final int SREM          = 0x49;
    public static final int IREM          = 0x4A;
    public static final int SNEG          = 0x4B;
    public static final int INEG          = 0x4C;

    // ── Short bitwise/shift ──
    public static final int SSHL          = 0x4D;
    public static final int ISHL          = 0x4E;
    public static final int SSHR          = 0x4F;
    public static final int ISHR          = 0x50;
    public static final int SUSHR         = 0x51;
    public static final int IUSHR         = 0x52;
    public static final int SAND          = 0x53;
    public static final int IAND          = 0x54;
    public static final int SOR           = 0x55;
    public static final int IOR           = 0x56;
    public static final int SXOR          = 0x57;
    public static final int IXOR          = 0x58;

    // ── Increment ──
    public static final int SINC          = 0x59;
    public static final int IINC          = 0x5A;

    // ── Conversions ──
    public static final int S2B           = 0x5B;
    public static final int S2I           = 0x5C;
    public static final int I2B           = 0x5D;
    public static final int I2S           = 0x5E;
    public static final int ICMP          = 0x5F;

    // ── Branches (1-byte offset) ──
    public static final int IFEQ          = 0x60;
    public static final int IFNE          = 0x61;
    public static final int IFLT          = 0x62;
    public static final int IFGE          = 0x63;
    public static final int IFGT          = 0x64;
    public static final int IFLE          = 0x65;
    public static final int IFNULL        = 0x66;
    public static final int IFNONNULL     = 0x67;
    public static final int IF_ACMPEQ     = 0x68;
    public static final int IF_ACMPNE     = 0x69;
    public static final int IF_SCMPEQ     = 0x6A;
    public static final int IF_SCMPNE     = 0x6B;
    public static final int IF_SCMPLT     = 0x6C;
    public static final int IF_SCMPGE     = 0x6D;
    public static final int IF_SCMPGT     = 0x6E;
    public static final int IF_SCMPLE     = 0x6F;

    // ── Control flow ──
    public static final int GOTO          = 0x70;
    public static final int JSR           = 0x71;
    public static final int RET           = 0x72;
    public static final int STABLESWITCH  = 0x73;
    public static final int ITABLESWITCH  = 0x74;
    public static final int SLOOKUPSWITCH = 0x75;
    public static final int ILOOKUPSWITCH = 0x76;

    // ── Returns ──
    public static final int ARETURN       = 0x77;
    public static final int SRETURN       = 0x78;
    public static final int IRETURN       = 0x79;
    public static final int RETURN        = 0x7A;

    // ── Static field access (2-byte CP index) ──
    public static final int GETSTATIC_A   = 0x7B;
    public static final int GETSTATIC_B   = 0x7C;
    public static final int GETSTATIC_S   = 0x7D;
    public static final int GETSTATIC_I   = 0x7E;
    public static final int PUTSTATIC_A   = 0x7F;
    public static final int PUTSTATIC_B   = 0x80;
    public static final int PUTSTATIC_S   = 0x81;
    public static final int PUTSTATIC_I   = 0x82;

    // ── Instance field access (1-byte CP index) ──
    public static final int GETFIELD_A    = 0x83;
    public static final int GETFIELD_B    = 0x84;
    public static final int GETFIELD_S    = 0x85;
    public static final int GETFIELD_I    = 0x86;
    public static final int PUTFIELD_A    = 0x87;
    public static final int PUTFIELD_B    = 0x88;
    public static final int PUTFIELD_S    = 0x89;
    public static final int PUTFIELD_I    = 0x8A;

    // ── Invocations ──
    public static final int INVOKEVIRTUAL   = 0x8B;
    public static final int INVOKESPECIAL   = 0x8C;
    public static final int INVOKESTATIC    = 0x8D;
    public static final int INVOKEINTERFACE = 0x8E;

    // ── Object creation ──
    public static final int NEW           = 0x8F;
    public static final int NEWARRAY      = 0x90;
    public static final int ANEWARRAY     = 0x91;
    public static final int ARRAYLENGTH   = 0x92;

    // ── Exceptions / type checks ──
    public static final int ATHROW        = 0x93;
    public static final int CHECKCAST     = 0x94;
    public static final int INSTANCEOF    = 0x95;

    // ── Wide variants ──
    public static final int SINC_W        = 0x96;
    public static final int IINC_W        = 0x97;
    public static final int IFEQ_W        = 0x98;
    public static final int IFNE_W        = 0x99;
    public static final int IFLT_W        = 0x9A;
    public static final int IFGE_W        = 0x9B;
    public static final int IFGT_W        = 0x9C;
    public static final int IFLE_W        = 0x9D;
    public static final int IFNULL_W      = 0x9E;
    public static final int IFNONNULL_W   = 0x9F;
    public static final int IF_ACMPEQ_W   = 0xA0;
    public static final int IF_ACMPNE_W   = 0xA1;
    public static final int IF_SCMPEQ_W   = 0xA2;
    public static final int IF_SCMPNE_W   = 0xA3;
    public static final int IF_SCMPLT_W   = 0xA4;
    public static final int IF_SCMPGE_W   = 0xA5;
    public static final int IF_SCMPGT_W   = 0xA6;
    public static final int IF_SCMPLE_W   = 0xA7;
    public static final int GOTO_W        = 0xA8;

    // ── Wide instance field access (2-byte CP index) ──
    public static final int GETFIELD_A_W  = 0xA9;
    public static final int GETFIELD_B_W  = 0xAA;
    public static final int GETFIELD_S_W  = 0xAB;
    public static final int GETFIELD_I_W  = 0xAC;
    public static final int GETFIELD_A_THIS = 0xAD;
    public static final int GETFIELD_B_THIS = 0xAE;
    public static final int GETFIELD_S_THIS = 0xAF;
    public static final int GETFIELD_I_THIS = 0xB0;
    public static final int PUTFIELD_A_W  = 0xB1;
    public static final int PUTFIELD_B_W  = 0xB2;
    public static final int PUTFIELD_S_W  = 0xB3;
    public static final int PUTFIELD_I_W  = 0xB4;
    public static final int PUTFIELD_A_THIS = 0xB5;
    public static final int PUTFIELD_B_THIS = 0xB6;
    public static final int PUTFIELD_S_THIS = 0xB7;
    public static final int PUTFIELD_I_THIS = 0xB8;

    /**
     * Returns the total instruction length in bytes (opcode byte + operand bytes) for
     * the given JCVM opcode.
     *
     * <p>Returns 0 for variable-length instructions ({@link #STABLESWITCH},
     * {@link #ITABLESWITCH}, {@link #SLOOKUPSWITCH}, {@link #ILOOKUPSWITCH}),
     * which require parsing the instruction body to determine the full length.
     *
     * @param opcode the JCVM opcode value (0x00-0xFF)
     * @return instruction length in bytes, or 0 for variable-length instructions
     */
    public static int length(int opcode) {
        return LENGTHS[opcode & 0xFF];
    }

    private static final int[] LENGTHS = new int[256];

    static {
        // Default: 1 byte (0-operand instructions)
        java.util.Arrays.fill(LENGTHS, 1);

        // 2-byte: opcode + 1 operand byte
        for (int op : new int[]{
                BSPUSH, BIPUSH, ALOAD, SLOAD, ILOAD, ASTORE, SSTORE, ISTORE,
                RET, NEWARRAY,
                GETFIELD_A, GETFIELD_B, GETFIELD_S, GETFIELD_I,
                PUTFIELD_A, PUTFIELD_B, PUTFIELD_S, PUTFIELD_I,
                GETFIELD_A_THIS, GETFIELD_B_THIS, GETFIELD_S_THIS, GETFIELD_I_THIS,
                PUTFIELD_A_THIS, PUTFIELD_B_THIS, PUTFIELD_S_THIS, PUTFIELD_I_THIS,
                // 1-byte branch offsets
                IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                IFNULL, IFNONNULL, IF_ACMPEQ, IF_ACMPNE,
                IF_SCMPEQ, IF_SCMPNE, IF_SCMPLT, IF_SCMPGE, IF_SCMPGT, IF_SCMPLE,
                GOTO
        }) {
            LENGTHS[op] = 2;
        }

        // 3-byte: opcode + 2 operand bytes
        for (int op : new int[]{
                SSPUSH, SIPUSH, SINC, IINC, JSR,
                GETSTATIC_A, GETSTATIC_B, GETSTATIC_S, GETSTATIC_I,
                PUTSTATIC_A, PUTSTATIC_B, PUTSTATIC_S, PUTSTATIC_I,
                INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC,
                GETFIELD_A_W, GETFIELD_B_W, GETFIELD_S_W, GETFIELD_I_W,
                PUTFIELD_A_W, PUTFIELD_B_W, PUTFIELD_S_W, PUTFIELD_I_W,
                NEW, ANEWARRAY,
                // 2-byte branch offsets (_w variants)
                IFEQ_W, IFNE_W, IFLT_W, IFGE_W, IFGT_W, IFLE_W,
                IFNULL_W, IFNONNULL_W, IF_ACMPEQ_W, IF_ACMPNE_W,
                IF_SCMPEQ_W, IF_SCMPNE_W, IF_SCMPLT_W, IF_SCMPGE_W, IF_SCMPGT_W, IF_SCMPLE_W,
                GOTO_W
        }) {
            LENGTHS[op] = 3;
        }

        // 4-byte
        LENGTHS[SINC_W] = 4;
        LENGTHS[IINC_W] = 4;
        LENGTHS[CHECKCAST] = 4;
        LENGTHS[INSTANCEOF] = 4;

        // 5-byte
        LENGTHS[INVOKEINTERFACE] = 5;
        LENGTHS[IIPUSH] = 5;

        // Variable-length
        LENGTHS[STABLESWITCH] = 0;
        LENGTHS[ITABLESWITCH] = 0;
        LENGTHS[SLOOKUPSWITCH] = 0;
        LENGTHS[ILOOKUPSWITCH] = 0;
    }
}
