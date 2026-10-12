package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.SubsetChecker;
import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformImport;
import name.velikodniy.jcexpress.converter.resolve.ImportLoader;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.resolve.LinkChecker;

import java.lang.classfile.ClassFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The checks of the converter's scan stage on the class files of one package, without converting them, for tools
 * that check code while it is compiled: the Java Card language subset (JCVM 3.1 §2.2, {@link SubsetChecker}) and,
 * when the subset holds, the linking of the references into the Java Card API, the GlobalPlatform Card API
 * ({@code org.globalplatform}), the packages of the Java SE platform and the packages whose export files the build
 * supplies (§4.3.3, §4.3.5, §4.5.2). The export files are found as the conversion finds them: a supplied file
 * replaces the converter's built-in data of its package. The rules and the messages are the converter's; where
 * {@link Converter#convert()} stops at the first stage that fails, this returns every problem of the stage with the
 * class, the member and the source line it comes from, and a problem the conversion reports once for a method only
 * once for each line of it.
 *
 * <p>References into other packages without a supplied export file are not checked: their export files are not
 * known here (§4.3.3); the conversion checks them. The same holds for a method a class of the package inherits while
 * one of its supertypes is in such a package: only that package's export file tells whether it declares the method,
 * so the reference is left to the conversion even where a built-in supertype lacks the method on the target. Left
 * out of the check are also the rules of the scan stage that depend on the conversion's configuration (the applets,
 * their {@code install} methods and the AIDs, §2.2.5, §4.2) and Java Card RMI (§2.2.6), and the later stages: static
 * initialization (§2.2.4.6), tokens (§4.3.7) and the limits of the CAP file format. The conversion checks all of
 * them.</p>
 */
public final class PackageCheck {

    private PackageCheck() {
    }

    /**
     * The settings of the conversion the check stands for.
     *
     * @param version           the target platform (its API for the link check)
     * @param supportInt32      whether the target supports the int type (JCVM 3.1 §2.2.3.1)
     * @param globalPlatformApi the version of {@code org.globalplatform} the conversion links against, or
     *                          {@code null} for the lowest version that has everything the package uses (§4.5.2)
     * @param exportFiles       export files the conversion is given for imported packages; each replaces the
     *                          built-in data of its package (§4.3.3)
     * @param exportPath        export path entries the conversion searches for the export files of imported
     *                          packages: directories or JAR files with {@code <package>/javacard/<name>.exp} (§5.2)
     */
    public record Settings(JavaCardVersion version, boolean supportInt32, String globalPlatformApi,
                           List<Path> exportFiles, List<Path> exportPath) {

        /**
         * Checks the settings.
         *
         * @throws NullPointerException     if {@code version} or a list is {@code null}
         * @throws IllegalArgumentException if the built-in data has no such version of {@code org.globalplatform}
         */
        public Settings {
            Objects.requireNonNull(version, "version");
            if (globalPlatformApi != null) {
                globalPlatformApi = GlobalPlatformApi.requireVersion(globalPlatformApi);
            }
            exportFiles = List.copyOf(exportFiles);
            exportPath = List.copyOf(exportPath);
        }

        /**
         * Settings without supplied export files: the converter's built-in data only.
         *
         * @param version           the target platform
         * @param supportInt32      whether the target supports the int type
         * @param globalPlatformApi the version of {@code org.globalplatform}, or {@code null} for the lowest
         *                          sufficient one
         */
        public Settings(JavaCardVersion version, boolean supportInt32, String globalPlatformApi) {
            this(version, supportInt32, globalPlatformApi, List.of(), List.of());
        }
    }

    /**
     * One problem the conversion of the package would report.
     *
     * @param className   internal name of the class whose class file has the problem
     * @param member      where in the class, as {@link Violation#context()} names it: {@code class},
     *                    {@code superclass}, {@code interface <name>}, {@code field <name>}, or a method as
     *                    {@code <name><descriptor>}
     * @param line        the source line, or -1 if the class file does not tell it (a declaration, or code compiled
     *                    without a LineNumberTable, JVMS §4.7.12)
     * @param declaration whether the problem is in the declaration of the member (its modifiers or signature) rather
     *                    than in its code; {@code line} is then at most a line of the member. A problem in a catch
     *                    clause is in the code, at the line of the clause; code without line numbers is still code
     * @param message     the converter's message, with the section of the specification it applies
     */
    public record Problem(String className, String member, int line, boolean declaration, String message) {

        static Problem of(Violation violation) {
            return new Problem(violation.className(), violation.context(), violation.line(), violation.bci() < 0,
                    violation.message());
        }

        static Problem of(LinkChecker.Problem problem) {
            ClassReferences.Site site = problem.reference().site();
            return new Problem(site.className(), site.member(), site.line(), !site.code(), problem.message());
        }
    }

