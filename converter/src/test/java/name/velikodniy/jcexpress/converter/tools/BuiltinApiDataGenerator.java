package name.velikodniy.jcexpress.converter.tools;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.testutil.OracleSdkExports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer tool that regenerates the built-in API linking table
 * ({@code javacard-api-exports.txt}, see
 * {@link name.velikodniy.jcexpress.converter.resolve.BuiltinExports BuiltinExports}).
 *
 * <p>It reads the public API export files of the locally installed Java Card Development Kits
 * 2.1.2-3.2.0 with this project's clean-room {@link ExportFileReader} (black-box data
 * extraction; no Oracle code is examined) and records, for every package, class, method and
 * non-constant field, its token, flags and the first Java Card version that has it. Constant
 * values are not recorded: javac inlines them and they are not needed for linking.
 *
 * <p>Usage: {@code java -cp target/classes:target/test-classes
 * name.velikodniy.jcexpress.converter.tools.BuiltinApiDataGenerator <output-file>}
 */
public final class BuiltinApiDataGenerator {

    private BuiltinApiDataGenerator() {}

    /**
     * Entry point.
     *
     * @param args output file path
     * @throws IOException if the kits cannot be read or the file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Map<JavaCardVersion, Map<String, ExportFile>> byVersion = new EnumMap<>(JavaCardVersion.class);
        for (JavaCardVersion v : JavaCardVersion.values()) {
            Map<String, ExportFile> pkgs = new TreeMap<>();
            for (byte[] data : OracleSdkExports.read(v).values()) {
                ExportFile ef = ExportFileReader.read(data);
                pkgs.put(ef.packageName(), ef);
            }
            byVersion.put(v, pkgs);
        }
        StringBuilder out = new StringBuilder(HEADER);
        for (String pkg : allPackages(byVersion)) {
            writePackage(out, pkg, byVersion);
        }
        Files.writeString(Path.of(args[0]), out, StandardCharsets.UTF_8);
    }

    private static final String HEADER = """
            # Java Card API linking data for the converter's built-in imports (BuiltinExports).
            # Tokens, flags and versions are interoperability facts of the Java Card API
            # (JCVM 3.1 §4.3.6, §5.3). They were read from the API export files of the Java Card
            # Development Kits 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5u3, 3.1.0 and 3.2.0 with the
            # project's clean-room ExportFileReader (black-box data extraction) by
            # BuiltinApiDataGenerator, and are verified against those files by
            # BuiltinExportsSdkComparisonTest. Constant values are not included.
            #
            # package <name> <AID> <version>=<major.minor> ...     (first version = introduced in)
            # class <token> <simple-name> <flags> <since> [supers=a,b] [interfaces=a,b] [flags@<version>=<flags>]
            # method <token> <flags> <since> <name> <descriptor> [flags@<version>=<flags>]
            # field <token> <flags> <since> <name> <descriptor>
            """;

    private static List<String> allPackages(Map<JavaCardVersion, Map<String, ExportFile>> byVersion) {
        List<String> names = new ArrayList<>(byVersion.get(JavaCardVersion.V3_2_0).keySet());
        for (var pkgs : byVersion.values()) {
            for (String n : pkgs.keySet()) if (!names.contains(n)) names.add(n);
        }
        names.sort(String::compareTo);
        return names;
    }

    private static void writePackage(StringBuilder out, String pkg,
                                     Map<JavaCardVersion, Map<String, ExportFile>> byVersion) {
        Map<JavaCardVersion, ExportFile> present = new EnumMap<>(JavaCardVersion.class);
        byVersion.forEach((v, pkgs) -> {
            if (pkgs.containsKey(pkg)) present.put(v, pkgs.get(pkg));
        });
        ExportFile latest = present.values().stream().reduce((a, b) -> b).orElseThrow();
        out.append("\npackage ").append(pkg).append(' ')
                .append(HexFormat.of().withUpperCase().formatHex(latest.aid()));
        present.forEach((v, ef) -> out.append(' ').append(v.name()).append('=')
                .append(ef.majorVersion()).append('.').append(ef.minorVersion()));
        out.append('\n');
        for (ExportFile.ClassExport cls : latest.classes()) {
            writeClass(out, cls, present);
        }
    }

    private static void writeClass(StringBuilder out, ExportFile.ClassExport cls,
                                   Map<JavaCardVersion, ExportFile> present) {
        Map<JavaCardVersion, ExportFile.ClassExport> versions = new LinkedHashMap<>();
        present.forEach((v, ef) -> ef.classes().stream()
                .filter(c -> c.name().equals(cls.name())).findFirst()
                .ifPresent(c -> versions.put(v, c)));
        JavaCardVersion since = versions.keySet().iterator().next();
        out.append("class ").append(cls.token()).append(' ').append(cls.simpleName()).append(' ')
                .append(hex(cls.accessFlags())).append(' ').append(since.name());
        if (!cls.supers().isEmpty()) out.append(" supers=").append(String.join(",", cls.supers()));
        if (!cls.interfaces().isEmpty()) {
            out.append(" interfaces=").append(String.join(",", cls.interfaces()));
        }
        versions.forEach((v, c) -> {
            if (c.accessFlags() != cls.accessFlags()) {
                out.append(" flags@").append(v.name()).append('=').append(hex(c.accessFlags()));
            }
        });
        out.append('\n');
        for (ExportFile.MethodExport m : cls.methods()) {
            writeMethod(out, m, versions);
        }
        for (ExportFile.FieldExport f : cls.fields()) {
            if (f.token() == ExportFile.CONSTANT_FIELD_TOKEN) continue;
            JavaCardVersion fieldSince = versions.entrySet().stream()
                    .filter(e -> e.getValue().fields().stream().anyMatch(x -> x.name().equals(f.name())))
                    .findFirst().orElseThrow().getKey();
            out.append("field ").append(f.token()).append(' ').append(hex(f.accessFlags())).append(' ')
                    .append(fieldSince.name()).append(' ').append(f.name()).append(' ')
                    .append(f.descriptor()).append('\n');
        }
    }

    private static void writeMethod(StringBuilder out, ExportFile.MethodExport m,
                                    Map<JavaCardVersion, ExportFile.ClassExport> versions) {
        JavaCardVersion since = null;
        StringBuilder overrides = new StringBuilder();
        for (var e : versions.entrySet()) {
            for (ExportFile.MethodExport x : e.getValue().methods()) {
                if (x.name().equals(m.name()) && x.descriptor().equals(m.descriptor())) {
                    if (since == null) since = e.getKey();
                    if (x.accessFlags() != m.accessFlags()) {
                        overrides.append(" flags@").append(e.getKey().name()).append('=')
                                .append(hex(x.accessFlags()));
                    }
                }
            }
        }
        out.append("method ").append(m.token()).append(' ').append(hex(m.accessFlags())).append(' ')
                .append(since == null ? "?" : since.name()).append(' ').append(m.name()).append(' ')
                .append(m.descriptor()).append(overrides).append('\n');
    }

    private static String hex(int flags) {
        return String.format("0x%04X", flags);
    }
}
