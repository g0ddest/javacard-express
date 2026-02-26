package name.velikodniy.jcexpress.sm;

import java.util.Objects;

/**
 * Holds the encryption and MAC keys for ISO 7816-4 Secure Messaging.
 *
 * <p>Defensive copies are made on construction and on access to prevent
 * key material from leaking through shared array references.</p>
 *
 * @param encKey the encryption key (16 bytes for DES3, 16 or 32 bytes for AES)
 * @param macKey the MAC key (16 bytes for DES3, 16 or 32 bytes for AES)
 */
public record SMKeys(byte[] encKey, byte[] macKey) {

    /**
     * Creates SM keys with defensive copies.
     *
     * @param encKey the encryption key
     * @param macKey the MAC key
     */
    public SMKeys {
        Objects.requireNonNull(encKey, "encKey must not be null");
        Objects.requireNonNull(macKey, "macKey must not be null");
        encKey = encKey.clone();
        macKey = macKey.clone();
    }

    @Override
    public byte[] encKey() {
        return encKey.clone();
    }

    @Override
    public byte[] macKey() {
        return macKey.clone();
    }
}
