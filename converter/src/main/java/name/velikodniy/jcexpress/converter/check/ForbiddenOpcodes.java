package name.velikodniy.jcexpress.converter.check;

import java.util.Arrays;

/**
 * Lookup table of the JVM bytecode opcodes that the subset check rejects: opcodes outside the Java Card
 * subset (JCVM 3.1 §2.3.2.1), and subroutine instructions, which must have been inlined before the check.
 *
 * <p>This class is used by {@link SubsetChecker} during <strong>Stage 2: Subset
 * Check</strong> of the converter pipeline to identify illegal bytecode instructions
 * in method bodies. It also provides instruction length information needed to walk
 * the bytecode stream opcode-by-opcode.
 *
 * <h2>Forbidden opcode categories</h2>
 * <ul>
 *   <li><strong>{@code long}, {@code float} and {@code double} operations</strong> (JCVM 3.1
 *       §2.2.1.3) -- all load, store, arithmetic, comparison, conversion, return, constant
 *       ({@code ldc2_w}) and array opcodes of these types.</li>
 *   <li><strong>{@code char}</strong> (§2.2.1.3) -- {@code caload}, {@code castore} and
 *       {@code i2c}.</li>
 *   <li><strong>Arrays of more than one dimension</strong> (§2.2.1.3) --
 *       {@code multianewarray}.</li>
 *   <li><strong>Threads</strong> (§2.2.1.1.4) -- {@code monitorenter} and
 *       {@code monitorexit} ({@code synchronized} blocks).</li>
 *   <li><strong>Subroutines</strong> -- {@code jsr} and {@code ret} are supported (§2.3.2.2, JCVM
 *       instructions §7.5.69 and §7.5.79), and {@code jsr_w}, like {@code goto_w}, has a translation, but
 *       the {@link name.velikodniy.jcexpress.converter.Converter} inlines subroutines when it reads the
 *       class files, before this check. They are reported only in code that skipped that step.</li>
 *   <li><strong>{@code invokedynamic}</strong> -- lambdas, method references and string
 *       concatenation have no JCVM counterpart.</li>
 * </ul>
 * {@code goto_w} is allowed: it is translated to the JCVM {@code goto} or {@code goto_w}
 * (§7.5.24, §7.5.25).
 *
 * <h2>Instruction length table</h2>
 * <p>In addition to the forbidden-opcode lookup, this class maintains a table of
 * instruction lengths for the full JVM instruction set. This is necessary for the
 * raw bytecode scan of methods without a parsed class file to correctly advance past
 * variable-length instructions such as {@code tableswitch}, {@code lookupswitch},
 * and {@code wide}. A length of {@code 0} signals that the instruction requires
 * special variable-length handling.
 *
 * <p>This class is a stateless utility with no public constructor. All tables are
 * populated in a static initializer.
 *
 * @see SubsetChecker
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html">
 *      JVM Specification, Chapter 6 -- The Java Virtual Machine Instruction Set</a>
 */
public final class ForbiddenOpcodes {

    private ForbiddenOpcodes() {}

    private static final String[] REASONS = new String[256];
    private static final int[] INST_LENGTHS = new int[256];

    static {
        initForbidden();
        initLengths();
    }

    /**
     * Returns {@code true} if the given JVM opcode is not part of the JCVM
     * instruction set and therefore forbidden on the Java Card platform.
     *
     * @param opcode the unsigned JVM opcode (0x00--0xFF)
     * @return {@code true} if the opcode is forbidden, {@code false} if it is
     *         allowed
     */
    public static boolean isForbidden(int opcode) {
        return REASONS[opcode & 0xFF] != null;
    }

    /**
     * Returns a human-readable reason why the given opcode is forbidden, or
     * {@code null} if the opcode is allowed.
     *
     * <p>Example reasons: {@code "float type not supported in JavaCard"},
     * {@code "threading not supported in JavaCard"}.
     *
     * @param opcode the unsigned JVM opcode (0x00--0xFF)
     * @return a descriptive reason string, or {@code null} if the opcode is allowed
     */
    public static String reason(int opcode) {
        return REASONS[opcode & 0xFF];
    }

