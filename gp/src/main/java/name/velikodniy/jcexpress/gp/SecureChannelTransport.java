package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.scp.SecureChannel;

/**
 * Transmission of the commands of a secure channel session for {@link GPSession}: the transport rules of
 * ISO/IEC 7816-4:2005 5.1.3 ('61XX', '6CXX') applied without breaking the MAC chaining of GlobalPlatform Card
 * Specification v2.3.1 Appendix E (SCP02) and Amendment D (SCP03).
 *
 * <ul>
 *   <li>Every command carrying a C-MAC is transmitted exactly once: the card verifies the C-MAC whatever it then
 *       answers and keeps it as the chaining value of the next command ("a verified C-MAC shall never be
 *       discarded in favor of a previously verified C-MAC", E.4.4; Amendment D 6.2.3, 6.2.4, and the SCP03
 *       encryption counter, 6.2.6). The same bytes sent again fail the MAC check, and the card aborts the secure
 *       channel (E.1.6).</li>
 *   <li>'6CXX' (wrong Le): the plain command is protected again with Le = SW2 and sent once more, with a new
 *       C-MAC (and the next SCP03 counter); the C-MAC does not cover Le (E.4.4: "It does not include Le";
 *       Amendment D 6.2.4). A second '6CXX' is returned to the caller. With SCP02 R-MAC, the bare '6CXX' error has
 *       advanced the R-MAC chain on both sides (E.4.5).</li>
 *   <li>'61XX' (more data): GET RESPONSE commands in plain, with the class byte of the protected command, fetch
 *       the rest (ISO/IEC 7816-4 5.1.3, 7.6.1). They belong to the transmission, not to the secure channel: the
 *       R-MAC and the response encryption cover the reassembled response (GPCS 11.1.5.2), which is unwrapped
 *       once.</li>
 *   <li>EXTERNAL AUTHENTICATE is transmitted once and never retried, whatever the card answers (E.5.2.7).</li>
 * </ul>
 *
 * <p>The guarantee ends at the {@link SmartCardSession}: a PC/SC provider may handle '61XX' and '6CXX' itself
 * below it (the JDK's SunPCSC does by default, see {@code PcscSession}).</p>
 */
final class SecureChannelTransport {

    /** SW1 '6C': wrong Le field, SW2 = exact number of available data bytes (ISO/IEC 7816-4:2005 5.1.3). */
    private static final int SW1_WRONG_LE = 0x6C;

    private SecureChannelTransport() {
    }

    /**
     * EXTERNAL AUTHENTICATE, transmitted exactly once: with a single {@code transmit}, not through
     * {@link APDUSequence}, whose '6CXX' handling would re-send it. Any answer other than '9000' destroys the
     * channel and is never retried (GPCS v2.3.1 E.5.2.7).
     *
     * @param session   the card session
     * @param candidate the channel whose card cryptogram was verified
     * @throws GPException if the card does not answer '9000'
     */
    static void externalAuthenticate(SmartCardSession session, SecureChannel candidate) {
        APDUResponse response;
        try {
            response = new APDUResponse(session.transmit(candidate.externalAuthenticate()));
        } catch (RuntimeException e) {
            candidate.destroy();
            throw e;
        }
        if (!response.isSuccess()) {
            candidate.destroy();
            String reason = response.sw() == 0x6300 ? " (host cryptogram rejected: wrong keys)" : "";
            throw new GPException("EXTERNAL AUTHENTICATE failed" + reason
                    + "; not retried, as cards count failed authentications", response.sw());
        }
    }

    /**
     * Protects, transmits and unwraps one command, re-protecting it once after '6CXX' (see the class
     * documentation).
     *
     * @param session   the card session
     * @param channel   the open secure channel
     * @param plainApdu the command before secure messaging
     * @param onFailure run before a transport or response verification failure is rethrown
     * @return the unwrapped response
     */
    static APDUResponse transmit(SmartCardSession session, SecureChannel channel, byte[] plainApdu,
                                 Runnable onFailure) {
        APDUResponse response = exchange(session, channel, plainApdu, onFailure);
        if (response.sw1() != SW1_WRONG_LE) {
            return response;
        }
        return exchange(session, channel, APDUCodec.correctLe(plainApdu, response.sw2()), onFailure);
    }

    /**
     * One protected exchange. A transport failure (the card may or may not have verified the C-MAC, so the MAC
     * chaining value is unknown, E.4.4) or a response that cannot be verified (E.4.5, Amendment D 6.2.5) runs
     * {@code onFailure}; a command the channel refuses to wrap is never sent and does not.
     */
    private static APDUResponse exchange(SmartCardSession session, SecureChannel channel, byte[] plainApdu,
                                         Runnable onFailure) {
        byte[] wrapped = channel.wrap(plainApdu);
        try {
            return channel.unwrap(APDUSequence.on(session).leCorrection(false).transmit(wrapped));
        } catch (RuntimeException e) {
            onFailure.run();
            throw e;
        }
    }
}
