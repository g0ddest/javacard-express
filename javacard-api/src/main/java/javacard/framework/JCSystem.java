package javacard.framework;

/**
 * Provides methods to control the Java Card runtime environment.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class JCSystem {

    public static final byte MEMORY_TYPE_PERSISTENT = 0;
    public static final byte MEMORY_TYPE_TRANSIENT_RESET = 1;
    public static final byte MEMORY_TYPE_TRANSIENT_DESELECT = 2;

    public static final byte NOT_A_TRANSIENT_OBJECT = 0;
    public static final byte CLEAR_ON_RESET = 1;
    public static final byte CLEAR_ON_DESELECT = 2;

    /**
     * Checks if the given object is transient.
     *
     * @param theObj the object to check
     * @return the transient type or NOT_A_TRANSIENT_OBJECT
     */
    public static byte isTransient(Object theObj) {
        return NOT_A_TRANSIENT_OBJECT;
    }

    /**
     * Creates a transient byte array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient byte array
     */
    public static byte[] makeTransientByteArray(short length, byte event) {
        return new byte[length];
    }

    /**
     * Creates a transient short array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient short array
     */
    public static short[] makeTransientShortArray(short length, byte event) {
        return new short[length];
    }

    /**
     * Creates a transient boolean array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient boolean array
     */
    public static boolean[] makeTransientBooleanArray(short length, byte event) {
        return new boolean[length];
    }

    /**
     * Creates a transient Object array.
     *
     * @param length array length
     * @param event  the clear event type
     * @return a new transient Object array
     */
    public static Object[] makeTransientObjectArray(short length, byte event) {
        return new Object[length];
    }

    /**
     * Returns the Java Card API version.
     *
     * @return version as a short (0x0305 for 3.0.5)
     */
    public static short getVersion() {
        return 0x0305;
    }

    /**
     * Returns the AID of the currently active applet.
     *
     * @return the applet AID, or null
     */
    public static AID getAID() {
        return null;
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
        return null;
    }

    /**
     * Returns the AID of the previously active applet context.
     *
     * @return the previous context AID, or null
     */
    public static AID getPreviousContextAID() {
        return null;
    }

    /**
     * Returns the available memory of the given type.
     *
     * @param memoryType the type of memory to query
     * @return available memory in bytes
     */
    public static short getAvailableMemory(byte memoryType) {
        return Short.MAX_VALUE;
    }

    /**
     * Returns the logical channel number assigned to the current applet.
     *
     * @return the channel number
     */
    public static byte getAssignedChannel() {
        return 0;
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
        return 0;
    }

    /**
     * Returns the maximum number of bytes that can be committed in a transaction.
     *
     * @return max commit capacity
     */
    public static short getMaxCommitCapacity() {
        return Short.MAX_VALUE;
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
        return null;
    }

    /**
     * Returns whether the runtime supports object deletion.
     *
     * @return true if object deletion is supported
     */
    public static boolean isObjectDeletionSupported() {
        return false;
    }
}
