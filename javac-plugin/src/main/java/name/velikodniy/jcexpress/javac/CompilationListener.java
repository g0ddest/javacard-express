package name.velikodniy.jcexpress.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.PackageCheck;

import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Follows a javac compilation: records the source trees of every analysed class and the classes javac writes, and
 * when the compilation is complete checks the chosen packages with the converter's rules ({@link PackageCheck}) and
 * reports each problem as a compile error at its source line. The class files of a source with errors are removed,
 * as javac writes none for a source with errors, so that the next incremental build compiles and reports it again.
 *
 * <p>Nothing the check does stops the compilation by failing: a class output it cannot read is a note that the check
 * did not run, a class file it cannot parse or any other failure a warning that names the package or the file.</p>
 */
final class CompilationListener implements TaskListener {

    /** Marks the errors of this check among javac's own. */
    static final String PREFIX = JavaCardCheckProcessor.PREFIX;

    private final CheckOptions options;
    private final ClassOutput output;
    private final Messager messager;
    private final Trees trees;
    private final Elements elements;
    private final Types types;
    private final SourceIndex sources;
    private final Map<String, TypeElement> generated = new LinkedHashMap<>();
    private final Map<String, CompilationUnitTree> units = new LinkedHashMap<>();
    private boolean failed;

    CompilationListener(CheckOptions options, ClassOutput output, Messager messager, Trees trees, Elements elements,
                        Types types) {
        this.options = options;
        this.output = output;
        this.messager = messager;
        this.trees = trees;
        this.elements = elements;
        this.types = types;
        this.sources = new SourceIndex(trees, elements, types);
    }

    @Override
    public void finished(TaskEvent event) {
        if (failed) {
            return;
        }
        try {
            switch (event.getKind()) {
                case ANALYZE -> sources.add(event.getTypeElement(), event.getCompilationUnit());
                case GENERATE -> generated(event);
                case COMPILATION -> check();
                default -> {
                    // parsing, entering and annotation processing tell nothing about class files
                }
            }
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            messager.printMessage(Diagnostic.Kind.WARNING, PREFIX + "the check failed: "
                    + JavaCardCheckProcessor.describe(e));
        }
    }

    private void generated(TaskEvent event) {
        TypeElement type = event.getTypeElement();
        if (type != null) {
            String name = elements.getBinaryName(type).toString();
            generated.put(name, type);
            units.put(name, event.getCompilationUnit());
        }
    }

    /**
     * Checks the chosen packages, once javac has written every class file. When the class output does not have
     * them (no class output directory, class files kept in memory), the check cannot run, and says so in a note.
     */
    private void check() {
        if (generated.isEmpty()) {
            return;
        }
        Set<String> packages;
        try {
            output.read(generated.keySet().iterator().next());
            packages = new CheckedPackages(options.packageNames(), output, elements, types).select(generated.values());
        } catch (UncheckedIOException e) {
            messager.printMessage(Diagnostic.Kind.NOTE, PREFIX + "the check did not run: " + e.getMessage());
            return;
        }
        if (!packages.isEmpty() && !options.notSupplied().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.NOTE, PREFIX + "not there (yet): " + String.join(", ",
                    options.notSupplied()) + "; only the language subset is checked, the linking is left to the conversion");
        }
        packages.forEach(this::check);
    }

    /** Checks one package; a package that cannot be checked is a note or a warning naming it. */
    private void check(String packageName) {
        try {
            Map<String, byte[]> compiled = new LinkedHashMap<>();
            generated.keySet().stream().filter(name -> packageOf(name).equals(packageName))
                    .forEach(name -> compiled.put(name, output.read(name)));
            report(compiled, problems(packageName, compiled));
        } catch (ClassOutput.UnreadableClassFile e) {
            messager.printMessage(Diagnostic.Kind.WARNING, PREFIX + "the check could not read " + e.file() + ": "
                    + e.getMessage());
        } catch (UncheckedIOException e) {
            messager.printMessage(Diagnostic.Kind.NOTE, PREFIX + "the check of package " + packageName
                    + " did not run: " + e.getMessage());
        } catch (ConverterException e) {
            // the export files the build supplies cannot be used: the conversion fails with the same message; as for
            // any error, the package's class files go, so that the next incremental build reports it again
            messager.printMessage(Diagnostic.Kind.ERROR, PREFIX + "package " + packageName + ": " + e.getMessage());
            generated.keySet().stream().filter(name -> packageOf(name).equals(packageName)).forEach(output::delete);
        } catch (RuntimeException | LinkageError e) {
            messager.printMessage(Diagnostic.Kind.WARNING, PREFIX + "the check of package " + packageName
                    + " failed: " + JavaCardCheckProcessor.describe(e));
        }
    }

    /**
     * The problems of a package: of the subset alone while export files or export path entries are not there yet,
     * otherwise also of the linking of the classes compiled now.
     */
    private List<PackageCheck.Problem> problems(String packageName, Map<String, byte[]> compiled)
            throws ConverterException {
        Collection<byte[]> classFiles = output.packageClasses(packageName, compiled).values();
        if (!options.notSupplied().isEmpty()) {
            return PackageCheck.checkSubset(packageName, classFiles, options.settings());
        }
        return PackageCheck.check(packageName, classFiles, options.settings(),
                className -> compiled.containsKey(className.replace('/', '.')));
    }

    /**
     * Reports the problems of the classes of this compilation at their places, and in one note which other classes of
     * the package the conversion will also report.
     */
    private void report(Map<String, byte[]> compiled, List<PackageCheck.Problem> problems) {
        Set<CompilationUnitTree> failedUnits = new LinkedHashSet<>();
        Set<String> reported = new HashSet<>();
        Set<String> others = new TreeSet<>();
        for (PackageCheck.Problem problem : problems) {
            String name = problem.className().replace('/', '.');
            if (!compiled.containsKey(name)) {
                others.add(name);
                continue;
            }
            CompilationUnitTree unit = units.get(name);
            SourceIndex.Place place = sources.locate(problem, unit);
            // one error per line and message: a field's type and its initializer give the same one twice
            if (reported.add(line(place) + ":" + problem.message())) {
                trees.printMessage(Diagnostic.Kind.ERROR, PREFIX + problem.message(), place.tree(), place.unit());
            }
            failedUnits.add(unit);
        }
        if (!others.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.NOTE, PREFIX + "the conversion also reports problems in classes"
                    + " this compilation did not compile: " + String.join(", ", others));
        }
        units.forEach((name, unit) -> {
            if (failedUnits.contains(unit)) {
                output.delete(name);
            }
        });
    }

    /** The line of a place, or its position where javac keeps no line map (compiled without line numbers). */
    private String line(SourceIndex.Place place) {
        long start = trees.getSourcePositions().getStartPosition(place.unit(), place.tree());
        LineMap lines = place.unit().getLineMap();
        return place.unit().getSourceFile().toUri() + ":" + (start < 0 ? -1
                : lines == null ? "@" + start : lines.getLineNumber(start));
    }

    private static String packageOf(String binaryName) {
        int dot = binaryName.lastIndexOf('.');
        return dot < 0 ? "" : binaryName.substring(0, dot);
    }
}
