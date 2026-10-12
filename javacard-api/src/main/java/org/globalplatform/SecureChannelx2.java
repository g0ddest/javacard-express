package org.globalplatform;

import javacard.framework.ISOException;

/**
 * A {@link SecureChannel} that also handles the commands of the secure channel protocol from a byte array instead
 * of the APDU object. Whether the object {@link GPSystem#getSecureChannel()} returns also implements this
 * interface depends on the card; an applet finds out with a cast.
 *
 * <p>Since version 1.4 of the package.</p>
 */
public interface SecureChannelx2 extends SecureChannel {

    /**
     * Handles a command of the secure channel protocol like {@link SecureChannel#processSecurity}, reading the
     * command from an array and writing the response data to the same array. The applet receives the command data
     * before the call.
     *
     * <p>Since version 1.4 of the package.</p>
     *
     * @param baBuffer   the array that holds the command and takes the response data
     * @param sInOffset  offset of the class byte of the command
     * @param sInLength  length of the whole command: header and data field
     * @param sOutOffset offset where the response data is written
     * @return the number of response bytes written
     * @throws ISOException with '6E00' or '6D00' when the class or the instruction is not one of the protocol,
     *                      or with the status word the protocol gives for a failed command
     */
    short processSecurity(byte[] baBuffer, short sInOffset, short sInLength, short sOutOffset) throws ISOException;
}
