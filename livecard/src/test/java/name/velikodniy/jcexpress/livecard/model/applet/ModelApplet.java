package name.velikodniy.jcexpress.livecard.model.applet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Applet of the declarative-model tests that run on every backend (CLA 80): an instance counter, the number of
 * instances created since the package was loaded (a static field), the install parameters, a CLEAR_ON_DESELECT
 * byte and the own AID. Java Card subset only: the GlobalPlatform backends convert it from the test classes
 * (compiled for Java 25; the converter accepts any class file version for code within the subset).
 *
 * <pre>
 * INS 30  counter + 1, answers the counter (2 bytes)       INS 33  answers the install parameters
 * INS 31  answers the counter (2 bytes)                    INS 34  stores P1 in the CLEAR_ON_DESELECT byte
 * INS 32  answers the instances created (2 bytes)          INS 35  answers the CLEAR_ON_DESELECT byte
 *                                                          INS 36  answers the instance AID
 * </pre>
 */
public class ModelApplet extends Applet {

    private static short created;

    private final byte[] parameters;
    private final byte[] transientByte;
    private short counter;

    private ModelApplet(byte[] bArray, short bOffset) {
        short infoOffset = (short) (bOffset + 1 + bArray[bOffset]);
        short dataOffset = (short) (infoOffset + 1 + bArray[infoOffset]);
        parameters = new byte[bArray[dataOffset]];
        Util.arrayCopyNonAtomic(bArray, (short) (dataOffset + 1), parameters, (short) 0, (short) parameters.length);
        transientByte = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        created++;
        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    /**
     * Installs an instance (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ModelApplet(bArray, bOffset);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];
        if (ins == 0x30) {
            counter++;
            sendShort(apdu, counter);
        } else if (ins == 0x31) {
            sendShort(apdu, counter);
        } else if (ins == 0x32) {
            sendShort(apdu, created);
        } else if (ins == 0x33) {
            Util.arrayCopyNonAtomic(parameters, (short) 0, buffer, (short) 0, (short) parameters.length);
            apdu.setOutgoingAndSend((short) 0, (short) parameters.length);
        } else if (ins == 0x34) {
            transientByte[0] = buffer[ISO7816.OFFSET_P1];
        } else if (ins == 0x35) {
            buffer[0] = transientByte[0];
            apdu.setOutgoingAndSend((short) 0, (short) 1);
        } else if (ins == 0x36) {
            short length = JCSystem.getAID().getBytes(buffer, (short) 0);
            apdu.setOutgoingAndSend((short) 0, length);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private static void sendShort(APDU apdu, short value) {
        Util.setShort(apdu.getBuffer(), (short) 0, value);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
