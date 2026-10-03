package name.velikodniy.jcexpress.memory;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Applet that reports the memory available on the card.
 *
 * <p>Install it next to the applets under test and compare two measurements (see
 * {@link name.velikodniy.jcexpress.assertions.MemoryInfoAssert#persistentConsumedAtMost}).</p>
 *
 * <p>Command (CLA '80'): <b>INS '01' (GET MEMORY)</b> returns 12 bytes, three big-endian 32-bit amounts in
 * bytes: available persistent memory, available {@code CLEAR_ON_DESELECT} transient memory and available
 * {@code CLEAR_ON_RESET} transient memory.</p>
 *
 * <p>The amounts come from {@code JCSystem.getAvailableMemory(short[] buffer, short offset, byte memoryType)}
 * (Java Card API 3.0.4 and later), which returns the amount as a 32-bit number in two shorts. The older
 * {@code getAvailableMemory(byte)} cannot be used: it returns 32767 whenever more than 32767 bytes are
 * available, which is the case on most cards. The probe therefore needs a Java Card 3.0.4 (or later)
 * platform. A platform that does not write the amount leaves the buffer unchanged; the probe then returns
 * {@code 'FFFFFFFF'} ({@link MemoryInfo#NOT_REPORTED}) for that memory type, and {@link MemoryInfo#from}
 * reports it. jCardSim, which the embedded and container backends run, does not model memory and reports
 * nothing.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * card.install(MemoryProbeApplet.class);
 * MemoryInfo before = MemoryInfo.from(card.send(0x80, 0x01));
 * card.install(MyApplet.class);
 * card.select(MemoryProbeApplet.class);
 * MemoryInfo after = MemoryInfo.from(card.send(0x80, 0x01));
 * assertThat(after).persistentConsumedAtMost(before, 4096);
 * </pre>
 *
 * @see MemoryInfo
 */
public class MemoryProbeApplet extends Applet {

    private static final byte INS_GET_MEMORY = 0x01;

    /** Half of the 'FFFFFFFF' marker written before each query (no platform reports 4 GB available). */
    private static final short NOT_REPORTED_HALF = (short) 0xFFFF;

    private final short[] amount;

    private MemoryProbeApplet() {
        amount = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
    }

    /**
     * Installs the probe.
     *
     * @param bArray  the installation parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MemoryProbeApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != INS_GET_MEMORY) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        putAvailable(buffer, (short) 0, JCSystem.MEMORY_TYPE_PERSISTENT);
        putAvailable(buffer, (short) 4, JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT);
        putAvailable(buffer, (short) 8, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
        apdu.setOutgoingAndSend((short) 0, (short) 12);
    }

    /** Writes the 32-bit amount of available memory of a type, or 'FFFFFFFF' if the platform reports none. */
    private void putAvailable(byte[] buffer, short offset, byte memoryType) {
        amount[0] = NOT_REPORTED_HALF;
        amount[1] = NOT_REPORTED_HALF;
        JCSystem.getAvailableMemory(amount, (short) 0, memoryType);
        Util.setShort(buffer, offset, amount[0]);
        Util.setShort(buffer, (short) (offset + 2), amount[1]);
    }
}
