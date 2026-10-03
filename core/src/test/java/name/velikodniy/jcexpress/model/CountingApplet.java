package name.velikodniy.jcexpress.model;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Test applet that counts how often the runtime calls its {@code select()} and {@code deselect()} (static fields, so
 * the counts cover every instance since the package was loaded), keeps a CLEAR_ON_DESELECT byte and a persistent
 * counter, and answers SELECT with an FCI that names the instance (ISO/IEC 7816-4:2005 5.3.3: '6F' FCI template
 * with the DF name '84').
 *
 * <pre>
 * SELECT  answers 6F L 84 L instance AID
 * INS 10  answers the select() and deselect() counts (2 + 2 bytes)
 * INS 11  stores P1 in the CLEAR_ON_DESELECT byte; INS 12 answers it
 * INS 13  counter + 1 (persistent), answers it (2 bytes)
 * INS 16  answers the instance AID
 * </pre>
 */
public final class CountingApplet extends Applet {

    private static short selects;
    private static short deselects;

    private final byte[] transientByte = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    private short counter;

    private CountingApplet(byte[] bArray, short bOffset) {
        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    /**
     * Installs an instance under the AID of the install data (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CountingApplet(bArray, bOffset);
    }

    @Override
    public boolean select() {
        selects++;
        return true;
    }

    @Override
    public void deselect() {
        deselects++;
    }

    @Override
    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        if (selectingApplet()) {
            short length = JCSystem.getAID().getBytes(buffer, (short) 4);
            buffer[0] = 0x6F;
            buffer[1] = (byte) (length + 2);
            buffer[2] = (byte) 0x84;
            buffer[3] = (byte) length;
            apdu.setOutgoingAndSend((short) 0, (short) (length + 4));
            return;
        }
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x10 -> {
                Util.setShort(buffer, (short) 0, selects);
                Util.setShort(buffer, (short) 2, deselects);
                apdu.setOutgoingAndSend((short) 0, (short) 4);
            }
            case 0x11 -> transientByte[0] = buffer[ISO7816.OFFSET_P1];
            case 0x12 -> {
                buffer[0] = transientByte[0];
                apdu.setOutgoingAndSend((short) 0, (short) 1);
            }
            case 0x13 -> {
                counter++;
                Util.setShort(buffer, (short) 0, counter);
                apdu.setOutgoingAndSend((short) 0, (short) 2);
            }
            case 0x16 -> apdu.setOutgoingAndSend((short) 0, JCSystem.getAID().getBytes(buffer, (short) 0));
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
