package javacard.framework.service;

import javacard.framework.APDU;

/**
 * Base implementation of {@link Service}: every processing method declines (returns {@code false}), and helper
 * methods give access to the command header, the data and the status word that services exchange through the
 * APDU buffer.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class BasicService implements Service {

    /**
     * Creates a service.
     */
    public BasicService() {
        throw new RuntimeException("stub");
    }

    /**
     * Declines the input phase; override to pre-process command data.
     *
     * @param apdu the command being processed
     * @return {@code false}
     */
    @Override
    public boolean processDataIn(APDU apdu) {
        throw new RuntimeException("stub");
    }

    /**
     * Declines the command phase; override to execute commands.
     *
     * @param apdu the command being processed
     * @return {@code false}
     */
    @Override
    public boolean processCommand(APDU apdu) {
        throw new RuntimeException("stub");
    }

    /**
     * Declines the output phase; override to post-process response data.
     *
     * @param apdu the command being processed
     * @return {@code false}
     */
    @Override
    public boolean processDataOut(APDU apdu) {
        throw new RuntimeException("stub");
    }

    /**
     * Receives all command data into the APDU buffer, starting at the command data offset.
     *
     * @param apdu the command being processed
     * @return the number of data bytes received
     * @throws ServiceException with CANNOT_ACCESS_IN_COMMAND if the data can no longer be received, or
     *                          COMMAND_DATA_TOO_LONG if it does not fit the buffer
     */
    public short receiveInData(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Marks the command as processed, so that later services skip the command phase.
     *
     * @param apdu the command being processed
     * @throws ServiceException with CANNOT_ACCESS_IN_COMMAND if the command can no longer be marked
     */
    public void setProcessed(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the command has been marked as processed.
     *
     * @param apdu the command being processed
     * @return {@code true} if the command is processed
     */
    public boolean isProcessed(APDU apdu) {
        throw new RuntimeException("stub");
    }

    /**
     * Records the length of the response data placed in the APDU buffer.
     *
     * @param apdu   the command being processed
     * @param length number of response bytes
     * @throws ServiceException with ILLEGAL_PARAM if the length is too large, or CANNOT_ACCESS_OUT_COMMAND if
     *                          the response can no longer be changed
     */
    public void setOutputLength(APDU apdu, short length) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the recorded length of the response data.
     *
     * @param apdu the command being processed
     * @return the number of response bytes
     * @throws ServiceException with CANNOT_ACCESS_OUT_COMMAND if the command has not been processed yet
     */
    public short getOutputLength(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Records the status word to return.
     *
     * @param apdu the command being processed
     * @param sw   the status word
     */
    public void setStatusWord(APDU apdu, short sw) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the recorded status word.
     *
     * @param apdu the command being processed
     * @return the status word
     * @throws ServiceException with CANNOT_ACCESS_OUT_COMMAND if the command has not been processed yet
     */
    public short getStatusWord(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Marks the command as processed with an error status word and no response data.
     *
     * @param apdu the command being processed
     * @param sw   the error status word
     * @return {@code true}
     * @throws ServiceException with CANNOT_ACCESS_OUT_COMMAND if the command can no longer be completed
     */
    public boolean fail(APDU apdu, short sw) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Marks the command as processed successfully (status word 9000).
     *
     * @param apdu the command being processed
     * @return {@code true}
     * @throws ServiceException with CANNOT_ACCESS_OUT_COMMAND if the command can no longer be completed
     */
    public boolean succeed(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Marks the command as processed successfully with the given status word.
     *
     * @param apdu the command being processed
     * @param sw   the status word
     * @return {@code true}
     * @throws ServiceException with CANNOT_ACCESS_OUT_COMMAND if the command can no longer be completed
     */
    public boolean succeedWithStatusWord(APDU apdu, short sw) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the class byte of the command.
     *
     * @param apdu the command being processed
     * @return the CLA byte
     */
    public byte getCLA(APDU apdu) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the instruction byte of the command.
     *
     * @param apdu the command being processed
     * @return the INS byte
     */
    public byte getINS(APDU apdu) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the first parameter byte of the command.
     *
     * @param apdu the command being processed
     * @return the P1 byte
     * @throws ServiceException with CANNOT_ACCESS_IN_COMMAND if the header is no longer available
     */
    public byte getP1(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the second parameter byte of the command.
     *
     * @param apdu the command being processed
     * @return the P2 byte
     * @throws ServiceException with CANNOT_ACCESS_IN_COMMAND if the header is no longer available
     */
    public byte getP2(APDU apdu) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the command being processed is the SELECT that selects the applet using this service.
     *
     * @return {@code true} while the applet is being selected
     */
    public boolean selectingApplet() {
        throw new RuntimeException("stub");
    }
}
