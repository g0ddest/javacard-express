package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;

/**
 * Fluent builder for constructing ISO 7816-4 APDU commands.
 *
 * <p>Provides a readable, chainable API for building APDU command bytes,
 * avoiding positional parameter confusion common with raw
 * {@code send(cla, ins, p1, p2, data, le)} calls.</p>
 *
 * <p>Supports both short APDUs (data up to 255 bytes, Le up to 256)
 * and extended APDUs (data up to 65535 bytes, Le up to 65536).
 * The encoding format is chosen automatically based on data length and Le.</p>
 *
 * <h2>Basic usage:</h2>
 * <pre>
 * // Build raw bytes
 * byte[] apdu = APDUBuilder.command()
 *     .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
 *     .data("A0000000031010")
 *     .build();
 *
 * // Build and send in one step
 * APDUResponse response = APDUBuilder.command()
 *     .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
 *     .data("A0000000031010")
 *     .sendTo(session);
 * </pre>
 *
 * <h2>Convenience factory methods:</h2>
 * <pre>
 * // SELECT by AID
 * APDUResponse r = APDUBuilder.select("A0000000031010").sendTo(session);
 *
 * // GET DATA
 * APDUResponse r = APDUBuilder.getData(0x00, 0xCF).sendTo(session);
 *
 * // Custom CLA (e.g., GlobalPlatform)
 * APDUResponse r = APDUBuilder.command()
 *     .cla(0x80).ins(0xCA).p1(0x00).p2(0xCF)
 *     .le(0)
 *     .sendTo(session);
 * </pre>
 *
 * <h2>APDU structure (ISO 7816-4):</h2>
 * <pre>
 * Short:    CLA INS P1 P2 [Lc(1) Data(1-255)] [Le(1)]
 * Extended: CLA INS P1 P2 0x00 [Lc(2) Data(1-65535)] [Le(2)]
 * </pre>
 *
 * @see SmartCardSession#transmit(byte[])
 * @see SmartCardSession#send(int, int, int, int, byte[], int)
 */
public final class APDUBuilder {

    private int cla;
    private int ins;
    private int p1;
    private int p2;
    private byte[] data;
    private int le = -1;
    private int channel = -1;

    private APDUBuilder() {
    }

    /**
     * Creates a new empty APDU builder.
     *
     * <p>All fields default to 0 (CLA, INS, P1, P2), no data, no Le.</p>
     *
     * @return a new builder instance
     */
    public static APDUBuilder command() {
        return new APDUBuilder();
    }

    /**
     * Creates an APDU builder pre-configured for SELECT (INS=A4) by DF name.
     *
     * <p>Equivalent to:
     * {@code command().cla(0x00).ins(0xA4).p1(0x04).p2(0x00).data(aid)}</p>
     *
     * @param aidHex the AID as a hex string (e.g., "A0000000031010")
     * @return a builder ready to {@link #build()} or {@link #sendTo(SmartCardSession)}
     */
    public static APDUBuilder select(String aidHex) {
        return command()
                .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                .data(aidHex);
    }

    /**
     * Creates an APDU builder pre-configured for SELECT (INS=A4) by DF name.
     *
     * @param aidBytes the AID as a byte array
     * @return a builder ready to {@link #build()} or {@link #sendTo(SmartCardSession)}
     */
    public static APDUBuilder select(byte[] aidBytes) {
        return command()
                .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                .data(aidBytes);
    }

    /**
     * Creates an APDU builder pre-configured for GET DATA (INS=CA).
     *
     * <p>Uses CLA=0x00 per ISO 7816-4. For GlobalPlatform GET DATA (CLA=0x80),
     * use {@link #command()} and set CLA manually.</p>
     *
     * @param p1 the P1 byte (tag high byte)
     * @param p2 the P2 byte (tag low byte)
     * @return a builder ready to {@link #build()} or {@link #sendTo(SmartCardSession)}
     */
    public static APDUBuilder getData(int p1, int p2) {
        return command()
                .cla(0x00).ins(0xCA).p1(p1).p2(p2)
                .le(0);
    }

    /**
     * Creates an APDU builder pre-configured for GET RESPONSE (INS=C0).
     *
     * @param length the expected response length (Le)
     * @return a builder ready to {@link #build()} or {@link #sendTo(SmartCardSession)}
     */
    public static APDUBuilder getResponse(int length) {
        return command()
                .cla(0x00).ins(0xC0).p1(0x00).p2(0x00)
                .le(length);
    }

    /**
     * Sets the CLA (class) byte.
     *
     * @param cla the CLA byte: {@code 0x00} to {@code 0xFF}, or a {@code byte} constant ({@code -128} and up)
     * @return this builder
     * @throws IllegalArgumentException if cla is out of range
     */
    public APDUBuilder cla(int cla) {
        this.cla = APDUCodec.headerByte("CLA", cla);
        return this;
    }

    /**
     * Sets the INS (instruction) byte.
     *
     * @param ins the INS byte: {@code 0x00} to {@code 0xFF}, or a {@code byte} constant ({@code -128} and up)
     * @return this builder
     * @throws IllegalArgumentException if ins is out of range
     */
    public APDUBuilder ins(int ins) {
        this.ins = APDUCodec.headerByte("INS", ins);
        return this;
    }

    /**
     * Sets the P1 (parameter 1) byte.
     *
     * @param p1 the P1 byte: {@code 0x00} to {@code 0xFF}, or a {@code byte} constant ({@code -128} and up)
     * @return this builder
     * @throws IllegalArgumentException if p1 is out of range
     */
    public APDUBuilder p1(int p1) {
        this.p1 = APDUCodec.headerByte("P1", p1);
        return this;
    }

