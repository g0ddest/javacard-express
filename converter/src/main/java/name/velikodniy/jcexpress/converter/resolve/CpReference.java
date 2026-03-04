package name.velikodniy.jcexpress.converter.resolve;

/**
 * Tracks the location of a constant pool reference within translated JCVM bytecode.
 *
 * <p>This record is produced during <strong>Stage 4: Reference Resolution</strong> of the
 * converter pipeline, as part of bytecode translation. Each time the translator emits a
 * JCVM instruction that references the constant pool (e.g. {@code getfield_a},
 * {@code invokevirtual}, {@code invokestatic}), a {@code CpReference} is recorded to
 * remember where in the bytecode the CP index was written.
 *
 * <p>These records are consumed by the Reference Location Component (JCVM spec 6.9),
 * which encodes the locations as a delta-compressed byte stream. The JCVM runtime uses
 * this table to resolve constant pool references during CAP file linking/installation.
 *
 * <h2>Index Size</h2>
 * <p>Most JCVM instructions use a 2-byte CP index, but some (notably instance field
 * access instructions like {@code getfield_a} and {@code putfield_a}) use a 1-byte index
 * when the CP index fits in a single byte. The {@code indexSize} field distinguishes
 * these cases so the Reference Location Component can encode them correctly.
 *
 * @param bytecodeOffset absolute byte position within the method's JCVM bytecode
 *                       where the CP index operand begins (0-based from method start)
 * @param cpIndex        index into the JCVM constant pool that was written at this location
 * @param indexSize      number of bytes used for the CP index operand: 1 for short-form
 *                       instance field references, 2 for all other references
 *
 * @see ReferenceResolver
 * @see name.velikodniy.jcexpress.converter.cap.RefLocationComponent
 * @see <a href="https://docs.oracle.com/javacard/3.0.5/JCVM/jcvm-spec-3_0_5.pdf">JCVM 3.0.5 spec, section 6.9 (Reference Location Component)</a>
 */
public record CpReference(int bytecodeOffset, int cpIndex, int indexSize) {}
