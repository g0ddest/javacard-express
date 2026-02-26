package name.velikodniy.jcexpress.scp;

/**
 * GlobalPlatform constants for Secure Channel Protocol operations.
 *
 * <h3>CLA bytes:</h3>
 * <ul>
 *   <li>{@link #CLA_GP} — standard GP commands (0x80)</li>
 *   <li>{@link #CLA_GP_SECURE} — GP commands with C-MAC (0x84)</li>
 * </ul>
 *
 * <h3>INS bytes:</h3>
 * <ul>
 *   <li>{@link #INS_INITIALIZE_UPDATE} — begin SCP authentication (0x50)</li>
 *   <li>{@link #INS_EXTERNAL_AUTHENTICATE} — complete authentication (0x82)</li>
 *   <li>{@link #INS_GET_STATUS} — query card content (0xF2)</li>
 *   <li>{@link #INS_SET_STATUS} — set lifecycle state (0xF0)</li>
 *   <li>{@link #INS_INSTALL} — install/load/personalize (0xE6)</li>
 *   <li>{@link #INS_LOAD} — upload executable load file (0xE8)</li>
 *   <li>{@link #INS_DELETE} — remove content (0xE4)</li>
 *   <li>{@link #INS_PUT_KEY} — install/update keys (0xD8)</li>
 *   <li>{@link #INS_STORE_DATA} — send data to applet (0xE2)</li>
 * </ul>
 *
 * <h3>SCP02 key derivation constants:</h3>
 * <ul>
 *   <li>{@link #SCP02_DERIVE_C_MAC} — C-MAC session key derivation</li>
 *   <li>{@link #SCP02_DERIVE_R_MAC} — R-MAC session key derivation</li>
 *   <li>{@link #SCP02_DERIVE_ENC} — encryption session key derivation</li>
 *   <li>{@link #SCP02_DERIVE_DEK} — data encryption session key derivation</li>
 * </ul>
 *
 * <h3>SCP03 key derivation constants:</h3>
 * <ul>
 *   <li>{@link #SCP03_DERIVE_C_MAC} — C-MAC session key</li>
 *   <li>{@link #SCP03_DERIVE_R_MAC} — R-MAC session key</li>
 *   <li>{@link #SCP03_DERIVE_ENC} — encryption session key</li>
 *   <li>{@link #SCP03_DERIVE_CARD_CRYPTO} — card cryptogram derivation</li>
 *   <li>{@link #SCP03_DERIVE_HOST_CRYPTO} — host cryptogram derivation</li>
 * </ul>
 *
 * @see SCP02
 * @see SCP03
 */
public final class GP {

    private GP() {
    }

    // ── CLA ──

    /** GlobalPlatform CLA byte for standard commands. */
    public static final int CLA_GP = 0x80;

    /** GlobalPlatform CLA byte for commands with C-MAC. */
    public static final int CLA_GP_SECURE = 0x84;

    // ── INS ──

    /** INITIALIZE UPDATE command (begins SCP authentication). */
    public static final int INS_INITIALIZE_UPDATE = 0x50;

    /** EXTERNAL AUTHENTICATE command (completes authentication). */
    public static final int INS_EXTERNAL_AUTHENTICATE = 0x82;

    /** GET STATUS command. */
    public static final int INS_GET_STATUS = 0xF2;

    /** SET STATUS command. */
    public static final int INS_SET_STATUS = 0xF0;

    /** INSTALL command (install, load, personalize). */
    public static final int INS_INSTALL = 0xE6;

    /** LOAD command. */
    public static final int INS_LOAD = 0xE8;

    /** DELETE command. */
    public static final int INS_DELETE = 0xE4;

    /** PUT KEY command. */
    public static final int INS_PUT_KEY = 0xD8;

    /** STORE DATA command. */
    public static final int INS_STORE_DATA = 0xE2;

    /** GET DATA command (GP variant, CLA=0x80). */
    public static final int INS_GET_DATA = 0xCA;

    // ── INSTALL P1 values ──

    /** INSTALL [for load] P1. */
    public static final int INSTALL_FOR_LOAD = 0x02;

    /** INSTALL [for install] P1. */
    public static final int INSTALL_FOR_INSTALL = 0x04;

    /** INSTALL [for make selectable] P1. */
    public static final int INSTALL_FOR_MAKE_SELECTABLE = 0x08;

    /** INSTALL [for install and make selectable] P1. */
    public static final int INSTALL_FOR_INSTALL_AND_SELECTABLE = 0x0C;

    /** INSTALL [for extradition] P1 — transfer applet to another Security Domain. */
    public static final int INSTALL_FOR_EXTRADITION = 0x10;

    /** INSTALL [for personalization] P1 — personalize a Security Domain. */
    public static final int INSTALL_FOR_PERSONALIZATION = 0x20;

    /** INSTALL [for registry update] P1 — change applet privileges (GP 2.2+). */
    public static final int INSTALL_FOR_REGISTRY_UPDATE = 0x40;

    // ── SCP02 key derivation constants ──

    /** SCP02 derivation constant for C-MAC session key. */
    public static final byte[] SCP02_DERIVE_C_MAC = {0x01, 0x01};

    /** SCP02 derivation constant for R-MAC session key. */
    public static final byte[] SCP02_DERIVE_R_MAC = {0x01, 0x02};

    /** SCP02 derivation constant for encryption session key. */
    public static final byte[] SCP02_DERIVE_ENC = {0x01, (byte) 0x82};

    /** SCP02 derivation constant for data encryption session key. */
    public static final byte[] SCP02_DERIVE_DEK = {0x01, (byte) 0x81};

    // ── SCP03 key derivation constants ──

    /** SCP03 derivation constant for card cryptogram generation. */
    public static final byte SCP03_DERIVE_CARD_CRYPTO = 0x00;

    /** SCP03 derivation constant for host cryptogram generation. */
    public static final byte SCP03_DERIVE_HOST_CRYPTO = 0x01;

    /** SCP03 derivation constant for C-MAC session key. */
    public static final byte SCP03_DERIVE_C_MAC = 0x06;

    /** SCP03 derivation constant for R-MAC session key. */
    public static final byte SCP03_DERIVE_R_MAC = 0x07;

    /** SCP03 derivation constant for encryption session key. */
    public static final byte SCP03_DERIVE_ENC = 0x04;

    /** SCP03 derivation constant for DEK session key. */
    public static final byte SCP03_DERIVE_DEK = 0x05;

    // ── Security levels ──

    /** No secure messaging. */
    public static final int SECURITY_NONE = 0x00;

    /** C-MAC only. */
    public static final int SECURITY_C_MAC = 0x01;

    /** C-MAC and C-DECRYPTION (command encryption). */
    public static final int SECURITY_C_MAC_C_ENC = 0x03;

    /** C-MAC and R-MAC (without C-ENC). */
    public static final int SECURITY_C_MAC_R_MAC = 0x11;

    /** C-MAC, C-DECRYPTION, and R-MAC. */
    public static final int SECURITY_C_MAC_C_ENC_R_MAC = 0x13;

    /** C-MAC, C-DECRYPTION, R-MAC, and R-ENCRYPTION (full protection). */
    public static final int SECURITY_C_MAC_C_ENC_R_MAC_R_ENC = 0x33;

    // ── SCP03 implementation options (i parameter) ──

    /** SCP03 option: pseudo-random card challenge (i=60). */
    public static final int SCP03_I60 = 0x60;

    /** SCP03 option: explicit card challenge (i=70, default). */
    public static final int SCP03_I70 = 0x70;
}
