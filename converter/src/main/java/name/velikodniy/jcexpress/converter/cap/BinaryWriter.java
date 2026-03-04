package name.velikodniy.jcexpress.converter.cap;

import java.io.ByteArrayOutputStream;

/**
 * Low-level utility for writing binary data in big-endian (network) byte order,
 * matching the JavaCard CAP file format conventions defined in JCVM 3.0.5 spec.
 *
 * <p>Every CAP component generator in this package uses {@code BinaryWriter} to
 * serialize its binary payload. The writer accumulates bytes into an internal
 * {@link ByteArrayOutputStream} and provides a fluent API for the three unsigned
 * integer widths used throughout the CAP format:
 * <ul>
 *   <li>{@link #u1(int)} -- single unsigned byte (0-255)</li>
 *   <li>{@link #u2(int)} -- two-byte unsigned value in big-endian order (0-65535)</li>
 *   <li>{@link #u4(int)} -- four-byte unsigned value in big-endian order</li>
 * </ul>
 *
 * <p>Additional convenience methods handle raw byte arrays and AID (Application
 * Identifier) fields with a u1 length prefix, as required by multiple CAP
 * components (Header, Applet, Import).
 *
 * <p>This class is used exclusively during Stage 6 (CAP file assembly) of the
 * converter pipeline.
 *
 * @see HeaderComponent#wrapComponent(int, byte[])
 */
// Utility for serializing CAP binary data per JCVM 3.0.5 §6.1 unsigned integer conventions:
// u1 = 1 byte, u2 = 2 bytes big-endian, u4 = 4 bytes big-endian.
public final class BinaryWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * Writes a single unsigned byte (u1).
     *
     * @param value the value to write; only the low 8 bits are used
     * @return this writer, for chaining
     */
    public BinaryWriter u1(int value) {
        out.write(value & 0xFF);
        return this;
    }

    /**
     * Writes a 2-byte unsigned value (u2) in big-endian order.
     *
     * @param value the value to write; only the low 16 bits are used
     * @return this writer, for chaining
     */
    public BinaryWriter u2(int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
        return this;
    }

    /**
     * Writes a 4-byte unsigned value (u4) in big-endian order.
     *
     * @param value the value to write; all 32 bits are used
     * @return this writer, for chaining
     */
    public BinaryWriter u4(int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
        return this;
    }

    /**
     * Writes raw bytes verbatim to the output.
     *
     * @param data the byte array to write
     * @return this writer, for chaining
     */
    public BinaryWriter bytes(byte[] data) {
        out.writeBytes(data);
        return this;
    }

    /**
     * Writes a sub-range of raw bytes to the output.
     *
     * @param data   the source byte array
     * @param offset the start offset within {@code data}
     * @param length the number of bytes to write
     * @return this writer, for chaining
     */
    public BinaryWriter bytes(byte[] data, int offset, int length) {
        out.write(data, offset, length);
        return this;
    }

    /**
     * Writes an AID (Application Identifier) prefixed by its length as a u1.
     * This pattern is used in the Header, Applet, and Import components where
     * AIDs are serialized as {@code u1 AID_length} followed by {@code u1[] AID}.
     *
     * @param aid the AID bytes (5-16 bytes per ISO 7816-5)
     * @return this writer, for chaining
     */
    public BinaryWriter aidWithLength(byte[] aid) {
        u1(aid.length);
        bytes(aid);
        return this;
    }

    /**
     * Returns the current size of written data.
     */
    public int size() {
        return out.size();
    }

    /**
     * Returns all written data as a byte array.
     */
    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
