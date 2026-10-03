package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Member lookup in the export entry of an imported class (JCVM 3.1 §5.8 fields, §5.9 methods).
 */
final class ExportMembers {

    private ExportMembers() {}

    /**
     * Returns whether the export entry lists a field with the given name (§5.8).
     *
     * @param cls  export entry
     * @param name field name
     * @return {@code true} if the entry lists the field
     */
    static boolean declaresField(ExportFile.ClassExport cls, String name) {
        return cls.fields().stream().anyMatch(f -> f.name().equals(name));
    }

    /**
     * Returns whether the export entry lists a method with the given name and descriptor (§5.9).
     *
     * @param cls  export entry
     * @param name method name
     * @param desc method descriptor
     * @return {@code true} if the entry lists the method
     */
    static boolean declaresMethod(ExportFile.ClassExport cls, String name, String desc) {
        return cls.methods().stream().anyMatch(m -> m.name().equals(name) && m.descriptor().equals(desc));
    }

    /**
     * Returns whether the export entry lists a public instance method with the given name and
     * descriptor: {@code ACC_PUBLIC} set, neither static nor a constructor (§5.9 Table 5-5).
     *
     * @param cls  export entry
     * @param name method name
     * @param desc method descriptor
     * @return {@code true} if the entry lists such a method
     */
    static boolean declaresPublicInstanceMethod(ExportFile.ClassExport cls, String name, String desc) {
        return cls.methods().stream().anyMatch(m -> m.name().equals(name) && m.descriptor().equals(desc)
                && (m.accessFlags() & ExportFile.ACC_PUBLIC) != 0 && !m.isStaticOrConstructor());
    }

    /**
     * Finds an exported field by name.
     *
     * @param classExport export entry
     * @param name        field name
     * @return the field
     * @throws NoSuchElementException if the entry does not list the field
     */
    static ExportFile.FieldExport field(ExportFile.ClassExport classExport, String name) {
        for (ExportFile.FieldExport fe : classExport.fields()) {
            if (fe.name().equals(name)) return fe;
        }
        throw new NoSuchElementException(
                "Field not found in export: " + classExport.name() + "." + name);
    }

    /**
     * Finds an exported method by name <em>and</em> descriptor: overloads have different tokens
     * (JCVM 3.1 §4.3.7.4, §4.3.7.6, §5.9), so a method with the same name but another descriptor
     * is never a substitute.
     *
     * @param classExport export entry
     * @param name        method name
     * @param desc        method descriptor
     * @return the method
     * @throws NoSuchElementException if the entry does not list the method, naming its overloads
     */
    static ExportFile.MethodExport method(ExportFile.ClassExport classExport, String name, String desc) {
        for (ExportFile.MethodExport me : classExport.methods()) {
            if (me.name().equals(name) && me.descriptor().equals(desc)) return me;
        }
        List<String> overloads = classExport.methods().stream()
                .filter(me -> me.name().equals(name))
                .map(me -> me.name() + me.descriptor())
                .toList();
        throw new NoSuchElementException("Method not found in export: " + classExport.name() + "." + name + desc
                + (overloads.isEmpty() ? "" : " (the export file declares " + String.join(", ", overloads)
                + "; the classes were compiled against a different API version or stubs)"));
    }
}
