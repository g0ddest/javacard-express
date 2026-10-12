package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SW;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.Assertions;

/**
 * The value readers of {@link APDUResponseAssert}: numbers and booleans in the response data, as AssertJ assertions.
 *
 * <p>Every number is an integer assertion, also that of a {@code byte} or a {@code short}: AssertJ's byte and short
 * assertions take only a {@code byte} or a {@code short} in {@code isEqualTo}, so {@code isEqualTo(26)} would compare
 * a {@code Short} with an {@code Integer} and fail. An integer assertion compares an int literal and a {@code short}
 * variable alike: {@code dataAsShort().isEqualTo(26)}, {@code dataAsShort().isEqualTo(expectedBalance)}.</p>
 *
 * <p>A read the data cannot give fails the assertion with the data and the status word.</p>
 *
 * @param <SELF> the assertion type
 */
abstract class AbstractResponseValueAssert<SELF extends AbstractResponseValueAssert<SELF>>
        extends AbstractAssert<SELF, APDUResponse> {

    AbstractResponseValueAssert(APDUResponse actual, Class<?> selfType) {
        super(actual, selfType);
    }

    /**
     * Returns an assertion on the response data as a {@code byte} ({@link APDUResponse#dataAsByte()}): exactly one
     * byte.
     *
     * @return an integer assertion on the byte, signed: {@code -128} to {@code 127}
     */
    public AbstractIntegerAssert<?> dataAsByte() {
        requireLength("dataAsByte", 1);
        return Assertions.assertThat((int) actual.dataAsByte()).as("dataAsByte() of %s", actual);
    }

    /**
     * Returns an assertion on the response data as a {@code short} ({@link APDUResponse#dataAsShort()}): exactly two
     * bytes, big-endian, as an applet writes them with {@code Util.setShort}:
     * {@code assertThat(card.send(GET_BALANCE)).isSuccess().dataAsShort().isEqualTo(100)}.
     *
     * @return an integer assertion on the number, {@code -32768} to {@code 32767}
     */
    public AbstractIntegerAssert<?> dataAsShort() {
        requireLength("dataAsShort", 2);
        return Assertions.assertThat((int) actual.dataAsShort()).as("dataAsShort() of %s", actual);
    }

    /**
     * Returns an assertion on the response data as an {@code int} ({@link APDUResponse#dataAsInt()}): exactly four
     * bytes, big-endian, as {@code JCint.setInt} writes them.
     *
     * @return an integer assertion on the number
     */
    public AbstractIntegerAssert<?> dataAsInt() {
        requireLength("dataAsInt", 4);
        return Assertions.assertThat(actual.dataAsInt()).as("dataAsInt() of %s", actual);
    }

    /**
     * Returns an assertion on the response data as a {@code boolean} ({@link APDUResponse#dataAsBoolean()}): exactly
     * one byte, {@code 00} for {@code false} and {@code 01} for {@code true}.
     *
     * @return a boolean assertion on the value
     */
    public AbstractBooleanAssert<?> dataAsBoolean() {
        requireLength("dataAsBoolean", 1);
        int value = actual.u8(0);
        if (value > 1) {
            failWithMessage("Expected response data 00 (false) or 01 (true) for dataAsBoolean, but the data is %02X"
                    + " (SW=%04X, %s)", value, actual.sw(), SW.describe(actual.sw()));
        }
        return Assertions.assertThat(actual.dataAsBoolean()).as("dataAsBoolean() of %s", actual);
    }

    /**
     * Returns an assertion on a {@code byte} of the response data ({@link APDUResponse#byteAt(int)}); {@link #u8(int)}
     * reads it unsigned.
     *
     * @param offset the offset in the data
     * @return an integer assertion on the byte, signed: {@code -128} to {@code 127}
     */
    public AbstractIntegerAssert<?> byteAt(int offset) {
        requireData("byteAt", offset, 1);
        return Assertions.assertThat((int) actual.byteAt(offset)).as("byteAt(%d) of %s", offset, actual);
    }

    /**
     * Returns an assertion on a {@code short} of the response data ({@link APDUResponse#shortAt(int)}): two bytes,
     * big-endian, as {@code Util.getShort} reads them; {@link #u16(int)} reads them unsigned.
     *
     * @param offset the offset of the high byte in the data
     * @return an integer assertion on the number, {@code -32768} to {@code 32767}
     */
    public AbstractIntegerAssert<?> shortAt(int offset) {
        requireData("shortAt", offset, 2);
        return Assertions.assertThat((int) actual.shortAt(offset)).as("shortAt(%d) of %s", offset, actual);
    }

    /**
     * Returns an assertion on an {@code int} of the response data ({@link APDUResponse#intAt(int)}): four bytes,
     * big-endian, as {@code JCint.getInt} reads them.
     *
     * @param offset the offset of the highest byte in the data
     * @return an integer assertion on the number
     */
    public AbstractIntegerAssert<?> intAt(int offset) {
        requireData("intAt", offset, 4);
        return Assertions.assertThat(actual.intAt(offset)).as("intAt(%d) of %s", offset, actual);
    }

    /**
     * Returns an assertion on an unsigned byte of the response data ({@link APDUResponse#u8(int)}).
     *
     * @param offset the offset in the data
     * @return an integer assertion on the byte, {@code 0} to {@code 255}
     */
    public AbstractIntegerAssert<?> u8(int offset) {
        requireData("u8", offset, 1);
        return Assertions.assertThat(actual.u8(offset)).as("u8 at offset %d of the data of %s", offset, actual);
    }

    /**
     * Returns an assertion on an unsigned big-endian 16-bit number of the response data
     * ({@link APDUResponse#u16(int)}): {@code assertThat(response).isSuccess().u16(0).isEqualTo(70)}.
     *
     * @param offset the offset of the high byte in the data
     * @return an integer assertion on the number, {@code 0} to {@code 65535}
     */
    public AbstractIntegerAssert<?> u16(int offset) {
        requireData("u16", offset, 2);
        return Assertions.assertThat(actual.u16(offset)).as("u16 at offset %d of the data of %s", offset, actual);
    }

    /**
     * Returns an assertion on a signed big-endian 16-bit number of the response data, the {@code short} an applet
     * wrote with {@code Util.setShort} ({@link APDUResponse#s16(int)}).
     *
     * @param offset the offset of the high byte in the data
     * @return an integer assertion on the number, {@code -32768} to {@code 32767}
     */
    public AbstractIntegerAssert<?> s16(int offset) {
        requireData("s16", offset, 2);
        return Assertions.assertThat(actual.s16(offset)).as("s16 at offset %d of the data of %s", offset, actual);
    }

    /** Fails (as an assertion) when the data has no {@code width} bytes at {@code offset}, naming the status word. */
    private void requireData(String reader, int offset, int width) {
        isNotNull();
        int length = actual.data().length;
        if (offset < 0 || offset > length - width) {
            failWithMessage("Expected response data with %d byte%s at offset %d for %s, but the data has %d byte%s"
                            + " (SW=%04X, %s)", width, width == 1 ? "" : "s", offset, reader, length,
                    length == 1 ? "" : "s", actual.sw(), SW.describe(actual.sw()));
        }
    }

    /** Fails (as an assertion) when the data is not exactly {@code width} bytes, showing the data and status word. */
    private void requireLength(String reader, int width) {
        isNotNull();
        byte[] data = actual.data();
        if (data.length != width) {
            String has = data.length + (data.length == 1 ? " byte" : " bytes")
                    + (data.length == 0 ? "" : ": " + Hex.encode(data));
            failWithMessage("Expected response data of exactly %d byte%s for %s, but the data has %s (SW=%04X, %s)",
                    width, width == 1 ? "" : "s", reader, has, actual.sw(), SW.describe(actual.sw()));
        }
    }
}
