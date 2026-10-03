package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.crypto.CryptoException;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateless codec for ISO 7816-4 Secure Messaging command wrapping and response unwrapping.
 *
 * <p>Implements the ICAO Doc 9303-11 (9.8) profile of the ISO/IEC 7816-4 secure messaging data objects, used by
 * ePassports and similar applications:</p>
 * <ul>
 *   <li><b>DO'87'</b> — encrypted data of an even-INS command or its response, prefixed with the padding-content
 *       indicator {@code 01}</li>
 *   <li><b>DO'85'</b> — encrypted data of an odd-INS command or its response, without indicator</li>
 *   <li><b>DO'97'</b> — Ne of the original command (one or two bytes)</li>
 *   <li><b>DO'8E'</b> — 8-byte MAC over the SSC, the padded header (commands) and the preceding data objects</li>
 *   <li><b>DO'99'</b> — status word of the response, authenticated by the MAC</li>
 * </ul>
 *
 * <p>The CLA byte is modified to indicate SM while keeping the logical channel and chaining bits
 * (ISO/IEC 7816-4 5.4.1): {@code 0x00 -> 0x0C}, {@code 0x01 -> 0x0D}, {@code 0x10 -> 0x1C},
 * {@code 0x41 -> 0x61}; the reserved classes '20'-'3F' and 'FF' are rejected.</p>
 *
 * @see SMContext
 * @see SMSession
 */
public final class SMCodec {

    private static final int TAG_CRYPTOGRAM_ODD_INS = 0x85;
    private static final int MAX_SHORT_LC = 255;
    private static final int MAX_SHORT_NE = 256;
    private static final int MAX_EXTENDED_LC = 65535;
    private static final int MAX_EXTENDED_NE = 65536;

    private SMCodec() {
    }

    /**
     * Wraps a plaintext APDU command with ISO 7816-4 Secure Messaging.
     *
     * <p>The protected command follows ICAO Doc 9303-11, 9.8.4 and Figure 5:</p>
     * <ol>
     *   <li>The command is parsed as an ISO/IEC 7816-4 (5.1) case 1-4 APDU in short or extended form;
     *       a malformed command raises an {@link SMException}.</li>
     *   <li>The SSC is increased (9.8.2).</li>
     *   <li>Command data is padded, encrypted (IV per {@link SMAlgorithm#iv(byte[], byte[])}) and carried in
     *       DO'87' with the padding-content indicator {@code 01} for an even INS, or in DO'85' without the
     *       indicator for an odd INS.</li>
     *   <li>Ne, if present, is carried in DO'97': one byte for 1..256 ({@code 256} as {@code 00}),
     *       two bytes for 257..65536 ({@code 65536} as {@code 0000}).</li>
     *   <li>DO'8E' holds the 8-byte MAC over {@code SSC || pad(CLA' INS P1 P2) || DO'85'/DO'87' || DO'97'},
     *       padded.</li>
     *   <li>The protected command is {@code CLA' INS P1 P2 Lc' body Le'}. It uses the extended form
     *       ({@code Lc' = '00' Lc1 Lc2}, {@code Le' = '00 00'}) when the body exceeds 255 bytes or Ne exceeds 256,
     *       and the short form ({@code Le' = '00'}) otherwise.</li>
     * </ol>
     *
     * <p>With short APDUs a protected response can carry at most 223 bytes of plaintext under AES and 231 bytes
     * under 3DES, because DO'87', DO'99' and DO'8E' must fit in 256 bytes. Request more than 256 bytes to get an
     * extended protected command.</p>
     *
     * <p>Wrapping is atomic: if it fails, the SSC is not changed.</p>
     *
     * @param ctx  the SM context (SSC is incremented)
     * @param apdu the plaintext APDU bytes
     * @return the wrapped APDU bytes
     * @throws SMException if the session is terminated or the command cannot be protected
     */
    public static byte[] wrapCommand(SMContext ctx, byte[] apdu) {
        ctx.requireActive();
        CommandApdu plain = CommandApdu.parse(apdu);
        byte[] header = {(byte) protectedCla(plain.cla()), (byte) plain.ins(), (byte) plain.p1(), (byte) plain.p2()};
        byte[] ssc = ctx.nextSsc();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (plain.data().length > 0) {
            body.writeBytes(cryptogramObject(ctx, ssc, plain.ins(), plain.data()));
        }
        if (plain.ne() > 0) {
            body.writeBytes(buildDO97(plain.ne()));
        }
        body.writeBytes(buildDO8E(commandMac(ctx, ssc, header, body.toByteArray())));
        byte[] wrapped = frame(header, body.toByteArray(), plain.ne());
        ctx.incrementSsc(); // 9.8.2: increased before the command is generated (ssc above is the new value)
        return wrapped;
    }

