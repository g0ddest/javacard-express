package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;

import java.io.ByteArrayOutputStream;

/**
 * Handles automatic GET RESPONSE chaining (SW=61XX) and Le correction (SW=6CXX), ISO/IEC 7816-4:2005 5.1.3.
 *
 * <p>When a card returns status word 61XX, XX more bytes of response data are available ('00': 256 or
 * more). This class sends GET RESPONSE (INS 'C0', Le = SW2) commands with the class byte of the original
 * command, so the remaining bytes are fetched on the same logical channel and class (ISO/IEC 7816-4:2005
 * 5.1.3 and 5.1.1.2; GlobalPlatform Card Specification 2.3.1 11.1.5.2), and concatenates the data until
 * the card answers another status word.</p>
 *
 * <p>When a card returns 6CXX, the command is retried once with Le = XX (SW2 is read like a short Le field,
 * '00' meaning 256), unless {@link #leCorrection(boolean) Le correction} is turned off.</p>
 *
 * <p><strong>Commands already protected by a secure channel</strong> (e.g. wrapped by GlobalPlatform SCP02/SCP03)
 * must never be re-sent as they are: the card verified the MAC of the command it answered with '6CXX' and moved
 * its MAC chaining value, so the same bytes fail the MAC check and the card aborts the secure channel (GPCS v2.3.1
 * E.4.4, E.1.6; Amendment D 6.2.4). Transmit such bytes with {@code leCorrection(false)} and protect the command
 * again with the corrected Le, as {@code GPSession} does. (A session that protects every command it is given,
 * such as {@code SMSession}, protects a re-sent command again.) GET RESPONSE after '61XX' is safe: it belongs to
 * the transmission, not to the secure channel, whose protection covers the reassembled response (GPCS v2.3.1
 * 11.1.5.2).</p>
 *
 * <p>The card of a {@code @JavaCardTest} class sends the commands of its {@code send} methods through this class,
 * so its tests need it only for {@link SmartCardSession#transmit(byte[]) transmit} and for sessions used
 * directly. On a PC/SC reader the JDK completes '61XX' and '6CXX' before this class sees them (see
 * {@code PcscSession}).</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * // Simple: auto-handle GET RESPONSE and Le correction
 * APDUResponse full = APDUSequence.on(session)
 *     .send(0x80, 0xF2, 0x40, 0x00, data);
 *
 * // With raw APDU bytes
 * APDUResponse full = APDUSequence.on(session)
 *     .transmit(rawApdu);
 *
 * // Custom GET RESPONSE class byte and chain limit
 * APDUResponse full = APDUSequence.on(session)
 *     .getResponseCla(0x00)
 *     .maxChain(512)
 *     .send(0x80, 0xF2, 0x40, 0x00, data);
 * </pre>
 *
 * @see SmartCardSession#transmit(byte[])
 */
public final class APDUSequence {

    /**
     * Default limit of GET RESPONSE commands per command: enough for the largest response a command can
     * request (Ne = 65 536) in pieces of 256 bytes.
     */
    public static final int DEFAULT_MAX_CHAIN = 256;

    private static final int INS_GET_RESPONSE = 0xC0;

    private final SmartCardSession session;
    private int getResponseCla = -1;
    private int maxChain = DEFAULT_MAX_CHAIN;
    private boolean leCorrection = true;

    private APDUSequence(SmartCardSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        this.session = session;
    }

    /**
     * Creates a new APDU sequence handler wrapping the given session.
     *
     * @param session the smart card session to send commands through
     * @return a new APDUSequence instance
     */
    public static APDUSequence on(SmartCardSession session) {
        return new APDUSequence(session);
    }

    /**
     * Sets the class byte used for GET RESPONSE commands instead of the class byte of the original command
     * (the default, ISO/IEC 7816-4:2005 5.1.3).
     *
     * <p>The logical channel is always the one of the original command: the channel bits of {@code cla} are
     * replaced (see {@link ClassByte#withChannel(int, int)}).</p>
     *
     * @param cla the CLA byte for GET RESPONSE (0x00-0xFF)
     * @return this instance for chaining
     * @throws IllegalArgumentException if {@code cla} is not a byte value
     */
    public APDUSequence getResponseCla(int cla) {
        if (cla < 0 || cla > 0xFF) {
            throw new IllegalArgumentException("GET RESPONSE CLA must be 0x00-0xFF, got: " + cla);
        }
        this.getResponseCla = cla;
        return this;
    }

