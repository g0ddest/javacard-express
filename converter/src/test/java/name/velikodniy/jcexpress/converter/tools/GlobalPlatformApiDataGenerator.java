package name.velikodniy.jcexpress.converter.tools;

import name.velikodniy.jcexpress.converter.testutil.GlobalPlatformExports;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Developer tool that regenerates the built-in linking table of the GlobalPlatform Card API package
 * {@code org.globalplatform} ({@code globalplatform-api-exports.txt}, see
 * {@link name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi GlobalPlatformApi}).
 *
 * <p>It reads the export files of the package versions installed locally (see {@link GlobalPlatformExports}) with
 * this project's {@link name.velikodniy.jcexpress.converter.token.ExportFileReader ExportFileReader} and records,
 * for every class and method of the newest version, its token, its flags and the first version that has it.
 * Fields are not recorded: every field of the package is a compile-time constant (token {@code 0xFF}), which
 * javac inlines and which needs no linking data. Nothing else of the export files is used, and the files
 * themselves are never copied into the repository.
 *
 * <p>The tool stops when a class or method does not have the same token, flags and descriptor (for a class also
 * the same superclasses and interfaces) in every version that has it, when a later version lacks something an
 * earlier one has, or when a field is not a constant: the table relies on later versions being binary compatible
 * with earlier ones (JCVM 3.1 §4.4).
 *
 * <p>Usage: {@code java -cp target/classes:target/test-classes
 * name.velikodniy.jcexpress.converter.tools.GlobalPlatformApiDataGenerator <exports-directory> <output-file>},
 * where the directory holds {@code <version>/org/globalplatform/javacard/globalplatform.exp}.
 */
public final class GlobalPlatformApiDataGenerator {

    private static final String HEADER = """
            # GlobalPlatform Card API (package org.globalplatform) linking data for the converter's built-in
            # import of that package (GlobalPlatformApi). Tokens, flags, AID and versions are interoperability
            # facts of the API (JCVM 3.1 §4.3.6, §5.3): a CAP file links on a card only if they match. They
            # were read from the export files of the package versions listed below with the project's
            # ExportFileReader by GlobalPlatformApiDataGenerator, and are checked against those files by
            # GlobalPlatformApiExportFilesTest when they are installed locally.
            #
            # One table serves every version: between the versions nothing was removed and no token, flag or
            # descriptor changed (JCVM 3.1 §4.4), so each class and method carries the first version that has it.
            # Fields are not listed: all fields of the package are compile-time constants (token 0xFF), which
            # javac inlines. No other content of the export files is included.
            #
            # package <name> <AID> <version> ...                  (every version of the package, ascending)
            # class <token> <simple-name> <flags> <since> [supers=a,b] [interfaces=a,b]
            # method <token> <flags> <since> <name> <descriptor>
            """;

    private GlobalPlatformApiDataGenerator() {}

    /**
     * Entry point.
     *
     * @param args the directory with the export files and the output file
     * @throws IOException if an export file cannot be read or the table cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GlobalPlatformApiDataGenerator <exports-directory>"
                    + " <output-file>");
        }
        Files.writeString(Path.of(args[1]), generate(GlobalPlatformExports.read(Path.of(args[0]))),
                StandardCharsets.UTF_8);
    }

    /**
     * Writes the table for the given export files.
     *
     * @param versions the export file of each version, in ascending version order
     * @return the text of the table
     * @throws IllegalStateException if the versions are not binary compatible with each other
     */
    public static String generate(Map<String, ExportFile> versions) {
        if (versions.isEmpty()) {
            throw new IllegalStateException("no export files of org.globalplatform were found");
        }
        ExportFile latest = versions.values().stream().reduce((a, b) -> b).orElseThrow();
        versions.forEach((version, export) -> checkPackage(version, export, latest));
        versions.forEach((version, export) -> checkNothingRemoved(version, export, latest));
        StringBuilder out = new StringBuilder(HEADER);
        out.append("\npackage ").append(latest.packageName()).append(' ')
                .append(HexFormat.of().withUpperCase().formatHex(latest.aid()));
        versions.keySet().forEach(version -> out.append(' ').append(version));
        out.append('\n');
        latest.classes().stream().sorted(Comparator.comparingInt(ExportFile.ClassExport::token))
                .forEach(cls -> writeClass(out, cls, versions));
        return out.toString();
    }

    private static void checkPackage(String version, ExportFile export, ExportFile latest) {
        String actual = export.majorVersion() + "." + export.minorVersion();
        require(version.equals(actual), "the export file of version " + version + " declares package version "
                + actual);
        require(export.packageName().equals(latest.packageName()), "version " + version + " describes package "
                + export.packageName());
        require(HexFormat.of().formatHex(export.aid()).equals(HexFormat.of().formatHex(latest.aid())),
                "version " + version + " has another AID than the newest version");
        require(export.packageFlags() == latest.packageFlags(), "version " + version + " has other package flags"
                + " than the newest version");
    }

