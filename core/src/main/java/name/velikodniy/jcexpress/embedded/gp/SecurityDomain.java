package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Hex;

import java.security.SecureRandom;
import java.util.function.Supplier;

/**
 * The Security Domain of a simulated card, as far as its applications see it: the key set and configuration of
 * the secure channel that {@code org.globalplatform.GPSystem.getSecureChannel()} hands out, and the session
 * opened through it. Every application of a {@link GlobalPlatformCard} is associated with this one domain, the
 * Issuer Security Domain (GlobalPlatform Card Specification v2.3.1 section 7.3.1: an application uses the secure
 * channel services of its associated Security Domain; H.1.3: the default AID).
 *
 * <p>The protocol is SCP03 with 8-byte challenges (GlobalPlatform Card Specification v2.3 Amendment D v1.1.2). The
 * key set is Key-ENC, Key-MAC and Key-DEK of one AES length (Table 6-1) under a Key Version Number, 'FF' by
 * default. The option "i" (Table 5-1) is '00' by default: random card challenge, no R-MAC, no R-ENCRYPTION; '10'
 * adds the pseudo-random card challenge with its sequence counter (6.2.2.1), '20' R-MAC, '60' R-MAC and
 * R-ENCRYPTION. A domain supports one session at a time, bound to the application that opened it.</p>
 *
 * <pre>{@code
 * SecurityDomain domain = card.globalPlatform().securityDomain();
 * domain.scp03Option(0x60);                       // let hosts ask for R-MAC and R-ENCRYPTION
 * byte[] staticKey = domain.keyEnc();             // what the host authenticates with
 * }</pre>
 *
 * <p>Not simulated: SCP02, 16-byte challenges (S16), BEGIN and END R-MAC SESSION, logical channels, key
 * management (PUT KEY) and the blocking of a key set after failed authentications; the failures are only
 * counted.</p>
 */
public final class SecurityDomain {

    /** The default AID of the Issuer Security Domain (GlobalPlatform Card Specification v2.3.1 H.1.3). */
    public static final AID ISSUER_SECURITY_DOMAIN = AID.fromHex("A000000151000000");

    /** Option "i": pseudo-random card challenge (Amendment D Table 5-1, b5). */
    public static final int OPTION_PSEUDO_RANDOM_CHALLENGE = 0x10;
    /** Option "i": R-MAC support (Amendment D Table 5-1, b6). */
    public static final int OPTION_R_MAC = 0x20;
    /** Option "i": R-MAC and R-ENCRYPTION support (Amendment D Table 5-1, b7 and b6). */
    public static final int OPTION_R_MAC_R_ENCRYPTION = 0x60;

    private static final String TEST_KEY = "404142434445464748494A4B4C4D4E4F";
    private static final int SEQUENCE_COUNTER_MAX = 0xFFFFFF;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AID aid = ISSUER_SECURITY_DOMAIN;
    private final byte[] keyEnc;
    private final byte[] keyMac;
    private final byte[] keyDek;
    private final Scp03Session session = new Scp03Session(this);
    private int keyVersion = 0xFF;
    private int option;
    private byte[] diversificationData = new byte[10];
    private int sequenceCounter;
    private int externalAuthentications;
    private int failedAuthentications;
    private String fixedBecause;
    private Supplier<byte[]> challenges = SecurityDomain::randomChallenge;

    private SecurityDomain(byte[] keyEnc, byte[] keyMac, byte[] keyDek) {
        this.keyEnc = keyEnc.clone();
        this.keyMac = keyMac.clone();
        this.keyDek = keyDek.clone();
    }

    /**
     * Creates a domain with the well-known test key set of development cards: Key-ENC, Key-MAC and Key-DEK are
     * all {@code 404142434445464748494A4B4C4D4E4F}.
     *
     * @return the domain, with Key Version Number 'FF' and option "i" '00'
     */
    public static SecurityDomain withTestKeys() {
        byte[] key = Hex.decode(TEST_KEY);
        return new SecurityDomain(key, key, key);
    }

