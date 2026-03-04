package name.velikodniy.jcexpress.sm;

import java.util.Arrays;
import java.util.HexFormat;
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

    /** {@inheritDoc} */
    @Override
    public byte[] encKey() {
        return encKey.clone();
    }

    /** {@inheritDoc} */
    @Override
    public byte[] macKey() {
        return macKey.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof SMKeys(var ek, var mk)) {
            return Arrays.equals(encKey, ek)
                    && Arrays.equals(macKey, mk);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(encKey) + Arrays.hashCode(macKey);
    }

    @Override
    public String toString() {
        HexFormat hex = HexFormat.of();
        return "SMKeys[encKey=" + hex.formatHex(encKey)
                + ", macKey=" + hex.formatHex(macKey) + "]";
    }
}
