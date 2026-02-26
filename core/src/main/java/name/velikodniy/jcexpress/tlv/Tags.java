package name.velikodniy.jcexpress.tlv;

/**
 * Common BER-TLV tag constants from ISO 7816-4, GlobalPlatform and EMV.
 */
public final class Tags {

    private Tags() {
    }

    // ISO 7816-4 — FCI (File Control Information)
    public static final int FCI_TEMPLATE = 0x6F;
    public static final int DF_NAME = 0x84;
    public static final int FCI_PROPRIETARY = 0xA5;

    // ISO 7816-4 — File structure
    public static final int FCP_TEMPLATE = 0x62;
    public static final int FMD_TEMPLATE = 0x64;
    public static final int FILE_SIZE = 0x80;
    public static final int TOTAL_FILE_SIZE = 0x81;
    public static final int FILE_DESCRIPTOR = 0x82;
    public static final int FILE_IDENTIFIER = 0x83;
    public static final int SFI = 0x88;
    public static final int LIFE_CYCLE_STATUS = 0x8A;
    public static final int SECURITY_ATTRIBUTE = 0x8C;

    // ISO 7816-4 — Data objects
    public static final int DISCRETIONARY_DATA = 0x53;
    public static final int COMMAND_TO_PERFORM = 0xAC;

    // EMV — Application
    public static final int APPLICATION_LABEL = 0x50;
    public static final int APPLICATION_PRIORITY = 0x87;
    public static final int PDOL = 0x9F38;
    public static final int APPLICATION_PREFERRED_NAME = 0x9F12;
    public static final int ISSUER_CODE_TABLE_INDEX = 0x9F11;
    public static final int LANGUAGE_PREFERENCE = 0x5F2D;

    // EMV — Processing
    public static final int AIP = 0x82;  // Application Interchange Profile
    public static final int AFL = 0x94;  // Application File Locator
    public static final int CDOL1 = 0x8C;
    public static final int CDOL2 = 0x8D;
    public static final int CVM_LIST = 0x8E;

    // EMV — Response templates
    public static final int RESPONSE_FORMAT_1 = 0x80;
    public static final int RESPONSE_FORMAT_2 = 0x77;

    // GlobalPlatform
    public static final int GP_CARD_DATA = 0x66;
    public static final int GP_CARD_RECOGNITION_DATA = 0x73;
    public static final int GP_SECURITY_DOMAIN_AID = 0x4F;

    // GlobalPlatform — GET DATA tags
    public static final int GP_IIN = 0x42;
    public static final int GP_CIN = 0x45;
    public static final int GP_KEY_INFO_TEMPLATE = 0xE0;
    public static final int GP_KEY_INFO_DATA = 0xC0;
    public static final int GP_SEQUENCE_COUNTER = 0xC1;
    public static final int GP_CONFIRMATION_COUNTER = 0xC2;

    /** Card Production Life Cycle Data (GET DATA P1P2=9F7F). */
    public static final int GP_CPLC = 0x9F7F;

    // PACE / General Authentication (ISO 7816-4 / BSI TR-03110)
    /** Dynamic Authentication Data template (constructed). */
    public static final int DYNAMIC_AUTH_DATA = 0x7C;
    /** Encrypted nonce (PACE Step 1 response). */
    public static final int PACE_NONCE = 0x80;
    /** Mapping data — terminal ephemeral public key (PACE Step 2 command). */
    public static final int PACE_MAP_DATA = 0x81;
    /** Card mapping response public key (PACE Step 2 response). */
    public static final int PACE_MAP_RESPONSE = 0x82;
    /** Terminal ephemeral public key on mapped generator (PACE Step 3 command). */
    public static final int PACE_EPHEMERAL_PK = 0x83;
    /** Card ephemeral public key on mapped generator (PACE Step 3 response). */
    public static final int PACE_EPHEMERAL_PK_RESPONSE = 0x84;
    /** Authentication token (PACE Step 4 command). */
    public static final int PACE_AUTH_TOKEN = 0x85;
    /** Card authentication token (PACE Step 4 response). */
    public static final int PACE_AUTH_TOKEN_RESPONSE = 0x86;
    /** Certificate Authority Reference. */
    public static final int PACE_CAR = 0x87;
    /** Previous Certificate Authority Reference. */
    public static final int PACE_CAR2 = 0x88;

    // Card Recognition Data sub-tags (inside 0x73)
    public static final int GP_OID = 0x06;
    public static final int GP_CARD_IDENTIFICATION_SCHEME = 0x60;
    public static final int GP_CARD_CONFIG_DETAILS = 0x64;
    public static final int GP_CARD_CHIP_DETAILS = 0x65;

    /**
     * Returns true if the tag indicates a constructed (non-primitive) TLV.
     * Per BER encoding (ISO/IEC 8825-1), bit 6 of the first tag byte
     * indicates constructed (1) vs primitive (0).
     *
     * @param tag the tag value
     * @return true if the tag is constructed
     */
    public static boolean isConstructed(int tag) {
        int firstByte = firstByte(tag);
        return (firstByte & 0x20) != 0;
    }

    /**
     * Returns the number of bytes this tag occupies in encoded form.
     *
     * @param tag the tag value
     * @return 1, 2, or 3
     */
    public static int tagSize(int tag) {
        if (tag <= 0xFF) return 1;
        if (tag <= 0xFFFF) return 2;
        return 3;
    }

    /**
     * Extracts the first (most significant) byte of a tag.
     */
    static int firstByte(int tag) {
        if (tag <= 0xFF) return tag;
        if (tag <= 0xFFFF) return (tag >> 8) & 0xFF;
        return (tag >> 16) & 0xFF;
    }
}
