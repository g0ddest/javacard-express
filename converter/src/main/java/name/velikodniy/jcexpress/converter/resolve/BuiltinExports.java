package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.resolve.BuiltinApiData.ApiClass;
import name.velikodniy.jcexpress.converter.resolve.BuiltinApiData.ApiMember;
import name.velikodniy.jcexpress.converter.resolve.BuiltinApiData.ApiPackage;
import name.velikodniy.jcexpress.converter.resolve.BuiltinApiData.PackageVersion;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Built-in export data of the standard Java Card API packages, used during
 * <strong>Stage 4: Reference Resolution</strong> so that applets can be converted without the
 * Java Card Development Kit's export files.
 *
 * <p>Linking against an API package needs its export file (JCVM 3.1 §4.3.3, Chapter 5): the
 * package AID and version recorded in the Import component (§4.5.2, §6.7) and the tokens of its
 * classes, methods and fields (§4.3.7). These values are interoperability facts fixed by the
 * owner of the API (§4.3.6, §5.3); a CAP file only links on a card if they match exactly.
 *
 * <h2>Provenance</h2>
 * <p>The data lives in the resource {@code javacard-api-exports.txt} (parsed by
 * {@link BuiltinApiData}). It was extracted from the API export files that ship with the Java Card
 * Development Kits 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5u3, 3.1.0 and 3.2.0 with this
 * project's own clean-room {@link name.velikodniy.jcexpress.converter.token.ExportFileReader
 * ExportFileReader} (black-box data extraction, no Oracle code examined) by the test tool
 * {@code BuiltinApiDataGenerator}. {@code BuiltinExportsSdkComparisonTest} re-reads those export
 * files, when the kits are installed locally, and requires the reconstructed export of every
 * package of every version to match them exactly (package version and AID, class tokens, flags,
 * superclasses and interfaces, method and field tokens and flags). Compile-time constant values
 * are not included: javac inlines them, so they are not needed for linking.
 *
 * <h2>Versions</h2>
 * <p>Each package, class and member is offered only from the Java Card version that introduced
 * it, and with the package version of the selected target (JCVM 3.1 §4.5.2). For example
 * {@code Util.arrayFill} is absent for a JC 2.2.2 target and {@code javacard.security} is
 * imported as 1.7 for JC 3.1.0 and 1.8 for JC 3.2.0. {@link #introducedIn} reports the version
 * that introduced an API element, for diagnostics.
 *
 * @see ImportedPackage
 * @see ReferenceResolver
 * @see ExportFile
 */
public final class BuiltinExports {

    /** Core packages, in the conventional import order (framework, lang, security, crypto). */
    private static final List<String> CORE_PACKAGES = List.of(
            "javacard/framework", "java/lang", "javacard/security", "javacardx/crypto");

    private static final List<ApiPackage> PACKAGES = sortCoreFirst(BuiltinApiData.load());

    private BuiltinExports() {}

    /**
     * Returns the built-in export file of an API package as shipped with Java Card 3.0.5.
     *
     * @param packageName internal name (slash notation, e.g. {@code "javacard/framework"})
     * @return the export file, or {@code null} if the package is not a built-in API package
     */
    public static ExportFile getExport(String packageName) {
        return getExport(packageName, JavaCardVersion.V3_0_5);
    }

    /**
     * Returns the built-in export file of an API package as shipped with the given version.
     *
     * @param packageName internal name (slash notation, e.g. {@code "javacardx/apdu"})
     * @param version     the target Java Card version
     * @return the export file, or {@code null} if the package does not exist in that version
     */
    public static ExportFile getExport(String packageName, JavaCardVersion version) {
        for (ApiPackage p : PACKAGES) {
            if (p.name().equals(packageName) && p.versions().containsKey(version)) {
                return view(p, version);
            }
        }
        return null;
    }

    /**
     * Returns the built-in API packages of Java Card 3.0.5 as imported packages.
     *
     * @param baseToken starting token for package numbering
     * @return imported packages for all built-in APIs
     */
    public static List<ImportedPackage> allBuiltinImports(int baseToken) {
        return allBuiltinImports(baseToken, JavaCardVersion.V3_0_5);
    }

    /**
     * Returns every API package available in the given Java Card version as an imported
     * package, with consecutive tokens starting at {@code baseToken}.
     *
     * <p>The core packages come first in the conventional order javacard.framework, java.lang,
     * javacard.security, javacardx.crypto; the optional packages follow in name order. Unused
     * packages are removed again when the imports are finalized (JCVM 3.1 §6.7). The package
     * versions are those of the target's API (JCVM 3.1 §4.5.2), for example framework 1.6,
     * security 1.6, crypto 1.6 for 3.0.5; framework 1.8, security 1.7, crypto 1.7 for 3.1.0;
     * framework 1.9, security 1.8, crypto 1.8 for 3.2.0; java.lang is always 1.0.
     *
     * @param baseToken starting token for package numbering
     * @param version   target Java Card version
     * @return imported packages for all API packages of the target version
     */
    public static List<ImportedPackage> allBuiltinImports(int baseToken, JavaCardVersion version) {
        List<ImportedPackage> result = new ArrayList<>();
        int token = baseToken;
        for (ApiPackage p : PACKAGES) {
            PackageVersion v = p.versions().get(version);
            if (v == null) continue;
            ExportFile ef = view(p, version);
            result.add(new ImportedPackage(token++, ef.aid(), v.major(), v.minor(), ef));
        }
        return result;
    }

    /**
     * Returns {@code true} if the package is a built-in API package of any supported version.
     *
     * @param packageName internal package name (slash notation)
     * @return whether built-in export data exists for the package
     */
    public static boolean isBuiltinPackage(String packageName) {
        return PACKAGES.stream().anyMatch(p -> p.name().equals(packageName));
    }

    /**
     * Returns the Java Card version that introduced an API class or member.
     *
     * @param className  internal class name (e.g. {@code "javacard/framework/Util"})
     * @param memberName method or field name, or {@code null} to ask about the class itself
     * @param descriptor JVM descriptor of the member ({@code '('} for methods), ignored for classes
     * @return the first version with that element, or empty if no supported version has it
     */
    public static Optional<JavaCardVersion> introducedIn(String className, String memberName,
                                                         String descriptor) {
        Optional<ApiClass> cls = findClass(className);
        if (cls.isEmpty() || memberName == null) return cls.map(ApiClass::since);
        boolean method = descriptor != null && descriptor.startsWith("(");
        List<ApiMember> members = method ? cls.get().methods() : cls.get().fields();
        return members.stream()
                .filter(m -> m.name().equals(memberName)
                        && (descriptor == null || m.descriptor().equals(descriptor)))
                .map(ApiMember::since)
                .findFirst();
    }

    /**
     * Returns the Java Card version that introduced an API package.
     *
     * @param packageName internal package name (slash notation)
     * @return the first version with that package, or empty if it is not an API package
     */
    public static Optional<JavaCardVersion> packageIntroducedIn(String packageName) {
        return PACKAGES.stream().filter(p -> p.name().equals(packageName))
                .map(ApiPackage::since).findFirst();
    }

    // ── export reconstruction ──

    private static Optional<ApiClass> findClass(String className) {
        int slash = className.lastIndexOf('/');
        String pkg = slash < 0 ? "" : className.substring(0, slash);
        return PACKAGES.stream().filter(p -> p.name().equals(pkg))
                .flatMap(p -> p.classes().stream())
                .filter(c -> c.name().equals(className))
                .findFirst();
    }

    /** Reconstructs the export file of {@code p} as shipped with {@code version} (JCVM 3.1 §5.5). */
    private static ExportFile view(ApiPackage p, JavaCardVersion version) {
        List<ExportFile.ClassExport> classes = new ArrayList<>();
        for (ApiClass c : p.classes()) {
            if (isAvailable(c.since(), version)) classes.add(view(c, version));
        }
        PackageVersion v = p.versions().get(version);
        return new ExportFile(p.name(), HexFormat.of().parseHex(p.aidHex()), v.major(), v.minor(),
                List.copyOf(classes));
    }

    private static ExportFile.ClassExport view(ApiClass c, JavaCardVersion version) {
        List<ExportFile.MethodExport> methods = new ArrayList<>();
        for (ApiMember m : c.methods()) {
            if (isAvailable(m.since(), version)) {
                methods.add(new ExportFile.MethodExport(m.name(), m.descriptor(), m.token(),
                        m.flags().at(version)));
            }
        }
        List<ExportFile.FieldExport> fields = new ArrayList<>();
        for (ApiMember f : c.fields()) {
            if (isAvailable(f.since(), version)) {
                fields.add(new ExportFile.FieldExport(f.name(), f.descriptor(), f.token(),
                        f.flags().at(version)));
            }
        }
        return new ExportFile.ClassExport(c.name(), c.token(), c.flags().at(version),
                methods, fields, c.supers(), c.interfaces());
    }

    private static boolean isAvailable(JavaCardVersion since, JavaCardVersion target) {
        return since.ordinal() <= target.ordinal();
    }

    private static List<ApiPackage> sortCoreFirst(List<ApiPackage> packages) {
        Comparator<ApiPackage> byCoreIndex = Comparator.comparingInt(p -> {
            int i = CORE_PACKAGES.indexOf(p.name());
            return i < 0 ? CORE_PACKAGES.size() : i;
        });
        return packages.stream()
                .sorted(byCoreIndex.thenComparing(ApiPackage::name))
                .toList();
    }
}
