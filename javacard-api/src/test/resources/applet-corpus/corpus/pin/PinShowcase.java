package corpus.pin;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPINBuilder;
import javacard.framework.OwnerPINx;
import javacard.framework.OwnerPINxWithPredecrement;
import javacard.framework.SensitiveArrays;
import javacard.framework.UserException;
import javacardx.security.SensitiveResult;

/**
 * Corpus applet: PIN handling and system services of Java Card 3.0.5 (OwnerPINx with pre-decrement,
 * integrity-sensitive arrays, global arrays, 32-bit memory queries, checked user exceptions).
 */
public class PinShowcase extends Applet {

    private static final byte INS_VERIFY = 0x20;
    private static final byte INS_CHANGE = 0x24;

    private final OwnerPINxWithPredecrement pin;
    private final OwnerPINx adminPin;
    private final CachedFlagPin legacyPin;
    private final Object counters;
    private final short[] memory = new short[2];

    private PinShowcase() {
        pin = (OwnerPINxWithPredecrement) OwnerPINBuilder.buildOwnerPIN((byte) 3, (byte) 8,
                OwnerPINBuilder.OWNER_PIN_X_WITH_PREDECREMENT);
        adminPin = (OwnerPINx) OwnerPINBuilder.buildOwnerPIN((byte) 5, (byte) 16, OwnerPINBuilder.OWNER_PIN_X);
        legacyPin = new CachedFlagPin((byte) 3, (byte) 8);
        counters = SensitiveArrays.isIntegritySensitiveArraysSupported()
                ? SensitiveArrays.makeIntegritySensitiveArray(JCSystem.ARRAY_TYPE_SHORT,
                        JCSystem.MEMORY_TYPE_PERSISTENT, (short) 4)
                : new short[4];
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PinShowcase();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        try {
            dispatch(buf, len);
        } catch (UserException e) {
            ISOException.throwIt((short) (ISO7816.SW_CONDITIONS_NOT_SATISFIED + e.getReason()));
        }
    }

    private void dispatch(byte[] buf, short len) throws UserException {
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_VERIFY:
                verify(buf, (byte) len);
                break;
            case INS_CHANGE:
                adminPin.update(buf, ISO7816.OFFSET_CDATA, (byte) len);
                adminPin.setTriesRemaining(adminPin.getTryLimit());
                break;
            default:
                UserException.throwIt((short) 1);
        }
    }

    private void verify(byte[] buf, byte len) {
        if (pin.decrementTriesRemaining() == 0) {
            ISOException.throwIt(ISO7816.SW_FILE_INVALID);
        }
        boolean ok = pin.check(buf, ISO7816.OFFSET_CDATA, len);
        if (ok) {
            SensitiveResult.assertTrue();
        }
        JCSystem.getAvailableMemory(memory, (short) 0, JCSystem.MEMORY_TYPE_PERSISTENT);
        byte[] shared = (byte[]) JCSystem.makeGlobalArray(JCSystem.ARRAY_TYPE_BYTE, (short) 16);
        AID self = JCSystem.getAID();
        self.getPartialBytes((short) 0, shared, (short) 0, (byte) 5);
        if (!JCSystem.isAppletActive(self) || JCSystem.getUnusedCommitCapacity() < 0 || legacyPin.isValidated()) {
            SensitiveArrays.assertIntegrity(counters);
            SensitiveArrays.clearArray(counters);
        }
    }
}
