package javacard.framework.service;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown by the service framework ({@link Dispatcher}, {@link BasicService} and related classes).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class ServiceException extends CardRuntimeException {

    /** A parameter is not valid. */
    public static final short ILLEGAL_PARAM = 1;
    /** The dispatcher cannot register more services. */
    public static final short DISPATCH_TABLE_FULL = 2;
    /** The command data does not fit the APDU buffer. */
    public static final short COMMAND_DATA_TOO_LONG = 3;
    /** The input data of the command cannot be accessed in the current state. */
    public static final short CANNOT_ACCESS_IN_COMMAND = 4;
    /** The output data of the command cannot be accessed in the current state. */
    public static final short CANNOT_ACCESS_OUT_COMMAND = 5;
    /** The command has already been completed. */
    public static final short COMMAND_IS_FINISHED = 6;
    /** A remote object reference was returned for an object that is not exported. */
    public static final short REMOTE_OBJECT_NOT_EXPORTED = 7;

    /**
     * Constructs a ServiceException with the given reason code.
     *
     * @param reason the reason code
     */
    public ServiceException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a ServiceException with the given reason code.
     *
     * @param reason the reason code
     * @throws ServiceException always
     */
    public static void throwIt(short reason) throws ServiceException {
        throw new ServiceException(reason);
    }
}
