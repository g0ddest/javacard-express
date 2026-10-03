package javacard.framework;

/**
 * Provides methods for handling ISO 7816-4 APDUs.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class APDU {

    /** APDU state: no command data has been received and no response data sent yet. */
    public static final byte STATE_INITIAL = 0;
    /** APDU state: part of the command data has been received. */
    public static final byte STATE_PARTIAL_INCOMING = 1;
    /** APDU state: all command data has been received. */
    public static final byte STATE_FULL_INCOMING = 2;
    /** APDU state: the applet switched to sending ({@code setOutgoing}). */
    public static final byte STATE_OUTGOING = 3;
    /** APDU state: the response length has been set ({@code setOutgoingLength}). */
    public static final byte STATE_OUTGOING_LENGTH_KNOWN = 4;
    /** APDU state: part of the response data has been sent. */
    public static final byte STATE_PARTIAL_OUTGOING = 5;
    /** APDU state: all response data has been sent. */
    public static final byte STATE_FULL_OUTGOING = 6;
    /** APDU error state: with T=0 the terminal did not send the expected GET RESPONSE. */
    public static final byte STATE_ERROR_NO_T0_GETRESPONSE = -1;
    /** APDU error state: with T=1 the terminal aborted the block chain. */
    public static final byte STATE_ERROR_T1_IFD_ABORT = -2;
    /** APDU error state: a transmission error occurred. */
    public static final byte STATE_ERROR_IO = -3;
    /** APDU error state: with T=0 the terminal did not re-send the command with the corrected Le. */
    public static final byte STATE_ERROR_NO_T0_REISSUE = -4;

    /** Media part of {@link #getProtocol()}: the contact interface (ISO/IEC 7816-3). */
    public static final byte PROTOCOL_MEDIA_DEFAULT = 0;
    /** Media part of {@link #getProtocol()}: contactless ISO/IEC 14443 Type A. */
    public static final byte PROTOCOL_MEDIA_CONTACTLESS_TYPE_A = (byte) 0x80;
    /** Media part of {@link #getProtocol()}: contactless ISO/IEC 14443 Type B. */
    public static final byte PROTOCOL_MEDIA_CONTACTLESS_TYPE_B = (byte) 0x90;
    /** Media part of {@link #getProtocol()}: USB. */
    public static final byte PROTOCOL_MEDIA_USB = (byte) 0xA0;
    /** Media type of an APDU received through the HCI APDU gate of a contactless front end. */
    public static final byte PROTOCOL_MEDIA_HCI_APDU_GATE = (byte) 0xB0;
    /**
     * Older name of {@link #PROTOCOL_MEDIA_HCI_APDU_GATE}; it has the same value.
     */
    public static final byte PROTOCOL_MEDIA_CONTACTLESS_TYPE_F = (byte) 0xB0;
    /** Mask that selects the media (upper nibble) of the value returned by {@link #getProtocol()}. */
    public static final byte PROTOCOL_MEDIA_MASK = (byte) 0xF0;
    /** Mask that selects the transport protocol type (lower nibble) of the value returned by {@link #getProtocol()}. */
    public static final byte PROTOCOL_TYPE_MASK = 0x0F;
    /** Protocol type part of {@link #getProtocol()}: character-oriented T=0 (ISO/IEC 7816-3). */
    public static final byte PROTOCOL_T0 = 0;
    /** Protocol type part of {@link #getProtocol()}: block-oriented T=1 (ISO/IEC 7816-3). */
    public static final byte PROTOCOL_T1 = 1;

    private APDU() {
    }

    /**
     * Returns the APDU buffer.
     *
     * @return the APDU buffer byte array
     */
    public byte[] getBuffer() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the ISO 7816 transport protocol in use.
     *
     * @return the protocol type
     */
    public static byte getProtocol() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the configured incoming block size.
     *
     * @return incoming block size
     */
    public static short getInBlockSize() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the configured outgoing block size.
     *
     * @return outgoing block size
     */
    public static short getOutBlockSize() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the NAD byte.
     *
     * @return the NAD byte
     */
    public byte getNAD() {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the data transfer direction to outgoing.
     *
     * @return the expected length
     * @throws APDUException on error
     */
    public short setOutgoing() throws APDUException {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the data transfer direction to outgoing without chaining.
     *
     * @return the expected length
     * @throws APDUException on error
     */
    public short setOutgoingNoChaining() throws APDUException {
        throw new RuntimeException("stub");
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
        throw new RuntimeException("stub");
    }

    /**
     * Sets the incoming transfer mode and receives the first block.
     *
     * @return number of bytes received
     * @throws APDUException on error
     */
    public short setIncomingAndReceive() throws APDUException {
        throw new RuntimeException("stub");
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
     * Returns the current APDU processing state.
     *
     * @return the state
     */
    public byte getCurrentState() {
        throw new RuntimeException("stub");
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
     */
    public short getOffsetCdata() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the number of command data bytes announced by the Lc field (Nc), for short and extended APDUs.
     *
     * @return the incoming data length, 0 when the command has no data
     * @throws APDUException if the incoming length is not available in the current state
     */
    public short getIncomingLength() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the logical channel number encoded in the CLA byte of the current command.
     *
     * @return the logical channel number (0 to 19)
     */
    public static byte getCLAChannel() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the APDU object of the command being processed. Only the currently selected applet may call it.
     *
     * @return the current APDU object
     * @throws SecurityException if the caller is not the currently selected applet, or no command is being processed
     */
    public static APDU getCurrentAPDU() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the APDU buffer of the command being processed. Only the currently selected applet may call it.
     *
     * @return the APDU buffer
     * @throws SecurityException if the caller is not the currently selected applet, or no command is being processed
     */
    public static byte[] getCurrentAPDUBuffer() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the CLA byte of the current command announces that more commands of a chain follow
     * (ISO/IEC 7816-4 command chaining).
     *
     * @return {@code true} if the command chaining bit is set
     */
    public boolean isCommandChainingCLA() {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the CLA byte of the current command indicates secure messaging.
     *
     * @return {@code true} if the secure messaging bits of the CLA byte are set
     */
    public boolean isSecureMessagingCLA() {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the CLA byte of the current command uses the ISO/IEC 7816-4 interindustry encoding.
     *
     * @return {@code true} for an interindustry class byte
     */
    public boolean isISOInterindustryCLA() {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the CLA byte of the current command is valid; the value {@code 0xFF} is not.
     *
     * @return {@code true} if the class byte is valid
     */
    public boolean isValidCLA() {
        throw new RuntimeException("stub");
    }
}
