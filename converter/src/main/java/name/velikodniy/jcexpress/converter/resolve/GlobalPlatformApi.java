package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.exp.ExportFileWriter;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApiData.ApiClass;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApiData.ApiMethod;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApiData.ApiPackage;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApiData.Version;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in export data of the GlobalPlatform Card API package {@code org.globalplatform}, so that an applet that
 * uses {@code GPSystem} or a Security Domain's {@code SecureChannel} converts without GlobalPlatform's export
 * file.
 *
 * <p>Linking against the package needs what its export file says (JCVM 3.1 §4.3.3, chapter 5): the package AID
 * and version recorded in the Import component (§4.5.2, §6.7) and the tokens of its classes and methods (§4.3.7).
 * These values are interoperability facts fixed by the owner of the API (§4.3.6, §5.3); a CAP file links on a card
 * only if they match.
 *
 * <h2>Versions</h2>
 * <p>The package has its own versions, 1.0 to 1.8, independent of the Java Card version: a card of any Java Card
 * version may hold any of them, and it links an import only when its own minor version is not lower than the
 * imported one (§4.5.2). Each later version is binary compatible with the earlier ones (§4.4): classes and methods
 * were only added and no token changed. One table therefore describes every version ({@link #export(String)}),
 * and the converter can import the lowest version that has everything a package uses
 * ({@link GlobalPlatformImport}), which lets the CAP file load onto the most cards.
 *
 * <h2>Provenance</h2>
 * <p>The data lives in the resource {@code globalplatform-api-exports.txt} (parsed by
 * {@link GlobalPlatformApiData}). It was read from the export files of the package versions 1.0 to 1.8 with this
 * project's {@link name.velikodniy.jcexpress.converter.token.ExportFileReader ExportFileReader} by the test tool
 * {@code GlobalPlatformApiDataGenerator}; {@code GlobalPlatformApiExportFilesTest} compares the reconstructed
 * export of every version with those files when they are installed locally. The package AID is also given in
 * GlobalPlatform Card Specification v2.3.1, Annex A.1. Compile-time constants are not included (javac inlines
 * them), nor is anything else of the export files.
 *
 * @see BuiltinExports
 */
public final class GlobalPlatformApi {

    /** Internal name of the package. */
    public static final String PACKAGE_NAME = "org/globalplatform";

    private static final ApiPackage DATA = GlobalPlatformApiData.load();
    /** The export file of every version; {@link #isBuiltin} recognizes the data by these objects. */
    private static final Map<Version, ExportFile> EXPORTS = exports();
    private static final Pattern CLASS_IN_DESCRIPTOR = Pattern.compile("L([^;]+);");

    private GlobalPlatformApi() {}

    /**
     * Returns the AID of the package (GlobalPlatform Card Specification v2.3.1, Annex A.1).
     *
     * @return {@code A00000015100}
     */
    public static byte[] aid() {
        return HexFormat.of().parseHex(DATA.aidHex());
    }

    /**
     * Returns the versions of the package that the built-in data describes.
     *
     * @return the versions as {@code <major>.<minor>}, in ascending order
     */
    public static List<String> versions() {
        return DATA.versions().stream().map(Version::toString).toList();
    }

    /**
     * Returns the newest version of the package that the built-in data describes.
     *
     * @return the last of {@link #versions()}
     */
    public static String latest() {
        return DATA.versions().getLast().toString();
    }

    /**
     * Checks that the built-in data describes a version.
     *
     * @param version the version as {@code <major>.<minor>}, e.g. {@code "1.5"}
     * @return the version in its canonical text
     * @throws IllegalArgumentException if the text is not a version of the package; the message lists the versions
     */
    public static String requireVersion(String version) {
        return known(version).toString();
    }

    /**
     * Returns the export file of a version of the package, reconstructed from the built-in data (JCVM 3.1 §5.5):
     * the classes and methods that version has, with the package version of that version.
     *
     * @param version the version as {@code <major>.<minor>}
     * @return the export file content (without the constant fields); the same object at every call
     * @throws IllegalArgumentException if the built-in data has no such version
     */
    public static ExportFile export(String version) {
        return EXPORTS.get(known(version));
    }

    /**
     * Writes the export file of a version of the package in the export file format of a target platform (2.1, or
     * 2.3 for Java Card 3.1 and later, JCVM 3.1 §5.5), for tools that take export files as files, like an off-card
     * verifier. The file has the classes and methods of {@link #export(String)} and no fields; in format 2.3 it
     * references {@code java.lang} and {@code javacard.framework} in the versions of the target's API.
     *
     * @param version the version as {@code <major>.<minor>}
     * @param target  the target platform, which decides the export file format
     * @return the bytes of the export file
     * @throws IllegalArgumentException if the built-in data has no such version
     */
    public static byte[] exportFileBytes(String version, JavaCardVersion target) {
        ExportFile view = export(version);
        boolean withReferences = target.exportFormatMinor() >= 3;
        return ExportFileWriter.write(new ExportFile(view.packageName(), view.aid(), view.majorVersion(),
                view.minorVersion(), view.classes(), view.packageFlags(), target.exportFormatMajor(),
                target.exportFormatMinor(), withReferences ? referencedPackages(view, target) : List.of()));
    }

    /**
     * Returns the version of the package that introduced a class or method.
     *
     * @param className  internal class name, e.g. {@code "org/globalplatform/GPSystem"}
     * @param memberName method name, or {@code null} to ask about the class itself
     * @param descriptor JVM descriptor of the method
     * @return the first version with that element, or empty if no version has it
     */
    public static Optional<String> introducedIn(String className, String memberName, String descriptor) {
        Optional<ApiClass> cls = DATA.classes().stream().filter(c -> c.name().equals(className)).findFirst();
        if (cls.isEmpty() || memberName == null) {
            return cls.map(c -> c.since().toString());
        }
        return cls.get().methods().stream()
                .filter(m -> m.name().equals(memberName) && m.descriptor().equals(descriptor))
                .map(m -> m.since().toString())
                .findFirst();
    }

    /**
     * Tells whether an imported package is this built-in data, not an export file that the user supplied: its
     * export file is one that {@link #export(String)} returned. A supplied export file is never built-in, even
     * with the same content, because its version is the one to record (JCVM 3.1 §4.5.2).
     *
     * @param imported a candidate or final import
     * @return whether the converter's own data describes the package
     */
    public static boolean isBuiltin(ImportedPackage imported) {
        ExportFile export = imported.exportFile();
        return EXPORTS.get(new Version(export.majorVersion(), export.minorVersion())) == export;
    }

    private static Version known(String version) {
        Version parsed;
        try {
            parsed = Version.parse(version);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(unknown(version), e);
        }
        if (!DATA.versions().contains(parsed)) {
            throw new IllegalArgumentException(unknown(version));
        }
        return parsed;
    }

    private static String unknown(String version) {
        return "'" + version + "' is not a version of the GlobalPlatform Card API package org.globalplatform that"
                + " the converter knows: " + String.join(", ", versions());
    }

    private static Map<Version, ExportFile> exports() {
        Map<Version, ExportFile> exports = new HashMap<>();
        for (Version v : DATA.versions()) {
            exports.put(v, new ExportFile(DATA.name(), HexFormat.of().parseHex(DATA.aidHex()), v.major(), v.minor(),
                    classesOf(v)));
        }
        return Map.copyOf(exports);
    }

    private static List<ExportFile.ClassExport> classesOf(Version version) {
        List<ExportFile.ClassExport> classes = new ArrayList<>();
        for (ApiClass c : DATA.classes()) {
            if (c.since().compareTo(version) > 0) continue;
            List<ExportFile.MethodExport> methods = new ArrayList<>();
            for (ApiMethod m : c.methods()) {
                if (m.since().compareTo(version) <= 0) {
                    methods.add(new ExportFile.MethodExport(m.name(), m.descriptor(), m.token(), m.flags()));
                }
            }
            classes.add(new ExportFile.ClassExport(c.name(), c.token(), c.flags(), methods, List.of(), c.supers(),
                    c.interfaces()));
        }
        return classes;
    }

    /**
     * JCVM 3.1 §5.5 referenced_packages (export file format 2.3): the other packages whose classes this export
     * file names as superclasses, interfaces or in descriptors, with AID and version of the target's API.
     */
    private static List<ExportFile.PackageReference> referencedPackages(ExportFile view, JavaCardVersion target) {
        Set<String> names = new LinkedHashSet<>();
        for (ExportFile.ClassExport c : view.classes()) {
            c.supers().forEach(s -> names.add(packageOf(s)));
            c.interfaces().forEach(i -> names.add(packageOf(i)));
            for (ExportFile.MethodExport m : c.methods()) {
                Matcher type = CLASS_IN_DESCRIPTOR.matcher(m.descriptor());
                while (type.find()) names.add(packageOf(type.group(1)));
            }
        }
        names.remove(view.packageName());
        List<ExportFile.PackageReference> referenced = new ArrayList<>();
        for (String name : names) {
            ExportFile api = BuiltinExports.getExport(name, target);
            if (api == null) {
                throw new IllegalStateException("org.globalplatform refers to package " + name.replace('/', '.')
                        + ", which Java Card " + target.specVersion() + " does not have");
            }
            referenced.add(new ExportFile.PackageReference(name, api.aid(), api.majorVersion(),
                    api.minorVersion()));
        }
        return referenced;
    }

    private static String packageOf(String className) {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash);
    }
}
