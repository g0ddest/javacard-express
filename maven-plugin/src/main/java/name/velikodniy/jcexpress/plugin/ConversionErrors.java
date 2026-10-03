package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.check.Violation;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a failed conversion in terms of the sources. Java Card subset violations are logged
 * once each, with the source file and line of the offending instruction; for other failures the
 * classes named in the converter's message are looked up in the converted package, so the error
 * says which code uses them and how to provide a missing export file.
 */
final class ConversionErrors {

    private static final int MAX_USAGES = 10;
    /** Internal class names such as {@code com/example/lib/ShortMath} in a converter message. */
    private static final Pattern INTERNAL_NAME = Pattern.compile("\\b[A-Za-z_$][\\w$]*(?:/[A-Za-z_$][\\w$]*)+\\b");
    /** {@code Export class not found: String}: a simple class name. */
    private static final Pattern SIMPLE_NAME = Pattern.compile("class not found: ([A-Za-z_$][\\w$]*)");
    /** {@code no export file was supplied for package com.example.lib}: a package (dot notation). */
    private static final Pattern PACKAGE_NAME =
            Pattern.compile("for package (?!of\\b)([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)");

    /** The class whose {@code requireNonNull} javac inserts as a null check. */
    private static final String OBJECTS = "java/util/Objects";
    /** Explains a {@code java.util.Objects} reference the developer did not write (JCVM 3.1 &sect;2.2). */
    private static final String NULL_CHECK_HINT = "\njavac inserts java.util.Objects.requireNonNull to null-check"
            + " the outer instance of an inner (non-static nested) class: at a qualified creation such as"
            + " outer.new Inner() and, with --release 25, in the constructor of every inner class."
            + " The Java Card platform has no java.util: use a static nested class and pass the outer object"
            + " to its constructor (and compile with maven.compiler.release 8).";

    /** {@code local variable 'total' of type int needs int support}: a LocalVariableTable entry. */
    private static final Pattern LOCAL_VARIABLE = Pattern.compile("^local variable '([^']+)'");
    /** Converter wording of the int rules of JCVM 3.1 &sect;2.2.3.1 that int support lifts. */
    private static final List<String> INT_RULES = List.of("needs int support", "int intermediate value");
    /** How to lift the int rules with this plugin. */
    private static final String INT_HINT = " The int type needs <supportInt32>true</supportInt32> in the plugin"
            + " configuration and a card that supports it (JCVM 3.1 §2.2.3.1, §6.4); otherwise keep these values in"
            + " short and byte.";

    private final SourceLocations sources;
    private final MissingExportFiles missingExportFiles;
    private final String packageName;
    private final Log log;

    /**
     * @param sources     source locations of the project classes
     * @param origins     where the classes of other packages come from
     * @param packageName the converted package (dot notation)
     * @param log         receives the violations
     */
    ConversionErrors(SourceLocations sources, PackageOrigins origins, String packageName, Log log) {
        this.sources = sources;
        this.missingExportFiles = new MissingExportFiles(sources, origins, packageName);
        this.packageName = packageName;
        this.log = log;
    }

    /**
     * Reports the failure.
     *
     * <p>Maven prints the message of every cause that the failure message does not already
     * contain; so the converter's own message is kept verbatim (never repeated), and subset
     * violations, which are logged with their source locations, are not chained as a cause.
     *
     * @param e the converter's exception
     * @return the exception that fails the build
     */
    MojoFailureException report(ConverterException e) {
        if (!e.violations().isEmpty()) {
            return violations(e);
        }
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        Optional<MissingExportFiles.Report> missing = missingExportFiles.report(message);
        if (missing.isPresent()) {
            // not chained: Maven would print the converter's list of every reference again
            log.debug(e);
            String packages = missing.get().packages();
            String remaining = missing.get().remaining()
                    .map(m -> "\nCannot convert package " + packageName + ". " + m + usages(m)).orElse("");
            boolean nullChecks = packages.contains(OBJECTS.replace('/', '.') + " is used at");
            return new MojoFailureException(packages + (nullChecks ? NULL_CHECK_HINT : "") + remaining);
        }
        return new MojoFailureException("Cannot convert package " + packageName + ". " + message + usages(message), e);
    }

