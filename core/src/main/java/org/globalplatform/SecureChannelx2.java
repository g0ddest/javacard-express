package org.globalplatform;

import javacard.framework.ISOException;

/**
 * A {@link SecureChannel} that also processes the commands of the security protocol from a byte array. An
 * application casts the object of {@link GPSystem#getSecureChannel()}; whether a card's object implements the
 * interface depends on the card (the simulated cards' object does). Part of the package since version 1.4.
 */
public interface SecureChannelx2 extends SecureChannel {

    /**
     * Processes a command of the security protocol like {@link SecureChannel#processSecurity}, reading the
     * command from and writing the response data to a byte array; the application has received the command
     * data before.
     *
     * @param baBuffer   the buffer with the command
     * @param sInOffset  the offset of the class byte
     * @param sInLength  the length of the command (header and data field)
     * @param sOutOffset where the response data are written
     * @return the length of the response data
     * @throws ISOException '6E00' or '6D00' when the command does not belong to the protocol, or the
     *                      protocol's own status word
     */
    short processSecurity(byte[] baBuffer, short sInOffset, short sInLength, short sOutOffset) throws ISOException;
}
