package javacard.security;

/**
 * Signature with message recovery (ISO/IEC 9796-2): part of the message is embedded in the signature and
 * recovered when it is verified. A {@link Signature} instance created for such an algorithm also implements
 * this interface.
 */
public interface SignatureMessageRecovery {

    /**
     * Initializes the object with a key and a mode.
     *
     * @param theKey  the signing or verification key
     * @param theMode {@link Signature#MODE_SIGN} or {@link Signature#MODE_VERIFY}
     * @throws CryptoException with ILLEGAL_VALUE if the mode or key is not valid for this algorithm
     */
    void init(Key theKey, byte theMode) throws CryptoException;

    /**
     * Starts a verification by processing the signature; the recoverable part of the message is written back
     * into the signature buffer.
     *
     * @param sigAndRecDataBuff buffer holding the signature, receives the recovered data
     * @param buffOffset        offset of the signature
     * @param sigLength         length of the signature
     * @return the length of the recovered message part
     * @throws CryptoException if the object is not initialized for verification or the signature is malformed
     */
    short beginVerify(byte[] sigAndRecDataBuff, short buffOffset, short sigLength) throws CryptoException;

    /**
     * Completes signing over the message processed so far plus the given final part.
     *
     * @param inBuff          buffer holding the last message part
     * @param inOffset        offset of the last message part
     * @param inLength        length of the last message part
     * @param sigBuff         buffer receiving the signature
     * @param sigOffset       offset of the signature in {@code sigBuff}
     * @param recMsgLen       array receiving the number of message bytes embedded in the signature
     * @param recMsgLenOffset index in {@code recMsgLen} that receives the length
     * @return the length of the signature
     * @throws CryptoException if the object is not initialized for signing
     */
    short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset, short[] recMsgLen,
            short recMsgLenOffset) throws CryptoException;

    /**
     * Completes a verification started with {@link #beginVerify(byte[], short, short)}.
     *
     * @param inBuff   buffer holding the non-recoverable message part
     * @param inOffset offset of that part
     * @param inLength length of that part
     * @return {@code true} if the signature is valid
     * @throws CryptoException if no verification is in progress
     */
    boolean verify(byte[] inBuff, short inOffset, short inLength) throws CryptoException;

    /**
     * Returns the algorithm of this object.
     *
     * @return one of the {@code Signature.ALG_*} constants
     */
    byte getAlgorithm();

    /**
     * Returns the length of the signature in bytes.
     *
     * @return the signature length
     * @throws CryptoException with INVALID_INIT if the object is not initialized
     */
    short getLength() throws CryptoException;

    /**
     * Feeds message data into the signing or verification.
     *
     * @param inBuff   buffer holding the data
     * @param inOffset offset of the data
     * @param inLength length of the data
     * @throws CryptoException if the object is not initialized
     */
    void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException;
}
