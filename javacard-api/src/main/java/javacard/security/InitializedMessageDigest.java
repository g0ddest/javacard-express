package javacard.security;

/**
 * Message digest whose intermediate hash value can be preset, so that hashing can resume from a state computed
 * elsewhere (for example a hash of a long prefix computed off-card). Instances are created with
 * {@link MessageDigest#getInitializedMessageDigestInstance(byte, boolean)}.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class InitializedMessageDigest extends MessageDigest {

    /**
     * Constructor for algorithm implementations; applets obtain instances through
     * {@link MessageDigest#getInitializedMessageDigestInstance(byte, boolean)}.
     */
    protected InitializedMessageDigest() {
        throw new RuntimeException("stub");
    }

    /**
     * Presets the intermediate hash value and the number of message bytes it covers.
     *
     * @param initialDigestBuf    buffer holding the intermediate hash value
     * @param initialDigestOffset offset of the hash value
     * @param initialDigestLength length of the hash value
     * @param digestedMsgLenBuf   buffer holding the number of bytes already hashed, big-endian
     * @param digestedMsgLenOffset offset of that number
     * @param digestedMsgLenLength length of that number in bytes
     * @throws CryptoException with ILLEGAL_VALUE if a length is not valid for the algorithm
     */
    public abstract void setInitialDigest(byte[] initialDigestBuf, short initialDigestOffset,
            short initialDigestLength, byte[] digestedMsgLenBuf, short digestedMsgLenOffset,
            short digestedMsgLenLength) throws CryptoException;

    /**
     * One-shot variant of {@link InitializedMessageDigest}: instances come from a platform pool through
     * {@link #open(byte)} and are returned to it with {@link #close()}.
     */
    public static final class OneShot extends InitializedMessageDigest {

        private OneShot() {
        }

        /**
         * Takes an instance for the given algorithm from the platform pool.
         *
         * @param algorithm one of the {@code MessageDigest.ALG_*} constants
         * @return an open instance
         * @throws CryptoException with NO_SUCH_ALGORITHM if the algorithm is not supported, or ILLEGAL_USE if no
         *                         instance is available
         */
        public static final OneShot open(byte algorithm) throws CryptoException {
            throw new RuntimeException("stub");
        }

        /**
         * Returns this instance to the platform pool; it must not be used afterwards.
         */
        public void close() {
            throw new RuntimeException("stub");
        }

        @Override
        public void setInitialDigest(byte[] initialDigestBuf, short initialDigestOffset,
                short initialDigestLength, byte[] digestedMsgLenBuf, short digestedMsgLenOffset,
                short digestedMsgLenLength) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public byte getAlgorithm() {
            throw new RuntimeException("stub");
        }

        @Override
        public byte getLength() {
            throw new RuntimeException("stub");
        }

        @Override
        public short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
                throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public void reset() {
            throw new RuntimeException("stub");
        }

        /**
         * Not supported: a one-shot digest processes the whole message in {@code doFinal}.
         *
         * @param inBuff   input buffer
         * @param inOffset offset into input buffer
         * @param inLength length of input data
         * @throws CryptoException with ILLEGAL_USE always
         */
        @Override
        public void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException {
            throw new RuntimeException("stub");
        }
    }
}
