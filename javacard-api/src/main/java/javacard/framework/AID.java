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
    }

    /**
     * Copies the AID bytes into the destination array.
     *
     * @param dest   destination byte array
     * @param offset starting offset in dest
     * @return the length of the AID
     */
    public byte getBytes(byte[] dest, short offset) {
        return 0;
    }

    /**
     * Compares this AID with a byte array.
     *
     * @param bArray byte array to compare
     * @param offset starting offset in the array
     * @param length length to compare
     * @return true if equal
     */
    public boolean equals(byte[] bArray, short offset, byte length) {
        return false;
    }

    @Override
    public boolean equals(Object anObject) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    /**
     * Checks partial equality with a byte array.
     *
     * @param bArray byte array to compare
     * @param offset starting offset
     * @param length length to compare
     * @return true if the partial match succeeds
     */
    public boolean partialEquals(byte[] bArray, short offset, byte length) {
        return false;
    }

    /**
     * Compares the RID (first 5 bytes) of this AID with another.
     *
     * @param otherAID the AID to compare with
     * @return true if RIDs match
     */
    public boolean RIDEquals(AID otherAID) {
        return false;
    }
}
