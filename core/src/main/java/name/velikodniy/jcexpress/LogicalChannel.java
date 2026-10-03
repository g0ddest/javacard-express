package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.apdu.ClassByte;

/**
 * ISO/IEC 7816-4 logical channel on top of a {@link SmartCardSession}.
 *
 * <p>Logical channels allow multiple independent application sessions on one card; each channel has its
 * own selected application and security status (ISO/IEC 7816-4:2005 5.1.1.2). A command refers to a
 * channel through its class byte: channels 0 to 3 in b2-b1 (Table 2), channels 4 to 19 in the further
 * interindustry coding {@code 01xx xxxx} (Table 3); the GlobalPlatform proprietary class uses the same
 * layouts with b8 = 1 (GlobalPlatform Card Specification 2.3.1 11.1.4). See {@link ClassByte}.</p>
 *
 * <h2>Backends</h2>
 * <ul>
 *   <li>{@link name.velikodniy.jcexpress.pcsc.PcscSession}: channels opened with {@link #open(SmartCardSession)}
 *       (MANAGE CHANNEL OPEN, the card assigns the number) are mapped to {@code javax.smartcardio} channels.</li>
 *   <li>{@link name.velikodniy.jcexpress.embedded.EmbeddedSession}: jCardSim has only the basic channel; commands
 *       for other channels and MANAGE CHANNEL are rejected instead of reaching the basic channel.</li>
 * </ul>
 *
 * <h2>Usage &mdash; managed channel (MANAGE CHANNEL open/close):</h2>
 * <pre>
 * try (LogicalChannel ch = LogicalChannel.open(card)) {
 *     ch.select(AID.fromHex("A0000000031010"));
 *     APDUResponse r = ch.send(0x80, 0x01);
 * }  // close() sends MANAGE CHANNEL CLOSE
 * </pre>
 *
 * <h2>Usage &mdash; channel opened elsewhere (no MANAGE CHANNEL):</h2>
 * <pre>
 * LogicalChannel ch1 = LogicalChannel.basic(card, 1);
 * ch1.send(0x00, 0xA4, 0x04, 0x00, aid);  // CLA becomes 0x01
 * </pre>
 *
 * @see SmartCardSession
 */
public final class LogicalChannel implements AutoCloseable {

    /** MANAGE CHANNEL instruction (ISO/IEC 7816-4:2005 7.1.2). */
    private static final int INS_MANAGE_CHANNEL = 0x70;

    /** P1 of MANAGE CHANNEL close function. */
    private static final int P1_CLOSE = 0x80;

    private final SmartCardSession session;
    private final int channelNumber;
    private final boolean managed;
    private boolean closed;

    private LogicalChannel(SmartCardSession session, int channelNumber, boolean managed) {
        this.session = session;
        this.channelNumber = channelNumber;
        this.managed = managed;
    }

    /**
     * Creates a logical channel wrapper without sending MANAGE CHANNEL.
     *
     * <p>Use this for the basic channel (0) or a channel the card already has open. Calling
     * {@link #close()} sends nothing.</p>
     *
     * @param session the underlying smart card session
     * @param channel the channel number (0-19)
     * @return a logical channel wrapper
     * @throws IllegalArgumentException if the session is null or the channel is out of range
     */
    public static LogicalChannel basic(SmartCardSession session, int channel) {
        requireSession(session);
        validateChannel(channel, 0);
        return new LogicalChannel(session, channel, false);
    }

    /**
     * Opens a new logical channel with MANAGE CHANNEL OPEN, letting the card assign its number
     * (ISO/IEC 7816-4:2005 7.1.2: P1-P2 = '0000', Le = '01', the response data is the channel number
     * '01' to '13').
     *
     * @param session the underlying smart card session
     * @return a managed logical channel (closed by {@link #close()})
     * @throws IllegalArgumentException if the session is null
     * @throws IllegalStateException    if the card rejects the command or does not return a channel number
     */
    public static LogicalChannel open(SmartCardSession session) {
        requireSession(session);
        APDUResponse r = session.send(0x00, INS_MANAGE_CHANNEL, 0x00, 0x00, null, 1);
        if (!r.isSuccess()) {
            throw new IllegalStateException(String.format("MANAGE CHANNEL OPEN failed: SW=%04X", r.sw()));
        }
        byte[] data = r.data();
        int assigned = data.length == 1 ? data[0] & 0xFF : -1;
        if (assigned < 1 || assigned > ClassByte.MAX_CHANNEL) {
            throw new IllegalStateException("MANAGE CHANNEL OPEN: expected one byte '01' to '13' with the"
                    + " assigned channel number (ISO/IEC 7816-4:2005 7.1.2), got: " + Hex.encode(data));
        }
        return new LogicalChannel(session, assigned, true);
    }

    /**
     * Opens the given logical channel with MANAGE CHANNEL OPEN (ISO/IEC 7816-4:2005 7.1.2: P2 is the channel
     * number, the Le field is absent).
     *
     * @param session the underlying smart card session
     * @param channel the channel number to open (1-19)
     * @return a managed logical channel (closed by {@link #close()})
     * @throws IllegalArgumentException if the session is null or the channel is 0 or out of range
     * @throws IllegalStateException    if the card rejects the command
     */
    public static LogicalChannel open(SmartCardSession session, int channel) {
        requireSession(session);
        if (channel == 0) {
            throw new IllegalArgumentException("Cannot open channel 0 (the basic channel) via MANAGE CHANNEL");
        }
        validateChannel(channel, 1);
        APDUResponse r = session.send(0x00, INS_MANAGE_CHANNEL, 0x00, channel);
        if (!r.isSuccess()) {
            throw new IllegalStateException(String.format("MANAGE CHANNEL OPEN failed for channel %d: SW=%04X",
                    channel, r.sw()));
        }
        return new LogicalChannel(session, channel, true);
    }

