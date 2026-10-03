package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed form of the built-in Java Card API linking table {@code javacard-api-exports.txt}
 * (see {@link BuiltinExports} for its provenance).
 *
 * <p>The table describes every public API package of Java Card 2.1.2 to 3.2.0 once, with the
 * first version that has each package, class and member, so that the export file of any
 * supported version can be reconstructed (JCVM 3.1 §5.5-§5.9). The line format is:
 * <pre>
 * package &lt;internal-name&gt; &lt;AID hex&gt; &lt;version&gt;=&lt;major.minor&gt; ...
 * class &lt;token&gt; &lt;simple-name&gt; &lt;flags&gt; &lt;since&gt; [supers=a,b] [interfaces=a,b] [flags@&lt;version&gt;=&lt;flags&gt;]
 * method &lt;token&gt; &lt;flags&gt; &lt;since&gt; &lt;name&gt; &lt;descriptor&gt; [flags@&lt;version&gt;=&lt;flags&gt;]
 * field &lt;token&gt; &lt;flags&gt; &lt;since&gt; &lt;name&gt; &lt;descriptor&gt; [flags@&lt;version&gt;=&lt;flags&gt;]
 * </pre>
 * A {@code flags@} entry gives the flags of one version where they differ from the default.
 */
final class BuiltinApiData {

    /** Package major/minor version (CONSTANT_Package_info, JCVM 3.1 §5.6.1). */
    record PackageVersion(int major, int minor) {}

    /** Access flags that may differ per Java Card version. */
    record VersionedFlags(int defaultFlags, Map<JavaCardVersion, Integer> overrides) {
        int at(JavaCardVersion version) {
            return overrides.getOrDefault(version, defaultFlags);
        }
    }

    /** A method or field entry (JCVM 3.1 §5.8, §5.9). */
    record ApiMember(String name, String descriptor, int token, VersionedFlags flags,
                     JavaCardVersion since) {}

    /** A class or interface entry (JCVM 3.1 §5.7); {@code name} is the internal name. */
    record ApiClass(String name, int token, VersionedFlags flags, JavaCardVersion since,
                    List<String> supers, List<String> interfaces,
                    List<ApiMember> methods, List<ApiMember> fields) {}

    /** A package and the versions of it shipped with each Java Card release. */
    record ApiPackage(String name, String aidHex, Map<JavaCardVersion, PackageVersion> versions,
                      List<ApiClass> classes) {
        JavaCardVersion since() {
            return versions.keySet().iterator().next();
        }
    }

    private static final String RESOURCE = "javacard-api-exports.txt";

    private BuiltinApiData() {}

    /**
     * Loads and parses the table shipped with the converter.
     *
     * @return the packages in file order
     * @throws IllegalStateException if the resource is missing or malformed
     */
    static List<ApiPackage> load() {
        try (InputStream in = BuiltinApiData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing converter resource " + RESOURCE);
            }
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new Parser().parse(reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Line parser; collects packages, classes and members as it goes. */
    private static final class Parser {
        private final List<ApiPackage> packages = new ArrayList<>();
        private PackageBuilder pkg;
        private ClassBuilder cls;
        private int lineNo;

        List<ApiPackage> parse(List<String> lines) {
            for (String line : lines) {
                lineNo++;
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                try {
                    parseLine(trimmed.split("\\s+"));
                } catch (RuntimeException e) {
                    throw new IllegalStateException(RESOURCE + " line " + lineNo + ": " + e.getMessage(), e);
                }
            }
            finishPackage();
            return List.copyOf(packages);
        }

        private void parseLine(String[] t) {
            switch (t[0]) {
                case "package" -> startPackage(t);
                case "class" -> startClass(t);
                case "method" -> requireClass().methods.add(member(t));
                case "field" -> requireClass().fields.add(member(t));
                default -> throw new IllegalArgumentException("unknown record '" + t[0] + "'");
            }
        }

        private void startPackage(String[] t) {
            finishPackage();
            Map<JavaCardVersion, PackageVersion> versions = new EnumMap<>(JavaCardVersion.class);
            for (int i = 3; i < t.length; i++) {
                String[] kv = t[i].split("=");
                String[] mm = kv[1].split("\\.");
                versions.put(JavaCardVersion.valueOf(kv[0]),
                        new PackageVersion(Integer.parseInt(mm[0]), Integer.parseInt(mm[1])));
            }
            if (versions.isEmpty()) throw new IllegalArgumentException("package without versions");
            pkg = new PackageBuilder(t[1], t[2], Collections.unmodifiableMap(versions));
        }

        private void startClass(String[] t) {
            finishClass();
            if (pkg == null) throw new IllegalArgumentException("class outside a package");
            cls = new ClassBuilder(pkg.name + "/" + t[2], Integer.parseInt(t[1]));
            cls.since = JavaCardVersion.valueOf(t[4]);
            List<String> extra = List.of(t).subList(5, t.length);
            cls.flags = flags(t[3], extra);
            for (String option : extra) {
                if (option.startsWith("supers=")) cls.supers = names(option);
                if (option.startsWith("interfaces=")) cls.interfaces = names(option);
            }
        }

        private ApiMember member(String[] t) {
            List<String> extra = List.of(t).subList(6, t.length);
            return new ApiMember(t[4], t[5], Integer.parseInt(t[1]), flags(t[2], extra),
                    JavaCardVersion.valueOf(t[3]));
        }

        private static VersionedFlags flags(String defaultFlags, List<String> options) {
            Map<JavaCardVersion, Integer> overrides = new EnumMap<>(JavaCardVersion.class);
            for (String option : options) {
                if (!option.startsWith("flags@")) continue;
                String[] kv = option.substring("flags@".length()).split("=");
                overrides.put(JavaCardVersion.valueOf(kv[0]), Integer.decode(kv[1]));
            }
            return new VersionedFlags(Integer.decode(defaultFlags), Map.copyOf(overrides));
        }

        private static List<String> names(String option) {
            return List.of(option.substring(option.indexOf('=') + 1).split(","));
        }

        private ClassBuilder requireClass() {
            if (cls == null) throw new IllegalArgumentException("member outside a class");
            return cls;
        }

        private void finishClass() {
            if (cls != null) pkg.classes.add(cls.build());
            cls = null;
        }

        private void finishPackage() {
            finishClass();
            if (pkg != null) {
                packages.add(new ApiPackage(pkg.name, pkg.aidHex, pkg.versions, List.copyOf(pkg.classes)));
            }
            pkg = null;
        }
    }

    private static final class PackageBuilder {
        final String name;
        final String aidHex;
        final Map<JavaCardVersion, PackageVersion> versions;
        final List<ApiClass> classes = new ArrayList<>();

        PackageBuilder(String name, String aidHex, Map<JavaCardVersion, PackageVersion> versions) {
            this.name = name;
            this.aidHex = aidHex;
            this.versions = versions;
        }
    }

    private static final class ClassBuilder {
        final String name;
        final int token;
        VersionedFlags flags;
        JavaCardVersion since;
        List<String> supers = List.of();
        List<String> interfaces = List.of();
        final List<ApiMember> methods = new ArrayList<>();
        final List<ApiMember> fields = new ArrayList<>();

        ClassBuilder(String name, int token) {
            this.name = name;
            this.token = token;
        }

        ApiClass build() {
            return new ApiClass(name, token, flags, since, supers, interfaces,
                    List.copyOf(methods), List.copyOf(fields));
        }
    }
}
