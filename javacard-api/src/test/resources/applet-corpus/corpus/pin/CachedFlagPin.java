package corpus.pin;

import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;

/**
 * Corpus class: an {@code OwnerPIN} subclass that keeps the validated flag in transient memory through the
 * protected hooks of the 3.0.5 API.
 */
public class CachedFlagPin extends OwnerPIN {

    private final boolean[] flag;

    public CachedFlagPin(byte tryLimit, byte maxPinSize) {
        super(tryLimit, maxPinSize);
        flag = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    }

    protected boolean getValidatedFlag() {
        return flag[0];
    }

    protected void setValidatedFlag(boolean value) {
        flag[0] = value;
    }
}
