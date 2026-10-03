package javacard.framework;

/**
 * Encapsulates the Application Identifier (AID) associated with an applet.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class AID {

    /**
     * Constructs an AID from a byte array.
     *
     * @param bArray byte array containing the AID
     * @param offset starting offset in the array
     * @param length length of the AID (5-16 bytes)
     */
    public AID(byte[] bArray, short offset, byte length) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies the AID bytes into the destination array.
     *
     * @param dest   destination byte array
     * @param offset starting offset in dest
     * @return the length of the AID
     */
    public final byte getBytes(byte[] dest, short offset) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies part of the AID bytes into the destination array.
     *
     * @param aidOffset offset within this AID of the first byte to copy
     * @param dest      destination byte array
     * @param oOffset   starting offset in {@code dest}
     * @param oLength   number of bytes to copy; 0 copies everything from {@code aidOffset} to the end of the AID
     * @return the number of bytes copied
     * @throws NullPointerException           if {@code dest} is {@code null}
     * @throws ArrayIndexOutOfBoundsException if the copy would access outside {@code dest} or the AID
     * @throws SecurityException              if {@code dest} is not accessible in the caller's context
     */
    public final byte getPartialBytes(short aidOffset, byte[] dest, short oOffset, byte oLength)
            throws NullPointerException, ArrayIndexOutOfBoundsException, SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Compares this AID with a byte array.
     *
     * @param bArray byte array to compare
     * @param offset starting offset in the array
     * @param length length to compare
     * @return true if equal
     */
    public final boolean equals(byte[] bArray, short offset, byte length) {
        throw new RuntimeException("stub");
    }

    @Override
    public final boolean equals(Object anObject) {
        throw new RuntimeException("stub");
    }

    /**
     * Checks partial equality with a byte array.
     *
     * @param bArray byte array to compare
     * @param offset starting offset
     * @param length length to compare
     * @return true if the partial match succeeds
     */
    public final boolean partialEquals(byte[] bArray, short offset, byte length) {
        throw new RuntimeException("stub");
    }

    /**
     * Compares the RID (first 5 bytes) of this AID with another.
     *
     * @param otherAID the AID to compare with
     * @return true if RIDs match
     */
    public final boolean RIDEquals(AID otherAID) {
        throw new RuntimeException("stub");
    }
}
