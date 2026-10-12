package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * The off-card entity of an SCP03 session with 8-byte challenges, written from GlobalPlatform Card Specification
 * v2.3 Amendment D v1.1.2 for the tests of the card side: it sends INITIALIZE UPDATE and EXTERNAL AUTHENTICATE
 * (7.1), checks the card cryptogram (6.2.2.2), protects commands (6.2.4, 6.2.6) and checks and removes the
 * protection of responses (6.2.5, 6.2.7).
 */
public final class Scp03Host {

    private final byte[] keyEnc;
    private final byte[] keyMac;
    private byte[] hostChallenge = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x01};
    private byte[] context;
    private byte[] sEnc;
    private byte[] sMac;
    private byte[] sRmac;
    private byte[] macChainingValue;
    private int level;
    private int counter;
    private int option;

    /**
     * Creates a host with a key set.
     *
     * @param keyEnc the static Key-ENC
     * @param keyMac the static Key-MAC
     */
    public Scp03Host(byte[] keyEnc, byte[] keyMac) {
        this.keyEnc = keyEnc.clone();
        this.keyMac = keyMac.clone();
    }

    /**
     * Creates a host with the well-known test key 404142...4F.
     *
     * @return the host
     */
    public static Scp03Host withTestKeys() {
        byte[] key = new byte[16];
        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (0x40 + i);
        }
        return new Scp03Host(key, key);
    }

    /**
     * Builds INITIALIZE UPDATE with a new host challenge (Amendment D Table 7-2), without Le.
     *
     * @param keyVersion P1, the Key Version Number, or 0
     * @return the command
     */
    public byte[] initializeUpdate(int keyVersion) {
        hostChallenge = hostChallenge.clone();
        hostChallenge[7]++;
        return concat(new byte[]{(byte) 0x80, 0x50, (byte) keyVersion, 0x00, 0x08}, hostChallenge);
    }

    /**
     * Takes the response data of INITIALIZE UPDATE (Table 7-3), derives the session keys (6.2.1) and checks the
     * card cryptogram (6.2.2.2).
     *
     * @param response the response data without the status word
     * @return true when the card cryptogram is the expected one
     */
    public boolean receive(byte[] response) {
        option = response[12] & 0xFF;
        context = concat(hostChallenge, Arrays.copyOfRange(response, 13, 21));
        int bits = keyEnc.length * 8;
        sEnc = CryptoUtil.deriveSCP03SessionKey(keyEnc, context, (byte) 0x04, bits);
        sMac = CryptoUtil.deriveSCP03SessionKey(keyMac, context, (byte) 0x06, bits);
        sRmac = CryptoUtil.deriveSCP03SessionKey(keyMac, context, (byte) 0x07, bits);
        byte[] expected = CryptoUtil.deriveSCP03SessionKey(sMac, context, (byte) 0x00, 64);
        return MessageDigest.isEqual(expected, Arrays.copyOfRange(response, 21, 29));
    }

    /**
     * Returns the option "i" the card reported.
     *
     * @return the third byte of the key information
     */
    public int option() {
        return option;
    }

    /**
     * Builds EXTERNAL AUTHENTICATE (Table 7-5) with the host cryptogram (6.2.2.3) and its C-MAC, computed with a
     * MAC chaining value of zeros (6.2.3); the session of this host is open afterwards.
     *
     * @param securityLevel P1 (Table 7-6)
     * @return the command
     */
    public byte[] externalAuthenticate(int securityLevel) {
        byte[] cryptogram = CryptoUtil.deriveSCP03SessionKey(sMac, context, (byte) 0x01, 64);
        byte[] header = {(byte) 0x84, (byte) 0x82, (byte) securityLevel, 0x00, 0x10};
        byte[] mac = CryptoUtil.aesCmac(sMac, concat(new byte[16], header, cryptogram));
        macChainingValue = mac;
        level = securityLevel;
        counter = 0;
        return concat(header, cryptogram, Arrays.copyOf(mac, 8));
    }

    /**
     * Protects a command as the security level asks: encrypts a non-empty data field at a C-DECRYPTION level
     * (6.2.6) and appends the C-MAC (6.2.4). At level '00' the command is returned as it is.
     *
     * @param command a short command: header, and Lc with data when it has data (no Le)
     * @return the protected command
     */
    public byte[] wrap(byte[] command) {
        if ((level & 0x01) == 0) {
            return command.clone();
        }
        counter++;
        byte[] data = command.length > 5 ? Arrays.copyOfRange(command, 5, command.length) : new byte[0];
        if ((level & 0x02) != 0 && data.length > 0) {
            data = CryptoUtil.aesCbcEncrypt(sEnc, CryptoUtil.pad80(data, 16), icv(false));
        }
        return withMac(command, data);
    }

    /**
     * Appends the C-MAC to a command whose data field is given as it goes on the wire, for tests that send
     * what a correct host would not.
     *
     * @param command the command (its header is used)
     * @param data    the data field to send before the C-MAC
     * @return the protected command
     */
    public byte[] withMac(byte[] command, byte[] data) {
        byte[] header = {(byte) ((command[0] & 0xF0) | 0x04), command[1], command[2], command[3],
            (byte) (data.length + 8)};
        byte[] mac = CryptoUtil.aesCmac(sMac, concat(macChainingValue, header, data));
        macChainingValue = mac;
        return concat(header, data, Arrays.copyOf(mac, 8));
    }

    /**
     * Counts a command and returns the ICV its encrypted data field needs, for tests that encrypt themselves.
     *
     * @return the ICV of command encryption for the next command (6.2.6)
     */
    public byte[] nextCommandIcv() {
        counter++;
        return icv(false);
    }

    /**
     * Returns S-ENC of the session.
     *
     * @return the session encryption key
     */
    public byte[] sessionEncryptionKey() {
        return sEnc.clone();
    }

    /**
     * Checks and removes the protection of a response: at an R-MAC level the R-MAC over the MAC chaining value,
     * the data and the status word (6.2.5), at an R-ENCRYPTION level the encryption of non-empty data (6.2.7). A
     * response with an error status word is not protected.
     *
     * @param response the response data followed by the status word
     * @return the plain response data followed by the status word
     * @throws AssertionError if the R-MAC is missing or wrong
     */
    public byte[] unwrap(byte[] response) {
        byte[] status = Arrays.copyOfRange(response, response.length - 2, response.length);
        int sw1 = status[0] & 0xFF;
        boolean protectedStatus = (sw1 == 0x90 && status[1] == 0) || sw1 == 0x62 || sw1 == 0x63;
        if ((level & 0x10) == 0 || !protectedStatus) {
            return response.clone();
        }
        if (response.length < 10) {
            throw new AssertionError("The response has no R-MAC");
        }
        byte[] data = Arrays.copyOf(response, response.length - 10);
        byte[] mac = CryptoUtil.aesCmac(sRmac, concat(macChainingValue, data, status));
        if (!MessageDigest.isEqual(Arrays.copyOf(mac, 8), Arrays.copyOfRange(response, data.length, data.length + 8))) {
            throw new AssertionError("The R-MAC of the response is wrong");
        }
        if ((level & 0x20) != 0 && data.length > 0) {
            data = CryptoUtil.unpad80(CryptoUtil.aesCbcDecrypt(sEnc, data, icv(true)));
        }
        return concat(data, status);
    }

    /** The ICV: the counter block, for responses with the first byte '80', encrypted with S-ENC (6.2.6, 6.2.7). */
    private byte[] icv(boolean response) {
        byte[] block = new byte[16];
        block[12] = (byte) (counter >> 24);
        block[13] = (byte) (counter >> 16);
        block[14] = (byte) (counter >> 8);
        block[15] = (byte) counter;
        if (response) {
            block[0] = (byte) 0x80;
        }
        return CryptoUtil.aesEcbEncrypt(sEnc, block);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