    /**
     * Creates a domain with a key set.
     *
     * @param keyEnc the static Key-ENC
     * @param keyMac the static Key-MAC
     * @param keyDek the static Key-DEK
     * @return the domain, with Key Version Number 'FF' and option "i" '00'
     * @throws IllegalArgumentException unless the three keys are AES keys of the same length, 16, 24 or 32 bytes
     *                                  (Amendment D Table 6-1)
     */
    public static SecurityDomain withKeys(byte[] keyEnc, byte[] keyMac, byte[] keyDek) {
        int length = keyEnc.length;
        if ((length != 16 && length != 24 && length != 32) || keyMac.length != length || keyDek.length != length) {
            throw new IllegalArgumentException("An SCP03 key set has a Key-ENC, a Key-MAC and a Key-DEK of the same"
                    + " AES length, 16, 24 or 32 bytes (Amendment D Table 6-1); got " + keyEnc.length + ", "
                    + keyMac.length + " and " + keyDek.length + " bytes");
        }
        return new SecurityDomain(keyEnc, keyMac, keyDek);
    }

    /**
     * Makes the configuration final: the Key Version Number, the option "i" and the key diversification data
     * cannot be changed any more. A card simulation that models one particular card uses it.
     *
     * @param reason why the configuration is fixed; the setters name it
     * @return this domain
     */
    public SecurityDomain fixed(String reason) {
        this.fixedBecause = reason == null ? "it is fixed" : reason;
        return this;
    }

    /**
     * Returns the AID of this Security Domain.
     *
     * @return {@link #ISSUER_SECURITY_DOMAIN}
     */
    public AID aid() {
        return aid;
    }

    /**
     * Returns the static secure channel encryption key.
     *
     * @return a copy of Key-ENC
     */
    public byte[] keyEnc() {
        return keyEnc.clone();
    }

    /**
     * Returns the static secure channel message authentication code key.
     *
     * @return a copy of Key-MAC
     */
    public byte[] keyMac() {
        return keyMac.clone();
    }

    /**
     * Returns the data encryption key, which {@code SecureChannel.decryptData} and {@code encryptData} use
     * (Amendment D 6.2.8).
     *
     * @return a copy of Key-DEK
     */
    public byte[] keyDek() {
        return keyDek.clone();
    }

    /**
     * Returns the Key Version Number of the key set.
     *
     * @return the number, 1 to 255
     */
    public int keyVersion() {
        return keyVersion;
    }

    /**
     * Sets the Key Version Number, which INITIALIZE UPDATE names in P1 and gets back in the key information
     * (Amendment D 7.1.1.3, 7.1.1.6).
     *
     * @param keyVersion the number, 1 to 255
     * @return this domain
     * @throws IllegalArgumentException if the number is out of range
     * @throws IllegalStateException    if the configuration is {@link #fixed(String) fixed}
     */
    public SecurityDomain keyVersion(int keyVersion) {
        requireConfigurable("Key Version Number");
        if (keyVersion < 1 || keyVersion > 0xFF) {
            throw new IllegalArgumentException("A Key Version Number is 1 to 255, got " + keyVersion);
        }
        this.keyVersion = keyVersion;
        return this;
    }

    /**
     * Returns the SCP03 option "i".
     *
     * @return the option (Amendment D Table 5-1)
     */
    public int scp03Option() {
        return option;
    }

    /**
     * Sets the SCP03 option "i" for the sessions opened from now on; an open session keeps the option it was
     * opened with.
     *
     * @param option a combination of {@link #OPTION_PSEUDO_RANDOM_CHALLENGE}, {@link #OPTION_R_MAC} and
     *               {@link #OPTION_R_MAC_R_ENCRYPTION}, or 0
     * @return this domain
     * @throws IllegalArgumentException if the value uses reserved bits or announces R-ENCRYPTION without R-MAC
     *                                  (Amendment D Table 5-1)
     * @throws IllegalStateException    if the configuration is {@link #fixed(String) fixed}
     */
    public SecurityDomain scp03Option(int option) {
        requireConfigurable("SCP03 option i");
        boolean reserved = (option & ~0x70) != 0;
        boolean encryptionWithoutMac = (option & 0x60) == 0x40;
        if (reserved || encryptionWithoutMac) {
            throw new IllegalArgumentException(String.format("SCP03 i=%02X is not defined: b8 is reserved, b4 to b1"
                    + " are RFU, and R-ENCRYPTION (b7) comes only with R-MAC (b6) (Amendment D Table 5-1)", option));
        }
        this.option = option;
        return this;
    }

