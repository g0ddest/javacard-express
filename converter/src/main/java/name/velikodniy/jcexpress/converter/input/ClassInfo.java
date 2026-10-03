package name.velikodniy.jcexpress.converter.input;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed representation of a single JVM {@code .class} file, capturing the
 * structural information needed by the converter pipeline.
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>Each {@code ClassInfo} record is produced by {@link ClassFileReader} during
 * the Load stage and represents one class or interface within the package being
 * converted. It stores the class hierarchy (superclass and interfaces), access
 * modifiers, and the complete lists of declared methods and fields.
 *
 * <p>All class and type names use JVM internal name format with {@code '/'} as
 * the separator (e.g. {@code "javacard/framework/Applet"}), matching the format
 * found in {@code .class} files.
 *
 * <h2>Usage in Later Stages</h2>
 * <ul>
 *   <li><strong>Stage 2 (Subset Check):</strong> The {@code accessFlags}, superclass,
 *       interfaces, methods, and fields are inspected for forbidden JVM constructs
 *       (e.g. {@code long}, {@code float}, {@code double} types).</li>
 *   <li><strong>Stage 3 (Token Assignment):</strong> The {@link #isInterface()} flag
 *       determines class ordering (interfaces first, per JCVM 3.0.5 spec Section 6.9).
 *       Methods and fields are enumerated for token allocation.</li>
 *   <li><strong>Stage 6 (CAP Generation):</strong> The Class Component
 *       (JCVM 3.0.5 spec Section 6.9) is built from the class hierarchy and
 *       access flags stored here.</li>
 * </ul>
 *
 * @param thisClass   internal name of this class (e.g. {@code "com/example/WalletApplet"})
 * @param superClass  internal name of the superclass, or {@code null} for
 *                    {@code java/lang/Object}
 * @param interfaces  internal names of directly implemented interfaces
 * @param accessFlags JVM access flags bitmask ({@code ACC_PUBLIC}, {@code ACC_INTERFACE},
 *                    {@code ACC_ABSTRACT}, etc. as defined in JVM Specification Section 4.1)
 * @param methods     all declared methods, including constructors and static initializers
 * @param fields      all declared fields, both instance and static
 * @param model       the parsed class file this record was read from, or {@code null} for
 *                    records built in code; gives the subset check access to method code,
 *                    line numbers and attributes. It is not part of equality.
 * @see ClassFileReader
 * @see MethodInfo
 * @see FieldInfo
 */
public record ClassInfo(
        String thisClass,
        String superClass,
        List<String> interfaces,
        int accessFlags,
        List<MethodInfo> methods,
        List<FieldInfo> fields,
        ClassModel model
) {
    /**
     * Creates a class record without a parsed class file.
     *
     * @param thisClass   internal name of this class
     * @param superClass  internal name of the superclass, or {@code null}
     * @param interfaces  internal names of directly implemented interfaces
     * @param accessFlags JVM access flags
     * @param methods     declared methods
     * @param fields      declared fields
     */
    public ClassInfo(String thisClass, String superClass, List<String> interfaces, int accessFlags,
                     List<MethodInfo> methods, List<FieldInfo> fields) {
        this(thisClass, superClass, interfaces, accessFlags, methods, fields, null);
    }

    /**
     * Returns the source file name of the class (SourceFile attribute), if known.
     *
     * @return the source file name
     */
    public Optional<String> sourceFile() {
        return model == null ? Optional.empty() : model.findAttribute(Attributes.sourceFile())
                .map(sf -> sf.sourceFile().stringValue());
    }

    /**
     * Returns the parsed method with the given name and descriptor, if the class file is known.
     *
     * @param name       method name
     * @param descriptor method descriptor
     * @return the method model
     */
    public Optional<MethodModel> methodModel(String name, String descriptor) {
        if (model == null) {
            return Optional.empty();
        }
        return model.methods().stream()
                .filter(m -> m.methodName().equalsString(name) && m.methodType().equalsString(descriptor))
                .findFirst();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClassInfo(var tc, var sc, var itf, var af, var ms, var fs, var ignored)
                && accessFlags == af && Objects.equals(thisClass, tc) && Objects.equals(superClass, sc)
                && Objects.equals(interfaces, itf) && Objects.equals(methods, ms)
                && Objects.equals(fields, fs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thisClass, superClass, interfaces, accessFlags, methods, fields);
    }

    @Override
    public String toString() {
        return "ClassInfo[thisClass=" + thisClass + ", superClass=" + superClass
                + ", interfaces=" + interfaces + ", accessFlags=" + accessFlags
                + ", methods=" + methods + ", fields=" + fields + "]";
    }

    /**
     * Returns {@code true} if this class is an interface ({@code ACC_INTERFACE} flag is set).
     *
     * <p>Interfaces are assigned class tokens before concrete classes during
     * Stage 3 (Token Assignment), per JCVM 3.0.5 spec Section 6.9.
     *
     * @return {@code true} if the {@code ACC_INTERFACE} flag (0x0200) is set
     */
    public boolean isInterface() {
        return (accessFlags & 0x0200) != 0;
    }

    /**
     * Returns {@code true} if this class is abstract ({@code ACC_ABSTRACT} flag is set).
     *
     * <p>Abstract classes cannot be instantiated on the card but may still define
     * virtual method slots that subclasses must implement.
     *
     * @return {@code true} if the {@code ACC_ABSTRACT} flag (0x0400) is set
     */
    public boolean isAbstract() {
        return (accessFlags & 0x0400) != 0;
    }

    /**
     * Returns the simple (unqualified) class name, i.e. the portion of the
     * internal name after the last {@code '/'} separator.
     *
     * <p>For example, {@code "javacard/framework/Applet"} yields {@code "Applet"}.
     *
     * @return the simple class name
     */
    public String simpleName() {
        int idx = thisClass.lastIndexOf('/');
        return idx < 0 ? thisClass : thisClass.substring(idx + 1);
    }

    /**
     * Returns the package portion of the internal class name in slash notation
     * (e.g. {@code "javacard/framework"}).
     *
     * <p>Returns an empty string if the class is in the default (unnamed) package.
     *
     * @return the package name in internal format, or {@code ""} for the default package
     */
    public String packageName() {
        int idx = thisClass.lastIndexOf('/');
        return idx < 0 ? "" : thisClass.substring(0, idx);
    }
}
