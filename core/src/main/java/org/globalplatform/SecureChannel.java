package org.globalplatform;

import javacard.framework.APDU;
import javacard.framework.ISOException;
import javacard.framework.Shareable;

/**
 * The secure channel services a Security Domain gives its applications: the application passes the commands
 * of the security protocol on, asks for the protection of a command to be checked and removed, and for a
 * response to be protected, without knowing protocol or keys (GlobalPlatform Card Specification v2.3.1
 * sections 7.3.1 and 10.3). An application gets the object from {@link GPSystem#getSecureChannel()}. Part of
 * the package since version 1.0.
 *
 * <p>On the simulated cards the protocol behind it is SCP03
 * ({@code name.velikodniy.jcexpress.embedded.gp.SecurityDomain}).</p>
 */
public interface SecureChannel extends Shareable {

    /** Security level bit: the off-card entity is authenticated as the application's provider. */
    byte AUTHENTICATED = (byte) 0x80;

    /** Security level bit: {@link #unwrap} decrypts the command data. */
    byte C_DECRYPTION = (byte) 0x02;

    /** Security level bit: {@link #unwrap} verifies the MAC of the command. */
    byte C_MAC = (byte) 0x01;

    /** Security level bit: {@link #wrap} encrypts the response data. */
    byte R_ENCRYPTION = (byte) 0x20;

    /** Security level bit: {@link #wrap} adds a MAC to the response. */
    byte R_MAC = (byte) 0x10;

    /** Security level: no session, nothing is checked or protected. */
    byte NO_SECURITY_LEVEL = (byte) 0x00;

    /**
     * Security level bit: an off-card entity other than the application's provider is authenticated. Part of
     * the package since version 1.1.
     */
    byte ANY_AUTHENTICATED = (byte) 0x40;

    /**
     * Processes a command of the security protocol (the commands that open a session). The method receives
     * the command data, writes response data at {@code ISO7816.OFFSET_CDATA} of the APDU buffer and returns
     * their length; the application sends them.
     *
     * @param apdu the command
     * @return the length of the response data
     * @throws ISOException '6E00' or '6D00' when the command does not belong to the protocol, or the
     *                      protocol's own status word
     */
    short processSecurity(APDU apdu) throws ISOException;

    /**
     * Protects a response as the current security level asks. The application puts the status bytes after
     * the response data; the method removes them.
     *
     * @param baBuffer the buffer with the response data and the status bytes
     * @param sOffset  where the response data start
     * @param sLength  the length of the response data and the status bytes
     * @return the length of the protected response data ({@code sLength - 2} when nothing is added)
     * @throws ISOException '6985' after a session was aborted, '6982' when the response cannot be protected
     */
    short wrap(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Checks and removes the protection of a command as the current security level asks; the application
     * has received the command data before.
     *
     * @param baBuffer the buffer with the command
     * @param sOffset  the offset of the class byte
     * @param sLength  the length of the command (header and data field)
     * @return the length of the command without its protection (header and data field)
     * @throws ISOException '6985' after a session was aborted, '6982' when the protection is missing or
     *                      wrong (the session is aborted then)
     */
    short unwrap(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Decrypts sensitive data in place with the key the protocol defines for it.
     *
     * @param baBuffer the buffer with the data
     * @param sOffset  where the data start
     * @param sLength  the length of the data
     * @return the length of the clear data
     * @throws ISOException '6985' without an open session, '6700' for a length the algorithm does not accept
     */
    short decryptData(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Encrypts sensitive data in place with the key the protocol defines for it.
     *
     * @param baBuffer the buffer with the data
     * @param sOffset  where the data start
     * @param sLength  the length of the data
     * @return the length of the encrypted data
     * @throws ISOException '6985' without an open session, '6700' for a length the algorithm does not accept,
     *                      '6982' when the card does not support the method
     */
    short encryptData(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Ends the session: both security levels become {@link #NO_SECURITY_LEVEL}. Does nothing without a
     * session; applications call it in {@code Applet.deselect()}.
     */
    void resetSecurity();

    /**
     * Returns the current security level.
     *
     * @return a combination of the security level constants
     */
    byte getSecurityLevel();
}
