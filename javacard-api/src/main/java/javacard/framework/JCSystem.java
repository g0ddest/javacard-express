package javacard.framework;

/**
 * Provides methods to control the Java Card runtime environment.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class JCSystem {

    private JCSystem() {
    }

    /** Memory type: persistent memory. */
    public static final byte MEMORY_TYPE_PERSISTENT = 0;
    /** Memory type: transient memory of {@link #CLEAR_ON_RESET} objects. */
    public static final byte MEMORY_TYPE_TRANSIENT_RESET = 1;
    /** Memory type: transient memory of {@link #CLEAR_ON_DESELECT} objects. */
    public static final byte MEMORY_TYPE_TRANSIENT_DESELECT = 2;

    /** Result of {@code isTransient}: the object is persistent. */
    public static final byte NOT_A_TRANSIENT_OBJECT = 0;
    /** Transient object event: the contents are cleared when the card is reset. */
    public static final byte CLEAR_ON_RESET = 1;
    /** Transient object event: the contents are cleared when the owning applet is deselected (and on card reset). */
    public static final byte CLEAR_ON_DESELECT = 2;

    /** Array type selector for {@link #makeGlobalArray(byte, short)}: {@code boolean[]}. */
    public static final byte ARRAY_TYPE_BOOLEAN = 1;
    /** Array type selector for {@link #makeGlobalArray(byte, short)}: {@code byte[]}. */
    public static final byte ARRAY_TYPE_BYTE = 2;
    /** Array type selector for {@link #makeGlobalArray(byte, short)}: {@code short[]}. */
    public static final byte ARRAY_TYPE_SHORT = 3;
    /** Array type selector for {@link #makeGlobalArray(byte, short)}: {@code int[]}. */
    public static final byte ARRAY_TYPE_INT = 4;
    /** Array type selector for {@link #makeGlobalArray(byte, short)}: {@code Object[]}. */
    public static final byte ARRAY_TYPE_OBJECT = 5;

    /**
     * Checks if the given object is transient.
     *
     * @param theObj the object to check
     * @return the transient type or NOT_A_TRANSIENT_OBJECT
     */
    public static byte isTransient(Object theObj) {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a transient byte array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient byte array
     */
    public static byte[] makeTransientByteArray(short length, byte event) {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a transient short array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient short array
     */
    public static short[] makeTransientShortArray(short length, byte event) {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a transient boolean array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient boolean array
     */
    public static boolean[] makeTransientBooleanArray(short length, byte event) {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a transient Object array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient Object array
     */
    public static Object[] makeTransientObjectArray(short length, byte event) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the Java Card API version.
     *
     * @return version as a short (0x0305 for 3.0.5)
     */
    public static short getVersion() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the AID of the currently active applet.
     *
     * @return the applet AID, or null
     */
    public static AID getAID() {
        throw new RuntimeException("stub");
    }

    /**
     * Looks up an AID in the registry.
     *
     * @param buffer byte array containing the AID
     * @param offset starting offset
     * @param length length of the AID
     * @return the matching AID, or null
     */
    public static AID lookupAID(byte[] buffer, short offset, byte length) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the AID of the previously active applet context.
     *
     * @return the previous context AID, or null
     */
    public static AID getPreviousContextAID() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the available memory of the given type.
     *
     * @param memoryType the type of memory to query
     * @return available memory in bytes
     */
    public static short getAvailableMemory(byte memoryType) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the logical channel number assigned to the current applet.
     *
     * @return the channel number
     */
    public static byte getAssignedChannel() {
        throw new RuntimeException("stub");
    }

    /**
     * Begins a transaction.
     */
    public static void beginTransaction() {
        throw new RuntimeException("stub");
    }

    /**
     * Aborts the current transaction.
     */
    public static void abortTransaction() {
        throw new RuntimeException("stub");
    }

    /**
     * Commits the current transaction.
     */
    public static void commitTransaction() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the current transaction nesting depth.
     *
     * @return the transaction depth
     */
    public static byte getTransactionDepth() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the maximum number of bytes that can be committed in a transaction.
     *
     * @return max commit capacity
     */
    public static short getMaxCommitCapacity() {
        throw new RuntimeException("stub");
    }

    /**
     * Requests that the runtime perform object deletion.
     */
    public static void requestObjectDeletion() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns a shareable interface object from the specified server applet.
     *
     * @param serverAID the server applet AID
     * @param parameter a parameter byte
     * @return the shareable interface object, or null
     */
    public static Shareable getAppletShareableInterfaceObject(AID serverAID, byte parameter) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns whether the runtime supports object deletion.
     *
     * @return true if object deletion is supported
     */
    public static boolean isObjectDeletionSupported() {
        throw new RuntimeException("stub");
    }

    /**
     * Reports the available memory of the given type as a 32-bit quantity, for platforms with more than
     * 32767 bytes available. The value is written as two shorts: the high word at {@code buffer[offset]} and the
     * low word at {@code buffer[offset + 1]}.
     *
     * @param buffer     destination array for the two shorts
     * @param offset     index of the first short in {@code buffer}
     * @param memoryType {@link #MEMORY_TYPE_PERSISTENT}, {@link #MEMORY_TYPE_TRANSIENT_RESET} or
     *                   {@link #MEMORY_TYPE_TRANSIENT_DESELECT}
     * @throws SystemException if {@code memoryType} is not a valid memory type
     */
    public static void getAvailableMemory(short[] buffer, short offset, byte memoryType) throws SystemException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a global, transient (clear-on-reset) array that every applet context can access, for example to
     * pass data to another applet through a shareable interface.
     *
     * @param type   one of the {@code ARRAY_TYPE_*} constants
     * @param length number of elements
     * @return the new array
     * @throws SystemException if the type is invalid or there is not enough transient memory
     */
    public static Object makeGlobalArray(byte type, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns how many bytes of the commit buffer are still free in the current transaction.
     *
     * @return the unused commit capacity in bytes
     */
    public static short getUnusedCommitCapacity() {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the applet with the given AID is currently selected on any logical channel.
     *
     * @param theApplet AID of the applet instance
     * @return {@code true} if that applet is active
     */
    public static boolean isAppletActive(AID theApplet) {
        throw new RuntimeException("stub");
    }
}
