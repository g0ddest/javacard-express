package name.velikodniy.jcexpress.livecard.sim;

import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.livecard.guard.GuardCrypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

/**
 * The Issuer Security Domain of a {@link SimulatedCard}: SCP03 S8 with i = '00' (GlobalPlatform Amendment D
 * v1.1.2), GET DATA with the real card's data objects, GET STATUS, INSTALL, LOAD, DELETE and SET STATUS
 * (GlobalPlatform Card Specification v2.3.1 chapter 11). The card side checks the host cryptogram and the C-MAC of
 * EXTERNAL AUTHENTICATE and counts failures. In the session that follows it checks the C-MAC of every command with
 * the MAC chaining value (6.2.3, 6.2.4) and decrypts C-DECRYPTION data with the counter-based ICV (6.2.6); a
 * command without secure messaging in a C-MAC session or a failed check answers '6982' and ends the session. R-MAC
 * is not modelled.
 */
final class SimulatedIsd {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Key diversification data (the real card's, with its serial number zeroed). */
    private static final String DIVERSIFICATION = "00000000000000000000";
    /** GET DATA objects of the real card (probe-1.log); serial numbers and personalization data zeroed. */
    private static final Map<String, String> DATA = Map.of(
            "9F7F", "9F7F2A479005038211635103028048000000000000000000000000000000000000000000000000000000000000",
            "0066", "663F733D06072A864886FC6B01600C060A2A864886FC6B02020101630906072A864886FC6B03640B06092A864886FC6B"
                    + "040300660C060A2B060104012A026E0102",
            "00E0", "E012C00401FF8810C00402FF8810C00403FF8810",
            "0042", "420100",
            "0045", "45080000000000000000",
            "00C1", "C103000000");

    private final byte[] staticKey;
    private final SimulatedContent content;
    private byte[] context;
    private byte[] sessionMac;
    private byte[] sessionEnc;
    private byte[] macChain;
    private int encryptionCounter;
    private int level = -1;
    private int externalAuthentications;
    private int failedAuthentications;
    private String pendingLoadFile;
    private ByteArrayOutputStream loadData;
    private boolean refuseInt;

    SimulatedIsd(byte[] staticKey, SimulatedContent content) {
        this.staticKey = staticKey.clone();
        this.content = content;
    }

    /** Ends the secure channel session (deselection, card reset). */
    void endSession() {
        context = null;
        sessionMac = null;
        sessionEnc = null;
        macChain = null;
        level = -1;
        pendingLoadFile = null;
    }

    /** Refuses load files with the Header flag ACC_INT, as a card without int support does. */
    void refuseIntPackages() {
        refuseInt = true;
    }

    int externalAuthentications() {
        return externalAuthentications;
    }

    int failedAuthentications() {
        return failedAuthentications;
    }

    byte[] process(int cla, int ins, int p1, int p2, byte[] data) {
        if (ins == 0x50 || ins == 0x82) {
            return ins == 0x50 ? initializeUpdate(p1, data) : externalAuthenticate(cla, p1, data);
        }
        boolean secureMessaging = (cla & 0x0C) != 0;
        if (level >= 0 && (secureMessaging || (level & 0x01) != 0)) {
            byte[] plain = secureMessaging ? unwrap(cla, ins, p1, p2, data) : null;
            if (plain == null) {
                endSession();
                return sw(0x6982);
            }
            return ins == 0xCA ? getData(p1, p2) : authenticated(ins, p1, p2, plain);
        }
        if (secureMessaging) {
            return sw(0x6982);
        }
        return ins == 0xCA ? getData(p1, p2) : level < 0 ? sw(0x6982) : authenticated(ins, p1, p2, data);
    }

    /** GET DATA outside any session (logical channels, where no session is modelled). */
    byte[] plainGetData(int p1, int p2) {
        return getData(p1, p2);
    }

    /**
     * Verifies the C-MAC with the MAC chaining value (Amendment D 6.2.4: class byte '84' with the logical channel
     * bits cleared, Lc including the C-MAC, no Le) and decrypts the data at a C-DECRYPTION level (6.2.6: AES-CBC with
     * S-ENC, ICV = AES(S-ENC, encryption counter), ISO/IEC 9797-1 padding method 2). The counter counts every
     * command of the session.
     *
     * @return the plain command data, or null if the C-MAC or the padding is wrong
     */
    private byte[] unwrap(int cla, int ins, int p1, int p2, byte[] data) {
        encryptionCounter++;
        if (data.length < 8) {
            return null;
        }
        byte[] payload = Arrays.copyOf(data, data.length - 8);
        byte[] header = {(byte) ((cla & 0xF0) | 0x04), (byte) ins, (byte) p1, (byte) p2, (byte) data.length};
        byte[] mac = GuardCrypto.aesCmac(sessionMac, concat(macChain, header, payload));
        if (!MessageDigest.isEqual(Arrays.copyOf(mac, 8), Arrays.copyOfRange(data, data.length - 8, data.length))) {
            return null;
        }
        macChain = mac;
        return (level & 0x02) == 0 || payload.length == 0 ? payload : decrypt(payload);
    }

    private byte[] decrypt(byte[] encrypted) {
        try {
            byte[] counter = new byte[16];
            counter[12] = (byte) (encryptionCounter >> 24);
            counter[13] = (byte) (encryptionCounter >> 16);
            counter[14] = (byte) (encryptionCounter >> 8);
            counter[15] = (byte) encryptionCounter;
            Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
            ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionEnc, "AES"));
            Cipher cbc = Cipher.getInstance("AES/CBC/NoPadding");
            cbc.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessionEnc, "AES"), new IvParameterSpec(ecb.doFinal(counter)));
            return unpad(cbc.doFinal(encrypted));
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /** Removes ISO/IEC 9797-1 padding method 2 ('80' then '00's); null if it is not there. */
    private static byte[] unpad(byte[] padded) {
        int end = padded.length - 1;
        while (end >= 0 && padded[end] == 0) {
            end--;
        }
        return end >= 0 && (padded[end] & 0xFF) == 0x80 ? Arrays.copyOf(padded, end) : null;
    }

    private byte[] authenticated(int ins, int p1, int p2, byte[] payload) {
        return switch (ins) {
            case 0xF2 -> getStatus(p1);
            case 0xE6 -> install(p1, payload);
            case 0xE8 -> load(p1, payload);
            case 0xE4 -> confirmed(content.delete(aidTlv(payload), p2 == 0x80));
            case 0xF0 -> setStatus(p1, p2, payload);
            default -> sw(0x6D00);
        };
    }

    private byte[] initializeUpdate(int keyVersion, byte[] hostChallenge) {
        endSession();
        if (keyVersion != 0 && keyVersion != 0xFF) {
            return sw(0x6A88);
        }
        byte[] cardChallenge = new byte[8];
        RANDOM.nextBytes(cardChallenge);
        context = concat(hostChallenge, cardChallenge);
        sessionMac = GuardCrypto.scp03Kdf(staticKey, 0x06, staticKey.length * 8, context);
        sessionEnc = GuardCrypto.scp03Kdf(staticKey, 0x04, staticKey.length * 8, context);
        byte[] cryptogram = GuardCrypto.scp03Kdf(sessionMac, 0x00, 64, context);
        return concat(HEX.parseHex(DIVERSIFICATION + "FF0300"), cardChallenge, cryptogram, sw(0x9000));
    }

    private byte[] externalAuthenticate(int cla, int securityLevel, byte[] data) {
        externalAuthentications++;
        byte[] mac = sessionMac;
        byte[] ctx = context;
        context = null;
        if (mac == null || data.length != 16) {
            failedAuthentications++;
            return sw(0x6985);
        }
        byte[] host = GuardCrypto.scp03Kdf(mac, 0x01, 64, ctx);
        byte[] input = concat(new byte[16], new byte[]{(byte) cla, (byte) 0x82, (byte) securityLevel, 0, 0x10}, host);
        byte[] fullMac = GuardCrypto.aesCmac(mac, input);
        if (!MessageDigest.isEqual(host, Arrays.copyOf(data, 8))
                || !MessageDigest.isEqual(Arrays.copyOf(fullMac, 8), Arrays.copyOfRange(data, 8, 16))) {
            failedAuthentications++;
            return sw(0x6300);
        }
        level = securityLevel;
        macChain = fullMac;
        encryptionCounter = 0;
        return sw(0x9000);
    }

    private static byte[] getData(int p1, int p2) {
        String value = DATA.get(String.format("%02X%02X", p1, p2));
        return value == null ? sw(0x6A88) : concat(HEX.parseHex(value), sw(0x9000));
    }

    private byte[] getStatus(int scope) {
        byte[] status = content.status(scope);
        return status == null ? sw(0x6A88) : concat(status, sw(0x9000));
    }

    private byte[] install(int p1, byte[] payload) {
        Fields in = new Fields(payload);
        byte[] loadFile = in.next();
        if (p1 == 0x02) {
            pendingLoadFile = HEX.formatHex(loadFile);
            loadData = new ByteArrayOutputStream();
            return confirmed(0x9000);
        }
        String module = HEX.formatHex(in.next());
        String instance = HEX.formatHex(in.next());
        in.next();
        byte[] parameters = in.next();
        if (p1 == 0x08) {
            SimulatedContent.Application application = content.application(instance);
            if (application != null) {
                application.state = 0x07;
            }
            return confirmed(application == null ? 0x6A88 : 0x9000);
        }
        if (!content.hasModule(HEX.formatHex(loadFile), module) || content.application(instance) != null) {
            return sw(0x6A80);
        }
        byte[] c9 = parameters.length >= 2 ? Arrays.copyOfRange(parameters, 2, parameters.length) : new byte[0];
        return confirmed(createInstance(instance, HEX.formatHex(loadFile), module, c9, p1 == 0x0C ? 0x07 : 0x03));
    }

    /**
     * Creates an application. Without an applet class for the module, or when the applet's install method fails, no
     * instance exists and the card answers an INSTALL error condition: '6A80' incorrect parameters in the data field
     * (GPCS v2.3.1 11.5.3.2), the field that carries the install parameters the install method got.
     */
    private int createInstance(String instance, String loadFile, String module, byte[] c9, int state) {
        try {
            return content.addApplication(instance, loadFile, module, c9, state) ? 0x9000 : 0x6A80;
        } catch (InstallException e) {
            return 0x6A80;
        }
    }

    private byte[] load(int p1, byte[] block) {
        if (pendingLoadFile == null) {
            return sw(0x6985);
        }
        loadData.writeBytes(block);
        if ((p1 & 0x80) != 0) {
            byte[] loadFile = loadData.toByteArray();
            if (refuseInt && SimulatedContent.usesInt(loadFile)) {
                pendingLoadFile = null;
                return sw(0x6A80);
            }
            content.addLoadFile(pendingLoadFile, SimulatedContent.appletAids(loadFile),
                    SimulatedContent.importedAids(loadFile));
            pendingLoadFile = null;
        }
        return confirmed(0x9000);
    }

    private byte[] setStatus(int scope, int state, byte[] aid) {
        SimulatedContent.Application application = content.application(HEX.formatHex(aid));
        if (scope != 0x40 || application == null) {
            return sw(scope != 0x40 ? 0x6A86 : 0x6A88);
        }
        application.state = state == 0x80 ? application.state | 0x80 : application.state & 0x7F;
        return sw(0x9000);
    }

    private static String aidTlv(byte[] payload) {
        return HEX.formatHex(payload, 2, 2 + (payload[1] & 0xFF));
    }

    /** INSTALL/DELETE answer with the real card's one-byte '00' response data on success. */
    private static byte[] confirmed(int sw) {
        return sw == 0x9000 ? new byte[]{0x00, (byte) 0x90, 0x00} : sw(sw);
    }

    static byte[] sw(int sw) {
        return new byte[]{(byte) (sw >> 8), (byte) sw};
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    /** Length-value fields with one-byte lengths ('81 xx' for longer parameter fields). */
    private static final class Fields {
        private final byte[] data;
        private int offset;

        Fields(byte[] data) {
            this.data = data;
        }

        byte[] next() {
            int length = data[offset++] & 0xFF;
            if (length == 0x81) {
                length = data[offset++] & 0xFF;
            }
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return value;
        }
    }
}
