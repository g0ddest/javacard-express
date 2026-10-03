package name.velikodniy.jcexpress.plugin;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.List;

/**
 * Checks that the project uses the toolkit in the plugin's own version. The API stubs
 * ({@code javacard-express-api}) are what javac compiles the applet against, and the converter in
 * the plugin carries its own data of the same Java Card API (signatures, tokens, constant values
 * of the API packages, JCVM 3.1 chapter 5); {@code javacard-express-core} runs the project's
 * tests. Mixing releases builds CAP files from code that was compiled and tested against another
 * API than the one the converter links it to. Maven resolves a plugin declared without
 * {@code <version>} to the newest release it finds, which is how such a mix happens unnoticed.
 */
final class ToolkitVersions {

    private static final String GROUP_ID = "name.velikodniy";
    private static final List<String> CHECKED = List.of("javacard-express-api", "javacard-express-core");

    private ToolkitVersions() {
    }

    /**
     * Compares the declared versions of {@code javacard-express-api} and
     * {@code javacard-express-core} with the plugin version.
     *
     * @param dependencies  the dependencies of the project (declared or inherited, with managed versions)
     * @param pluginVersion the version of this plugin, or {@code null} if unknown (no check)
     * @throws MojoExecutionException if a version differs, naming each artifact and version
     */
    static void check(List<Dependency> dependencies, String pluginVersion) throws MojoExecutionException {
        if (pluginVersion == null) {
            return;
        }
        List<String> others = dependencies.stream()
                .filter(d -> GROUP_ID.equals(d.getGroupId()) && CHECKED.contains(d.getArtifactId()))
                .filter(d -> d.getVersion() != null && !isRange(d.getVersion()))
                .filter(d -> !sameVersion(d.getVersion(), pluginVersion))
                .map(d -> d.getArtifactId() + " " + d.getVersion())
                .distinct()
                .toList();
        if (!others.isEmpty()) {
            throw new MojoExecutionException("javacard-express-maven-plugin " + pluginVersion
                    + " builds this project, but the project depends on " + String.join(" and ", others)
                    + ". The converter in the plugin, the API stubs javac compiles against and the toolkit the"
                    + " tests run on must come from one release: give the plugin and these dependencies one"
                    + " version (a property such as ${jcexpress.version}, the javacard-express-bom, or the parent"
                    + " javacard-express-applet-parent). A plugin declared without <version> is the newest release"
                    + " Maven finds. <checkVersions>false</checkVersions> skips this check.");
        }
    }

    private static boolean sameVersion(String declared, String plugin) {
        return ArtifactUtils.toSnapshotVersion(declared).equals(ArtifactUtils.toSnapshotVersion(plugin));
    }

    private static boolean isRange(String version) {
        return version.startsWith("[") || version.startsWith("(");
    }
}
