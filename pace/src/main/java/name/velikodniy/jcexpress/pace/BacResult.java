package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.sm.SMAlgorithm;
import name.velikodniy.jcexpress.sm.SMContext;
import name.velikodniy.jcexpress.sm.SMKeys;
import name.velikodniy.jcexpress.sm.SMSession;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Result of a successful Basic Access Control run (ICAO Doc 9303-11, 4.3): the session keys and the initial Send
 * Sequence Counter for 3DES Secure Messaging.
 *
 * <p>{@link #toSMContext()} and {@link #toSMSession(SmartCardSession)} start Secure Messaging with two-key 3DES
 * (9.8.6) and {@code SSC = RND.IC (4 least significant bytes) || RND.IFD (4 least significant bytes)} (9.8.6.3).
 * {@link #toString()} does not reveal the keys.</p>
 *
 * @param encKey the session encryption key {@code KSEnc = KDF(K.IFD xor K.IC, 1)} (16 bytes)
 * @param macKey the session MAC key {@code KSMAC = KDF(K.IFD xor K.IC, 2)} (16 bytes)
 * @param ssc    the initial Send Sequence Counter (8 bytes)
 */
public record BacResult(byte[] encKey, byte[] macKey, byte[] ssc) {

    private static final int KEY_LENGTH = 16;
    private static final int SSC_LENGTH = 8;

    /**
     * Creates a BAC result with defensive copies.
     *
     * @throws IllegalArgumentException if a key does not have 16 bytes or the SSC does not have 8 bytes
     */
    public BacResult {
        encKey = copy(encKey, KEY_LENGTH, "encKey");
        macKey = copy(macKey, KEY_LENGTH, "macKey");
        ssc = copy(ssc, SSC_LENGTH, "ssc");
    }

    /** {@inheritDoc} */
    @Override
    public byte[] encKey() {
        return encKey.clone();
    }

    /** {@inheritDoc} */
    @Override
    public byte[] macKey() {
        return macKey.clone();
    }

    /** {@inheritDoc} */
    @Override
    public byte[] ssc() {
        return ssc.clone();
    }

    /**
     * Creates the Secure Messaging context: 3DES, the session keys and the BAC SSC (ICAO Doc 9303-11, 9.8.6).
     *
     * @return a new SM context
     */
    public SMContext toSMContext() {
        return new SMContext(SMAlgorithm.DES3, new SMKeys(encKey, macKey), ssc);
    }

    /**
     * Wraps the given session with the Secure Messaging established by BAC.
     *
     * @param session the session BAC was performed on
     * @return a new SM session
     */
    public SMSession toSMSession(SmartCardSession session) {
        return SMSession.wrap(session, toSMContext());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BacResult(var ek, var mk, var s)
                && Arrays.equals(encKey, ek) && Arrays.equals(macKey, mk) && Arrays.equals(ssc, s);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(encKey) + Arrays.hashCode(macKey)) + Arrays.hashCode(ssc);
    }

    /** Shows the SSC but not the session keys. */
    @Override
    public String toString() {
        return "BacResult[encKey=<redacted>, macKey=<redacted>, ssc=" + HexFormat.of().formatHex(ssc) + "]";
    }

    private static byte[] copy(byte[] value, int length, String name) {
        Objects.requireNonNull(value, name);
        if (value.length != length) {
            throw new IllegalArgumentException(name + " must have " + length + " bytes, got " + value.length);
        }
        return value.clone();
    }
}
