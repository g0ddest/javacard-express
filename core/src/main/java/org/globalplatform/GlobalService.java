package org.globalplatform;

import javacard.framework.ISOException;
import javacard.framework.Shareable;

/**
 * What a Global Services application hands out through {@code Applet.getShareableInterfaceObject}: a client
 * asks it for the object that provides a service (GlobalPlatform Card Specification v2.3.1 section 8.1). An
 * application gets it from {@link GPSystem#getService}. Part of the package since version 1.1.
 */
public interface GlobalService extends Shareable {

    /** Key access: the Security Domain and its applications may use the key. */
    byte KEY_ACCESS_ANY = (byte) 0x00;

    /** Key access: only the Security Domain may use the key. */
    byte KEY_ACCESS_SECURITY_DOMAIN = (byte) 0x01;

    /** Key access: only the applications of the Security Domain may use the key. */
    byte KEY_ACCESS_APPLICATION = (byte) 0x02;

    /** Key type: AES. */
    byte KEY_TYPE_AES = (byte) 0x88;

    /** Key type: triple DES, mode implicitly known. */
    byte KEY_TYPE_3DES = (byte) 0x81;

    /** Key type: triple DES in CBC mode. */
    byte KEY_TYPE_3DES_CBC = (byte) 0x82;

    /** Key type: DES, mode implicitly known. */
    byte KEY_TYPE_DES = (byte) 0x80;

    /** Key type: DES in CBC mode. */
    byte KEY_TYPE_DES_CBC = (byte) 0x84;

    /** Key type: DES in ECB mode. */
    byte KEY_TYPE_DES_ECB = (byte) 0x83;

    /** Key type: coded in a further byte. */
    byte KEY_TYPE_EXTENDED = (byte) 0xFF;

    /** Key type: HMAC-SHA1, length implicitly known. */
    byte KEY_TYPE_HMAC_SHA1 = (byte) 0x90;

    /** Key type: HMAC-SHA1 with a 160-bit key. */
    byte KEY_TYPE_HMAC_SHA1_160 = (byte) 0x91;

    /** Key type: RSA private key, Chinese remainder component P. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_P = (byte) 0xA4;

    /** Key type: RSA private key, Chinese remainder component Q. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_Q = (byte) 0xA5;

    /** Key type: RSA private key, Chinese remainder component PQ. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_PQ = (byte) 0xA6;

    /** Key type: RSA private key, Chinese remainder component DP1. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_DP1 = (byte) 0xA7;

    /** Key type: RSA private key, Chinese remainder component DQ1. */
    byte KEY_TYPE_RSA_PRIVATE_CRT_DQ1 = (byte) 0xA8;

    /** Key type: RSA private exponent. */
    byte KEY_TYPE_RSA_PRIVATE_EXPONENT = (byte) 0xA3;

    /** Key type: RSA modulus of a private key. */
    byte KEY_TYPE_RSA_PRIVATE_MODULUS = (byte) 0xA2;

    /** Key type: RSA public exponent. */
    byte KEY_TYPE_RSA_PUBLIC_EXPONENT = (byte) 0xA0;

    /** Key type: RSA modulus of a public key. */
    byte KEY_TYPE_RSA_PUBLIC_MODULUS = (byte) 0xA1;

    /** Key usage: computation and decipherment. */
    byte KEY_USAGE_COMPUTATION_DECIPHERMENT = (byte) 0x40;

    /** Key usage: confidentiality of sensitive data. */
    byte KEY_USAGE_CONFIDENTIALITY = (byte) 0x08;

    /** Key usage: cryptographic authorization. */
    byte KEY_USAGE_CRYPTOGRAPHIC_AUTHORIZATION = (byte) 0x01;

    /** Key usage: cryptographic checksum. */
    byte KEY_USAGE_CRYPTOGRAPHIC_CHECKSUM = (byte) 0x04;

    /** Key usage: digital signature. */
    byte KEY_USAGE_DIGITAL_SIGNATURE = (byte) 0x02;

    /** Key usage: secure messaging of commands. */
    byte KEY_USAGE_SM_COMMAND = (byte) 0x10;

    /** Key usage: secure messaging of responses. */
    byte KEY_USAGE_SM_RESPONSE = (byte) 0x20;

    /** Key usage: verification and encipherment. */
    byte KEY_USAGE_VERIFICATION_ENCIPHERMENT = (byte) 0x80;

    /**
     * Returns the object that provides a service to a client.
     *
     * @param clientRegistryEntry the registry entry of the asking application
     * @param sServiceName        the service name: family in the first byte, service in the second
     * @param baBuffer            further parameters of the request, or null
     * @param sOffset             where the parameters start
     * @param sLength             the length of the parameters
     * @return the object that provides the service, or null when the request is refused
     * @throws ISOException when the request is refused ('6A88', '6982' and '6985' are recommended)
     */
    Shareable getServiceInterface(GPRegistryEntry clientRegistryEntry, short sServiceName, byte[] baBuffer,
                                  short sOffset, short sLength) throws ISOException;
}