    /**
     * Checks the class files of a package as the conversion of the package checks them.
     *
     * @param packageName the package in dot notation, e.g. {@code com.example.wallet}
     * @param classFiles  the class files of every class of the package
     * @param settings    the settings of the conversion
     * @return every problem; the link problems only when the subset holds, as in the conversion; empty if the
     *         package can be converted as far as these checks go
     * @throws ConverterException       if a supplied export file cannot be read, or the supplied export files
     *                                  contradict each other or the settings: the conversion fails with the same
     *                                  message
     * @throws IllegalArgumentException if a class file cannot be parsed
     */
    public static List<Problem> check(String packageName, Collection<byte[]> classFiles, Settings settings)
            throws ConverterException {
        return check(packageName, classFiles, settings, className -> true);
    }

    /**
     * Checks the class files of a package for a compilation that compiled only some of its classes, such as an
     * incremental build. The conversion links the package only when all its classes keep the subset (JCVM 3.1 §2.2);
     * here the link problems are also returned when only classes that were not compiled now break the subset, so
     * that a problem left in an earlier class file does not hide those of the classes just compiled.
     *
     * @param packageName the package in dot notation
     * @param classFiles  the class files of every class of the package
     * @param settings    the settings of the conversion
     * @param compiled    whether a class, by internal name, was compiled now
     * @return every problem of the subset and, when the classes compiled now keep it, every link problem
     * @throws ConverterException       if a supplied export file cannot be read, or the supplied export files
     *                                  contradict each other or the settings
     * @throws IllegalArgumentException if a class file cannot be parsed
     */
    public static List<Problem> check(String packageName, Collection<byte[]> classFiles, Settings settings,
                                      Predicate<String> compiled) throws ConverterException {
        Map<String, byte[]> files = byName(classFiles);
        try {
            files = SubroutineInliner.inline(files);
        } catch (ConverterException e) {
            return e.violations().stream().map(Problem::of).toList();
        }
        PackageInfo info = PackageScanner.read(packageName, files.values());
        List<Problem> problems = new ArrayList<>(subset(info, files, settings));
        if (problems.stream().noneMatch(problem -> compiled.test(problem.className()))) {
            problems.addAll(linkProblems(info, List.copyOf(files.values()), settings));
        }
        return List.copyOf(problems);
    }

    /**
     * Checks the class files of a package against the Java Card language subset only (JCVM 3.1 §2.2), for a build
     * whose export files are not all there yet: the linking is then left to the conversion.
     *
     * @param packageName the package in dot notation
     * @param classFiles  the class files of every class of the package
     * @param settings    the settings of the conversion (its int support)
     * @return every problem of the subset
     * @throws IllegalArgumentException if a class file cannot be parsed
     */
    public static List<Problem> checkSubset(String packageName, Collection<byte[]> classFiles, Settings settings) {
        Map<String, byte[]> files = byName(classFiles);
        try {
            files = SubroutineInliner.inline(files);
        } catch (ConverterException e) {
            return e.violations().stream().map(Problem::of).toList();
        }
        return subset(PackageScanner.read(packageName, files.values()), files, settings);
    }

    private static List<Problem> subset(PackageInfo info, Map<String, byte[]> files, Settings settings) {
        CatchClauses catches = new CatchClauses(files);
        return SubsetChecker.check(info.classes(), settings.supportInt32()).stream().map(catches::place).toList();
    }

    private static List<Problem> linkProblems(PackageInfo info, List<byte[]> files, Settings settings)
            throws ConverterException {
        List<Reference> references = ClassReferences.scan(files);
        Set<String> referenced = ClassReferences.packages(references, info.internalName());
        List<ImportedPackage> imports = GlobalPlatformImport.select(settings.globalPlatformApi(),
                ImportLoader.load(settings.version(), settings.globalPlatformApi(), settings.exportFiles(),
                        settings.exportPath(), referenced), references, info.classes(), settings.version());
        Set<String> imported = new HashSet<>();
        imports.forEach(imp -> imported.add(imp.exportFile().packageName()));
        Set<String> reported = new HashSet<>();
        return LinkChecker.problems(references, imports, info.classes(), settings.version()).stream()
                .filter(problem -> knownHere(problem.packageName(), info.internalName(), imported))
                // the conversion reports each message once per location (LinkChecker#check), a method; here once
                // per line of it, so that two catch clauses of one class are two places
                .filter(problem -> reported.add(problem.reference().location() + "@" + problem.reference().site().line()
                        + ": " + problem.message()))
                .map(Problem::of)
                .toList();
    }

    /**
     * Whether the conversion's answer about a package is known without the build's other export files: the Java
     * Card API and {@code org.globalplatform} have built-in data, the build supplied an export file for the package,
     * or the package is one of the Java SE platform, which is not part of the Java Card platform (JCVM 3.1
     * §2.2.1.4). A class of the checked package without a class file is a matter of the classes directory, not of
     * the code.
     */
    private static boolean knownHere(String packageName, String checkedPackage, Set<String> imported) {
        if (packageName.equals(checkedPackage)) {
            return false;
        }
        return BuiltinExports.isBuiltinPackage(packageName) || packageName.equals(GlobalPlatformApi.PACKAGE_NAME)
                || imported.contains(packageName) || LinkChecker.isJavaSePackage(packageName);
    }

    private static Map<String, byte[]> byName(Collection<byte[]> classFiles) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (byte[] bytes : classFiles) {
            files.put(ClassFile.of().parse(bytes).thisClass().asInternalName(), bytes);
        }
        return files;
    }
}
