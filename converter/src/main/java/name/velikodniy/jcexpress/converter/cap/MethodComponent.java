package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod.JcvmExceptionHandler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates the CAP Method component (tag 7) in the compact format of JCVM 3.1 spec section 6.10.
 *
 * <p>The Method component contains all executable code for the package: a global exception
 * handler table followed by all {@code method_info} structures laid out sequentially. The byte
 * offset of each method within this component is used by the Applet, Class, Constant Pool and
 * Descriptor components.
 *
 * <p><b>Exception handler table (6.10.1, 6.10.3):</b> the handlers of all methods are collected
 * into one table with absolute offsets, method by method. Each method's handlers are already in
 * ascending {@code handler_offset} order (enforced by {@link TranslatedMethod}). The
 * {@code stop_bit} is 1 exactly when no succeeding handler's active range intersects this
 * handler's active range (handlers of other methods never intersect).
 *
 * <p><b>Method headers (6.10.4):</b> a 2-byte {@code method_header_info} when max_stack, nargs
 * and max_locals all fit in 4 bits, otherwise a 4-byte {@code extended_method_header_info} with
 * {@code ACC_EXTENDED}. Abstract methods have {@code ACC_ABSTRACT}, an empty bytecode array and,
 * when their arguments need more than 15 words, the extended header.
 *
 * <p><b>Limits:</b> {@code handler_count} is a u1 (at most 255 handlers), header values of the
 * extended header are u1, a method has at most 32767 bytecodes (2.2.4.4), and all offsets are
 * u2, so the component info may not exceed 65535 bytes. Violations raise an
 * {@link IllegalStateException} instead of silently truncated output.
 *
 * <pre>
 * u1  tag = 7
 * u2  size
 * u1  handler_count
 * exception_handler_info[handler_count]:
 *   u2  start_offset, u2 bitfield (stop_bit(1) | active_length(15)),
 *   u2  handler_offset, u2 catch_type_index (0 = finally)
 * method_info[]: method_header + u1 bytecodes[]
 * </pre>
 *
 * @see AppletComponent
 * @see ClassComponent
 * @see RefLocationComponent
 * @see name.velikodniy.jcexpress.converter.translate.TranslatedMethod
 */
public final class MethodComponent {

    public static final int TAG = 7;

    // Method header flags (high nibble of the first header byte)
    public static final int ACC_EXTENDED = 0x80;
    public static final int ACC_ABSTRACT = 0x40;

    /** JCVM 3.1 §2.2.4.4: "A method can have at most 32767 Java Card virtual machine bytecodes." */
    static final int MAX_BYTECODES = 32767;
    private static final int MAX_U1 = 0xFF;
    private static final int MAX_U2 = 0xFFFF;
    private static final int MAX_ACTIVE_LENGTH = 0x7FFF;

    private MethodComponent() {}

    /**
     * Generates the Method component bytes and the offset of every method within the
     * component info (used by the Applet, Class, Constant Pool and Descriptor components).
     *
     * @param methods translated methods in Method component order
     * @return result containing component bytes and method offsets
     * @throws IllegalStateException if a JCVM 3.1 format limit is exceeded
     */
    public static MethodResult generate(List<TranslatedMethod> methods) {
        int handlerCount = methods.stream().mapToInt(m -> m.exceptionHandlers().size()).sum();
        if (handlerCount > MAX_U1) {
            throw new IllegalStateException("Package contains " + handlerCount
                    + " exception handlers; the Method component allows at most 255"
                    + " (JCVM 3.1 §6.10, §6.10.1: u1 handler_count)" + largestHandlerTables(methods));
        }
        int[] offsets = methodOffsets(methods, 1 + handlerCount * 8);

        var info = new BinaryWriter();
        info.u1(handlerCount);
        for (int i = 0; i < methods.size(); i++) {
            writeHandlers(info, methods.get(i).exceptionHandlers(), offsets[i] + headerSize(methods.get(i)));
        }
        for (TranslatedMethod m : methods) {
            writeMethod(info, m);
        }
        if (info.size() > MAX_U2) {
            throw new IllegalStateException("Method component is " + info.size() + " bytes;"
                    + " the compact CAP format allows at most 65535 (JCVM 3.1 §6.10: u2 offsets)");
        }
        return new MethodResult(HeaderComponent.wrapComponent(TAG, info.toByteArray()), offsets);
    }

