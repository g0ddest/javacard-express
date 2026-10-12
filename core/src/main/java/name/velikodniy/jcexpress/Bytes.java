package name.velikodniy.jcexpress;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Command data of the Java Card basic types, written as an applet reads them: a {@code short} as two bytes,
 * big-endian ({@code Util.getShort} of the Java Card API), an {@code int} as four bytes, big-endian
 * ({@code JCint.getInt} of {@code javacardx.framework.util.intx}), a {@code boolean} as one byte.
 *
 * <pre>
 * assertThat(card.send(0x80, 0x30, 0x00, 0x00, Bytes.ofShort(100))).isSuccess();
 * card.send(CREDIT.data(Bytes.ofShort(100)));
 * card.send(SET_LIMITS.data(Bytes.concat(Bytes.ofShort(500), Bytes.ofByte(3))));
 * </pre>
 *
 * <p>The readers of the response go the other way: {@link APDUResponse#dataAsShort()} and the like.</p>
 */
public final class Bytes {

    private Bytes() {
    }

    /**
     * Returns one byte.
     *
     * @param value the byte, signed ({@code -128} to {@code 127}, e.g. a {@code byte} constant) or unsigned
     *              ({@code 0} to {@code 255}, e.g. {@code 0x90})
     * @return the byte
     * @throws IllegalArgumentException if {@code value} is outside {@code -128} to {@code 255}
     */
    public static byte[] ofByte(int value) {
        requireRange("ofByte", value, Byte.MIN_VALUE, 0xFF, "a byte, signed or unsigned");
        return new byte[]{(byte) value};
    }

    /**
     * Returns a {@code short} as two bytes, big-endian, as {@code Util.getShort} reads them.
     *
     * @param value the number, signed ({@code -32768} to {@code 32767}) or unsigned ({@code 0} to {@code 65535},
     *              e.g. {@code 0xFFFF})
     * @return the two bytes, high byte first
     * @throws IllegalArgumentException if {@code value} is outside {@code -32768} to {@code 65535}
     */
    public static byte[] ofShort(int value) {
        requireRange("ofShort", value, Short.MIN_VALUE, 0xFFFF, "a short, signed or unsigned");
        return new byte[]{(byte) (value >> 8), (byte) value};
    }

    /**
     * Returns an {@code int} as four bytes, big-endian, as {@code JCint.getInt} reads them (on a platform with the
     * optional int type).
     *
     * @param value the number
     * @return the four bytes, highest byte first
     */
    public static byte[] ofInt(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    /**
     * Returns a {@code boolean} as one byte: {@code 01} for {@code true}, {@code 00} for {@code false}. The Java Card
     * API defines no wire form of a boolean; this is the usual one, and the one
     * {@link APDUResponse#dataAsBoolean()} reads.
     *
     * @param value the value
     * @return {@code 01} or {@code 00}
     */
    public static byte[] ofBoolean(boolean value) {
        return new byte[]{(byte) (value ? 1 : 0)};
    }

    /**
     * Joins byte arrays, e.g. the fields of a command's data.
     *
     * @param parts the arrays, in order
     * @return a new array with the bytes of every part
     * @throws NullPointerException if a part is {@code null}; the message names its index
     */
    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (int i = 0; i < parts.length; i++) {
            joined.writeBytes(Objects.requireNonNull(parts[i], "part " + i + " of concat is null"));
        }
        return joined.toByteArray();
    }

    private static void requireRange(String writer, int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(String.format("%s takes %d to %d (%s); got %d", writer, min, max,
                    what, value));
        }
    }
}
