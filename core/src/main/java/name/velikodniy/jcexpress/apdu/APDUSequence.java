package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;

import java.io.ByteArrayOutputStream;

/**
 * Handles automatic GET RESPONSE chaining (SW=61XX) and Le correction (SW=6CXX).
 *
 * <p>When a card returns status word 61XX, it means XX more bytes of response data
 * are available. This class automatically sends GET RESPONSE (INS=C0) commands
 * and concatenates the data until all bytes are fetched.</p>
 *
 * <p>When a card returns 6CXX, it means the command should be retried with Le=XX.
 * This class retries the original command once with the corrected Le value.</p>
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
 * // Custom GET RESPONSE CLA and chain limit
 * APDUResponse full = APDUSequence.on(session)
 *     .getResponseCla(0x00)
 *     .maxChain(64)
 *     .send(0x80, 0xF2, 0x40, 0x00, data);
 * </pre>
 *
 * @see SmartCardSession#transmit(byte[])
 */
public final class APDUSequence {

    private final SmartCardSession session;
    private int getResponseCla = 0x00;
    private int maxChain = 32;

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
     * Sets the CLA byte used for GET RESPONSE commands.
     *
     * <p>Default is 0x00 (ISO 7816-4). Some cards require a different CLA
     * (e.g., 0x80 for GlobalPlatform proprietary commands).</p>
     *
     * @param cla the CLA byte for GET RESPONSE
     * @return this instance for chaining
     */
    public APDUSequence getResponseCla(int cla) {
        this.getResponseCla = cla;
        return this;
    }

    /**
     * Sets the maximum number of chained GET RESPONSE calls.
     *
     * <p>Default is 32. This is a safety limit to prevent infinite loops
     * in case of a misbehaving card.</p>
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
     * Sends an APDU with automatic GET RESPONSE chaining and Le correction.
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @return the complete response (all chained data concatenated)
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        byte[] apdu = APDUBuilder.command()
                .cla(cla).ins(ins).p1(p1).p2(p2)
                .data(data)
                .build();
        return transmit(apdu);
    }

    /**
     * Sends an APDU with Le, with automatic GET RESPONSE chaining.
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @param le   the expected response length
     * @return the complete response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] apdu = APDUBuilder.command()
                .cla(cla).ins(ins).p1(p1).p2(p2)
                .data(data)
                .le(le)
                .build();
        return transmit(apdu);
    }

    /**
     * Sends an APDU without data, with automatic chaining.
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the complete response
     */
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        byte[] apdu = APDUBuilder.command()
                .cla(cla).ins(ins).p1(p1).p2(p2)
                .build();
        return transmit(apdu);
    }

    /**
     * Sends raw APDU bytes with automatic GET RESPONSE chaining and Le correction.
     *
     * @param rawApdu the raw APDU command bytes
     * @return the complete response (all chained data concatenated)
     */
    public APDUResponse transmit(byte[] rawApdu) {
        byte[] rawResponse = session.transmit(rawApdu);
        APDUResponse response = new APDUResponse(rawResponse);
        return resolve(response, rawApdu);
    }

    /**
     * Resolves GET RESPONSE chaining (61XX) and Le correction (6CXX).
     */
    private APDUResponse resolve(APDUResponse response, byte[] originalApdu) {
        // Handle 6CXX: wrong Le, retry with correct value
        if (response.sw1() == 0x6C) {
            byte[] corrected = correctLe(originalApdu, response.sw2());
            byte[] rawResponse = session.transmit(corrected);
            response = new APDUResponse(rawResponse);
            // After Le correction, fall through to check for 61XX
        }

        // Handle 61XX: more data available, chain GET RESPONSE
        if (response.sw1() == 0x61) {
            return chainGetResponse(response);
        }

        return response;
    }

    /**
     * Chains GET RESPONSE commands to fetch all remaining data.
     */
    private APDUResponse chainGetResponse(APDUResponse initial) {
        ByteArrayOutputStream accumulator = new ByteArrayOutputStream();
        accumulator.write(initial.data(), 0, initial.data().length);

        APDUResponse current = initial;
        int iterations = 0;

        while (current.sw1() == 0x61 && iterations < maxChain) {
            int remaining = current.sw2();
            byte[] getResponse = APDUBuilder.command()
                    .cla(getResponseCla).ins(0xC0).p1(0x00).p2(0x00)
                    .le(remaining == 0 ? 256 : remaining)
                    .build();

            byte[] rawResponse = session.transmit(getResponse);
            current = new APDUResponse(rawResponse);
            accumulator.write(current.data(), 0, current.data().length);
            iterations++;
        }

        byte[] allData = accumulator.toByteArray();
        return new APDUResponse(allData, current.sw());
    }

    /**
     * Corrects the Le in the original APDU (handles both short and extended formats).
     */
    private static byte[] correctLe(byte[] apdu, int correctLe) {
        return APDUCodec.correctLe(apdu, correctLe);
    }
}
