package name.velikodniy.jcexpress.pace;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chip side of Basic Access Control, written directly from ICAO Doc 9303-11 (8th ed.) 4.3.1 step 3: check M_IFD,
 * decrypt E_IFD, check RND.IC, answer {@code E_IC || M_IC} with {@code R = RND.IC || RND.IFD || K.IC}, and derive the
 * session keys from {@code K.IFD xor K.IC} and the SSC of 9.8.6.3.
 *
 * <p>The 3DES primitives ({@link SMAlgorithm#DES3}) and the KDF ({@link PaceMrz#kdf}) are reused; both are checked
 * against ICAO App. D in {@link IcaoAppendixDBacTest}. RND.IC and K.IC are random. Like a real chip it answers a wrong
 * checksum or a wrong RND.IC with 6300; {@link Fault}s make it answer like a broken chip.</p>
 */
final class IcaoBacChipSimulator implements SmartCardSession {

    /** Deliberate deviations used to test the inspection system's checks. */
    enum Fault { NONE, SHORT_CHALLENGE, SHORT_RESPONSE, WRONG_CHECKSUM, WRONG_RND_IFD, WRONG_RND_IC }

    /** One received command with the Ne the terminal asked for. */
    record Command(int cla, int ins, byte[] data, int le) {
    }

    private static final SMAlgorithm DES3 = SMAlgorithm.DES3;
    private static final byte[] ZERO_IV = new byte[8];

    private final byte[] kEnc;
    private final byte[] kMac;
    private final byte[] rndIc = new byte[8];
    private final byte[] kIc = new byte[16];
    private Fault fault = Fault.NONE;
    final List<Command> commands = new ArrayList<>();

    byte[] ksEnc;
    byte[] ksMac;
    byte[] ssc;
    boolean authenticated;

    private IcaoBacChipSimulator(byte[] kEnc, byte[] kMac) {
        this.kEnc = kEnc;
        this.kMac = kMac;
        SecureRandom random = new SecureRandom();
        random.nextBytes(rndIc);
        random.nextBytes(kIc);
    }

    /**
     * A chip whose Document Basic Access Keys are derived from the MRZ_information (4.3.2, 9.7.2).
     *
     * @param mrzInformation the MRZ_information printed in the chip's MRZ, e.g. {@code "L898902C<369080619406236"}
     * @return the simulator
     */
    static IcaoBacChipSimulator forMrzInformation(String mrzInformation) {
        byte[] hash = CryptoUtil.sha1(mrzInformation.getBytes(StandardCharsets.US_ASCII));
        byte[] kSeed = Arrays.copyOf(hash, 16);
        return new IcaoBacChipSimulator(PaceMrz.kdf(kSeed, 1, 16), PaceMrz.kdf(kSeed, 2, 16));
    }

    IcaoBacChipSimulator withFault(Fault newFault) {
        this.fault = newFault;
        return this;
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        commands.add(new Command(cla, ins, data == null ? new byte[0] : data.clone(), le));
        if (ins == 0x84 && p1 == 0 && p2 == 0) {
            return new APDUResponse(fault == Fault.SHORT_CHALLENGE ? Arrays.copyOf(rndIc, 7) : rndIc.clone(), 0x9000);
        }
        if (ins == 0x82 && p1 == 0 && p2 == 0) {
            return externalAuthenticate(data == null ? new byte[0] : data);
        }
        return new APDUResponse(new byte[0], 0x6D00);
    }

    private APDUResponse externalAuthenticate(byte[] data) {
        if (data.length != 40) {
            return new APDUResponse(new byte[0], 0x6700);
        }
        byte[] eIfd = Arrays.copyOf(data, 32);
        if (!Arrays.equals(checksum(eIfd), Arrays.copyOfRange(data, 32, 40))) {
            return new APDUResponse(new byte[0], 0x6300); // step 3a
        }
        byte[] s = DES3.decrypt(kEnc, eIfd, ZERO_IV);
        byte[] rndIfd = Arrays.copyOf(s, 8);
        byte[] kIfd = Arrays.copyOfRange(s, 16, 32);
        if (!Arrays.equals(Arrays.copyOfRange(s, 8, 16), rndIc)) {
            return new APDUResponse(new byte[0], 0x6300); // step 3c
        }
        deriveSessionKeys(kIfd, rndIfd);
        authenticated = true;
        return new APDUResponse(chipResponse(rndIfd), 0x9000);
    }

    /** Steps 3e-3h: E_IC || M_IC with R = RND.IC || RND.IFD || K.IC. */
    private byte[] chipResponse(byte[] rndIfd) {
        byte[] r = concat(concat(rndIc, rndIfd), kIc);
        if (fault == Fault.WRONG_RND_IFD) {
            r[8] ^= 0x01;
        } else if (fault == Fault.WRONG_RND_IC) {
            r[0] ^= 0x01;
        }
        byte[] eIc = DES3.encrypt(kEnc, r, ZERO_IV);
        byte[] mIc = checksum(eIc);
        if (fault == Fault.WRONG_CHECKSUM) {
            mIc[7] ^= 0x01;
        }
        byte[] response = concat(eIc, mIc);
        return fault == Fault.SHORT_RESPONSE ? Arrays.copyOf(response, 32) : response;
    }

    /** Step 5 and 9.8.6.3. */
    private void deriveSessionKeys(byte[] kIfd, byte[] rndIfd) {
        byte[] kSeed = new byte[16];
        for (int i = 0; i < kSeed.length; i++) {
            kSeed[i] = (byte) (kIfd[i] ^ kIc[i]);
        }
        ksEnc = PaceMrz.kdf(kSeed, 1, 16);
        ksMac = PaceMrz.kdf(kSeed, 2, 16);
        ssc = concat(Arrays.copyOfRange(rndIc, 4, 8), Arrays.copyOfRange(rndIfd, 4, 8));
    }

    /** 4.3.3.2: ISO/IEC 9797-1 MAC algorithm 3, padding method 2. */
    private byte[] checksum(byte[] cryptogram) {
        return DES3.mac(kMac, DES3.pad(cryptogram));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // ---- remaining SmartCardSession methods (not used by BAC)

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
