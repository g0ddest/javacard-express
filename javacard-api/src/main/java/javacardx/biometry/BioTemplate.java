package javacardx.biometry;

/**
 * Read and match access to a biometric reference template. Matching follows the pattern
 * {@link #initMatch(byte[], short, short)} followed by zero or more {@link #match(byte[], short, short)} calls
 * while the result is {@link #MATCH_NEEDS_MORE_DATA}.
 */
public interface BioTemplate {

    /** Lowest score that counts as a successful match; scores range up to 32767. */
    short MINIMUM_SUCCESSFUL_MATCH_SCORE = 16384;
    /** Result of a match step meaning that more candidate data is needed. */
    short MATCH_NEEDS_MORE_DATA = -1;

    /**
     * Tells whether the template has completed enrollment.
     *
     * @return {@code true} if a reference template is enrolled
     */
    boolean isInitialized();

    /**
     * Tells whether the last match in this session succeeded.
     *
     * @return {@code true} if the holder is currently validated
     */
    boolean isValidated();

    /**
     * Clears the validated state.
     */
    void reset();

    /**
     * Returns the number of failed matches still allowed before the template is blocked.
     *
     * @return the remaining tries
     */
    byte getTriesRemaining();

    /**
     * Returns the biometric type of the template.
     *
     * @return one of the type constants of {@link BioBuilder}
     */
    byte getBioType();

    /**
     * Copies the version of the matching algorithm into a buffer.
     *
     * @param dest   destination array
     * @param offset offset in {@code dest}
     * @return the number of bytes written
     */
    short getVersion(byte[] dest, short offset);

    /**
     * Copies part of the public template data (information the terminal needs to capture a candidate).
     *
     * @param publicOffset offset within the public data
     * @param dest         destination array
     * @param destOffset   offset in {@code dest}
     * @param length       number of bytes requested
     * @return the number of bytes written
     * @throws BioException with NO_TEMPLATES_ENROLLED if the template is not enrolled
     */
    short getPublicTemplateData(short publicOffset, byte[] dest, short destOffset, short length)
            throws BioException;

    /**
     * Starts matching a candidate against the reference template.
     *
     * @param candidate array holding the first part of the candidate data
     * @param offset    offset of the candidate data
     * @param length    length of the candidate data
     * @return a match score, or {@link #MATCH_NEEDS_MORE_DATA}
     * @throws BioException with NO_TEMPLATES_ENROLLED if no template is enrolled, or INVALID_DATA if the
     *                      candidate is malformed
     */
    short initMatch(byte[] candidate, short offset, short length) throws BioException;

    /**
     * Continues a match started with {@link #initMatch(byte[], short, short)}.
     *
     * @param candidate array holding the next part of the candidate data
     * @param offset    offset of the candidate data
     * @param length    length of the candidate data
     * @return a match score, or {@link #MATCH_NEEDS_MORE_DATA}
     * @throws BioException with ILLEGAL_USE if no match is in progress, or INVALID_DATA if the data is malformed
     */
    short match(byte[] candidate, short offset, short length) throws BioException;
}
