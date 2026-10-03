package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assigns and checks the AIDs of the applets of one CAP file.
 *
 * <p>An applet without a configured AID gets the package AID extended by its 1-based position
 * ({@code packageAid || index}), which keeps the package RID as JCVM 3.1 &sect;4.2.2.2 and
 * &sect;6.6 require: "The RID of each applet in a CAP file must be the same as the RID of the
 * CAP file AID" and "An applet AID must not have the same value as the AID of any other applet
 * of the same CAP file".
 */
final class AppletAids {

    private AppletAids() {
    }

    /**
     * An applet to put into the Applet component, before its AID is known.
     *
     * @param className fully qualified class name (dot notation)
     * @param aid       configured AID (hex), or {@code null} to derive one from the package AID
     */
    record Request(String className, String aid) {
    }

    /**
     * An applet with its final AID.
     *
     * @param className fully qualified class name (dot notation)
     * @param aid       the applet AID
     * @param derived   {@code true} if the AID was derived from the package AID
     */
    record Assigned(String className, Aid aid, boolean derived) {
    }

    /**
     * Resolves the AID of every applet and validates them against the package AID.
     *
     * @param packageAid the package (CAP file) AID
     * @param requests   the applets in Applet component order
     * @return the applets with their AIDs, in the same order
     * @throws MojoExecutionException if an AID is malformed, cannot be derived, has another RID
     *                                than the package, is used twice, or equals the package AID
     */
    static List<Assigned> assign(Aid packageAid, List<Request> requests) throws MojoExecutionException {
        List<Assigned> assigned = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            Request request = requests.get(i);
            if (request.aid() == null || request.aid().isBlank()) {
                assigned.add(new Assigned(request.className(), derive(packageAid, i + 1, request), true));
            } else {
                assigned.add(new Assigned(request.className(),
                        Aid.parse("AID of applet " + request.className(), request.aid()), false));
            }
        }
        checkRids(packageAid, assigned);
        checkUnique(assigned);
        checkNotPackageAid(packageAid, assigned);
        return assigned;
    }

    /**
     * Explains AIDs that follow from the class-name order of several discovered applets: an applet
     * added later whose name sorts before them changes their positions and so their AIDs, and a card
     * that has the old AIDs installed no longer matches the CAP file.
     *
     * @param applets the discovered applets with their AIDs, in class-name order
     * @return a warning with the {@code <applets>} configuration that keeps the current AIDs, or
     *         empty if fewer than two AIDs are derived
     */
    static Optional<String> orderWarning(List<Assigned> applets) {
        List<Assigned> derived = applets.stream().filter(Assigned::derived).toList();
        if (derived.size() < 2) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder("The AIDs of ").append(derived.size())
                .append(" applets follow from their order by class name: ")
                .append(derived.stream().map(a -> a.className() + " " + a.aid()).collect(Collectors.joining(", ")))
                .append(". An applet added later whose name sorts before them changes these AIDs. To keep them,")
                .append(" list the applets with their AIDs in the plugin configuration:\n<applets>");
        for (Assigned applet : derived) {
            text.append("\n    <applet>\n        <className>").append(applet.className())
                    .append("</className>\n        <aid>").append(applet.aid()).append("</aid>\n    </applet>");
        }
        return Optional.of(text.append("\n</applets>").toString());
    }

    private static Aid derive(Aid packageAid, int index, Request request) throws MojoExecutionException {
        if (packageAid.isMaximumLength()) {
            throw new MojoExecutionException("The package AID " + packageAid + " is 16 bytes long, so no applet"
                    + " AID can be derived from it (an AID has at most 16 bytes, JCVM 3.1 §4.2.1). Configure an"
                    + " <aid> for applet " + request.className() + " in <applets>, or use a shorter <packageAid>.");
        }
        return packageAid.withSuffix(index);
    }

    private static void checkRids(Aid packageAid, List<Assigned> applets) throws MojoExecutionException {
        for (Assigned applet : applets) {
            if (!applet.aid().sameRid(packageAid)) {
                throw new MojoExecutionException("The AID " + applet.aid() + " of applet " + applet.className()
                        + " has the RID " + applet.aid().ridHex() + ", but the package AID " + packageAid
                        + " has the RID " + packageAid.ridHex() + ". All applet AIDs of a CAP file must have"
                        + " the RID of the package AID (JCVM 3.1 §4.2.2.2, §6.6).");
            }
        }
    }

    private static void checkUnique(List<Assigned> applets) throws MojoExecutionException {
        Map<Aid, Assigned> seen = new HashMap<>();
        for (Assigned applet : applets) {
            Assigned other = seen.putIfAbsent(applet.aid(), applet);
            if (other != null) {
                throw new MojoExecutionException("Applets " + other.className() + " and " + applet.className()
                        + " have the same AID " + applet.aid() + "; every applet of a CAP file needs its own AID"
                        + " (JCVM 3.1 §4.2.2.2).");
            }
        }
    }

    private static void checkNotPackageAid(Aid packageAid, List<Assigned> applets) throws MojoExecutionException {
        for (Assigned applet : applets) {
            if (applet.aid().equals(packageAid)) {
                throw new MojoExecutionException("Applet " + applet.className() + " has the same AID as the package ("
                        + packageAid + "). The CAP file and each applet are distinct AID-named entities (JCVM 3.1"
                        + " §4.2.2), and a card registry cannot hold the load file and the applet instance under one"
                        + " AID: give the applet its own PIX in <applets>, or change <packageAid>.");
            }
        }
    }
}
