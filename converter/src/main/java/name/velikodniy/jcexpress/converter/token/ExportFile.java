package name.velikodniy.jcexpress.converter.token;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Parsed, in-memory representation of a Java Card export ({@code .exp}) file.
 *
 * <p>Export files are the Java Card platform's mechanism for cross-package linking
 * (JCVM 3.1, Section 4.3.3 and Chapter 5). They capture the public API of a package --
 * classes, methods, and fields -- together with the numeric tokens assigned to each element
 * during conversion. When a package imports another, the converter reads the
 * imported package's export file to resolve symbolic references to concrete tokens.
 *
 * <h2>Two different versions</h2>
 *
 * <p>An export file carries two unrelated version numbers (JCVM 3.1 §5.5, §5.6.1):
 * <ul>
 *   <li>{@link #majorVersion()}/{@link #minorVersion()} -- the version of the
 *       <em>package</em>, taken from the {@code CONSTANT_Package_info} entry referenced by
 *       {@code this_package}. This is the version an importing CAP file records in its
 *       Import component (JCVM 3.1 §4.5.2, §6.7).</li>
 *   <li>{@link #formatMajor()}/{@link #formatMinor()} -- the version of the export file
 *       <em>format</em> (the header {@code major_version}/{@code minor_version}, e.g. 2.1 or
 *       2.3). It only determines how the file is laid out.</li>
 * </ul>
 *
 * <h2>Role in the Converter Pipeline</h2>
 *
 * <ul>
 *   <li><b>Stage 1 (Load)</b> -- {@link ExportFileReader} deserializes {@code .exp} files
 *       for imported packages into {@code ExportFile} instances.</li>
 *   <li><b>Stage 3 (Token Assignment)</b> -- {@link TokenAssigner} queries {@code ExportFile}
 *       data to inherit virtual method tokens from external superclasses.</li>
 *   <li><b>Stage 7 (Export Generation)</b> --
 *       {@link name.velikodniy.jcexpress.converter.exp.ExportFileWriter ExportFileWriter}
 *       generates a new {@code .exp} file for the package being converted.</li>
 * </ul>
 *
 * @param packageName   package name in internal form (e.g. {@code "javacard/framework"})
 * @param aid           AID of the package (5-16 bytes, JCVM 3.1 §5.6.1)
 * @param majorVersion  major version of the <em>package</em> (CONSTANT_Package_info)
 * @param minorVersion  minor version of the <em>package</em> (CONSTANT_Package_info)
 * @param classes       exported classes with their assigned tokens, methods, and fields
 * @param packageFlags  CONSTANT_Package_info flags ({@link #ACC_LIBRARY})
 * @param formatMajor   export file format major version (file header)
 * @param formatMinor   export file format minor version (file header)
 * @param referencedPackages packages whose classes are subclassed, implemented or used in
 *                      descriptors by this export file ({@code referenced_packages[]}, export
 *                      file format 2.3, JCVM 3.1 §5.5); empty for older formats
 * @see ExportFileReader
 * @see TokenAssigner
 */
public record ExportFile(
        String packageName,
        byte[] aid,
        int majorVersion,
        int minorVersion,
        List<ClassExport> classes,
        int packageFlags,
        int formatMajor,
        int formatMinor,
        List<PackageReference> referencedPackages
) {
    /** CONSTANT_Package_info flag: the package declares no applets (JCVM 3.1 §5.6.1 Table 5-2). */
    public static final int ACC_LIBRARY = 0x01;

    /** Class flag ACC_PUBLIC (JCVM 3.1 §5.7 Table 5-3). */
    public static final int ACC_PUBLIC = 0x0001;
    /** Member flag ACC_PROTECTED (JCVM 3.1 §5.8 Table 5-4, §5.9 Table 5-5). */
    public static final int ACC_PROTECTED = 0x0004;
    /** Member flag ACC_STATIC (JCVM 3.1 §5.8 Table 5-4, §5.9 Table 5-5). */
    public static final int ACC_STATIC = 0x0008;
    /** Class/member flag ACC_FINAL (JCVM 3.1 §5.7 Table 5-3). */
    public static final int ACC_FINAL = 0x0010;
    /** Class flag ACC_INTERFACE (JCVM 3.1 §5.7 Table 5-3). */
    public static final int ACC_INTERFACE = 0x0200;
    /** Class/method flag ACC_ABSTRACT (JCVM 3.1 §5.7 Table 5-3). */
    public static final int ACC_ABSTRACT = 0x0400;
    /** Class flag ACC_SHAREABLE (JCVM 3.1 §5.7 Table 5-3). */
    public static final int ACC_SHAREABLE = 0x0800;
    /** Class flag ACC_REMOTE (JCVM 3.1 §5.7 Table 5-3). */
    public static final int ACC_REMOTE = 0x1000;

    /** Token value of compile-time constant fields (JCVM 3.1 §5.8). */
    public static final int CONSTANT_FIELD_TOKEN = 0xFF;

    /**
     * Canonical constructor; copies the lists.
     */
    public ExportFile {
        classes = List.copyOf(classes);
        referencedPackages = List.copyOf(referencedPackages);
    }

    /**
     * Creates an export file model without referenced packages (export file formats before 2.3).
     *
     * @param packageName  package name in internal form
     * @param aid          package AID
     * @param majorVersion package major version
     * @param minorVersion package minor version
     * @param classes      exported classes
     * @param packageFlags CONSTANT_Package_info flags
     * @param formatMajor  export file format major version
     * @param formatMinor  export file format minor version
     */
    public ExportFile(String packageName, byte[] aid, int majorVersion, int minorVersion,
                      List<ClassExport> classes, int packageFlags, int formatMajor, int formatMinor) {
        this(packageName, aid, majorVersion, minorVersion, classes, packageFlags, formatMajor, formatMinor,
                List.of());
    }

    /**
     * Creates an export file model for a library package in export file format 2.1.
     *
     * <p>Kept for source compatibility; the version arguments are the <em>package</em>
     * version (JCVM 3.1 §5.6.1).
     *
     * @param packageName  package name in internal form
     * @param aid          package AID
     * @param majorVersion package major version
     * @param minorVersion package minor version
     * @param classes      exported classes
     */
    public ExportFile(String packageName, byte[] aid, int majorVersion, int minorVersion,
                      List<ClassExport> classes) {
        this(packageName, aid, majorVersion, minorVersion, classes, ACC_LIBRARY, 2, 1);
    }

    /**
     * Returns {@code true} if the package is a library package (ACC_LIBRARY set,
     * JCVM 3.1 §5.6.1).
     *
     * @return whether the ACC_LIBRARY flag is set
     */
    public boolean isLibrary() {
        return (packageFlags & ACC_LIBRARY) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ExportFile(var pn, var a, var maj, var min, var cls, var fl, var fmj, var fmn,
                                    var refs)) {
            return majorVersion == maj
                    && minorVersion == min
                    && packageFlags == fl
                    && formatMajor == fmj
                    && formatMinor == fmn
                    && Objects.equals(packageName, pn)
                    && Arrays.equals(aid, a)
                    && Objects.equals(classes, cls)
                    && Objects.equals(referencedPackages, refs);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Objects.hash(packageName, majorVersion, minorVersion, classes,
                packageFlags, formatMajor, formatMinor, referencedPackages) + Arrays.hashCode(aid));
    }

    @Override
    public String toString() {
        return "ExportFile[packageName=" + packageName
                + ", aid=" + HexFormat.of().formatHex(aid)
                + ", majorVersion=" + majorVersion
                + ", minorVersion=" + minorVersion
                + ", packageFlags=" + packageFlags
                + ", format=" + formatMajor + "." + formatMinor
                + ", referencedPackages=" + referencedPackages
                + ", classes=" + classes + "]";
    }

    /**
     * A package referenced by an export file of format 2.3 (JCVM 3.1 §5.5): the
     * {@code CONSTANT_Package_info} of a package whose classes are subclassed, implemented or
     * used in descriptors.
     *
     * @param name         package name in internal form
     * @param aid          package AID
     * @param majorVersion package major version
     * @param minorVersion package minor version
     */
    public record PackageReference(String name, byte[] aid, int majorVersion, int minorVersion) {

        /**
         * Canonical constructor; copies the AID.
         */
        public PackageReference {
            Objects.requireNonNull(name, "name");
            aid = aid.clone();
        }

        @Override
        public byte[] aid() {
            return aid.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PackageReference(var n, var a, var maj, var min)
                    && name.equals(n) && Arrays.equals(aid, a) && majorVersion == maj && minorVersion == min;
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(name, majorVersion, minorVersion) + Arrays.hashCode(aid);
        }

        @Override
        public String toString() {
            return name + " " + majorVersion + "." + minorVersion + " " + HexFormat.of().withUpperCase().formatHex(aid);
        }
    }

    /**
     * Finds an exported class by its simple (unqualified) or fully qualified internal name.
     *
     * @param name the simple class name (e.g. {@code "Applet"}) or the internal name
     *             (e.g. {@code "javacard/framework/Applet"})
     * @return the matching class export
     * @throws NoSuchElementException if no class with the given name is exported
     */
    public ClassExport findClass(String name) {
        for (ClassExport ce : classes) {
            if (ce.name().equals(name) || ce.simpleName().equals(name)) return ce;
        }
        throw new NoSuchElementException("Export class not found: " + name
                + " (package " + packageName + ")");
    }

    /**
     * An exported class or interface (JCVM 3.1 §5.7).
     *
     * @param name        class name as stored in the export file: the fully qualified
     *                    internal name (e.g. {@code "javacard/framework/Applet"}); simple
     *                    names are accepted for hand-built models
     * @param token       class token (0-based, package-scoped, JCVM 3.1 §4.3.7.2)
     * @param accessFlags class flags of JCVM 3.1 §5.7 Table 5-3
     * @param methods     exported methods with their tokens
     * @param fields      exported fields with their tokens
     * @param supers      internal names of all public superclasses (JCVM 3.1 §5.7 supers[])
     * @param interfaces  internal names of all public superinterfaces (JCVM 3.1 §5.7
     *                    interfaces[])
     * @param cap22InheritableCount number of public and protected virtual method tokens a
     *                    subclass in a CAP file of format 2.2 or earlier inherits
     *                    ({@code CAP22_inheritable_public_method_token_count}, export file format
     *                    2.3, JCVM 3.1 §5.7); for older formats the value implied by §6.9.2.7
     */
    public record ClassExport(
            String name,
            int token,
            int accessFlags,
            List<MethodExport> methods,
            List<FieldExport> fields,
            List<String> supers,
            List<String> interfaces,
            int cap22InheritableCount
    ) {
        /**
         * Canonical constructor; copies the lists.
         */
        public ClassExport {
            methods = List.copyOf(methods);
            fields = List.copyOf(fields);
            supers = List.copyOf(supers);
            interfaces = List.copyOf(interfaces);
        }

        /**
         * Creates a class export whose CAP22 inheritable method count is implied by its methods:
         * every public virtual method token of a class, none for an interface (JCVM 3.1 §6.9.2.7:
         * in CAP format 2.2 and earlier all public virtual methods are inherited).
         *
         * @param name        class name
         * @param token       class token
         * @param accessFlags class flags
         * @param methods     exported methods
         * @param fields      exported fields
         * @param supers      public superclasses
         * @param interfaces  public superinterfaces
         */
        public ClassExport(String name, int token, int accessFlags, List<MethodExport> methods,
                           List<FieldExport> fields, List<String> supers, List<String> interfaces) {
            this(name, token, accessFlags, methods, fields, supers, interfaces,
                    impliedCap22Count(accessFlags, methods));
        }

        private static int impliedCap22Count(int accessFlags, List<MethodExport> methods) {
            if ((accessFlags & ACC_INTERFACE) != 0) return 0;
            return methods.stream().filter(m -> !m.isStaticOrConstructor())
                    .mapToInt(m -> m.token() + 1).max().orElse(0);
        }

        /**
         * Creates a class export without hierarchy information (source compatibility).
         *
         * @param name        class name
         * @param token       class token
         * @param accessFlags class flags
         * @param methods     exported methods
         * @param fields      exported fields
         */
        public ClassExport(String name, int token, int accessFlags,
                           List<MethodExport> methods, List<FieldExport> fields) {
            this(name, token, accessFlags, methods, fields, List.of(), List.of());
        }

        /**
         * Returns the simple class name (the part after the last {@code '/'}).
         *
         * @return the simple class name
         */
        public String simpleName() {
            int slash = name.lastIndexOf('/');
            return slash < 0 ? name : name.substring(slash + 1);
        }

        /**
         * Returns {@code true} if this entry describes an interface (ACC_INTERFACE).
         *
         * @return whether ACC_INTERFACE is set
         */
        public boolean isInterface() {
            return (accessFlags & ACC_INTERFACE) != 0;
        }

        /**
         * Returns {@code true} if this class or interface is shareable (ACC_SHAREABLE,
         * JCVM 3.1 §5.7).
         *
         * @return whether ACC_SHAREABLE is set
         */
        public boolean isShareable() {
            return (accessFlags & ACC_SHAREABLE) != 0;
        }
    }

    /**
     * An exported method with its token and access flags (JCVM 3.1 §5.9).
     *
     * <p>Both virtual and static methods appear in this list. Static methods can be
     * identified by the {@code ACC_STATIC} (0x0008) bit in {@link #accessFlags()};
     * constructors are named {@code <init>} and use static method tokens.
     *
     * @param name        method name (e.g. {@code "process"})
     * @param descriptor  method descriptor in JVM format (e.g. {@code "(Ljavacard/framework/APDU;)V"})
     * @param token       method token (static, virtual or interface method token)
     * @param accessFlags method flags of JCVM 3.1 §5.9 Table 5-5
     */
    public record MethodExport(
            String name,
            String descriptor,
            int token,
            int accessFlags
    ) {
        /**
         * Returns {@code true} for static methods and constructors, which use static method
         * tokens (JCVM 3.1 §4.3.7.4).
         *
         * @return whether the method is bound statically
         */
        public boolean isStaticOrConstructor() {
            return (accessFlags & ACC_STATIC) != 0 || "<init>".equals(name);
        }
    }

    /**
     * An exported field with its token and access flags (JCVM 3.1 §5.8).
     *
     * @param name          field name
     * @param descriptor    field descriptor in JVM format (e.g. {@code "S"} for short)
     * @param token         field token; {@link #CONSTANT_FIELD_TOKEN} for compile-time constants
     * @param accessFlags   field flags of JCVM 3.1 §5.8 Table 5-4
     * @param constantValue value of the ConstantValue attribute (JCVM 3.1 §5.10.1), or
     *                      {@code null} if the field is not a compile-time constant
     */
    public record FieldExport(
            String name,
            String descriptor,
            int token,
            int accessFlags,
            Integer constantValue
    ) {
        /**
         * Creates a field export without a constant value (source compatibility).
         *
         * @param name        field name
         * @param descriptor  field descriptor
         * @param token       field token
         * @param accessFlags field flags
         */
        public FieldExport(String name, String descriptor, int token, int accessFlags) {
            this(name, descriptor, token, accessFlags, null);
        }

        /**
         * Returns {@code true} if this is a static field.
         *
         * @return whether ACC_STATIC is set
         */
        public boolean isStatic() {
            return (accessFlags & ACC_STATIC) != 0;
        }
    }
}
