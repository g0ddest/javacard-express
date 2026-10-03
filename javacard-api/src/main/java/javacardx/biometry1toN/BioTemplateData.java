package javacardx.biometry1toN;

/**
 * One enrolled biometric reference, as stored in a {@link BioMatcher} slot.
 */
public interface BioTemplateData {

    /**
     * Returns the biometric type of the template data.
     *
     * @return one of the type constants of {@link Bio1toNBuilder}
     */
    byte getBioType();

    /**
     * Tells whether the template data has completed enrollment.
     *
     * @return {@code true} if the data is enrolled
     */
    boolean isInitialized();

    /**
     * Copies part of the public data (information the terminal needs to capture a candidate).
     *
     * @param publicOffset offset within the public data
     * @param dest         destination array
     * @param destOffset   offset in {@code dest}
     * @param length       number of bytes requested
     * @return the number of bytes written
     * @throws Bio1toNException with ILLEGAL_USE if the data is not enrolled
     */
    short getPublicData(short publicOffset, byte[] dest, short destOffset, short length) throws Bio1toNException;
}
