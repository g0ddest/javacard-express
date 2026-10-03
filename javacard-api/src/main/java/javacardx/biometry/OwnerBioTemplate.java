package javacardx.biometry;

/**
 * The owner's view of a biometric reference template: in addition to matching it allows enrollment and
 * unblocking. Enrollment is {@link #init(byte[], short, short)}, optional {@link #update(byte[], short, short)}
 * calls and a final {@link #doFinal()}.
 */
public interface OwnerBioTemplate extends BioTemplate {

    /**
     * Starts enrollment and discards any previous reference template.
     *
     * @param bArray array holding the first part of the enrollment data
     * @param offset offset of the data
     * @param length length of the data
     * @throws BioException with INVALID_DATA if the data is malformed
     */
    void init(byte[] bArray, short offset, short length) throws BioException;

    /**
     * Adds more enrollment data.
     *
     * @param bArray array holding the next part of the enrollment data
     * @param offset offset of the data
     * @param length length of the data
     * @throws BioException with ILLEGAL_USE if enrollment was not started, or INVALID_DATA if the data is
     *                      malformed
     */
    void update(byte[] bArray, short offset, short length) throws BioException;

    /**
     * Completes enrollment; the template becomes usable for matching.
     *
     * @throws BioException with ILLEGAL_USE if enrollment was not started, or INVALID_DATA if the collected data
     *                      does not form a valid template
     */
    void doFinal() throws BioException;

    /**
     * Unblocks the template, clears the validated state and sets a new try limit.
     *
     * @param newTryLimit the new number of allowed consecutive failed matches
     * @throws BioException with ILLEGAL_VALUE if the try limit is not valid
     */
    void resetUnblockAndSetTryLimit(byte newTryLimit) throws BioException;
}
