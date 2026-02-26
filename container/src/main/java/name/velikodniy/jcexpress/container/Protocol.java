package name.velikodniy.jcexpress.container;

/**
 * TCP protocol constants (client-side copy).
 */
final class Protocol {

    static final int PORT = 9876;

    static final byte CMD_INSTALL = 0x01;
    static final byte CMD_SELECT = 0x02;
    static final byte CMD_TRANSMIT = 0x03;
    static final byte CMD_RESET = 0x04;
    static final byte CMD_PING = 0x05;

    static final byte STATUS_OK = 0x00;

    private Protocol() {
    }
}