    /**
     * Sets the P2 (parameter 2) byte.
     *
     * @param p2 the P2 byte: {@code 0x00} to {@code 0xFF}, or a {@code byte} constant ({@code -128} and up)
     * @return this builder
     * @throws IllegalArgumentException if p2 is out of range
     */
    public APDUBuilder p2(int p2) {
        this.p2 = APDUCodec.headerByte("P2", p2);
        return this;
    }

    /**
     * Sets the command data from a byte array.
     *
     * <p>Data up to 255 bytes produces a short APDU. Data from 256 to 65535 bytes
     * produces an extended APDU automatically.</p>
     *
     * @param data the command data (max 65535 bytes)
     * @return this builder
     * @throws IllegalArgumentException if data exceeds 65535 bytes
     */
    public APDUBuilder data(byte[] data) {
        if (data != null && data.length > 65535) {
            throw new IllegalArgumentException(
                    "APDU data too long: " + data.length + " bytes (max 65535)");
        }
        this.data = data != null ? data.clone() : null;
        return this;
    }

    /**
     * Sets the command data from a hex string.
     *
     * <p>Spaces in the hex string are ignored, allowing formats like
     * {@code "A0 00 00 00 03 10 10"} or {@code "A0000000031010"}.</p>
     *
     * @param hex the command data as a hex string
     * @return this builder
     * @throws IllegalArgumentException if the hex string is invalid or data exceeds 65535 bytes
     */
    public APDUBuilder data(String hex) {
        return data(Hex.decode(hex));
    }

    /**
     * Sets the logical channel number to encode in the CLA byte.
     *
     * <p>When set, the channel is applied during {@link #build()}: channels 0 to 3 in b2-b1, channels 4 to
     * 19 in the further interindustry coding (ISO/IEC 7816-4:2005 5.1.1 Tables 2 and 3; GlobalPlatform
     * Card Specification 2.3.1 11.1.4 for the proprietary class). See {@link ClassByte#withChannel(int, int)}.</p>
     *
     * @param channel the logical channel number (0-19)
     * @return this builder
     * @throws IllegalArgumentException if channel is out of range
     */
    public APDUBuilder channel(int channel) {
        if (channel < 0 || channel > ClassByte.MAX_CHANNEL) {
            throw new IllegalArgumentException(
                    "Logical channel must be 0-" + ClassByte.MAX_CHANNEL + ", got: " + channel);
        }
        this.channel = channel;
        return this;
    }

    /**
     * Sets the expected response length (Le).
     *
     * <p>For short APDUs: 0 means 256 bytes. For extended APDUs: 0 means 65536 bytes.
     * The encoding format (short vs extended) is chosen automatically based on the
     * Le value and data length.</p>
     *
     * <p>Omitting Le (not calling this method) creates a Case 1 or Case 3 APDU.</p>
     *
     * @param le the expected response length (0-65536, where 0 means max for the format)
     * @return this builder
     * @throws IllegalArgumentException if le is out of range
     */
    public APDUBuilder le(int le) {
        if (le < 0 || le > 65536) {
            throw new IllegalArgumentException("Le must be 0-65536, got: " + le);
        }
        this.le = le;
        return this;
    }

    /**
     * Builds the APDU as a byte array.
     *
     * <p>The resulting format depends on which fields are set and their values:</p>
     * <ul>
     *   <li>Case 1: {@code CLA INS P1 P2} — no data, no Le</li>
     *   <li>Case 2S: {@code CLA INS P1 P2 Le(1)} — Le &le; 256</li>
     *   <li>Case 2E: {@code CLA INS P1 P2 0x00 Le(2)} — Le &gt; 256</li>
     *   <li>Case 3S: {@code CLA INS P1 P2 Lc(1) Data} — data &le; 255</li>
     *   <li>Case 3E: {@code CLA INS P1 P2 0x00 Lc(2) Data} — data &gt; 255</li>
     *   <li>Case 4S: {@code CLA INS P1 P2 Lc(1) Data Le(1)} — both short</li>
     *   <li>Case 4E: {@code CLA INS P1 P2 0x00 Lc(2) Data Le(2)} — extended</li>
     * </ul>
     *
     * @return the encoded APDU command bytes
     */
    public byte[] build() {
        int effectiveCla = (channel >= 0) ? ClassByte.withChannel(cla, channel) : cla;
        return APDUCodec.encode(effectiveCla, ins, p1, p2, data, le);
    }

    /**
     * Builds the APDU and sends it via {@link SmartCardSession#transmit(byte[])}.
     *
     * <p>This is a convenience method equivalent to:
     * {@code new APDUResponse(session.transmit(apdu)).inReplyTo(apdu)} with {@code apdu = builder.build()}.</p>
     *
     * @param session the smart card session to send the APDU through
     * @return the APDU response, which knows its command ({@link APDUResponse#inReplyTo(byte[])})
     */
    public APDUResponse sendTo(SmartCardSession session) {
        byte[] apdu = build();
        return new APDUResponse(session.transmit(apdu)).inReplyTo(apdu);
    }

    /**
     * Returns a hex string representation of the built APDU (for debugging).
     *
     * @return spaced hex string of the APDU bytes
     */
    @Override
    public String toString() {
        return Hex.encodeSpaced(build());
    }
}
