package name.velikodniy.jcexpress;

/**
 * Encodes the installation parameters that a Java Card runtime passes to an applet's
 * {@code install(byte[] bArray, short bOffset, byte bLength)} method.
 *
 * <p>Java Card API ({@code javacard.framework.Applet.install}, 3.0.5 and 3.1): the parameters are the
 * consecutive length-value fields</p>
 * <pre>
 * [Li][instance AID][Lc][control info][La][applet data]
 * </pre>
 * <p>where any of Li, Lc and La may be zero, the control information is implementation dependent, and
 * {@code bLength} (the total) is at most {@value #MAX_LENGTH}. On GlobalPlatform cards the control
 * information carries the Privileges and the applet data is the value of the application specific
 * parameters (tag 'C9') of INSTALL [for install] (GlobalPlatform Card Specification 2.3.1, A.1
 * "GlobalPlatform on a Java Card", Installation).</p>
 *
 * <p>Applets following the canonical pattern
 * {@code register(bArray, (short) (bOffset + 1), bArray[bOffset])} register under the instance AID found
 * in this structure.</p>
 */
public final class AppletInstallParameters {

    /** Maximum total length of the installation parameters ({@code bLength}), Java Card API. */
    public static final int MAX_LENGTH = 127;

    /** Bytes taken by the three length fields Li, Lc and La. */
    private static final int LENGTH_FIELDS = 3;

    private AppletInstallParameters() {
    }

    /**
     * Encodes installation parameters without control information.
     *
     * @param instanceAid the AID the applet instance is installed under
     * @param appletData  the applet specific data (La part; may be null or empty)
     * @return {@code [Li][AID][00][La][appletData]}
     * @throws IllegalArgumentException if the result would exceed {@value #MAX_LENGTH} bytes
     */
    public static byte[] encode(AID instanceAid, byte[] appletData) {
        return encode(instanceAid, new byte[0], appletData);
    }

    /**
     * Encodes installation parameters.
     *
     * @param instanceAid the AID the applet instance is installed under
     * @param controlInfo the implementation dependent control information (Lc part; may be null or empty)
     * @param appletData  the applet specific data (La part; may be null or empty)
     * @return {@code [Li][AID][Lc][controlInfo][La][appletData]}
     * @throws IllegalArgumentException if the result would exceed {@value #MAX_LENGTH} bytes
     */
    public static byte[] encode(AID instanceAid, byte[] controlInfo, byte[] appletData) {
        if (instanceAid == null) {
            throw new IllegalArgumentException("Instance AID must not be null");
        }
        byte[] aid = instanceAid.toBytes();
        byte[] control = controlInfo != null ? controlInfo : new byte[0];
        byte[] data = appletData != null ? appletData : new byte[0];
        int total = LENGTH_FIELDS + aid.length + control.length + data.length;
        if (total > MAX_LENGTH) {
            throw new IllegalArgumentException("Install parameters too long: " + total + " bytes, the Java Card"
                    + " API limits Applet.install bLength to " + MAX_LENGTH + "; with a " + aid.length
                    + "-byte AID and " + control.length + " bytes of control information at most "
                    + maxAppletDataLength(instanceAid, control.length) + " bytes of applet data fit, got "
                    + data.length);
        }
        byte[] out = new byte[total];
        int offset = put(out, 0, aid);
        offset = put(out, offset, control);
        put(out, offset, data);
        return out;
    }

    /**
     * Returns how many bytes of applet data fit next to the given AID and control information.
     *
     * @param instanceAid       the instance AID
     * @param controlInfoLength the length of the control information
     * @return the maximum applet data length (may be negative if nothing fits)
     */
    public static int maxAppletDataLength(AID instanceAid, int controlInfoLength) {
        return MAX_LENGTH - LENGTH_FIELDS - instanceAid.toBytes().length - controlInfoLength;
    }

    private static int put(byte[] out, int offset, byte[] value) {
        out[offset] = (byte) value.length;
        System.arraycopy(value, 0, out, offset + 1, value.length);
        return offset + 1 + value.length;
    }
}
