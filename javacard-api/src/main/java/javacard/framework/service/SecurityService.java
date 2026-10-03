package javacard.framework.service;

/**
 * A service that knows the security state of the session: which principals are authenticated and which
 * protections (confidentiality, integrity) apply to the channel and to the current command.
 */
public interface SecurityService extends Service {

    /** Property flag: incoming data is encrypted. */
    byte PROPERTY_INPUT_CONFIDENTIALITY = 1;
    /** Property flag: incoming data is integrity protected. */
    byte PROPERTY_INPUT_INTEGRITY = 2;
    /** Property flag: outgoing data is encrypted. */
    byte PROPERTY_OUTPUT_CONFIDENTIALITY = 4;
    /** Property flag: outgoing data is integrity protected. */
    byte PROPERTY_OUTPUT_INTEGRITY = 8;

    /** Principal: the cardholder. */
    short PRINCIPAL_CARDHOLDER = 1;
    /** Principal: the card issuer. */
    short PRINCIPAL_CARD_ISSUER = 2;
    /** Principal: the application provider. */
    short PRINCIPAL_APP_PROVIDER = 3;

    /**
     * Tells whether a principal is currently authenticated.
     *
     * @param principal one of the {@code PRINCIPAL_*} constants
     * @return {@code true} if the principal is authenticated
     * @throws ServiceException with ILLEGAL_PARAM if the principal is unknown
     */
    boolean isAuthenticated(short principal) throws ServiceException;

    /**
     * Tells whether the communication channel provides the given protections.
     *
     * @param properties a combination of {@code PROPERTY_*} flags
     * @return {@code true} if all requested protections are in place
     * @throws ServiceException with ILLEGAL_PARAM if the flags are not valid
     */
    boolean isChannelSecure(byte properties) throws ServiceException;

    /**
     * Tells whether the current command was protected as requested.
     *
     * @param properties a combination of {@code PROPERTY_*} flags
     * @return {@code true} if all requested protections apply to the current command
     * @throws ServiceException with ILLEGAL_PARAM if the flags are not valid
     */
    boolean isCommandSecure(byte properties) throws ServiceException;
}