    /**
     * Returns the logical channel number.
     *
     * @return the channel number (0-19)
     */
    public int channelNumber() {
        return channelNumber;
    }

    /**
     * Returns the underlying smart card session.
     *
     * @return the session
     */
    public SmartCardSession session() {
        return session;
    }

    /**
     * Returns true if this channel was opened via MANAGE CHANNEL.
     *
     * @return true if managed (will send CLOSE on {@link #close()})
     */
    public boolean isManaged() {
        return managed;
    }

    // ── APDU dispatch ──

    /**
     * Sends a command on this logical channel (no data, no Le field).
     *
     * @param cla the CLA byte; its channel bits are replaced (see {@link #encodeCla(int, int)})
     * @param ins the INS byte
     * @return the response
     * @throws IllegalStateException if this channel has been closed
     */
    public APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0, null, SmartCardSession.NO_LE);
    }

    /**
     * Sends a command on this logical channel (no data, no Le field).
     *
     * @param cla the CLA byte; its channel bits are replaced
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the response
     * @throws IllegalStateException if this channel has been closed
     */
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, SmartCardSession.NO_LE);
    }

    /**
     * Sends a command with data on this logical channel (no Le field).
     *
     * @param cla  the CLA byte; its channel bits are replaced
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @return the response
     * @throws IllegalStateException if this channel has been closed
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, SmartCardSession.NO_LE);
    }

    /**
     * Sends a command on this logical channel.
     *
     * @param cla  the CLA byte; its channel bits are replaced
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @param le   Ne as defined by {@link SmartCardSession#send(int, int, int, int, byte[], int)}
     * @return the response
     * @throws IllegalStateException if this channel has been closed
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        ensureOpen();
        return session.send(encodeCla(cla, channelNumber), ins, p1, p2, data, le);
    }

    /**
     * Sends raw APDU bytes on this logical channel; the channel bits of the first byte (CLA) are replaced.
     *
     * @param rawApdu the raw APDU bytes
     * @return the raw response bytes
     * @throws IllegalArgumentException if the APDU is shorter than 4 bytes
     * @throws IllegalStateException    if this channel has been closed
     */
    public byte[] transmit(byte[] rawApdu) {
        if (rawApdu == null || rawApdu.length < 4) {
            throw new IllegalArgumentException("APDU must be at least 4 bytes");
        }
        ensureOpen();
        byte[] modified = rawApdu.clone();
        modified[0] = (byte) encodeCla(modified[0] & 0xFF, channelNumber);
        return session.transmit(modified);
    }

    /**
     * Selects an applet by AID on this logical channel: SELECT by DF name (ISO/IEC 7816-4:2005 7.1.1) with
     * Le '00', as GlobalPlatform Card Specification 2.3.1 Table 11-79 defines the command.
     *
     * @param aid the AID to select
     * @return the SELECT response
     * @throws IllegalStateException if this channel has been closed
     */
    public APDUResponse select(AID aid) {
        return send(0x00, 0xA4, 0x04, 0x00, aid.toBytes(), 256);
    }

    /**
     * Closes this logical channel.
     *
     * <p>A channel opened with {@link #open(SmartCardSession)} sends MANAGE CHANNEL CLOSE on the channel,
     * with the channel number in P2 (ISO/IEC 7816-4:2005 7.1.2). Channels created with {@link #basic} send
     * nothing. Closing twice has no further effect.</p>
     *
     * @throws IllegalStateException if the card rejects MANAGE CHANNEL CLOSE
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!managed) {
            return;
        }
        APDUResponse r = session.send(encodeCla(0x00, channelNumber), INS_MANAGE_CHANNEL, P1_CLOSE, channelNumber);
        if (!r.isSuccess()) {
            throw new IllegalStateException(String.format("MANAGE CHANNEL CLOSE of channel %d failed: SW=%04X",
                    channelNumber, r.sw()));
        }
    }

    /**
     * Encodes a logical channel number into a CLA byte.
     *
     * <p>Channels 0 to 3 use b2-b1 of the first interindustry coding, channels 4 to 19 the further
     * interindustry coding (ISO/IEC 7816-4:2005 5.1.1 Tables 2 and 3; GlobalPlatform Card Specification
     * 2.3.1 Tables 11-11 and 11-12 for the proprietary class). Existing channel bits are replaced; the class,
     * chaining and secure messaging indications are kept. See {@link ClassByte#withChannel(int, int)}.</p>
     *
     * @param cla     the original CLA byte
     * @param channel the channel number (0-19)
     * @return the CLA byte with channel encoded
     * @throws IllegalArgumentException if the channel is out of range or the CLA codes no channel
     */
    public static int encodeCla(int cla, int channel) {
        return ClassByte.withChannel(cla, channel);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Logical channel " + channelNumber + " has been closed");
        }
    }

    private static void requireSession(SmartCardSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
    }

    private static void validateChannel(int channel, int min) {
        if (channel < min || channel > ClassByte.MAX_CHANNEL) {
            throw new IllegalArgumentException("Logical channel must be " + min + "-" + ClassByte.MAX_CHANNEL
                    + " (ISO/IEC 7816-4:2005 5.1.1.2), got: " + channel);
        }
    }
}
