package javacard.framework;

/**
 * Defines constants related to ISO 7816-3 and ISO 7816-4.
 */
public interface ISO7816 {

    /** Offset of the class byte CLA in the APDU buffer. */
    byte OFFSET_CLA = 0;
    /** Offset of the instruction byte INS in the APDU buffer. */
    byte OFFSET_INS = 1;
    /** Offset of parameter byte P1 in the APDU buffer. */
    byte OFFSET_P1 = 2;
    /** Offset of parameter byte P2 in the APDU buffer. */
    byte OFFSET_P2 = 3;
    /** Offset of the Lc field in the APDU buffer. */
    byte OFFSET_LC = 4;
    /** Offset of the command data in the APDU buffer when the command has a one-byte Lc field. */
    byte OFFSET_CDATA = 5;
    /** Offset of the command data in the APDU buffer when the command uses an extended length (Lc is 3 bytes). */
    byte OFFSET_EXT_CDATA = 7;

    /** Interindustry class byte without secure messaging, chaining or logical channel. */
    byte CLA_ISO7816 = 0x00;

    /** INS byte of the SELECT command (ISO/IEC 7816-4). */
    byte INS_SELECT = (byte) 0xA4;
    /** INS byte of the EXTERNAL AUTHENTICATE command (ISO/IEC 7816-4). */
    byte INS_EXTERNAL_AUTHENTICATE = (byte) 0x82;

    /** Status word '9000': normal processing. */
    short SW_NO_ERROR = (short) 0x9000;
    /** Status word '61XX' with SW2 = '00'; SW2 tells how many response bytes are still available. */
    short SW_BYTES_REMAINING_00 = 0x6100;
    /** Status word '6700': wrong length. */
    short SW_WRONG_LENGTH = 0x6700;
    /** Status word '6982': security status not satisfied. */
    short SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982;
    /** Status word '6983': file invalid. */
    short SW_FILE_INVALID = 0x6983;
    /** Status word '6984': data invalid. */
    short SW_DATA_INVALID = 0x6984;
    /** Status word '6985': conditions of use not satisfied. */
    short SW_CONDITIONS_NOT_SATISFIED = 0x6985;
    /** Status word '6986': command not allowed (no current EF). */
    short SW_COMMAND_NOT_ALLOWED = 0x6986;
    /** Status word '6999': applet selection failed (Java Card specific). */
    short SW_APPLET_SELECT_FAILED = 0x6999;
    /** Status word '6A80': incorrect parameters in the command data field. */
    short SW_WRONG_DATA = 0x6A80;
    /** Status word '6A81': function not supported. */
    short SW_FUNC_NOT_SUPPORTED = 0x6A81;
    /** Status word '6A82': file or application not found. */
    short SW_FILE_NOT_FOUND = 0x6A82;
    /** Status word '6A83': record not found. */
    short SW_RECORD_NOT_FOUND = 0x6A83;
    /** Status word '6A86': incorrect parameters P1-P2. */
    short SW_INCORRECT_P1P2 = 0x6A86;
    /** Status word '6B00': wrong parameters P1-P2. */
    short SW_WRONG_P1P2 = 0x6B00;
    /** Status word '6CXX' with SW2 = '00'; SW2 tells the exact number of available bytes (Le). */
    short SW_CORRECT_LENGTH_00 = 0x6C00;
    /** Status word '6D00': instruction code not supported or invalid. */
    short SW_INS_NOT_SUPPORTED = 0x6D00;
    /** Status word '6E00': class not supported. */
    short SW_CLA_NOT_SUPPORTED = 0x6E00;
    /** Status word '6F00': no precise diagnosis. */
    short SW_UNKNOWN = 0x6F00;
    /** Status word '6A84': not enough memory space in the file. */
    short SW_FILE_FULL = 0x6A84;
    /** Status word '6881': logical channel not supported. */
    short SW_LOGICAL_CHANNEL_NOT_SUPPORTED = 0x6881;
    /** Status word '6882': secure messaging not supported. */
    short SW_SECURE_MESSAGING_NOT_SUPPORTED = 0x6882;
    /** Status word '6200': warning, state of non-volatile memory unchanged. */
    short SW_WARNING_STATE_UNCHANGED = 0x6200;
    /** Status word '6883': last command of the chain expected. */
    short SW_LAST_COMMAND_EXPECTED = 0x6883;
    /** Status word '6884': command chaining not supported. */
    short SW_COMMAND_CHAINING_NOT_SUPPORTED = 0x6884;
}
