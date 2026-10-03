package name.velikodniy.jcexpress.fakes;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardNotPresentException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fake {@code javax.smartcardio} terminal implementing the documented API contract (the javadoc of
 * {@link CardTerminal}, {@link Card} and {@link CardChannel}) that the real SunPCSC provider follows
 * (verified by the audit against pcsc-lite + vpcd + jCardSim):
 * <ul>
 *   <li>{@code CardTerminal.connect}: "If a connection has previously established using the specified
 *       protocol, this method returns the same Card object as the previous call."</li>
 *   <li>{@code Card.disconnect}: afterwards, methods "that require interaction with the card will raise an
 *       IllegalStateException".</li>
 *   <li>{@code CardChannel.transmit}: "The CLA byte of the command APDU is automatically adjusted to match
 *       the channel number of this CardChannel" (SunPCSC adjusts interindustry classes only) and
 *       "IllegalArgumentException - if the APDU encodes a MANAGE CHANNEL command".</li>
 *   <li>{@code Card.openLogicalChannel} sends MANAGE CHANNEL OPEN and returns a channel with the number the
 *       card assigned; {@code CardChannel.close} sends MANAGE CHANNEL CLOSE.</li>
 *   <li>{@code Card.beginExclusive}: "CardException - if exclusive access has already been set"; while a
 *       thread holds exclusive access, "other threads attempting communication will receive a CardException"
 *       (transmit, openLogicalChannel, channel close and disconnect, as SunPCSC does).</li>
 *   <li>{@code Card.disconnect(true)} while the connection holds exclusive access (a PC/SC transaction) does
 *       not reset the card, as macOS PC/SC behaves (observed on a JCOP 4 card); after
 *       {@code endExclusive()} it does. {@link #resets()} counts performed resets only.</li>
 * </ul>
 *
 * <p>The card itself is a {@code responder(channel, apduBytes) -> responseBytes} function; every APDU that
 * reaches the card is recorded in {@link #wire} after CLA adjustment.</p>
 */
public final class ContractCardTerminal extends CardTerminal {

    /** Every APDU as it reached the card (after the provider's CLA adjustment). */
    public final List<byte[]> wire = new ArrayList<>();
    private final BiFunction<Integer, byte[], byte[]> responder;
    private final String protocol;
    private String name = "Contract Reader 00";
    private boolean cardPresent = true;
    private final List<String> events = new ArrayList<>();
    private int connects;
    private int resets;
    private boolean endExclusiveFails;
    private FakeCard current;

    /** Creates a terminal whose card answers 9000 to everything, using protocol T=1. */
    public ContractCardTerminal() {
        this("T=1", (channel, apdu) -> new byte[]{(byte) 0x90, 0x00});
    }

    /**
     * Creates a terminal with a custom card.
     *
     * @param protocol  the protocol reported by the card ("T=0" or "T=1")
     * @param responder the card: (logical channel, APDU bytes) to response bytes including SW
     */
    public ContractCardTerminal(String protocol, BiFunction<Integer, byte[], byte[]> responder) {
        this.protocol = protocol;
        this.responder = responder;
    }

    /**
     * Sets the reader name.
     *
     * @param readerName the name reported by {@link #getName()}
     * @return this terminal
     */
    public ContractCardTerminal named(String readerName) {
        this.name = readerName;
        return this;
    }

    /**
     * Removes or inserts the card.
     *
     * @param present whether a card is present
     * @return this terminal
     */
    public ContractCardTerminal cardPresent(boolean present) {
        this.cardPresent = present;
        return this;
    }

    /** @return number of physical connections made (a reused Card does not count) */
    public int connects() {
        return connects;
    }

    /** @return number of card resets performed (disconnect(true) without exclusive access held) */
    public int resets() {
        return resets;
    }

    /**
     * Returns the connection events in order: {@code connect}, {@code beginExclusive}, {@code endExclusive},
     * {@code disconnect(reset)}, {@code disconnect(leave)}.
     *
     * @return the events so far
     */
    public List<String> events() {
        return List.copyOf(events);
    }

    /**
     * Makes {@code Card.endExclusive()} fail with a CardException (the provider still ends the transaction,
     * as SunPCSC does in its finally block).
     *
     * @return this terminal
     */
    public ContractCardTerminal failingEndExclusive() {
        this.endExclusiveFails = true;
        return this;
    }

    /** @return the Card object of the current connection, or null */
    public FakeCard currentCard() {
        return current;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isCardPresent() {
        return cardPresent;
    }

    @Override
    public boolean waitForCardPresent(long timeout) {
        return true;
    }

    @Override
    public boolean waitForCardAbsent(long timeout) {
        return false;
    }

    @Override
    public Card connect(String requested) throws CardException {
        if (!cardPresent) {
            throw new CardNotPresentException("No card present");
        }
        if (current == null || current.disposed) {
            current = new FakeCard();
            connects++;
            events.add("connect");
        }
        return current;
    }

    private byte[] toCard(int channel, byte[] apdu) {
        wire.add(apdu.clone());
        return responder.apply(channel, apdu);
    }

    /** Card of the fake terminal. */
    public final class FakeCard extends Card {
        private boolean disposed;
        private Thread exclusiveOwner;
        private final FakeChannel basic = new FakeChannel(this, 0);

        /** @return true if exclusive access is currently held */
        public boolean isExclusive() {
            return exclusiveOwner != null;
        }

        /** @return true after disconnect */
        public boolean isDisposed() {
            return disposed;
        }

        void check() {
            if (disposed) {
                throw new IllegalStateException("Card has been disconnected");
            }
        }

        void checkExclusive() throws CardException {
            if (exclusiveOwner != null && exclusiveOwner != Thread.currentThread()) {
                throw new CardException("Exclusive access established by another Thread");
            }
        }

        @Override
        public ATR getATR() {
            return new ATR(new byte[]{0x3B, 0x00});
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public CardChannel getBasicChannel() {
            check();
            return basic;
        }

        @Override
        public CardChannel openLogicalChannel() throws CardException {
            check();
            checkExclusive();
            byte[] response = toCard(0, new byte[]{0x00, 0x70, 0x00, 0x00, 0x01});
            ResponseAPDU r = new ResponseAPDU(response);
            if (r.getSW() != 0x9000 || r.getData().length != 1) {
                throw new CardException("openLogicalChannel() failed, card response: " + Integer.toHexString(r.getSW()));
            }
            return new FakeChannel(this, r.getData()[0] & 0xFF);
        }

        @Override
        public void beginExclusive() throws CardException {
            check();
            if (exclusiveOwner != null) {
                throw new CardException("Exclusive access has already been set");
            }
            exclusiveOwner = Thread.currentThread();
            events.add("beginExclusive");
        }

        @Override
        public void endExclusive() throws CardException {
            check();
            if (exclusiveOwner != Thread.currentThread()) {
                throw new IllegalStateException("Active thread does not have exclusive access");
            }
            exclusiveOwner = null;
            events.add("endExclusive");
            if (endExclusiveFails) {
                throw new CardException("endExclusive() failed");
            }
        }

        @Override
        public byte[] transmitControlCommand(int code, byte[] command) {
            check();
            return new byte[0];
        }

        @Override
        public void disconnect(boolean reset) throws CardException {
            if (disposed) {
                return;
            }
            checkExclusive();
            events.add(reset ? "disconnect(reset)" : "disconnect(leave)");
            if (reset && exclusiveOwner == null) {
                resets++;
            }
            disposed = true;
            exclusiveOwner = null;
        }
    }

    /** Channel of the fake card. */
    public final class FakeChannel extends CardChannel {
        private final FakeCard card;
        private final int number;
        private boolean closed;

        FakeChannel(FakeCard card, int number) {
            this.card = card;
            this.number = number;
        }

        @Override
        public Card getCard() {
            return card;
        }

        @Override
        public int getChannelNumber() {
            card.check();
            return number;
        }

        @Override
        public ResponseAPDU transmit(CommandAPDU command) throws CardException {
            card.check();
            if (closed) {
                throw new IllegalStateException("Logical channel has been closed");
            }
            card.checkExclusive();
            byte[] apdu = command.getBytes();
            if ((apdu[1] & 0xFF) == 0x70) {
                throw new IllegalArgumentException("Manage channel command not allowed, use openLogicalChannel()");
            }
            apdu[0] = (byte) adjustCla(apdu[0] & 0xFF, number);
            return new ResponseAPDU(toCard(number, apdu));
        }

        @Override
        public int transmit(ByteBuffer command, ByteBuffer response) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws CardException {
            card.check();
            if (number == 0) {
                throw new IllegalStateException("Cannot close basic logical channel");
            }
            if (closed) {
                return;
            }
            card.checkExclusive();
            closed = true;
            byte[] response = toCard(number, new byte[]{(byte) adjustCla(0x00, number), 0x70, (byte) 0x80,
                    (byte) number});
            if (new ResponseAPDU(response).getSW() != 0x9000) {
                throw new CardException("close() failed");
            }
        }
    }

    /**
     * Responder helper: a card that supports logical channels, assigns channel numbers 1, 2, ... to
     * MANAGE CHANNEL OPEN, accepts MANAGE CHANNEL CLOSE and answers everything else with {@code other}.
     *
     * @param other responder for all other commands
     * @return the responder
     */
    public static BiFunction<Integer, byte[], byte[]> cardAssignsChannels(
            BiFunction<Integer, byte[], byte[]> other) {
        int[] next = {1};
        return (channel, apdu) -> {
            if ((apdu[1] & 0xFF) == 0x70 && apdu[2] == 0x00) {
                return new byte[]{(byte) next[0]++, (byte) 0x90, 0x00};
            }
            if ((apdu[1] & 0xFF) == 0x70) {
                return new byte[]{(byte) 0x90, 0x00};
            }
            return other.apply(channel, apdu);
        };
    }

    /**
     * CLA adjustment performed by the SunPCSC provider: interindustry classes get the channel number
     * (ISO/IEC 7816-4:2005 5.1.1 Tables 2 and 3); proprietary classes are left unchanged.
     */
    static int adjustCla(int cla, int channel) {
        if ((cla & 0x80) != 0 || (cla & 0xE0) == 0x20) {
            return cla;
        }
        if (channel <= 3) {
            return (cla & 0xBC) | channel;
        }
        return (cla & 0xB0) | 0x40 | (channel - 4);
    }
}
