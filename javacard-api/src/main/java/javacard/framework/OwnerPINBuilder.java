package javacard.framework;

/**
 * Factory for the owner PIN implementations offered by the platform: the classic {@link OwnerPIN} and the
 * {@link OwnerPINx} variants.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class OwnerPINBuilder {

    /** Builds a plain {@link OwnerPIN}. */
    public static final byte OWNER_PIN = 1;
    /** Builds an {@link OwnerPINx}. */
    public static final byte OWNER_PIN_X = 2;
    /** Builds an {@link OwnerPINxWithPredecrement}. */
    public static final byte OWNER_PIN_X_WITH_PREDECREMENT = 3;

    private OwnerPINBuilder() {
    }

    /**
     * Creates an owner PIN of the requested kind.
     *
     * @param tryLimit   number of consecutive wrong presentations allowed before the PIN is blocked
     * @param maxPINSize maximum PIN length in bytes
     * @param type       {@link #OWNER_PIN}, {@link #OWNER_PIN_X} or {@link #OWNER_PIN_X_WITH_PREDECREMENT}
     * @return the new PIN object
     * @throws PINException if a parameter is invalid or the requested kind is not supported
     */
    public static PIN buildOwnerPIN(byte tryLimit, byte maxPINSize, byte type) throws PINException {
        throw new RuntimeException("stub");
    }
}
