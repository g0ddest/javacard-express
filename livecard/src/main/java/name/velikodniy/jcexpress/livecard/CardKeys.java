package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.scp.SCPKeys;

import java.util.HexFormat;
import java.util.Locale;

/**
 * The static SCP03 key set of the card's Issuer Security Domain (GlobalPlatform Card Specification v2.3
 * Amendment D 6.2.1: Key-ENC, Key-MAC, Key-DEK; AES-128, -192 or -256).
 *
 * <p>Written as the setting {@code keys}: {@code test} for the well-known GlobalPlatform test keys
 * {@code 404142...4F} (default), one hex key used for ENC, MAC and DEK, or {@code enc,mac,dek}. Key values
 * never appear in {@link #toString()}, transcripts or reports.</p>
 */
public final class CardKeys {

    /** The well-known GlobalPlatform test key of development cards. */
    private static final String TEST_KEY = "404142434445464748494A4B4C4D4E4F";
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] enc;
    private final byte[] mac;
    private final byte[] dek;
    private final String description;

    private CardKeys(byte[] enc, byte[] mac, byte[] dek, String description) {
        this.enc = enc.clone();
        this.mac = mac.clone();
        this.dek = dek.clone();
        this.description = description;
    }

    /**
     * Returns the GlobalPlatform test key set ({@code 404142...4F} for ENC, MAC and DEK).
     *
     * @return the test keys
     */
    public static CardKeys testKeys() {
        byte[] key = HEX.parseHex(TEST_KEY);
        return new CardKeys(key, key, key, "GP test keys 40..4F");
    }

    /**
     * Parses the {@code keys} setting.
     *
     * @param value {@code test}, one hex key, or {@code enc,mac,dek}
     * @return the key set
     * @throws IllegalArgumentException if the value is not one of the three forms or a key is not AES-128/192/256
     */
    public static CardKeys parse(String value) {
        String trimmed = value.strip();
        if (trimmed.toLowerCase(Locale.ROOT).equals("test")) {
            return testKeys();
        }
        String[] parts = trimmed.split(",", -1);
        if (parts.length == 1) {
            byte[] key = key(parts[0], "key");
            return new CardKeys(key, key, key, "custom AES-" + key.length * 8 + " key (ENC = MAC = DEK)");
        }
        if (parts.length != 3) {
            throw new IllegalArgumentException("keys must be 'test', one hex key, or 'enc,mac,dek'");
        }
        byte[] enc = key(parts[0], "ENC key");
        byte[] mac = key(parts[1], "MAC key");
        byte[] dek = key(parts[2], "DEK key");
        if (enc.length != mac.length || enc.length != dek.length) {
            throw new IllegalArgumentException("ENC, MAC and DEK keys must have the same length");
        }
        return new CardKeys(enc, mac, dek, "custom AES-" + enc.length * 8 + " keys (ENC, MAC, DEK)");
    }

    private static byte[] key(String hex, String name) {
        String clean = hex.replaceAll("\\s", "");
        if (!clean.matches("([0-9A-Fa-f]{2})+")) {
            throw new IllegalArgumentException(name + " is not hex");  // no cause: it could quote a key character
        }
        byte[] key = HEX.parseHex(clean);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException(name + " must be 16, 24 or 32 bytes (AES), got " + key.length);
        }
        return key;
    }

    /**
     * Returns the key set for {@link name.velikodniy.jcexpress.gp.GPSession}.
     *
     * @return the keys as {@link SCPKeys}
     */
    public SCPKeys toScpKeys() {
        return SCPKeys.of(enc, mac, dek);
    }

    /**
     * Returns the static Key-MAC, which the APDU guard uses to verify the handshake independently.
     *
     * @return a copy of the MAC key
     */
    public byte[] mac() {
        return mac.clone();
    }

    /**
     * Returns whether these are the GlobalPlatform test keys.
     *
     * @return true for the {@code test} setting
     */
    public boolean isTestKeys() {
        return HEX.formatHex(enc).equalsIgnoreCase(TEST_KEY) && HEX.formatHex(mac).equalsIgnoreCase(TEST_KEY)
                && HEX.formatHex(dek).equalsIgnoreCase(TEST_KEY);
    }

    /** Describes the key set without revealing key values. */
    @Override
    public String toString() {
        return description;
    }
}
