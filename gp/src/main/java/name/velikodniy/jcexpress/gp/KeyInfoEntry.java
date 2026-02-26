package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.tlv.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single key entry from the Key Information Template (GET DATA P1P2=00E0).
 *
 * <p>Each key entry (tag 0xC0) contains a key identifier, key version,
 * and one or more key components describing the cryptographic algorithm
 * and key length.</p>
 *
 * <h2>C0 entry format:</h2>
 * <pre>
 * C0 len keyId(1) keyVersion(1) [keyType(1) keyLength(1)]+
 * </pre>
 *
 * <p>A key set typically has 3 components (ENC, MAC, DEK),
 * each described by a type-length pair.</p>
 *
 * @param keyId      the key identifier (1-127)
 * @param keyVersion the key version number (1-127)
 * @param components the key components (algorithm + length)
 */
public record KeyInfoEntry(
        int keyId,
        int keyVersion,
        List<KeyComponent> components
) {

    /**
     * A single key component within a key entry.
     *
     * @param keyType  the key type: 0x80 = DES3, 0x88 = AES
     * @param keyLength the key length in bytes (e.g., 16, 24, 32)
     */
    public record KeyComponent(int keyType, int keyLength) {

        /** Key type constant for Triple DES. */
        public static final int TYPE_DES3 = 0x80;

        /** Key type constant for AES. */
        public static final int TYPE_AES = 0x88;

        /**
         * Returns a human-readable name for the key type.
         *
         * @return "DES3", "AES", or "UNKNOWN(0xNN)"
         */
        public String keyTypeName() {
            return switch (keyType) {
                case TYPE_DES3 -> "DES3";
                case TYPE_AES -> "AES";
                default -> "UNKNOWN(0x" + Integer.toHexString(keyType) + ")";
            };
        }

        /** Returns true if this is a Triple DES key component.
         * @return true for DES3 keys */
        public boolean isDes3() { return keyType == TYPE_DES3; }

        /** Returns true if this is an AES key component.
         * @return true for AES keys */
        public boolean isAes() { return keyType == TYPE_AES; }

        @Override
        public String toString() {
            return keyTypeName() + "/" + (keyLength * 8) + "bit";
        }
    }

    /**
     * Parses a single C0 key entry value.
     *
     * @param data the C0 tag value bytes (keyId + keyVersion + components)
     * @return parsed KeyInfoEntry
     * @throws GPException if the data is too short or malformed
     */
    public static KeyInfoEntry parse(byte[] data) {
        if (data.length < 4) {
            throw new GPException(
                    "Key info entry too short: " + data.length + " bytes (minimum 4)");
        }

        int keyId = data[0] & 0xFF;
        int keyVersion = data[1] & 0xFF;

        List<KeyComponent> components = new ArrayList<>();
        int offset = 2;
        while (offset + 1 < data.length) {
            int keyType = data[offset] & 0xFF;
            int keyLength = data[offset + 1] & 0xFF;
            components.add(new KeyComponent(keyType, keyLength));
            offset += 2;
        }

        return new KeyInfoEntry(keyId, keyVersion, Collections.unmodifiableList(components));
    }

    /**
     * Parses all key entries from a Key Information Template (tag 0xE0) response.
     *
     * <p>The response contains one or more C0 tags, each describing a key set.</p>
     *
     * @param responseData the GET DATA response bytes (may include 0xE0 wrapper)
     * @return list of key entries
     */
    public static List<KeyInfoEntry> parseAll(byte[] responseData) {
        if (responseData == null || responseData.length == 0) {
            return List.of();
        }

        TLVList tlvList = TLVParser.parse(responseData);
        List<KeyInfoEntry> entries = new ArrayList<>();

        // Try to find E0 wrapper first
        TLVList searchIn = tlvList.find(Tags.GP_KEY_INFO_TEMPLATE)
                .map(TLV::children)
                .orElse(tlvList);

        for (TLV tlv : searchIn) {
            if (tlv.tag() == Tags.GP_KEY_INFO_DATA) {
                entries.add(parse(tlv.value()));
            }
        }

        return Collections.unmodifiableList(entries);
    }

    @Override
    public String toString() {
        return "Key[id=" + keyId + ", ver=" + keyVersion + ", " + components + "]";
    }
}
