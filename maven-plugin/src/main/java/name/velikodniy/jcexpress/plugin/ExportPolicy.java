package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.logging.Log;

import java.util.List;

/**
 * Decides whether a CAP file gets an Export component and whether an export file is written.
 *
 * <p>JCVM 3.1 &sect;6.13: "For public packages that include applets, the Export Component includes
 * entries only for all public interfaces that are shareable. For public packages that do not
 * include any applets, the Export Component contains an entry for each public class and public
 * interface", and its class_count "must be greater than zero". &sect;5.6.1: "If the package is not
 * a library package, this export file can only contain shareable interfaces." A compact CAP file
 * with an Export component makes its package public, i.e. linkable by other packages.
 *
 * <p>Default (no {@code generateExport} configured): export when there is something to export,
 * i.e. for library packages and for applet packages that declare public shareable interfaces.
 */
final class ExportPolicy {

    private static final String SPEC = "JCVM 3.1 §6.13, §5.6.1";

    private ExportPolicy() {
    }

    /**
     * Decides about the export.
     *
     * @param requested     the {@code generateExport} parameter, {@code null} if not configured
     * @param packageName   the converted package (dot notation)
     * @param appletPackage {@code true} if the package declares applets
     * @param exportable    names of the types the package can export: its public shareable interfaces
     *                      for an applet package, its public classes and interfaces otherwise
     * @param log           receives the explanation
     * @return {@code true} to generate the Export component and write the export file
     */
    static boolean decide(Boolean requested, String packageName, boolean appletPackage,
                          List<String> exportable, Log log) {
        if (Boolean.FALSE.equals(requested)) {
            if (!appletPackage) {
                log.warn("generateExport=false: the library package " + packageName + " gets no Export"
                        + " component, so no other package can link against it on a card (" + SPEC + ").");
            }
            return false;
        }
        if (exportable.isEmpty()) {
            String reason = appletPackage
                    ? "the applet package " + packageName + " declares no public shareable interface, and an"
                    + " applet package exports nothing else"
                    : "the library package " + packageName + " has no public class or interface";
            if (Boolean.TRUE.equals(requested)) {
                log.warn("generateExport=true is ignored: " + reason + " (" + SPEC + "). The CAP file gets no"
                        + " Export component and no export file is written.");
            } else {
                log.info("No Export component: " + reason + " (" + SPEC + ").");
            }
            return false;
        }
        log.info("Export component and export file for " + String.join(", ", exportable));
        return true;
    }
}
