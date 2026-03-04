package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates the CAP Method component (tag 7) as defined in JCVM 3.0.5 spec section 6.9.
 *
 * <p>The Method component contains all executable code for the package. It consists of
 * two sections: a global exception handler table at the beginning, followed by all
 * methods laid out sequentially. The byte offset of each method within this component
 * is used by the Applet component (for {@code install()} entry points), the Class
 * component (for virtual method dispatch tables), and the Descriptor component
 * (for off-card verification).
 *
 * <p><b>Exception handler table:</b> All exception handlers from all methods are
 * collected into a single flat table at the start of the component. Each handler
 * specifies absolute offsets (within the component's info area) for the protected
 * range and handler entry point. The {@code stop_bit} in the bitfield marks the
 * last handler belonging to a particular method, allowing the JCVM to partition
 * handlers by method without additional metadata.
 *
 * <p><b>Method headers:</b> Each method begins with either a standard 2-byte header
 * (when max_stack, nargs, and max_locals all fit in 4 bits) or an extended 4-byte
 * header (when any value exceeds 15). Abstract methods use the extended format with
 * both {@code ACC_EXTENDED} and {@code ACC_ABSTRACT} flags set and all counters at 0.
 *
 * <p>The generation process is two-pass: first, method sizes and handler counts are
 * pre-computed to determine absolute offsets; then the actual bytes are written.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.9, Tables 6-7 through 6-9):
 * <pre>
 * u1  tag = 7
 * u2  size
 * u1  handler_count                  (total exception handlers across all methods)
 * exception_handler_info[handler_count]:
 *   u2  start_offset                 (absolute offset of try-block start)
 *   u2  bitfield                     (stop_bit(1) | active_length(15))
 *   u2  handler_offset               (absolute offset of handler entry)
 *   u2  catch_type_index             (CP index of caught class, 0 = finally/any)
 * method_info[]:
 *   method_header:
 *     Standard (2 bytes): flags|max_stack(4 bits) + nargs|max_locals(4 bits)
 *     Extended (4 bytes): flags(8) + max_stack(8) + nargs(8) + max_locals(8)
 *   u1  bytecodes[]                  (JCVM bytecode)
 * </pre>
 *
 * @see AppletComponent
 * @see ClassComponent
 * @see RefLocationComponent
 * @see name.velikodniy.jcexpress.converter.translate.TranslatedMethod
 */
public final class MethodComponent {

    public static final int TAG = 7;

    // Method header flags
    public static final int ACC_EXTENDED = 0x80;
    public static final int ACC_ABSTRACT = 0x40;

    private MethodComponent() {}

