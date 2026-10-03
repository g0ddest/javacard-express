package javacard.framework.service;

import javacard.framework.APDU;
import javacard.framework.ISOException;

/**
 * Routes each command APDU through registered {@link Service} objects in three phases: input data, command and
 * output data. An applet typically creates one dispatcher and calls {@link #process(APDU)} from its own
 * {@code process} method.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class Dispatcher {

    /** Phase selector: no processing phase. */
    public static final byte PROCESS_NONE = 0;
    /** Phase selector: pre-processing of the command data. */
    public static final byte PROCESS_INPUT_DATA = 1;
    /** Phase selector: execution of the command. */
    public static final byte PROCESS_COMMAND = 2;
    /** Phase selector: post-processing of the response data. */
    public static final byte PROCESS_OUTPUT_DATA = 3;

    /**
     * Creates a dispatcher with room for a fixed number of services.
     *
     * @param maxServices the maximum number of registered services
     * @throws ServiceException with ILLEGAL_PARAM if {@code maxServices} is negative
     */
    public Dispatcher(short maxServices) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Registers a service for one processing phase.
     *
     * @param service the service
     * @param phase   one of the {@code PROCESS_*} phase selectors other than {@link #PROCESS_NONE}
     * @throws ServiceException with ILLEGAL_PARAM for an invalid phase, or DISPATCH_TABLE_FULL if no room is left
     */
    public void addService(Service service, byte phase) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Unregisters a service from one processing phase.
     *
     * @param service the service
     * @param phase   one of the {@code PROCESS_*} phase selectors other than {@link #PROCESS_NONE}
     * @throws ServiceException with ILLEGAL_PARAM for an invalid phase
     */
    public void removeService(Service service, byte phase) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Runs the processing phases from {@code phase} onwards and returns instead of throwing.
     *
     * @param command the command being processed
     * @param phase   the first phase to run
     * @return the exception raised by a service, or {@code null} if all phases completed
     * @throws ServiceException with ILLEGAL_PARAM for an invalid phase
     */
    public Exception dispatch(APDU command, byte phase) throws ServiceException {
        throw new RuntimeException("stub");
    }

    /**
     * Runs all processing phases for a command and sends the response; intended to be called from the applet's
     * {@code process} method.
     *
     * @param command the command being processed
     * @throws ISOException with the status word recorded by the services when processing fails
     */
    public void process(APDU command) throws ISOException {
        throw new RuntimeException("stub");
    }
}
