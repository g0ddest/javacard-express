package name.velikodniy.jcexpress.livecard.guard;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * The guard's rules for commands that change the card content (GlobalPlatform Card Specification v2.3.1
 * chapter 11): only objects whose AIDs start with the test prefix, never privileges, tokens or other
 * Security Domains. Each check returns {@code null} when the command is allowed, otherwise the reason.
 */
final class CardContentRules {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /** INSTALL P1 values (Table 11-41). */
    static final int FOR_LOAD = 0x02;
    static final int FOR_INSTALL = 0x04;
    static final int FOR_MAKE_SELECTABLE = 0x08;
    static final int FOR_INSTALL_AND_MAKE_SELECTABLE = 0x0C;

    private CardContentRules() {
    }

    /**
     * The fields of INSTALL [for load] (Table 11-42), [for install] (11-43) or [for make selectable] (11-44).
     * Fields that the variant does not have are empty.
     *
     * @param p1             the INSTALL variant
     * @param loadFile       the (Executable) Load File AID
     * @param securityDomain the Security Domain AID of INSTALL [for load]
     * @param module         the Executable Module AID
     * @param instance       the Application AID
     * @param privileges     the privilege bytes
     * @param token          the Load/Install Token
     */
    record InstallFields(int p1, byte[] loadFile, byte[] securityDomain, byte[] module, byte[] instance,
                         byte[] privileges, byte[] token) {

        /**
         * Parses INSTALL command data.
         *
         * @param p1      the INSTALL variant
         * @param payload the command data without C-MAC
         * @return the fields
         * @throws IllegalArgumentException if the data is malformed or the variant is not supported
         */
        static InstallFields parse(int p1, byte[] payload) {
            LvReader in = new LvReader(payload);
            byte[] empty = new byte[0];
            if (p1 == FOR_LOAD) {
                byte[] loadFile = in.shortField();
                byte[] sd = in.shortField();
                in.shortField();
                in.berField();
                byte[] token = in.berField();
                in.requireEnd();
                return new InstallFields(p1, loadFile, sd, empty, empty, empty, token);
            }
            byte[] loadFile = in.shortField();
            byte[] module = in.shortField();
            byte[] instance = in.shortField();
            byte[] privileges = in.shortField();
            in.berField();
            byte[] token = in.berField();
            in.requireEnd();
            return new InstallFields(p1, loadFile, empty, module, instance, privileges, token);
        }
    }

    /**
     * Checks INSTALL.
     *
     * @param policy the run's policy
     * @param fields the parsed command data
     * @return null if allowed, otherwise the reason
     */
    static String install(GuardPolicy policy, InstallFields fields) {
        if (fields.token().length != 0) {
            return "INSTALL with a token (delegated management) is not allowed";
        }
        return switch (fields.p1()) {
            case FOR_LOAD -> installForLoad(policy, fields);
            case FOR_INSTALL, FOR_INSTALL_AND_MAKE_SELECTABLE -> installForInstall(policy, fields);
            case FOR_MAKE_SELECTABLE -> fields.loadFile().length == 0 && fields.module().length == 0
                    ? instanceAndPrivileges(policy, fields)
                    : "INSTALL [for make selectable] with load file or module fields";
            default -> String.format("INSTALL variant P1=%02X is not allowed", fields.p1());
        };
    }

    private static String installForLoad(GuardPolicy policy, InstallFields fields) {
        if (!policy.owns(fields.loadFile())) {
            return "INSTALL [for load] of load file " + hex(fields.loadFile()) + " outside the AID prefix "
                    + hex(policy.aidPrefix());
        }
        byte[] sd = fields.securityDomain();
        if (sd.length != 0 && !Arrays.equals(sd, policy.isdAid())) {
            return "INSTALL [for load] into Security Domain " + hex(sd) + "; only the Issuer Security Domain is"
                    + " allowed";
        }
        return null;
    }

    private static String installForInstall(GuardPolicy policy, InstallFields fields) {
        if (!policy.owns(fields.loadFile()) || !policy.owns(fields.module())) {
            return "INSTALL [for install] from load file " + hex(fields.loadFile()) + " / module "
                    + hex(fields.module()) + " outside the AID prefix " + hex(policy.aidPrefix());
        }
        return instanceAndPrivileges(policy, fields);
    }

    private static String instanceAndPrivileges(GuardPolicy policy, InstallFields fields) {
        if (!policy.owns(fields.instance())) {
            return "INSTALL of application " + hex(fields.instance()) + " outside the AID prefix "
                    + hex(policy.aidPrefix());
        }
        byte[] privileges = fields.privileges();
        boolean zero = (privileges.length == 1 || privileges.length == 3) && allZero(privileges);
        return zero ? null : "INSTALL with privileges " + hex(privileges) + "; only '00' is allowed";
    }

    /**
     * Parses DELETE data (Table 11-23): exactly one {@code '4F' len AID}.
     *
     * @param payload the command data without C-MAC
     * @return the AID
     * @throws IllegalArgumentException if the data is not a single AID TLV
     */
    static byte[] deleteAid(byte[] payload) {
        if (payload.length < 2 || (payload[0] & 0xFF) != 0x4F || (payload[1] & 0xFF) != payload.length - 2) {
            throw new IllegalArgumentException("DELETE data must be exactly one '4F' AID (no tokens, no"
                    + " control reference templates), got " + hex(payload));
        }
        return Arrays.copyOfRange(payload, 2, payload.length);
    }

    /**
     * Checks DELETE [card content].
     *
     * @param policy  the run's policy
     * @param command the command
     * @param payload the command data without C-MAC
     * @return null if allowed, otherwise the reason
     */
    static String delete(GuardPolicy policy, Apdu command, byte[] payload) {
        if (command.p1() != 0x00 || (command.p2() != 0x00 && command.p2() != 0x80)) {
            return String.format("DELETE with P1-P2 %02X%02X; only 0000 and 0080 are allowed", command.p1(),
                    command.p2());
        }
        byte[] aid = deleteAid(payload);
        return policy.owns(aid) ? null
                : "DELETE of " + hex(aid) + " outside the AID prefix " + hex(policy.aidPrefix());
    }

    /**
     * Checks SET STATUS (Table 11-60): only lock ('80') and unlock ('00') of an application under the prefix.
     *
     * @param policy  the run's policy
     * @param command the command
     * @param payload the command data without C-MAC (the AID)
     * @return null if allowed, otherwise the reason
     */
    static String setStatus(GuardPolicy policy, Apdu command, byte[] payload) {
        if (command.p1() != 0x40) {
            return String.format("SET STATUS with status type %02X; only applications ('40') may change state,"
                    + " never the card or a Security Domain", command.p1());
        }
        if (command.p2() != 0x00 && command.p2() != 0x80) {
            return String.format("SET STATUS to state control %02X; only lock (80) and unlock (00)", command.p2());
        }
        return policy.owns(payload) && payload.length <= 16 ? null
                : "SET STATUS of " + hex(payload) + " outside the AID prefix " + hex(policy.aidPrefix());
    }

    private static boolean allZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    static String hex(byte[] bytes) {
        return bytes.length == 0 ? "(empty)" : HEX.formatHex(bytes);
    }
}
