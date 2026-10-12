package org.globalplatform;

import javacard.framework.AID;
import javacard.framework.ISOException;
import javacard.framework.Shareable;

/**
 * The entry of one application in the GlobalPlatform Registry of the card: its AID, life cycle state, privileges,
 * Security Domain and the service names it registered. An applet gets its own entry, or with the Global Registry
 * privilege the entry of another application, from {@link GPSystem#getRegistryEntry(AID)}.
 *
 * <p>The {@code PRIVILEGE} constants are the privilege numbers of GlobalPlatform Card Specification v2.3.1
 * Table 6-1; number n is bit 8 - (n mod 8) of privilege byte n / 8 + 1 (Tables 11-7 to 11-9).</p>
 *
 * <p>Since version 1.1 of the package.</p>
 */
public interface GPRegistryEntry extends Shareable {

    /**
     * Privilege 9, Authorized Management: a Security Domain that may manage card content. Since version 1.1 of the
     * package.
     */
    byte PRIVILEGE_AUTHORIZED_MANAGEMENT = 0x09;
    /** Privilege 3, Card Lock: the application may lock the card. Since version 1.1 of the package. */
    byte PRIVILEGE_CARD_LOCK = 0x03;
    /**
     * Privilege 5, Card Reset: the application may change the historical bytes of the ATR. Since version 1.1 of
     * the package.
     */
    byte PRIVILEGE_CARD_RESET = 0x05;
    /** Privilege 4, Card Terminate: the application may terminate the card. Since version 1.1 of the package. */
    byte PRIVILEGE_CARD_TERMINATE = 0x04;
    /**
     * Privilege 6, CVM Management: the application may manage a shared CVM, see {@link CVM}. Since version 1.1 of
     * the package.
     */
    byte PRIVILEGE_CVM_MANAGEMENT = 0x06;
    /**
     * Privilege 1, DAP Verification: a Security Domain that can verify a Data Authentication Pattern. Since
     * version 1.1 of the package.
     */
    byte PRIVILEGE_DAP_VERIFICATION = 0x01;
    /**
     * Privilege 2, Delegated Management: a Security Domain that can perform delegated card content management.
     * Since version 1.1 of the package.
     */
    byte PRIVILEGE_DELEGATED_MANAGEMENT = 0x02;
    /**
     * Privilege 14, Final Application: the only application that can be selected on a locked or terminated card.
     * Since version 1.1 of the package.
     */
    byte PRIVILEGE_FINAL_APPLICATION = 0x0E;
    /**
     * Privilege 11, Global Delete: the application may delete any card content. Since version 1.1 of the package.
     */
    byte PRIVILEGE_GLOBAL_DELETE = 0x0B;
    /**
     * Privilege 12, Global Lock: the application may lock and unlock any application. Since version 1.1 of the
     * package.
     */
    byte PRIVILEGE_GLOBAL_LOCK = 0x0C;
    /**
     * Privilege 13, Global Registry: the application may read the registry entry of any application. Since
     * version 1.1 of the package.
     */
    byte PRIVILEGE_GLOBAL_REGISTRY = 0x0D;
    /**
     * Privilege 15, Global Service: the application offers services to other applications, see
     * {@link GlobalService}. Since version 1.1 of the package.
     */
    byte PRIVILEGE_GLOBAL_SERVICE = 0x0F;
    /**
     * Privilege 7, Mandated DAP Verification: a Security Domain whose DAP check every load must pass. Since
     * version 1.1 of the package.
     */
    byte PRIVILEGE_MANDATED_DAP = 0x07;
    /**
     * Privilege 16, Receipt Generation: a Security Domain that issues receipts for delegated management. Since
     * version 1.1 of the package.
     */
    byte PRIVILEGE_RECEIPT_GENERATION = 0x10;
    /** Privilege 0, Security Domain: the application is a Security Domain. Since version 1.1 of the package. */
    byte PRIVILEGE_SECURITY_DOMAIN = 0x00;
    /**
     * Privilege 10, Token Verification: a Security Domain that verifies the tokens of delegated management. Since
     * version 1.1 of the package.
     */
    byte PRIVILEGE_TOKEN_VERIFICATION = 0x0A;
    /**
     * Privilege 8, Trusted Path: the application is a trusted path for the communication between applications.
     * Since version 1.1 of the package.
     */
    byte PRIVILEGE_TRUSTED_PATH = 0x08;
    /**
     * Privilege 17, Ciphered Load File Data Block: a Security Domain whose load files must be loaded ciphered.
     * Since version 1.2 of the package.
     */
    byte PRIVILEGE_CIPHERED_LOAD_FILE_DATA_BLOCK = 0x11;

    /**
     * Withdraws a service name this application registered. Only the application of this entry may call, and it
     * needs the Global Service privilege.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param sServiceName the service name: a family byte ({@code FAMILY} constants of {@link GPSystem}) followed
     *                     by the identifier of the service within the family
     * @throws ISOException if the name is not registered by this application or may not be withdrawn
     */
    void deregisterService(short sServiceName) throws ISOException;

    /**
     * Returns the AID of the application of this entry.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @return the AID
     */
    AID getAID();

    /**
     * Writes the privilege bytes of the application of this entry.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param baBuffer the array the privilege bytes are written to
     * @param sOffset  offset where they start
     * @return {@code sOffset} plus the number of bytes written
     * @throws ArrayIndexOutOfBoundsException if the bytes do not fit into the array
     */
    short getPrivileges(byte[] baBuffer, short sOffset) throws ArrayIndexOutOfBoundsException;

    /**
     * Returns the life cycle state of the application of this entry.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @return the state, coded as GlobalPlatform Card Specification v2.3.1 section 11.1.1 defines
     */
    byte getState();

    /**
     * Tells whether the application of this entry is associated with a Security Domain.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param sdAID the AID of a Security Domain on the card
     * @return true if the AID names a Security Domain and the application is associated with it
     */
    boolean isAssociated(AID sdAID);

    /**
     * Tells whether the application of this entry has a privilege.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param bPrivilege a privilege number, one of the {@code PRIVILEGE} constants
     * @return true if the application has the privilege; false also for an unknown number
     */
    boolean isPrivileged(byte bPrivilege);

    /**
     * Registers a service name for this application, so that {@link GPSystem#getService(AID, short)} finds the
     * application by that name alone. A name is registered by one application at a time; a name with identifier
     * '00' stands for its whole family. Only the application of this entry may call, and it needs the Global
     * Service privilege.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param sServiceName the service name: a family byte ({@code FAMILY} constants of {@link GPSystem}) followed
     *                     by the identifier of the service within the family
     * @throws ISOException if the name is taken or the application may not register it
     */
    void registerService(short sServiceName) throws ISOException;

    /**
     * Changes the life cycle state of the application of this entry within the rules of GlobalPlatform Card
     * Specification v2.3.1 chapter 5. A value with bit 8 set locks the application, which the application itself
     * and an application with the Global Lock privilege may do; unlocking (bit 8 clear for a locked application)
     * needs the Global Lock privilege. The step from INSTALLED to SELECTABLE is refused.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param bState the new state; {@link GPSystem#APPLICATION_LOCKED} to lock, '00' to unlock
     * @return true if the state was changed
     */
    boolean setState(byte bState);
}
