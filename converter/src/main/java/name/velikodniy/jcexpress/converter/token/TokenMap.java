package name.velikodniy.jcexpress.converter.token;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Immutable data structure holding all token assignments for a single Java Card package.
 *
 * <p>A {@code TokenMap} is produced by {@link TokenAssigner} during <b>Stage 3: Token Assignment</b>
 * of the converter pipeline and is consumed by every subsequent stage:
 *
 * <ul>
 *   <li><b>Stage 4 (Reference Resolution)</b> -- translates symbolic class/method/field references
 *       into compact token-based references for the JCVM constant pool.</li>
 *   <li><b>Stage 5 (Bytecode Translation)</b> -- emits token values in translated JCVM bytecodes
 *       (e.g., {@code invokevirtual} operands encode the virtual method token).</li>
 *   <li><b>Stage 6 (CAP Generation)</b> -- writes class, method, and descriptor components using
 *       tokens as indices.</li>
 *   <li><b>Stage 7 (Export Generation)</b> -- produces the {@code .exp} file that maps public
 *       API elements to their assigned tokens for use by downstream packages.</li>
 * </ul>
 *
 * <p>Tokens follow JCVM 3.1 §4.3.7. Class tokens are package-scoped and assigned only to public
 * classes and interfaces (package-visible ones have {@link #NO_TOKEN}); method and field tokens
 * are class-scoped. The {@link #classes()} list is in Class component order (§6.9: interfaces
 * first, supertypes before subtypes), which is <em>not</em> the token order; look classes up by
 * name, never by token.
 *
 * @param packageName package name in dot notation (e.g., {@code "javacard.framework"})
 * @param classes     classes and interfaces in Class component order
 * @see TokenAssigner
 * @see ExportFile
 */
public record TokenMap(
        String packageName,
        List<ClassEntry> classes
) {
    /** Token value of a package-visible class or interface, which has no class token (§6.14.2). */
    public static final int NO_TOKEN = 0xFF;

    /**
     * Finds a class entry by its JVM internal name.
     *
     * @param internalName JVM internal name (e.g., {@code "com/example/MyApplet"})
     * @return the matching class entry
     * @throws NoSuchElementException if no class with the given name exists in this map
     */
    public ClassEntry findClass(String internalName) {
        for (ClassEntry ce : classes) {
            if (ce.internalName().equals(internalName)) return ce;
        }
        throw new NoSuchElementException("Class not found: " + internalName);
    }

    /**
     * Returns the token assigned to the class identified by the given internal name.
     *
     * @param internalName JVM internal name (e.g., {@code "com/example/MyApplet"})
     * @return the class token (0-based, package-scoped), or {@link #NO_TOKEN} for a
     *         package-visible class or interface (JCVM 3.1 §4.3.7.2)
     * @throws NoSuchElementException if no class with the given name exists in this map
     */
    public int classToken(String internalName) {
        return findClass(internalName).token();
    }

    /**
     * Returns the total number of classes in this package.
     *
     * @return the class count
     */
    public int classCount() {
        return classes.size();
    }

    /**
     * Token assignments for a single class, including its methods and fields.
     *
     * <p>Virtual and static methods have independent token namespaces (both start at 0).
     * Similarly, instance and static fields have independent token namespaces (JCVM 3.1 §4.3.7):
     * the token kind is encoded in the reference type, so the same numeric token can refer to
     * different entities depending on context. Package-visible virtual methods carry private
     * tokens with the high bit set (0x80 | n, §4.3.7.6).
     *
     * @param internalName   JVM internal name of the class (e.g., {@code "com/example/MyApplet"})
     * @param token          the class token, or {@link #NO_TOKEN} if the class is package-visible
     * @param virtualMethods for a class: virtual method tokens including the entries inherited
     *                       from superclasses; for an interface: the interface method tokens
     *                       including the methods of its superinterfaces (§4.3.7.7)
     * @param staticMethods  static method token entries (class-scoped, 0-based)
     * @param instanceFields instance field token entries (class-scoped, 0-based)
     * @param staticFields   static field token entries (class-scoped, 0-based)
     */
    public record ClassEntry(
            String internalName,
            int token,
            List<MethodEntry> virtualMethods,
            List<MethodEntry> staticMethods,
            List<FieldEntry> instanceFields,
            List<FieldEntry> staticFields
    ) {
        /**
         * Finds a virtual method entry by name and descriptor.
         *
         * @param name       method name (e.g., {@code "process"})
         * @param descriptor method descriptor (e.g., {@code "(Ljavacard/framework/APDU;)V"})
         * @return the matching method entry
         * @throws NoSuchElementException if no virtual method matches
         */
        public MethodEntry findVirtualMethod(String name, String descriptor) {
            for (MethodEntry me : virtualMethods) {
                if (me.name().equals(name) && me.descriptor().equals(descriptor)) return me;
            }
            throw new NoSuchElementException("Virtual method not found: " + name + descriptor);
        }

        /**
         * Finds a static method entry by name and descriptor.
         *
         * @param name       method name (e.g., {@code "install"})
         * @param descriptor method descriptor
         * @return the matching method entry
         * @throws NoSuchElementException if no static method matches
         */
        public MethodEntry findStaticMethod(String name, String descriptor) {
            for (MethodEntry me : staticMethods) {
                if (me.name().equals(name) && me.descriptor().equals(descriptor)) return me;
            }
            throw new NoSuchElementException("Static method not found: " + name + descriptor);
        }

        /**
         * Finds an instance field entry by name.
         *
         * @param name field name
         * @return the matching field entry
         * @throws NoSuchElementException if no instance field matches
         */
        public FieldEntry findInstanceField(String name) {
            for (FieldEntry fe : instanceFields) {
                if (fe.name().equals(name)) return fe;
            }
            throw new NoSuchElementException("Instance field not found: " + name);
        }

        /**
         * Finds a static field entry by name.
         *
         * @param name field name
         * @return the matching field entry
         * @throws NoSuchElementException if no static field matches
         */
        public FieldEntry findStaticField(String name) {
            for (FieldEntry fe : staticFields) {
                if (fe.name().equals(name)) return fe;
            }
            throw new NoSuchElementException("Static field not found: " + name);
        }
    }

    /**
     * A method's token assignment.
     *
     * @param name       method name (e.g., {@code "process"})
     * @param descriptor method descriptor in JVM format (e.g., {@code "(Ljavacard/framework/APDU;)V"})
     * @param token      the assigned token (0-based, class-scoped within the virtual or static namespace)
     */
    public record MethodEntry(String name, String descriptor, int token) {}

    /**
     * A field's token assignment.
     *
     * @param name       field name
     * @param descriptor field descriptor in JVM format (e.g., {@code "B"} for byte, {@code "[S"} for short array)
     * @param token      the assigned token (0-based, class-scoped within the instance or static namespace)
     */
    public record FieldEntry(String name, String descriptor, int token) {}
}
