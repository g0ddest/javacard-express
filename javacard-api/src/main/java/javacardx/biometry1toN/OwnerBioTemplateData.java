package javacardx.biometry1toN;

/**
 * The owner's view of {@link BioTemplateData}: it allows enrollment, which is
 * {@link #init(byte[], short, short)}, optional {@link #update(byte[], short, short)} calls and a final
 * {@link #doFinal()}.
 */
public interface OwnerBioTemplateData extends BioTemplateData {

    /**
     * Starts enrollment and discards any previous data.
     *
     * @param bArray array holding the first part of the enrollment data
     * @param offset offset of the data
     * @param length length of the data
     * @throws Bio1toNException with INVALID_DATA if the data is malformed
     */
    void init(byte[] bArray, short offset, short length) throws Bio1toNException;

    /**
     * Adds more enrollment data.
     *
     * @param bArray array holding the next part of the enrollment data
     * @param offset offset of the data
     * @param length length of the data
     * @throws Bio1toNException with ILLEGAL_USE if enrollment was not started, or INVALID_DATA if the data is
     *                          malformed
     */
    void update(byte[] bArray, short offset, short length) throws Bio1toNException;

    /**
     * Completes enrollment.
     *
     * @throws Bio1toNException with ILLEGAL_USE if enrollment was not started, or INVALID_DATA if the collected
     *                          data does not form a valid template
     */
    void doFinal() throws Bio1toNException;
}
