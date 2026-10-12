package org.globalplatform;

import javacard.framework.Shareable;
import javacard.security.CryptoException;

/**
 * Signing and key recovery services of a Controlling Authority Security Domain (GlobalPlatform Card
 * Specification v2.3 Amendment A). Part of the package since version 1.2; version 1.8 deprecates it.
 *
 * <p>The simulated cards provide no such service.</p>
 *
 * @deprecated version 1.8 of the package deprecates the interface: applications have no use for it.
 */
@Deprecated
public interface Authority extends Shareable {

    /** Mode of {@link #init}: signature. */
    byte MODE_SIGN = (byte) 0x01;

    /** Mode of {@link #init}: key recovery. */
    byte MODE_KEY_RECOVERY = (byte) 0x02;

    /**
     * Starts an operation.
     *
     * @param theMode one of the {@code MODE} constants
     * @throws CryptoException when the mode is not available
     */
    void init(byte theMode) throws CryptoException;

    /**
     * Signs the accumulated data and the given data.
     *
     * @param inBuff    the buffer with the last data to sign
     * @param inOffset  where the data start
     * @param inLength  the length of the data
     * @param sigBuff   the buffer for the signature
     * @param sigOffset where the signature is written
     * @return the length of the signature
     * @throws CryptoException when the operation fails
     */
    short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset) throws CryptoException;

    /**
     * Accumulates data to sign.
     *
     * @param inBuff   the buffer with the data
     * @param inOffset where the data start
     * @param inLength the length of the data
     * @throws CryptoException when the operation fails
     */
    void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException;

    /**
     * Recovers a key.
     *
     * @param inBuff    the buffer with the key data
     * @param inOffset  where the key data start
     * @param inLength  the length of the key data
     * @param outBuff   the buffer for the result
     * @param outOffset where the result is written
     * @return the length of the result
     * @throws CryptoException when the operation fails
     */
    short recoverKey(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;
}
