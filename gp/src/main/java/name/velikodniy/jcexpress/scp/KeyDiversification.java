package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

/**
 * Key diversification algorithms for deriving card-specific keys from a master key.
 *
 * <p>In production deployments, each card should have unique keys derived from
 * a master key using the card's diversification data (returned in the first 10 bytes
 * of the INITIALIZE UPDATE response).</p>
 *
 * <h3>Supported algorithms:</h3>
 * <ul>
 *   <li>{@link #visa2} — VISA2 diversification (3DES, used with SCP02)</li>
 *   <li>{@link #emvCps11} — EMV CPS 1.1 diversification (3DES, used with SCP02)</li>
 *   <li>{@link #kdf3} — KDF3 diversification (AES-CMAC, used with SCP03)</li>
 * </ul>
 *
 * <h3>Usage with GPSession:</h3>
 * <pre>
 * GPSession gp = GPSession.on(card)
 *     .keys(SCPKeys.fromMasterKey(masterKey))
 *     .diversification(KeyDiversification::visa2)
 *     .open();
 * </pre>
 *
 * @see GPSession
 */
public final class KeyDiversification {

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

        return SCPKeys.of(enc, mac, dek);
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

        return SCPKeys.of(enc, mac, dek);
    }

    /**
     * KDF3 key diversification for SCP03 (AES).
     *
     * <p>Uses the same AES-CMAC based KDF as SCP03 session key derivation,
     * but with the diversification data as context instead of the challenges.</p>
     *
     * @param masterKeys          the master key set
     * @param diversificationData the 10-byte diversification data from INITIALIZE UPDATE
     * @return diversified key set
     * @throws SCPException if the crypto operation fails
     */
    public static SCPKeys kdf3(SCPKeys masterKeys, byte[] diversificationData) {
        validateDiversificationData(diversificationData);
        int keyBits = masterKeys.keyLength() * 8;

        byte[] enc = CryptoUtil.deriveSCP03SessionKey(
                masterKeys.enc(), diversificationData, GP.SCP03_DERIVE_ENC, keyBits);
        byte[] mac = CryptoUtil.deriveSCP03SessionKey(
                masterKeys.mac(), diversificationData, GP.SCP03_DERIVE_C_MAC, keyBits);
        byte[] dek = CryptoUtil.deriveSCP03SessionKey(
                masterKeys.dek(), diversificationData, GP.SCP03_DERIVE_DEK, keyBits);

        return SCPKeys.of(enc, mac, dek);
    }

    // ── Internal ──

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
