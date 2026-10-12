package name.velikodniy.jcexpress.converter.resolve;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed form of the built-in linking table of the GlobalPlatform Card API package
 * {@code org.globalplatform}, {@code globalplatform-api-exports.txt} (see {@link GlobalPlatformApi} for its
 * provenance).
 *
 * <p>The table describes the package once, for all its versions: every class and method with its token, its flags
 * and the first package version that has it, so that the export file of any version can be reconstructed
 * (JCVM 3.1 §5.5-§5.9). This works because every version of the package is binary compatible with the earlier ones
 * (§4.4): nothing was removed and no token changed. The versions of this package are its own; they do not follow
 * the Java Card version, which is why the data is not part of {@code javacard-api-exports.txt}. The line format is:
 * <pre>
 * package &lt;internal-name&gt; &lt;AID hex&gt; &lt;major.minor&gt; ...
 * class &lt;token&gt; &lt;simple-name&gt; &lt;flags&gt; &lt;since&gt; [supers=a,b] [interfaces=a,b]
 * method &lt;token&gt; &lt;flags&gt; &lt;since&gt; &lt;name&gt; &lt;descriptor&gt;
 * </pre>
 */
final class GlobalPlatformApiData {

    /**
     * A version of the package (CONSTANT_Package_info, JCVM 3.1 §5.6.1).
     *
     * @param major the major version
     * @param minor the minor version
     */
    record Version(int major, int minor) implements Comparable<Version> {

        /**
         * Parses {@code <major>.<minor>}.
         *
         * @param text the version text
         * @return the version
         * @throws IllegalArgumentException if the text is not two numbers of 0 to 255 separated by a dot
         */
        static Version parse(String text) {
            if (text == null || !text.matches("\\d{1,3}\\.\\d{1,3}")) {
                throw new IllegalArgumentException("'" + text + "' is not a package version <major>.<minor>");
            }
            int dot = text.indexOf('.');
            Version version = new Version(Integer.parseInt(text.substring(0, dot)),
                    Integer.parseInt(text.substring(dot + 1)));
            if (version.major > 0xFF || version.minor > 0xFF) {
                throw new IllegalArgumentException("'" + text + "' is not a package version: each number is at"
                        + " most 255 (JCVM 3.1 §4.5)");
            }
            return version;
        }

        @Override
        public int compareTo(Version other) {
            return major != other.major ? Integer.compare(major, other.major) : Integer.compare(minor, other.minor);
        }

        @Override
        public String toString() {
            return major + "." + minor;
        }
    }

    /** A method entry (JCVM 3.1 §5.9). */
    record ApiMethod(String name, String descriptor, int token, int flags, Version since) {}

    /** A class or interface entry (JCVM 3.1 §5.7); {@code name} is the internal name. */
    record ApiClass(String name, int token, int flags, Version since, List<String> supers, List<String> interfaces,
                    List<ApiMethod> methods) {}

    /** The package: its name, AID, versions in ascending order, and classes in table order. */
    record ApiPackage(String name, String aidHex, List<Version> versions, List<ApiClass> classes) {}

    private static final String RESOURCE = "globalplatform-api-exports.txt";

    private GlobalPlatformApiData() {}

    /**
     * Loads and parses the table shipped with the converter.
     *
     * @return the package
     * @throws IllegalStateException if the resource is missing or malformed
     */
    static ApiPackage load() {
        return new Parser().parse(ResourceLines.read(GlobalPlatformApiData.class, RESOURCE));
    }

    /** Line parser; the table holds exactly one package. */
    private static final class Parser {
        private String name;
        private String aidHex;
        private final List<Version> versions = new ArrayList<>();
        private final List<ApiClass> classes = new ArrayList<>();
        private ClassBuilder cls;
        private int lineNo;

        ApiPackage parse(List<String> lines) {
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
            finishClass();
            if (name == null) throw new IllegalStateException(RESOURCE + " has no package record");
            return new ApiPackage(name, aidHex, List.copyOf(versions), List.copyOf(classes));
        }

        private void parseLine(String[] t) {
            switch (t[0]) {
                case "package" -> startPackage(t);
                case "class" -> startClass(t);
                case "method" -> requireClass().methods.add(new ApiMethod(t[4], t[5], Integer.parseInt(t[1]),
                        Integer.decode(t[2]), known(t[3])));
                default -> throw new IllegalArgumentException("unknown record '" + t[0] + "'");
            }
        }

        private void startPackage(String[] t) {
            if (name != null) throw new IllegalArgumentException("a second package record");
            name = t[1];
            aidHex = t[2];
            for (int i = 3; i < t.length; i++) {
                Version version = Version.parse(t[i]);
                if (!versions.isEmpty() && version.compareTo(versions.getLast()) <= 0) {
                    throw new IllegalArgumentException("versions are not ascending at " + t[i]);
                }
                versions.add(version);
            }
            if (versions.isEmpty()) throw new IllegalArgumentException("package without versions");
        }

        private void startClass(String[] t) {
            finishClass();
            if (name == null) throw new IllegalArgumentException("class outside a package");
            cls = new ClassBuilder(name + "/" + t[2], Integer.parseInt(t[1]), Integer.decode(t[3]), known(t[4]));
            for (String option : List.of(t).subList(5, t.length)) {
                if (option.startsWith("supers=")) cls.supers = names(option);
                if (option.startsWith("interfaces=")) cls.interfaces = names(option);
            }
        }

        /** A since value must be one of the versions of the package record. */
        private Version known(String text) {
            Version version = Version.parse(text);
            if (!versions.contains(version)) throw new IllegalArgumentException("unknown version " + text);
            return version;
        }

        private static List<String> names(String option) {
            return List.of(option.substring(option.indexOf('=') + 1).split(","));
        }

        private ClassBuilder requireClass() {
            if (cls == null) throw new IllegalArgumentException("method outside a class");
            return cls;
        }

        private void finishClass() {
            if (cls != null) {
                classes.add(new ApiClass(cls.name, cls.token, cls.flags, cls.since, cls.supers, cls.interfaces,
                        List.copyOf(cls.methods)));
            }
            cls = null;
        }
    }

    private static final class ClassBuilder {
        final String name;
        final int token;
        final int flags;
        final Version since;
        List<String> supers = List.of();
        List<String> interfaces = List.of();
        final List<ApiMethod> methods = new ArrayList<>();

        ClassBuilder(String name, int token, int flags, Version since) {
            this.name = name;
            this.token = token;
            this.flags = flags;
            this.since = since;
        }
    }
}