    /**
     * Sets the secure messaging indication in the class byte (ISO/IEC 7816-4 5.4.1), keeping the command chaining
     * and logical channel bits.
     *
     * <ul>
     *   <li>First interindustry class {@code 000x xxxx} (Table 2): b4-b3 = {@code 11}, SM with authenticated
     *       command header, i.e. {@code CLA = 0x0C} on the basic channel as ICAO 9303-11 9.8.4 requires.</li>
     *   <li>Further interindustry class {@code 01xx xxxx} (Table 3, channels 4-19): b6 = 1, the only SM indication
     *       of this class. The ICAO profile implemented here still includes the header in the MAC.</li>
     *   <li>Proprietary classes {@code 1xxx xxxx} follow the same layout as the interindustry classes, as
     *       GlobalPlatform does: b7 = 0 like the first, b7 = 1 like the further interindustry class.</li>
     * </ul>
     *
     * @throws SMException for the reserved classes '20'-'3F' and the invalid class 'FF'
     */
    static int protectedCla(int cla) {
        if (cla == 0xFF || (cla & 0xE0) == 0x20) {
            throw new SMException(String.format("CLA %02X is %s (ISO/IEC 7816-4 5.4.1) and cannot be protected",
                    cla, cla == 0xFF ? "invalid" : "reserved for future use"));
        }
        if ((cla & 0x40) != 0) {
            return cla | 0x20;
        }
        return (cla & 0xF3) | 0x0C;
    }

    /**
     * ICAO 9303-11 9.8.4: DO'87' (padding-content indicator {@code 01} + cryptogram) for an even INS, DO'85'
     * (cryptogram only) for an odd INS, whose data field is BER-TLV encoded.
     */
    private static byte[] cryptogramObject(SMContext ctx, byte[] ssc, int ins, byte[] data) {
        SMAlgorithm alg = ctx.algorithm();
        byte[] iv = alg.iv(ctx.encKeyRef(), ssc);
        if ((ins & 0x01) == 0) {
            return buildDO87(alg, ctx.encKeyRef(), data, iv);
        }
        return buildTlv(TAG_CRYPTOGRAM_ODD_INS, alg.encrypt(ctx.encKeyRef(), alg.pad(data), iv));
    }

    private static byte[] commandMac(SMContext ctx, byte[] ssc, byte[] header, byte[] dataObjects) {
        SMAlgorithm alg = ctx.algorithm();
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.writeBytes(ssc);
        input.writeBytes(alg.pad(header));
        input.writeBytes(dataObjects);
        return alg.mac(ctx.macKeyRef(), alg.pad(input.toByteArray()));
    }

    private static byte[] frame(byte[] header, byte[] body, int ne) {
        if (body.length > MAX_EXTENDED_LC) {
            throw new SMException("Protected command body of " + body.length
                    + " bytes exceeds the extended Lc limit of 65535 bytes (ISO/IEC 7816-4 5.1)");
        }
        boolean extended = body.length > MAX_SHORT_LC || ne > MAX_SHORT_NE;
        ByteArrayOutputStream out = new ByteArrayOutputStream(header.length + body.length + 5);
        out.writeBytes(header);
        if (extended) {
            out.write(0x00);
            out.write(body.length >> 8);
        }
        out.write(body.length);
        out.writeBytes(body);
        out.writeBytes(new byte[extended ? 2 : 1]); // Le' = '00' or '00 00' (ICAO 9303-11 Figure 5)
        return out.toByteArray();
    }

    /**
     * Unwraps a Secure Messaging response from the card.
     *
     * <p>Whether a response is protected is decided by its structure, not by its status word
     * (ICAO Doc 9303-11, 9.8.4, 9.8.5 and Figure 6):</p>
     * <ul>
     *   <li>A response with a data field is a protected response, whatever SW1-SW2 it carries
     *       (e.g. 6282 at end of file, 6A82, 6982). The SSC is incremented, the MAC in DO'8E' is
     *       verified over the SSC and the received bytes of all preceding data objects, the cryptogram
     *       of DO'87' or DO'85' is decrypted, and the authenticated status word of DO'99' is returned.
     *       Any structural error or MAC mismatch raises an {@link SMException}; the SSC stays counted,
     *       so it remains in step with the chip.</li>
     *   <li>A bare status word is an unprotected response: per 9.8.5 the chip reports an SM error
     *       without Secure Messaging and aborts the session (9.8.3). The context is
     *       {@linkplain SMContext#terminate(String) terminated} and the status word is returned so that
     *       tests can assert on it. A bare {@code 9000} is never accepted as a successful protected
     *       response: it raises an {@link SMException}.</li>
     * </ul>
     *
     * @param ctx           the SM context (SSC is incremented for protected responses)
     * @param responseBytes the raw response bytes (data + SW1 SW2)
     * @return the unwrapped response: decrypted data and the status word from DO'99'
     * @throws SMException if the session is terminated, MAC verification fails or the response is malformed
     */
    public static APDUResponse unwrapResponse(SMContext ctx, byte[] responseBytes) {
        ctx.requireActive();
        if (responseBytes == null || responseBytes.length < 2) {
            throw new SMException("Response must be at least 2 bytes");
        }
        if (responseBytes.length == 2) {
            return unprotectedStatus(ctx, ((responseBytes[0] & 0xFF) << 8) | (responseBytes[1] & 0xFF));
        }
        ctx.incrementSsc(); // 9.8.2: the SSC is increased before the response is generated
        ProtectedResponse response = ProtectedResponse.parse(responseBytes);
        verifyMac(ctx, response);
        byte[] data = response.cryptogram() == null ? new byte[0] : decrypt(ctx, response.cryptogram());
        return new APDUResponse(data, response.statusWord());
    }

