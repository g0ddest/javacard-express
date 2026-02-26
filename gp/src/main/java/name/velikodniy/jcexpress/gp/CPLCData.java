package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;

/**
 * Card Production Life Cycle (CPLC) data retrieved via GET DATA P1P2=9F7F.
 *
 * <p>CPLC is a fixed-format 38-42 byte structure (not TLV-encoded) that contains
 * the complete production history of the card: IC fabrication, module packaging,
 * card embedding, pre-personalization, and personalization details.</p>
 *
 * <h3>Structure (GP Amendments B):</h3>
 * <pre>
 * Offset  Length  Field
 * 0       2       IC Fabricator
 * 2       2       IC Type
 * 4       2       Operating System ID
 * 6       2       Operating System Release Date
 * 8       2       Operating System Release Level
 * 10      2       IC Fabrication Date
 * 12      4       IC Serial Number
 * 16      2       IC Batch Identifier
 * 18      2       IC Module Fabricator
 * 20      2       IC Module Packaging Date
 * 22      2       ICC Manufacturer
 * 24      2       IC Embedding Date
 * 26      2       IC Pre-Personalizer
 * 28      2       IC Pre-Personalization Date
 * 30      2       IC Pre-Personalization Equipment Identifier
 * 32      2       IC Personalizer
 * 34      2       IC Personalization Date
 * 36      2       IC Personalization Equipment Identifier
 * </pre>
 *
 * @param icFabricator                 IC fabricator code
 * @param icType                       IC type code
 * @param osId                         operating system identifier
 * @param osReleaseDate                OS release date (raw 2-byte value)
 * @param osReleaseLevel               OS release level
 * @param icFabricationDate            IC fabrication date (raw 2-byte value)
 * @param icSerialNumber               IC serial number (4 bytes)
 * @param icBatchId                    IC batch identifier
 * @param icModuleFabricator           IC module fabricator code
 * @param icModulePackagingDate        IC module packaging date (raw 2-byte value)
 * @param iccManufacturer              ICC manufacturer code
 * @param icEmbeddingDate              IC embedding date (raw 2-byte value)
 * @param icPrePersonalizer            IC pre-personalizer code
 * @param icPrePersonalizationDate     IC pre-personalization date (raw 2-byte value)
 * @param icPrePersonalizationEquipId  IC pre-personalization equipment identifier
 * @param icPersonalizer               IC personalizer code
 * @param icPersonalizationDate        IC personalization date (raw 2-byte value)
 * @param icPersonalizationEquipId     IC personalization equipment identifier
 */
public record CPLCData(
        int icFabricator,
        int icType,
        int osId,
        int osReleaseDate,
        int osReleaseLevel,
        int icFabricationDate,
        byte[] icSerialNumber,
        int icBatchId,
        int icModuleFabricator,
        int icModulePackagingDate,
        int iccManufacturer,
        int icEmbeddingDate,
        int icPrePersonalizer,
        int icPrePersonalizationDate,
        int icPrePersonalizationEquipId,
        int icPersonalizer,
        int icPersonalizationDate,
        int icPersonalizationEquipId
) {

    /** Minimum CPLC data length (38 bytes for all mandatory fields). */
    private static final int MIN_LENGTH = 38;

    /**
     * Parses CPLC data from a GET DATA response.
     *
     * <p>Automatically strips the 9F7F tag wrapper if present.</p>
     *
     * @param data the response data bytes
     * @return parsed CPLCData
     * @throws GPException if the data is too short
     */
    public static CPLCData parse(byte[] data) {
        byte[] cplc = stripTagWrapper(data);

        if (cplc.length < MIN_LENGTH) {
            throw new GPException(
                    "CPLC data too short: " + cplc.length + " bytes (minimum " + MIN_LENGTH + ")");
        }

        return new CPLCData(
                readUint16(cplc, 0),
                readUint16(cplc, 2),
                readUint16(cplc, 4),
                readUint16(cplc, 6),
                readUint16(cplc, 8),
                readUint16(cplc, 10),
                Arrays.copyOfRange(cplc, 12, 16),
                readUint16(cplc, 16),
                readUint16(cplc, 18),
                readUint16(cplc, 20),
                readUint16(cplc, 22),
                readUint16(cplc, 24),
                readUint16(cplc, 26),
                readUint16(cplc, 28),
                readUint16(cplc, 30),
                readUint16(cplc, 32),
                readUint16(cplc, 34),
                readUint16(cplc, 36)
        );
    }

    /**
     * Returns the IC serial number as a hex string.
     *
     * @return hex-encoded serial number (8 hex chars)
     */
    public String serialNumberHex() {
        return Hex.encode(icSerialNumber);
    }

    /**
     * Formats a 2-byte CPLC date value as a 4-character hex string.
     *
     * <p>CPLC date encoding varies by manufacturer. The raw hex value
     * is returned for maximum compatibility.</p>
     *
     * @param dateValue the raw 2-byte date value
     * @return 4-character hex string (e.g., "3210")
     */
    public static String formatDate(int dateValue) {
        return String.format("%04X", dateValue & 0xFFFF);
    }

    /**
     * Strips the 9F7F TLV tag wrapper if present.
     *
     * <p>Some cards return CPLC data wrapped in the 9F7F tag,
     * others return the raw bytes directly.</p>
     */
    private static byte[] stripTagWrapper(byte[] data) {
        if (data.length > 2 && (data[0] & 0xFF) == 0x9F && (data[1] & 0xFF) == 0x7F) {
            // Tag 9F7F is 2 bytes; next byte(s) = length
            int offset = 2;
            int len = data[offset] & 0xFF;
            if (len <= 0x7F) {
                offset++;
            } else if (len == 0x81) {
                offset++;
                len = data[offset] & 0xFF;
                offset++;
            } else {
                // Unlikely for CPLC (42 bytes max)
                return data;
            }
            if (offset + len <= data.length) {
                return Arrays.copyOfRange(data, offset, offset + len);
            }
        }
        return data;
    }

    private static int readUint16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    @Override
    public String toString() {
        return "CPLC[fabricator=" + String.format("%04X", icFabricator)
                + ", type=" + String.format("%04X", icType)
                + ", os=" + String.format("%04X", osId)
                + ", serial=" + serialNumberHex()
                + ", batch=" + String.format("%04X", icBatchId)
                + "]";
    }
}
