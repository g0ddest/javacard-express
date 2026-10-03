package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.BuildDescriptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * What a test run on a GlobalPlatform card knows about AIDs, for the message of a failed SELECT: the instances it
 * installed and the AIDs the Maven plugin's build gave the packages and applets it loaded. Selecting the build's
 * AID (the one the build prints) in place of the run's is a typical mistake: tests address the run's AIDs.
 */
final class RunAids {

    /** Class names of the instances on the card, by instance AID. */
    private final Map<AID, String> instances = new LinkedHashMap<>();
    /** The packages of the builds, by the package AID the build gave them. */
    private final Map<AID, String> buildPackages = new LinkedHashMap<>();
    /** The applet classes of the builds, by the applet AID the build gave them. */
    private final Map<AID, String> buildApplets = new LinkedHashMap<>();

    /**
     * Records an instance the run installed.
     *
     * @param applet the instance
     */
    void installed(AppletDeclaration applet) {
        instances.put(applet.instanceAid(), applet.appletClass().getName());
    }

    /**
     * Records an instance the run deleted.
     *
     * @param applet the instance
     */
    void deleted(AppletDeclaration applet) {
        instances.remove(applet.instanceAid());
    }

    /**
     * Records the AIDs of a build.
     *
     * @param build the build descriptor of a loaded package
     */
    void built(BuildDescriptor build) {
        buildPackages.put(build.packageAid(), build.packageName());
        build.applets().forEach((className, aid) -> buildApplets.put(aid, className));
    }

    /** What an AID of a build is and how a test reaches it in this run, or null for no AID of a build. */
    private String build(AID aid) {
        String packageName = buildPackages.get(aid);
        if (packageName != null) {
            return "the package AID the build gave " + packageName + " (a package is loaded, its applets are"
                    + " selected)";
        }
        String className = buildApplets.get(aid);
        if (className == null) {
            return null;
        }
        String simple = className.substring(className.lastIndexOf('.') + 1);
        List<String> runAids = instances.entrySet().stream().filter(entry -> entry.getValue().equals(className))
                .map(entry -> entry.getKey().toHex()).toList();
        if (runAids.isEmpty()) {
            return "the AID the build gave " + className + ", and this run did not install it: declare it with"
                    + " @InstallApplet(" + simple + ".class) on the test class or method to test it";
        }
        return "the AID the build gave " + className + ": a test addresses the run's AIDs, here " + runAids
                + (runAids.size() == 1 ? ", card.select(" + simple + ".class)"
                : ", card.select(card.aid(\"<suffix>\"))");
    }

    /**
     * Explains what the run knows about an AID that could not be selected.
     *
     * @param aid    the AID
     * @param prefix the run's AID prefix
     * @return text to append to the message, starting with {@code "; "}
     */
    String explain(AID aid, String prefix) {
        StringBuilder text = new StringBuilder("; ");
        String instance = instances.get(aid);
        if (instance != null) {
            text.append("it is the instance of ").append(instance).append(" that this run installed");
        } else {
            StringJoiner list = new StringJoiner(", ", "no instance of this run has this AID (instances of this run: ",
                    ")").setEmptyValue("no instance of this run has this AID (this run has no instance on the card)");
            instances.forEach((instanceAid, className) -> list.add(className + " as " + instanceAid.toHex()));
            text.append(list);
        }
        String build = build(aid);
        if (build != null) {
            text.append("; ").append(aid.toHex()).append(" is ").append(build);
        } else if (instance == null && !aid.toHex().startsWith(prefix)) {
            text.append("; ").append(aid.toHex()).append(" is outside the run's AID prefix ").append(prefix);
        }
        return text.toString();
    }
}
