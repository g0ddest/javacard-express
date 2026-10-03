package name.velikodniy.jcexpress.livecard.guard;

/**
 * Why the {@link ApduGuard} no longer knows the selected application of a channel, worded for the reason of a later
 * block: the command after which the selection became unknown, or the event. The guard keeps its rule (a SELECT or
 * MANAGE CHANNEL outside the plain inter-industry classes answered with success may have changed the selection,
 * ISO/IEC 7816-4:2005 7.1.1, 7.1.2), but a test whose applet uses these INS values for its own commands learns which
 * command caused the block and what to change.
 */
final class SelectionLoss {

    private static final String WAY_OUT = "; if it is the applet's own command, give it another INS, or select the"
            + " applet again (card.select(...)) before its next command";

    private SelectionLoss() {
    }

    /**
     * Adds to the reason of a block why the channel's selection is unknown, when it is: the strict rules for an
     * unknown application then blocked the command because of it.
     *
     * @param reason  why the command was blocked
     * @param channel the state of the command's channel, or null
     * @return the reason, with the cause when there is one
     */
    static String explain(String reason, ChannelState channel) {
        String cause = channel == null ? null : channel.unknownSince();
        return cause == null ? reason : reason + "; " + cause;
    }

    /**
     * After a SELECT by DF name in a class other than the plain inter-industry ones, answered with success.
     *
     * @param command the command
     * @param channel the logical channel of the command
     * @param sw      the status word
     * @return the cause
     */
    static String select(byte[] command, int channel, int sw) {
        return String.format("channel %d became unknown after %s (answered %04X), which %s the guard has to treat as"
                + " a SELECT by DF name (ISO/IEC 7816-4:2005 7.1.1) that may have selected another application",
                channel, header(command), sw, inClass(command[0] & 0xFF)) + WAY_OUT;
    }

    /**
     * After a MANAGE CHANNEL in a class other than the plain inter-industry ones, answered '9000'.
     *
     * @param command the command
     * @param sw      the status word
     * @return the cause
     */
    static String manageChannel(byte[] command, int sw) {
        return String.format("the channels became unknown after %s (answered %04X), which %s the guard has to treat"
                + " as MANAGE CHANNEL (ISO/IEC 7816-4:2005 7.1.2) that may have changed the selection on any channel",
                header(command), sw, inClass(command[0] & 0xFF)) + WAY_OUT;
    }

    /** After a card reset: the card selects its default application on the basic channel (ISO/IEC 7816-4 5.1.1.2). */
    static final String RESET = "the basic channel is back to the card's default application since the card reset"
            + " (ISO/IEC 7816-4:2005 5.1.1.2); select the applet again (card.select(...)) before its next command";

    /** After a transport failure: whether the card processed the command is unknown. */
    static final String TRANSPORT_FAILURE = "the channels became unknown after a transport failure (whether the card"
            + " processed the command is unknown); select the applet again before its next command";

    /**
     * After a SELECT by DF name in a plain class that did not establish the context of a test applet or the ISD.
     *
     * @param aid      the AID of the SELECT
     * @param channel  the logical channel
     * @param sw       the status word
     * @param standard whether the SELECT had the standard form the guard follows ({@link ClassBytes#standardSelect})
     * @return the cause
     */
    static String otherSelection(String aid, int channel, int sw, boolean standard) {
        if (sw != 0x9000 && (sw >> 8) != 0x61) {
            return String.format("the SELECT of %s on channel %d was answered %04X", aid, channel, sw);
        }
        return standard ? String.format("channel %d has %s selected, which is neither a test applet (an AID under"
                + " the prefix) nor the Issuer Security Domain", channel, aid)
                : String.format("the SELECT of %s on channel %d was not in the form the guard follows (P1 04, P2 00,"
                + " a 5-16 byte AID, no extended length)", aid, channel);
    }

    private static String inClass(int cla) {
        return ClassBytes.proprietary(cla) ? "in a proprietary class"
                : String.format("in class %02X (secure messaging, command chaining or a reserved class)", cla);
    }

    /** The header bytes CLA INS P1 P2, spaced. */
    private static String header(byte[] command) {
        return String.format("%02X %02X %02X %02X", command[0] & 0xFF, command[1] & 0xFF, command[2] & 0xFF,
                command[3] & 0xFF);
    }
}
