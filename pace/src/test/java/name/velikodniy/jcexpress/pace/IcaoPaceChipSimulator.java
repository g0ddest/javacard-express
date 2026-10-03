package name.velikodniy.jcexpress.pace;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chip side of PACE with ECDH Generic Mapping, written directly from ICAO Doc 9303-11 (8th ed.): MSE:Set AT
 * (4.4.4.1), the GENERAL AUTHENTICATE chain (4.4.4.2, 4.4.5), the mapping {@code G^ = s*G + H} with
 * {@code H = SK_Map,IC * PK_Map,IFD} (4.4.3.3.1), the key agreement and KDF (9.6, 9.7.1) and the authentication
 * tokens (4.4.3.4).
 *
 * <p>Only the elliptic-curve point primitives of {@link PaceCrypto} and the KDF of {@link PaceMrz} are reused (both
 * are checked against the ICAO App. G.1 values in {@link IcaoAppendixG1PaceTest}); the protocol logic and the TLV
 * handling are independent of {@link PaceSession}. With the chip keys of App. G.1 the simulator reproduces the
 * chip side of the ICAO transcript. Like a real chip it rejects an unknown domain parameter ID with 6A88 and a
 * wrong terminal token with 6300.</p>
 */
final class IcaoPaceChipSimulator implements SmartCardSession {

    /** Deliberate deviations used to test the terminal's checks. */
    enum Fault { NONE, OFF_CURVE_MAPPING_KEY, ECHO_TERMINAL_KEY, WRONG_CHIP_TOKEN }

    /** One received GENERAL AUTHENTICATE or MSE command, with the Ne the terminal asked for. */
    record Command(int cla, int ins, byte[] data, int le) {
    }

    private final ECParameterSpec params;
    private final int fieldSize;
    private final PasswordRef passwordRef;
    private final byte[] kPi;
    private final byte[] oid;
    private final int domainParameterId;
    private final BigInteger nonce;
    private final BigInteger skMap;
    private final BigInteger skDh;
    private Fault fault = Fault.NONE;
    final List<Command> commands = new ArrayList<>();

    private ECPoint mappedGenerator;
    private byte[] pkDhIc;
    private byte[] pkDhIfd;
    byte[] ksEnc;
    byte[] ksMac;
    boolean authenticated;

    IcaoPaceChipSimulator(ECParameterSpec params, PasswordRef passwordRef, byte[] kPi, byte[] oid,
                          int domainParameterId, BigInteger nonce, BigInteger skMap, BigInteger skDh) {
        this.params = params;
        this.fieldSize = PaceCrypto.fieldSize(params.getCurve());
        this.passwordRef = passwordRef;
        this.kPi = kPi.clone();
        this.oid = oid.clone();
        this.domainParameterId = domainParameterId;
        this.nonce = nonce;
        this.skMap = skMap;
        this.skDh = skDh;
    }

    /**
     * A chip with random nonce and ephemeral keys.
     *
     * @param parameterId the domain parameters listed in the chip's PACEInfo
     * @param algorithm   the PACE algorithm the chip supports
     * @param passwordRef the password the chip expects in MSE:Set AT
     * @param kPi         the chip's password key K_pi
     * @return the simulator
     */
    static IcaoPaceChipSimulator random(PaceParameterId parameterId, PaceAlgorithm algorithm,
                                        PasswordRef passwordRef, byte[] kPi) {
        SecureRandom random = new SecureRandom();
        ECParameterSpec spec = parameterId.ecParameterSpec();
        byte[] s = new byte[16];
        random.nextBytes(s);
        return new IcaoPaceChipSimulator(spec, passwordRef, kPi, algorithm.oidBytes(), parameterId.id(),
                new BigInteger(1, s), PaceCrypto.randomScalar(spec.getOrder(), random),
                PaceCrypto.randomScalar(spec.getOrder(), random));
    }

    IcaoPaceChipSimulator withFault(Fault newFault) {
        this.fault = newFault;
        return this;
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        commands.add(new Command(cla, ins, data == null ? new byte[0] : data.clone(), le));
        if (ins == 0x22 && p1 == 0xC1 && p2 == 0xA4) {
            return mseSetAt(parse(data));
        }
        if (ins == 0x86 && p1 == 0 && p2 == 0) {
            return generalAuthenticate(parse(parse(data).get(0x7C)));
        }
        return sw(0x6D00);
    }

    private APDUResponse mseSetAt(Map<Integer, byte[]> objects) {
        if (!Arrays.equals(objects.get(0x80), oid)) {
            return sw(0x6A80); // 4.4.4.1: algorithm not supported
        }
        byte[] password = objects.get(0x83);
        if (password == null || password.length != 1 || password[0] != passwordRef.ref()) {
            return sw(0x6A88);
        }
        byte[] id = objects.get(0x84);
        if (id != null && (id.length != 1 || (id[0] & 0xFF) != domainParameterId)) {
            return sw(0x6A88); // 4.4.4.1: referenced data (domain parameters) not available
        }
        return sw(0x9000);
    }

