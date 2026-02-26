package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;

/**
 * Parsed data from a GlobalPlatform INITIALIZE UPDATE response.
 *
 * <p>The INITIALIZE UPDATE response contains information about the card's
 * key configuration and provides the data needed for session key derivation
 * and mutual authentication.</p>
 *
 * <h2>Response structure (28+ bytes):</h2>
 * <pre>
 * Offset  Length  Description
 * 0       10      Key diversification data
 * 10      1       Key version number
 * 11      1       SCP identifier (0x02 or 0x03)
 * 12      2-8     Sequence counter (SCP02: 2 bytes) or card challenge start
 * 14-20   6-8     Card challenge
 * 20-28   8       Card cryptogram
 * </pre>
 *
 * @param diversificationData the 10-byte key diversification data
 * @param keyVersion          the key version number (0-127)
 * @param scpIdentifier       the SCP protocol identifier (2 or 3)
 * @param sequenceCounter     the sequence counter bytes (SCP02: 2 bytes; SCP03: empty)
 * @param cardChallenge       the card challenge bytes
 * @param cardCryptogram      the 8-byte card cryptogram
 */
public record CardInfo(
        byte[] diversificationData,
        int keyVersion,
        int scpIdentifier,
        byte[] sequenceCounter,
        byte[] cardChallenge,
        byte[] cardCryptogram
) {

    /**
     * Parses an INITIALIZE UPDATE response into a CardInfo (explicit challenge mode).
     *
     * @param responseData the response data bytes (minimum 28 bytes)
     * @return parsed CardInfo
     * @throws GPException if the response is too short
     */
    public static CardInfo parse(byte[] responseData) {
        return parse(responseData, false);
    }

    /**
     * Parses an INITIALIZE UPDATE response into a CardInfo.
     *
     * <p>For SCP03 with pseudo-random challenge (i=60), the response is 29 bytes:
     * {@code keyDivData(10) || keyInfo(2) || seqCounter(3) || cardChallenge(6) || cryptogram(8)}.
     * For SCP03 with explicit challenge (i=70, default), the response is 28 bytes:
     * {@code keyDivData(10) || keyInfo(2) || cardChallenge(8) || cryptogram(8)}.</p>
     *
     * @param responseData            the response data bytes
     * @param pseudoRandomChallenge   true for SCP03 i=60 (pseudo-random), false for i=70 (explicit)
     * @return parsed CardInfo
     * @throws GPException if the response is too short
     */
    public static CardInfo parse(byte[] responseData, boolean pseudoRandomChallenge) {
        if (responseData.length < 28) {
            throw new GPException(
                    "INITIALIZE UPDATE response too short: " + responseData.length + " bytes (expected >= 28)");
        }

        byte[] diversification = Arrays.copyOfRange(responseData, 0, 10);
        int keyVersion = responseData[10] & 0xFF;
        int scpId = responseData[11] & 0xFF;

        byte[] seqCounter;
        byte[] cardChallenge;
        byte[] cardCryptogram;

        if (scpId == 0x03 && pseudoRandomChallenge) {
            // SCP03 pseudo-random (i=60): seqCounter(3) + cardChallenge(6) + cryptogram(8) = 29 bytes
            if (responseData.length < 29) {
                throw new GPException(
                        "INITIALIZE UPDATE response too short for SCP03 pseudo-random: "
                                + responseData.length + " bytes (expected >= 29)");
            }
            seqCounter = Arrays.copyOfRange(responseData, 12, 15);
            cardChallenge = Arrays.copyOfRange(responseData, 15, 21);
            cardCryptogram = Arrays.copyOfRange(responseData, 21, 29);
        } else if (scpId == 0x03) {
            // SCP03 explicit (i=70): cardChallenge(8) + cryptogram(8)
            seqCounter = new byte[0];
            cardChallenge = Arrays.copyOfRange(responseData, 12, 20);
            cardCryptogram = Arrays.copyOfRange(responseData, 20, 28);
        } else {
            // SCP02: bytes 12-13 = sequence counter, 14-19 = card challenge, 20-27 = cryptogram
            seqCounter = Arrays.copyOfRange(responseData, 12, 14);
            cardChallenge = Arrays.copyOfRange(responseData, 14, 20);
            cardCryptogram = Arrays.copyOfRange(responseData, 20, 28);
        }

        return new CardInfo(diversification, keyVersion, scpId, seqCounter, cardChallenge, cardCryptogram);
    }

    /**
     * Returns the SCP version (2 or 3).
     *
     * @return the SCP version number
     */
    public int scpVersion() {
        return scpIdentifier;
    }

    /**
     * Returns the key diversification data as a hex string.
     *
     * @return hex-encoded diversification data
     */
    public String diversificationHex() {
        return Hex.encode(diversificationData);
    }

    @Override
    public String toString() {
        return "CardInfo[keyVersion=" + keyVersion
                + ", scp=" + scpIdentifier
                + ", diversification=" + Hex.encode(diversificationData) + "]";
    }
}
