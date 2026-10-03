package name.velikodniy.jcexpress.embedded;

import name.velikodniy.jcexpress.apdu.ClassByte;

import javax.smartcardio.CommandAPDU;

/**
 * The commands an {@link EmbeddedSession} accepts: command APDUs (ISO/IEC 7816-4:2005 5.1) for the basic logical
 * channel, the only channel jCardSim implements.
 */
final class BasicChannel {

    /** MANAGE CHANNEL instruction (ISO/IEC 7816-4:2005 7.1.2). */
    private static final int INS_MANAGE_CHANNEL = 0x70;

    private BasicChannel() {
    }

    /**
     * Checks a command for jCardSim.
     *
     * @param rawApdu the command as the test gave it
     * @return the command bytes
     * @throws IllegalArgumentException      if the bytes are not a command APDU (ISO/IEC 7816-4:2005 5.1)
     * @throws UnsupportedOperationException if the command addresses another logical channel or is MANAGE CHANNEL
     */
    static byte[] checked(byte[] rawApdu) {
        byte[] command = new CommandAPDU(rawApdu).getBytes();
        rejectLogicalChannels(command);
        return command;
    }

    /**
     * jCardSim implements only the basic logical channel: it ignores the channel bits of CLA, so the command
     * would reach the applet selected on the basic channel, and it has no MANAGE CHANNEL. ISO/IEC 7816-4:2005
     * 5.1.1.2 gives every channel its own selection and security status, so such commands are rejected.
     */
    private static void rejectLogicalChannels(byte[] command) {
        int cla = command[0] & 0xFF;
        if ((command[1] & 0xFF) == INS_MANAGE_CHANNEL && ClassByte.isInterindustry(cla)) {
            throw new UnsupportedOperationException("MANAGE CHANNEL is not supported by EmbeddedSession: jCardSim"
                    + " implements only the basic logical channel");
        }
        int channel = ClassByte.channel(cla);
        if (channel != 0) {
            throw new UnsupportedOperationException(String.format("CLA '%02X' addresses logical channel %d, but"
                    + " EmbeddedSession (jCardSim) implements only the basic channel and would deliver the command"
                    + " to the applet selected there", cla, channel));
        }
    }
}
