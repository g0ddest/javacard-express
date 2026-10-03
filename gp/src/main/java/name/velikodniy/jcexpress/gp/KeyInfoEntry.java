package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import name.velikodniy.jcexpress.tlv.Tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A single key entry from the Key Information Template (GET DATA P1P2=00E0, GPCS v2.3.1 11.3.3.1.1).
 *
 * <p>Each Key Information Data object (tag 'C0') describes one key: its Key Identifier, Key Version Number
 * and the type and length of each key component. Two structures exist:</p>
 * <pre>
 * Basic (Table 11-28):    keyId(1) keyVersion(1) [keyType(1) keyLength(1)]+
 * Extended (Table 11-29): keyId(1) keyVersion(1) ['FF' keyType(1) keyLength(2)]+
 *                         Lusage(1) keyUsage(Lusage) Laccess(1) keyAccess(Laccess)
 * </pre>
 *
 * <p>Key types are coded per Table 11-16 ('80' DES, '88' AES); in the extended structure the key type is
 * the byte that follows 'FF'. A key set (e.g. ENC, MAC, DEK) is reported as one entry per key. Malformed
 * entries are rejected, never parsed partially.</p>
 *
 * @param keyId      the key identifier
 * @param keyVersion the key version number
 * @param components the key components (type and length), at least one
 * @param keyUsage   the Key Usage Qualifier (11.1.9) of an extended entry, empty if absent
 * @param keyAccess  the Key Access (11.1.10) of an extended entry, empty if absent
 */
public record KeyInfoEntry(
        int keyId,
        int keyVersion,
        List<KeyComponent> components,
        byte[] keyUsage,
        byte[] keyAccess
) {

    /** First byte of a two-byte (extended format) key type (Table 11-16). */
    private static final int EXTENDED_KEY_TYPE = 0xFF;

    /**
     * Canonical constructor; copies the component list and the byte arrays.
     *
     * @param keyId      the key identifier
     * @param keyVersion the key version number
     * @param components the key components
     * @param keyUsage   the Key Usage Qualifier, empty if absent
     * @param keyAccess  the Key Access, empty if absent
     */
    public KeyInfoEntry {
        components = List.copyOf(components);
        keyUsage = keyUsage.clone();
        keyAccess = keyAccess.clone();
    }

    /**
     * Creates an entry without Key Usage and Key Access (basic structure, Table 11-28).
     *
     * @param keyId      the key identifier
     * @param keyVersion the key version number
     * @param components the key components
     */
    public KeyInfoEntry(int keyId, int keyVersion, List<KeyComponent> components) {
        this(keyId, keyVersion, components, new byte[0], new byte[0]);
    }

    /**
     * A single key component within a key entry.
     *
     * @param keyType   the key type (Table 11-16): 0x80 = DES3, 0x88 = AES
     * @param keyLength the key component length in bytes (e.g., 16, 24, 32)
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
     * Parses a single 'C0' Key Information Data value, basic (Table 11-28) or extended (Table 11-29).
     *
     * @param data the 'C0' value bytes
     * @return parsed KeyInfoEntry
     * @throws GPException if the data is too short or does not match its structure exactly
     */
    public static KeyInfoEntry parse(byte[] data) {
        if (data == null || data.length < 4) {
            throw new GPException("Key info entry too short: " + (data == null ? 0 : data.length)
                    + " bytes (minimum 4)");
        }
        int keyId = data[0] & 0xFF;
        int keyVersion = data[1] & 0xFF;
        return (data[2] & 0xFF) == EXTENDED_KEY_TYPE
                ? parseExtended(data, keyId, keyVersion)
                : parseBasic(data, keyId, keyVersion);
    }

    /**
     * Parses all key entries from a Key Information Template (tag 0xE0) response.
     *
     * <p>The response contains one or more C0 tags, each describing a key.</p>
     *
     * @param responseData the GET DATA response bytes (may include 0xE0 wrapper)
     * @return list of key entries
     * @throws GPException if an entry is malformed
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
    public boolean equals(Object o) {
        return o instanceof KeyInfoEntry e && keyId == e.keyId && keyVersion == e.keyVersion
                && components.equals(e.components) && Arrays.equals(keyUsage, e.keyUsage)
                && Arrays.equals(keyAccess, e.keyAccess);
    }

    @Override
    public int hashCode() {
        return (keyId * 31 + keyVersion) * 31 + components.hashCode();
    }

    @Override
    public String toString() {
        String extended = keyUsage.length == 0 && keyAccess.length == 0 ? ""
                : ", usage=" + Hex.encode(keyUsage) + ", access=" + Hex.encode(keyAccess);
        return "Key[id=" + keyId + ", ver=" + keyVersion + ", " + components + extended + "]";
    }

    /** Table 11-28: one-byte key types ('00'-'FE') and one-byte lengths, nothing else. */
    private static KeyInfoEntry parseBasic(byte[] data, int keyId, int keyVersion) {
        if (data.length % 2 != 0) {
            throw malformed(data, "Table 11-28", "incomplete key type/length pair");
        }
        List<KeyComponent> components = new ArrayList<>();
        for (int offset = 2; offset < data.length; offset += 2) {
            if ((data[offset] & 0xFF) == EXTENDED_KEY_TYPE) {
                throw malformed(data, "Table 11-28", "extended key type after a basic one");
            }
            components.add(new KeyComponent(data[offset] & 0xFF, data[offset + 1] & 0xFF));
        }
        return new KeyInfoEntry(keyId, keyVersion, components);
    }

    /** Table 11-29: 'FF xx' key types with two-byte lengths, then Key Usage and Key Access with their lengths. */
    private static KeyInfoEntry parseExtended(byte[] data, int keyId, int keyVersion) {
        List<KeyComponent> components = new ArrayList<>();
        int offset = 2;
        while (offset < data.length && (data[offset] & 0xFF) == EXTENDED_KEY_TYPE) {
            if (offset + 4 > data.length) {
                throw malformed(data, "Table 11-29", "truncated key component");
            }
            int length = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            components.add(new KeyComponent(data[offset + 1] & 0xFF, length));
            offset += 4;
        }
        byte[] usage = lengthPrefixed(data, offset, "Key Usage");
        offset += 1 + usage.length;
        byte[] access = lengthPrefixed(data, offset, "Key Access");
        offset += 1 + access.length;
        if (offset != data.length) {
            throw malformed(data, "Table 11-29", (data.length - offset) + " unexpected trailing bytes");
        }
        return new KeyInfoEntry(keyId, keyVersion, components, usage, access);
    }

    private static byte[] lengthPrefixed(byte[] data, int offset, String field) {
        if (offset >= data.length) {
            throw malformed(data, "Table 11-29", "missing length of " + field);
        }
        int length = data[offset] & 0xFF;
        if (offset + 1 + length > data.length) {
            throw malformed(data, "Table 11-29", field + " longer than the entry");
        }
        return Arrays.copyOfRange(data, offset + 1, offset + 1 + length);
    }

    private static GPException malformed(byte[] data, String table, String reason) {
        return new GPException("Malformed Key Information Data " + Hex.encode(data) + " (GPCS v2.3.1 " + table
                + "): " + reason);
    }
}
