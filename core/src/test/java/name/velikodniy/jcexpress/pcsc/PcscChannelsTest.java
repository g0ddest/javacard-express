package name.velikodniy.jcexpress.pcsc;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.LogicalChannel;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Logical channels (ISO/IEC 7816-4:2005 5.1.1.2, 7.1.2) map to javax.smartcardio channels:
 * MANAGE CHANNEL goes through Card.openLogicalChannel / CardChannel.close, commands through the
 * CardChannel of the channel coded in CLA.
 */
class PcscChannelsTest {

    /** Answers 9000 with the channel number the command arrived on. */
    private ContractCardTerminal channelCard() {
        return new ContractCardTerminal("T=1", ContractCardTerminal.cardAssignsChannels(
                (channel, apdu) -> new byte[]{channel.byteValue(), (byte) 0x90, 0x00}));
    }

    @Test
    void managedChannelIsOpenedUsedAndClosedThroughJavax() {
        ContractCardTerminal reader = channelCard();
        PcscSession card = PcscSession.open(reader);

        try (LogicalChannel ch = LogicalChannel.open(card)) {
            assertThat(ch.channelNumber()).isEqualTo(1);
            assertThat(ch.select(AID.fromHex("A000000151000000")).data()).containsExactly(1);
            assertThat(ch.send(0x80, 0xCA, 0x00, 0x66, null, 256).data()).containsExactly(1);
        }

        assertThat(reader.wire).extracting(Hex::encodeSpaced).containsExactly(
                "00 70 00 00 01",
                "01 A4 04 00 08 A0 00 00 01 51 00 00 00 00",
                "81 CA 00 66 00",
                "01 70 80 01");
        assertThat(card.send(0x00, 0xB0, 0x00, 0x00).data()).containsExactly(0);
    }

    @Test
    void channelsFromFourOnUseTheFurtherInterindustryClass() {
        ContractCardTerminal reader = channelCard();
        PcscSession card = PcscSession.open(reader);
        for (int i = 0; i < 3; i++) {
            LogicalChannel.open(card);
        }

        LogicalChannel fourth = LogicalChannel.open(card);
        APDUResponse r = fourth.send(0x00, 0xB0, 0x00, 0x00);

        assertThat(fourth.channelNumber()).isEqualTo(4);
        assertThat(r.data()).containsExactly(4);
        assertThat(Hex.encodeSpaced(reader.wire.get(reader.wire.size() - 1))).isEqualTo("40 B0 00 00");
    }

    @Test
    void channelsNotOpenedThroughTheSessionCannotBeAddressed() {
        ContractCardTerminal reader = channelCard();
        PcscSession card = PcscSession.open(reader);

        assertThatThrownBy(() -> LogicalChannel.basic(card, 2).send(0x00, 0xB0, 0x00, 0x00))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("channel 2");
        assertThatThrownBy(() -> card.transmit(Hex.decode("81CA006600")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(reader.wire).isEmpty();
    }

    @Test
    void openingAGivenChannelNumberIsNotSupportedByJavax() {
        PcscSession card = PcscSession.open(channelCard());

        assertThatThrownBy(() -> LogicalChannel.open(card, 2))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("P2 = '00'");
    }

    /**
     * The plain MANAGE CHANNEL is mapped to Card.openLogicalChannel / CardChannel.close, which send unprotected
     * commands. A MANAGE CHANNEL whose CLA indicates secure messaging (ISO/IEC 7816-4:2005 5.1.1) carries protected
     * data objects that this mapping would drop, so it is refused before anything reaches the card.
     */
    @Test
    void manageChannelUnderSecureMessagingIsRefusedInsteadOfBeingSentInPlain() {
        ContractCardTerminal reader = channelCard();
        PcscSession card = PcscSession.open(reader);

        assertThatThrownBy(() -> card.transmit(Hex.decode("0C7000000D9701018E08A1A2A3A4A5A6A7A800")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("secure messaging");
        assertThatThrownBy(() -> card.transmit(Hex.decode("0C7080010A8E08A1A2A3A4A5A6A7A8")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(reader.wire).isEmpty();
    }

    @Test
    void aRefusedManageChannelIsReportedAsPcscException() {
        ContractCardTerminal reader = new ContractCardTerminal("T=1", (channel, apdu) -> Hex.decode("6881"));
        PcscSession card = PcscSession.open(reader);

        assertThatThrownBy(() -> LogicalChannel.open(card)).isInstanceOf(PcscException.class);
    }

    @Test
    void resetClosesLogicalChannels() {
        PcscSession card = PcscSession.open(channelCard());
        LogicalChannel ch = LogicalChannel.open(card);

        card.reset();

        assertThatThrownBy(() -> ch.send(0x00, 0xB0, 0x00, 0x00))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void closingTheSessionClosesItsLogicalChannels() {
        ContractCardTerminal reader = channelCard();
        PcscSession card = PcscSession.open(reader);
        LogicalChannel.open(card);

        card.close();

        assertThat(Hex.encodeSpaced(reader.wire.get(reader.wire.size() - 1))).isEqualTo("01 70 80 01");
    }
}
