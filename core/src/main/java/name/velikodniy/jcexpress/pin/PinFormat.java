package name.velikodniy.jcexpress.pin;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * PIN encoding formats for smart card PIN operations.
 *
 * <p>Different cards expect PINs in different binary formats.
 * This enum provides the three most common encodings.</p>
 */
public enum PinFormat {

    /**
     * ASCII encoding: each digit is its ASCII code.
     * "1234" → {0x31, 0x32, 0x33, 0x34}
     *
     * <p>Most common for JavaCard applets.</p>
     */
    ASCII {
        @Override
        public byte[] encode(String pin) {
            validateDigits(pin);
            return pin.getBytes(StandardCharsets.US_ASCII);
        }
    },

    /**
     * BCD (Binary Coded Decimal): two digits per byte.
     * "1234" → {0x12, 0x34}
     *
     * <p>Odd-length PINs are right-padded with 0xF: "123" → {0x12, 0x3F}</p>
     */
    BCD {
        @Override
        public byte[] encode(String pin) {
            validateDigits(pin);
            int len = (pin.length() + 1) / 2;
            byte[] result = new byte[len];
            for (int i = 0; i < pin.length(); i++) {
                int digit = pin.charAt(i) - '0';
                if (i % 2 == 0) {
                    result[i / 2] = (byte) (digit << 4);
                } else {
                    result[i / 2] |= (byte) digit;
                }
            }
            // Pad odd-length with 0xF
            if (pin.length() % 2 != 0) {
                result[len - 1] |= 0x0F;
            }
            return result;
        }
    },

    /**
     * ISO 9564 Format 2: length-prefixed BCD with 0xFF padding to 8 bytes.
     * "1234" → {0x24, 0x12, 0x34, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF}
     *
     * <p>Used by EMV payment cards. First nibble = 0x2 (format code),
     * second nibble = PIN length, followed by BCD digits padded with 0xF.</p>
     */
    ISO_9564_FORMAT_2 {
        @Override
        public byte[] encode(String pin) {
            validateDigits(pin);
            if (pin.length() < 4 || pin.length() > 12) {
                throw new IllegalArgumentException(
                        "ISO 9564 Format 2 requires PIN length 4-12, got " + pin.length());
            }
            byte[] result = new byte[8];
            Arrays.fill(result, (byte) 0xFF);

            // First byte: 0x2N where N = PIN length
            result[0] = (byte) (0x20 | pin.length());

            // BCD digits starting at nibble 3 (byte 1, high nibble)
            for (int i = 0; i < pin.length(); i++) {
                int digit = pin.charAt(i) - '0';
                int byteIdx = (i + 2) / 2;
                if ((i + 2) % 2 == 0) {
                    result[byteIdx] = (byte) ((digit << 4) | (result[byteIdx] & 0x0F));
                } else {
                    result[byteIdx] = (byte) ((result[byteIdx] & 0xF0) | digit);
                }
            }

            return result;
        }
    };

    /**
     * Encodes a PIN string to bytes in this format.
     *
     * @param pin the PIN as a digit string
     * @return encoded bytes
     * @throws IllegalArgumentException if the PIN contains non-digit characters
     */
    public abstract byte[] encode(String pin);

    static void validateDigits(String pin) {
        if (pin == null || pin.isEmpty()) {
            throw new IllegalArgumentException("PIN must not be null or empty");
        }
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("PIN must contain only digits, found '" + c + "'");
            }
        }
    }
}