    private APDUResponse generalAuthenticate(Map<Integer, byte[]> in) {
        if (in.isEmpty()) {
            byte[] s = PaceCrypto.toFixedLength(nonce, 16);
            return ok(tlv(0x7C, tlv(0x80, CryptoUtil.aesCbcEncrypt(kPi, s))));
        }
        if (in.containsKey(0x81)) {
            return mapNonce(in.get(0x81));
        }
        if (in.containsKey(0x83)) {
            return agreeKeys(in.get(0x83));
        }
        if (in.containsKey(0x85)) {
            return mutualAuthentication(in.get(0x85));
        }
        return sw(0x6A80);
    }

    private APDUResponse mapNonce(byte[] pkMapIfdEncoded) {
        ECPoint pkMapIfd = PaceCrypto.decodePoint(pkMapIfdEncoded, params.getCurve());
        ECPoint h = PaceCrypto.scalarMultiply(skMap, pkMapIfd, params.getCurve());
        ECPoint sG = PaceCrypto.scalarMultiply(nonce, params.getGenerator(), params.getCurve());
        mappedGenerator = PaceCrypto.pointAdd(sG, h, params.getCurve());
        ECPoint pkMapIc = PaceCrypto.scalarMultiply(skMap, params.getGenerator(), params.getCurve());
        byte[] encoded = PaceCrypto.encodePoint(pkMapIc, fieldSize);
        if (fault == Fault.OFF_CURVE_MAPPING_KEY) {
            encoded[encoded.length - 1] ^= 0x01;
        }
        return ok(tlv(0x7C, tlv(0x82, encoded)));
    }

    private APDUResponse agreeKeys(byte[] pkDhIfdEncoded) {
        pkDhIfd = pkDhIfdEncoded.clone();
        ECPoint terminalKey = PaceCrypto.decodePoint(pkDhIfd, params.getCurve());
        ECPoint chipKey = PaceCrypto.scalarMultiply(skDh, mappedGenerator, params.getCurve());
        pkDhIc = fault == Fault.ECHO_TERMINAL_KEY ? pkDhIfd.clone() : PaceCrypto.encodePoint(chipKey, fieldSize);
        ECPoint shared = PaceCrypto.scalarMultiply(skDh, terminalKey, params.getCurve());
        byte[] k = PaceCrypto.toFixedLength(shared.getAffineX(), fieldSize);
        ksEnc = PaceMrz.kdf(k, 1, kPi.length);
        ksMac = PaceMrz.kdf(k, 2, kPi.length);
        return ok(tlv(0x7C, tlv(0x84, pkDhIc)));
    }

    private APDUResponse mutualAuthentication(byte[] terminalToken) {
        if (!Arrays.equals(token(pkDhIc), terminalToken)) {
            return sw(0x6300); // 4.4.4.2: authentication failed
        }
        authenticated = true;
        byte[] chipToken = token(pkDhIfd);
        if (fault == Fault.WRONG_CHIP_TOKEN) {
            chipToken[0] ^= 0x01;
        }
        return ok(tlv(0x7C, tlv(0x86, chipToken)));
    }

    /** 4.4.3.4: CMAC-8(KSMAC, 7F49 { 06 OID, 86 PK }). */
    private byte[] token(byte[] publicKey) {
        byte[] inner = concat(tlv(0x06, oid), tlv(0x86, publicKey));
        byte[] input = concat(new byte[]{0x7F, 0x49}, lengthAndValue(inner));
        return Arrays.copyOf(CryptoUtil.aesCmac(ksMac, input), 8);
    }

    // ---- minimal BER-TLV handling, independent of the project's TLV classes

    static Map<Integer, byte[]> parse(byte[] data) {
        Map<Integer, byte[]> objects = new LinkedHashMap<>();
        int i = 0;
        while (data != null && i < data.length) {
            int tag = data[i++] & 0xFF;
            if ((tag & 0x1F) == 0x1F) {
                tag = (tag << 8) | (data[i++] & 0xFF);
            }
            int length = data[i++] & 0xFF;
            if (length == 0x81) {
                length = data[i++] & 0xFF;
            } else if (length == 0x82) {
                length = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
                i += 2;
            }
            objects.put(tag, Arrays.copyOfRange(data, i, i + length));
            i += length;
        }
        return objects;
    }

    static byte[] tlv(int tag, byte[] value) {
        return concat(new byte[]{(byte) tag}, lengthAndValue(value));
    }

    private static byte[] lengthAndValue(byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (value.length >= 0x80) {
            out.write(0x81);
        }
        out.write(value.length);
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static APDUResponse ok(byte[] data) {
        return new APDUResponse(data, 0x9000);
    }

    private static APDUResponse sw(int sw) {
        return new APDUResponse(new byte[0], sw);
    }

    // ---- remaining SmartCardSession methods (not used by PACE)

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0, null, -1);
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        return new byte[]{0x6D, 0x00};
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
    }

    @Override
    public void select(AID aid) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void close() {
    }
}
