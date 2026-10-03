package name.velikodniy.jcexpress.server;

/**
 * Wire protocol between {@code ContainerSession} (client) and the simulator server.
 *
 * <p>The client-side copy is {@code name.velikodniy.jcexpress.container.Protocol}; both must stay in sync.</p>
 *
 * <ul>
 *   <li>Request frame: {@code [u1 command][s4 payload length][payload]}.</li>
 *   <li>Reply frame: {@code [u1 status][s4 payload length][payload]}; exactly one reply per request.</li>
 *   <li>Integers are big-endian. A payload length outside {@code 0..}{@link #MAX_PAYLOAD} is a framing error:
 *       the server answers with {@link #STATUS_ERROR} and closes the connection.</li>
 *   <li>An error reply carries UTF-8 text made of {@code key: value} lines (see {@link ErrorReport}).</li>
 * </ul>
 */
final class Protocol {

    /** Default TCP port. */
    static final int PORT = 9876;

    /** Largest accepted request payload (16 MiB): bounds the allocation a single frame can cause. */
    static final int MAX_PAYLOAD = 16 * 1024 * 1024;

    /**
     * Largest install parameter block: Java Card API, {@code Applet.install(byte[] bArray, short bOffset,
     * byte bLength)}: "The maximum value of bLength is 127."
     */
    static final int MAX_INSTALL_PARAMS = 127;

    /**
     * Install an applet and select it; payload layout in {@link InstallRequest}; reply = the R-APDU of the
     * SELECT command sent after the installation.
     */
    static final byte CMD_INSTALL = 0x01;
    /** Select an applet by AID; payload = AID bytes; reply = the R-APDU of the SELECT command. */
    static final byte CMD_SELECT = 0x02;
    /** Send a command APDU; payload = C-APDU bytes; reply = R-APDU bytes (data + SW). */
    static final byte CMD_TRANSMIT = 0x03;
    /** Wipe the card: every applet and its state is deleted (jCardSim {@code resetRuntime}). */
    static final byte CMD_RESET = 0x04;
    /** Liveness check; reply payload = {@code 01}. */
    static final byte CMD_PING = 0x05;
    /**
     * Card reset, as if the card were removed and reinserted: installed applets and their persistent objects are
     * kept, {@code CLEAR_ON_RESET} transient memory is cleared and no applet is selected (jCardSim {@code reset}).
     */
    static final byte CMD_CARD_RESET = 0x06;
    /**
     * Authenticate the connection: payload = the access token (UTF-8); reply payload empty. When the server has a
     * token ({@code JCX_TOKEN}) this must be the first request of every connection; a missing or wrong token is
     * answered with an error and the connection is closed.
     */
    static final byte CMD_HELLO = 0x07;

    /** Reply status: success. */
    static final byte STATUS_OK = 0x00;
    /** Reply status: failure, payload = error report text. */
    static final byte STATUS_ERROR = 0x01;

    private Protocol() {
    }

    /**
     * Returns a readable name of a command code for log and error messages.
     *
     * @param command the command code
     * @return the command name, or its hex value when unknown
     */
    static String commandName(int command) {
        switch (command) {
            case CMD_INSTALL: return "INSTALL";
            case CMD_SELECT: return "SELECT";
            case CMD_TRANSMIT: return "TRANSMIT";
            case CMD_RESET: return "RESET";
            case CMD_PING: return "PING";
            case CMD_CARD_RESET: return "CARD_RESET";
            case CMD_HELLO: return "HELLO";
            default: return String.format("0x%02X", command & 0xFF);
        }
    }
}
