package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.io.ByteArrayOutputStream;

/**
 * Builds the data fields of the GlobalPlatform INSTALL and DELETE commands (GPCS v2.3.1 11.5.2.3 and
 * 11.2.2.3).
 *
 * <p>Package-private utility used by {@link GPSession}. AIDs are LV coded with a one-byte length; the
 * lengths of parameter and token fields are coded as ASN.1 BER lengths ('00'-'7F', '81 80'-'81 FF',
 * '82 01 00'-'82 FF FF', GPCS 11.1.5 and Tables 11-42/11-43).</p>
 */
final class InstallParams {

    private InstallParams() {
    }

    /**
     * Builds INSTALL [for load] data without hash and load parameters (Table 11-42).
     *
     * @param packageAid the Load File AID bytes
     * @param sdAid      the Security Domain AID bytes (empty array = the selected Security Domain)
     * @return the constructed data field
     */
    static byte[] forLoad(byte[] packageAid, byte[] sdAid) {
        return forLoad(packageAid, sdAid, new byte[0], new byte[0]);
    }

    /**
     * Builds INSTALL [for load] data (Table 11-42).
     *
     * <pre>
     * Data: len(LF AID) LF AID | len(SD AID) SD AID | len(hash) hash | BER-len(load params) load params
     *       | BER-len(token)=00
     * </pre>
     *
     * @param loadFileAid    the Load File AID (5-16 bytes)
     * @param sdAid          the Security Domain AID (empty, or 5-16 bytes)
     * @param loadFileHash   the Load File Data Block hash (empty, or up to 127 bytes, see section C.2)
     * @param loadParameters the Load Parameters field (Table 11-48), may be empty
     * @return the constructed data field
     */
    static byte[] forLoad(byte[] loadFileAid, byte[] sdAid, byte[] loadFileHash, byte[] loadParameters) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAid(out, "Load File", loadFileAid, false);
        writeAid(out, "Security Domain", sdAid, true);
        if (loadFileHash.length > 0x7F) {
            throw new GPException("Load File Data Block hash longer than 127 bytes (Table 11-42)");
        }
        out.write(loadFileHash.length);
        out.writeBytes(loadFileHash);
        writeBerValue(out, loadParameters);
        out.write(0); // no Load Token
        return out.toByteArray();
    }

    /**
     * Builds INSTALL [for install] / [for install and make selectable] data (Table 11-43).
     *
     * <pre>
     * Data: len(ELF AID) ELF AID | len(module AID) module AID | len(app AID) app AID
     *       | len(privileges) privileges | BER-len(install params) [C9 BER-len application params]
     *       | BER-len(token)=00
     * </pre>
     *
     * @param packageAid    the Executable Load File AID bytes
     * @param moduleAid     the Executable Module (applet class) AID bytes
     * @param instanceAid   the Application (instance) AID bytes
     * @param privileges    the privilege bytes (1 or 3 bytes, section 11.1.2)
     * @param installParams the application specific parameters (tag 'C9' value), may be null or empty
     * @return the constructed data field
     */
    static byte[] forInstall(byte[] packageAid, byte[] moduleAid, byte[] instanceAid,
                             byte[] privileges, byte[] installParams) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAid(out, "Executable Load File", packageAid, false);
        writeAid(out, "Executable Module", moduleAid, false);
        writeAid(out, "Application", instanceAid, false);
        writePrivileges(out, privileges);
        // Install Parameters field: mandatory, at least the 'C9' Application Specific Parameters (Table 11-49)
        ByteArrayOutputStream params = new ByteArrayOutputStream();
        params.write(0xC9);
        writeBerValue(params, installParams == null ? new byte[0] : installParams);
        writeBerValue(out, params.toByteArray());
        out.write(0); // no Install Token
        return out.toByteArray();
    }

    /**
     * Encodes privileges given as an int: values up to 0xFF are one byte (byte 1), larger values are the
     * three bytes {@code byte1 byte2 byte3} (section 11.1.2, Tables 11-7 to 11-9).
     *
     * @param privileges the privileges
     * @return 1 or 3 privilege bytes
     */
    static byte[] privileges(int privileges) {
        if (privileges < 0 || privileges > 0xFFFFFF) {
            throw new GPException("Privileges must be 0x00-0xFF (byte 1) or 0x000100-0xFFFFFF (3 bytes)");
        }
        if (privileges <= 0xFF) {
            return new byte[]{(byte) privileges};
        }
        return new byte[]{(byte) (privileges >> 16), (byte) (privileges >> 8), (byte) privileges};
    }

    /**
     * Builds DELETE [card content] data: {@code '4F' len AID} (Table 11-23).
     *
     * @param aid the AID bytes to delete (5-16 bytes)
     * @return the constructed data field
     */
    static byte[] forDelete(byte[] aid) {
        requireAid("Executable Load File or Application", aid);
        byte[] data = new byte[2 + aid.length];
        data[0] = 0x4F;
        data[1] = (byte) aid.length;
        System.arraycopy(aid, 0, data, 2, aid.length);
        return data;
    }

    /**
     * Builds INSTALL [for extradition] data (Table 11-45).
     *
     * <pre>
     * Data: len(SD AID) SD AID | 00 | len(app AID) app AID | 00 | 00 (params) | 00 (token)
     * </pre>
     *
     * @param sdAid     the target Security Domain AID bytes
     * @param appletAid the Application or Executable Load File AID bytes to extradite
     * @return the constructed data field
     */
    static byte[] forExtradition(byte[] sdAid, byte[] appletAid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAid(out, "Security Domain", sdAid, false);
        out.write(0);
        writeAid(out, "Application", appletAid, false);
        out.write(0);
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    /**
     * Builds INSTALL [for personalization] data (Table 11-47).
     *
     * <pre>
     * Data: 00 | 00 | len(app AID) app AID | 00 | 00 | 00
     * </pre>
     *
     * @param appletAid the Application AID bytes to personalize
     * @return the constructed data field
     */
    static byte[] forPersonalization(byte[] appletAid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(0);
        writeAid(out, "Application", appletAid, false);
        out.write(0);
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    /**
     * Builds INSTALL [for registry update] data (Table 11-46).
     *
     * <pre>
     * Data: 00 (SD AID) | 00 | len(app AID) app AID | len(privileges) privileges | 00 (params) | 00 (token)
     * </pre>
     *
     * @param appletAid  the Application AID bytes
     * @param privileges the new privilege bytes (1 or 3)
     * @return the constructed data field
     */
    static byte[] forRegistryUpdate(byte[] appletAid, byte[] privileges) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(0);
        writeAid(out, "Application", appletAid, false);
        writePrivileges(out, privileges);
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    /**
     * Writes an ASN.1 BER length followed by the value (GPCS v2.3.1 11.1.5).
     *
     * @param out   the output
     * @param value the value; its length must not exceed 65535
     */
    static void writeBerValue(ByteArrayOutputStream out, byte[] value) {
        int length = value.length;
        if (length <= 0x7F) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length);
        } else {
            throw new GPException("Field of " + length + " bytes exceeds the 65535-byte BER limit of"
                    + " GPCS v2.3.1 11.1.5");
        }
        out.writeBytes(value);
    }

    private static void writePrivileges(ByteArrayOutputStream out, byte[] privileges) {
        if (privileges.length != 1 && privileges.length != 3) {
            throw new GPException("Privileges must be 1 or 3 bytes (GPCS v2.3.1 11.1.2, Table 11-43), got "
                    + privileges.length);
        }
        out.write(privileges.length);
        out.writeBytes(privileges);
    }

    private static void writeAid(ByteArrayOutputStream out, String name, byte[] aid, boolean optional) {
        if (!(optional && aid.length == 0)) {
            requireAid(name, aid);
        }
        out.write(aid.length);
        out.writeBytes(aid);
    }

    private static void requireAid(String name, byte[] aid) {
        if (aid.length < 5 || aid.length > 16) {
            throw new GPException(name + " AID must be 5-16 bytes (ISO/IEC 7816-5), got " + aid.length
                    + " bytes: " + Hex.encode(aid));
        }
    }
}
