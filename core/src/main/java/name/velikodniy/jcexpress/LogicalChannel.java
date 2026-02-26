package name.velikodniy.jcexpress;

/**
 * ISO 7816-4 logical channel abstraction.
 *
 * <p>Logical channels allow multiple independent application sessions
 * on a single smart card. Each channel has its own selected applet
 * and security state. The channel number is encoded in the CLA byte
 * (bits [1:0] for basic channels 0-3).</p>
 *
 * <h2>Usage — basic channel (no MANAGE CHANNEL):</h2>
 * <pre>
 * LogicalChannel ch1 = LogicalChannel.basic(card, 1);
 * ch1.send(0x00, 0xA4, 0x04, 0x00, aid);  // CLA becomes 0x01
 * </pre>
 *
 * <h2>Usage — managed channel (MANAGE CHANNEL open/close):</h2>
 * <pre>
 * try (LogicalChannel ch = LogicalChannel.open(card)) {
 *     ch.select(AID.fromHex("A0000000031010"));
 *     APDUResponse r = ch.send(0x00, 0x01);
 * }  // auto-close sends MANAGE CHANNEL CLOSE
 * </pre>
 *
 * @see SmartCardSession
 */
public final class LogicalChannel implements AutoCloseable {

    /** MANAGE CHANNEL instruction (ISO 7816-4). */
    private static final int INS_MANAGE_CHANNEL = 0x70;

    private final SmartCardSession session;
    private final int channelNumber;
    private final boolean managed;

    private LogicalChannel(SmartCardSession session, int channelNumber, boolean managed) {
        this.session = session;
        this.channelNumber = channelNumber;
        this.managed = managed;
    }

    /**
     * Creates a logical channel wrapper without sending MANAGE CHANNEL.
     *
     * <p>Use this for basic channels (0-3) when the card already has the
     * channel open or when the card doesn't support MANAGE CHANNEL.
     * Calling {@link #close()} on a basic channel is a no-op.</p>
     *
     * @param session the underlying smart card session
     * @param channel the channel number (0-3)
     * @return a logical channel wrapper
     * @throws IllegalArgumentException if channel is out of range
     */
    public static LogicalChannel basic(SmartCardSession session, int channel) {
        validateChannel(channel);
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        return new LogicalChannel(session, channel, false);
    }

    /**
     * Opens a new logical channel via MANAGE CHANNEL OPEN.
     *
     * <p>The card assigns the channel number. The response data byte
     * contains the assigned channel number.</p>
     *
     * @param session the underlying smart card session
     * @return a managed logical channel (auto-closes on {@link #close()})
     * @throws IllegalStateException if MANAGE CHANNEL fails
     */
    public static LogicalChannel open(SmartCardSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        // MANAGE CHANNEL OPEN: CLA=0x00, INS=0x70, P1=0x00, P2=0x00
        APDUResponse r = session.send(0x00, INS_MANAGE_CHANNEL, 0x00, 0x00);
        if (!r.isSuccess()) {
            throw new IllegalStateException(
                    "MANAGE CHANNEL OPEN failed: SW=" + String.format("%04X", r.sw()));
        }
        byte[] data = r.data();
        if (data == null || data.length < 1) {
            throw new IllegalStateException("MANAGE CHANNEL OPEN: no channel number in response");
        }
        int assigned = data[0] & 0xFF;
        return new LogicalChannel(session, assigned, true);
    }

