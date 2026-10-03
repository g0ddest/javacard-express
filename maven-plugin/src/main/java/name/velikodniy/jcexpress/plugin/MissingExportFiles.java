package name.velikodniy.jcexpress.plugin;

import org.apache.maven.artifact.Artifact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports packages that the converted package uses but whose export file is missing (JCVM 3.1
 * &sect;4.1.1: converting a package that imports another one needs the export file of the imported
 * package). The converter's link check lists every reference; this report names each package
 * once, shows where the code uses it, and says what to add, depending on where the package's
 * classes come from: the export file of the dependency that holds them, or, for a package compiled
 * in the same module, how to build it first.
 */
final class MissingExportFiles {

    private static final String LINK_FAILURE = "Cannot link ";
    private static final Pattern MISSING = Pattern.compile(
            ": no export file was supplied for package ([\\w$.]+) \\(use importExportFile or exportPath\\)$");
    private static final int MAX_USAGES = 10;
    private static final String SPEC = "JCVM 3.1 §4.1.1";

    private final SourceLocations sources;
    private final PackageOrigins origins;
    private final String packageName;

    /**
     * @param sources     source locations of the module's classes
     * @param origins     where the classes of other packages come from
     * @param packageName the converted package (dot notation)
     */
    MissingExportFiles(SourceLocations sources, PackageOrigins origins, String packageName) {
        this.sources = sources;
        this.origins = origins;
        this.packageName = packageName;
    }

    /**
     * The report: one paragraph per package without an export file, and the converter's own lines
     * for the other references it cannot link.
     *
     * @param packages  one paragraph per package without an export file
     * @param remaining the converter's message for the other references, if any
     */
    record Report(String packages, Optional<String> remaining) {
    }

    /**
     * @param converterMessage the message of the converter's failure
     * @return the report, or empty if the failure is not about packages without an export file
     */
    Optional<Report> report(String converterMessage) {
        List<String> lines = converterMessage.lines().toList();
        if (lines.isEmpty() || !lines.getFirst().startsWith(LINK_FAILURE)) {
            return Optional.empty();
        }
        Map<String, Integer> missing = new LinkedHashMap<>();
        List<String> others = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            Matcher m = MISSING.matcher(line);
            if (m.find()) {
                missing.merge(m.group(1), 1, Integer::sum);
            } else {
                others.add(line);
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        List<String> paragraphs = new ArrayList<>();
        missing.forEach((pkg, references) -> paragraphs.add(paragraph(pkg, references)));
        Optional<String> remaining = others.isEmpty() ? Optional.empty() : Optional.of(lines.getFirst()
                .replaceFirst("^Cannot link \\d+ ", "Cannot link " + others.size() + " other ") + "\n"
                + String.join("\n", others));
        return Optional.of(new Report(String.join("\n", paragraphs), remaining));
    }

    private String paragraph(String pkg, int references) {
        return "Cannot convert package " + packageName + ": no export file for package " + pkg + ", which it refers to "
                + references + (references == 1 ? " time" : " times") + usages(pkg) + "\n"
                + advice(pkg, origins.of(pkg));
    }

    /** Where the code uses the classes of a package, at most {@value #MAX_USAGES} places. */
    private String usages(String pkg) {
        Set<String> classes = sources.referencedClasses(packageName, name -> packageOf(name).equals(pkg));
        List<String> lines = new ArrayList<>();
        for (String className : classes) {
            for (SourceLocations.Location location : sources.usages(packageName, className::equals, MAX_USAGES)) {
                lines.add("\n  " + className.replace('/', '.') + " is used at " + sources.format(location));
            }
        }
        String shown = String.join("", lines.subList(0, Math.min(lines.size(), MAX_USAGES)));
        return (lines.isEmpty() ? "." : ":" + shown)
                + (lines.size() > MAX_USAGES ? "\n  (" + (lines.size() - MAX_USAGES) + " more)" : "");
    }

    private String advice(String pkg, PackageOrigins.Origin origin) {
        return switch (origin) {
            case PackageOrigins.FromDependency d -> fromDependency(pkg, d.artifact());
            case PackageOrigins.ThisModule ignored -> "The classes of " + pkg + " are compiled in this module, but a"
                    + " CAP file holds one package (JCVM 3.1 §4.1.2): move them into " + packageName + ", or build "
                    + pkg + " first, in a plugin execution of its own (<packageName>, <classifier>), and name its"
                    + " export file in <importExportFiles> of this execution.";
            case PackageOrigins.Unknown ignored -> "The converter needs the export file of " + pkg + " (" + SPEC
                    + "). Add the library with the package to the dependencies (scope provided): a library built by"
                    + " javacard-express-maven-plugin has the export file in its jar (one built by an earlier version"
                    + " attaches it: add the same dependency with <type>exp</type> as well). Or name the export file in"
                    + " <importExportFiles>, or a directory or jar that contains " + ExportFileLookup.location(pkg)
                    + " in <exportPath>.";
        };
    }

    private static String fromDependency(String pkg, Artifact artifact) {
        return "The classes of " + pkg + " come from " + artifact.getGroupId() + ":" + artifact.getArtifactId()
                + ":" + artifact.getBaseVersion() + ", which has no export file at " + ExportFileLookup.location(pkg)
                + ". The converter needs the package's export file as well (" + SPEC + "). This version of"
                + " javacard-express-maven-plugin writes it there when it builds a library; for a library built by"
                + " an earlier version, add the export file it attaches as a dependency:\n"
                + "<dependency>\n"
                + "    <groupId>" + artifact.getGroupId() + "</groupId>\n"
                + "    <artifactId>" + artifact.getArtifactId() + "</artifactId>\n"
                + "    <version>" + artifact.getBaseVersion() + "</version>\n"
                + "    <type>exp</type>\n"
                + "    <scope>provided</scope>\n"
                + "</dependency>\n"
                + "In a multi-module build Maven has that file from the library's package phase on: build with"
                + " mvn package, verify or install. Otherwise name the export file in <importExportFiles>, or a"
                + " directory or jar that contains " + ExportFileLookup.location(pkg) + " in <exportPath>.";
    }

    private static String packageOf(String className) {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash).replace('/', '.');
    }
}
