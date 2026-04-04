package javacard.framework;

/**
 * Provides methods for handling ISO 7816-4 APDUs.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class APDU {

    public static final byte STATE_INITIAL = 0;
    public static final byte STATE_PARTIAL_INCOMING = 1;
    public static final byte STATE_FULL_INCOMING = 2;
    public static final byte STATE_OUTGOING = 3;
    public static final byte STATE_OUTGOING_LENGTH_KNOWN = 4;
    public static final byte STATE_PARTIAL_OUTGOING = 5;
    public static final byte STATE_FULL_OUTGOING = 6;
    public static final byte STATE_ERROR_NO_T0_GETRESPONSE = 7;
    public static final byte STATE_ERROR_T1_IFD_ABORT = 8;
    public static final byte STATE_ERROR_IO = 9;
    public static final byte STATE_ERROR_NO_T0_REISSUE = 10;

    public static final byte PROTOCOL_MEDIA_DEFAULT = 0;
    public static final byte PROTOCOL_MEDIA_CONTACTLESS_TYPE_A = 0;
    public static final byte PROTOCOL_MEDIA_CONTACTLESS_TYPE_B = 0;
    public static final byte PROTOCOL_MEDIA_USB = 0;
    public static final byte PROTOCOL_T0 = 0;
    public static final byte PROTOCOL_T1 = 1;

    /**
     * Returns the APDU buffer.
     *
     * @return the APDU buffer byte array
     */
    public byte[] getBuffer() {
        return null;
    }

    /**
     * Returns the ISO 7816 transport protocol in use.
     *
     * @return the protocol type
     */
    public static byte getProtocol() {
        return 0;
    }

    /**
     * Returns the configured incoming block size.
     *
     * @return incoming block size
     */
    public static byte getInBlockSize() {
        return 0;
    }

    /**
     * Returns the configured outgoing block size.
     *
     * @return outgoing block size
     */
    public static byte getOutBlockSize() {
        return 0;
    }

    /**
     * Returns the NAD byte.
     *
     * @return the NAD byte
     */
    public byte getNAD() {
        return 0;
    }

    /**
     * Sets the data transfer direction to outgoing.
     *
     * @return the expected length
     * @throws APDUException on error
     */
    public short setOutgoing() throws APDUException {
        return 0;
    }

    /**
     * Sets the data transfer direction to outgoing without chaining.
     *
     * @return the expected length
     * @throws APDUException on error
     */
    public short setOutgoingNoChaining() throws APDUException {
        return 0;
    }

    /**
     * Sets the actual length of outgoing data.
     *
     * @param len the length
     * @throws APDUException on error
     */
    public void setOutgoingLength(short len) throws APDUException {
        throw new RuntimeException("stub");
    }

    /**
     * Receives bytes into the APDU buffer.
     *
     * @param bOff the offset
     * @return number of bytes received
     * @throws APDUException on error
     */
    public short receiveBytes(short bOff) throws APDUException {
        return 0;
    }

    /**
     * Sets the incoming transfer mode and receives the first block.
     *
     * @return number of bytes received
     * @throws APDUException on error
     */
    public short setIncomingAndReceive() throws APDUException {
        return 0;
    }

    /**
     * Sends bytes from the APDU buffer.
     *
     * @param bOff starting offset
     * @param len  number of bytes to send
     * @throws APDUException on error
     */
    public void sendBytes(short bOff, short len) throws APDUException {
        throw new RuntimeException("stub");
    }

    /**
     * Sends bytes from a specified byte array.
     *
     * @param outData source byte array
     * @param bOff    starting offset
     * @param len     number of bytes to send
     * @throws APDUException on error
     */
    public void sendBytesLong(byte[] outData, short bOff, short len) throws APDUException {
        throw new RuntimeException("stub");
    }

    /**
     * Convenience method to set outgoing and send in one call.
     *
     * @param bOff starting offset in the APDU buffer
     * @param len  number of bytes to send
     * @throws APDUException on error
     */
    public void setOutgoingAndSend(short bOff, short len) throws APDUException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the maximum number of bytes that can be committed in a transaction.
     *
     * @return max commit capacity
     */
    public static short getMaxCommitCapacity() {
        return 0;
    }

    /**
     * Returns the current APDU processing state.
     *
     * @return the state
     */
    public byte getCurrentState() {
        return 0;
    }

    /**
     * Requests an extension of the ISO 7816-3 waiting time.
     */
    public static void waitExtension() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the offset within the APDU buffer for the command data (C-data).
     *
     * @return the offset to the C-data field
     * @since 3.0.5
     */
    public short getOffsetCdata() {
        return 0;
    }
}
