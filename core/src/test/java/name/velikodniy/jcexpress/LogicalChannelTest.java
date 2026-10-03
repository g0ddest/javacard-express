package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.fakes.RecordingSession;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LogicalChannel}: CLA coding per ISO/IEC 7816-4:2005 5.1.1 (Tables 2 and 3) and
 * GlobalPlatform Card Specification 2.3.1 11.1.4, MANAGE CHANNEL per ISO/IEC 7816-4:2005 7.1.2.
 * {@link RecordingSession} records the bytes the shipped backends would transmit.
 */
class LogicalChannelTest {

    @Nested
    class ClaEncoding {

        @Test
        void basicChannelsUseBitsTwoAndOne() {
            assertThat(LogicalChannel.encodeCla(0x00, 1)).isEqualTo(0x01);
            assertThat(LogicalChannel.encodeCla(0x00, 3)).isEqualTo(0x03);
            assertThat(LogicalChannel.encodeCla(0x80, 2)).isEqualTo(0x82);
            assertThat(LogicalChannel.encodeCla(0x84, 3)).isEqualTo(0x87);
        }

        /** ISO/IEC 7816-4:2005 Table 3: b4-b1 code the channel number minus four. */
        @Test
        void channelsFourToNineteenUseTheFurtherInterindustryCoding() {
            assertThat(LogicalChannel.encodeCla(0x00, 4)).isEqualTo(0x40);
            assertThat(LogicalChannel.encodeCla(0x00, 19)).isEqualTo(0x4F);
            assertThat(LogicalChannel.encodeCla(0x80, 4)).isEqualTo(0xC0);
        }

        /** Channel 0 means "clear the channel bits", the javadoc promises they are overwritten. */
        @Test
        void channelZeroClearsExistingChannelBits() {
            assertThat(LogicalChannel.encodeCla(0x03, 0)).isEqualTo(0x00);
            assertThat(LogicalChannel.encodeCla(0x41, 0)).isEqualTo(0x00);
        }

        /** 'C0' is the GlobalPlatform class on channel 4 (Table 11-12); on channel 1 it is '81' (Table 11-11). */
        @Test
        void globalPlatformFurtherClassMovesToTheFirstCoding() {
            assertThat(LogicalChannel.encodeCla(0xC0, 1)).isEqualTo(0x81);
        }

        @Test
        void secureMessagingIndicationIsKept() {
            assertThat(LogicalChannel.encodeCla(0x04, 1)).isEqualTo(0x05);
            assertThat(LogicalChannel.encodeCla(0x0C, 5)).isEqualTo(0x61);
        }

        @Test
        void classBytesWithoutChannelCodingAreKeptOnChannelZero() {
            assertThat(LogicalChannel.encodeCla(0xFF, 0)).isEqualTo(0xFF);
        }
    }

    @Nested
    class BasicChannel {

        @Test
        void sendEncodesTheChannelAndKeepsDataAndLe() {
            RecordingSession card = new RecordingSession();
            LogicalChannel ch = LogicalChannel.basic(card, 2);

            ch.send(0x00, 0xA4, 0x04, 0x00);
            ch.send(0x00, 0xB0, 0x00, 0x00, null, 256);
            ch.send(0x80, 0xCA, 0x00, 0x66, new byte[]{0x5C}, 256);

            assertThat(card.wireHex(0)).isEqualTo("02 A4 04 00");
            assertThat(card.wireHex(1)).isEqualTo("02 B0 00 00 00");
            assertThat(card.wireHex(2)).isEqualTo("82 CA 00 66 01 5C 00");
        }

        @Test
        void channelFourUsesClass40() {
            RecordingSession card = new RecordingSession();

            LogicalChannel.basic(card, 4).send(0x00, 0xB0, 0x00, 0x00);

            assertThat(card.wireHex(0)).isEqualTo("40 B0 00 00");
        }

        @Test
        void channelZeroClearsChannelBitsOfTheCommand() {
            RecordingSession card = new RecordingSession();

            LogicalChannel.basic(card, 0).send(0x03, 0xB0, 0x00, 0x00);

            assertThat(card.wire.get(0)[0]).isZero();
        }

        @Test
        void closeSendsNothing() {
            RecordingSession card = new RecordingSession();
            LogicalChannel ch = LogicalChannel.basic(card, 1);

            ch.close();

            assertThat(card.wire).isEmpty();
            assertThat(ch.isManaged()).isFalse();
            assertThat(ch.channelNumber()).isEqualTo(1);
        }

