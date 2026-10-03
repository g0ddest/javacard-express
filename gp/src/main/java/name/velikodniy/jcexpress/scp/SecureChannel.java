package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;

/**
 * Common interface for SCP02 and SCP03 secure channel sessions (host side).
 *
 * <p>A secure channel wraps outgoing APDU commands by adding a C-MAC and optionally encrypting the
 * command data (C-ENC), and unwraps responses by verifying an R-MAC and optionally decrypting the
 * response data (R-ENC), according to the security level requested in EXTERNAL AUTHENTICATE.</p>
 *
 * <h2>Lifecycle (explicit secure channel initiation, GPCS v2.3.1 E.1.2.1, Amendment D 5.2):</h2>
 * <ol>
 *   <li>Create the channel from the INITIALIZE UPDATE response; the card cryptogram is verified
 *       ({@link SCP03} verifies it on creation, {@link SCP02#verifyCardCryptogram(byte[])} must be
 *       called explicitly)</li>
 *   <li>Send the command returned by {@link #externalAuthenticate()} and stop if the card does not
 *       answer '9000' (never retry: failed authentications are counted by the card)</li>
 *   <li>Wrap all subsequent commands via {@link #wrap(byte[])} and unwrap their responses via
 *       {@link #unwrap(APDUResponse)}, strictly in command/response order, also responses with an error status
 *       word (SCP02 advances its R-MAC chain for them, GPCS v2.3.1 E.4.5)</li>
 *   <li>Transmit each wrapped command exactly once: the card verifies its C-MAC whatever it answers (E.4.4,
 *       Amendment D 6.2.4). Complete '61XX' with GET RESPONSE commands in plain and unwrap the reassembled
 *       response (GPCS 11.1.5.2), e.g. with {@code APDUSequence.on(session).leCorrection(false)}; after '6CXX'
 *       wrap the plain command again with Le = SW2 (ISO/IEC 7816-4:2005 5.1.3). {@code GPSession} does both.</li>
 *   <li>Call {@link #destroy()} when the session ends</li>
 * </ol>
 *
 * @see SCP02
 * @see SCP03
 */
public interface SecureChannel {

    /**
     * Wraps an APDU command with secure messaging (C-MAC, optionally C-ENC).
     *
     * <p>The input APDU must be a short APDU: {@code CLA INS P1 P2 [Lc Data] [Le]}. Before the
     * EXTERNAL AUTHENTICATE command has been produced, only that command is accepted (with its host
     * cryptogram as data); it is MACed but never encrypted.</p>
     *
     * @param apdu the plaintext APDU command bytes
     * @return the wrapped APDU
     * @throws SCPException if the APDU is malformed or extended, its data exceeds
     *                      {@link #maxCommandDataLength()}, or the channel is not usable
     */
    byte[] wrap(byte[] apdu);

    /**
     * Returns the security level of this channel (the P1 of EXTERNAL AUTHENTICATE).
     *
     * @return the security level (combination of {@link GP} security constants)
     * @see GP#SECURITY_C_MAC
     * @see GP#SECURITY_C_MAC_C_ENC
     */
    int securityLevel();

    /**
     * Returns the DEK (data encryption key) for encrypting sensitive data such as new keys in PUT KEY
     * commands.
     *
     * <p>For SCP02, this is the <b>session</b> DEK derived from the static DEK and the sequence counter
     * (GPCS v2.3.1 E.4.1, E.4.7). For SCP03, this is the <b>static</b> Key-DEK (Amendment D 6.2.8).</p>
     *
     * @return a copy of the DEK bytes
     */
    byte[] dek();

    /**
     * Unwraps an APDU response of the command wrapped last: verifies and strips the R-MAC and decrypts
     * the response data, as required by the security level.
     *
     * @param response the raw APDU response (after GET RESPONSE chaining)
     * @return the response with R-MAC removed and data decrypted
     * @throws SCPException if the R-MAC verification or the decryption fails
     */
    default APDUResponse unwrap(APDUResponse response) {
        return response;
    }

    /**
     * Builds the EXTERNAL AUTHENTICATE command that completes the mutual authentication: host
     * cryptogram plus a C-MAC computed with a zero initial chaining value; the command is never
     * encrypted, whatever the security level (GPCS v2.3.1 E.5.2.3 and Table E-10, Amendment D
     * Table 7-5). It may be produced only once and only after the card cryptogram was verified.
     *
     * @return the EXTERNAL AUTHENTICATE command
     * @throws SCPException if the card cryptogram was not verified or the command was already produced
     */
    default byte[] externalAuthenticate() {
        throw new UnsupportedOperationException("EXTERNAL AUTHENTICATE is not supported by " + getClass());
    }

    /**
     * Returns the maximum length of the clear command data field that still fits into a short APDU
     * after secure messaging (C-MAC and, with C-ENC, padding) is applied (GPCS v2.3.1 11.1.5).
     *
     * @return the maximum number of clear data bytes per command
     */
    default int maxCommandDataLength() {
        return 239;
    }

    /**
     * Destroys the session keys (overwrites them with zeros). Afterwards {@link #wrap(byte[])} and
     * {@link #unwrap(APDUResponse)} fail.
     */
    default void destroy() {
        // implementations without key material have nothing to destroy
    }
}
