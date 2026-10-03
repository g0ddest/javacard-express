package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.SmartCardSession;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.ByteBuffer;

/**
 * A {@code javax.smartcardio} card for {@code PcscSession} tests whose provider hands every response up unchanged,
 * as the JDK's SunPCSC does with {@code sun.security.smartcardio.t0GetResponse=false} and {@code t1GetResponse=false}:
 * no GET RESPONSE after '61XX' and no re-send after '6CXX' below the session. The card behind it is a
 * {@link SmartCardSession} stand-in (e.g. a {@link TranscriptCard}) that receives the bytes of every command.
 */
final class PassThroughPcscCard extends Card {

    private final SmartCardSession card;

    /**
     * Creates the card.
     *
     * @param card the stand-in answering the commands
     */
    PassThroughPcscCard(SmartCardSession card) {
        this.card = card;
    }

    @Override
    public ATR getATR() {
        return new ATR(new byte[]{0x3B, (byte) 0xF0, 0x11, 0x00, (byte) 0xFF, 0x01});
    }

    @Override
    public String getProtocol() {
        return "T=1";
    }

    @Override
    public CardChannel getBasicChannel() {
        return new BasicChannel();
    }

    @Override
    public CardChannel openLogicalChannel() {
        throw new UnsupportedOperationException("basic channel only");
    }

    @Override
    public void beginExclusive() {
        // a stand-in has no other clients
    }

    @Override
    public void endExclusive() {
        // a stand-in has no other clients
    }

    @Override
    public byte[] transmitControlCommand(int controlCode, byte[] command) {
        throw new UnsupportedOperationException("no reader control commands");
    }

    @Override
    public void disconnect(boolean reset) {
        // nothing to release
    }

    /** The basic channel: every command goes to the stand-in, every response comes back unchanged. */
    private final class BasicChannel extends CardChannel {

        @Override
        public Card getCard() {
            return PassThroughPcscCard.this;
        }

        @Override
        public int getChannelNumber() {
            return 0;
        }

        @Override
        public ResponseAPDU transmit(CommandAPDU command) {
            return new ResponseAPDU(card.transmit(command.getBytes()));
        }

        @Override
        public int transmit(ByteBuffer command, ByteBuffer response) {
            throw new UnsupportedOperationException("PcscSession transmits CommandAPDU objects");
        }

        @Override
        public void close() {
            throw new IllegalStateException("the basic channel cannot be closed");
        }
    }
}
