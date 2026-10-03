package javacardx.external;

/**
 * Factory for access objects to memory subsystems outside the Java Card heap, such as a MIFARE memory area.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class Memory {

    /** MIFARE memory subsystem. */
    public static final byte MEMORY_TYPE_MIFARE = 1;
    /** Extended store memory subsystem. */
    public static final byte MEMORY_TYPE_EXTENDED_STORE = 2;

    private Memory() {
    }

    /**
     * Returns an access object for a memory subsystem.
     *
     * @param memoryType       one of the {@code MEMORY_TYPE_*} constants
     * @param memorySize       array receiving the size of the memory, or {@code null}
     * @param memorySizeOffset index in {@code memorySize} at which the size is written
     * @return the access object
     * @throws ExternalException with NO_SUCH_SUBSYSTEM if the subsystem is not available
     */
    public static final MemoryAccess getMemoryAccessInstance(byte memoryType, short[] memorySize,
            short memorySizeOffset) throws ExternalException {
        throw new RuntimeException("stub");
    }
}