        @Test
        void selectAndTransmitEncodeTheChannel() {
            RecordingSession card = new RecordingSession();
            LogicalChannel ch = LogicalChannel.basic(card, 1);

            ch.select(AID.fromHex("A0000000031010"));
            ch.transmit(Hex.decode("00CA006600"));

            assertThat(card.wireHex(0)).isEqualTo("01 A4 04 00 07 A0 00 00 00 03 10 10 00");
            assertThat(card.wireHex(1)).isEqualTo("01 CA 00 66 00");
        }
    }

    @Nested
    class ManagedChannel {

        /** ISO/IEC 7816-4:2005 7.1.2: with P2 = '00' the Le field shall be set to '01'. */
        @Test
        void openSendsManageChannelOpenWithLe01() {
            RecordingSession card = new RecordingSession().reply("01 9000");

            LogicalChannel ch = LogicalChannel.open(card);

            assertThat(card.wireHex(0)).isEqualTo("00 70 00 00 01");
            assertThat(ch.channelNumber()).isEqualTo(1);
            assertThat(ch.isManaged()).isTrue();
        }

        /** The card may assign any channel from '01' to '13' (7.1.2); channel 4 must not alias channel 0. */
        @Test
        void cardAssignedChannelFourIsUsedAsChannelFour() {
            RecordingSession card = new RecordingSession().reply("04 9000");

            LogicalChannel ch = LogicalChannel.open(card);
            ch.send(0x00, 0xB0, 0x00, 0x00);

            assertThat(ch.channelNumber()).isEqualTo(4);
            assertThat(card.wireHex(1)).isEqualTo("40 B0 00 00");
        }

        @ParameterizedTest(name = "response {0}")
        @ValueSource(strings = {"9000", "00 9000", "14 9000", "01 02 9000"})
        void openRejectsResponsesWithoutAValidChannelNumber(String response) {
            RecordingSession card = new RecordingSession().reply(response);

            assertThatThrownBy(() -> LogicalChannel.open(card))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MANAGE CHANNEL");
        }

        @Test
        void openReportsTheStatusWordOfAFailure() {
            RecordingSession card = new RecordingSession().reply("6881");

            assertThatThrownBy(() -> LogicalChannel.open(card))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("6881");
        }

        /** ISO/IEC 7816-4:2005 7.1.2: P2 '01' to '13' opens that channel, the Le field shall be absent. */
        @Test
        void openGivenChannelSendsP2WithoutLe() {
            RecordingSession card = new RecordingSession();

            LogicalChannel ch = LogicalChannel.open(card, 7);

            assertThat(card.wireHex(0)).isEqualTo("00 70 00 07");
            assertThat(ch.channelNumber()).isEqualTo(7);
        }

        @Test
        void openGivenChannelRejectsInvalidNumbers() {
            RecordingSession card = new RecordingSession();

            assertThatThrownBy(() -> LogicalChannel.open(card, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("channel 0");
            assertThatThrownBy(() -> LogicalChannel.open(card, 20))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(card.wire).isEmpty();
        }

        @Test
        void closeSendsManageChannelCloseOnceOnTheChannel() {
            RecordingSession card = new RecordingSession().reply("05 9000");
            LogicalChannel ch = LogicalChannel.open(card);

            ch.close();
            ch.close();

            assertThat(card.wire).hasSize(2);
            assertThat(card.wireHex(1)).isEqualTo("41 70 80 05");
        }

        @Test
        void closeReportsAFailure() {
            RecordingSession card = new RecordingSession().reply("03 9000").reply("6881");
            LogicalChannel ch = LogicalChannel.open(card);

            assertThatThrownBy(ch::close)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("6881");
        }

        @Test
        void aClosedChannelCannotBeUsed() {
            RecordingSession card = new RecordingSession().reply("02 9000");
            LogicalChannel ch = LogicalChannel.open(card);
            ch.close();

            assertThatThrownBy(() -> ch.send(0x00, 0xB0)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ch.transmit(Hex.decode("00B00000"))).isInstanceOf(IllegalStateException.class);
            assertThat(card.wire).hasSize(2);
        }
    }

    @Nested
    class Validation {

        @Test
        void channelNumbersAboveNineteenAreRejected() {
            RecordingSession card = new RecordingSession();

            assertThatThrownBy(() -> LogicalChannel.basic(card, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0-19");
            assertThatThrownBy(() -> LogicalChannel.basic(card, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullSessionIsRejected() {
            assertThatThrownBy(() -> LogicalChannel.basic(null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
            assertThatThrownBy(() -> LogicalChannel.open(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void transmitRejectsShortApdu() {
            LogicalChannel ch = LogicalChannel.basic(new RecordingSession(), 1);

            assertThatThrownBy(() -> ch.transmit(new byte[]{0x00}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("4 bytes");
        }
    }
}
