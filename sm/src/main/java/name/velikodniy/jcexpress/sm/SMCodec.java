package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateless codec for ISO 7816-4 Secure Messaging command wrapping and response unwrapping.
 *
 * <p>Implements the SM data object format used by ePassports (ICAO 9303) and
 * similar applications:</p>
 * <ul>
 *   <li><b>DO87</b> (tag 0x87) — encrypted command data, prefixed with 0x01 padding indicator</li>
 *   <li><b>DO97</b> (tag 0x97) — Le byte from the original command</li>
 *   <li><b>DO8E</b> (tag 0x8E) — 8-byte MAC computed over SSC + header + DOs</li>
 *   <li><b>DO99</b> (tag 0x99) — status word from the card response</li>
 * </ul>
 *
 * <p>The CLA byte is modified to indicate SM: {@code CLA' = (CLA & 0xF0) | 0x0C}.</p>
 *
 * @see SMContext
 * @see SMSession
 */
public final class SMCodec {

    private SMCodec() {
    }

    /**
     * Wraps a plaintext APDU command with ISO 7816-4 Secure Messaging.
     *
     * <p>The wrapping process:</p>
     * <ol>
     *   <li>Parse the APDU into CLA, INS, P1, P2, data, Le</li>
     *   <li>Build DO87 if data is present (encrypt padded data)</li>
     *   <li>Build DO97 if Le is present</li>
     *   <li>Increment SSC</li>
     *   <li>Compute MAC over: SSC || padded(CLA' INS P1 P2) || DO87 || DO97</li>
     *   <li>Build DO8E with the MAC</li>
     *   <li>Assemble: CLA' INS P1 P2 Lc' DO87 DO97 DO8E 00</li>
     * </ol>
     *
     * @param ctx  the SM context (SSC is incremented)
     * @param apdu the plaintext APDU bytes
     * @return the wrapped APDU bytes
     * @throws SMException if wrapping fails
     */
    public static byte[] wrapCommand(SMContext ctx, byte[] apdu) {
        if (apdu == null || apdu.length < 4) {
            throw new SMException("APDU must be at least 4 bytes (CLA INS P1 P2)");
        }

        SMAlgorithm alg = ctx.algorithm();

        // Parse APDU: CLA INS P1 P2 [Lc Data] [Le]
        int cla = apdu[0] & 0xFF;
        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;

        byte[] data = null;
        int le = -1;

        if (apdu.length == 4) {
            // Case 1: CLA INS P1 P2
        } else if (apdu.length == 5) {
            // Case 2: CLA INS P1 P2 Le
            le = apdu[4] & 0xFF;
            if (le == 0) le = 256;
        } else {
            int lc = apdu[4] & 0xFF;
            if (lc > 0 && apdu.length >= 5 + lc) {
                data = Arrays.copyOfRange(apdu, 5, 5 + lc);
                if (apdu.length == 5 + lc + 1) {
                    // Case 4: CLA INS P1 P2 Lc Data Le
                    le = apdu[5 + lc] & 0xFF;
                    if (le == 0) le = 256;
                }
            }
        }

        // Build SM CLA: set SM bits (0x0C in the low nibble)
        int claSm = (cla & 0xF0) | 0x0C;

        // Build DO87 (encrypted data)
        byte[] do87 = null;
        if (data != null && data.length > 0) {
            do87 = buildDO87(alg, ctx.encKey(), data);
        }

        // Build DO97 (Le)
        byte[] do97 = null;
        if (le >= 0) {
            do97 = buildDO97(le);
        }

        // Increment SSC
        ctx.incrementSsc();

        // Build MAC input: SSC || padded(CLA' INS P1 P2) || [DO87] || [DO97] || padding
        ByteArrayOutputStream macInput = new ByteArrayOutputStream();
        macInput.write(ctx.ssc(), 0, ctx.ssc().length);

        // Padded header
        byte[] header = {(byte) claSm, (byte) ins, (byte) p1, (byte) p2};
        byte[] paddedHeader = alg.pad(header);
        macInput.write(paddedHeader, 0, paddedHeader.length);

        if (do87 != null) {
            macInput.write(do87, 0, do87.length);
        }
        if (do97 != null) {
            macInput.write(do97, 0, do97.length);
        }

        // Pad the entire MAC input to block boundary
        byte[] macData = alg.pad(macInput.toByteArray());
        byte[] mac = alg.mac(ctx.macKey(), macData);

        // Build DO8E
        byte[] do8e = buildDO8E(mac);

        // Assemble wrapped APDU: CLA' INS P1 P2 Lc' body 00
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (do87 != null) {
            body.write(do87, 0, do87.length);
        }
        if (do97 != null) {
            body.write(do97, 0, do97.length);
        }
        body.write(do8e, 0, do8e.length);

        byte[] bodyBytes = body.toByteArray();

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(claSm);
        result.write(ins);
        result.write(p1);
        result.write(p2);
        result.write(bodyBytes.length);
        result.write(bodyBytes, 0, bodyBytes.length);
        result.write(0x00); // Le=0x00

        return result.toByteArray();
    }

    /**
     * Unwraps a Secure Messaging response from the card.
     *
     * <p>The unwrapping process:</p>
     * <ol>
     *   <li>Extract SM data objects: DO87, DO99, DO8E</li>
     *   <li>Increment SSC</li>
     *   <li>Verify MAC over: SSC || padded(DO87 || DO99)</li>
     *   <li>Decrypt DO87 payload if present</li>
     *   <li>Return APDUResponse with decrypted data and SW from DO99</li>
     * </ol>
     *
     * @param ctx           the SM context (SSC is incremented)
     * @param responseBytes the raw response bytes (data + SW1 SW2)
     * @return the unwrapped response
     * @throws SMException if MAC verification fails or response is malformed
     */
    public static APDUResponse unwrapResponse(SMContext ctx, byte[] responseBytes) {
        if (responseBytes == null || responseBytes.length < 2) {
            throw new SMException("Response must be at least 2 bytes");
        }

        // Split: data || SW1 || SW2
        int len = responseBytes.length;
        int outerSw = ((responseBytes[len - 2] & 0xFF) << 8) | (responseBytes[len - 1] & 0xFF);
        byte[] dataPart = Arrays.copyOfRange(responseBytes, 0, len - 2);

        // Check outer SW — must be 9000 for SM response
        if (outerSw != 0x9000) {
            // Card returned error before SM processing — return as-is
            return new APDUResponse(dataPart, outerSw);
        }

        // Parse SM data objects
        Map<Integer, byte[]> dos = parseSMDataObjects(dataPart);

        byte[] do99raw = dos.get(0x99);
        byte[] do8eRaw = dos.get(0x8E);
        byte[] do87raw = dos.get(0x87);

        if (do99raw == null) {
            throw new SMException("Missing DO99 (status word) in SM response");
        }
        if (do8eRaw == null) {
            throw new SMException("Missing DO8E (MAC) in SM response");
        }

        SMAlgorithm alg = ctx.algorithm();

        // Increment SSC
        ctx.incrementSsc();

        // Build MAC verification input: SSC || pad(DO87_raw || DO99_raw)
        // We need the raw TLV bytes (tag + length + value) for DO87 and DO99
        ByteArrayOutputStream macInput = new ByteArrayOutputStream();
        macInput.write(ctx.ssc(), 0, ctx.ssc().length);

        // Reconstruct raw TLV bytes for MAC input
        if (do87raw != null) {
            byte[] do87tlv = buildTlv(0x87, do87raw);
            macInput.write(do87tlv, 0, do87tlv.length);
        }
        byte[] do99tlv = buildTlv(0x99, do99raw);
        macInput.write(do99tlv, 0, do99tlv.length);

        byte[] macData = alg.pad(macInput.toByteArray());
        byte[] computedMac = alg.mac(ctx.macKey(), macData);

        // Verify MAC
        if (!Arrays.equals(computedMac, do8eRaw)) {
            throw new SMException("SM response MAC verification failed");
        }

        // Extract SW from DO99 (2 bytes: SW1 SW2)
        if (do99raw.length != 2) {
            throw new SMException("DO99 must contain exactly 2 bytes (SW1 SW2), got " + do99raw.length);
        }
        int sw = ((do99raw[0] & 0xFF) << 8) | (do99raw[1] & 0xFF);

        // Decrypt data from DO87 if present
        byte[] plainData = new byte[0];
        if (do87raw != null && do87raw.length > 1) {
            // DO87 value: 0x01 (padding indicator) || encrypted data
            if (do87raw[0] != 0x01) {
                throw new SMException("DO87 padding indicator must be 0x01, got 0x"
                        + String.format("%02X", do87raw[0] & 0xFF));
            }
            byte[] ciphertext = Arrays.copyOfRange(do87raw, 1, do87raw.length);
            byte[] decrypted = alg.decrypt(ctx.encKey(), ciphertext, new byte[alg.blockSize()]);
            plainData = alg.unpad(decrypted);
        }

        return new APDUResponse(plainData, sw);
    }

    // ── DO builders ──

    /**
     * Builds DO87 (encrypted data object).
     *
     * <p>Format: {@code 87 L 01 encrypted(padded(data))}</p>
     *
     * @param alg     the algorithm suite
     * @param encKey  the encryption key
     * @param data    the plaintext data
     * @return the DO87 TLV bytes
     */
    static byte[] buildDO87(SMAlgorithm alg, byte[] encKey, byte[] data) {
        byte[] padded = alg.pad(data);
        byte[] encrypted = alg.encrypt(encKey, padded, new byte[alg.blockSize()]);

        // Value: 0x01 (padding indicator) || encrypted
        byte[] value = new byte[1 + encrypted.length];
        value[0] = 0x01;
        System.arraycopy(encrypted, 0, value, 1, encrypted.length);

        return buildTlv(0x87, value);
    }

    /**
     * Builds DO97 (Le indicator).
     *
     * <p>Format: {@code 97 01 Le}</p>
     *
     * @param le the expected response length (1-256, where 256 is encoded as 0x00)
     * @return the DO97 TLV bytes
     */
    static byte[] buildDO97(int le) {
        byte leByte = (byte) (le == 256 ? 0x00 : le);
        return new byte[]{(byte) 0x97, 0x01, leByte};
    }

    /**
     * Builds DO8E (MAC data object).
     *
     * <p>Format: {@code 8E 08 mac[8]}</p>
     *
     * @param mac the 8-byte MAC value
     * @return the DO8E TLV bytes
     */
    static byte[] buildDO8E(byte[] mac) {
        return buildTlv(0x8E, mac);
    }

    /**
     * Parses SM data objects from response data.
     *
     * <p>Extracts tag-value pairs for SM-specific tags (0x87, 0x97, 0x99, 0x8E).
     * Tags are single-byte. Lengths follow BER short/long form.</p>
     *
     * @param data the SM response data (without SW)
     * @return map of tag → value bytes
     */
    static Map<Integer, byte[]> parseSMDataObjects(byte[] data) {
        Map<Integer, byte[]> result = new LinkedHashMap<>();
        int offset = 0;

        while (offset < data.length) {
            // Read tag (single byte for SM objects)
            int tag = data[offset++] & 0xFF;

            // Read length (BER short or long form)
            if (offset >= data.length) {
                throw new SMException("Truncated SM data object at tag 0x" + String.format("%02X", tag));
            }
            int length;
            int firstLenByte = data[offset++] & 0xFF;
            if (firstLenByte <= 0x7F) {
                length = firstLenByte;
            } else if (firstLenByte == 0x81) {
                if (offset >= data.length) {
                    throw new SMException("Truncated length in SM data object");
                }
                length = data[offset++] & 0xFF;
            } else if (firstLenByte == 0x82) {
                if (offset + 1 >= data.length) {
                    throw new SMException("Truncated length in SM data object");
                }
                length = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                offset += 2;
            } else {
                throw new SMException("Unsupported length encoding: 0x" + String.format("%02X", firstLenByte));
            }

            // Read value
            if (offset + length > data.length) {
                throw new SMException("SM data object value extends beyond data");
            }
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;

            result.put(tag, value);
        }

        return result;
    }

    /**
     * Builds a simple TLV: tag (1 byte) + BER length + value.
     */
    private static byte[] buildTlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (value.length <= 0x7F) {
            out.write(value.length);
        } else if (value.length <= 0xFF) {
            out.write(0x81);
            out.write(value.length);
        } else {
            out.write(0x82);
            out.write((value.length >> 8) & 0xFF);
            out.write(value.length & 0xFF);
        }
        out.write(value, 0, value.length);
        return out.toByteArray();
    }
}
