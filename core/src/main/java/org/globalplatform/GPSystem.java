package org.globalplatform;

import javacard.framework.AID;
import name.velikodniy.jcexpress.embedded.gp.CardApi;

/**
 * What the GlobalPlatform environment of a card (the OPEN) offers its applications: the secure channel of the
 * associated Security Domain, the life cycle state of the application and of the card, the GlobalPlatform
 * Registry and Global Services (GlobalPlatform Card Specification v2.3.1 Annex A.1; the methods are specified
 * in the GlobalPlatform Card API). Part of the package since version 1.0.
 *
 * <p>This is the class the simulated cards of this toolkit run: every method asks the card on which the calling
 * applet runs ({@link CardApi}, {@code name.velikodniy.jcexpress.embedded.gp.GlobalPlatformCard}). Applets are
 * compiled against the stubs of {@code javacard-express-api}.</p>
 */
public class GPSystem {

    /** Application life cycle state INSTALLED. */
    public static final byte APPLICATION_INSTALLED = (byte) 0x03;

    /** Application life cycle state SELECTABLE, the first application specific state. */
    public static final byte APPLICATION_SELECTABLE = (byte) 0x07;

    /** Application life cycle state bit LOCKED. Part of the package since version 1.1. */
    public static final byte APPLICATION_LOCKED = (byte) 0x80;

    /** Security Domain life cycle state PERSONALIZED. */
    public static final byte SECURITY_DOMAIN_PERSONALIZED = (byte) 0x0F;

    /** Card life cycle state OP_READY. */
    public static final byte CARD_OP_READY = (byte) 0x01;

    /** Card life cycle state INITIALIZED. */
    public static final byte CARD_INITIALIZED = (byte) 0x07;

    /** Card life cycle state SECURED. */
    public static final byte CARD_SECURED = (byte) 0x0F;

    /** Card life cycle state CARD_LOCKED. */
    public static final byte CARD_LOCKED = (byte) 0x7F;

    /** Card life cycle state TERMINATED. */
    public static final byte CARD_TERMINATED = (byte) 0xFF;

    /** CVM identifier of the Global PIN. */
    public static final byte CVM_GLOBAL_PIN = (byte) 0x11;

    /** CVM identifier of the ETSI PIN of application 1. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_1 = (byte) 0x01;

    /** CVM identifier of the ETSI PIN of application 2. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_2 = (byte) 0x02;

    /** CVM identifier of the ETSI PIN of application 3. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_3 = (byte) 0x03;

    /** CVM identifier of the ETSI PIN of application 4. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_4 = (byte) 0x04;

    /** CVM identifier of the ETSI PIN of application 5. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_5 = (byte) 0x05;

    /** CVM identifier of the ETSI PIN of application 6. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_6 = (byte) 0x06;

    /** CVM identifier of the ETSI PIN of application 7. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_7 = (byte) 0x07;

    /** CVM identifier of the ETSI PIN of application 8. Part of the package since version 1.5. */
    public static final byte CVM_ETSI_PIN_APP_8 = (byte) 0x08;

    /** Service family of secure channels. Part of the package since version 1.1. */
    public static final byte FAMILY_SECURE_CHANNEL = (byte) 0x81;

    /** Service family of Cardholder Verification Methods. Part of the package since version 1.1. */
    public static final byte FAMILY_CVM = (byte) 0x82;

    /**
     * Service family of the Authority service, with the spelling of the API.
     *
     * @deprecated the API replaced the name with {@link #FAMILY_AUTHORITY}.
     */
    @Deprecated
    public static final byte FAMILY_AUHTORITY = (byte) 0x83;

    /**
     * Service family of the Authority service.
     *
     * @deprecated version 1.8 of the package deprecates {@link Authority}.
     */
    @Deprecated
    public static final byte FAMILY_AUTHORITY = (byte) 0x83;

    /** Service family of remote administration over HTTP. Part of the package since version 1.3. */
    public static final byte FAMILY_HTTP_ADMINISTRATION = (byte) 0x84;

    /**
     * Service family of the HTTP administration report.
     *
     * @deprecated version 1.8 of the package replaces it with
     *             {@link AdminSessionListener#SERVICE_ADMIN_SESSION_REPORT}.
     */
    @Deprecated
    public static final byte FAMILY_HTTP_REPORT = (byte) 0x85;

    /** Service family of privacy protocols. Part of the package since version 1.7. */
    public static final byte FAMILY_PRIVACY_PROTOCOL = (byte) 0x86;

    /** Service family of the Authority signature service. Part of the package since version 1.7. */
    public static final byte FAMILY_AUTHORITY_SIGNATURE = (byte) 0x87;

    /** Service family of brokers. Part of the package since version 1.7. */
    public static final byte FAMILY_BROKER = (byte) 0x88;

    /** Service family of the Global Master File. Part of the package since version 1.8. */
    public static final byte FAMILY_GLOBAL_MASTER_FILE = (byte) 0x89;

    /** Service family of remote administration over CoAP. Part of the package since version 1.8. */
    public static final byte FAMILY_COAP_ADMINISTRATION = (byte) 0x8A;

