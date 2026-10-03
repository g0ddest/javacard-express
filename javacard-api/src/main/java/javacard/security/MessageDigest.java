package javacard.security;

/**
 * The MessageDigest class is the base class for hashing algorithms.
 *
 * <p>The {@code ALG_*} constants identify hash algorithms and the {@code LENGTH_*} constants give the digest
 * length in bytes of each algorithm.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class MessageDigest {

    /**
     * Constructor for algorithm implementations; applets obtain instances through {@code getInstance}.
     */
    protected MessageDigest() {
        throw new RuntimeException("stub");
    }

    /** No hash algorithm; used where an algorithm selector allows "none". */
    public static final byte ALG_NULL = 0;
    /** Digest algorithm SHA-1. */
    public static final byte ALG_SHA = 1;
    /** Digest algorithm MD5. */
    public static final byte ALG_MD5 = 2;
    /** Digest algorithm RIPEMD-160. */
    public static final byte ALG_RIPEMD160 = 3;
    /** Digest algorithm SHA-256. */
    public static final byte ALG_SHA_256 = 4;
    /** Digest algorithm SHA-384. */
    public static final byte ALG_SHA_384 = 5;
    /** Digest algorithm SHA-512. */
    public static final byte ALG_SHA_512 = 6;
    /** Digest algorithm SHA-224. */
    public static final byte ALG_SHA_224 = 7;
    /** Digest algorithm SHA3-224. */
    public static final byte ALG_SHA3_224 = 8;
    /** Digest algorithm SHA3-256. */
    public static final byte ALG_SHA3_256 = 9;
    /** Digest algorithm SHA3-384. */
    public static final byte ALG_SHA3_384 = 10;
    /** Digest algorithm SHA3-512. */
    public static final byte ALG_SHA3_512 = 11;

    /** Length in bytes of a MD5 digest. */
    public static final byte LENGTH_MD5 = 16;
    /** Length in bytes of a RIPEMD-160 digest. */
    public static final byte LENGTH_RIPEMD160 = 20;
    /** Length in bytes of a SHA-1 digest. */
    public static final byte LENGTH_SHA = 20;
    /** Length in bytes of a SHA-224 digest. */
    public static final byte LENGTH_SHA_224 = 28;
    /** Length in bytes of a SHA-256 digest. */
    public static final byte LENGTH_SHA_256 = 32;
    /** Length in bytes of a SHA-384 digest. */
    public static final byte LENGTH_SHA_384 = 48;
    /** Length in bytes of a SHA-512 digest. */
    public static final byte LENGTH_SHA_512 = 64;
    /** Length in bytes of a SHA3-224 digest. */
    public static final byte LENGTH_SHA3_224 = 28;
    /** Length in bytes of a SHA3-256 digest. */
    public static final byte LENGTH_SHA3_256 = 32;
    /** Length in bytes of a SHA3-384 digest. */
    public static final byte LENGTH_SHA3_384 = 48;
    /** Length in bytes of a SHA3-512 digest. */
    public static final byte LENGTH_SHA3_512 = 64;

    /**
     * Creates a MessageDigest instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the MessageDigest instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static final MessageDigest getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a message digest whose intermediate hash value can be preset with
     * {@link InitializedMessageDigest#setInitialDigest(byte[], short, short, byte[], short, short)}.
     *
     * @param algorithm      one of the {@code ALG_*} constants
     * @param externalAccess {@code true} if the instance may be used from other applet contexts
     * @return the new instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the algorithm is not supported
     */
    public static final InitializedMessageDigest getInitializedMessageDigestInstance(byte algorithm,
            boolean externalAccess) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the algorithm type.
     *
     * @return the algorithm type
     */
    public abstract byte getAlgorithm();

    /**
     * Returns the byte length of the digest.
     *
     * @return the digest length
     */
    public abstract byte getLength();

    /**
     * Generates a hash of all/last input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @param outBuff output buffer
     * @param outOffset offset into output buffer
     * @return number of bytes of hash output
     * @throws CryptoException if the accumulated input exceeds what the algorithm supports
     */
    public abstract short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;

    /**
     * Accumulates a hash of the input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @throws CryptoException if the accumulated input exceeds what the algorithm supports
     */
    public abstract void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException;

    /**
     * Resets the MessageDigest to the initial state.
     */
    public abstract void reset();

    /**
     * Message digest for hashing a complete message in a single {@code doFinal} call. Instances come from a
     * platform pool through {@link #open(byte)} and are returned to it with {@link #close()}, so no persistent
     * memory is consumed.
     */
    public static final class OneShot extends MessageDigest {

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

        @Override
        public void reset() {
            throw new RuntimeException("stub");
        }
    }
}
