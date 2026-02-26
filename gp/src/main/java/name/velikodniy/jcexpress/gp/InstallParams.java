package name.velikodniy.jcexpress.gp;

import java.io.ByteArrayOutputStream;

/**
 * Builds the data field for GlobalPlatform INSTALL commands.
 *
 * <p>Package-private utility used by {@link GPSession}. The INSTALL command
 * (INS=E6) has a complex data format with length-prefixed AID fields.</p>
 */
final class InstallParams {

    private InstallParams() {
    }

    /**
     * Builds INSTALL [for load] data (P1=0x02).
     *
     * <pre>
     * Data: len(pkgAID) || pkgAID || len(sdAID) || sdAID || hashLen(0) || paramsLen(0) || tokenLen(0)
     * </pre>
     *
     * @param packageAid the package AID bytes
     * @param sdAid      the security domain AID bytes (empty array = ISD)
     * @return the constructed data field
     */
    static byte[] forLoad(byte[] packageAid, byte[] sdAid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLengthPrefixed(out, packageAid);
        writeLengthPrefixed(out, sdAid);
        out.write(0); // hash length
        out.write(0); // parameters length
        out.write(0); // token length
        return out.toByteArray();
    }

    /**
     * Builds INSTALL [for install and make selectable] data (P1=0x0C).
     *
     * <pre>
     * Data: len(pkgAID) || pkgAID || len(modAID) || modAID || len(instAID) || instAID
     *       || 01 || privileges || paramsLen || [C9 len params] || 00 (token)
     * </pre>
     *
     * @param packageAid    the package AID bytes
     * @param moduleAid     the module (applet class) AID bytes
     * @param instanceAid   the instance AID bytes
     * @param privileges    the privilege byte
     * @param installParams the install parameters (may be null or empty)
     * @return the constructed data field
     */
    static byte[] forInstall(byte[] packageAid, byte[] moduleAid, byte[] instanceAid,
                             int privileges, byte[] installParams) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLengthPrefixed(out, packageAid);
        writeLengthPrefixed(out, moduleAid);
        writeLengthPrefixed(out, instanceAid);

        // Privileges field: 1 byte length + privileges byte
        out.write(1);
        out.write(privileges);

        // Install parameters: wrapped in C9 TLV
        if (installParams != null && installParams.length > 0) {
            int paramsFieldLen = 2 + installParams.length; // C9 tag + len + data
            out.write(paramsFieldLen);
            out.write(0xC9);
            out.write(installParams.length);
            out.write(installParams, 0, installParams.length);
        } else {
            out.write(2);  // parameters field length
            out.write(0xC9);
            out.write(0);  // empty C9 TLV
        }

        // Token length
        out.write(0);
        return out.toByteArray();
    }

    /**
     * Builds DELETE command data.
     *
     * <pre>
     * Data: 4F || len || AID bytes
     * </pre>
     *
     * @param aid the AID bytes to delete
     * @return the constructed data field
     */
    static byte[] forDelete(byte[] aid) {
        byte[] data = new byte[2 + aid.length];
        data[0] = 0x4F; // tag
        data[1] = (byte) aid.length;
        System.arraycopy(aid, 0, data, 2, aid.length);
        return data;
    }

    /**
     * Builds INSTALL [for extradition] data (P1=0x10).
     *
     * <p>Transfers an applet from one Security Domain to another.</p>
     *
     * <pre>
     * Data: len(sdAID) || sdAID || 00 || len(appAID) || appAID || 00 || paramsLen(0) || tokenLen(0)
     * </pre>
     *
     * @param sdAid     the target Security Domain AID bytes
     * @param appletAid the applet AID bytes to extradite
     * @return the constructed data field
     */
    static byte[] forExtradition(byte[] sdAid, byte[] appletAid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLengthPrefixed(out, sdAid);
        out.write(0); // empty executable load file AID
        writeLengthPrefixed(out, appletAid);
        out.write(0); // empty privileges
        out.write(0); // parameters length
        out.write(0); // token length
        return out.toByteArray();
    }

    /**
     * Builds INSTALL [for personalization] data (P1=0x20).
     *
     * <p>Sends personalization data to a Security Domain.</p>
     *
     * <pre>
     * Data: 00 || 00 || len(appAID) || appAID || 00 || paramsLen(0) || tokenLen(0)
     * </pre>
     *
     * @param appletAid the Security Domain AID bytes to personalize
     * @return the constructed data field
     */
    static byte[] forPersonalization(byte[] appletAid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); // empty executable load file AID
        out.write(0); // empty executable module AID
        writeLengthPrefixed(out, appletAid);
        out.write(0); // empty privileges
        out.write(0); // parameters length
        out.write(0); // token length
        return out.toByteArray();
    }

    /**
     * Builds INSTALL [for registry update] data (P1=0x40).
     *
     * <p>Updates the privileges of an installed applet (GP 2.2+).</p>
     *
     * <pre>
     * Data: 00 || 00 || len(appAID) || appAID || 01 || privileges || paramsLen(0) || tokenLen(0)
     * </pre>
     *
     * @param appletAid  the applet AID bytes
     * @param privileges the new privilege byte
     * @return the constructed data field
     */
    static byte[] forRegistryUpdate(byte[] appletAid, int privileges) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); // empty executable load file AID
        out.write(0); // empty executable module AID
        writeLengthPrefixed(out, appletAid);
        out.write(1); // privileges length
        out.write(privileges);
        out.write(0); // parameters length
        out.write(0); // token length
        return out.toByteArray();
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream out, byte[] data) {
        out.write(data.length);
        if (data.length > 0) {
            out.write(data, 0, data.length);
        }
    }
}
