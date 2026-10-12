package name.velikodniy.jcexpress.embedded.gp;

import javacard.framework.ISOException;

/**
 * The card information of {@code GPSystem.getCardInfo} and {@code getCardInfoLength} (GlobalPlatform Card API
 * 1.8): values in the ranges the API reserves name internal card data, of which the simulated card has the
 * historical bytes of the ATR; any other value is the tag of a data object in the data store of the Issuer
 * Security Domain.
 */
final class CardInfo {

    /** The historical bytes of the ATR sent last on the contact interface ({@code CARD_INFO_ATR_HISTORICAL_BYTES}). */
    static final int ATR_HISTORICAL_BYTES = 0x80;

    private static final int SW_WRONG_DATA = 0x6A80;
    private static final int SW_REFERENCED_DATA_NOT_FOUND = 0x6A88;

    private CardInfo() {
    }

    /**
     * Finds card information.
     *
     * @param card the card
     * @param info the value of the {@code info} parameter as an unsigned 16-bit value
     * @return the information
     * @throws ISOException '6A88' when the card does not have the information, '6A80' when the value is neither
     *                      in a reserved range nor a BER-TLV tag
     */
    static byte[] of(GlobalPlatformCard card, int info) {
        byte[] value;
        if (reserved(info)) {
            value = info == ATR_HISTORICAL_BYTES ? card.historicalBytes() : new byte[0];
        } else if (berTlvTag(info)) {
            value = card.dataObjects().getOrDefault(info, new byte[0]);
        } else {
            throw new ISOException((short) SW_WRONG_DATA);
        }
        if (value.length == 0) {
            throw new ISOException((short) SW_REFERENCED_DATA_NOT_FOUND);
        }
        return value;
    }

    /** The ranges the API reserves for internal card data. */
    private static boolean reserved(int info) {
        return between(info, 0x80, 0x9E) || between(info, 0xA0, 0xBE) || between(info, 0xC0, 0xDD)
                || between(info, 0xE0, 0xFD) || between(info, 0xDF1F, 0xDF7F) || between(info, 0xFF1F, 0xFF7F);
    }

    private static boolean between(int value, int from, int to) {
        return value >= from && value <= to;
    }

    /**
     * Tells whether a value is a BER-TLV tag of one or two bytes (ISO/IEC 7816-4:2005 5.2.2.1): one byte whose
     * five low bits are not all set and that is not '00', or a first byte with the five low bits set followed by
     * a last byte '1F' to '7F'.
     *
     * @param tag the value
     * @return true for a tag
     */
    static boolean berTlvTag(int tag) {
        if (tag > 0 && tag <= 0xFF) {
            return (tag & 0x1F) != 0x1F;
        }
        int first = tag >> 8;
        int second = tag & 0xFF;
        return tag > 0xFF && tag <= 0xFFFF && (first & 0x1F) == 0x1F && second >= 0x1F && second <= 0x7F;
    }
}
