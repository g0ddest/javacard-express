package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;

/**
 * Holds the three static keys used by GlobalPlatform Secure Channel Protocol.
 *
 * <p>Both SCP02 (3DES) and SCP03 (AES) use three keys:</p>
 * <ul>
 *   <li><b>ENC</b> — data encryption key (C-ENC for command encryption)</li>
 *   <li><b>MAC</b> — message authentication code key (C-MAC)</li>
 *   <li><b>DEK</b> — data encryption key (for encrypting sensitive data like new keys)</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>
 * // Default test keys (all 404142...4F)
 * SCPKeys keys = SCPKeys.defaultKeys();
 *
 * // Custom keys (same key for all three)
 * SCPKeys keys = SCPKeys.fromMasterKey(myKeyBytes);
 *
 * // Separate keys
 * SCPKeys keys = SCPKeys.of(encKey, macKey, dekKey);
 * </pre>
 *
 * <p>The default key value {@code 404142434445464748494A4B4C4D4E4F} is the
 * well-known GlobalPlatform test key used for development cards.</p>
 *
 * @see SCP02
 * @see SCP03
 */
public final class SCPKeys {

    /**
     * The default GlobalPlatform test key (16 bytes: 0x40-0x4F).
     */
    private static final byte[] DEFAULT_KEY = Hex.decode("404142434445464748494A4B4C4D4E4F");

    private final byte[] enc;
    private final byte[] mac;
    private final byte[] dek;

    private SCPKeys(byte[] enc, byte[] mac, byte[] dek) {
        this.enc = enc.clone();
        this.mac = mac.clone();
        this.dek = dek.clone();
    }

    /**
     * Creates keys with separate ENC, MAC, and DEK values.
     *
     * @param enc the encryption key (16 bytes for SCP02/SCP03-128, 32 bytes for SCP03-256)
     * @param mac the MAC key (same size as enc)
     * @param dek the data encryption key (same size as enc)
     * @return a new key set
     * @throws IllegalArgumentException if any key is null or has invalid length
     */
    public static SCPKeys of(byte[] enc, byte[] mac, byte[] dek) {
        validateKey("ENC", enc);
        validateKey("MAC", mac);
        validateKey("DEK", dek);
        return new SCPKeys(enc, mac, dek);
    }

    /**
     * Creates a key set where all three keys share the same value.
     *
     * <p>This is the typical configuration for development and test cards.</p>
     *
     * @param masterKey the key value to use for ENC, MAC, and DEK
     * @return a new key set
     * @throws IllegalArgumentException if the key is null or has invalid length
     */
    public static SCPKeys fromMasterKey(byte[] masterKey) {
        return of(masterKey, masterKey, masterKey);
    }

    /**
     * Creates a key set with the default GlobalPlatform test key ({@code 404142...4F}).
     *
     * <p>This key is used by most development JavaCards and simulators.
     * <strong>Never use this in production.</strong></p>
     *
     * @return a new key set with default test keys
     */
    public static SCPKeys defaultKeys() {
        return fromMasterKey(DEFAULT_KEY);
    }

    /**
     * Returns the ENC (encryption) key.
     *
     * @return a copy of the encryption key bytes
     */
    public byte[] enc() {
        return enc.clone();
    }

    /**
     * Returns the MAC (message authentication code) key.
     *
     * @return a copy of the MAC key bytes
     */
    public byte[] mac() {
        return mac.clone();
    }

    /**
     * Returns the DEK (data encryption key).
     *
     * @return a copy of the DEK bytes
     */
    public byte[] dek() {
        return dek.clone();
    }

    /**
     * Returns the key length in bytes (all three keys have the same length).
     *
     * @return key length (16 or 32)
     */
    public int keyLength() {
        return enc.length;
    }

    @Override
    public String toString() {
        return "SCPKeys[length=" + enc.length + "]";
    }

    private static void validateKey(String name, byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException(name + " key must not be null");
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException(
                    name + " key must be 16, 24 or 32 bytes, got: " + key.length);
        }
    }
}
