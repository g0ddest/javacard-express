package org.globalplatform;

import javacard.framework.AID;
import javacard.framework.ISOException;
import javacard.framework.Shareable;

/**
 * The entry of an application in the GlobalPlatform Registry: its AID, life cycle state, privileges and the
 * Global Services it registered (GlobalPlatform Card Specification v2.3.1 sections 6.6 and 8.1, life cycle
 * coding in 11.1.1, privilege numbers in Tables 11-7 to 11-9). An application gets entries from
 * {@link GPSystem#getRegistryEntry(AID)}. Part of the package since version 1.1.
 */
public interface GPRegistryEntry extends Shareable {

    /** Privilege number of Authorized Management. */
    byte PRIVILEGE_AUTHORIZED_MANAGEMENT = (byte) 0x09;

    /** Privilege number of Card Lock. */
    byte PRIVILEGE_CARD_LOCK = (byte) 0x03;

    /** Privilege number of Card Reset. */
    byte PRIVILEGE_CARD_RESET = (byte) 0x05;

    /** Privilege number of Card Terminate. */
    byte PRIVILEGE_CARD_TERMINATE = (byte) 0x04;

    /** Privilege number of CVM Management. */
    byte PRIVILEGE_CVM_MANAGEMENT = (byte) 0x06;

    /** Privilege number of DAP Verification. */
    byte PRIVILEGE_DAP_VERIFICATION = (byte) 0x01;

    /** Privilege number of Delegated Management. */
    byte PRIVILEGE_DELEGATED_MANAGEMENT = (byte) 0x02;

    /** Privilege number of Final Application. */
    byte PRIVILEGE_FINAL_APPLICATION = (byte) 0x0E;

    /** Privilege number of Global Delete. */
    byte PRIVILEGE_GLOBAL_DELETE = (byte) 0x0B;

    /** Privilege number of Global Lock. */
    byte PRIVILEGE_GLOBAL_LOCK = (byte) 0x0C;

    /** Privilege number of Global Registry. */
    byte PRIVILEGE_GLOBAL_REGISTRY = (byte) 0x0D;

    /** Privilege number of Global Service. */
    byte PRIVILEGE_GLOBAL_SERVICE = (byte) 0x0F;

    /** Privilege number of Mandated DAP Verification. */
    byte PRIVILEGE_MANDATED_DAP = (byte) 0x07;

    /** Privilege number of Receipt Generation. */
    byte PRIVILEGE_RECEIPT_GENERATION = (byte) 0x10;

    /** Privilege number of Security Domain. */
    byte PRIVILEGE_SECURITY_DOMAIN = (byte) 0x00;

    /** Privilege number of Token Verification. */
    byte PRIVILEGE_TOKEN_VERIFICATION = (byte) 0x0A;

    /** Privilege number of Trusted Path. */
    byte PRIVILEGE_TRUSTED_PATH = (byte) 0x08;

    /** Privilege number of Ciphered Load File Data Block. Part of the package since version 1.2. */
    byte PRIVILEGE_CIPHERED_LOAD_FILE_DATA_BLOCK = (byte) 0x11;

    /**
     * Removes a service name this application registered.
     *
     * @param sServiceName the service name: family in the first byte, service in the second
     * @throws ISOException when the caller is not the application of this entry, lacks the Global Service
     *                      privilege or did not register the name
     */
    void deregisterService(short sServiceName) throws ISOException;

    /**
     * Returns the AID of the application.
     *
     * @return the AID
     */
    AID getAID();

    /**
     * Writes the privilege bytes of the application.
     *
     * @param baBuffer the buffer to write to
     * @param sOffset  where to write
     * @return {@code sOffset} plus the number of bytes written
     * @throws ArrayIndexOutOfBoundsException when the bytes do not fit
     */
    short getPrivileges(byte[] baBuffer, short sOffset) throws ArrayIndexOutOfBoundsException;

    /**
     * Returns the life cycle state of the application.
     *
     * @return the state
     */
    byte getState();

    /**
     * Tells whether the application is associated with a Security Domain.
     *
     * @param sdAID the AID of the Security Domain
     * @return true when the AID names a Security Domain of the card that this application is associated with
     */
    boolean isAssociated(AID sdAID);

    /**
     * Tells whether the application has a privilege.
     *
     * @param bPrivilege the privilege number, one of the {@code PRIVILEGE} constants
     * @return true when the application has the privilege; false for an unknown number
     */
    boolean isPrivileged(byte bPrivilege);

    /**
     * Registers a service name for this application; no other application can register it or its family
     * until it is removed.
     *
     * @param sServiceName the service name: family in the first byte, service in the second ('00' for the
     *                     whole family)
     * @throws ISOException when the caller is not the application of this entry, lacks the Global Service
     *                      privilege or the name is taken
     */
    void registerService(short sServiceName) throws ISOException;

    /**
     * Changes the life cycle state of the application within the transitions the Card Specification allows
     * (chapter 5): a value with b8 set locks it, a value without b8 unlocks a locked application, and an
     * application moves itself between its application specific states.
     *
     * @param bState the new state
     * @return true when the state changed
     */
    boolean setState(byte bState);
}
