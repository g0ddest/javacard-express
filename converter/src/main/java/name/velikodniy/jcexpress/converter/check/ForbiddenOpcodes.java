package name.velikodniy.jcexpress.converter.check;

import java.util.Arrays;

/**
 * Lookup table of JVM bytecode opcodes that are forbidden on the Java Card platform.
 *
 * <p>This class is used by {@link SubsetChecker} during <strong>Stage 2: Subset
 * Check</strong> of the converter pipeline to identify illegal bytecode instructions
 * in method bodies. It also provides instruction length information needed to walk
 * the bytecode stream opcode-by-opcode.
 *
 * <h2>Forbidden opcode categories</h2>
 * <p>The JCVM specification (3.0.5, Section 3.2 and Section 7) defines the following
 * categories of unsupported JVM bytecodes:
 * <ul>
 *   <li><strong>{@code long} operations</strong> -- all load, store, arithmetic,
 *       comparison, conversion, return, and array opcodes for {@code long}
 *       (e.g., {@code lconst_0}, {@code ladd}, {@code lcmp}, {@code lreturn}).</li>
 *   <li><strong>{@code float} operations</strong> -- all load, store, arithmetic,
 *       comparison, conversion, return, and array opcodes for {@code float}
 *       (e.g., {@code fconst_0}, {@code fadd}, {@code fcmpl}, {@code freturn}).</li>
 *   <li><strong>{@code double} operations</strong> -- all load, store, arithmetic,
 *       comparison, conversion, return, and array opcodes for {@code double}
 *       (e.g., {@code dconst_0}, {@code dadd}, {@code dcmpl}, {@code dreturn}).</li>
 *   <li><strong>Threading</strong> -- {@code monitorenter} (0xC2) and
 *       {@code monitorexit} (0xC3), since Java Card has no multi-threading.</li>
 *   <li><strong>Subroutines</strong> -- {@code jsr} (0xA8) and {@code ret} (0xA9),
 *       deprecated in Java SE and absent from the JCVM instruction set.</li>
 *   <li><strong>{@code invokedynamic}</strong> (0xBA) -- not supported on the
 *       Java Card platform.</li>
 *   <li><strong>Wide jumps</strong> -- {@code goto_w} (0xC8) and {@code jsr_w}
 *       (0xC9), since JCVM methods are limited in size and these are not part of
 *       the JCVM instruction set.</li>
 *   <li><strong>{@code ldc2_w}</strong> (0x14) -- loads {@code long} or
 *       {@code double} constants from the constant pool, which are unsupported
 *       types.</li>
 * </ul>
 *
 * <h2>Instruction length table</h2>
 * <p>In addition to the forbidden-opcode lookup, this class maintains a table of
 * instruction lengths for the full JVM instruction set. This is necessary for the
 * bytecode scanner in {@link SubsetChecker} to correctly advance past
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

    private static void initForbidden() {
        String longReason = "long type not supported in JavaCard";
        String floatReason = "float type not supported in JavaCard";
        String doubleReason = "double type not supported in JavaCard";
        String threadReason = "threading not supported in JavaCard";
        String dynamicReason = "invokedynamic not supported in JavaCard";
        String subroutineReason = "subroutines (jsr/ret) not supported in JavaCard";
        String wideJumpReason = "wide jumps not supported in JavaCard";

        // long constants
        forbid(0x09, longReason); // lconst_0
        forbid(0x0A, longReason); // lconst_1

        // float constants
        forbidRange(0x0B, 0x0D, floatReason); // fconst_0..2

        // double constants
        forbid(0x0E, doubleReason); // dconst_0
        forbid(0x0F, doubleReason); // dconst_1

        // ldc2_w (loads long/double from constant pool)
        forbid(0x14, "ldc2_w: long/double constants not supported in JavaCard");

        // load instructions
        forbid(0x16, longReason);   // lload
        forbid(0x17, floatReason);  // fload
        forbid(0x18, doubleReason); // dload
        forbidRange(0x1E, 0x21, longReason);   // lload_0..3
        forbidRange(0x22, 0x25, floatReason);  // fload_0..3
        forbidRange(0x26, 0x29, doubleReason); // dload_0..3

        // array load
        forbid(0x2F, longReason);   // laload
        forbid(0x30, floatReason);  // faload
        forbid(0x31, doubleReason); // daload

        // store instructions
        forbid(0x37, longReason);   // lstore
        forbid(0x38, floatReason);  // fstore
        forbid(0x39, doubleReason); // dstore
        forbidRange(0x3F, 0x42, longReason);   // lstore_0..3
        forbidRange(0x43, 0x46, floatReason);  // fstore_0..3
        forbidRange(0x47, 0x4A, doubleReason); // dstore_0..3

        // array store
        forbid(0x50, longReason);   // lastore
        forbid(0x51, floatReason);  // fastore
        forbid(0x52, doubleReason); // dastore

        // arithmetic — long
        forbid(0x61, longReason); // ladd
        forbid(0x65, longReason); // lsub
        forbid(0x69, longReason); // lmul
        forbid(0x6D, longReason); // ldiv
        forbid(0x71, longReason); // lrem
        forbid(0x75, longReason); // lneg
        forbid(0x79, longReason); // lshl
        forbid(0x7B, longReason); // lshr
        forbid(0x7D, longReason); // lushr
        forbid(0x7F, longReason); // land
        forbid(0x81, longReason); // lor
        forbid(0x83, longReason); // lxor

        // arithmetic — float
        forbid(0x62, floatReason); // fadd
        forbid(0x66, floatReason); // fsub
        forbid(0x6A, floatReason); // fmul
        forbid(0x6E, floatReason); // fdiv
        forbid(0x72, floatReason); // frem
        forbid(0x76, floatReason); // fneg

        // arithmetic — double
        forbid(0x63, doubleReason); // dadd
        forbid(0x67, doubleReason); // dsub
        forbid(0x6B, doubleReason); // dmul
        forbid(0x6F, doubleReason); // ddiv
        forbid(0x73, doubleReason); // drem
        forbid(0x77, doubleReason); // dneg

        // conversions
        forbid(0x85, longReason);   // i2l
        forbid(0x86, floatReason);  // i2f
        forbid(0x87, doubleReason); // i2d
        forbid(0x88, longReason);   // l2i
        forbid(0x89, longReason);   // l2f
        forbid(0x8A, longReason);   // l2d
        forbid(0x8B, floatReason);  // f2i
        forbid(0x8C, floatReason);  // f2l
        forbid(0x8D, floatReason);  // f2d
        forbid(0x8E, doubleReason); // d2i
        forbid(0x8F, doubleReason); // d2l
        forbid(0x90, doubleReason); // d2f

        // comparisons
        forbid(0x94, longReason);   // lcmp
        forbid(0x95, floatReason);  // fcmpl
        forbid(0x96, floatReason);  // fcmpg
        forbid(0x97, doubleReason); // dcmpl
        forbid(0x98, doubleReason); // dcmpg

        // returns
        forbid(0xAD, longReason);   // lreturn
        forbid(0xAE, floatReason);  // freturn
        forbid(0xAF, doubleReason); // dreturn

        // subroutines (deprecated in Java, absent from JCVM)
        forbid(0xA8, subroutineReason); // jsr
        forbid(0xA9, subroutineReason); // ret

        // invokedynamic
        forbid(0xBA, dynamicReason);

        // monitors (no threading in JavaCard)
        forbid(0xC2, threadReason); // monitorenter
        forbid(0xC3, threadReason); // monitorexit

        // wide jumps
        forbid(0xC8, wideJumpReason); // goto_w
        forbid(0xC9, wideJumpReason); // jsr_w
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
