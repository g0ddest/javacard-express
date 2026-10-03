package javacardx.framework.math;

/**
 * Sets the parity bit (the least significant bit) of each byte, as required for example by DES keys.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class ParityBit {

    /**
     * Creates an instance; the only method is static.
     */
    public ParityBit() {
        throw new RuntimeException("stub");
    }

    /**
     * Adjusts the least significant bit of every byte so that the byte has the requested parity.
     *
     * @param bArray array holding the bytes
     * @param bOff   offset of the first byte
     * @param bLen   number of bytes
     * @param isEven {@code true} for even parity, {@code false} for odd parity
     */
    public static void set(byte[] bArray, short bOff, short bLen, boolean isEven) {
        throw new RuntimeException("stub");
    }
}
