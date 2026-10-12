package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;

import java.util.Arrays;

/**
 * An application in the GlobalPlatform Registry of a simulated card: its AID, life cycle state and privileges
 * (GlobalPlatform Card Specification v2.3.1 section 6.6; coding in 11.1.1 and 11.1.2). The applet sees and
 * changes them through {@code org.globalplatform.GPSystem} and {@code GPRegistryEntry} under the rules of the
 * API; a test or a card simulation reads and sets them here directly, as the card's own management does (SET
 * STATUS, INSTALL).
 *
 * <pre>{@code
 * CardApplication wallet = card.globalPlatform().application(walletAid).orElseThrow();
 * wallet.grant(GPRegistryEntry.PRIVILEGE_CARD_LOCK);
 * assertThat(wallet.state()).isEqualTo(0x0F);     // the applet moved itself to an application specific state
 * }</pre>
 */
public final class CardApplication {

    /** Application life cycle state INSTALLED (GPCS v2.3.1 Table 11-4). */
    public static final int INSTALLED = 0x03;
    /** Application life cycle state SELECTABLE (GPCS v2.3.1 Table 11-4). */
    public static final int SELECTABLE = 0x07;
    /** Application life cycle state bit LOCKED (GPCS v2.3.1 Table 11-4). */
    public static final int LOCKED = 0x80;

    private static final int PRIVILEGE_BYTES = 3;

    private final AID aid;
    private final byte[] privileges = new byte[PRIVILEGE_BYTES];
    private int state;

    CardApplication(AID aid, int state, byte[] privileges) {
        this.aid = aid;
        state(state);
        privileges(privileges);
    }

    /**
     * Returns the AID of the application.
     *
     * @return the instance AID
     */
    public AID aid() {
        return aid;
    }

    /**
     * Returns the life cycle state.
     *
     * @return the state byte as an unsigned value (GPCS v2.3.1 Table 11-4)
     */
    public int state() {
        return state;
    }

    /**
     * Sets the life cycle state without the checks the applet's own requests go through.
     *
     * @param state the state byte, 0 to 255
     * @return this application
     * @throws IllegalArgumentException if the value is not a byte
     */
    public CardApplication state(int state) {
        if (state < 0 || state > 0xFF) {
            throw new IllegalArgumentException("A life cycle state is one byte, got " + state);
        }
        this.state = state;
        return this;
    }

    /**
     * Tells whether the application is locked.
     *
     * @return true when b8 of the state is set
     */
    public boolean locked() {
        return (state & LOCKED) != 0;
    }

    /**
     * Tells whether the application can be selected: SELECTABLE or an application specific state, not locked.
     *
     * @return true when b8 is clear and the three low bits are set
     */
    public boolean selectable() {
        return (state & (LOCKED | SELECTABLE)) == SELECTABLE;
    }

    /**
     * Returns the privileges.
     *
     * @return a copy of the three privilege bytes (GPCS v2.3.1 Tables 11-7 to 11-9)
     */
    public byte[] privileges() {
        return privileges.clone();
    }

    /**
     * Sets the privileges.
     *
     * @param bytes the privilege bytes as INSTALL carries them: none, one or three; missing bytes are zero
     * @return this application
     * @throws IllegalArgumentException if there are more than three bytes
     */
    public CardApplication privileges(byte[] bytes) {
        if (bytes.length > PRIVILEGE_BYTES) {
            throw new IllegalArgumentException("Privileges are at most three bytes (GPCS v2.3.1 11.1.2), got "
                    + bytes.length);
        }
        Arrays.fill(privileges, (byte) 0);
        System.arraycopy(bytes, 0, privileges, 0, bytes.length);
        return this;
    }

    /**
     * Tells whether the application has a privilege. Privilege number n is bit b(8 - n mod 8) of privilege byte
     * n / 8 + 1; the combined codings of byte 1 (DAP Verification, Delegated Management, Mandated DAP
     * Verification) are not interpreted.
     *
     * @param number the privilege number (GPCS v2.3.1 Tables 11-7 to 11-9; the {@code PRIVILEGE} constants of
     *               {@code org.globalplatform.GPRegistryEntry})
     * @return true when the bit is set; false for a number outside the three bytes
     */
    public boolean hasPrivilege(int number) {
        return number >= 0 && number < PRIVILEGE_BYTES * 8 && (privileges[number / 8] & (0x80 >> (number % 8))) != 0;
    }

    /**
     * Grants privileges.
     *
     * @param numbers privilege numbers, 0 to 23
     * @return this application
     * @throws IllegalArgumentException if a number is outside the three privilege bytes
     */
    public CardApplication grant(int... numbers) {
        for (int number : numbers) {
            privileges[byteOf(number)] |= (byte) (0x80 >> (number % 8));
        }
        return this;
    }

    /**
     * Takes privileges away.
     *
     * @param numbers privilege numbers, 0 to 23
     * @return this application
     * @throws IllegalArgumentException if a number is outside the three privilege bytes
     */
    public CardApplication revoke(int... numbers) {
        for (int number : numbers) {
            privileges[byteOf(number)] &= (byte) ~(0x80 >> (number % 8));
        }
        return this;
    }

    private static int byteOf(int number) {
        if (number < 0 || number >= PRIVILEGE_BYTES * 8) {
            throw new IllegalArgumentException("Privilege numbers are 0 to 23 (GPCS v2.3.1 Tables 11-7 to 11-9),"
                    + " got " + number);
        }
        return number / 8;
    }

    @Override
    public String toString() {
        return String.format("%s in state %02X with privileges %02X%02X%02X", aid.toHex(), state, privileges[0],
                privileges[1], privileges[2]);
    }
}
