package name.velikodniy.jcexpress.pace;

/**
 * PACE password reference types (BSI TR-03110, Section 3).
 *
 * <p>Identifies which shared secret is used for PACE authentication.
 * The reference value is encoded in tag 0x83 of MSE:Set AT.</p>
 */
public enum PasswordRef {

    /** Machine Readable Zone (MRZ) — document number, date of birth, date of expiry. */
    MRZ(0x01),

    /** Card Access Number (CAN) — printed on the card face. */
    CAN(0x02),

    /** Personal Identification Number (PIN). */
    PIN(0x03),

    /** PIN Unblocking Key (PUK). */
    PUK(0x04);

    private final int ref;

    PasswordRef(int ref) {
        this.ref = ref;
    }

    /**
     * Returns the password reference value for MSE:Set AT.
     *
     * @return the reference byte value
     */
    public int ref() {
        return ref;
    }
}
