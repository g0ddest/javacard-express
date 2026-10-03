package javacardx.biometry1toN;

/**
 * Matches a candidate against a set of enrolled reference template data (one-to-many identification).
 * Matching follows the pattern {@link #initMatch(byte[], short, short)} followed by zero or more
 * {@link #match(byte[], short, short)} calls while the result is {@link #MATCH_NEEDS_MORE_DATA}.
 */
public interface BioMatcher {

    /** Lowest score that counts as a successful match; scores range up to 32767. */
    short MINIMUM_SUCCESSFUL_MATCH_SCORE = 16384;
    /** Result of a match step meaning that more candidate data is needed. */
    short MATCH_NEEDS_MORE_DATA = -1;

    /**
     * Tells whether at least one reference template data is enrolled.
     *
     * @return {@code true} if the matcher can be used
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
     * Returns the number of failed matches still allowed before the matcher is blocked.
     *
     * @return the remaining tries
     */
    byte getTriesRemaining();

    /**
     * Returns the biometric type handled by the matcher.
     *
     * @return one of the type constants of {@link Bio1toNBuilder}
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
     * Starts matching a candidate against the enrolled template data.
     *
     * @param candidate array holding the first part of the candidate data
     * @param offset    offset of the candidate data
     * @param length    length of the candidate data
     * @return a match score, or {@link #MATCH_NEEDS_MORE_DATA}
     * @throws Bio1toNException with NO_BIO_TEMPLATE_ENROLLED if nothing is enrolled, or INVALID_DATA if the
     *                          candidate is malformed
     */
    short initMatch(byte[] candidate, short offset, short length) throws Bio1toNException;

    /**
     * Continues a match started with {@link #initMatch(byte[], short, short)}.
     *
     * @param candidate array holding the next part of the candidate data
     * @param offset    offset of the candidate data
     * @param length    length of the candidate data
     * @return a match score, or {@link #MATCH_NEEDS_MORE_DATA}
     * @throws Bio1toNException with ILLEGAL_USE if no match is in progress, or INVALID_DATA if the data is
     *                          malformed
     */
    short match(byte[] candidate, short offset, short length) throws Bio1toNException;

    /**
     * Returns the number of reference template slots.
     *
     * @return the capacity of the matcher
     */
    short getMaxNbOfBioTemplateData();

    /**
     * Returns the slot index of the template data that matched last.
     *
     * @return the slot index, or -1 if the last match failed
     */
    short getIndexOfLastMatchingBioTemplateData();

    /**
     * Returns the template data stored in a slot.
     *
     * @param index the slot index
     * @return the template data, or {@code null} if the slot is empty
     */
    BioTemplateData getBioTemplateData(short index);
}
