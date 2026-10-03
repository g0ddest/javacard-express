package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Key diversification algorithms for deriving card-specific keys from a master key.
 *
 * <p>In production deployments, each card should have unique keys derived from
 * a master key using the card's diversification data (returned in the first 10 bytes
 * of the INITIALIZE UPDATE response).</p>
 *
 * <h2>Supported algorithms:</h2>
 * <ul>
 *   <li>{@link #visa2} — VISA2 diversification (3DES, used with SCP02)</li>
 *   <li>{@link #emvCps11} — EMV CPS 1.1 diversification (3DES, used with SCP02)</li>
 *   <li>{@link #kdf3} — "KDF3" diversification of AES key sets (AES-CMAC counter-mode KDF, used with SCP03)</li>
 * </ul>
 *
 * <h2>Usage with GPSession:</h2>
 * <pre>
 * GPSession gp = GPSession.on(card)
 *     .keys(SCPKeys.fromMasterKey(masterKey))
 *     .diversification(KeyDiversification::visa2)
 *     .open();
 * </pre>
 *
 * @see name.velikodniy.jcexpress.gp.GPSession
 */
public final class KeyDiversification {

    private static final int KDD_LENGTH = 10;
    private static final int KDF3_ENC = 0x01;
    private static final int KDF3_MAC = 0x02;
    private static final int KDF3_DEK = 0x03;

    private KeyDiversification() {
    }

    /**
     * VISA2 key diversification for SCP02 (3DES).
     *
     * <p>Derives card-specific keys using bytes from the diversification data:</p>
     * <pre>
     * For each key (k=1 ENC, k=2 MAC, k=3 DEK):
     *   left  = d[0] d[1] d[4] d[5] d[6] d[7] 0xF0 k
     *   right = d[0] d[1] d[4] d[5] d[6] d[7] 0x0F k
     *   key   = 3DES-ECB(masterKey, left) || 3DES-ECB(masterKey, right)
     * </pre>
     *
     * @param masterKeys          the master key set (typically all three keys are the same)
     * @param diversificationData the 10-byte diversification data from INITIALIZE UPDATE
     * @return diversified key set
     * @throws SCPException if the crypto operation fails
     */
    public static SCPKeys visa2(SCPKeys masterKeys, byte[] diversificationData) {
        validateDiversificationData(diversificationData);
        byte[] d = diversificationData;

        byte[] enc = visa2DeriveKey(masterKeys.enc(), d[0], d[1], d[4], d[5], d[6], d[7], 1);
        byte[] mac = visa2DeriveKey(masterKeys.mac(), d[0], d[1], d[4], d[5], d[6], d[7], 2);
        byte[] dek = visa2DeriveKey(masterKeys.dek(), d[0], d[1], d[4], d[5], d[6], d[7], 3);

        return SCPKeys.des3(enc, mac, dek);
    }

    /**
     * EMV CPS 1.1 key diversification for SCP02 (3DES).
     *
     * <p>Similar to VISA2 but uses different byte positions from the diversification data:</p>
     * <pre>
     * For each key (k=1 ENC, k=2 MAC, k=3 DEK):
     *   left  = d[4] d[5] d[6] d[7] d[8] d[9] 0xF0 k
     *   right = d[4] d[5] d[6] d[7] d[8] d[9] 0x0F k
     *   key   = 3DES-ECB(masterKey, left) || 3DES-ECB(masterKey, right)
     * </pre>
     *
     * @param masterKeys          the master key set
     * @param diversificationData the 10-byte diversification data from INITIALIZE UPDATE
     * @return diversified key set
     * @throws SCPException if the crypto operation fails
     */
    public static SCPKeys emvCps11(SCPKeys masterKeys, byte[] diversificationData) {
        validateDiversificationData(diversificationData);
        byte[] d = diversificationData;

        byte[] enc = visa2DeriveKey(masterKeys.enc(), d[4], d[5], d[6], d[7], d[8], d[9], 1);
        byte[] mac = visa2DeriveKey(masterKeys.mac(), d[4], d[5], d[6], d[7], d[8], d[9], 2);
        byte[] dek = visa2DeriveKey(masterKeys.dek(), d[4], d[5], d[6], d[7], d[8], d[9], 3);

        return SCPKeys.des3(enc, mac, dek);
    }

    /**
     * "KDF3" key diversification for SCP03 (AES) key sets.
     *
     * <p>This is the scheme of the GlobalPlatformPro tool's {@code kdf3} key template; it is not defined by a
     * GlobalPlatform specification. Each key is derived from its master key with the NIST SP 800-108 KDF in
     * counter mode, AES-CMAC (NIST SP 800-38B) as PRF and the counter first; the i-th PRF input is:</p>
     * <pre>
     * i (1 byte, '01', '02') || '00 00 00' || key type ('01' ENC, '02' MAC, '03' DEK) || '00' || KDD (10 bytes)
     * </pre>
     * <p>The output is truncated to the master key length (AES-128, AES-192 or AES-256). The scheme
     * reproduces the public GlobalPlatformPro test vectors (key check values for AES-128/192/256 master
     * keys and an external 256-bit vector). It differs from the session key derivation of Amendment D
     * 4.1.5, which has a 12-byte label and an L field.</p>
     *
     * @param masterKeys          the AES master key set
     * @param diversificationData the key diversification data from INITIALIZE UPDATE (the first 10 bytes are used)
     * @return the diversified AES key set
     * @throws SCPException if the diversification data is shorter than 10 bytes or the master keys are 3DES keys
     */
    public static SCPKeys kdf3(SCPKeys masterKeys, byte[] diversificationData) {
        validateDiversificationData(diversificationData);
        if (masterKeys.keyType().filter(type -> type != KeyInfo.KeyType.AES).isPresent()) {
            throw new SCPException("KDF3 diversifies AES (SCP03) key sets, but the master key set is typed 3DES");
        }
        byte[] kdd = Arrays.copyOf(diversificationData, KDD_LENGTH);
        return SCPKeys.aes(kdf3Key(masterKeys.enc(), KDF3_ENC, kdd), kdf3Key(masterKeys.mac(), KDF3_MAC, kdd),
                kdf3Key(masterKeys.dek(), KDF3_DEK, kdd));
    }

    // ── Internal ──

    /** One KDF3 key: counter-mode KDF with AES-CMAC, output truncated to the master key length. */
    private static byte[] kdf3Key(byte[] masterKey, int keyType, byte[] kdd) {
        ByteArrayOutputStream derived = new ByteArrayOutputStream();
        for (int counter = 1; derived.size() < masterKey.length; counter++) {
            byte[] input = new byte[6 + KDD_LENGTH];
            input[0] = (byte) counter;
            input[4] = (byte) keyType;
            System.arraycopy(kdd, 0, input, 6, KDD_LENGTH);
            derived.writeBytes(CryptoUtil.aesCmac(masterKey, input));
        }
        return Arrays.copyOf(derived.toByteArray(), masterKey.length);
    }

    /**
     * Derives a single 16-byte key using the VISA2/EMV CPS scheme.
     *
     * @param masterKey the 16-byte master key for this purpose
     * @param b0-b5     the 6 selected diversification bytes
     * @param keyIndex  the key index (1=ENC, 2=MAC, 3=DEK)
     * @return 16-byte derived key (left half || right half)
     */
    private static byte[] visa2DeriveKey(byte[] masterKey,
                                          byte b0, byte b1, byte b2,
                                          byte b3, byte b4, byte b5,
                                          int keyIndex) {
        byte[] left = {b0, b1, b2, b3, b4, b5, (byte) 0xF0, (byte) keyIndex};
        byte[] right = {b0, b1, b2, b3, b4, b5, (byte) 0x0F, (byte) keyIndex};

        byte[] encLeft = CryptoUtil.des3EcbEncrypt(masterKey, left);
        byte[] encRight = CryptoUtil.des3EcbEncrypt(masterKey, right);

        byte[] result = new byte[16];
        System.arraycopy(encLeft, 0, result, 0, 8);
        System.arraycopy(encRight, 0, result, 8, 8);
        return result;
    }

    private static void validateDiversificationData(byte[] data) {
        if (data == null || data.length < 10) {
            throw new SCPException(
                    "Diversification data must be at least 10 bytes, got: "
                            + (data == null ? "null" : data.length));
        }
    }
}
