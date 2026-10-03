package javacard.framework;

/**
 * Creates and checks integrity-sensitive arrays: arrays whose content the platform protects against
 * corruption, for example by keeping a checksum.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class SensitiveArrays {

    private SensitiveArrays() {
    }

    /**
     * Checks the integrity of an integrity-sensitive array.
     *
     * @param obj the array to check
     * @throws SecurityException    if the content is found to be corrupted
     * @throws NullPointerException if {@code obj} is {@code null}
     */
    public static void assertIntegrity(Object obj) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether an array was created as integrity-sensitive.
     *
     * @param obj the array to examine
     * @return {@code true} if the array is integrity-sensitive
     */
    public static boolean isIntegritySensitive(Object obj) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the platform supports integrity-sensitive arrays.
     *
     * @return {@code true} if they are supported
     */
    public static boolean isIntegritySensitiveArraysSupported() {
        throw new RuntimeException("stub");
    }

    /**
     * Creates an integrity-sensitive array.
     *
     * @param type   one of the {@code JCSystem.ARRAY_TYPE_*} constants
     * @param memory one of the {@code JCSystem.MEMORY_TYPE_*} constants
     * @param length number of elements
     * @return the new array
     * @throws SystemException if the type or memory kind is not supported or memory is exhausted
     */
    public static Object makeIntegritySensitiveArray(byte type, byte memory, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Sets every element of an integrity-sensitive array to its default value.
     *
     * @param obj the array to clear
     * @return the number of elements cleared
     * @throws TransactionException if clearing a persistent array overflows the commit buffer
     */
    public static short clearArray(Object obj) throws TransactionException {
        throw new RuntimeException("stub");
    }
}
