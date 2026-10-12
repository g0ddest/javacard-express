package org.globalplatform;

import javacard.framework.Shareable;
import javacard.security.CryptoException;

/**
 * A service of the Controlling Authority Security Domain for other Security Domains: signing data and recovering
 * a cryptographic key, with an algorithm and credentials that the caller does not choose. It is offered as a
 * Global Service of the family {@link GPSystem#FAMILY_AUTHORITY}.
 *
 * <p>Since version 1.2 of the package.</p>
 *
 * @deprecated Deprecated in version 1.8 of the package: applications that are not Security Domains have no use
 *             for it.
 */
@Deprecated
public interface Authority extends Shareable {

    /** Mode for {@link #init}: sign data. Since version 1.2 of the package. */
    byte MODE_SIGN = 1;
    /** Mode for {@link #init}: recover a key. Since version 1.2 of the package. */
    byte MODE_KEY_RECOVERY = 2;

    /**
     * Starts an operation.
     *
     * <p>Since version 1.2 of the package.</p>
     *
     * @param theMode {@link #MODE_SIGN} or {@link #MODE_KEY_RECOVERY}
     * @throws CryptoException with reason {@code ILLEGAL_VALUE} for another mode
     */
    void init(byte theMode) throws CryptoException;

    /**
     * Signs the input data, together with the data given to {@link #update} before, and leaves the object ready
     * for the next signature. Both arrays are global arrays and may overlap.
     *
     * <p>Since version 1.2 of the package.</p>
     *
     * @param inBuff    the array that holds the last, or all, data to sign
     * @param inOffset  offset of the data to sign
     * @param inLength  length of the data to sign
     * @param sigBuff   the array the signature is written to
     * @param sigOffset offset where the signature starts
     * @return the length of the signature
     * @throws CryptoException with reason {@code INVALID_INIT} when the object is not initialized for signing,
     *                         {@code ILLEGAL_USE} when the algorithm does not pad and the data is not block aligned
     */
    short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset)
            throws CryptoException;

    /**
     * Adds input data to the operation in progress, for data that does not arrive in one array.
     *
     * <p>Since version 1.2 of the package.</p>
     *
     * @param inBuff   the array that holds the data; a global array
     * @param inOffset offset of the data
     * @param inLength length of the data
     * @throws CryptoException with reason {@code INVALID_INIT} when the object is not initialized
     */
    void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException;

    /**
     * Recovers a key from the data structures in the input, after checking where the key comes from and that it is
     * intact, and writes it as a key data structure. The object is ready for the next key afterwards. Both arrays
     * are global arrays and may overlap.
     *
     * <p>Since version 1.2 of the package.</p>
     *
     * @param inBuff    the array that holds the input data
     * @param inOffset  offset of the input data
     * @param inLength  length of the input data
     * @param outBuff   the array the key data structure is written to
     * @param outOffset offset where the key data structure starts
     * @return the length of the key data structure, or 0 if the key could not be recovered
     * @throws CryptoException with reason {@code INVALID_INIT} when the object is not initialized for key recovery
     */
    short recoverKey(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;
}
