package name.velikodniy.jcexpress;

/**
 * A single APDU exchange log entry: command sent and response received.
 *
 * <p>Used by {@link LoggingSession} to record APDU traffic for
 * programmatic access and debugging.</p>
 *
 * @param command     the raw APDU command bytes sent to the card
 * @param response    the response received from the card
 * @param timestampMs the timestamp when the command was sent (milliseconds since epoch)
 */
public record APDULogEntry(
        byte[] command,
        APDUResponse response,
        long timestampMs
) {

    /**
     * Returns the command as a spaced hex string.
     *
     * @return spaced hex (e.g., "00 A4 04 00 07 A0 00 00 00 03 10 10")
     */
    public String commandHex() {
        return Hex.encodeSpaced(command);
    }

    /**
     * Returns the CLA byte of the command.
     *
     * @return the CLA byte (0-255)
     */
    public int cla() {
        return command.length > 0 ? command[0] & 0xFF : 0;
    }

    /**
     * Returns the INS byte of the command.
     *
     * @return the INS byte (0-255)
     */
    public int ins() {
        return command.length > 1 ? command[1] & 0xFF : 0;
    }

    /**
     * Returns true if the response status word is 0x9000 (success).
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return response != null && response.isSuccess();
    }

    @Override
    public String toString() {
        String sw = response != null ? String.format("%04X", response.sw()) : "????";
        return ">> " + commandHex() + " << [" + sw + "]";
    }
}
