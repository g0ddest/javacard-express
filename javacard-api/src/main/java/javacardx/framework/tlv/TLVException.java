package javacardx.framework.tlv;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown by the BER TLV classes of this package.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class TLVException extends CardRuntimeException {

    /** A parameter is not valid. */
    public static final short INVALID_PARAM = 1;
    /** A size is not valid, for example a capacity that is too small. */
    public static final short ILLEGAL_SIZE = 2;
    /** The tag object has not been initialized. */
    public static final short EMPTY_TAG = 3;
    /** The TLV object has not been initialized. */
    public static final short EMPTY_TLV = 4;
    /** The tag encoding is malformed. */
    public static final short MALFORMED_TAG = 5;
    /** The TLV encoding is malformed. */
    public static final short MALFORMED_TLV = 6;
    /** The object does not have room for the result. */
    public static final short INSUFFICIENT_STORAGE = 7;
    /** The tag encoding is longer than 127 bytes. */
    public static final short TAG_SIZE_GREATER_THAN_127 = 8;
    /** The tag number is greater than 32767. */
    public static final short TAG_NUMBER_GREATER_THAN_32767 = 9;
    /** The TLV encoding is longer than 32767 bytes. */
    public static final short TLV_SIZE_GREATER_THAN_32767 = 10;
    /** The value length of the TLV is greater than 32767. */
    public static final short TLV_LENGTH_GREATER_THAN_32767 = 11;

    /**
     * Constructs a TLVException with the given reason code.
     *
     * @param reason the reason code
     */
    public TLVException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a TLVException with the given reason code.
     *
     * @param reason the reason code
     * @throws TLVException always
     */
    public static void throwIt(short reason) throws TLVException {
        throw new TLVException(reason);
    }
}
