package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Determines the classes that the Class and Descriptor components name outside the
 * constant pool, so that their packages are imported even when no bytecode refers to them
 * (JCVM 3.1 §6.7: the Import component lists every package referenced in the CAP file).
 *
 * <p>These are the superclasses, the implemented and extended interfaces together with all their
 * superinterfaces (class_info and interface_info, §6.9), and the classes used in field and method
 * descriptors (type_descriptor_info, §6.14). An applet that implements
 * {@code javacardx.apdu.ExtendedLength} and uses nothing else of {@code javacardx.apdu} must
 * still import that package.
 */
public final class StructuralReferences {

    private StructuralReferences() {}

    /**
     * Returns the internal names of the classes named by class or descriptor structures.
     *
     * @param refs    references of the package's class files
     * @param imports candidate imports, used to expand the superinterfaces of imported interfaces
     * @return class names (internal form) in first-reference order; may include classes of the
     *         package being converted
     */
    public static Set<String> classes(List<Reference> refs, List<ImportedPackage> imports) {
        Map<String, ExportFile> exports = new HashMap<>();
        for (ImportedPackage imp : imports) exports.put(imp.exportFile().packageName(), imp.exportFile());
        Set<String> result = new LinkedHashSet<>();
        for (Reference r : refs) {
            switch (r.kind()) {
                case SUPERCLASS, TYPE -> result.add(r.owner());
                case INTERFACE -> addWithSuperinterfaces(r.owner(), exports, result);
                default -> { /* constant pool references are tracked during translation */ }
            }
        }
        return result;
    }

    private static void addWithSuperinterfaces(String iface, Map<String, ExportFile> exports,
                                               Set<String> result) {
        if (!result.add(iface)) return;
        findExport(iface, exports).ifPresent(cls -> {
            for (String sup : cls.interfaces()) addWithSuperinterfaces(sup, exports, result);
        });
    }

    private static Optional<ExportFile.ClassExport> findExport(String cls, Map<String, ExportFile> exports) {
        int slash = cls.lastIndexOf('/');
        ExportFile ef = exports.get(slash < 0 ? "" : cls.substring(0, slash));
        if (ef == null) return Optional.empty();
        String simple = cls.substring(slash + 1);
        return ef.classes().stream()
                .filter(c -> c.name().equals(cls) || c.name().equals(simple))
                .findFirst();
    }
}
