package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Validates the AIDs of a CAP file before it is generated (fail-closed).
 *
 * <p>Errors:
 * <ul>
 *   <li>an AID is not 5 to 16 bytes long: a 5-byte RID and a PIX of 0 to 11 bytes
 *       (JCVM 3.1 §4.2.1; package_info and applet AID_length, §6.4, §6.6);</li>
 *   <li>the RID of an applet AID differs from the RID of the package AID, so all applet RIDs
 *       are equal too (§4.2.2.2, §6.6);</li>
 *   <li>two applets have the same AID (§4.2.2.2), or an applet has the AID of the package: on a
 *       card the package and each applet are distinct AID-named entities (§4.2.2);</li>
 *   <li>the package has the AID of a package it imports (§4.2.2.3: no two packages have the same
 *       AID; such a CAP file cannot be linked).</li>
 * </ul>
 * Warnings: the package AID equals the AID of another known package that this package does not
 * import (built-in API or a supplied export file; §4.2.2.3), or it was generated from the package
 * name and is therefore a proprietary, unregistered AID (§4.2.1).
 */
final class AidRules {

    private static final int RID_LENGTH = 5;
    private static final int MAX_AID_LENGTH = 16;

    private AidRules() {}

    /**
     * Checks all AIDs.
     *
     * @param packageName        internal name of the package being converted
     * @param packageAid         AID of the package being converted
     * @param applets            applet class names (dot notation) and AIDs, in registration order
     * @param knownPackages      packages with an export file (candidate imports)
     * @param referencedPackages internal names of the packages the classes reference
     * @return warnings (empty when the AIDs are unremarkable)
     * @throws ConverterException listing every violated rule
     */
    static List<String> check(String packageName, byte[] packageAid, Map<String, byte[]> applets,
                              List<ImportedPackage> knownPackages, Collection<String> referencedPackages)
            throws ConverterException {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        checkLength("package AID", packageAid, problems);
        applets.forEach((cls, aid) -> checkLength("AID of applet " + cls, aid, problems));
        if (problems.isEmpty()) {
            checkRids(packageAid, applets, problems);
            checkUniqueness(packageAid, applets, problems);
            checkPackages(packageName, packageAid, knownPackages, referencedPackages, problems, warnings);
        }
        if (!problems.isEmpty()) {
            throw new ConverterException("Invalid AIDs:\n  - " + String.join("\n  - ", problems));
        }
        return warnings;
    }

    private static void checkLength(String what, byte[] aid, List<String> problems) {
        if (aid.length < RID_LENGTH || aid.length > MAX_AID_LENGTH) {
            problems.add(what + " " + hex(aid) + " is " + aid.length
                    + " bytes long; an AID has 5 to 16 bytes (JCVM 3.1 §4.2.1)");
        }
    }

    private static void checkRids(byte[] packageAid, Map<String, byte[]> applets, List<String> problems) {
        byte[] rid = Arrays.copyOf(packageAid, RID_LENGTH);
        applets.forEach((cls, aid) -> {
            byte[] appletRid = Arrays.copyOf(aid, RID_LENGTH);
            if (!Arrays.equals(appletRid, rid)) {
                problems.add("AID of applet " + cls + " " + hex(aid) + " has RID " + hex(appletRid)
                        + ", but the package RID is " + hex(rid)
                        + "; applet AIDs must use the RID of the package AID (JCVM 3.1 §6.6)");
            }
        });
    }

    private static void checkUniqueness(byte[] packageAid, Map<String, byte[]> applets, List<String> problems) {
        List<Map.Entry<String, byte[]>> seen = new ArrayList<>();
        for (Map.Entry<String, byte[]> applet : applets.entrySet()) {
            if (Arrays.equals(applet.getValue(), packageAid)) {
                problems.add("applet " + applet.getKey() + " has the package AID " + hex(packageAid)
                        + "; applet and package AIDs must differ (JCVM 3.1 §4.2.2)");
            }
            for (Map.Entry<String, byte[]> other : seen) {
                if (Arrays.equals(other.getValue(), applet.getValue())) {
                    problems.add("applets " + other.getKey() + " and " + applet.getKey() + " have the same AID "
                            + hex(applet.getValue()) + " (JCVM 3.1 §4.2.2.2)");
                }
            }
            seen.add(applet);
        }
    }

    private static void checkPackages(String packageName, byte[] packageAid, List<ImportedPackage> knownPackages,
                                      Collection<String> referencedPackages, List<String> problems,
                                      List<String> warnings) {
        for (ImportedPackage known : knownPackages) {
            String name = known.exportFile().packageName();
            if (!Arrays.equals(known.aid(), packageAid) || name.equals(packageName)) continue;
            String message = "package AID " + hex(packageAid) + " is the AID of the "
                    + (referencedPackages.contains(name) ? "imported " : "") + "package "
                    + name.replace('/', '.') + " (JCVM 3.1 §4.2.2.3: no two packages have the same AID)";
            (referencedPackages.contains(name) ? problems : warnings).add(message);
        }
    }

    /**
     * Returns the warning for a package AID that the builder generated from the package name.
     *
     * @param packageAid the generated AID
     * @return the warning text
     */
    static String generatedAidWarning(byte[] packageAid) {
        return "no package AID was configured; using " + hex(packageAid) + ", generated from the package name."
                + " Its RID is proprietary (not registered with ISO, JCVM 3.1 §4.2.1); configure a package AID"
                + " under your own RID for cards in the field";
    }

    private static String hex(byte[] aid) {
        return HexFormat.of().withUpperCase().formatHex(aid);
    }
}