    /**
     * Opens a specific logical channel via MANAGE CHANNEL OPEN.
     *
     * @param session the underlying smart card session
     * @param channel the requested channel number (1-3)
     * @return a managed logical channel (auto-closes on {@link #close()})
     * @throws IllegalArgumentException if channel is 0 (basic channel can't be opened)
     * @throws IllegalStateException    if MANAGE CHANNEL fails
     */
    public static LogicalChannel open(SmartCardSession session, int channel) {
        if (channel == 0) {
            throw new IllegalArgumentException("Cannot open channel 0 via MANAGE CHANNEL");
        }
        validateChannel(channel);
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        // MANAGE CHANNEL OPEN specific: CLA=0x00, INS=0x70, P1=0x00, P2=channel
        APDUResponse r = session.send(0x00, INS_MANAGE_CHANNEL, 0x00, channel);
        if (!r.isSuccess()) {
            throw new IllegalStateException(
                    "MANAGE CHANNEL OPEN failed for channel " + channel
                            + ": SW=" + String.format("%04X", r.sw()));
        }
        return new LogicalChannel(session, channel, true);
    }

    /**
     * Returns the logical channel number.
     *
     * @return the channel number (0-3)
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
     * Sends an APDU on this logical channel.
     *
     * <p>The CLA byte is modified to encode the channel number in bits [1:0].</p>
     *
     * @param cla the CLA byte (channel bits will be overwritten)
     * @param ins the INS byte
     * @return the response
     */
    public APDUResponse send(int cla, int ins) {
        return session.send(encodeCla(cla, channelNumber), ins);
    }

    /**
     * Sends an APDU on this logical channel.
     *
     * @param cla the CLA byte (channel bits will be overwritten)
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return session.send(encodeCla(cla, channelNumber), ins, p1, p2);
    }

    /**
     * Sends an APDU on this logical channel.
     *
     * @param cla  the CLA byte (channel bits will be overwritten)
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data
     * @return the response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return session.send(encodeCla(cla, channelNumber), ins, p1, p2, data);
    }

    /**
     * Sends an APDU on this logical channel.
     *
     * @param cla  the CLA byte (channel bits will be overwritten)
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @param le   the expected response length
     * @return the response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        return session.send(encodeCla(cla, channelNumber), ins, p1, p2, data, le);
    }

    /**
     * Sends raw APDU bytes on this logical channel.
     *
     * <p>The first byte (CLA) is modified to encode the channel number.</p>
     *
     * @param rawApdu the raw APDU bytes
     * @return the raw response bytes
     */
    public byte[] transmit(byte[] rawApdu) {
        if (rawApdu == null || rawApdu.length < 4) {
            throw new IllegalArgumentException("APDU must be at least 4 bytes");
        }
        byte[] modified = rawApdu.clone();
        modified[0] = (byte) encodeCla(modified[0] & 0xFF, channelNumber);
        return session.transmit(modified);
    }

    /**
     * Selects an applet by AID on this logical channel.
     *
     * @param aid the AID to select
     * @return the SELECT response
     */
    public APDUResponse select(AID aid) {
        return send(0x00, 0xA4, 0x04, 0x00, aid.toBytes());
    }

    /**
     * Closes this logical channel.
     *
     * <p>If the channel was opened via {@link #open(SmartCardSession)}, sends
     * MANAGE CHANNEL CLOSE. For channels created with {@link #basic}, this is a no-op.</p>
     */
    @Override
    public void close() {
        if (!managed) {
            return;
        }
        // MANAGE CHANNEL CLOSE: CLA with channel, INS=0x70, P1=0x80, P2=channel
        session.send(encodeCla(0x00, channelNumber), INS_MANAGE_CHANNEL, 0x80, channelNumber);
    }

    /**
     * Encodes a logical channel number into a CLA byte.
     *
     * <p>Per ISO 7816-4, basic logical channel numbers (0-3) are encoded
     * in bits [1:0] of the CLA byte. All other CLA bits are preserved.</p>
     *
     * @param cla     the original CLA byte
     * @param channel the channel number (0-3)
     * @return the CLA byte with channel encoded
     */
    public static int encodeCla(int cla, int channel) {
        if (channel == 0) {
            return cla;
        }
        return (cla & 0xFC) | (channel & 0x03);
    }

    private static void validateChannel(int channel) {
        if (channel < 0 || channel > 3) {
            throw new IllegalArgumentException(
                    "Basic logical channel must be 0-3, got: " + channel);
        }
    }
}
