package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;

/**
 * Card Production Life Cycle (CPLC) data retrieved via GET DATA P1P2=9F7F.
 *
 * <p>CPLC is a fixed-format 42-byte structure (not TLV-encoded inside) with the production history of the
 * card: IC fabrication, module packaging, card embedding, pre-personalization and personalization. The
 * layout is not defined by GPCS v2.3.1; it is the de-facto standard layout (Visa GlobalPlatform card
 * implementation requirements) returned by real cards, with 4-byte equipment identifiers.</p>
 *
 * <h2>Structure (42 bytes):</h2>
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
 * 30      4       IC Pre-Personalization Equipment Identifier
 * 34      2       IC Personalizer
 * 36      2       IC Personalization Date
 * 38      4       IC Personalization Equipment Identifier
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
 * @param icPrePersonalizationEquipId  IC pre-personalization equipment identifier (raw 4-byte value)
 * @param icPersonalizer               IC personalizer code
 * @param icPersonalizationDate        IC personalization date (raw 2-byte value)
 * @param icPersonalizationEquipId     IC personalization equipment identifier (raw 4-byte value)
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

    /** Length of the CPLC data. */
    private static final int LENGTH = 42;

    /**
     * Parses CPLC data from a GET DATA response: either the 42 bytes themselves or the data object
     * {@code '9F7F' L data} (BER length, which must match the data).
     *
     * @param data the response data bytes
     * @return parsed CPLCData
     * @throws GPException if the data is not 42 bytes long or the '9F7F' wrapper is malformed
     */
    public static CPLCData parse(byte[] data) {
        byte[] cplc = unwrap(data);
        if (cplc.length != LENGTH) {
            throw new GPException("CPLC data must be " + LENGTH + " bytes, got " + cplc.length);
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
                readInt32(cplc, 30),
                readUint16(cplc, 34),
                readUint16(cplc, 36),
                readInt32(cplc, 38)
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

    /** Returns the CPLC bytes: raw 42-byte data, or the value of a well-formed '9F7F' data object. */
    private static byte[] unwrap(byte[] data) {
        if (data.length == LENGTH || data.length < 3 || (data[0] & 0xFF) != 0x9F || (data[1] & 0xFF) != 0x7F) {
            return data;
        }
        int offset = 2;
        int length = data[offset++] & 0xFF;
        if (length == 0x81 && data.length > offset) {
            length = data[offset++] & 0xFF;
        } else if (length > 0x7F) {
            throw new GPException(String.format("Unsupported CPLC data object length byte %02X", length));
        }
        if (offset + length != data.length) {
            throw new GPException("CPLC data object '9F7F' declares " + length + " bytes, but "
                    + (data.length - offset) + " follow");
        }
        return Arrays.copyOfRange(data, offset, data.length);
    }

    private static int readInt32(byte[] data, int offset) {
        return (readUint16(data, offset) << 16) | readUint16(data, offset + 2);
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
