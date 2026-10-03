package name.velikodniy.jcexpress.pcsc;

import name.velikodniy.jcexpress.apdu.ClassByte;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Logical channels of one PC/SC connection.
 *
 * <p>Commands carry their logical channel in the class byte (ISO/IEC 7816-4:2005 5.1.1, see
 * {@link ClassByte}). {@code javax.smartcardio} instead sends every command through a {@link CardChannel}
 * object and rewrites the channel bits of interindustry class bytes to that channel's number; its
 * {@code CardChannel.transmit} refuses MANAGE CHANNEL. This class therefore</p>
 * <ul>
 *   <li>performs MANAGE CHANNEL OPEN with P1-P2 = '0000' through {@link Card#openLogicalChannel()} (which
 *       sends {@code 00 70 00 00 01}) and answers {@code [channel] 9000} like the card;</li>
 *   <li>performs MANAGE CHANNEL CLOSE (P1 = '80', channel in P2 or in CLA) through {@link CardChannel#close()};</li>
 *   <li>sends every other command through the {@link CardChannel} of the channel its CLA codes.</li>
 * </ul>
 * <p>Channels that were not opened through this connection cannot be addressed, and MANAGE CHANNEL OPEN of
 * a given channel number (P2 = '01' to '13') has no {@code javax.smartcardio} equivalent; both are
 * rejected with {@link UnsupportedOperationException} instead of reaching another channel. So is a MANAGE
 * CHANNEL whose class byte indicates secure messaging: the plain {@code javax.smartcardio} calls would drop its
 * protection.</p>
 */
final class PcscChannels {

    private static final int INS_MANAGE_CHANNEL = 0x70;
    private static final int P1_OPEN = 0x00;
    private static final int P1_CLOSE = 0x80;

    private final Card card;
    private final Map<Integer, CardChannel> opened = new TreeMap<>();

    PcscChannels(Card card) {
        this.card = card;
    }

    /**
     * Transmits a command on the channel coded in its CLA.
     *
     * @param command the command
     * @return the response bytes (data and SW1-SW2)
     * @throws CardException if the card or the reader fails
     */
    byte[] transmit(CommandAPDU command) throws CardException {
        int cla = command.getCLA();
        if (command.getINS() == INS_MANAGE_CHANNEL && ClassByte.isInterindustry(cla)) {
            if (ClassByte.indicatesSecureMessaging(cla)) {
                throw new UnsupportedOperationException(String.format("MANAGE CHANNEL with CLA '%02X' (secure"
                        + " messaging) is not supported: javax.smartcardio only opens and closes channels with"
                        + " plain commands, which would replace the protected command", cla));
            }
            return manageChannel(command);
        }
        return channel(ClassByte.channel(cla)).transmit(command).getBytes();
    }

    /** Closes every channel opened through this connection, ignoring failures (the connection ends anyway). */
    void closeAll() {
        List<CardChannel> channels = new ArrayList<>(opened.values());
        opened.clear();
        for (CardChannel channel : channels) {
            try {
                channel.close();
            } catch (CardException | RuntimeException e) {
                // best effort: the card closes all logical channels on its next reset
            }
        }
    }

    private CardChannel channel(int number) {
        if (number == 0) {
            return card.getBasicChannel();
        }
        CardChannel channel = opened.get(number);
        if (channel == null) {
            throw new UnsupportedOperationException("Logical channel " + number + " is not open in this"
                    + " session: javax.smartcardio can only address channels opened through it"
                    + " (Card.openLogicalChannel); open the channel with LogicalChannel.open(session)");
        }
        return channel;
    }

    /** ISO/IEC 7816-4:2005 7.1.2, Table 41. */
    private byte[] manageChannel(CommandAPDU command) throws CardException {
        int p1 = command.getP1();
        int p2 = command.getP2();
        if (p1 == P1_OPEN && p2 == 0) {
            return open(command.getCLA());
        }
        if (p1 == P1_OPEN && p2 <= ClassByte.MAX_CHANNEL) {
            throw new UnsupportedOperationException("MANAGE CHANNEL OPEN of channel " + p2 + " is not supported:"
                    + " javax.smartcardio only opens channels numbered by the card (P2 = '00'); use"
                    + " LogicalChannel.open(session)");
        }
        if (p1 == P1_CLOSE && p2 <= ClassByte.MAX_CHANNEL) {
            return close(p2 != 0 ? p2 : ClassByte.channel(command.getCLA()));
        }
        throw new IllegalArgumentException(String.format("MANAGE CHANNEL with P1-P2 '%02X%02X' is reserved for"
                + " future use (ISO/IEC 7816-4:2005 7.1.2)", p1, p2));
    }

    private byte[] open(int cla) throws CardException {
        if (ClassByte.channel(cla) != 0) {
            throw new UnsupportedOperationException(String.format("MANAGE CHANNEL OPEN from logical channel %d"
                    + " is not supported: javax.smartcardio opens channels from the basic channel",
                    ClassByte.channel(cla)));
        }
        CardChannel channel = card.openLogicalChannel();
        int number = channel.getChannelNumber();
        opened.put(number, channel);
        return new byte[]{(byte) number, (byte) 0x90, 0x00};
    }

    private byte[] close(int number) throws CardException {
        if (number == 0) {
            throw new IllegalArgumentException("The basic channel cannot be closed (ISO/IEC 7816-4:2005 5.1.1.2)");
        }
        CardChannel channel = opened.remove(number);
        if (channel == null) {
            throw new UnsupportedOperationException("Logical channel " + number + " is not open in this session");
        }
        channel.close();
        return new byte[]{(byte) 0x90, 0x00};
    }
}
