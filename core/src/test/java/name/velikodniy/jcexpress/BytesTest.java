package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.apdu.APDUCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The command data of the Java Card basic types, as an applet reads them: {@code Util.getShort} and
 * {@code JCint.getInt} read big-endian.
 */
class BytesTest {

    @Test
    void ofShortWritesTwoBytesBigEndianLikeUtilSetShort() {
        assertThat(Bytes.ofShort(26)).containsExactly(0x00, 0x1A);
        assertThat(Bytes.ofShort(-2)).containsExactly(0xFF, 0xFE);
        assertThat(Bytes.ofShort(0xFFFF)).containsExactly(0xFF, 0xFF);
        assertThat(Bytes.ofShort(Short.MIN_VALUE)).containsExactly(0x80, 0x00);
    }

    @Test
    void ofByteTakesASignedOrAnUnsignedByte() {
        assertThat(Bytes.ofByte(0x90)).containsExactly(0x90);
        assertThat(Bytes.ofByte(-1)).containsExactly(0xFF);
        assertThat(Bytes.ofByte(Byte.MIN_VALUE)).containsExactly(0x80);
    }

    @Test
    void ofIntWritesFourBytesBigEndianLikeJCintSetInt() {
        assertThat(Bytes.ofInt(123456)).containsExactly(0x00, 0x01, 0xE2, 0x40);
        assertThat(Bytes.ofInt(-1)).containsExactly(0xFF, 0xFF, 0xFF, 0xFF);
    }

    @Test
    void ofBooleanIsOneOrZero() {
        assertThat(Bytes.ofBoolean(true)).containsExactly(0x01);
        assertThat(Bytes.ofBoolean(false)).containsExactly(0x00);
    }

    @Test
    void aValueOutsideTheTypeIsRejectedWithTheValue() {
        assertThatThrownBy(() -> Bytes.ofByte(256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ofByte takes -128 to 255 (a byte, signed or unsigned); got 256");
        assertThatThrownBy(() -> Bytes.ofShort(70000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ofShort takes -32768 to 65535 (a short, signed or unsigned); got 70000");
        assertThatThrownBy(() -> Bytes.ofShort(-32769))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("got -32769");
    }

    @Test
    void concatJoinsThePartsInOrder() {
        assertThat(Bytes.concat(Bytes.ofShort(1), Bytes.ofByte(2), new byte[0])).containsExactly(0x00, 0x01, 0x02);
        assertThat(Bytes.concat()).isEmpty();
        assertThatThrownBy(() -> Bytes.concat(Bytes.ofByte(1), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("part 1 of concat is null");
    }

    @Test
    void aCommandCarriesTheEncodedValue() {
        assertThat(APDUCommand.of(0x80, 0x30).data(Bytes.ofShort(100)).toBytes())
                .containsExactly(0x80, 0x30, 0x00, 0x00, 0x02, 0x00, 0x64);
    }
}