    /**
     * Returns the key diversification data of the INITIALIZE UPDATE response.
     *
     * @return a copy of the 10 bytes, zeros by default
     */
    public byte[] keyDiversificationData() {
        return diversificationData.clone();
    }

    /**
     * Sets the key diversification data, which a card returns first in the INITIALIZE UPDATE response
     * (Amendment D Table 7-3).
     *
     * @param data 10 bytes
     * @return this domain
     * @throws IllegalArgumentException if the data are not 10 bytes
     * @throws IllegalStateException    if the configuration is {@link #fixed(String) fixed}
     */
    public SecurityDomain keyDiversificationData(byte[] data) {
        requireConfigurable("key diversification data");
        if (data.length != 10) {
            throw new IllegalArgumentException("Key diversification data are 10 bytes (Amendment D Table 7-3), got "
                    + data.length);
        }
        this.diversificationData = data.clone();
        return this;
    }

    /**
     * Returns the sequence counter of the key set, which counts the INITIALIZE UPDATE commands that asked for a
     * pseudo-random card challenge (Amendment D 6.2.2.1).
     *
     * @return the counter, 0 on a new domain
     */
    public int sequenceCounter() {
        return sequenceCounter;
    }

    /**
     * Returns how many EXTERNAL AUTHENTICATE commands applications passed to this domain.
     *
     * @return the count
     */
    public int externalAuthentications() {
        return externalAuthentications;
    }

    /**
     * Returns how many EXTERNAL AUTHENTICATE commands this domain rejected (what a card counts against its key
     * set).
     *
     * @return the count
     */
    public int failedAuthentications() {
        return failedAuthentications;
    }

    /**
     * Returns the Current Security Level of the session an application opened through this domain.
     *
     * @return a combination of the {@code org.globalplatform.SecureChannel} level constants as an unsigned
     *         value, 0 without a session and after a session was aborted
     */
    public int securityLevel() {
        return session.currentLevel();
    }

    /** The protocol state of this domain. */
    Scp03Session session() {
        return session;
    }

    /** Replaces the source of random card challenges, for known-answer tests. */
    void challenges(Supplier<byte[]> source) {
        this.challenges = source;
    }

    /** A random card challenge (Amendment D 6.2.2.1, option "i" b5 = 0). */
    byte[] nextChallenge() {
        return challenges.get();
    }

    /**
     * Increments the sequence counter for a pseudo-random card challenge (Amendment D 6.2.2.1).
     *
     * @return the new value as 3 bytes, or null when the counter has reached its maximum
     */
    byte[] nextSequenceCounter() {
        if (sequenceCounter >= SEQUENCE_COUNTER_MAX) {
            return null;
        }
        sequenceCounter++;
        return new byte[]{(byte) (sequenceCounter >> 16), (byte) (sequenceCounter >> 8), (byte) sequenceCounter};
    }

    /** Sets the sequence counter, for tests of its limit. */
    void sequenceCounter(int value) {
        this.sequenceCounter = value;
    }

    void authenticationReceived() {
        externalAuthentications++;
    }

    void authenticationRejected() {
        failedAuthentications++;
    }

    /** The keys without a copy, for the protocol. */
    byte[] encKey() {
        return keyEnc;
    }

    byte[] macKey() {
        return keyMac;
    }

    byte[] dekKey() {
        return keyDek;
    }

    private void requireConfigurable(String what) {
        if (fixedBecause != null) {
            throw new IllegalStateException("The " + what + " of this Security Domain cannot be changed: "
                    + fixedBecause);
        }
    }

    private static byte[] randomChallenge() {
        byte[] challenge = new byte[8];
        RANDOM.nextBytes(challenge);
        return challenge;
    }
}
