package name.velikodniy.jcexpress.pace;

/**
 * PACE password references, sent in tag {@code 0x83} of MSE:Set AT.
 *
 * <p>MRZ ({@code 0x01}) and CAN ({@code 0x02}) are defined by ICAO Doc 9303-11, 4.4.4.1; PIN ({@code 0x03}) and
 * PUK ({@code 0x04}) by BSI TR-03110 for eID cards.</p>
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
