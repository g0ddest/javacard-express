package name.velikodniy.jcexpress.memory;

import name.velikodniy.jcexpress.APDUResponse;

/**
 * Memory usage information returned by {@link MemoryProbeApplet}.
 *
 * <p>Contains available memory for three memory types:</p>
 * <ul>
 *   <li><b>persistent</b> — EEPROM/Flash, survives card reset</li>
 *   <li><b>transientDeselect</b> — RAM cleared when applet is deselected</li>
 *   <li><b>transientReset</b> — RAM cleared only on card reset</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * card.install(MemoryProbeApplet.class);
 * APDUResponse response = card.send(0x80, 0x01);
 * MemoryInfo info = MemoryInfo.from(response);
 * System.out.println("Free EEPROM: " + info.persistent());
 * System.out.println("Free RAM (deselect): " + info.transientDeselect());
 * System.out.println("Free RAM (reset): " + info.transientReset());
 * </pre>
 *
 * @param persistent         available persistent (EEPROM) memory in bytes
 * @param transientDeselect  available transient CLEAR_ON_DESELECT memory in bytes
 * @param transientReset     available transient CLEAR_ON_RESET memory in bytes
 */
public record MemoryInfo(int persistent, int transientDeselect, int transientReset) {

    /**
     * Parses a {@link MemoryProbeApplet} response into a {@link MemoryInfo}.
     *
     * <p>Expects a successful response with exactly 12 bytes of data:
     * 3 big-endian 32-bit integers.</p>
     *
     * @param response the APDU response from MemoryProbeApplet (INS=0x01)
     * @return parsed memory info
     * @throws IllegalArgumentException if the response is not successful or has wrong data length
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
        return new MemoryInfo(
                readInt(data, 0),
                readInt(data, 4),
                readInt(data, 8)
        );
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
