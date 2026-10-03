package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;
import java.util.Optional;

/**
 * Holds the three static keys used by GlobalPlatform Secure Channel Protocols.
 *
 * <p>Both SCP02 (3DES) and SCP03 (AES) use three keys:</p>
 * <ul>
 *   <li><b>ENC</b> — Secure Channel encryption key (authentication cryptograms, C-ENC)</li>
 *   <li><b>MAC</b> — Secure Channel message authentication code key (C-MAC, R-MAC)</li>
 *   <li><b>DEK</b> — data encryption key (for encrypting sensitive data like new keys)</li>
 * </ul>
 *
 * <p>A key set may carry its key type ({@link KeyInfo.KeyType#DES3} or {@link KeyInfo.KeyType#AES}),
 * which PUT KEY needs to encode the key data (GPCS v2.3.1 11.8.2.3, Table 11-16). Key sets created
 * with {@link #of(byte[], byte[], byte[])} have no explicit type.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * // Well-known GlobalPlatform test keys (all 404142...4F) - development cards only
 * SCPKeys keys = SCPKeys.defaultKeys();
 *
 * // Same key for all three
 * SCPKeys keys = SCPKeys.fromMasterKey(myKeyBytes);
 *
 * // Separate keys, with or without an explicit key type
 * SCPKeys keys = SCPKeys.of(encKey, macKey, dekKey);
 * SCPKeys aes = SCPKeys.aes(encKey, macKey, dekKey);
 * SCPKeys des = SCPKeys.des3(encKey, macKey, dekKey);
 * </pre>
 *
 * @see SCP02
 * @see SCP03
 */
public final class SCPKeys {

    /**
     * The well-known GlobalPlatform test key (16 bytes: 0x40-0x4F).
     */
    private static final byte[] DEFAULT_KEY = Hex.decode("404142434445464748494A4B4C4D4E4F");

    private final byte[] enc;
    private final byte[] mac;
    private final byte[] dek;
    private final KeyInfo.KeyType type;

    private SCPKeys(byte[] enc, byte[] mac, byte[] dek, KeyInfo.KeyType type) {
        this.enc = enc.clone();
        this.mac = mac.clone();
        this.dek = dek.clone();
        this.type = type;
    }

    /**
     * Creates keys with separate ENC, MAC, and DEK values and no explicit key type.
     *
     * @param enc the encryption key (16, 24 or 32 bytes)
     * @param mac the MAC key (same size as enc)
     * @param dek the data encryption key (same size as enc)
     * @return a new key set
     * @throws IllegalArgumentException if any key is null, has an invalid length, or the lengths differ
     */
    public static SCPKeys of(byte[] enc, byte[] mac, byte[] dek) {
        return create(enc, mac, dek, null);
    }

    /**
     * Creates an AES key set (key type '88', AES-128/192/256; Amendment D Table 6-1).
     *
     * @param enc the Key-ENC (16, 24 or 32 bytes)
     * @param mac the Key-MAC (same size as enc)
     * @param dek the Key-DEK (same size as enc)
     * @return a new AES key set
     * @throws IllegalArgumentException if a key is null or the lengths are invalid or differ
     */
    public static SCPKeys aes(byte[] enc, byte[] mac, byte[] dek) {
        return create(enc, mac, dek, KeyInfo.KeyType.AES);
    }

    /**
     * Creates a Triple DES key set (key type '80'; 16-byte double-length keys for SCP02, GPCS v2.3.1
     * Table E-3, or 24-byte triple-length keys).
     *
     * @param enc the ENC key (16 or 24 bytes)
     * @param mac the MAC key (same size as enc)
     * @param dek the DEK key (same size as enc)
     * @return a new 3DES key set
     * @throws IllegalArgumentException if a key is null or the lengths are invalid or differ
     */
    public static SCPKeys des3(byte[] enc, byte[] mac, byte[] dek) {
        if (enc != null && enc.length == 32) {
            throw new IllegalArgumentException("3DES keys are 16 or 24 bytes, got 32");
        }
        return create(enc, mac, dek, KeyInfo.KeyType.DES3);
    }

    /**
     * Creates a key set where all three keys share the same value.
     *
     * <p>This is the typical configuration for development and test cards, and the way to pass the single
     * Secure Channel base key of SCP02 implementation options with b1 = 0 (GPCS v2.3.1 Table E-1).</p>
     *
     * @param masterKey the key value to use for ENC, MAC, and DEK
     * @return a new key set
     * @throws IllegalArgumentException if the key is null or has invalid length
     */
    public static SCPKeys fromMasterKey(byte[] masterKey) {
        return of(masterKey, masterKey, masterKey);
    }

    /**
     * Creates a key set with the well-known GlobalPlatform test key ({@code 404142...4F}).
     *
     * <p>This key is used by most development JavaCards and simulators.
     * <strong>Never use this in production.</strong> GPSession never falls back to it: it must be
     * requested explicitly.</p>
     *
     * @return a new key set with the well-known test keys
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
     * @return key length (16, 24 or 32)
     */
    public int keyLength() {
        return enc.length;
    }

    /**
     * Returns the explicit key type of this key set, if one was given.
     *
     * @return the key type, or empty for key sets created with {@link #of(byte[], byte[], byte[])}
     */
    public Optional<KeyInfo.KeyType> keyType() {
        return Optional.ofNullable(type);
    }

    /**
     * Returns a copy of this key set with an explicit key type.
     *
     * @param keyType the key type
     * @return a typed key set with the same key values
     * @throws IllegalArgumentException if the key length is not valid for the type
     */
    public SCPKeys withKeyType(KeyInfo.KeyType keyType) {
        return keyType == KeyInfo.KeyType.AES ? aes(enc, mac, dek) : des3(enc, mac, dek);
    }

    /** Returns true if ENC, MAC and DEK are the same key (single Secure Channel base key). */
    boolean allEqual() {
        return Arrays.equals(enc, mac) && Arrays.equals(enc, dek);
    }

    @Override
    public String toString() {
        return "SCPKeys[length=" + enc.length + (type == null ? "" : ", type=" + type) + "]";
    }

    private static SCPKeys create(byte[] enc, byte[] mac, byte[] dek, KeyInfo.KeyType type) {
        validateKey("ENC", enc);
        validateKey("MAC", mac);
        validateKey("DEK", dek);
        if (mac.length != enc.length || dek.length != enc.length) {
            throw new IllegalArgumentException("ENC, MAC and DEK keys must have the same length, got "
                    + enc.length + "/" + mac.length + "/" + dek.length);
        }
        return new SCPKeys(enc, mac, dek, type);
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
