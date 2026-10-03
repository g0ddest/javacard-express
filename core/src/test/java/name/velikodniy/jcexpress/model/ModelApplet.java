package name.velikodniy.jcexpress.model;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Test applet of the declarative model (CLA 80): an instance counter, a static count of the instances created
 * since the package was loaded, the install parameters, a CLEAR_ON_DESELECT byte and the own AID.
 *
 * <pre>
 * INS 10  counter + 1, answers the counter (2 bytes)
 * INS 11  answers the counter (2 bytes)
 * INS 12  answers the number of instances created since the package was loaded (2 bytes, a static field)
 * INS 13  answers the install parameters
 * INS 14  stores P1 in the CLEAR_ON_DESELECT byte; INS 15 answers it
 * INS 16  answers the instance AID
 * </pre>
 */
public final class ModelApplet extends Applet {

    private static short created;

    private final byte[] parameters;
    private final byte[] transientByte;
    private short counter;

    private ModelApplet(byte[] bArray, short bOffset, byte bLength) {
        short aidLength = bArray[bOffset];
        short infoOffset = (short) (bOffset + 1 + aidLength);
        short infoLength = bArray[infoOffset];
        short dataOffset = (short) (infoOffset + 1 + infoLength);
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
        new ModelApplet(bArray, bOffset, bLength);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x10 -> {
                counter++;
                sendShort(apdu, counter);
            }
            case 0x11 -> sendShort(apdu, counter);
            case 0x12 -> sendShort(apdu, created);
            case 0x13 -> send(apdu, parameters, (short) parameters.length);
            case 0x14 -> transientByte[0] = buffer[ISO7816.OFFSET_P1];
            case 0x15 -> send(apdu, transientByte, (short) 1);
            case 0x16 -> {
                short length = JCSystem.getAID().getBytes(buffer, (short) 0);
                apdu.setOutgoingAndSend((short) 0, length);
            }
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private static void sendShort(APDU apdu, short value) {
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, value);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private static void send(APDU apdu, byte[] data, short length) {
        Util.arrayCopyNonAtomic(data, (short) 0, apdu.getBuffer(), (short) 0, length);
        apdu.setOutgoingAndSend((short) 0, length);
    }
}
