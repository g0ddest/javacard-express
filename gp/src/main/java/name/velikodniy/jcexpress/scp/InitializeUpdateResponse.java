package name.velikodniy.jcexpress.scp;

import java.util.Arrays;

/**
 * The data field of an INITIALIZE UPDATE response, split according to the Secure Channel Protocol.
 *
 * <ul>
 *   <li><b>SCP02</b> (GPCS v2.3.1 Table E-8, 28 bytes): key diversification data (10), key information
 *       (2: Key Version Number, '02'), sequence counter (2), card challenge (6), card cryptogram (8).
 *       The "i" parameter is not part of the response.</li>
 *   <li><b>SCP03</b> (Amendment D Table 7-3): key diversification data (10), key information
 *       (3: Key Version Number, '03', "i"), card challenge (8, or 16 in S16 mode), card cryptogram
 *       (8, or 16 in S16 mode) and a 3-byte sequence counter that is present only when "i" indicates
 *       pseudo-random card challenges (Table 5-1, b5). S16 mode is indicated by "i" b1 (Amendment D v1.2).</li>
 * </ul>
 *
 * @param diversificationData the key diversification data (10 bytes)
 * @param keyVersion          the Key Version Number
 * @param scpIdentifier       the Secure Channel Protocol whose layout was applied (2 or 3)
 * @param option              the SCP03 "i" parameter, or -1 for SCP02 (not transmitted)
 * @param sequenceCounter     the sequence counter (2 bytes for SCP02, 3 or 0 bytes for SCP03)
 * @param cardChallenge       the card challenge
 * @param cardCryptogram      the card cryptogram
 */
public record InitializeUpdateResponse(byte[] diversificationData, int keyVersion, int scpIdentifier, int option,
                                       byte[] sequenceCounter, byte[] cardChallenge, byte[] cardCryptogram) {

    /** Length of the SCP02 response (GPCS v2.3.1 Table E-8). */
    public static final int SCP02_LENGTH = 28;

    /** "i" bit b5: pseudo-random card challenge, sequence counter present (Amd D Table 5-1). */
    static final int SCP03_PSEUDO_RANDOM = 0x10;

    /** "i" bit b1: S16 mode, 16-byte challenges and cryptograms (Amd D v1.2). */
    static final int SCP03_S16 = 0x01;

    /**
     * Parses a response using the Secure Channel Protocol identifier it contains (byte 11).
     *
     * @param data the response data field (without status word)
     * @return the parsed response
     * @throws SCPException if the protocol is not SCP02/SCP03 or the length does not match its layout
     */
    public static InitializeUpdateResponse parse(byte[] data) {
        if (data == null || data.length < 12) {
            throw new SCPException("INITIALIZE UPDATE response too short: "
                    + (data == null ? 0 : data.length) + " bytes");
        }
        return parse(data, data[11] & 0xFF);
    }

    /**
     * Parses a response with an explicitly chosen protocol layout.
     *
     * @param data          the response data field (without status word)
     * @param scpIdentifier 2 for the SCP02 layout, 3 for the SCP03 layout
     * @return the parsed response
     * @throws SCPException if the protocol is unsupported or the length does not match the layout
     */
    public static InitializeUpdateResponse parse(byte[] data, int scpIdentifier) {
        return switch (scpIdentifier) {
            case 2 -> parseScp02(data);
            case 3 -> parseScp03(data);
            default -> throw new SCPException(String.format(
                    "Unsupported Secure Channel Protocol '%02X' in INITIALIZE UPDATE response", scpIdentifier));
        };
    }

    private static InitializeUpdateResponse parseScp02(byte[] data) {
        if (data.length != SCP02_LENGTH) {
            throw new SCPException("SCP02 INITIALIZE UPDATE response must be 28 bytes (GPCS v2.3.1 Table E-8), got "
                    + data.length);
        }
        return new InitializeUpdateResponse(Arrays.copyOf(data, 10), data[10] & 0xFF, 2, -1,
                Arrays.copyOfRange(data, 12, 14), Arrays.copyOfRange(data, 14, 20), Arrays.copyOfRange(data, 20, 28));
    }

    private static InitializeUpdateResponse parseScp03(byte[] data) {
        if (data.length < 13) {
            throw new SCPException("SCP03 INITIALIZE UPDATE response too short: " + data.length + " bytes");
        }
        int option = data[12] & 0xFF;
        int size = (option & SCP03_S16) != 0 ? 16 : 8;
        int counter = (option & SCP03_PSEUDO_RANDOM) != 0 ? 3 : 0;
        int expected = 13 + 2 * size + counter;
        if (data.length != expected) {
            throw new SCPException(String.format("SCP03 INITIALIZE UPDATE response with i=%02X must be %d bytes"
                    + " (Amendment D Table 7-3), got %d", option, expected, data.length));
        }
        int challengeEnd = 13 + size;
        int cryptogramEnd = challengeEnd + size;
        return new InitializeUpdateResponse(Arrays.copyOf(data, 10), data[10] & 0xFF, 3, option,
                Arrays.copyOfRange(data, cryptogramEnd, expected), Arrays.copyOfRange(data, 13, challengeEnd),
                Arrays.copyOfRange(data, challengeEnd, cryptogramEnd));
    }

    /**
     * Returns true for SCP03 S16 mode (16-byte challenges, cryptograms and MACs).
     *
     * @return true if the "i" parameter indicates S16 mode
     */
    public boolean s16() {
        return scpIdentifier == 3 && (option & SCP03_S16) != 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InitializeUpdateResponse r && keyVersion == r.keyVersion
                && scpIdentifier == r.scpIdentifier && option == r.option
                && Arrays.equals(diversificationData, r.diversificationData)
                && Arrays.equals(sequenceCounter, r.sequenceCounter)
                && Arrays.equals(cardChallenge, r.cardChallenge) && Arrays.equals(cardCryptogram, r.cardCryptogram);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cardChallenge) * 31 + Arrays.hashCode(cardCryptogram);
    }

    @Override
    public String toString() {
        return String.format("InitializeUpdateResponse[kvn=%02X, scp=%02X, i=%s]", keyVersion, scpIdentifier,
                option < 0 ? "-" : String.format("%02X", option));
    }
}