    /**
     * Returns the total length of the instruction in bytes, including the opcode
     * byte itself.
     *
     * <p>Most JVM instructions have a fixed length (1--5 bytes). Three instructions
     * have variable length and return {@code 0} to signal that the caller must
     * compute the length manually:
     * <ul>
     *   <li>{@code tableswitch} (0xAA) -- alignment padding + jump table</li>
     *   <li>{@code lookupswitch} (0xAB) -- alignment padding + match-offset pairs</li>
     *   <li>{@code wide} (0xC4) -- depends on the widened opcode</li>
     * </ul>
     *
     * @param opcode the unsigned JVM opcode (0x00--0xFF)
     * @return the instruction length in bytes, or {@code 0} for variable-length
     *         instructions
     */
    public static int instructionLength(int opcode) {
        return INST_LENGTHS[opcode & 0xFF];
    }

    private static void forbid(int opcode, String reason) {
        REASONS[opcode] = reason;
    }

    private static void forbidRange(int from, int to, String reason) {
        for (int i = from; i <= to; i++) REASONS[i] = reason;
    }

    private static final String LONG = "long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
    private static final String FLOAT = "float type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
    private static final String DOUBLE = "double type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
    private static final String CHAR = "char type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
    private static final String THREADS = "threading not supported in JavaCard (synchronized,"
            + " JCVM 3.1 §2.2.1.1.4)";
    private static final String DYNAMIC = "invokedynamic not supported in JavaCard (lambdas, method"
            + " references and string concatenation have no JCVM counterpart)";
    private static final String SUBROUTINE = "jsr/ret subroutine that was not inlined: the converter inlines"
            + " subroutines when it reads the class files, before this check (JCVM 3.1 §2.3.2.2 supports them)";
    private static final String MULTI_ARRAY = "arrays of more than one dimension not supported in"
            + " JavaCard (JCVM 3.1 §2.2.1.3)";

    private static void initForbidden() {
        forbidConstantsLoadsAndStores();
        forbidArithmetic();
        forbidConversionsComparisonsAndReturns();
        forbidControlAndObjectOpcodes();
    }

    private static void forbidConstantsLoadsAndStores() {
        forbidRange(0x09, 0x0A, LONG);   // lconst_0..1
        forbidRange(0x0B, 0x0D, FLOAT);  // fconst_0..2
        forbidRange(0x0E, 0x0F, DOUBLE); // dconst_0..1
        forbid(0x14, "ldc2_w: long/double constants not supported in JavaCard (JCVM 3.1 §2.2.1.3)");
        forbid(0x16, LONG);   // lload
        forbid(0x17, FLOAT);  // fload
        forbid(0x18, DOUBLE); // dload
        forbidRange(0x1E, 0x21, LONG);   // lload_0..3
        forbidRange(0x22, 0x25, FLOAT);  // fload_0..3
        forbidRange(0x26, 0x29, DOUBLE); // dload_0..3
        forbid(0x2F, LONG);   // laload
        forbid(0x30, FLOAT);  // faload
        forbid(0x31, DOUBLE); // daload
        forbid(0x34, CHAR);   // caload
        forbid(0x37, LONG);   // lstore
        forbid(0x38, FLOAT);  // fstore
        forbid(0x39, DOUBLE); // dstore
        forbidRange(0x3F, 0x42, LONG);   // lstore_0..3
        forbidRange(0x43, 0x46, FLOAT);  // fstore_0..3
        forbidRange(0x47, 0x4A, DOUBLE); // dstore_0..3
        forbid(0x50, LONG);   // lastore
        forbid(0x51, FLOAT);  // fastore
        forbid(0x52, DOUBLE); // dastore
        forbid(0x55, CHAR);   // castore
    }

