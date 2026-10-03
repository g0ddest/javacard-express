package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chooses the package that becomes the CAP file.
 *
 * <p>JCVM 3.1 &sect;4.1.2: "a CAP file in Compact format can only contain a single Java package";
 * &sect;4.1.3: its components are stored in the directory of that package (e.g.
 * {@code com/oracle/framework/javacard}), which the unnamed (default) package does not have. So the
 * plugin never guesses: with classes in several packages {@code <packageName>} must name one, and
 * the classes of the other packages are reported as not converted.
 */
final class PackageSelection {

    private static final String DEFAULT_PACKAGE = "(default package)";
    private static final String IDENTIFIER = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern PACKAGE_NAME = Pattern.compile(IDENTIFIER + "(\\." + IDENTIFIER + ")*");

    private final ClassIndex index;
    private final Path classesDir;

    /**
     * @param index      the project classes
     * @param classesDir where they come from (for messages)
     */
    PackageSelection(ClassIndex index, Path classesDir) {
        this.index = index;
        this.classesDir = classesDir;
    }

    /**
     * Selects the package to convert.
     *
     * @param configured the {@code packageName} parameter (dot or internal form), or {@code null}
     * @param log        receives the packages that are not converted
     * @return the package name in dot notation
     * @throws MojoExecutionException if there is nothing to convert, the configured package has no
     *                                classes or is not a package name, or the choice is ambiguous
     */
    String select(String configured, Log log) throws MojoExecutionException {
        List<String> packages = index.packages();
        if (packages.isEmpty()) {
            throw new MojoExecutionException("No class files in " + classesDir + ": nothing to convert.");
        }
        String selected = configured == null || configured.isBlank()
                ? detect(packages) : checkConfigured(configured.trim().replace('/', '.'), packages);
        for (String other : packages) {
            if (!other.equals(selected)) {
                log.warn("Package " + describe(other) + " is not part of this CAP file: a CAP file holds one"
                        + " package (JCVM 3.1 §4.1.2). Convert it with another plugin execution.");
            }
        }
        return selected;
    }

    private String detect(List<String> packages) throws MojoExecutionException {
        if (packages.size() > 1) {
            throw new MojoExecutionException("Classes found in " + packages.size() + " packages: "
                    + packages.stream().map(this::describe).collect(Collectors.joining(", "))
                    + ". A CAP file holds one package (JCVM 3.1 §4.1.2)" + advice(packages));
        }
        String only = packages.getFirst();
        if (only.isEmpty()) {
            throw new MojoExecutionException("Classes in the default package cannot be converted ("
                    + names(only) + "): a Java Card package needs a name, its CAP file components are stored"
                    + " in the package's directory (JCVM 3.1 §4.1.3). Move the classes into a named package.");
        }
        return only;
    }

    /**
     * What to do about several packages. A package that uses another one needs that package's export
     * file to be converted (JCVM 3.1 §4.1.1), so {@code <packageName>} alone only moves the failure
     * to the link check; for a helper package, moving its classes is the simple way out.
     */
    private String advice(List<String> packages) {
        for (String user : packages) {
            List<String> used = usedPackages(user, packages);
            if (!used.isEmpty()) {
                String helper = used.getFirst();
                return ", and " + user + " uses " + helper + ": move the classes of " + helper + " into " + user
                        + ". To keep two packages, build " + helper + " first, in a plugin execution of its own"
                        + " (<packageName>, <classifier>), and name its export file in <importExportFiles> of the"
                        + " execution for " + user + ".";
            }
        }
        return ": set <packageName> to the package to convert, with one plugin execution per package and a"
                + " <classifier> for each, or move the classes into one package.";
    }

    /** The other packages of the module that the classes of a package refer to. */
    private List<String> usedPackages(String user, List<String> packages) {
        return index.classesOf(user).stream()
                .flatMap(c -> c.referencedPackages().stream())
                .filter(p -> !p.equals(user) && packages.contains(p))
                .distinct().sorted().toList();
    }

    private String checkConfigured(String name, List<String> packages) throws MojoExecutionException {
        if (!PACKAGE_NAME.matcher(name).matches()) {
            throw new MojoExecutionException("<packageName> '" + name + "' is not a Java package name"
                    + " (dot notation, e.g. com.example.wallet).");
        }
        if (!packages.contains(name)) {
            throw new MojoExecutionException("<packageName> " + name + " has no classes in " + classesDir
                    + ". Packages with classes: " + packages.stream().map(this::describe)
                    .collect(Collectors.joining(", ")) + ".");
        }
        return name;
    }

    /** E.g. {@code com.example.wallet (4 classes, applets com.example.wallet.WalletApplet)}. */
    private String describe(String packageName) {
        int classes = index.classesOf(packageName).size();
        List<String> applets = new AppletClasses(index, packageName).applets();
        String label = packageName.isEmpty() ? DEFAULT_PACKAGE : packageName;
        return label + " (" + classes + (classes == 1 ? " class" : " classes")
                + (applets.isEmpty() ? "" : (applets.size() == 1 ? ", applet " : ", applets ")
                + String.join(", ", applets)) + ")";
    }

    private String names(String packageName) {
        return index.classesOf(packageName).stream().map(ClassSummary::javaName).collect(Collectors.joining(", "));
    }
}
