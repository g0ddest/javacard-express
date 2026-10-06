package name.velikodniy.jcexpress;

import java.time.Duration;

/**
 * A single APDU exchange: the command sent, the response received, when the command was sent and how long the
 * exchange took.
 *
 * <p>The sessions of JavaCard Express record their exchanges in their {@link SmartCardSession#history()}
 * ({@link APDUHistory#entries()}, {@link APDUHistory#last()}); {@link LoggingSession} keeps entries of its own.</p>
 *
 * <p>The duration runs from just before the command is sent until the response has arrived. On a card in a PC/SC
 * reader it includes the driver, the reader and the exchanges that the JDK's PC/SC provider adds below the session
 * (GET RESPONSE after {@code 61XX}, the command again after {@code 6CXX}); on jCardSim (the embedded and the
 * simulated GlobalPlatform backends) it is the time of the simulator in the test JVM, not the time a card would
 * take.</p>
 *
 * @param command     the raw APDU command bytes sent to the card
 * @param response    the response received from the card
 * @param timestampMs when the command was sent (milliseconds since the epoch)
 * @param duration    how long the exchange took, or {@code null} if the session that recorded it did not measure it
 */
public record APDULogEntry(
        byte[] command,
        APDUResponse response,
        long timestampMs,
        Duration duration
) {

    /**
     * Creates an entry without a measured duration.
     *
     * @param command     the raw APDU command bytes sent to the card
     * @param response    the response received from the card
     * @param timestampMs when the command was sent (milliseconds since the epoch)
     */
    public APDULogEntry(byte[] command, APDUResponse response, long timestampMs) {
        this(command, response, timestampMs, null);
    }

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

    /**
     * Returns the exchange in the transcript format ({@link TranscriptFormat}): the {@code C:} line and the
     * {@code R:} line with the time of the exchange if it was measured, separated by a line break.
     *
     * @return e.g. {@code "C: 8052000002\nR: 00649000  (12.3 ms)"}
     */
    public String transcript() {
        byte[] received = response != null ? response.toBytes() : new byte[0];
        return TranscriptFormat.command(command) + "\n" + TranscriptFormat.response(received, duration);
    }

    @Override
    public String toString() {
        String sw = response != null ? String.format("%04X", response.sw()) : "????";
        String time = duration != null ? " (" + TranscriptFormat.milliseconds(duration) + ")" : "";
        return ">> " + commandHex() + " << [" + sw + "]" + time;
    }
}
