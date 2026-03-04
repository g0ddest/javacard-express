package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.CpReference;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of translating a single JVM method body into JCVM bytecode.
 *
 * <p>This record is produced by {@link BytecodeTranslator} during
 * <b>Stage 5: Bytecode Translation</b> of the converter pipeline and consumed by
 * {@link name.velikodniy.jcexpress.converter.cap.MethodComponent} to assemble the
 * {@code method_component} of a CAP file (JCVM 3.0.5 spec, section 6.9).
 *
 * <p>Each {@code TranslatedMethod} carries all the information needed to emit a complete
 * method_info structure: the translated bytecode, operand stack and local variable limits
 * (used to build the method header), exception handler entries, and the byte positions
 * of embedded constant pool references (used by
 * {@link name.velikodniy.jcexpress.converter.cap.RefLocationComponent}).
 *
 * <p>The special sentinel {@link #EMPTY} is returned for abstract or native methods that
 * have no code attribute.
 *
 * @param bytecode          the translated JCVM bytecode array
 * @param maxStack          maximum operand stack depth in 16-bit cells
 *                          (JCVM spec 6.9, method_header_info.max_stack)
 * @param maxLocals         maximum local variable slots in 16-bit cells, excluding method
 *                          parameters (JCVM spec 6.9, method_header_info.max_locals)
 * @param nargs             number of argument slots including {@code this} for instance methods
 *                          (JCVM spec 6.9, method_header_info.nargs)
 * @param exceptionHandlers translated exception handler table entries
 *                          (JCVM spec 6.9, exception_handler_info)
 * @param isExtended        {@code true} if an extended method header is required because
 *                          {@code maxStack}, {@code maxLocals}, or {@code nargs} exceeds 15
 *                          (JCVM spec 6.9, extended_method_header_info)
 * @param cpReferences      byte positions and sizes of constant pool references within the
 *                          bytecode, used to populate the RefLocation component
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
        List<CpReference> cpReferences
) {
    /**
     * Sentinel instance representing a method with no bytecode body.
     * Returned by {@link BytecodeTranslator} for abstract or native methods
     * that lack a {@code Code} attribute.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof TranslatedMethod(var bc, var ms, var ml, var na, var eh, var ie, var cr)) {
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

    public static final TranslatedMethod EMPTY =
            new TranslatedMethod(new byte[0], 0, 0, 0, List.of(), false, List.of());

    /**
     * A single entry in the JCVM exception handler table for this method.
     *
     * <p>Corresponds to the {@code exception_handler_info} structure defined in
     * JCVM 3.0.5 spec, section 6.9. Offsets are byte positions within the
     * translated JCVM bytecode array, not the original JVM bytecode.
     *
     * @param startOffset   byte offset of the start of the try block (inclusive)
     * @param endOffset     byte offset of the end of the try block (exclusive)
     * @param handlerOffset byte offset of the exception handler entry point
     * @param catchTypeIndex constant pool index of the caught exception class,
     *                       or 0 for a catch-all / finally handler
     */
    public record JcvmExceptionHandler(
            int startOffset,
            int endOffset,
            int handlerOffset,
            int catchTypeIndex
    ) {}
}
