package name.velikodniy.jcexpress;

/**
 * The static secure channel keys of the Security Domain the applets of a test run are associated with: on the
 * backends of {@link JavaCardTest} classes the Issuer Security Domain. An applet that uses the GlobalPlatform API
 * ({@code org.globalplatform.GPSystem.getSecureChannel()}) opens its secure channel with these keys, so a test that
 * talks to such an applet needs them on the host side, whichever backend it runs on. Tests declare the key set as a
 * parameter:
 *
 * <pre>{@code
 * @Test
 * void acceptsTheValueUnderASecureChannel(SmartCardSession card, SecurityDomainKeys keys) {
 *     try (GPSession channel = GPSession.on(card).keys(SCPKeys.of(keys)).open()) {      // javacard-express-gp
 *         assertThat(channel.send(0x80, 0x10, 0, 0, value)).isSuccess();
 *     }
 * }
 * }</pre>
 *
 * <p>The simulated cards (the default backend and {@code simulated-gp}) have the well-known GlobalPlatform test key
 * {@code 404142...4F} for Key-ENC, Key-MAC and Key-DEK; on a card in a reader ({@code livecard}) the keys are those
 * of the live-card settings. Key values never appear in {@link #toString()}.</p>
 */
public final class SecurityDomainKeys {

    private final byte[] enc;
    private final byte[] mac;
    private final byte[] dek;

    /**
     * Creates a key set.
     *
     * @param enc the static secure channel encryption key (Key-ENC)
     * @param mac the static secure channel message authentication code key (Key-MAC)
     * @param dek the data encryption key (Key-DEK)
     */
    public SecurityDomainKeys(byte[] enc, byte[] mac, byte[] dek) {
        this.enc = enc.clone();
        this.mac = mac.clone();
        this.dek = dek.clone();
    }

    /**
     * Returns the static secure channel encryption key.
     *
     * @return a copy of Key-ENC
     */
    public byte[] enc() {
        return enc.clone();
    }

    /**
     * Returns the static secure channel message authentication code key.
     *
     * @return a copy of Key-MAC
     */
    public byte[] mac() {
        return mac.clone();
    }

    /**
     * Returns the data encryption key.
     *
     * @return a copy of Key-DEK
     */
    public byte[] dek() {
        return dek.clone();
    }

    /** Describes the key set without revealing key values. */
    @Override
    public String toString() {
        return "SecurityDomainKeys[" + enc.length * 8 + "-bit keys]";
    }
}
