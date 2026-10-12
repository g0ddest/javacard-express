package org.globalplatform;

import javacard.framework.ISOException;
import javacard.framework.Shareable;

/**
 * The entry point of an application that offers services to other applications on the card (a Global Services
 * Application): a client asks it for the object that provides one service. A client gets the entry point from
 * {@link GPSystem#getService(javacard.framework.AID, short)}.
 *
 * <p>The {@code KEY} constants are the codings of key access, key type and key usage of GlobalPlatform Card
 * Specification v2.3.1 sections 11.1.8 to 11.1.10, for services that deal with keys.</p>
 *
 * <p>Since version 1.1 of the package.</p>
 */
public interface GlobalService extends Shareable {

    /**
     * Key access: the Security Domain and its applications may use the key ('00'). Since version 1.1 of the
     * package.
     */
    byte KEY_ACCESS_ANY = 0x00;
    /** Key access: only the Security Domain may use the key ('01'). Since version 1.1 of the package. */
    byte KEY_ACCESS_SECURITY_DOMAIN = 0x01;
    /**
     * Key access: the applications of the Security Domain may use the key, the Security Domain itself may not
     * ('02'). Since version 1.1 of the package.
     */
    byte KEY_ACCESS_APPLICATION = 0x02;
    /** Key type: AES ('88'). Since version 1.1 of the package. */
    byte KEY_TYPE_AES = (byte) 0x88;
    /** Key type: Triple DES, kept for particular implementations ('81'). Since version 1.1 of the package. */
    byte KEY_TYPE_3DES = (byte) 0x81;
    /** Key type: Triple DES in CBC mode ('82'). Since version 1.1 of the package. */
    byte KEY_TYPE_3DES_CBC = (byte) 0x82;
    /** Key type: DES, with the mode known from the context ('80'). Since version 1.1 of the package. */
    byte KEY_TYPE_DES = (byte) 0x80;
    /** Key type: DES in CBC mode ('84'). Since version 1.1 of the package. */
    byte KEY_TYPE_DES_CBC = (byte) 0x84;
    /** Key type: DES in ECB mode ('83'). Since version 1.1 of the package. */
    byte KEY_TYPE_DES_ECB = (byte) 0x83;
    /** Key type: the extended key format follows ('FF'). Since version 1.1 of the package. */
    byte KEY_TYPE_EXTENDED = (byte) 0xFF;
    /**
     * Key type: HMAC-SHA1, with the length of the HMAC known from the context ('90'). Since version 1.1 of the
     * package.
     */
    byte KEY_TYPE_HMAC_SHA1 = (byte) 0x90;
    /** Key type: HMAC-SHA1 with an HMAC of 160 bits ('91'). Since version 1.1 of the package. */
    byte KEY_TYPE_HMAC_SHA1_160 = (byte) 0x91;
    /** Key type: Chinese Remainder component P of an RSA private key ('A4'). Since version 1.1 of the package. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_P = (byte) 0xA4;
    /** Key type: Chinese Remainder component Q of an RSA private key ('A5'). Since version 1.1 of the package. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_Q = (byte) 0xA5;
    /**
     * Key type: Chinese Remainder component PQ of an RSA private key ('A6'). Since version 1.1 of the package.
     */
    byte KEY_TYPE_RSA_PRIVATE_CRT_PQ = (byte) 0xA6;
    /**
     * Key type: Chinese Remainder component DP1 of an RSA private key ('A7'). Since version 1.1 of the package.
     */
    byte KEY_TYPE_RSA_PRIVATE_CRT_DP1 = (byte) 0xA7;
    /**
     * Key type: Chinese Remainder component DQ1 of an RSA private key ('A8'). Since version 1.1 of the package.
     */
    byte KEY_TYPE_RSA_PRIVATE_CRT_DQ1 = (byte) 0xA8;
    /** Key type: private exponent of an RSA private key ('A3'). Since version 1.1 of the package. */
    byte KEY_TYPE_RSA_PRIVATE_EXPONENT = (byte) 0xA3;
    /** Key type: modulus of an RSA private key ('A2'). Since version 1.1 of the package. */
    byte KEY_TYPE_RSA_PRIVATE_MODULUS = (byte) 0xA2;
    /** Key type: public exponent of an RSA public key ('A0'). Since version 1.1 of the package. */
    byte KEY_TYPE_RSA_PUBLIC_EXPONENT = (byte) 0xA0;
    /** Key type: modulus of an RSA public key ('A1'). Since version 1.1 of the package. */
    byte KEY_TYPE_RSA_PUBLIC_MODULUS = (byte) 0xA1;
    /** Key usage bit: computation and decipherment ('40'). Since version 1.1 of the package. */
    byte KEY_USAGE_COMPUTATION_DECIPHERMENT = 0x40;
    /** Key usage bit: confidentiality of sensitive data ('08'). Since version 1.1 of the package. */
    byte KEY_USAGE_CONFIDENTIALITY = 0x08;
    /** Key usage bit: cryptographic authorization ('01'). Since version 1.1 of the package. */
    byte KEY_USAGE_CRYPTOGRAPHIC_AUTHORIZATION = 0x01;
    /** Key usage bit: cryptographic checksum, such as a MAC ('04'). Since version 1.1 of the package. */
    byte KEY_USAGE_CRYPTOGRAPHIC_CHECKSUM = 0x04;
    /** Key usage bit: digital signature ('02'). Since version 1.1 of the package. */
    byte KEY_USAGE_DIGITAL_SIGNATURE = 0x02;
    /** Key usage bit: secure messaging in command data fields ('10'). Since version 1.1 of the package. */
    byte KEY_USAGE_SM_COMMAND = 0x10;
    /** Key usage bit: secure messaging in response data fields ('20'). Since version 1.1 of the package. */
    byte KEY_USAGE_SM_RESPONSE = 0x20;
    /** Key usage bit: verification and encipherment ('80'). Since version 1.1 of the package. */
    byte KEY_USAGE_VERIFICATION_ENCIPHERMENT = (byte) 0x80;

    /**
     * Returns the object that provides a service, which the client casts to the interface of that service. The
     * Global Services Application decides by its own rules, from the client's registry entry and the additional
     * parameters, whether the client gets the service.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param clientRegistryEntry the registry entry of the application that asks
     * @param sServiceName        the service name: a family byte ({@code FAMILY} constants of {@link GPSystem})
     *                            followed by the identifier of the service within the family
     * @param baBuffer            a global array with what else the request needs, for example authentication
     *                            data
     * @param sOffset             where that data starts in the array
     * @param sLength             how many bytes of it there are
     * @return the object that provides the service; null when the application does not offer the service to
     *         this client
     * @throws ISOException if the request is refused; '6A88' for an unknown service, '6982' for a security
     *                      condition and '6985' for another condition are the recommended reasons
     */
    Shareable getServiceInterface(GPRegistryEntry clientRegistryEntry, short sServiceName, byte[] baBuffer,
                                  short sOffset, short sLength) throws ISOException;
}
