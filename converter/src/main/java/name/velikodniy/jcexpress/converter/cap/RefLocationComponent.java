package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.resolve.CpReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates the CAP RefLocation component (tag 9) as defined in JCVM 3.0.5 spec section 6.11.
 *
 * <p>The RefLocation component enables the JCVM's on-card linker to locate and resolve
 * all constant pool references embedded in the Method component's bytecode. Without
 * this component, the JCVM would need to decode every instruction to find CP index
 * operands; instead, it uses the pre-computed offset table from this component.
 *
 * <p>During on-card linking, the JCVM walks through the offset list, locates each
 * CP reference in the bytecode, and patches it with the resolved runtime value
 * (e.g., replacing a CP index with an actual memory address or method table offset).
 *
 * <p>References are separated into two categories:
 * <ul>
 *   <li><b>1-byte index references</b> ({@code byte_index}): Used only for
 *       {@code getfield_a_this} and similar short-form instance field access instructions
 *       that encode the CP index in a single byte</li>
 *   <li><b>2-byte index references</b> ({@code byte2_index}): Used for all other
 *       CP-referencing instructions ({@code invokevirtual}, {@code invokestatic},
 *       {@code getstatic}, {@code new}, etc.) that encode the CP index in two bytes</li>
 * </ul>
 *
 * <p>Within each category, reference positions are delta-encoded: each value represents
 * the distance (in bytes) from the previous reference position. When a gap exceeds 254
 * bytes, it is encoded as one or more {@code 0xFF} (255) bytes followed by the
 * remainder, allowing arbitrarily large gaps without wasting space.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.11, Table 6-11):
 * <pre>
 * u1  tag = 9
 * u2  size
 * u2  byte_index_count                 (length of 1-byte delta array)
 * u1  offsets_to_byte_indices[]        (delta-encoded positions)
 * u2  byte2_index_count                (length of 2-byte delta array)
 * u1  offsets_to_byte2_indices[]       (delta-encoded positions)
 * </pre>
 *
 * @see MethodComponent
 * @see ConstantPoolComponent
 * @see name.velikodniy.jcexpress.converter.resolve.CpReference
 */
public final class RefLocationComponent {

    public static final int TAG = 9;

    private RefLocationComponent() {}

    /**
     * Generates the RefLocation component bytes.
     *
     * @param references all CP references from all translated methods
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(List<CpReference> references) {
        // §6.11: Split into 1-byte (byte_index) and 2-byte (byte2_index) references
        List<CpReference> byteRefs = new ArrayList<>();
        List<CpReference> shortRefs = new ArrayList<>();

        for (CpReference ref : references) {
            if (ref.indexSize() == 1) {
                byteRefs.add(ref);
            } else {
                shortRefs.add(ref);
            }
        }

        // §6.11: offsets within each category are delta-encoded in ascending order
        byteRefs.sort(Comparator.comparingInt(CpReference::bytecodeOffset));
        shortRefs.sort(Comparator.comparingInt(CpReference::bytecodeOffset));

        byte[] byteDeltas = deltaEncode(byteRefs);
        byte[] shortDeltas = deltaEncode(shortRefs);

        // --- reference_location_component (§6.11 Table 6-11) ---
        var info = new BinaryWriter();
        info.u2(byteDeltas.length);  // §6.11: u2 byte_index_count
        info.bytes(byteDeltas);      // §6.11: u1[] offsets_to_byte_indices (delta-encoded)
        info.u2(shortDeltas.length); // §6.11: u2 byte2_index_count
        info.bytes(shortDeltas);     // §6.11: u1[] offsets_to_byte2_indices (delta-encoded)

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }

    /**
     * Delta-encodes a sorted list of CP reference positions into a compact byte array.
     *
     * <p>Each entry is the distance from the previous reference (or from offset 0 for
     * the first entry). Gaps of 255 or more bytes are encoded as one or more
     * {@code 0xFF} escape bytes followed by the remainder (0-254), as specified
     * in JCVM 3.0.5 spec section 6.11.
     *
     * @param refs CP references sorted by ascending bytecodeOffset
     * @return delta-encoded byte array
     */
    static byte[] deltaEncode(List<CpReference> refs) {
        // §6.11: delta encoding — each value is offset from previous reference position.
        // Gaps >= 255 are encoded as 0xFF escape bytes followed by remainder (0-254).
        var out = new BinaryWriter();
        int prevOffset = 0;

        for (CpReference ref : refs) {
            int delta = ref.bytecodeOffset() - prevOffset;
            while (delta >= 255) {
                out.u1(255); // §6.11: 0xFF escape byte for large gaps
                delta -= 255;
            }
            out.u1(delta); // §6.11: u1 delta (0-254)
            prevOffset = ref.bytecodeOffset();
        }

        return out.toByteArray();
    }
}