    /** The methods with the most exception handlers (catch and finally blocks), for the error. */
    private static String largestHandlerTables(List<TranslatedMethod> methods) {
        String top = methods.stream()
                .filter(m -> !m.exceptionHandlers().isEmpty())
                .sorted(Comparator.comparingInt((TranslatedMethod m) -> m.exceptionHandlers().size())
                        .reversed())
                .limit(5)
                .map(m -> Objects.requireNonNullElse(m.origin(), "method") + ": "
                        + m.exceptionHandlers().size())
                .collect(Collectors.joining(", "));
        return top.isEmpty() ? "" : ". Most handlers: " + top;
    }

    private static int[] methodOffsets(List<TranslatedMethod> methods, int firstOffset) {
        int[] offsets = new int[methods.size()];
        int offset = firstOffset;
        for (int i = 0; i < methods.size(); i++) {
            offsets[i] = offset;
            offset += headerSize(methods.get(i)) + methods.get(i).bytecode().length;
        }
        return offsets;
    }

    /** JCVM 3.1 §6.10.3: stop_bit = 1 iff no succeeding handler's active range intersects. */
    private static void writeHandlers(BinaryWriter info, List<JcvmExceptionHandler> handlers,
                                      int bytecodeBase) {
        for (int i = 0; i < handlers.size(); i++) {
            JcvmExceptionHandler h = handlers.get(i);
            int activeLength = h.endOffset() - h.startOffset();
            if (activeLength <= 0 || activeLength > MAX_ACTIVE_LENGTH) {
                throw new IllegalStateException("Invalid exception handler active range length "
                        + activeLength + " (JCVM 3.1 §6.10.3: 1..32767)");
            }
            if (!h.catchesAll() && h.catchTypeIndex() == 0) {
                throw new IllegalStateException("A catch block refers to constant pool index 0;"
                        + " JCVM 3.1 §6.10.3 requires catch types at non-zero indices (0 = finally)");
            }
            boolean stop = handlers.subList(i + 1, handlers.size()).stream()
                    .noneMatch(h::intersects);
            info.u2(bytecodeBase + h.startOffset());             // u2 start_offset
            info.u2((stop ? 0x8000 : 0) | activeLength);          // u2 stop_bit | active_length
            info.u2(bytecodeBase + h.handlerOffset());            // u2 handler_offset
            info.u2(h.catchTypeIndex());                          // u2 catch_type_index (0 = finally)
        }
    }

    private static void writeMethod(BinaryWriter info, TranslatedMethod m) {
        int length = m.bytecode().length;
        if (length > MAX_BYTECODES) {
            throw new IllegalStateException("Method has " + length + " bytes of bytecode;"
                    + " JCVM 3.1 §2.2.4.4 allows at most 32767");
        }
        boolean isAbstract = length == 0;
        int flags = isAbstract ? ACC_ABSTRACT : 0;
        if (m.isExtended()) {
            checkU1("max_stack", m.maxStack());
            checkU1("nargs", m.nargs());
            checkU1("max_locals", m.maxLocals());
            info.u1(ACC_EXTENDED | flags);                        // flags | padding
            info.u1(m.maxStack());
            info.u1(m.nargs());
            info.u1(m.maxLocals());
        } else {
            info.u1(flags | m.maxStack());                        // flags(4) | max_stack(4)
            info.u1((m.nargs() << 4) | m.maxLocals());            // nargs(4) | max_locals(4)
        }
        info.bytes(m.bytecode());
    }

    private static void checkU1(String item, int value) {
        if (value < 0 || value > MAX_U1) {
            throw new IllegalStateException("Method header " + item + " = " + value
                    + " does not fit in a u1 (JCVM 3.1 §6.10.4, §2.2.4.4)");
        }
    }

    private static int headerSize(TranslatedMethod m) {
        return m.isExtended() ? 4 : 2;
    }

    /**
     * Returns the size of a method's header in the Method component (2 for
     * {@code method_header_info}, 4 for {@code extended_method_header_info}), i.e. the distance
     * from the method offset to its first bytecode (JCVM 3.1 §6.10.4).
     *
     * @param m translated method
     * @return header size in bytes
     */
    public static int methodHeaderSize(TranslatedMethod m) {
        return headerSize(m);
    }

    /**
     * Result of Method component generation.
     *
     * @param bytes   complete component bytes
     * @param offsets per-method offsets within the component info (after tag+size)
     */
    public record MethodResult(byte[] bytes, int[] offsets) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof MethodResult(var b, var ofs)) {
                return Arrays.equals(bytes, b) && Arrays.equals(offsets, ofs);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(bytes) + Arrays.hashCode(offsets);
        }

        @Override
        public String toString() {
            return "MethodResult[bytes=" + HexFormat.of().formatHex(bytes)
                    + ", offsets=" + Arrays.toString(offsets) + "]";
        }
    }
}
