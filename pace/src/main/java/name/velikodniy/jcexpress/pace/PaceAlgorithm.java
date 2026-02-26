package name.velikodniy.jcexpress.pace;

import java.io.ByteArrayOutputStream;

/**
 * PACE algorithm identifiers (BSI TR-03110, Table A.3).
 *
 * <p>Only ECDH with Generic Mapping and AES-CBC-CMAC is supported — this is
 * the standard for all modern ePassports and eID cards. Legacy DES3 and
 * Integrated Mapping variants are not implemented.</p>
 *
 * <p>Each constant provides the OID (as string and DER-encoded bytes)
 * and the session key length in bytes.</p>
 */
public enum PaceAlgorithm {

    /** ECDH-GM with AES-CBC-CMAC-128 (OID 0.4.0.127.0.7.2.2.4.2.2). */
    ECDH_GM_AES_CBC_CMAC_128("0.4.0.127.0.7.2.2.4.2.2", 16),

    /** ECDH-GM with AES-CBC-CMAC-192 (OID 0.4.0.127.0.7.2.2.4.2.3). */
    ECDH_GM_AES_CBC_CMAC_192("0.4.0.127.0.7.2.2.4.2.3", 24),

    /** ECDH-GM with AES-CBC-CMAC-256 (OID 0.4.0.127.0.7.2.2.4.2.4). */
    ECDH_GM_AES_CBC_CMAC_256("0.4.0.127.0.7.2.2.4.2.4", 32);

    private final String oid;
    private final int keyLength;
    private final byte[] oidBytes;

    PaceAlgorithm(String oid, int keyLength) {
        this.oid = oid;
        this.keyLength = keyLength;
        this.oidBytes = encodeOid(oid);
    }

    /**
     * Returns the OID as a dotted string.
     *
     * @return the OID string
     */
    public String oid() {
        return oid;
    }

    /**
     * Returns the session key length in bytes (16, 24, or 32).
     *
     * @return key length in bytes
     */
    public int keyLength() {
        return keyLength;
    }

    /**
     * Returns the DER-encoded OID bytes (without the tag and length).
     *
     * <p>Used in MSE:Set AT command (tag 0x80) and authentication token computation.</p>
     *
     * @return a copy of the OID bytes
     */
    public byte[] oidBytes() {
        return oidBytes.clone();
    }

    /**
     * Encodes a dotted OID string to DER content bytes (no tag/length wrapper).
     */
    private static byte[] encodeOid(String oid) {
        String[] parts = oid.split("\\.");
        int[] components = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            components[i] = Integer.parseInt(parts[i]);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // First two components: 40 * c0 + c1
        out.write(40 * components[0] + components[1]);

        // Remaining components: base-128 encoding
        for (int i = 2; i < components.length; i++) {
            encodeBase128(out, components[i]);
        }
        return out.toByteArray();
    }

    private static void encodeBase128(ByteArrayOutputStream out, int value) {
        if (value < 128) {
            out.write(value);
            return;
        }
        // Count bytes needed
        int temp = value;
        int byteCount = 0;
        while (temp > 0) {
            temp >>= 7;
            byteCount++;
        }
        // Write high bytes with continuation bit, last byte without
        for (int i = byteCount - 1; i >= 0; i--) {
            int b = (value >> (7 * i)) & 0x7F;
            if (i > 0) {
                b |= 0x80;
            }
            out.write(b);
        }
    }
}
