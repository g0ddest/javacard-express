package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;

/**
 * Common interface for SCP02 and SCP03 secure channel sessions.
 *
 * <p>A secure channel wraps outgoing APDU commands by adding
 * a C-MAC and optionally encrypting the command data (C-ENC).
 * Once established, all subsequent commands to the card should
 * be wrapped through this interface.</p>
 *
 * <h2>Typical lifecycle:</h2>
 * <ol>
 *   <li>Create session keys from INITIALIZE UPDATE response</li>
 *   <li>Verify card cryptogram</li>
 *   <li>Send EXTERNAL AUTHENTICATE with host cryptogram</li>
 *   <li>Wrap all subsequent commands via {@link #wrap(byte[])}</li>
 *   <li>Unwrap responses via {@link #unwrap(APDUResponse)} if R-MAC is enabled</li>
 * </ol>
 *
 * @see SCP02
 * @see SCP03
 */
public interface SecureChannel {

    /**
     * Wraps an APDU command with secure messaging (C-MAC, optionally C-ENC).
     *
     * <p>The input APDU must be in standard format:
     * {@code CLA INS P1 P2 [Lc Data] [Le]}</p>
     *
     * <p>The output APDU will have:</p>
     * <ul>
     *   <li>CLA byte modified (bit 3 set for secure messaging)</li>
     *   <li>C-MAC appended (8 bytes for SCP02, 8 bytes for SCP03)</li>
     *   <li>Command data encrypted if C-ENC security level is set</li>
     *   <li>Lc updated to reflect the new data length</li>
     * </ul>
     *
     * @param apdu the plaintext APDU command bytes
     * @return the wrapped APDU with C-MAC (and optionally encrypted data)
     * @throws SCPException if wrapping fails
     */
    byte[] wrap(byte[] apdu);

    /**
     * Returns the security level of this channel.
     *
     * @return the security level (combination of {@link GP} security constants)
     * @see GP#SECURITY_C_MAC
     * @see GP#SECURITY_C_MAC_C_ENC
     */
    int securityLevel();

    /**
     * Returns the DEK (data encryption key) for encrypting sensitive data
     * such as new keys in PUT KEY commands.
     *
     * <p>For SCP02, this is the <b>session</b> DEK derived from the static DEK
     * and the sequence counter. For SCP03, this is the <b>static</b> DEK
     * (key encryption uses the static key, not a derived session key).</p>
     *
     * @return a copy of the DEK bytes
     */
    byte[] dek();

    /**
     * Unwraps an APDU response by verifying and stripping the R-MAC.
     *
     * <p>If R-MAC is not enabled (security level bit 4 not set), the response
     * is returned as-is. Otherwise, the last 8 bytes of the response data are
     * verified as an R-MAC and stripped from the returned response.</p>
     *
     * <p>Wire format of a response with R-MAC:
     * {@code [responseData(N)] [R-MAC(8)] [SW1] [SW2]}</p>
     *
     * @param response the raw APDU response (after GET RESPONSE chaining)
     * @return the unwrapped response with R-MAC stripped
     * @throws SCPException if the R-MAC verification fails
     */
    default APDUResponse unwrap(APDUResponse response) {
        return response;
    }
}
