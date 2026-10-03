package javacard.framework.service;

import javacard.framework.APDU;

/**
 * A service that takes part in processing a command APDU under the control of a {@link Dispatcher}. Each method
 * handles one processing phase and returns {@code true} when it has dealt with that phase, so that no further
 * service is asked.
 */
public interface Service {

    /**
     * Pre-processes the command data, for example by unwrapping secure messaging.
     *
     * @param apdu the command being processed
     * @return {@code true} if the input data has been processed by this service
     */
    boolean processDataIn(APDU apdu);

    /**
     * Executes the command.
     *
     * @param apdu the command being processed
     * @return {@code true} if the command has been processed by this service
     */
    boolean processCommand(APDU apdu);

    /**
     * Post-processes the response data, for example by wrapping it with secure messaging.
     *
     * @param apdu the command being processed
     * @return {@code true} if the output data has been processed by this service
     */
    boolean processDataOut(APDU apdu);
}
