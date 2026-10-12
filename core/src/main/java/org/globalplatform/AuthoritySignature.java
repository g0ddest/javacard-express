package org.globalplatform;

import javacard.framework.Shareable;

/**
 * The signature service of a Controlling Authority Security Domain (GlobalPlatform Card Specification v2.3
 * Amendment A). Part of the package since version 1.7.
 *
 * <p>The simulated cards provide no such service.</p>
 */
public interface AuthoritySignature extends Shareable {

    /** Mode of {@link #init}: integrity. */
    byte MODE_INTEGRITY = (byte) 0x01;

    /**
     * Starts a signature.
     *
     * @param mode   one of the {@code MODE} constants
     * @param params the buffer with parameters of the signature
     * @param offset where the parameters start
     * @param length the length of the parameters
     */
    void init(byte mode, byte[] params, short offset, short length);

    /**
     * Signs the accumulated data and the given data.
     *
     * @param inBuff    the buffer with the last data to sign
     * @param inOffset  where the data start
     * @param inLength  the length of the data
     * @param sigBuff   the buffer for the signature
     * @param sigOffset where the signature is written
     * @return the length of the signature
     */
    short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset);

    /**
     * Accumulates data to sign.
     *
     * @param inBuff   the buffer with the data
     * @param inOffset where the data start
     * @param inLength the length of the data
     */
    void update(byte[] inBuff, short inOffset, short inLength);

    /**
     * Returns the length of the signature.
     *
     * @return the length in bytes
     */
    short getLength();
}
