package name.velikodniy.jcexpress.converter.testutil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stand-in sources of the GlobalPlatform Card API package {@code org.globalplatform} for converter tests: the
 * declarations a probe applet needs to compile (names, signatures and a few constants), so that the tests depend
 * on no API jar. Only the converter's built-in linking data decides what links; these sources also declare one
 * class and one method that no version of the package has ({@code Unofficial}, {@code GPSystem.unofficial()}).
 */
public final class GlobalPlatformStandIns {

    private static final String PACKAGE = "org.globalplatform";

    private GlobalPlatformStandIns() {}

    /**
     * Returns the stand-in sources.
     *
     * @return source text by binary class name, for {@link JavaSources#compile}
     */
    public static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        shareable(sources, "Application", "void processData(byte[] baBuffer, short sOffset, short sLength);");
        shareable(sources, "Personalization", "short processData(byte[] inBuffer, short inOffset, short inLength,"
                + " byte[] outBuffer, short outOffset);");
        secureChannels(sources);
        shareable(sources, "CVM", "short CVM_SUCCESS = 0; boolean isVerified();"
                + " short verify(byte[] baBuffer, short sOffset, byte bLength, byte bFormat);");
        shareable(sources, "GPRegistryEntry", "byte getState(); boolean setState(byte bState);"
                + " javacard.framework.AID getAID();");
        shareable(sources, "GlobalService", "javacard.framework.Shareable getServiceInterface(GPRegistryEntry entry,"
                + " short sServiceName, byte[] baBuffer, short sOffset, short sLength);");
        shareable(sources, "AuthoritySignature", "short getLength();");
        shareable(sources, "AdministrationSession", "void requestAdministrationSession(byte[] triggeringParameters,"
                + " short offset, short length);");
        sources.put(PACKAGE + ".Unofficial", "package org.globalplatform;\n"
                + "public class Unofficial { public static void touch() { } }\n");
        sources.put(PACKAGE + ".GPSystem", GP_SYSTEM);
        return sources;
    }

    /** SecureChannel and the two interfaces that extend it. */
    private static void secureChannels(Map<String, String> sources) {
        shareable(sources, "SecureChannel", """
                byte C_MAC = 1;
                byte AUTHENTICATED = (byte) 0x80;
                short processSecurity(javacard.framework.APDU apdu) throws javacard.framework.ISOException;
                short wrap(byte[] baBuffer, short sOffset, short sLength);
                short unwrap(byte[] baBuffer, short sOffset, short sLength);
                short decryptData(byte[] baBuffer, short sOffset, short sLength);
                short encryptData(byte[] baBuffer, short sOffset, short sLength);
                void resetSecurity();
                byte getSecurityLevel();""");
        sources.put(PACKAGE + ".SecureChannelx", "package org.globalplatform;\n"
                + "public interface SecureChannelx extends SecureChannel { void setSecurityLevel(byte bLevel); }\n");
        sources.put(PACKAGE + ".SecureChannelx2", "package org.globalplatform;\n"
                + "public interface SecureChannelx2 extends SecureChannel {\n"
                + "    short processSecurity(byte[] baBuffer, short sInOffset, short sInLength, short sOutOffset)\n"
                + "            throws javacard.framework.ISOException;\n}\n");
    }

    private static void shareable(Map<String, String> sources, String name, String members) {
        sources.put(PACKAGE + "." + name, "package org.globalplatform;\npublic interface " + name
                + " extends javacard.framework.Shareable {\n" + members + "\n}\n");
    }

    private static final String GP_SYSTEM = """
            package org.globalplatform;
            import javacard.framework.AID;
            public class GPSystem {
                public static final byte APPLICATION_SELECTABLE = 7;
                public static final byte CVM_GLOBAL_PIN = 0x11;
                public GPSystem() { }
                public static byte getCardContentState() { return 0; }
                public static byte getCardState() { return 0; }
                public static CVM getCVM(byte bCVMIdentifier) { return null; }
                public static SecureChannel getSecureChannel() { return null; }
                public static boolean setCardContentState(byte bState) { return false; }
                public static GPRegistryEntry getRegistryEntry(AID aid) { return null; }
                public static GlobalService getService(AID serverAID, short sServiceName) { return null; }
                public static short getCardInfo(byte[] buffer, short offset, short info) { return 0; }
                public static short getCardInfoLength(short info) { return 0; }
                public static void unofficial() { }
            }
            """;
}
