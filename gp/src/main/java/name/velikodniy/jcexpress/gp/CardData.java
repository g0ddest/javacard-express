package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.tlv.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed response from GET DATA (P1P2=0066) — Card Data.
 *
 * <p>The Card Data template (tag 0x66) contains the Card Recognition Data (tag 0x73),
 * which includes OIDs describing the card's capabilities (supported protocols,
 * cryptographic algorithms, etc.).</p>
 *
 * <h3>Response structure:</h3>
 * <pre>
 * 66 len                           — Card Data template
 *   73 len                         — Card Recognition Data
 *     06 len OID-value             — GlobalPlatform OID
 *     60 len                       — Card identification scheme
 *       06 len OID-value           — scheme OID
 *       ...
 *     ...
 * </pre>
 *
 * @param rawData         the full response data
 * @param recognitionData the parsed Card Recognition Data (tag 0x73), or empty TLVList
 */
public record CardData(
        byte[] rawData,
        TLVList recognitionData
) {

    /**
     * Parses a GET DATA 0066 response into a CardData.
     *
     * @param responseData the response data bytes
     * @return parsed CardData
     */
    public static CardData parse(byte[] responseData) {
        TLVList top = TLVParser.parse(responseData);

        TLVList recData = top.find(Tags.GP_CARD_DATA)
                .map(TLV::children)
                .flatMap(children -> children.find(Tags.GP_CARD_RECOGNITION_DATA))
                .map(TLV::children)
                .orElse(TLVList.empty());

        return new CardData(responseData.clone(), recData);
    }

    /**
     * Extracts all OID values from the Card Recognition Data.
     *
     * <p>OIDs are encoded with tag 0x06. This method collects all OID values
     * found at any depth within the recognition data.</p>
     *
     * @return list of OID byte arrays
     */
    public List<byte[]> oids() {
        List<byte[]> result = new ArrayList<>();
        collectOids(recognitionData, result);
        return result;
    }

    private void collectOids(TLVList list, List<byte[]> result) {
        for (TLV tlv : list) {
            if (tlv.tag() == Tags.GP_OID) {
                result.add(tlv.value().clone());
            }
            if (tlv.isConstructed()) {
                collectOids(tlv.children(), result);
            }
        }
    }

    /**
     * Returns all OIDs as dot-notation strings.
     *
     * @return list of OID strings (e.g., "1.2.840.114283.1")
     */
    public List<String> oidStrings() {
        return oids().stream()
                .map(CardData::oidToString)
                .toList();
    }

    /**
     * Returns the GlobalPlatform version OID, if present.
     *
     * <p>Looks for the first top-level OID (tag 0x06) starting with {@code 1.2.840.114283}
     * in the Card Recognition Data. The last arc indicates the GP version:</p>
     * <ul>
     *   <li>{@code 1.2.840.114283.1} → GP 2.1.1</li>
     *   <li>{@code 1.2.840.114283.2} → GP 2.2</li>
     *   <li>{@code 1.2.840.114283.4} → GP 2.3</li>
     * </ul>
     *
     * @return the GP version OID string, or empty if not found
     */
    public Optional<String> gpVersion() {
        for (TLV tlv : recognitionData) {
            if (tlv.tag() == Tags.GP_OID) {
                String oid = oidToString(tlv.value());
                if (oid.startsWith("1.2.840.114283")) {
                    return Optional.of(oid);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns supported SCP version OIDs from the Card Identification Scheme (tag 0x60).
     *
     * <p>The card identification scheme is a constructed element inside the
     * Card Recognition Data that contains OIDs identifying the supported
     * Secure Channel Protocols.</p>
     *
     * @return list of SCP version OID strings
     */
    public List<String> scpVersions() {
        List<String> result = new ArrayList<>();
        for (TLV tlv : recognitionData) {
            if (tlv.tag() == Tags.GP_CARD_IDENTIFICATION_SCHEME && tlv.isConstructed()) {
                for (TLV child : tlv.children()) {
                    if (child.tag() == Tags.GP_OID) {
                        result.add(oidToString(child.value()));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Converts a BER-encoded OID value to dot-notation string.
     */
    static String oidToString(byte[] oid) {
        if (oid.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        // First byte encodes two components: X*40 + Y
        int first = oid[0] & 0xFF;
        sb.append(first / 40).append('.').append(first % 40);

        // Remaining bytes: base-128 encoded components
        long value = 0;
        for (int i = 1; i < oid.length; i++) {
            value = (value << 7) | (oid[i] & 0x7F);
            if ((oid[i] & 0x80) == 0) {
                sb.append('.').append(value);
                value = 0;
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        List<String> oids = oidStrings();
        return "CardData[oids=" + oids + ", rawLength=" + rawData.length + "]";
    }
}