    /**
     * Generates the Method component bytes.
     * Also returns an array of method offsets within the component
     * (used by AppletComponent and ClassComponent for install/virtual method tables).
     *
     * @param methods translated methods in declaration order
     * @return result containing component bytes and method offsets
     */
    @SuppressWarnings("java:S3776") // Inherently complex method_component binary generation
    public static MethodResult generate(List<TranslatedMethod> methods) {
        // First, collect all exception handlers from all methods, adjusting offsets
        // to be absolute within the Method component's info area.
        // Also track per-method handler boundaries for stop_bit.
        var allHandlers = new ArrayList<AdjustedHandler>();

        // We need to pre-compute method offsets to adjust handler positions.
        // Exception handlers come first in the component, then methods.
        // So we do a 2-pass: first calculate total handler table size,
        // then calculate method offsets.

        int totalHandlerCount = 0;
        for (TranslatedMethod m : methods) {
            totalHandlerCount += m.exceptionHandlers().size();
        }

        // §6.9 Table 6-7: handler_count(u1) + exception_handler_info[count] * 8 bytes each
        int handlerTableSize = 1 + totalHandlerCount * 8;

        // Pre-calculate method byte sizes to know offsets
        int[] methodSizes = new int[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            TranslatedMethod m = methods.get(i);
            if (m == TranslatedMethod.EMPTY || m.bytecode().length == 0) {
                methodSizes[i] = 2; // abstract header
            } else if (m.isExtended()) {
                methodSizes[i] = 4 + m.bytecode().length; // extended header + bytecode
            } else {
                methodSizes[i] = 2 + m.bytecode().length; // standard header + bytecode
            }
        }

        // Calculate absolute method offsets (from start of info)
        int[] methodOffsets = new int[methods.size()];
        int currentOffset = handlerTableSize;
        for (int i = 0; i < methods.size(); i++) {
            methodOffsets[i] = currentOffset;
            currentOffset += methodSizes[i];
        }

        // Collect all handlers with adjusted offsets
        for (int i = 0; i < methods.size(); i++) {
            TranslatedMethod m = methods.get(i);
            // Method bytecode starts after its header
            int headerSize = (m == TranslatedMethod.EMPTY || m.bytecode().length == 0)
                    ? 2 : (m.isExtended() ? 4 : 2);
            int bytecodeBase = methodOffsets[i] + headerSize;

            var methodHandlers = m.exceptionHandlers();
            for (int h = 0; h < methodHandlers.size(); h++) {
                var handler = methodHandlers.get(h);
                boolean isLastForMethod = (h == methodHandlers.size() - 1);
                allHandlers.add(new AdjustedHandler(
                        bytecodeBase + handler.startOffset(),
                        handler.endOffset() - handler.startOffset(),
                        bytecodeBase + handler.handlerOffset(),
                        handler.catchTypeIndex(),
                        isLastForMethod
                ));
            }
        }

        // --- method_component (§6.9 Table 6-7) ---
        var info = new BinaryWriter();
        info.u1(totalHandlerCount); // §6.9 Table 6-7: u1 handler_count

        // --- exception_handler_info (§6.9 Table 6-8) ---
        for (var handler : allHandlers) {
            info.u2(handler.startOffset);   // §6.9 Table 6-8: u2 start_offset
            int bitfield = handler.activeLength & 0x7FFF;
            if (handler.isLast) {
                bitfield |= 0x8000; // §6.9 Table 6-8: bit 15 = stop_bit (last handler per method)
            }
            info.u2(bitfield);              // §6.9 Table 6-8: u2 bitfield (stop_bit|active_length)
            info.u2(handler.handlerOffset); // §6.9 Table 6-8: u2 handler_offset
            info.u2(handler.catchTypeIndex); // §6.9 Table 6-8: u2 catch_type_index (0 = any)
        }

        // --- method_info[] (§6.9 Table 6-9) ---
        int[] offsets = new int[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            offsets[i] = info.size();
            TranslatedMethod m = methods.get(i);

            if (m == TranslatedMethod.EMPTY || m.bytecode().length == 0) {
                // §6.9 Table 6-9: standard_method_header with ACC_ABSTRACT (2 bytes)
                // Oracle uses standard 2-byte format for abstract methods.
                info.u1(ACC_ABSTRACT | (0 & 0x0F)); // §6.9: flags(4)|max_stack(4) — ACC_ABSTRACT=0x40
                info.u1(((m.nargs() & 0x0F) << 4) | (0 & 0x0F)); // §6.9: nargs(4)|max_locals(4)
                continue;
            }

            if (m.isExtended()) {
                // §6.9 Table 6-9: extended_method_header (4 bytes, used when values > 15)
                info.u1(ACC_EXTENDED);   // §6.9: u1 flags (bit 7 = ACC_EXTENDED)
                info.u1(m.maxStack());   // §6.9: u1 max_stack
                info.u1(m.nargs());      // §6.9: u1 nargs
                info.u1(m.maxLocals());  // §6.9: u1 max_locals
            } else {
                // §6.9 Table 6-9: method_header_info (standard 2-byte compact form)
                // byte 1: flags(4bit) | max_stack(4bit)
                info.u1((0x00 << 4) | (m.maxStack() & 0x0F)); // §6.9: u1 flags|max_stack
                // byte 2: nargs(4bit) | max_locals(4bit)
                info.u1(((m.nargs() & 0x0F) << 4) | (m.maxLocals() & 0x0F)); // §6.9: u1 nargs|max_locals
            }

            info.bytes(m.bytecode()); // §6.9: u1[] bytecodes
        }

        return new MethodResult(
                HeaderComponent.wrapComponent(TAG, info.toByteArray()),
                offsets
        );
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

    private record AdjustedHandler(int startOffset, int activeLength,
                                   int handlerOffset, int catchTypeIndex,
                                   boolean isLast) {}
}
