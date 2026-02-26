package name.velikodniy.jcexpress.server;

/**
 * TCP protocol constants shared between server and client.
 */
final class Protocol {

    static final int PORT = 9876;

    // Commands
    static final byte CMD_INSTALL = 0x01;
    static final byte CMD_SELECT = 0x02;
    static final byte CMD_TRANSMIT = 0x03;
    static final byte CMD_RESET = 0x04;
    static final byte CMD_PING = 0x05;

    // Response status
    static final byte STATUS_OK = 0x00;
    static final byte STATUS_ERROR = 0x01;

    private Protocol() {
    }
}
