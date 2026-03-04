package javacard.framework;

/**
 * User-defined checked exception.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class UserException extends CardException {

    /**
     * Constructs a UserException with reason code 0.
     */
    public UserException() {
        super((short) 0);
    }

    /**
     * Constructs a UserException with the given reason code.
     *
     * @param reason the reason code
     */
    public UserException(short reason) {
        super(reason);
    }

    /**
     * Throws a UserException with the given reason code.
     *
     * @param reason the reason code
     * @throws UserException always
     */
    public static void throwIt(short reason) throws UserException {
        throw new UserException(reason);
    }
}
