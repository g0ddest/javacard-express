package javacardx.framework.string;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown by {@link StringUtil}.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class StringException extends CardRuntimeException {

    /** The requested character encoding is not supported. */
    public static final short UNSUPPORTED_ENCODING = 1;
    /** The text is not a valid number. */
    public static final short ILLEGAL_NUMBER_FORMAT = 2;
    /** The data is not a valid byte sequence for its encoding. */
    public static final short INVALID_BYTE_SEQUENCE = 3;

    /**
     * Constructs a StringException with the given reason code.
     *
     * @param reason the reason code
     */
    public StringException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a StringException with the given reason code.
     *
     * @param reason the reason code
     * @throws StringException always
     */
    public static void throwIt(short reason) throws StringException {
        throw new StringException(reason);
    }
}
