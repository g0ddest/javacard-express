package name.velikodniy.jcexpress.livecard;

import java.nio.file.Path;
import java.util.List;

/**
 * What {@link LiveCard#deploy(AppletPackage, AppletInstance...)} put on the card.
 *
 * @param packageAid   the load file (package) AID, uppercase hex
 * @param instances    the instances created
 * @param capSize      the size of the CAP file in bytes
 * @param warnings     the converter's warnings
 * @param capFile      where the loaded CAP file was saved (next to the transcripts)
 * @param verification the result of the off-card verification, or a note that none was configured
 * @param exportPath   for a package with an Export component ({@link AppletPackage#withExportComponent()}): the
 *                     export path entry that holds its export file (JCVM 3.0.5 4.1.1), for the packages that
 *                     import it ({@link AppletPackage#withExportPath(Path...)}); otherwise null
 */
public record Deployment(String packageAid, List<AppletInstance> instances, int capSize, List<String> warnings,
                         Path capFile, String verification, Path exportPath) {

    /**
     * Copies the lists.
     */
    public Deployment {
        instances = List.copyOf(instances);
        warnings = List.copyOf(warnings);
    }
}