    /** JCVM 3.1 §4.4: a binary compatible version keeps every class and member with its token. */
    private static void checkNothingRemoved(String version, ExportFile export, ExportFile latest) {
        for (ExportFile.ClassExport cls : export.classes()) {
            ExportFile.ClassExport now = find(latest, cls.name()).orElseThrow(() -> new IllegalStateException(
                    cls.name() + " of version " + version + " is missing in the newest version"));
            for (ExportFile.MethodExport method : cls.methods()) {
                require(now.methods().stream().anyMatch(m -> m.name().equals(method.name())
                        && m.descriptor().equals(method.descriptor())), cls.name() + "." + method.name()
                        + method.descriptor() + " of version " + version + " is missing in the newest version");
            }
            for (ExportFile.FieldExport field : cls.fields()) {
                require(field.token() == ExportFile.CONSTANT_FIELD_TOKEN, cls.name() + "." + field.name()
                        + " of version " + version + " is not a compile-time constant: the table has no fields");
            }
        }
    }

    private static void writeClass(StringBuilder out, ExportFile.ClassExport cls, Map<String, ExportFile> versions) {
        String since = null;
        for (Map.Entry<String, ExportFile> version : versions.entrySet()) {
            Optional<ExportFile.ClassExport> then = find(version.getValue(), cls.name());
            if (then.isEmpty()) {
                require(since == null, cls.name() + " is missing in version " + version.getKey()
                        + " but exists in an earlier one");
                continue;
            }
            since = since == null ? version.getKey() : since;
            requireSameClass(cls, then.get(), version.getKey());
        }
        out.append("class ").append(cls.token()).append(' ').append(cls.simpleName()).append(' ')
                .append(hex(cls.accessFlags())).append(' ').append(since);
        if (!cls.supers().isEmpty()) out.append(" supers=").append(String.join(",", cls.supers()));
        if (!cls.interfaces().isEmpty()) out.append(" interfaces=").append(String.join(",", cls.interfaces()));
        out.append('\n');
        for (ExportFile.MethodExport method : cls.methods()) {
            writeMethod(out, cls, method, versions);
        }
    }

    private static void requireSameClass(ExportFile.ClassExport latest, ExportFile.ClassExport then, String version) {
        require(then.token() == latest.token() && then.accessFlags() == latest.accessFlags(), latest.name()
                + " has another token or other flags in version " + version);
        require(then.supers().equals(latest.supers()) && then.interfaces().equals(latest.interfaces()),
                latest.name() + " has other superclasses or interfaces in version " + version);
    }

    private static void writeMethod(StringBuilder out, ExportFile.ClassExport cls, ExportFile.MethodExport method,
                                    Map<String, ExportFile> versions) {
        String since = null;
        for (Map.Entry<String, ExportFile> version : versions.entrySet()) {
            Optional<ExportFile.MethodExport> then = find(version.getValue(), cls.name()).stream()
                    .flatMap(c -> c.methods().stream())
                    .filter(m -> m.name().equals(method.name()) && m.descriptor().equals(method.descriptor()))
                    .findFirst();
            if (then.isEmpty()) {
                require(since == null, cls.name() + "." + method.name() + method.descriptor()
                        + " is missing in version " + version.getKey() + " but exists in an earlier one");
                continue;
            }
            since = since == null ? version.getKey() : since;
            require(then.get().token() == method.token() && then.get().accessFlags() == method.accessFlags(),
                    cls.name() + "." + method.name() + method.descriptor() + " has another token or other flags"
                            + " in version " + version.getKey());
        }
        out.append("method ").append(method.token()).append(' ').append(hex(method.accessFlags())).append(' ')
                .append(Objects.requireNonNull(since)).append(' ').append(method.name()).append(' ')
                .append(method.descriptor()).append('\n');
    }

    private static Optional<ExportFile.ClassExport> find(ExportFile export, String className) {
        List<ExportFile.ClassExport> found = new ArrayList<>();
        for (ExportFile.ClassExport cls : export.classes()) {
            if (cls.name().equals(className)) found.add(cls);
        }
        require(found.size() <= 1, className + " is exported twice");
        return found.stream().findFirst();
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalStateException("The export files of org.globalplatform are not binary compatible"
                    + " with each other (JCVM 3.1 §4.4): " + problem);
        }
    }

    private static String hex(int flags) {
        return String.format("0x%04X", flags);
    }
}
