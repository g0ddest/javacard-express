package name.velikodniy.jcexpress.sm;

import java.util.Objects;

/**
 * Mutable state for an ISO 7816-4 Secure Messaging session.
 *
 * <p>Holds the algorithm suite, keys, and the Send Sequence Counter (SSC).
 * The SSC is incremented before each MAC computation (both command wrapping
 * and response unwrapping).</p>
 *
 * <p>SSC size matches the cipher block size: 8 bytes for DES3, 16 bytes for AES.</p>
 */
public final class SMContext {

    private final SMAlgorithm algorithm;
    private final SMKeys keys;
    private final byte[] ssc;

    /**
     * Creates a new SM context.
     *
     * @param algorithm  the algorithm suite (DES3 or AES)
     * @param keys       the encryption and MAC keys
     * @param initialSsc the initial Send Sequence Counter (must match block size)
     */
    public SMContext(SMAlgorithm algorithm, SMKeys keys, byte[] initialSsc) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.keys = Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(initialSsc, "initialSsc");
        if (initialSsc.length != algorithm.blockSize()) {
            throw new IllegalArgumentException("SSC length " + initialSsc.length
                    + " does not match block size " + algorithm.blockSize());
        }
        this.ssc = initialSsc.clone();
    }

    /**
     * Returns the algorithm suite.
     *
     * @return the algorithm
     */
    public SMAlgorithm algorithm() {
        return algorithm;
    }

    /**
     * Returns a copy of the current SSC.
     *
     * @return SSC bytes
     */
    public byte[] ssc() {
        return ssc.clone();
    }

    /**
     * Returns a copy of the encryption key.
     *
     * @return encryption key bytes
     */
    public byte[] encKey() {
        return keys.encKey();
    }

    /**
     * Returns a copy of the MAC key.
     *
     * @return MAC key bytes
     */
    public byte[] macKey() {
        return keys.macKey();
    }

    /**
     * Increments the SSC by 1 (big-endian).
     *
     * <p>Called before each MAC computation. The counter is treated as an
     * unsigned big-endian integer and incremented with carry propagation.</p>
     */
    public void incrementSsc() {
        for (int i = ssc.length - 1; i >= 0; i--) {
            int val = (ssc[i] & 0xFF) + 1;
            ssc[i] = (byte) val;
            if (val <= 0xFF) {
                break; // no carry
            }
            // carry: continue to next byte
        }
    }
}
