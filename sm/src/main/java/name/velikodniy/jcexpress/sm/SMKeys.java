package name.velikodniy.jcexpress.sm;

import java.util.Arrays;
import java.util.Objects;

/**
 * Holds the encryption and MAC keys for ISO 7816-4 Secure Messaging.
 *
 * <p>Defensive copies are made on construction and on access to prevent
 * key material from leaking through shared array references, and {@link #toString()} does not reveal the keys.</p>
 *
 * @param encKey the encryption key (16 or 24 bytes for DES3; 16, 24 or 32 bytes for AES)
 * @param macKey the MAC key (16 or 24 bytes for DES3; 16, 24 or 32 bytes for AES)
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

    /**
     * Describes the keys without revealing them: only their lengths are shown, so that keys do not end up in logs
     * or assertion messages.
     *
     * @return e.g. {@code SMKeys[encKey=<16 bytes, redacted>, macKey=<16 bytes, redacted>]}
     */
    @Override
    public String toString() {
        return "SMKeys[encKey=" + redacted(encKey) + ", macKey=" + redacted(macKey) + "]";
    }

    /**
     * Describes a secret by its length only.
     *
     * @param secret the secret
     * @return e.g. {@code <16 bytes, redacted>}
     */
    private static String redacted(byte[] secret) {
        return "<" + secret.length + " bytes, redacted>";
    }
}
