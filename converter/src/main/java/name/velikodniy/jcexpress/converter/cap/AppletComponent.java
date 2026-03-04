package name.velikodniy.jcexpress.converter.cap;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates the CAP Applet component (tag 3) as defined in JCVM 3.0.5 spec section 6.5.
 *
 * <p>The Applet component is present only in packages that contain one or more applets
 * (indicated by {@link HeaderComponent#ACC_APPLET} in the Header flags). It enumerates
 * every applet in the package by listing its AID (Application Identifier) and the byte
 * offset of its {@code install()} method within the Method component.
 *
 * <p>When the JCRE processes an INSTALL [for install] APDU, it looks up the applet's
 * AID in this component and jumps to the corresponding {@code install_method_offset}
 * in the Method component to invoke the applet's static {@code install()} factory method.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.5, Table 6-4):
 * <pre>
 * u1  tag = 3
 * u2  size
 * u1  count                    (number of applets)
 * applet_info[count]:
 *   u1  AID_length             (5-16)
 *   u1  AID[AID_length]        (applet AID bytes)
 *   u2  install_method_offset  (byte offset into Method component info area)
 * </pre>
 *
 * @see MethodComponent
 */
public final class AppletComponent {

    public static final int TAG = 3;

    private AppletComponent() {}

    /**
     * An applet entry with its AID and install method offset.
     *
     * @param aid                  applet AID (5-16 bytes)
     * @param installMethodOffset  offset of the install() method in the Method component
     */
    public record AppletEntry(byte[] aid, int installMethodOffset) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof AppletEntry(var a, var imo)) {
                return installMethodOffset == imo && Arrays.equals(aid, a);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(aid) + Integer.hashCode(installMethodOffset);
        }

        @Override
        public String toString() {
            return "AppletEntry[aid=" + HexFormat.of().formatHex(aid)
                    + ", installMethodOffset=" + installMethodOffset + "]";
        }
    }

    /**
     * Generates the Applet component bytes.
     *
     * @param applets list of applet entries
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(List<AppletEntry> applets) {
        // --- applet_component (§6.5 Table 6-4) ---
        var info = new BinaryWriter();
        info.u1(applets.size()); // §6.5: u1 count (number of applets)

        for (AppletEntry entry : applets) {
            // --- applet_info (§6.5 Table 6-4) ---
            info.aidWithLength(entry.aid());        // §6.5: u1 AID_length + u1[] AID
            info.u2(entry.installMethodOffset());   // §6.5: u2 install_method_offset
        }

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }
}
