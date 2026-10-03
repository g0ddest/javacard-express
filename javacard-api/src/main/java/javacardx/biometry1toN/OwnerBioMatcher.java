package javacardx.biometry1toN;

/**
 * The owner's view of a {@link BioMatcher}: it can store reference template data in slots and unblock the
 * matcher.
 */
public interface OwnerBioMatcher extends BioMatcher {

    /**
     * Stores template data in a slot, replacing what was there.
     *
     * @param index        the slot index
     * @param templateData the enrolled template data, or {@code null} to clear the slot
     * @throws Bio1toNException with ILLEGAL_VALUE if the index is out of range, or MISMATCHED_BIO_TYPE if the
     *                          biometric types differ
     * @throws SecurityException if the template data belongs to another context
     */
    void putBioTemplateData(short index, BioTemplateData templateData) throws Bio1toNException, SecurityException;

    /**
     * Unblocks the matcher, clears the validated state and sets a new try limit.
     *
     * @param newTryLimit the new number of allowed consecutive failed matches
     * @throws Bio1toNException with ILLEGAL_VALUE if the try limit is not valid
     */
    void resetUnblockAndSetTryLimit(byte newTryLimit) throws Bio1toNException;

    /**
     * Returns the slot index of the template data that matched last.
     *
     * @return the slot index, or -1 if the last match failed
     */
    @Override
    short getIndexOfLastMatchingBioTemplateData();

    /**
     * Returns the template data stored in a slot, with owner access.
     *
     * @param index the slot index
     * @return the template data, or {@code null} if the slot is empty
     */
    @Override
    OwnerBioTemplateData getBioTemplateData(short index);
}
