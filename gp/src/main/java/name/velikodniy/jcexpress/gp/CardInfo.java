package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.scp.InitializeUpdateResponse;
import name.velikodniy.jcexpress.scp.SCPException;

import java.util.Arrays;

/**
 * Parsed data from a GlobalPlatform INITIALIZE UPDATE response.
 *
 * <p>The INITIALIZE UPDATE response contains information about the card's key configuration and
 * provides the data needed for session key derivation and mutual authentication.</p>
 *
 * <h2>Response structure:</h2>
 * <pre>
 * SCP02 (GPCS v2.3.1 Table E-8, 28 bytes):
 *   key diversification data (10) | KVN (1) | '02' (1) | sequence counter (2) | card challenge (6)
 *   | card cryptogram (8)
 * SCP03 (Amendment D Table 7-3):
 *   key diversification data (10) | KVN (1) | '03' (1) | i (1) | card challenge (8, S16: 16)
 *   | card cryptogram (8, S16: 16) | [sequence counter (3), only with pseudo-random challenges]
 * </pre>
 *
 * @param diversificationData  the 10-byte key diversification data
 * @param keyVersion           the key version number
 * @param scpIdentifier        the SCP protocol identifier (2 or 3)
 * @param sequenceCounter      the sequence counter bytes (SCP02: 2 bytes; SCP03: 3 bytes or empty)
 * @param cardChallenge        the card challenge bytes
 * @param cardCryptogram       the card cryptogram
 * @param implementationOption the SCP03 "i" parameter, or -1 for SCP02 (not part of its response)
 */
public record CardInfo(
        byte[] diversificationData,
        int keyVersion,
        int scpIdentifier,
        byte[] sequenceCounter,
        byte[] cardChallenge,
        byte[] cardCryptogram,
        int implementationOption
) {

    /**
     * Creates a CardInfo without implementation option (compatibility constructor).
     *
     * @param diversificationData the 10-byte key diversification data
     * @param keyVersion          the key version number
     * @param scpIdentifier       the SCP protocol identifier
     * @param sequenceCounter     the sequence counter bytes
     * @param cardChallenge       the card challenge bytes
     * @param cardCryptogram      the card cryptogram
     */
    public CardInfo(byte[] diversificationData, int keyVersion, int scpIdentifier, byte[] sequenceCounter,
                    byte[] cardChallenge, byte[] cardCryptogram) {
        this(diversificationData, keyVersion, scpIdentifier, sequenceCounter, cardChallenge, cardCryptogram, -1);
    }

    /**
     * Parses an INITIALIZE UPDATE response, choosing the layout from the SCP identifier (byte 11).
     *
     * @param responseData the response data bytes
     * @return parsed CardInfo
     * @throws GPException if the protocol is not SCP02/SCP03 or the length does not match its layout
     */
    public static CardInfo parse(byte[] responseData) {
        if (responseData == null || responseData.length < 12) {
            throw new GPException("INITIALIZE UPDATE response too short: "
                    + (responseData == null ? 0 : responseData.length) + " bytes");
        }
        return parse(responseData, responseData[11] & 0xFF);
    }

    /**
     * Parses an INITIALIZE UPDATE response with an explicitly chosen protocol layout.
     *
     * @param responseData the response data bytes
     * @param scpVersion   2 for the SCP02 layout, 3 for the SCP03 layout
     * @return parsed CardInfo
     * @throws GPException if the protocol is unsupported or the length does not match the layout
     */
    public static CardInfo parse(byte[] responseData, int scpVersion) {
        try {
            InitializeUpdateResponse r = InitializeUpdateResponse.parse(responseData, scpVersion);
            return new CardInfo(r.diversificationData(), r.keyVersion(), r.scpIdentifier(), r.sequenceCounter(),
                    r.cardChallenge(), r.cardCryptogram(), r.option());
        } catch (SCPException e) {
            throw new GPException(e.getMessage(), e);
        }
    }

    /**
     * Parses an INITIALIZE UPDATE response.
     *
     * @param responseData          the response data bytes
     * @param pseudoRandomChallenge ignored: the SCP03 layout is defined by the "i" parameter of the response
     * @return parsed CardInfo
     * @throws GPException if the response is malformed
     * @deprecated whether an SCP03 card uses pseudo-random challenges is part of the response
     *             (Amendment D Table 5-1, Table 7-3); use {@link #parse(byte[])}
     */
    @Deprecated
    public static CardInfo parse(byte[] responseData, boolean pseudoRandomChallenge) {
        return parse(responseData);
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
    public boolean equals(Object o) {
        return o instanceof CardInfo c && keyVersion == c.keyVersion && scpIdentifier == c.scpIdentifier
                && implementationOption == c.implementationOption
                && Arrays.equals(diversificationData, c.diversificationData)
                && Arrays.equals(sequenceCounter, c.sequenceCounter)
                && Arrays.equals(cardChallenge, c.cardChallenge)
                && Arrays.equals(cardCryptogram, c.cardCryptogram);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cardChallenge) * 31 + Arrays.hashCode(cardCryptogram);
    }

    @Override
    public String toString() {
        return "CardInfo[keyVersion=" + keyVersion
                + ", scp=" + scpIdentifier
                + (implementationOption < 0 ? "" : String.format(", i=%02X", implementationOption))
                + ", diversification=" + Hex.encode(diversificationData) + "]";
    }
}
