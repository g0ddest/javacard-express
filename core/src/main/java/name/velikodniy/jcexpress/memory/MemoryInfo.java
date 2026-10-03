package name.velikodniy.jcexpress.memory;

import name.velikodniy.jcexpress.APDUResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory available on a card, as reported by {@link MemoryProbeApplet}.
 *
 * <p>Contains available memory for three memory types:</p>
 * <ul>
 *   <li><b>persistent</b> &mdash; EEPROM/Flash, survives card reset</li>
 *   <li><b>transientDeselect</b> &mdash; RAM cleared when the applet is deselected</li>
 *   <li><b>transientReset</b> &mdash; RAM cleared on card reset</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * card.install(MemoryProbeApplet.class);
 * MemoryInfo info = MemoryInfo.from(card.send(0x80, 0x01));
 * System.out.println("Free EEPROM: " + info.persistent());
 * </pre>
 *
 * @param persistent        available persistent (EEPROM) memory in bytes
 * @param transientDeselect available transient CLEAR_ON_DESELECT memory in bytes
 * @param transientReset    available transient CLEAR_ON_RESET memory in bytes
 */
public record MemoryInfo(int persistent, int transientDeselect, int transientReset) {

    /** Value ('FFFFFFFF') the probe returns for a memory type the platform does not report. */
    public static final int NOT_REPORTED = -1;

    /**
     * Parses a {@link MemoryProbeApplet} response into a {@link MemoryInfo}.
     *
     * <p>Expects a successful response with exactly 12 bytes of data: three big-endian 32-bit
     * integers.</p>
     *
     * @param response the APDU response from MemoryProbeApplet (INS=0x01)
     * @return parsed memory info
     * @throws IllegalArgumentException      if the response is not successful or has wrong data length
     * @throws UnsupportedOperationException if the card did not report a memory type (see
     *                                       {@link MemoryProbeApplet}); jCardSim reports none
     */
    public static MemoryInfo from(APDUResponse response) {
        if (!response.isSuccess()) {
            throw new IllegalArgumentException(
                    "Expected successful response, got SW=" + String.format("%04X", response.sw()));
        }
        byte[] data = response.data();
        if (data.length != 12) {
            throw new IllegalArgumentException(
                    "Expected 12 bytes of memory data, got " + data.length);
        }
        MemoryInfo info = new MemoryInfo(readInt(data, 0), readInt(data, 4), readInt(data, 8));
        List<String> missing = info.notReported();
        if (!missing.isEmpty()) {
            throw new UnsupportedOperationException("The card does not report available memory for " + missing
                    + ": JCSystem.getAvailableMemory(short[], short, byte) (Java Card 3.0.4+) left the probe's"
                    + " buffer unchanged. jCardSim, which runs the embedded and container backends, does not"
                    + " model memory; measure on a card or simulator that implements the method");
        }
        return info;
    }

    private List<String> notReported() {
        List<String> missing = new ArrayList<>();
        if (persistent == NOT_REPORTED) {
            missing.add("persistent");
        }
        if (transientDeselect == NOT_REPORTED) {
            missing.add("transientDeselect");
        }
        if (transientReset == NOT_REPORTED) {
            missing.add("transientReset");
        }
        return missing;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    @Override
    public String toString() {
        return String.format("MemoryInfo[persistent=%d, transientDeselect=%d, transientReset=%d]",
                persistent, transientDeselect, transientReset);
    }
}