    private MojoFailureException violations(ConverterException e) {
        Set<String> problems = new LinkedHashSet<>();
        for (Violation v : e.violations()) {
            problems.add(locate(v) + ": " + v.message());
        }
        log.error("Java Card subset violations in package " + packageName + ":");
        problems.forEach(log::error);
        log.debug(e);
        boolean intRules = e.violations().stream().anyMatch(v -> INT_RULES.stream().anyMatch(v.message()::contains));
        return new MojoFailureException("Cannot convert package " + packageName + ": " + problems.size()
                + (problems.size() == 1 ? " use of a feature" : " uses of features")
                + " the Java Card platform does not support (listed above, JCVM 3.1 §2.2)."
                + (intRules ? INT_HINT : ""));
    }

    /** {@code <file>:[<line>] <class>.<method>()} for code, {@code <file> <class> (<context>)} otherwise. */
    private String locate(Violation v) {
        String context = v.context();
        boolean inMethod = context.contains("(");
        String where = location(v, inMethod).map(sources::format).orElse(v.className().replace('/', '.'));
        return inMethod ? where : where + " (" + context + ")";
    }

    /** The instruction of a violation, or for a local variable its declaration (it has no instruction). */
    private Optional<SourceLocations.Location> location(Violation v, boolean inMethod) {
        Matcher variable = LOCAL_VARIABLE.matcher(v.message());
        if (inMethod && v.bci() < 0 && variable.find()) {
            Optional<SourceLocations.Location> declaration =
                    sources.declaration(v.className(), v.context(), variable.group(1));
            if (declaration.isPresent()) {
                return declaration;
            }
        }
        return sources.at(v.className(), inMethod ? v.context() : "", inMethod ? v.bci() : -1);
    }

    /** Where the classes of other packages named in the message are used, plus a hint about export files. */
    private String usages(String message) {
        StringBuilder text = new StringBuilder();
        Set<String> named = namedClasses(message);
        named.addAll(classesOfNamedPackages(message));
        for (String className : named) {
            if (packageOf(className).equals(packageName)) {
                continue;
            }
            for (SourceLocations.Location location : sources.usages(packageName, matcher(className), MAX_USAGES)) {
                text.append("\n  ").append(className.replace('/', '.')).append(" is used at ")
                        .append(sources.format(location));
            }
        }
        if (!text.isEmpty()) {
            text.append("\nIf the class belongs to another Java Card package, provide that package's export file:"
                    + " <importExportFiles>, <exportPath>, or a dependency of type exp (JCVM 3.1 §4.1.1)."
                    + " Classes of the Java SE library that are not part of the Java Card API cannot be used"
                    + " on a card.");
        }
        if (named.contains(OBJECTS)) {
            text.append(NULL_CHECK_HINT);
        }
        return text.toString();
    }

    /** Matches the internal name, or any class with that simple name if the message only gave one. */
    private static Predicate<String> matcher(String className) {
        return className.contains("/") ? className::equals
                : name -> name.substring(name.lastIndexOf('/') + 1).equals(className);
    }

    private static String packageOf(String className) {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash).replace('/', '.');
    }

    /** The classes of each package named in the message that the converted package uses. */
    private Set<String> classesOfNamedPackages(String message) {
        Set<String> classes = new LinkedHashSet<>();
        Matcher m = PACKAGE_NAME.matcher(message);
        while (m.find()) {
            String pkg = m.group(1);
            if (!pkg.equals(packageName)) {
                classes.addAll(sources.referencedClasses(packageName, name -> packageOf(name).equals(pkg)));
            }
        }
        return classes;
    }

    private static Set<String> namedClasses(String message) {
        Set<String> names = new LinkedHashSet<>();
        Matcher internal = INTERNAL_NAME.matcher(message);
        while (internal.find()) {
            names.add(internal.group());
        }
        Matcher simple = SIMPLE_NAME.matcher(message);
        while (simple.find()) {
            names.add(simple.group(1));
        }
        return names;
    }

}
