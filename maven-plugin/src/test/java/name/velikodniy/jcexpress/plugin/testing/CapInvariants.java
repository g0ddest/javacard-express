package name.velikodniy.jcexpress.plugin.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Structural rules of the JCVM 3.1 specification that a CAP file built by the plugin must obey,
 * checked with the independent {@link CapFile} reader. They cover what the plugin decides (applets,
 * AIDs, export): the converter's own components are checked by the converter tests.
 *
 * <ul>
 *   <li>&sect;6.4 Table 6-3: ACC_APPLET is set if and only if an Applet component is included,
 *       ACC_EXPORT if and only if an Export component is included; undefined flag bits are zero.</li>
 *   <li>&sect;4.2.1: AIDs are 5 to 16 bytes; &sect;4.2.2.2, &sect;6.6: applet AIDs have the RID of
 *       the package AID and are unique.</li>
 *   <li>&sect;6.6: each install_method_offset designates the static
 *       {@code install(byte[], short, byte)} method (type descriptor nibbles {@code B431},
 *       &sect;6.14.5).</li>
 *   <li>&sect;6.13: an Export component has a class_count greater than zero.</li>
 * </ul>
 */
public final class CapInvariants {

    private static final int DEFINED_FLAGS = CapFile.ACC_INT | CapFile.ACC_EXPORT | CapFile.ACC_APPLET
            | CapFile.ACC_EXTENDED;
    private static final int RID_HEX_DIGITS = 10;

    private CapInvariants() {
    }

    /**
     * Checks the rules.
     *
     * @param cap the CAP file
     * @return the violated rules, empty if there are none
     */
    public static List<String> violations(CapFile cap) {
        List<String> problems = new ArrayList<>();
        CapFile.Header header = cap.header();
        flag(problems, header, CapFile.ACC_APPLET, cap.has("Applet"), "ACC_APPLET", "Applet");
        flag(problems, header, CapFile.ACC_EXPORT, cap.has("Export"), "ACC_EXPORT", "Export");
        if ((header.flags() & ~DEFINED_FLAGS) != 0) {
            problems.add("6.4: reserved Header flag bits set: 0x" + Integer.toHexString(header.flags()));
        }
        aid(problems, "package", header.packageAid());
        Set<String> seen = new HashSet<>();
        for (CapFile.Applet applet : cap.applets()) {
            aid(problems, "applet", applet.aid());
            if (!applet.aid().regionMatches(0, header.packageAid(), 0, RID_HEX_DIGITS)) {
                problems.add("4.2.2.2/6.6: applet AID " + applet.aid() + " does not have the RID of package AID "
                        + header.packageAid());
            }
            if (!seen.add(applet.aid())) {
                problems.add("4.2.2.2: applet AID " + applet.aid() + " is used twice");
            }
            installMethod(cap, applet).ifPresent(problems::add);
        }
        cap.exportClassCount().filter(count -> count == 0)
                .ifPresent(count -> problems.add("6.13: Export component with class_count 0"));
        return problems;
    }

    /**
     * Fails with all violated rules.
     *
     * @param cap the CAP file
     */
    public static void check(CapFile cap) {
        List<String> problems = violations(cap);
        if (!problems.isEmpty()) {
            throw new AssertionError("CAP file violates JCVM 3.1:\n  " + String.join("\n  ", problems));
        }
    }

    private static void flag(List<String> problems, CapFile.Header header, int flag, boolean present,
                             String flagName, String component) {
        boolean set = (header.flags() & flag) != 0;
        if (set != present) {
            problems.add("6.4: " + flagName + " is " + (set ? "set" : "clear") + " but the " + component
                    + " component is " + (present ? "present" : "absent"));
        }
    }

    private static void aid(List<String> problems, String what, String hexAid) {
        int bytes = hexAid.length() / 2;
        if (bytes < 5 || bytes > 16) {
            problems.add("4.2.1: " + what + " AID " + hexAid + " has " + bytes + " bytes");
        }
    }

    private static Optional<String> installMethod(CapFile cap, CapFile.Applet applet) {
        Optional<CapFile.MethodLocation> install = cap.methodAt(applet.installMethodOffset());
        if (install.isEmpty()) {
            return Optional.of("6.6: install_method_offset " + applet.installMethodOffset() + " of applet "
                    + applet.aid() + " is not the start of a method");
        }
        CapFile.MethodDescriptor method = install.get().method();
        boolean isStatic = (method.flags() & CapFile.MethodDescriptor.ACC_STATIC) != 0;
        boolean isInit = (method.flags() & CapFile.MethodDescriptor.ACC_INIT) != 0;
        String type = cap.typeNibbles(method.typeOffset());
        if (!isStatic || isInit || !"B431".equals(type)) {
            return Optional.of("6.6: install_method_offset of applet " + applet.aid() + " designates a method with"
                    + " flags 0x" + Integer.toHexString(method.flags()) + " and type " + type
                    + ", not static install(byte[], short, byte)");
        }
        return Optional.empty();
    }
}