    /** ladd..lxor, fadd..fneg, dadd..dneg: every fourth opcode from 0x61 / 0x62 / 0x63 on. */
    private static void forbidArithmetic() {
        for (int op : new int[]{0x61, 0x65, 0x69, 0x6D, 0x71, 0x75, 0x79, 0x7B, 0x7D, 0x7F, 0x81, 0x83}) {
            forbid(op, LONG); // ladd, lsub, lmul, ldiv, lrem, lneg, lshl, lshr, lushr, land, lor, lxor
        }
        for (int op : new int[]{0x62, 0x66, 0x6A, 0x6E, 0x72, 0x76}) {
            forbid(op, FLOAT); // fadd, fsub, fmul, fdiv, frem, fneg
        }
        for (int op : new int[]{0x63, 0x67, 0x6B, 0x6F, 0x73, 0x77}) {
            forbid(op, DOUBLE); // dadd, dsub, dmul, ddiv, drem, dneg
        }
    }

    private static void forbidConversionsComparisonsAndReturns() {
        forbid(0x85, LONG);   // i2l
        forbid(0x86, FLOAT);  // i2f
        forbid(0x87, DOUBLE); // i2d
        forbidRange(0x88, 0x8A, LONG);   // l2i, l2f, l2d
        forbidRange(0x8B, 0x8D, FLOAT);  // f2i, f2l, f2d
        forbidRange(0x8E, 0x90, DOUBLE); // d2i, d2l, d2f
        forbid(0x92, CHAR);   // i2c
        forbid(0x94, LONG);   // lcmp
        forbidRange(0x95, 0x96, FLOAT);  // fcmpl, fcmpg
        forbidRange(0x97, 0x98, DOUBLE); // dcmpl, dcmpg
        forbid(0xAD, LONG);   // lreturn
        forbid(0xAE, FLOAT);  // freturn
        forbid(0xAF, DOUBLE); // dreturn
    }

    /** Subroutines are inlined before the check (§2.3.2.2); goto_w is an ordinary jump (JCVM goto / goto_w). */
    private static void forbidControlAndObjectOpcodes() {
        forbid(0xA8, SUBROUTINE);  // jsr
        forbid(0xA9, SUBROUTINE);  // ret
        forbid(0xC9, SUBROUTINE);  // jsr_w
        forbid(0xBA, DYNAMIC);     // invokedynamic
        forbid(0xC2, THREADS);     // monitorenter
        forbid(0xC3, THREADS);     // monitorexit
        forbid(0xC5, MULTI_ARRAY); // multianewarray
    }

    @SuppressWarnings("MagicNumber")
    private static void initLengths() {
        Arrays.fill(INST_LENGTHS, 1);

        // 2-byte instructions
        for (int op : new int[]{
                0x10, // bipush
                0x12, // ldc
                0x15, 0x16, 0x17, 0x18, 0x19, // iload..aload
                0x36, 0x37, 0x38, 0x39, 0x3A, // istore..astore
                0xA9, // ret
                0xBC  // newarray
        }) {
            INST_LENGTHS[op] = 2;
        }

        // 3-byte instructions
        for (int op : new int[]{
                0x11, 0x13, 0x14, 0x84,  // sipush, ldc_w, ldc2_w, iinc
                0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E,  // ifeq..ifle
                0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4,  // if_icmpeq..if_icmple
                0xA5, 0xA6,  // if_acmpeq, if_acmpne
                0xA7, 0xA8,  // goto, jsr
                0xB2, 0xB3, 0xB4, 0xB5,  // getstatic, putstatic, getfield, putfield
                0xB6, 0xB7, 0xB8,  // invokevirtual, invokespecial, invokestatic
                0xBB, 0xBD,  // new, anewarray
                0xC0, 0xC1,  // checkcast, instanceof
                0xC6, 0xC7   // ifnull, ifnonnull
        }) {
            INST_LENGTHS[op] = 3;
        }

        INST_LENGTHS[0xC5] = 4; // multianewarray
        INST_LENGTHS[0xB9] = 5; // invokeinterface
        INST_LENGTHS[0xBA] = 5; // invokedynamic
        INST_LENGTHS[0xC8] = 5; // goto_w
        INST_LENGTHS[0xC9] = 5; // jsr_w

        // Variable-length: 0 means special handling needed
        INST_LENGTHS[0xAA] = 0; // tableswitch
        INST_LENGTHS[0xAB] = 0; // lookupswitch
        INST_LENGTHS[0xC4] = 0; // wide
    }
}
