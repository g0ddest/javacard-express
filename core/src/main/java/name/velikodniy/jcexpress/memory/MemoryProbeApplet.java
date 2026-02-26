package name.velikodniy.jcexpress.memory;

import javacard.framework.*;

/**
 * Utility applet for querying available memory on a JavaCard.
 *
 * <p>Install this applet alongside your applets under test to measure
 * memory consumption. It reports available persistent (EEPROM) and
 * transient (RAM) memory via APDU responses.</p>
 *
 * <p>Supported commands (CLA=0x80):</p>
 * <ul>
 *   <li><b>INS 0x01 (GET_MEMORY)</b>: returns 12 bytes:
 *     <ul>
 *       <li>Bytes 0-3: available persistent memory (EEPROM) — big-endian int</li>
 *       <li>Bytes 4-7: available transient memory (CLEAR_ON_DESELECT) — big-endian int</li>
 *       <li>Bytes 8-11: available transient memory (CLEAR_ON_RESET) — big-endian int</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Usage with JavaCard Express:</p>
 * <pre>
 * // Install your applet first to measure its footprint
 * card.install(MyApplet.class);
 *
 * // Then install the probe to measure remaining memory
 * card.install(MemoryProbeApplet.class);
 * card.select(MemoryProbeApplet.class);
 * APDUResponse response = card.send(0x80, 0x01);
 * MemoryInfo info = MemoryInfo.from(response);
 * System.out.println("Persistent: " + info.persistent() + " bytes free");
 * </pre>
 *
 * @see MemoryInfo
 */
public class MemoryProbeApplet extends Applet {

    private static final byte INS_GET_MEMORY = 0x01;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MemoryProbeApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];

        if (ins == INS_GET_MEMORY) {
            // Persistent (EEPROM)
            short persistent = JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_PERSISTENT);
            putShortAsInt(buffer, (short) 0, persistent);

            // Transient CLEAR_ON_DESELECT (RAM)
            short transientDeselect = JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT);
            putShortAsInt(buffer, (short) 4, transientDeselect);

            // Transient CLEAR_ON_RESET (RAM)
            short transientReset = JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
            putShortAsInt(buffer, (short) 8, transientReset);

            apdu.setOutgoingAndSend((short) 0, (short) 12);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /**
     * Writes a JavaCard short (16-bit) as a 32-bit big-endian int.
     * JavaCard's getAvailableMemory returns short, but we send as int
     * for forward compatibility with extended memory APIs.
     */
    private static void putShortAsInt(byte[] buffer, short offset, short value) {
        // Upper 2 bytes = 0 (short is unsigned in JavaCard context)
        buffer[offset] = 0;
        buffer[(short) (offset + 1)] = 0;
        buffer[(short) (offset + 2)] = (byte) ((value >> 8) & 0xFF);
        buffer[(short) (offset + 3)] = (byte) (value & 0xFF);
    }
}
