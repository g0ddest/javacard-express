package name.velikodniy.jcexpress.container;

/**
 * Wire protocol constants, client-side copy of {@code name.velikodniy.jcexpress.server.Protocol} in the
 * {@code docker/} project (keep both in sync).
 *
 * <p>Request frame {@code [u1 command][s4 length][payload]}, reply frame {@code [u1 status][s4 length][payload]},
 * big-endian, one reply per request. Error replies carry UTF-8 {@code key: value} lines (see
 * {@link RemoteError}).</p>
 */
final class Protocol {

    /** TCP port the server listens on inside the container. */
    static final int PORT = 9876;

    /** Largest frame payload either side accepts (16 MiB). */
    static final int MAX_PAYLOAD = 16 * 1024 * 1024;

    /**
     * Java Card API, {@code Applet.install(byte[] bArray, short bOffset, byte bLength)}: "The maximum value of
     * bLength is 127."
     */
    static final int MAX_INSTALL_PARAMS = 127;

    /** Install and select an applet; reply = R-APDU of the SELECT. */
    static final byte CMD_INSTALL = 0x01;
    /** Select an applet by AID; reply = R-APDU of the SELECT. */
    static final byte CMD_SELECT = 0x02;
    /** Send a C-APDU; reply = R-APDU. */
    static final byte CMD_TRANSMIT = 0x03;
    /** Wipe the card (all applets deleted). */
    static final byte CMD_RESET = 0x04;
    /** Liveness check; reply = {@code 01}. */
    static final byte CMD_PING = 0x05;
    /** Card reset: applets and persistent state kept, CLEAR_ON_RESET memory cleared, nothing selected. */
    static final byte CMD_CARD_RESET = 0x06;
    /** Authenticate with the access token (UTF-8 payload); first request when the server has a token. */
    static final byte CMD_HELLO = 0x07;

    /** Reply status: success. */
    static final byte STATUS_OK = 0x00;
    /** Reply status: failure; payload = error report. */
    static final byte STATUS_ERROR = 0x01;

    private Protocol() {
    }
}