    /** Service family of Cryptographic Service Providers. Part of the package since version 1.8. */
    public static final byte FAMILY_CRYPTO_SERVICE_PROVIDER = (byte) 0x8B;

    /**
     * Service family of the USSM service.
     *
     * @deprecated version 1.8 of the package deprecates the family.
     */
    @Deprecated
    public static final byte FAMILY_USSM = (byte) 0xA0;

    /**
     * The parameter of {@code Applet.getShareableInterfaceObject} with which the OPEN asks a Global Services
     * application for its {@link GlobalService}. Part of the package since version 1.1.
     */
    public static final byte GLOBAL_SERVICE_IDENTIFIER = (byte) 0x80;

    /**
     * Card information: the historical bytes of the Answer To Reset the card sent last on its contact
     * interface. Part of the package since version 1.7.
     */
    public static final short CARD_INFO_ATR_HISTORICAL_BYTES = (short) 0x0080;

    /**
     * Card information: the UID or PUPI of the contactless interface. Part of the package since version 1.7.
     */
    public static final short CARD_INFO_CL_IO_RANDOM = (short) 0x0081;

    /** The API declares a public constructor; the class has only static members. */
    public GPSystem() {
        // nothing to initialise
    }

    /**
     * Returns the life cycle state of the calling application.
     *
     * @return the state (GlobalPlatform Card Specification v2.3.1 Table 11-4)
     */
    public static byte getCardContentState() {
        return CardApi.getCardContentState();
    }

    /**
     * Returns the life cycle state of the card.
     *
     * @return the state (GlobalPlatform Card Specification v2.3.1 Table 11-6)
     */
    public static byte getCardState() {
        return CardApi.getCardState();
    }

    /**
     * Returns a Cardholder Verification Method: the object a Global Services application registered for the
     * identifier in the CVM family, or for the whole family.
     *
     * @param bCVMIdentifier the CVM, for example {@link #CVM_GLOBAL_PIN}
     * @return the CVM, or null when the card has none for the identifier
     */
    public static CVM getCVM(byte bCVMIdentifier) {
        return CardApi.getCVM(bCVMIdentifier);
    }

    /**
     * Returns the secure channel of the Security Domain the calling application is associated with.
     *
     * @return the secure channel
     */
    public static SecureChannel getSecureChannel() {
        return CardApi.getSecureChannel();
    }

    /**
     * Moves the card to the life cycle state CARD_LOCKED (Card Lock privilege).
     *
     * @return true when the card was locked
     */
    public static boolean lockCard() {
        return CardApi.lockCard();
    }

    /**
     * Moves the card to the life cycle state TERMINATED (Card Terminate privilege).
     *
     * @return true when the card was terminated
     */
    public static boolean terminateCard() {
        return CardApi.terminateCard();
    }

    /**
     * Sets the historical bytes of the Answer To Reset the card sends from its next reset on (Card Reset
     * privilege).
     *
     * @param baBuffer the buffer with the historical bytes
     * @param sOffset  where they start
     * @param bLength  their number, 1 to 15
     * @return true when the bytes were set
     */
    public static boolean setATRHistBytes(byte[] baBuffer, short sOffset, byte bLength) {
        return CardApi.setATRHistBytes(baBuffer, sOffset, bLength);
    }

    /**
     * Moves the calling application to another application specific life cycle state, or locks it.
     *
     * @param bState an application specific state ('07' to '7F' with the three low bits set), or a value with
     *               b8 set to lock the application
     * @return true when the state changed
     */
    public static boolean setCardContentState(byte bState) {
        return CardApi.setCardContentState(bState);
    }

    /**
     * Returns an entry of the GlobalPlatform Registry. Part of the package since version 1.1.
     *
     * @param aid the application, or null for the calling application
     * @return the entry, or null when there is none or the caller may not see it (another application's
     *         entry needs the Global Registry privilege)
     */
    public static GPRegistryEntry getRegistryEntry(AID aid) {
        return CardApi.getRegistryEntry(aid);
    }

    /**
     * Returns the Global Service of an application that provides a service. Part of the package since
     * version 1.1.
     *
     * @param serverAID    the Global Services application, or null to search the registered service names
     * @param sServiceName the service name: family in the first byte, service in the second ('00' for any
     *                     service of the family)
     * @return the Global Service, or null when no application provides the service
     */
    public static GlobalService getService(AID serverAID, short sServiceName) {
        return CardApi.getService(serverAID, sServiceName);
    }

    /**
     * Writes card information into a buffer. Part of the package since version 1.7.
     *
     * @param buffer the buffer to write to
     * @param offset where to write
     * @param info   one of the {@code CARD_INFO} constants, or the tag of a data object of the Issuer
     *               Security Domain
     * @return {@code offset} plus the number of bytes written
     */
    public static short getCardInfo(byte[] buffer, short offset, short info) {
        return CardApi.getCardInfo(buffer, offset, info);
    }

    /**
     * Returns the length of card information. Part of the package since version 1.7.
     *
     * @param info one of the {@code CARD_INFO} constants, or the tag of a data object of the Issuer Security
     *             Domain
     * @return the length in bytes
     */
    public static short getCardInfoLength(short info) {
        return CardApi.getCardInfoLength(info);
    }
}
