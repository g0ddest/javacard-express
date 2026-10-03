package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.CpReference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable result of translating a single JVM method body into JCVM bytecode.
 *
 * <p>This record is produced by {@link BytecodeTranslator} during
 * <b>Stage 5: Bytecode Translation</b> of the converter pipeline and consumed by
 * {@link name.velikodniy.jcexpress.converter.cap.MethodComponent} to assemble the
 * {@code method_component} of a CAP file (JCVM 3.1 spec, section 6.10).
 *
 * <p>Each {@code TranslatedMethod} carries all the information needed to emit a complete
 * method_info structure: the translated bytecode, operand stack and local variable limits
 * (used to build the method header), exception handler entries, and the byte positions
 * of embedded constant pool references (used by
 * {@link name.velikodniy.jcexpress.converter.cap.RefLocationComponent}). Methods produced by the
 * translator also keep their symbolic {@link JcvmCode}, so that they can be re-encoded after the
 * constant pool has been reordered ({@link #withConstantPoolRemap(int[])}).
 *
 * <p>The special sentinel {@link #EMPTY} is returned for abstract or native methods that
 * have no code attribute.
 *
 * @param bytecode          the translated JCVM bytecode array
 * @param maxStack          maximum operand stack depth in 16-bit words
 *                          (JCVM 3.1 §6.10.4, method_header_info.max_stack)
 * @param maxLocals         local variable words, excluding method parameters
 *                          (JCVM 3.1 §6.10.4, method_header_info.max_locals)
 * @param nargs             parameter words including {@code this} for instance methods
 *                          (JCVM 3.1 §6.10.4, method_header_info.nargs)
 * @param exceptionHandlers translated exception handler table entries in Method component order
 *                          (JCVM 3.1 §6.10.3, exception_handler_info)
 * @param isExtended        {@code true} if an extended method header is required because
 *                          {@code maxStack}, {@code maxLocals}, or {@code nargs} exceeds 15
 *                          (JCVM 3.1 §6.10.4, extended_method_header_info)
 * @param cpReferences      byte positions and sizes of constant pool references within the
 *                          bytecode, used to populate the RefLocation component
 * @param code              symbolic code this method was assembled from, or {@code null} for
 *                          methods built directly from bytes; not part of equality
 *
 * @see BytecodeTranslator
 * @see name.velikodniy.jcexpress.converter.cap.MethodComponent
 * @see name.velikodniy.jcexpress.converter.cap.RefLocationComponent
 */
public record TranslatedMethod(
        byte[] bytecode,
        int maxStack,
        int maxLocals,
        int nargs,
        List<JcvmExceptionHandler> exceptionHandlers,
        boolean isExtended,
        List<CpReference> cpReferences,
        JcvmCode code
) {
    private static final int MAX_COMPACT_HEADER_VALUE = 0x0F;

    /**
     * Normalizes the method: exception handlers are put in the order required by JCVM 3.1
     * §6.10.1 (ascending handler offset) and {@code isExtended} is forced to {@code true}
     * whenever max_stack, nargs or max_locals does not fit in the 4-bit fields of
     * {@code method_header_info} (§6.10.4).
     *
     * @throws IllegalStateException if the handlers cannot be ordered by handler offset without
     *                               changing which handler catches an exception
     */
    public TranslatedMethod {
        exceptionHandlers = JcvmExceptionHandler.inMethodComponentOrder(exceptionHandlers);
        cpReferences = List.copyOf(cpReferences);
        isExtended = isExtended || maxStack > MAX_COMPACT_HEADER_VALUE
                || nargs > MAX_COMPACT_HEADER_VALUE || maxLocals > MAX_COMPACT_HEADER_VALUE;
    }

    /**
     * Creates a method from already encoded bytecode (no symbolic code).
     *
     * @param bytecode          the JCVM bytecode
     * @param maxStack          max_stack in words
     * @param maxLocals         max_locals in words
     * @param nargs             nargs in words
     * @param exceptionHandlers exception handlers
     * @param isExtended        whether the extended header is requested
     * @param cpReferences      constant pool index positions in {@code bytecode}
     */
    public TranslatedMethod(byte[] bytecode, int maxStack, int maxLocals, int nargs,
                            List<JcvmExceptionHandler> exceptionHandlers, boolean isExtended,
                            List<CpReference> cpReferences) {
        this(bytecode, maxStack, maxLocals, nargs, exceptionHandlers, isExtended, cpReferences, null);
    }

    /**
     * Sentinel instance representing a method with no bytecode body.
     * Returned by {@link BytecodeTranslator} for abstract or native methods
     * that lack a {@code Code} attribute.
     */
    public static final TranslatedMethod EMPTY =
            new TranslatedMethod(new byte[0], 0, 0, 0, List.of(), false, List.of());

    /**
     * Returns this method re-encoded for a renumbered constant pool.
     *
     * <p>Methods with symbolic code are re-assembled, so instruction widths (1-byte versus
     * {@code _w} field indices, JCVM 3.1 §7.5.22), branch offsets and handler ranges follow the
     * new indices; exception handler catch types (§6.10.3) are renumbered too. Methods without
     * symbolic code only get their recorded index operands rewritten in place of a copy.
     *
     * @param cpRemap {@code cpRemap[old] = new}; an empty array means no change
     * @return the re-encoded method
     * @throws IllegalStateException if a 1-byte index of a method without symbolic code would
     *                               exceed 255
     */
    public TranslatedMethod withConstantPoolRemap(int[] cpRemap) {
        if (cpRemap.length == 0) {
            return this;
        }
        if (code != null) {
            return code.assemble(cpRemap);
        }
        return rewrittenInPlace(cpRemap);
    }

    private TranslatedMethod rewrittenInPlace(int[] cpRemap) {
        byte[] bytes = bytecode.clone();
        List<CpReference> refs = cpReferences.stream().map(ref -> {
            int index = cpRemap[ref.cpIndex()];
            if (ref.indexSize() == 1 && index > 0xFF) {
                throw new IllegalStateException("constant pool index " + index
                        + " does not fit the 1-byte operand at offset " + ref.bytecodeOffset());
            }
            if (ref.indexSize() == 1) {
                bytes[ref.bytecodeOffset()] = (byte) index;
            } else {
                bytes[ref.bytecodeOffset()] = (byte) (index >> 8);
                bytes[ref.bytecodeOffset() + 1] = (byte) index;
            }
            return new CpReference(ref.bytecodeOffset(), index, ref.indexSize());
        }).toList();
        List<JcvmExceptionHandler> handlers = exceptionHandlers.stream()
                .map(h -> h.catchesAll() ? h : new JcvmExceptionHandler(h.startOffset(),
                        h.endOffset(), h.handlerOffset(), cpRemap[h.catchTypeIndex()], false))
                .toList();
        return new TranslatedMethod(bytes, maxStack, maxLocals, nargs, handlers, isExtended, refs);
    }

    /**
     * Returns the constant pool indices that the given methods use as exception catch types
     * (JCVM 3.1 §6.10.3), in the numbering the methods currently have.
     *
     * @param methods translated methods
     * @return sorted set of constant pool indices
     */
    public static Set<Integer> catchTypeIndices(List<TranslatedMethod> methods) {
        Set<Integer> types = new TreeSet<>();
        for (TranslatedMethod m : methods) {
            for (JcvmExceptionHandler h : m.exceptionHandlers()) {
                if (!h.catchesAll()) {
                    types.add(h.catchTypeIndex());
                }
            }
        }
        return types;
    }

    /**
     * Re-encodes every method of the list for a renumbered constant pool (in place).
     *
     * @param methods mutable list of translated methods
     * @param cpRemap {@code cpRemap[old] = new}; an empty array means no change
     * @see #withConstantPoolRemap(int[])
     */
    public static void remapAll(List<TranslatedMethod> methods, int[] cpRemap) {
        methods.replaceAll(m -> m.withConstantPoolRemap(cpRemap));
    }

    /**
     * Returns whether this method contains an instruction of type int or int array, one of the
     * uses of the int type that set ACC_INT (JCVM 3.1 §6.4). Methods without symbolic code report
     * {@code false}.
     *
     * @return {@code true} if the method has int instructions
     */
    public boolean usesInt() {
        return code != null && code.usesInt();
    }

    /**
     * Returns the source method this code was translated from, for diagnostics.
     *
     * @return class, method name and descriptor (and source file), or {@code null} for methods
     *         without symbolic code
     */
    public String origin() {
        return code == null ? null : code.origin();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof TranslatedMethod(var bc, var ms, var ml, var na, var eh, var ie, var cr, var ignored)) {
            return maxStack == ms
                    && maxLocals == ml
                    && nargs == na
                    && isExtended == ie
                    && Arrays.equals(bytecode, bc)
                    && Objects.equals(exceptionHandlers, eh)
                    && Objects.equals(cpReferences, cr);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Objects.hash(maxStack, maxLocals, nargs, isExtended, exceptionHandlers, cpReferences)
                + Arrays.hashCode(bytecode));
    }

    @Override
    public String toString() {
        return "TranslatedMethod[bytecode=" + HexFormat.of().formatHex(bytecode)
                + ", maxStack=" + maxStack
                + ", maxLocals=" + maxLocals
                + ", nargs=" + nargs
                + ", isExtended=" + isExtended
                + ", handlers=" + exceptionHandlers.size()
                + ", cpRefs=" + cpReferences.size() + "]";
    }

    /**
     * A single entry in the JCVM exception handler table for this method.
     *
     * <p>Corresponds to the {@code exception_handler_info} structure of JCVM 3.1 §6.10.3.
     * Offsets are byte positions within the translated JCVM bytecode array, not the original
     * JVM bytecode.
     *
     * @param startOffset    byte offset of the start of the try block (inclusive)
     * @param endOffset      byte offset of the end of the try block (exclusive)
     * @param handlerOffset  byte offset of the exception handler entry point
     * @param catchTypeIndex constant pool index of the caught exception class (0 for a
     *                       {@code finally} handler)
     * @param catchesAll     {@code true} for a {@code finally} (catch-all) handler; kept apart
     *                       from {@code catchTypeIndex} because index 0 is a valid constant pool
     *                       index until the converter has ordered the constant pool
     */
    public record JcvmExceptionHandler(
            int startOffset,
            int endOffset,
            int handlerOffset,
            int catchTypeIndex,
            boolean catchesAll
    ) {
        /**
         * Creates a handler; a {@code catchTypeIndex} of 0 denotes a {@code finally} handler.
         *
         * @param startOffset    start of the active range
         * @param endOffset      end of the active range (exclusive)
         * @param handlerOffset  handler entry point
         * @param catchTypeIndex constant pool index of the caught class, or 0 for finally
         */
        public JcvmExceptionHandler(int startOffset, int endOffset, int handlerOffset,
                                    int catchTypeIndex) {
            this(startOffset, endOffset, handlerOffset, catchTypeIndex, catchTypeIndex == 0);
        }

        /**
         * Creates a {@code finally} (catch-all) handler, catch_type_index 0 (JCVM 3.1 §6.10.3).
         *
         * @param startOffset   start of the active range
         * @param endOffset     end of the active range (exclusive)
         * @param handlerOffset handler entry point
         * @return the handler
         */
        public static JcvmExceptionHandler catchAll(int startOffset, int endOffset, int handlerOffset) {
            return new JcvmExceptionHandler(startOffset, endOffset, handlerOffset, 0, true);
        }

        /**
         * Returns the handlers of one method sorted by handler offset (stable), as required by
         * JCVM 3.1 §6.10.1: "Entries in the exception_handlers array are sorted in ascending
         * order by the offset to the handler of the exception handler." The JVM selects the first
         * matching entry of its exception table, so two handlers whose active ranges intersect
         * must keep their relative JVM order; if the sort would swap them the method is rejected.
         *
         * @param handlers handlers in JVM exception table order
         * @return immutable list in Method component order
         * @throws IllegalStateException if the required order would change handler selection
         */
        public static List<JcvmExceptionHandler> inMethodComponentOrder(
                List<JcvmExceptionHandler> handlers) {
            for (int i = 0; i < handlers.size(); i++) {
                for (int j = i + 1; j < handlers.size(); j++) {
                    JcvmExceptionHandler a = handlers.get(i);
                    JcvmExceptionHandler b = handlers.get(j);
                    if (a.intersects(b) && a.handlerOffset() > b.handlerOffset()) {
                        throw new IllegalStateException("Exception table cannot be ordered by"
                                + " handler offset as JCVM 3.1 §6.10.1 requires without changing"
                                + " which handler catches an exception (handlers at "
                                + a.handlerOffset() + " and " + b.handlerOffset()
                                + " protect overlapping ranges)");
                    }
                }
            }
            return handlers.stream()
                    .sorted(Comparator.comparingInt(JcvmExceptionHandler::handlerOffset))
                    .toList();
        }

        /**
         * Returns whether the active ranges [start, end) of both handlers overlap.
         *
         * @param other another handler of the same method
         * @return {@code true} if the ranges intersect
         */
        public boolean intersects(JcvmExceptionHandler other) {
            return startOffset < other.endOffset && other.startOffset < endOffset;
        }
    }
}
