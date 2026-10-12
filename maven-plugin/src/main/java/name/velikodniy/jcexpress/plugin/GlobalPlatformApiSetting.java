package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.Optional;
import java.util.Set;

/**
 * The parameter {@code <globalPlatformApi>}: the version of the GlobalPlatform Card API package
 * {@code org.globalplatform} that the CAP file imports when the converter's built-in data describes the package.
 * A card resolves an import only if it holds the package in the recorded version or a later minor version
 * (JCVM 3.1 &sect;4.5.2), so the converter records the lowest version that has everything the converted package
 * uses unless the parameter fixes one.
 */
final class GlobalPlatformApiSetting {

    /** The package in dot notation. */
    static final String PACKAGE = GlobalPlatformApi.PACKAGE_NAME.replace('/', '.');

    private final String version;

    private GlobalPlatformApiSetting(String version) {
        this.version = version;
    }

    /**
     * Reads the parameter.
     *
     * @param configured the configured value, or {@code null} or blank when the parameter is not set
     * @return the setting
     * @throws MojoExecutionException if the value is not a version of the package that the converter knows
     */
    static GlobalPlatformApiSetting parse(String configured) throws MojoExecutionException {
        if (configured == null || configured.isBlank()) {
            return new GlobalPlatformApiSetting(null);
        }
        try {
            return new GlobalPlatformApiSetting(GlobalPlatformApi.requireVersion(configured.strip()));
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Invalid <globalPlatformApi>" + configured + "</globalPlatformApi>: "
                    + e.getMessage() + ". Leave the parameter out to import the lowest version that has everything"
                    + " the package uses.", e);
        }
    }

    /**
     * Returns the fixed version.
     *
     * @return the version {@code <major>.<minor>}, or empty when the converter chooses
     */
    Optional<String> version() {
        return Optional.ofNullable(version);
    }

    /**
     * Passes the setting to the converter.
     *
     * @param builder the converter being configured
     */
    void applyTo(Converter.Builder builder) {
        builder.globalPlatformApi(version);
    }

    /**
     * Returns the packages whose export file is not looked up on the class path: with a fixed version the
     * built-in data of that version is meant, not the export file a GlobalPlatform API jar happens to contain.
     * Export files named in {@code <importExportFiles>} or found on {@code <exportPath>} still apply.
     *
     * @return {@code org.globalplatform} when a version is fixed, else nothing
     */
    Set<String> packagesNotSearchedOnTheClassPath() {
        return version == null ? Set.of() : Set.of(PACKAGE);
    }

    /**
     * Describes the version of the package that a CAP file imports and where it comes from.
     *
     * @param result the conversion result
     * @return the line for the build log, or empty if the CAP file does not import the package
     */
    Optional<String> describe(ConverterResult result) {
        return result.imports().stream().filter(imported -> imported.packageName().equals(PACKAGE)).findFirst()
                .map(imported -> "GlobalPlatform API: " + PACKAGE + " " + imported.version() + " ("
                        + origin(imported) + "; cards with this version or a later one can load the CAP file)");
    }

    private String origin(ConverterResult.Import imported) {
        if (!imported.builtIn()) {
            return "the version of the export file found for the package";
        }
        return version != null ? "fixed with <globalPlatformApi>"
                : "the lowest version that has everything the package uses";
    }
}
