package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.LogicalChannel;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.PcscView;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-PCSC: the project's {@link PcscSession} on the real card (port of real-card step 5). Read-only, Issuer
 * Security Domain only: SELECT and GET DATA, no authentication. The guard decorates the session, so the
 * session's own {@code send}/{@code transmit}, channel routing and reset are what reach the card.
 */
@LiveCardTest
@Order(2)
class PcscSessionLiveTest {

    private static PcscView pcsc(LiveCard card) {
        return card.pcsc().orElseThrow(() -> new AssertionError("the card is not connected through PcscSession"));
    }

    private static AID isd(LiveCard card) {
        return AID.fromHex(card.config().isd());
    }

    private static APDUResponse cplc(LiveCard card) {
        return card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256);
    }

    /**
     * {@code PcscSession.Options.defaults()}: any protocol, exclusive access for the whole connection. The ATR the
     * session reads ({@code PcscSession.getATR()}) is an ISO/IEC 7816-3 ATR (8.1: TS '3B' or '3F', then at least
     * T0) and the one the connection reported.
     */
    @Test
    void sessionHoldsExclusiveAccessWithTheDefaultOptions(LiveCard card) {
        PcscView view = pcsc(card);

        assertThat(view.options()).isEqualTo(PcscSession.Options.defaults());
        assertThat(view.options().exclusive()).isTrue();
        assertThat(view.protocol()).isIn("T=0", "T=1");
        assertThat(view.atr()).as("ATR read by PcscSession.getATR()").matches("3[BF]([0-9A-F]{2})+");
        assertThat(view.atr()).isEqualTo(card.atr());
    }

    /** ISO/IEC 7816-4:2005 5.1: Ne = 256 on a short command is the Le byte '00'. */
    @Test
    void getDataWithNe256SendsLe00(LiveCard card) throws IOException {
        card.session().select(isd(card));

        APDUResponse response = cplc(card);

        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.data()).hasSize(45);
        assertThat(Files.readAllLines(card.transcript().file())).as("command as sent").contains("C: 80CA9F7F00");
    }

    /**
     * {@code reset()} really resets the card and connects again: a logical channel left open before is closed by
     * the reset (the next MANAGE CHANNEL OPEN gets the same number again), the ATR is the same and the session
     * keeps working. Without a real reset (a PC/SC transaction held during {@code disconnect(true)} on macOS) the
     * card would still have the channel open and assign the next number.
     */
    @Test
    void resetKeepsTheSessionUsable(LiveCard card) {
        String atr = pcsc(card).atr();
        card.session().select(isd(card));
        byte[] before = cplc(card).data();
        int leftOpen = LogicalChannel.open(card.session()).channelNumber();

        card.reset();

        assertThat(pcsc(card).atr()).isEqualTo(atr);
        try (LogicalChannel next = LogicalChannel.open(card.session())) {
            assertThat(next.channelNumber()).as("channel %d is free again only after a real card reset", leftOpen)
                    .isEqualTo(leftOpen);
        }
        card.session().select(isd(card));
        APDUResponse after = cplc(card);
        assertThat(after.sw()).isEqualTo(0x9000);
        assertThat(after.data()).isEqualTo(before);
    }

    /** MANAGE CHANNEL OPEN gives channel 1; the ISD answers there as on the basic channel, which keeps working. */
    @Test
    void logicalChannelReadsWhatTheBasicChannelReads(LiveCard card) {
        card.session().select(isd(card));
        byte[] basic = cplc(card).data();

        try (LogicalChannel channel = LogicalChannel.open(card.session())) {
            assertThat(channel.channelNumber()).isEqualTo(1);
            assertThat(channel.isManaged()).isTrue();
            assertThat(channel.select(isd(card)).sw()).isEqualTo(0x9000);
            APDUResponse onChannel = channel.send(0x80, 0xCA, 0x9F, 0x7F, null, 256);
            assertThat(onChannel.sw()).isEqualTo(0x9000);
            assertThat(onChannel.data()).isEqualTo(basic);
        }

        APDUResponse cardData = card.session().send(0x80, 0xCA, 0x00, 0x66, null, 256);
        assertThat(cardData.sw()).as("basic channel after closing channel 1").isEqualTo(0x9000);
    }
}
