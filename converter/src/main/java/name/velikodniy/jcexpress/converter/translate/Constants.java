package name.velikodniy.jcexpress.converter.translate;

/**
 * Encodes constant pushes and checks constant ranges (JCVM 3.1 §7.5.15 bspush, §7.5.87
 * sconst_&lt;s&gt;, §7.5.102 sspush, §7.5.14 bipush, §7.5.33 iconst_&lt;i&gt;, §7.5.91 sipush,
 * §7.5.47 iipush). The JCVM has no constant pool entries for numeric values, so every constant is
 * an immediate operand.
 */
final class Constants {

    private Constants() {}

    /**
     * Pushes an int constant as a short (one word) or as an int (two words).
     *
     * @param emit  code emitter
     * @param value constant value
     * @param asInt {@code true} to push a 32-bit int
     * @throws IllegalStateException if a short push is requested for a value outside the short range
     */
    static void push(CodeEmitter emit, int value, boolean asInt) {
        if (asInt) {
            pushInt(emit, value);
        } else {
            pushShort(emit, value);
        }
    }

    private static void pushShort(CodeEmitter emit, int value) {
        if (value >= -1 && value <= 5) {
            emit.op(JcvmOpcode.SCONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            emit.op(JcvmOpcode.BSPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            emit.opU2(JcvmOpcode.SSPUSH, value);
        } else {
            throw new IllegalStateException("int constant " + value + " is outside the short range"
                    + " and needs int support (JCVM 3.1 §2.2.3.1)");
        }
    }

    private static void pushInt(CodeEmitter emit, int value) {
        if (value >= -1 && value <= 5) {
            emit.op(JcvmOpcode.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            emit.op(JcvmOpcode.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            emit.opU2(JcvmOpcode.SIPUSH, value);
        } else {
            emit.op(JcvmOpcode.IIPUSH, value >> 24, value >> 16, value >> 8, value);
        }
    }

    /** Checks that short switch keys (stableswitch / slookupswitch) fit in 16 bits. */
    static void requireShortKeys(int lowest, int highest) {
        if (lowest < Short.MIN_VALUE || highest > Short.MAX_VALUE) {
            throw new IllegalStateException("switch key outside the short range needs int support"
                    + " (JCVM 3.1 §2.2.3.1, §7.5.66 itableswitch)");
        }
    }
}
