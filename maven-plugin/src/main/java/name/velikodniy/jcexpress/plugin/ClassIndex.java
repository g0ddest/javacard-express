package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The compiled classes of the project, plus on-demand lookups of their supertypes on the compile
 * class path. Answers the questions the plugin asks before conversion: which classes are applets
 * (non-abstract subclasses of {@code javacard.framework.Applet}, directly or indirectly, JCVM 3.1
 * &sect;6.6) and which interfaces are shareable ({@code javacard.framework.Shareable} or an
 * interface extending it, &sect;6.13).
 */
final class ClassIndex implements AutoCloseable {

    static final String APPLET = "javacard/framework/Applet";
    static final String SHAREABLE = "javacard/framework/Shareable";
    private static final String JAVA_PACKAGES = "java/";

    private final Path classesDir;
    private final Map<String, ClassSummary> projectClasses;
    private final ClassPathLookup classPath;
    private final Map<String, Optional<ClassSummary>> resolved = new HashMap<>();

    private ClassIndex(Path classesDir, Map<String, ClassSummary> projectClasses, ClassPathLookup classPath) {
        this.classesDir = classesDir;
        this.projectClasses = projectClasses;
        this.classPath = classPath;
    }

    /**
     * Reads every class file below the classes directory ({@code module-info.class} and
     * {@code META-INF} are skipped).
     *
     * @param classesDir the compiled classes of the project
     * @param classPath  further class path entries used to resolve supertypes
     * @return the index
     * @throws MojoExecutionException if a class file cannot be read or parsed
     */
    static ClassIndex scan(Path classesDir, List<Path> classPath) throws MojoExecutionException {
        Map<String, ClassSummary> classes = new TreeMap<>();
        for (Path file : classFiles(classesDir)) {
            ClassSummary summary = parse(file);
            classes.put(summary.name(), summary);
        }
        return new ClassIndex(classesDir, classes, new ClassPathLookup(classPath));
    }

    private static List<Path> classFiles(Path classesDir) throws MojoExecutionException {
        try (Stream<Path> files = Files.walk(classesDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".class"))
                    .filter(p -> !p.getFileName().toString().equals("module-info.class"))
                    .filter(p -> !classesDir.relativize(p).startsWith("META-INF"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot list the class files in " + classesDir + ": " + e.getMessage(), e);
        }
    }

    private static ClassSummary parse(Path file) throws MojoExecutionException {
        try {
            return ClassSummary.parse(Files.readAllBytes(file));
        } catch (IOException | IllegalArgumentException e) {
            throw new MojoExecutionException("Cannot read class file " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads the class file of a project class again (for the details the summary leaves out).
     *
     * @param type a project class
     * @return the class file bytes, or empty if the file cannot be read any more
     */
    Optional<byte[]> classFile(ClassSummary type) {
        try {
            return Optional.of(Files.readAllBytes(classesDir.resolve(type.name() + ".class")));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * @param packageName package in dot notation ({@code ""} for the default package)
     * @return the project classes of that package, sorted by name
     */
    List<ClassSummary> classesOf(String packageName) {
        return projectClasses.values().stream().filter(c -> c.packageName().equals(packageName)).toList();
    }

    /**
     * Finds a class of the project.
     *
     * @param internalName internal class name
     * @return the class, or empty if the project has no such class
     */
    Optional<ClassSummary> projectClass(String internalName) {
        return Optional.ofNullable(projectClasses.get(internalName));
    }

    /**
     * Finds a class of the project or, failing that, of the compile class path.
     *
     * @param internalName internal class name
     * @return the class, or empty if it is on neither
     */
    Optional<ClassSummary> find(String internalName) {
        ClassSummary own = projectClasses.get(internalName);
        if (own != null) {
            return Optional.of(own);
        }
        return resolved.computeIfAbsent(internalName, this::loadFromClassPath);
    }

    private Optional<ClassSummary> loadFromClassPath(String internalName) {
        try {
            return classPath.read(internalName).map(ClassSummary::parse);
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Walks the superclass chain of a class through the project classes and the compile class path.
     * The walk ends at {@code javacard.framework.Applet}, at a {@code java.*} class (those never
     * extend a Java Card API class), or at a class that cannot be found.
     *
     * @param type a class
     * @return whether the class extends {@code javacard.framework.Applet} directly or indirectly,
     *         and the first superclass that could not be found, if the walk stopped there
     */
    SuperclassChain superclassChain(ClassSummary type) {
        Set<String> seen = new HashSet<>();
        String superName = type.superName();
        while (superName != null && seen.add(superName)) {
            if (APPLET.equals(superName)) {
                return new SuperclassChain(true, null);
            }
            if (superName.startsWith(JAVA_PACKAGES)) {
                break;
            }
            Optional<ClassSummary> superclass = find(superName);
            if (superclass.isEmpty()) {
                return new SuperclassChain(false, superName);
            }
            superName = superclass.get().superName();
        }
        return new SuperclassChain(false, null);
    }

    /**
     * @param type a class or interface
     * @return {@code true} if it is a shareable interface (JCVM 3.1 &sect;6.13)
     */
    boolean isShareableInterface(ClassSummary type) {
        return type.isInterface() && extendsShareable(type.name(), new HashSet<>());
    }

    private boolean extendsShareable(String interfaceName, Set<String> seen) {
        if (SHAREABLE.equals(interfaceName)) {
            return true;
        }
        if (!seen.add(interfaceName)) {
            return false;
        }
        List<String> supers = find(interfaceName).map(ClassSummary::interfaces).orElse(List.of());
        return supers.stream().anyMatch(s -> extendsShareable(s, seen));
    }

    /**
     * @return the packages (dot notation) that contain project classes, sorted
     */
    List<String> packages() {
        return new ArrayList<>(new TreeMap<>(groupByPackage()).keySet());
    }

    private Map<String, List<ClassSummary>> groupByPackage() {
        Map<String, List<ClassSummary>> byPackage = new HashMap<>();
        projectClasses.values().forEach(c ->
                byPackage.computeIfAbsent(c.packageName(), k -> new ArrayList<>()).add(c));
        return byPackage;
    }

    /**
     * The result of walking a superclass chain.
     *
     * @param extendsApplet {@code true} if {@code javacard.framework.Applet} was reached
     * @param missingClass  internal name of the first superclass that is neither a project class
     *                      nor on the compile class path, or {@code null} if the chain is complete
     */
    record SuperclassChain(boolean extendsApplet, String missingClass) {
    }

    @Override
    public void close() {
        classPath.close();
    }
}
