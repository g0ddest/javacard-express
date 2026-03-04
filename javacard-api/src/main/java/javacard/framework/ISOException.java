package javacard.framework;

/**
 * Exception representing an ISO 7816 status word error.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class ISOException extends CardRuntimeException {

    /**
     * Constructs an ISOException with the given status word.
     *
     * @param sw the status word
     */
    public ISOException(short sw) {
        super(sw);
    }

    /**
     * Throws an ISOException with the given status word.
     *
     * @param sw the status word
     * @throws ISOException always
     */
    public static void throwIt(short sw) throws ISOException {
        throw new ISOException(sw);
    }
}
