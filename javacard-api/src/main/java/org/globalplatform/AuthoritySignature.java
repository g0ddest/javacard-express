package org.globalplatform;

import javacard.framework.Shareable;

/**
 * A service of the Controlling Authority Security Domain that signs data for an application. The application
 * chooses a mode, not the algorithm or the key; the output names the mode, the algorithm and the key next to the
 * signature. It is offered as a Global Service of the family {@link GPSystem#FAMILY_AUTHORITY_SIGNATURE}.
 *
 * <p>Since version 1.7 of the package.</p>
 */
public interface AuthoritySignature extends Shareable {

    /**
     * Mode for {@link #init}: the input data is signed as it is, to protect its integrity; the mode takes no
     * parameters. Since version 1.7 of the package.
     */
    byte MODE_INTEGRITY = 1;

    /**
     * Starts a signature in a mode. Any application may call at any time; the other methods then accept only the
     * application that called last. Failures are reported as a {@code CryptoException}: {@code ILLEGAL_VALUE} for
     * an unknown mode, {@code UNINITIALIZED_KEY} when the signature key is not available.
     *
     * <p>Since version 1.7 of the package.</p>
     *
     * @param mode   {@link #MODE_INTEGRITY}
     * @param params the array that holds the parameters of the mode, a global array; null if the mode has none
     * @param offset offset of the parameters
     * @param length length of the parameters
     */
    void init(byte mode, byte[] params, short offset, short length);

    /**
     * Signs the input data, together with the data given to {@link #update} before, and writes the output: the
     * signature and the information about caller, algorithm and key that the mode defines. The object is ready for
     * the next signature afterwards. A {@code CryptoException} with reason {@code INVALID_INIT} reports that the
     * caller did not initialize the object.
     *
     * <p>Since version 1.7 of the package.</p>
     *
     * @param inBuff    the array that holds the last, or all, input data; a global array
     * @param inOffset  offset of the input data
     * @param inLength  length of the input data
     * @param sigBuff   the array the output is written to; a global array
     * @param sigOffset offset where the output starts
     * @return the number of bytes written to {@code sigBuff}
     */
    short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset);

    /**
     * Adds input data to the signature in progress, for data that does not arrive in one array. A
     * {@code CryptoException} with reason {@code INVALID_INIT} reports that the caller did not initialize the
     * object.
     *
     * <p>Since version 1.7 of the package.</p>
     *
     * @param inBuff   the array that holds the input data; a global array
     * @param inOffset offset of the input data
     * @param inLength length of the input data
     */
    void update(byte[] inBuff, short inOffset, short inLength);

    /**
     * Returns how many bytes {@link #sign} will write. A {@code CryptoException} with reason {@code INVALID_INIT}
     * reports that the caller did not initialize the object.
     *
     * <p>Since version 1.7 of the package.</p>
     *
     * @return the length of the output in bytes
     */
    short getLength();
}
