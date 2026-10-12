package org.globalplatform;

import javacard.framework.APDU;
import javacard.framework.ISOException;
import javacard.framework.Shareable;

/**
 * The secure channel a Security Domain offers to the applications associated with it: the applet hands the
 * commands of the secure channel protocol to {@link #processSecurity(APDU)} and has its own commands and
 * responses checked and protected by {@link #unwrap} and {@link #wrap}, without knowing the protocol or the keys.
 * An applet gets the object from {@link GPSystem#getSecureChannel()}.
 *
 * <p>A session has a Session Security Level, negotiated when it is opened and kept until it is terminated, and a
 * Current Security Level, which is what {@link #getSecurityLevel()} returns and what applies to the next command;
 * it never goes below the session level while the session is open. A command that fails its check aborts the
 * session: the current level becomes {@link #NO_SECURITY_LEVEL}, and {@link #unwrap} and {@link #wrap} refuse to
 * work until the session is terminated. {@link #resetSecurity()} terminates it, and so do the selection of another
 * application, the closing of the logical channel and a card reset (GlobalPlatform Card Specification v2.3.1
 * section 10.2.3). A session belongs to the application and the logical channel that opened it.</p>
 *
 * <p>Since version 1.0 of the package.</p>
 */
public interface SecureChannel extends Shareable {

    /**
     * Security level bit: the off-card entity is authenticated as the Application Provider ('80'). Since version
     * 1.0 of the package.
     */
    byte AUTHENTICATED = (byte) 0x80;
    /**
     * Security level bit: {@link #unwrap} decrypts the command data ('02'). Since version 1.0 of the package.
     */
    byte C_DECRYPTION = 0x02;
    /**
     * Security level bit: {@link #unwrap} verifies the MAC of the command ('01'). Since version 1.0 of the
     * package.
     */
    byte C_MAC = 0x01;
    /**
     * Security level bit: {@link #wrap} encrypts the response data ('20'). Since version 1.0 of the package.
     */
    byte R_ENCRYPTION = 0x20;
    /**
     * Security level bit: {@link #wrap} adds a MAC to the response ('10'). Since version 1.0 of the package.
     */
    byte R_MAC = 0x10;
    /**
     * Security level without authentication and without protection of commands and responses ('00'). Since version
     * 1.0 of the package.
     */
    byte NO_SECURITY_LEVEL = 0x00;
    /**
     * Security level bit: an off-card entity other than the Application Provider is authenticated ('40'). Since
     * version 1.1 of the package.
     */
    byte ANY_AUTHENTICATED = 0x40;

    /**
     * Handles a command of the secure channel protocol, for example the two commands that open a session. An
     * applet may pass every command it does not know itself. The method receives the command data, writes the
     * response data into the APDU buffer at {@code ISO7816.OFFSET_CDATA} and returns its length; sending it is the
     * applet's task.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param apdu the incoming command
     * @return the number of response bytes to send from {@code ISO7816.OFFSET_CDATA}
     * @throws ISOException with '6E00' or '6D00' when the class or the instruction is not one of the protocol,
     *                      or with the status word the protocol gives for a failed command
     */
    short processSecurity(APDU apdu) throws ISOException;

    /**
     * Protects a response according to the Current Security Level. The applet appends the status bytes to the
     * response data before the call; the method removes them again and adds what the level asks for (a MAC,
     * encryption). Without response protection, and without a session, the data stays as it is and the result is
     * {@code sLength - 2}.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the response data followed by the status bytes
     * @param sOffset  offset of the response data
     * @param sLength  length of the response data including the two status bytes
     * @return the length of the protected response data, without status bytes
     * @throws ISOException with '6985' when the session was aborted and not yet terminated, '6982' when the
     *                      response cannot be protected
     */
    short wrap(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Checks a command according to the Current Security Level and removes its protection, in place. The applet
     * receives the command data before the call. A command whose class byte shows no secure messaging is left as
     * it is when the level asks for no command protection and refused otherwise; a failed check aborts the
     * session. An unwrapped command without data has an Lc byte of '00'.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the command
     * @param sOffset  offset of the class byte of the command
     * @param sLength  length of the whole command: header and data field
     * @return the length of the unwrapped command: header and data field
     * @throws ISOException with '6985' when the session was aborted and not yet terminated, '6982' when the
     *                      protection is missing or wrong
     */
    short unwrap(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Decrypts sensitive data in place with the key and algorithm the protocol defines for such data. Padding the
     * application added itself stays in the data.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the encrypted data
     * @param sOffset  offset of the encrypted data
     * @param sLength  length of the encrypted data
     * @return the length of the clear data
     * @throws ISOException with '6985' when no session is open, '6700' when the length does not fit the
     *                      algorithm
     */
    short decryptData(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Encrypts sensitive data in place with the key and algorithm the protocol defines for such data.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the clear data
     * @param sOffset  offset of the clear data
     * @param sLength  length of the clear data
     * @return the length of the encrypted data
     * @throws ISOException with '6982' when the card does not support the operation, '6985' when no session is
     *                      open, '6700' when the length does not fit the algorithm
     */
    short encryptData(byte[] baBuffer, short sOffset, short sLength) throws ISOException;

    /**
     * Terminates the session: both security levels become {@link #NO_SECURITY_LEVEL} and the session keys are
     * discarded. Does nothing when no session exists. Applets usually call it in {@code deselect()}.
     *
     * <p>Since version 1.0 of the package.</p>
     */
    void resetSecurity();

    /**
     * Returns the Current Security Level, which an applet checks before it executes a command that needs
     * protection.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return a combination of {@link #AUTHENTICATED} or {@link #ANY_AUTHENTICATED}, {@link #C_MAC},
     *         {@link #C_DECRYPTION}, {@link #R_MAC} and {@link #R_ENCRYPTION}, or {@link #NO_SECURITY_LEVEL}
     */
    byte getSecurityLevel();
}