    private static APDUResponse unprotectedStatus(SMContext ctx, int sw) {
        ctx.terminate(String.format("the chip answered SW=%04X without Secure Messaging, which ends the"
                + " SM session (ICAO 9303-11 9.8.3, 9.8.5)", sw));
        if (sw == 0x9000) {
            throw new SMException("Unprotected 9000 response: a protected response must carry DO'99' and DO'8E'"
                    + " (ICAO 9303-11 9.8.4)");
        }
        return new APDUResponse(new byte[0], sw);
    }

    private static void verifyMac(SMContext ctx, ProtectedResponse response) {
        SMAlgorithm alg = ctx.algorithm();
        byte[] ssc = ctx.ssc();
        byte[] input = new byte[ssc.length + response.macInput().length];
        System.arraycopy(ssc, 0, input, 0, ssc.length);
        System.arraycopy(response.macInput(), 0, input, ssc.length, response.macInput().length);
        byte[] expected = alg.mac(ctx.macKeyRef(), alg.pad(input));
        if (!MessageDigest.isEqual(expected, response.mac())) {
            throw new SMException("SM response MAC verification failed");
        }
    }

    private static byte[] decrypt(SMContext ctx, byte[] cryptogram) {
        SMAlgorithm alg = ctx.algorithm();
        if (cryptogram.length == 0 || cryptogram.length % alg.blockSize() != 0) {
            throw new SMException("SM response cryptogram length " + cryptogram.length
                    + " is not a positive multiple of the block size " + alg.blockSize());
        }
        try {
            byte[] padded = alg.decrypt(ctx.encKeyRef(), cryptogram, alg.iv(ctx.encKeyRef(), ctx.ssc()));
            return alg.unpad(padded);
        } catch (CryptoException e) {
            throw new SMException("SM response data could not be decrypted: " + e.getMessage(), e);
        }
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
     * @param iv      the CBC IV from {@link SMAlgorithm#iv(byte[], byte[])}
     * @return the DO87 TLV bytes
     */
    static byte[] buildDO87(SMAlgorithm alg, byte[] encKey, byte[] data, byte[] iv) {
        byte[] padded = alg.pad(data);
        byte[] encrypted = alg.encrypt(encKey, padded, iv);

        // Value: 0x01 (padding indicator) || encrypted
        byte[] value = new byte[1 + encrypted.length];
        value[0] = 0x01;
        System.arraycopy(encrypted, 0, value, 1, encrypted.length);

        return buildTlv(0x87, value);
    }

    /**
     * Builds DO97 (Le indicator).
     *
     * <p>Format: {@code 97 01 Le} for Ne 1..256 (256 encoded as {@code 00}) and {@code 97 02 Le1 Le2} for Ne
     * 257..65536 (65536 encoded as {@code 00 00}), the short and extended Le encodings of ISO/IEC 7816-4 5.1.</p>
     *
     * @param le the expected response length Ne (1-65536)
     * @return the DO97 TLV bytes
     * @throws SMException if Ne is out of range
     */
    static byte[] buildDO97(int le) {
        if (le < 1 || le > MAX_EXTENDED_NE) {
            throw new SMException("Ne must be 1..65536, got " + le);
        }
        if (le <= MAX_SHORT_NE) {
            return new byte[]{(byte) 0x97, 0x01, (byte) le};
        }
        return new byte[]{(byte) 0x97, 0x02, (byte) (le >> 8), (byte) le};
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
     * <p>Extracts tag-value pairs. Tags are single-byte. Lengths follow BER short/long form.
     * A later object with the same tag replaces an earlier one; use
     * {@link SmDataObject#parseAll(byte[], int)} to keep every object and its received encoding.</p>
     *
     * @param data the SM response data (without SW)
     * @return map of tag → value bytes
     */
    static Map<Integer, byte[]> parseSMDataObjects(byte[] data) {
        Map<Integer, byte[]> result = new LinkedHashMap<>();
        for (SmDataObject object : SmDataObject.parseAll(data, data.length)) {
            result.put(object.tag(), object.value());
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
        } else if (value.length <= 0xFFFF) {
            out.write(0x82);
            out.write((value.length >> 8) & 0xFF);
            out.write(value.length & 0xFF);
        } else {
            throw new SMException(String.format("SM data object %02X value of %d bytes exceeds 65535 bytes",
                    tag, value.length));
        }
        out.write(value, 0, value.length);
        return out.toByteArray();
    }
}
