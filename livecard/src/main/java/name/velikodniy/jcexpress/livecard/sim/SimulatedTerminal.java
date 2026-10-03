package name.velikodniy.jcexpress.livecard.sim;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.ByteBuffer;
import java.util.HexFormat;

/**
 * A {@code javax.smartcardio} reader holding a {@link SimulatedCard}, so the project's real
 * {@link name.velikodniy.jcexpress.pcsc.PcscSession} talks to the simulated card exactly as to a card in a PC/SC
 * reader. It follows the documented contract the SunPCSC provider implements: {@code connect} returns the
 * connection's {@link Card} until it is disconnected, {@code disconnect(true)} resets the card,
 * {@link Card#openLogicalChannel()} sends MANAGE CHANNEL OPEN, {@link CardChannel#transmit(CommandAPDU)} puts the
 * channel number into interindustry class bytes and refuses MANAGE CHANNEL. Like macOS PC/SC (observed with the
 * validated card), {@code disconnect(true)} does not reset the card while the connection holds exclusive access.
 */
public final class SimulatedTerminal extends CardTerminal {

    /** Reader name reported for the simulated card. */
    public static final String READER = "Simulated GlobalPlatform card (jCardSim)";

    private static final HexFormat HEX = HexFormat.of();

    private final SimulatedCard card;
    private Connection current;

    /**
     * Creates a reader that holds a card.
     *
     * @param card the card in the reader
     */
    public SimulatedTerminal(SimulatedCard card) {
        this.card = card;
    }

    @Override
    public String getName() {
        return READER;
    }

    @Override
    public Card connect(String protocol) {
        if (current == null || current.disposed) {
            current = new Connection();
        }
        return current;
    }

    @Override
    public boolean isCardPresent() {
        return true;
    }

    @Override
    public boolean waitForCardPresent(long timeout) {
        return true;
    }

    @Override
    public boolean waitForCardAbsent(long timeout) {
        return false;
    }

    /** The connection to the card. */
    final class Connection extends Card {
        private final Channel basic = new Channel(0);
        private boolean disposed;
        private Thread exclusive;

        private void check() {
            if (disposed) {
                throw new IllegalStateException("Card has been disconnected");
            }
        }

        @Override
        public ATR getATR() {
            return new ATR(HEX.parseHex(SimulatedCard.ATR));
        }

        @Override
        public String getProtocol() {
            return "T=1";
        }

        @Override
        public CardChannel getBasicChannel() {
            check();
            return basic;
        }

        @Override
        public CardChannel openLogicalChannel() throws CardException {
            check();
            ResponseAPDU response = new ResponseAPDU(card.transmit(new byte[]{0x00, 0x70, 0x00, 0x00, 0x01}));
            if (response.getSW() != 0x9000 || response.getData().length != 1) {
                throw new CardException("openLogicalChannel() failed: " + Integer.toHexString(response.getSW()));
            }
            return new Channel(response.getData()[0] & 0xFF);
        }

        @Override
        public void beginExclusive() throws CardException {
            check();
            if (exclusive != null) {
                throw new CardException("Exclusive access has already been set");
            }
            exclusive = Thread.currentThread();
        }

        @Override
        public void endExclusive() {
            check();
            exclusive = null;
        }

        @Override
        public byte[] transmitControlCommand(int controlCode, byte[] command) throws CardException {
            throw new CardException("control commands are not simulated");
        }

        @Override
        public void disconnect(boolean reset) {
            if (!disposed) {
                disposed = true;
                if (reset && exclusive == null) {
                    card.reset();
                }
                exclusive = null;
            }
        }

        /** A logical channel of the connection. */
        final class Channel extends CardChannel {
            private final int number;
            private boolean closed;

            Channel(int number) {
                this.number = number;
            }

            @Override
            public Card getCard() {
                return Connection.this;
            }

            @Override
            public int getChannelNumber() {
                check();
                return number;
            }

            @Override
            public ResponseAPDU transmit(CommandAPDU command) throws CardException {
                check();
                if (closed) {
                    throw new IllegalStateException("Logical channel has been closed");
                }
                byte[] apdu = command.getBytes();
                // as the JDK's channel: MANAGE CHANNEL is refused in the interindustry class only (CLA '00'-'7F')
                if (apdu[0] >= 0 && apdu[1] == 0x70) {
                    throw new IllegalArgumentException("Manage channel command not allowed, use openLogicalChannel()");
                }
                apdu[0] = (byte) withChannel(apdu[0] & 0xFF, number);
                return new ResponseAPDU(exchange(apdu));
            }

            @Override
            public int transmit(ByteBuffer command, ByteBuffer response) {
                throw new UnsupportedOperationException("not simulated");
            }

            @Override
            public void close() throws CardException {
                check();
                if (number == 0) {
                    throw new IllegalStateException("Cannot close basic logical channel");
                }
                if (!closed) {
                    closed = true;
                    byte[] response = card.transmit(new byte[]{(byte) withChannel(0, number), 0x70, (byte) 0x80,
                        (byte) number});
                    if (new ResponseAPDU(response).getSW() != 0x9000) {
                        throw new CardException("close() failed");
                    }
                }
            }
        }
    }

    /**
     * Sends a command to the simulated card. A failure inside the simulator (an applet class that cannot be loaded, a
     * jCardSim error) is a transmission failure with its cause, as javax.smartcardio reports one: an
     * {@link IllegalStateException} would read as a lost connection to the {@code PcscSession}.
     */
    private byte[] exchange(byte[] apdu) throws CardException {
        try {
            return card.transmit(apdu);
        } catch (RuntimeException e) {
            throw new CardException("the simulated card failed: " + e, e);
        }
    }

    /** SunPCSC's class byte adjustment: interindustry classes get the channel, proprietary ones stay. */
    private static int withChannel(int cla, int channel) {
        if ((cla & 0x80) != 0 || (cla & 0xE0) == 0x20) {
            return cla;
        }
        return channel <= 3 ? (cla & 0xBC) | channel : (cla & 0xB0) | 0x40 | (channel - 4);
    }
}
