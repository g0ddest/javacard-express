package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.tlv.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed response from GET DATA (P1P2=0066) — Card Data with the Card Recognition Data
 * (GPCS v2.3.1 Appendix H.2, Table H-1).
 *
 * <h2>Response structure:</h2>
 * <pre>
 * 66 len                           — Card Data template
 *   73 len                         — Card Recognition Data
 *     06 len {globalPlatform 1}    — GlobalPlatform as Tag Allocation Authority
 *     60 len 06 {globalPlatform 2 v}      — Card Management Type and Version
 *     63 len 06 {globalPlatform 3}        — Card Identification Scheme
 *     64 len 06 {globalPlatform 4 scp i}  — Secure Channel Protocol of the ISD and its "i" (repeatable)
 *     65 .. 68                     — optional card configuration, chip details, certificates
 * </pre>
 *
 * <p>The "i" parameter of SCP02 is not part of the INITIALIZE UPDATE response; read it here and pass it to
 * {@link GPSession#scp02Option(int)} if the card does not use the default '15'.</p>
 *
 * @param rawData         the full response data
 * @param recognitionData the parsed Card Recognition Data (tag 0x73), or empty TLVList
 */
public record CardData(
        byte[] rawData,
        TLVList recognitionData
) {

    /** Card Management Type and Version template (Table H-1). */
    private static final int TAG_CARD_MANAGEMENT_VERSION = 0x60;
    /** Secure Channel Protocol template (Table H-1). */
    private static final int TAG_SECURE_CHANNEL_PROTOCOL = 0x64;
    /** {globalPlatform 4}: Secure Channel Protocol OIDs. */
    private static final String SCP_OID_PREFIX = "1.2.840.114283.4.";

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
     * Returns the Card Management Type and Version OID {@code {globalPlatform 2 v}} of tag '60', if present
     * (Table H-1), e.g. {@code 1.2.840.114283.2.2.1.1} for GlobalPlatform 2.1.1.
     *
     * @return the OID string, or empty if the card does not provide tag '60'
     */
    public Optional<String> gpVersion() {
        return templateOids(TAG_CARD_MANAGEMENT_VERSION).stream().findFirst();
    }

    /**
     * Returns the Secure Channel Protocol OIDs {@code {globalPlatform 4 scp i}} of the tag '64' templates
     * (Table H-1), e.g. {@code 1.2.840.114283.4.2.21} for SCP02 with i='15'.
     *
     * @return the OID strings in card order (empty if the card provides none)
     */
    public List<String> scpVersions() {
        return templateOids(TAG_SECURE_CHANNEL_PROTOCOL);
    }

    /**
     * Returns the Secure Channel Protocols of the Issuer Security Domain with their implementation option
     * "i", decoded from the tag '64' OIDs {@code {globalPlatform 4 scp i}} (Table H-1).
     *
     * @return the protocols in card order
     */
    public List<SecureChannelProtocol> secureChannelProtocols() {
        List<SecureChannelProtocol> result = new ArrayList<>();
        for (String oid : scpVersions()) {
            String[] arcs = oid.substring(Math.min(oid.length(), SCP_OID_PREFIX.length())).split("\\.");
            if (oid.startsWith(SCP_OID_PREFIX) && arcs.length == 2) {
                result.add(new SecureChannelProtocol(Integer.parseInt(arcs[0]), Integer.parseInt(arcs[1])));
            }
        }
        return result;
    }

    /**
     * A Secure Channel Protocol announced in the Card Recognition Data.
     *
     * @param protocol the protocol number (2 for SCP02, 3 for SCP03)
     * @param option   the implementation option "i" (GPCS v2.3.1 Table E-1, Amendment D Table 5-1)
     */
    public record SecureChannelProtocol(int protocol, int option) {
        @Override
        public String toString() {
            return String.format("SCP%02d i=%02X", protocol, option);
        }
    }

    private List<String> templateOids(int tag) {
        List<String> result = new ArrayList<>();
        for (TLV tlv : recognitionData) {
            if (tlv.tag() == tag && tlv.isConstructed()) {
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
