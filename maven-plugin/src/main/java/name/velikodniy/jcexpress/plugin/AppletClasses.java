package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds and checks the applet classes of the converted package.
 *
 * <p>JCVM 3.1 &sect;6.6: "Applets are defined by implementing a non-abstract subclass, direct or
 * indirect, of the javacard.framework.Applet class", and the {@code install_method_offset} of each
 * Applet component entry must point at "the static install(byte[],short,byte) method of the
 * applet". A class that is not such an applet must never reach the Applet component: the entry
 * would point at some other method (the converter used to fall back to the first one, usually a
 * constructor).
 */
final class AppletClasses {

    private static final String SPEC = "JCVM 3.1 §6.6";
    private static final String INSTALL = "static install(byte[], short, byte)";

    private final ClassIndex index;
    private final String packageName;

    /**
     * @param index       the project classes
     * @param packageName the converted package (dot notation)
     */
    AppletClasses(ClassIndex index, String packageName) {
        this.index = index;
        this.packageName = packageName;
    }

    /**
     * Finds the applets of the package: every non-abstract direct or indirect subclass of
     * {@code javacard.framework.Applet} that declares the static install method. Subclasses that
     * cannot be installed are reported and left out.
     *
     * @param log receives the classes that look like applets but are not installable
     * @return the applet class names (dot notation), sorted
     */
    List<String> discover(Log log) {
        for (ClassSummary type : candidates()) {
            Optional<String> problem = problem(type);
            if (problem.isPresent() && type.isAbstract()) {
                log.debug("Not an applet: " + type.javaName() + " " + problem.get());
            } else if (problem.isPresent()) {
                log.warn(type.javaName() + " " + problem.get() + ", so it is not registered as an applet ("
                        + SPEC + "). " + hint(type));
            }
        }
        return applets();
    }

    /**
     * Checks the applet classes configured in {@code <applets>}.
     *
     * @param classNames the configured class names (dot notation), in configuration order
     * @throws MojoExecutionException if a class is listed twice, does not exist in the package, or
     *                                is not an installable applet
     */
    void check(List<String> classNames) throws MojoExecutionException {
        Set<String> seen = new HashSet<>();
        for (String className : classNames) {
            if (!seen.add(className)) {
                throw new MojoExecutionException("Applet class " + className + " is listed twice in <applets>.");
            }
            ClassSummary type = configuredClass(className);
            Optional<String> problem = problem(type);
            if (problem.isPresent()) {
                throw new MojoExecutionException("Applet class " + className + " (configured in <applets>) "
                        + problem.get() + ". An applet is a non-abstract subclass of javacard.framework.Applet"
                        + " that declares " + INSTALL + " (" + SPEC + ").");
            }
        }
    }

    /**
     * Reports applets of the package that are missing from {@code <applets>}.
     *
     * @param classNames the configured class names (dot notation)
     * @param log        receives one warning per unlisted applet
     */
    void reportUnlisted(List<String> classNames, Log log) {
        for (ClassSummary type : candidates()) {
            if (problem(type).isEmpty() && !classNames.contains(type.javaName())) {
                log.warn("Applet class " + type.javaName() + " is not listed in <applets>, so the CAP file does not"
                        + " register it. Add it to <applets>, or remove <applets> to register every applet of"
                        + " package " + packageName + ".");
            }
        }
    }

    private ClassSummary configuredClass(String className) throws MojoExecutionException {
        Optional<ClassSummary> type = index.projectClass(className.replace('.', '/'));
        if (type.isEmpty()) {
            throw new MojoExecutionException("Applet class " + className + " (configured in <applets>) was not found"
                    + " in the compiled classes. " + describe(applets()));
        }
        if (!type.get().packageName().equals(packageName)) {
            throw new MojoExecutionException("Applet class " + className + " (configured in <applets>) belongs to"
                    + " package " + type.get().packageName() + ", but the CAP file is built for package " + packageName
                    + ". A CAP file holds one package; build the other package with its own <packageName>.");
        }
        return type.get();
    }

    private String describe(List<String> applets) {
        return applets.isEmpty()
                ? "Package " + packageName + " has no applet classes."
                : "Applet classes of package " + packageName + ": " + String.join(", ", applets) + ".";
    }

    /**
     * @return the installable applets of the package (dot notation), sorted by name, without logging
     */
    List<String> applets() {
        return candidates().stream().filter(type -> problem(type).isEmpty()).map(ClassSummary::javaName).toList();
    }

    /** Classes of the package that are, or might be, subclasses of Applet. */
    private List<ClassSummary> candidates() {
        return index.classesOf(packageName).stream()
                .filter(type -> !type.isInterface())
                .filter(type -> {
                    ClassIndex.SuperclassChain chain = index.superclassChain(type);
                    return chain.extendsApplet() || chain.missingClass() != null;
                })
                .toList();
    }

    /** Why the class is not an installable applet, or empty if it is one. */
    private Optional<String> problem(ClassSummary type) {
        if (type.isInterface()) {
            return Optional.of("is an interface");
        }
        ClassIndex.SuperclassChain chain = index.superclassChain(type);
        if (chain.missingClass() != null) {
            return Optional.of("cannot be checked: its superclass " + chain.missingClass().replace('/', '.')
                    + " is neither a project class nor on the compile class path");
        }
        if (!chain.extendsApplet()) {
            return Optional.of("does not extend javacard.framework.Applet");
        }
        if (type.isAbstract()) {
            return Optional.of("is abstract");
        }
        if (!type.declaresInstall()) {
            return Optional.of("does not declare " + INSTALL);
        }
        return Optional.empty();
    }

    private static String hint(ClassSummary type) {
        return type.declaresInstall() ? ""
                : "Declare 'public static void install(byte[] bArray, short bOffset, byte bLength)' in it, or make it"
                + " abstract if it is a base class.";
    }
}
