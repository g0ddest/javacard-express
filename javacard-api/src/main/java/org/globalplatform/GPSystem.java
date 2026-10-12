package org.globalplatform;

import javacard.framework.AID;

/**
 * The services of the GlobalPlatform environment of the card (the OPEN) for the applet that calls them: its
 * secure channel, its life cycle state and the card's, shared Cardholder Verification Methods, the registry of
 * applications and the services applications offer each other.
 *
 * <p>In an applet's constructor and in its {@code install} method only {@link #getCardState()},
 * {@link #getCVM(byte)} and {@link #getService(AID, short)} have a defined behaviour: the applet may not have its
 * registry entry yet (GlobalPlatform Card Specification v2.3.1 A.1).</p>
 *
 * <p>Since version 1.0 of the package.</p>
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class GPSystem {

    /** Life cycle state INSTALLED of an application ('03'). Since version 1.0 of the package. */
    public static final byte APPLICATION_INSTALLED = 0x03;
    /**
     * Life cycle state SELECTABLE of an application ('07'); the states an application defines for itself keep
     * these three bits set. Since version 1.0 of the package.
     */
    public static final byte APPLICATION_SELECTABLE = 0x07;
    /**
     * The bit of a life cycle state that tells that the application is locked ('80'). Since version 1.1 of the
     * package.
     */
    public static final byte APPLICATION_LOCKED = (byte) 0x80;
    /** Life cycle state PERSONALIZED of a Security Domain ('0F'). Since version 1.0 of the package. */
    public static final byte SECURITY_DOMAIN_PERSONALIZED = 0x0F;
    /** Life cycle state OP_READY of the card ('01'). Since version 1.0 of the package. */
    public static final byte CARD_OP_READY = 0x01;
    /** Life cycle state INITIALIZED of the card ('07'). Since version 1.0 of the package. */
    public static final byte CARD_INITIALIZED = 0x07;
    /** Life cycle state SECURED of the card ('0F'). Since version 1.0 of the package. */
    public static final byte CARD_SECURED = 0x0F;
    /** Life cycle state CARD_LOCKED of the card ('7F'). Since version 1.0 of the package. */
    public static final byte CARD_LOCKED = 0x7F;
    /** Life cycle state TERMINATED of the card ('FF'). Since version 1.0 of the package. */
    public static final byte CARD_TERMINATED = (byte) 0xFF;
    /** CVM identifier of the global PIN ('11'), for {@link #getCVM(byte)}. Since version 1.0 of the package. */
    public static final byte CVM_GLOBAL_PIN = 0x11;
    /** CVM identifier of the ETSI application PIN 1 ('01'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_1 = 0x01;
    /** CVM identifier of the ETSI application PIN 2 ('02'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_2 = 0x02;
    /** CVM identifier of the ETSI application PIN 3 ('03'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_3 = 0x03;
    /** CVM identifier of the ETSI application PIN 4 ('04'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_4 = 0x04;
    /** CVM identifier of the ETSI application PIN 5 ('05'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_5 = 0x05;
    /** CVM identifier of the ETSI application PIN 6 ('06'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_6 = 0x06;
    /** CVM identifier of the ETSI application PIN 7 ('07'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_7 = 0x07;
    /** CVM identifier of the ETSI application PIN 8 ('08'). Since version 1.5 of the package. */
    public static final byte CVM_ETSI_PIN_APP_8 = 0x08;
    /** Service family of secure channels ('81'). Since version 1.1 of the package. */
    public static final byte FAMILY_SECURE_CHANNEL = (byte) 0x81;
    /** Service family of Cardholder Verification Methods ('82'). Since version 1.1 of the package. */
    public static final byte FAMILY_CVM = (byte) 0x82;
    /**
     * Service family of the authority service ('83'), under a misspelt name. Since version 1.3 of the package.
     *
     * @deprecated A misspelling of {@link #FAMILY_AUTHORITY}, which has the same value.
     */
    @Deprecated
    public static final byte FAMILY_AUHTORITY = (byte) 0x83;
    /**
     * Service family of the authority service ('83'), see {@link Authority}. Since version 1.4 of the package.
     *
     * @deprecated Deprecated in version 1.8 of the package together with {@link Authority}.
     */
    @Deprecated
    public static final byte FAMILY_AUTHORITY = (byte) 0x83;
    /**
     * Service family of administration sessions over HTTP ('84'), see {@link AdministrationSession}. Since version
     * 1.3 of the package.
     */
    public static final byte FAMILY_HTTP_ADMINISTRATION = (byte) 0x84;
    /**
     * Service family of the reports about HTTP administration sessions ('85'). Since version 1.3 of the package.
     *
     * @deprecated Deprecated in version 1.8 of the package; {@link AdminSessionListener} is found with
     *             {@link AdminSessionListener#SERVICE_ADMIN_SESSION_REPORT} instead.
     */
    @Deprecated
    public static final byte FAMILY_HTTP_REPORT = (byte) 0x85;
    /** Service family of privacy protocols ('86'). Since version 1.7 of the package. */
    public static final byte FAMILY_PRIVACY_PROTOCOL = (byte) 0x86;
    /**
     * Service family of authority signatures ('87'), see {@link AuthoritySignature}. Since version 1.7 of the
     * package.
     */
    public static final byte FAMILY_AUTHORITY_SIGNATURE = (byte) 0x87;
    /** Service family of the broker interface ('88'). Since version 1.7 of the package. */
    public static final byte FAMILY_BROKER = (byte) 0x88;
    /** Service family of the global master file ('89'). Since version 1.8 of the package. */
    public static final byte FAMILY_GLOBAL_MASTER_FILE = (byte) 0x89;
    /**
     * Service family of administration sessions over CoAP ('8A'), see {@link AdministrationSession}. Since version
     * 1.8 of the package.
     */
    public static final byte FAMILY_COAP_ADMINISTRATION = (byte) 0x8A;
    /** Service family of the crypto service provider ('8B'). Since version 1.8 of the package. */
    public static final byte FAMILY_CRYPTO_SERVICE_PROVIDER = (byte) 0x8B;
    /**
     * Service family USSM ('A0'). Since version 1.1 of the package.
     *
     * @deprecated Deprecated in version 1.8 of the package.
     */
    @Deprecated
    public static final byte FAMILY_USSM = (byte) 0xA0;
    /**
     * The parameter with which the card asks an applet's {@code getShareableInterfaceObject} for its
     * {@link GlobalService} ('80'). Since version 1.1 of the package.
     */
    public static final byte GLOBAL_SERVICE_IDENTIFIER = (byte) 0x80;
    /**
     * Card information for {@link #getCardInfo}: the historical bytes of the last Answer To Reset on the contact
     * interface. Since version 1.7 of the package.
     */
    public static final short CARD_INFO_ATR_HISTORICAL_BYTES = 0x80;
    /**
     * Card information for {@link #getCardInfo}: the UID or PUPI of the contactless interface, available while the
     * card is in the field. Since version 1.7 of the package.
     */
    public static final short CARD_INFO_CL_IO_RANDOM = 0x81;

    /**
     * Creates an object of the class; all its services are static.
     *
     * <p>Since version 1.0 of the package.</p>
     */
    public GPSystem() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the life cycle state of the calling applet.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return {@link #APPLICATION_INSTALLED}, {@link #APPLICATION_SELECTABLE} or a state the applet set itself,
     *         with the bit {@link #APPLICATION_LOCKED} when the applet is locked
     */
    public static byte getCardContentState() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the life cycle state of the card.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return {@link #CARD_OP_READY}, {@link #CARD_INITIALIZED}, {@link #CARD_SECURED}, {@link #CARD_LOCKED} or
     *         {@link #CARD_TERMINATED}
     */
    public static byte getCardState() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns a Cardholder Verification Method the card shares between applications. Since version 1.1 the card
     * finds it as a Global Service of the family {@link #FAMILY_CVM}.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param bCVMIdentifier the identifier of the CVM, for example {@link #CVM_GLOBAL_PIN}
     * @return the CVM, or null if the card has none with this identifier
     */
    public static CVM getCVM(byte bCVMIdentifier) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the secure channel that the Security Domain of the calling applet provides to it.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return the secure channel
     */
    public static SecureChannel getSecureChannel() {
        throw new RuntimeException("stub");
    }

    /**
     * Moves the card to the life cycle state {@link #CARD_LOCKED}. The calling applet needs the Card Lock
     * privilege.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true if the card was locked
     */
    public static boolean lockCard() {
        throw new RuntimeException("stub");
    }

    /**
     * Moves the card to the life cycle state {@link #CARD_TERMINATED}. The calling applet needs the Card Terminate
     * privilege.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true if the card was terminated
     */
    public static boolean terminateCard() {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the historical bytes of the Answer To Reset that the contact interface sends after the next power-up or
     * cold reset. The calling applet needs the Card Reset privilege, and the length is 1 to 15.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the historical bytes
     * @param sOffset  offset of the historical bytes
     * @param bLength  number of historical bytes
     * @return true if the historical bytes were set
     */
    public static boolean setATRHistBytes(byte[] baBuffer, short sOffset, byte bLength) {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the life cycle state of the calling applet: to another state of its own ('07' to '7F' with the three
     * low bits set) or, since version 1.5 of the package, to locked with any value that has bit 8 set. Refused
     * while the applet is INSTALLED or locked; an applet cannot unlock itself.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param bState the new state, or a value with the bit {@link #APPLICATION_LOCKED} to lock the applet
     * @return true if the state was changed
     */
    public static boolean setCardContentState(byte bState) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the registry entry of an application. Another application's entry needs the Global Registry
     * privilege.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param aid the AID of the application, or null for the calling applet
     * @return the entry, or null if there is none or the calling applet may not see it
     */
    public static GPRegistryEntry getRegistryEntry(AID aid) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the entry point of the application that provides a service. Without an AID the card looks among the
     * service names that applications registered ({@link GPRegistryEntry#registerService(short)}); with an AID
     * that application must have the Global Service privilege. The card obtains the entry point from the
     * application's {@code getShareableInterfaceObject} with the caller's AID and the parameter
     * {@link #GLOBAL_SERVICE_IDENTIFIER}.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param serverAID    the AID of the application that provides the service, or null
     * @param sServiceName the service name: a family byte (one of the {@code FAMILY} constants) followed by the
     *                     identifier of the service within the family; '00' for any service of the family
     * @return the entry point, or null if no application provides the service
     */
    public static GlobalService getService(AID serverAID, short sServiceName) {
        throw new RuntimeException("stub");
    }

    /**
     * Writes a piece of information about the card. The value {@code info} is one of the {@code CARD_INFO}
     * constants or the tag of a data object in the data store of the Issuer Security Domain, of which the value
     * part is written. Failures are reported as an {@code ISOException}: '6A80' if {@code info} is not a valid
     * tag, '6A88' if the card does not have the information.
     *
     * <p>Since version 1.7 of the package.</p>
     *
     * @param buffer the array the information is written to
     * @param offset offset where it starts
     * @param info   what is asked for
     * @return {@code offset} plus the number of bytes written
     */
    public static short getCardInfo(byte[] buffer, short offset, short info) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the length of a piece of information about the card, see {@link #getCardInfo}, which also tells
     * how failures are reported.
     *
     * <p>Since version 1.7 of the package.</p>
     *
     * @param info what is asked for
     * @return the number of bytes {@link #getCardInfo} writes for it
     */
    public static short getCardInfoLength(short info) {
        throw new RuntimeException("stub");
    }
}