    /**
     * Sets the maximum number of GET RESPONSE commands sent for one command.
     *
     * <p>Default is {@value #DEFAULT_MAX_CHAIN}. This is a safety limit against a card that keeps answering
     * 61XX; reaching it throws {@link IllegalStateException}.</p>
     *
     * @param max the maximum chain length
     * @return this instance for chaining
     */
    public APDUSequence maxChain(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("maxChain must be >= 1, got: " + max);
        }
        this.maxChain = max;
        return this;
    }

    /**
     * Turns the re-sending of a command answered with '6CXX' on (the default) or off.
     *
     * <p>ISO/IEC 7816-4:2005 5.1.3: after '6CXX' "the same command may be re-issued using SW2 ... as short Le
     * field". Turn it off for commands that must not be transmitted twice with the same bytes, such as commands
     * protected by a secure channel (see the class documentation): '6CXX' is then returned to the caller.
     * GET RESPONSE chaining after '61XX' is not affected.</p>
     *
     * @param enabled true to re-send the command with Le = SW2 after '6CXX', false to return '6CXX'
     * @return this instance for chaining
     */
    public APDUSequence leCorrection(boolean enabled) {
        this.leCorrection = enabled;
        return this;
    }

    /**
     * Sends an APDU without Le field, with automatic GET RESPONSE chaining and Le correction.
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @return the complete response (all chained data concatenated)
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, SmartCardSession.NO_LE);
    }

    /**
     * Sends an APDU with automatic GET RESPONSE chaining and Le correction.
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @param le   Ne as defined by {@link SmartCardSession#send(int, int, int, int, byte[], int)}
     *             ({@link SmartCardSession#NO_LE} for no Le field, 256 for Le '00')
     * @return the complete response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        return transmit(APDUCodec.encode(cla, ins, p1, p2, data, le));
    }

    /**
     * Sends an APDU without data and Le field, with automatic chaining.
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the complete response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, SmartCardSession.NO_LE);
    }

    /**
     * Sends raw APDU bytes with automatic GET RESPONSE chaining and Le correction.
     *
     * @param rawApdu the raw APDU command bytes
     * @return the complete response (all chained data concatenated); it knows {@code rawApdu} as its command
     *         ({@link APDUResponse#inReplyTo(byte[])})
     * @throws IllegalStateException if the card still answers 61XX after {@link #maxChain(int)} GET RESPONSE
     *                               commands
     */
    public APDUResponse transmit(byte[] rawApdu) {
        byte[] rawResponse = session.transmit(rawApdu);
        APDUResponse response = new APDUResponse(rawResponse);
        return resolve(response, rawApdu).inReplyTo(rawApdu);
    }

    /**
     * Resolves GET RESPONSE chaining (61XX) and Le correction (6CXX).
     */
    private APDUResponse resolve(APDUResponse response, byte[] originalApdu) {
        // Handle 6CXX: wrong Le, retry with correct value
        if (leCorrection && response.sw1() == 0x6C) {
            byte[] corrected = APDUCodec.correctLe(originalApdu, response.sw2());
            response = new APDUResponse(session.transmit(corrected));
            // After Le correction, fall through to check for 61XX
        }
        if (response.sw1() == 0x61) {
            return chainGetResponse(response, originalApdu[0] & 0xFF);
        }
        return response;
    }

    /**
     * Chains GET RESPONSE commands to fetch all remaining data.
     */
    private APDUResponse chainGetResponse(APDUResponse initial, int commandCla) {
        int cla = getResponseCla < 0 ? commandCla
                : ClassByte.withChannel(getResponseCla, ClassByte.channel(commandCla));
        ByteArrayOutputStream accumulator = new ByteArrayOutputStream();
        accumulator.write(initial.data(), 0, initial.data().length);
        APDUResponse current = initial;
        int iterations = 0;
        while (current.sw1() == 0x61) {
            if (iterations == maxChain) {
                throw new IllegalStateException(String.format("GET RESPONSE chain limit reached (maxChain=%d):"
                        + " %d bytes received and the card still answers SW=%04X", maxChain,
                        accumulator.size(), current.sw()));
            }
            int remaining = current.sw2();
            byte[] getResponse = APDUCodec.encode(cla, INS_GET_RESPONSE, 0x00, 0x00, null,
                    remaining == 0 ? 256 : remaining);
            current = new APDUResponse(session.transmit(getResponse));
            accumulator.write(current.data(), 0, current.data().length);
            iterations++;
        }
        return new APDUResponse(accumulator.toByteArray(), current.sw());
    }
}
