package name.velikodniy.jcexpress.sm;

import java.util.Arrays;
import java.util.Objects;

/**
 * Mutable state for an ISO 7816-4 Secure Messaging session.
 *
 * <p>Holds the algorithm suite, the session keys and the Send Sequence Counter (SSC). The SSC is
 * increased before every protected command and every protected response (ICAO Doc 9303-11, 9.8.2);
 * its size matches the cipher block size: 8 bytes for DES3, 16 bytes for AES.</p>
 *
 * <p>A session ends when the chip aborts Secure Messaging, which it does whenever it detects an SM
 * error or receives a plain APDU (9.8.3, 9.8.5), or when the card is reset. {@link SMCodec} and
 * {@link SMSession} then call {@link #terminate(String)}: the key copies held here are wiped and every
 * further wrap or unwrap attempt fails with an {@link SMException} naming the reason. A new session
 * needs a new context (after BAC, PACE or another key agreement).</p>
 *
 * <p>Instances are not thread-safe.</p>
 */
public final class SMContext {

    private final SMAlgorithm algorithm;
    private final byte[] encKey;
    private final byte[] macKey;
    private final byte[] ssc;
    private String terminationReason;

    /**
     * Creates a new SM context.
     *
     * @param algorithm  the algorithm suite (DES3 or AES)
     * @param keys       the encryption and MAC keys (16 or 24 bytes for DES3; 16, 24 or 32 for AES)
     * @param initialSsc the initial Send Sequence Counter (must match block size)
     * @throws IllegalArgumentException if the SSC or a key has the wrong length for the algorithm
     */
    public SMContext(SMAlgorithm algorithm, SMKeys keys, byte[] initialSsc) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(initialSsc, "initialSsc");
        if (initialSsc.length != algorithm.blockSize()) {
            throw new IllegalArgumentException("SSC length " + initialSsc.length
                    + " does not match block size " + algorithm.blockSize());
        }
        this.encKey = checkKey(algorithm, keys.encKey(), "encKey");
        this.macKey = checkKey(algorithm, keys.macKey(), "macKey");
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
     * @throws SMException if the session has been terminated
     */
    public byte[] encKey() {
        requireActive();
        return encKey.clone();
    }

    /**
     * Returns a copy of the MAC key.
     *
     * @return MAC key bytes
     * @throws SMException if the session has been terminated
     */
    public byte[] macKey() {
        requireActive();
        return macKey.clone();
    }

    /**
     * Returns this context's own encryption key array without copying it, so that {@link SMCodec} leaves no key
     * copies behind. Callers must neither modify it nor let it escape; {@link #terminate(String)} wipes it.
     *
     * @return the encryption key array
     * @throws SMException if the session has been terminated
     */
    byte[] encKeyRef() {
        requireActive();
        return encKey;
    }

    /**
     * Returns this context's own MAC key array without copying it (see {@link #encKeyRef()}).
     *
     * @return the MAC key array
     * @throws SMException if the session has been terminated
     */
    byte[] macKeyRef() {
        requireActive();
        return macKey;
    }

    /**
     * Increments the SSC by 1 (big-endian).
     *
     * <p>Called before each MAC computation. The counter is treated as an
     * unsigned big-endian integer and incremented with carry propagation.</p>
     */
    public void incrementSsc() {
        increment(ssc);
    }

    /**
     * Returns the value the SSC will have after the next {@link #incrementSsc()}, without changing it.
     *
     * <p>Lets {@link SMCodec} build a protected command with the new SSC and commit the increment only once the
     * command has been built completely.</p>
     *
     * @return a copy of SSC + 1
     */
    byte[] nextSsc() {
        byte[] next = ssc.clone();
        increment(next);
        return next;
    }

    private static void increment(byte[] counter) {
        for (int i = counter.length - 1; i >= 0; i--) {
            int val = (counter[i] & 0xFF) + 1;
            counter[i] = (byte) val;
            if (val <= 0xFF) {
                break; // no carry
            }
            // carry: continue to next byte
        }
    }

    /**
     * Returns whether the Secure Messaging session has ended.
     *
     * @return true after {@link #terminate(String)} was called
     */
    public boolean isTerminated() {
        return terminationReason != null;
    }

    /**
     * Returns why the session ended.
     *
     * @return the reason given to {@link #terminate(String)}, or {@code null} while the session is active
     */
    public String terminationReason() {
        return terminationReason;
    }

    /**
     * Ends the Secure Messaging session: wipes the key copies held by this context and makes every
     * later use fail. Calling it again keeps the first reason.
     *
     * <p>Mirrors the chip side of ICAO Doc 9303-11, 9.8.3: once Secure Messaging is aborted, the
     * session keys are deleted.</p>
     *
     * @param reason why the session ended (shown in later exceptions)
     */
    public void terminate(String reason) {
        if (terminationReason != null) {
            return;
        }
        terminationReason = Objects.requireNonNull(reason, "reason");
        Arrays.fill(encKey, (byte) 0);
        Arrays.fill(macKey, (byte) 0);
    }

    /**
     * Throws if the session has been terminated.
     *
     * @throws SMException naming the termination reason
     */
    void requireActive() {
        if (terminationReason != null) {
            throw new SMException("Secure Messaging session terminated: " + terminationReason);
        }
    }

    private static byte[] checkKey(SMAlgorithm algorithm, byte[] key, String name) {
        boolean valid = switch (algorithm) {
            case DES3 -> key.length == 16 || key.length == 24;
            case AES -> key.length == 16 || key.length == 24 || key.length == 32;
        };
        if (!valid) {
            throw new IllegalArgumentException(name + " has invalid length " + key.length + " for " + algorithm);
        }
        return key;
    }
}
