package javacardx.external;

/**
 * Reads and writes an external memory subsystem obtained from
 * {@link Memory#getMemoryAccessInstance(byte, short[], short)}. Access may require an authentication key; the
 * meaning of the address parameters depends on the subsystem (for MIFARE they are sector and block numbers).
 */
public interface MemoryAccess {

    /**
     * Writes data to the external memory.
     *
     * @param data         array holding the data to write
     * @param dataOffset   offset of the data
     * @param dataLength   number of bytes to write
     * @param authKey      array holding the authentication key, or {@code null}
     * @param authKeyOffset offset of the key
     * @param authKeyLength length of the key
     * @param address      first address component (for MIFARE the sector)
     * @param subAddress   second address component (for MIFARE the block)
     * @return {@code true} if the data was written
     * @throws ExternalException with INVALID_PARAM if a parameter is not valid, or INTERNAL_ERROR if the
     *                           subsystem fails
     */
    boolean writeData(byte[] data, short dataOffset, short dataLength, byte[] authKey, short authKeyOffset,
            short authKeyLength, short address, short subAddress) throws ExternalException;

    /**
     * Reads data from the external memory.
     *
     * @param dest          destination array
     * @param destOffset    offset in {@code dest}
     * @param authKey       array holding the authentication key, or {@code null}
     * @param authKeyOffset offset of the key
     * @param authKeyLength length of the key
     * @param address       first address component (for MIFARE the sector)
     * @param subAddress    second address component (for MIFARE the block)
     * @param length        number of bytes to read
     * @return the number of bytes read
     * @throws ExternalException with INVALID_PARAM if a parameter is not valid, or INTERNAL_ERROR if the
     *                           subsystem fails
     */
    short readData(byte[] dest, short destOffset, byte[] authKey, short authKeyOffset, short authKeyLength,
            short address, short subAddress, short length) throws ExternalException;
}
