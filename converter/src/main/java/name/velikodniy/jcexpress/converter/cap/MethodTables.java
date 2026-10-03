package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Builds the {@code public_virtual_method_table} and {@code package_virtual_method_table} of a
 * {@code class_info} structure (JCVM 3.1 §6.9.2.3).
 *
 * <p>A table starts at the smallest token of a method the class declares or overrides in that
 * namespace and runs up to the largest token of the namespace in the class hierarchy, so that
 * every token at or above the base can be dispatched through this class (§7.5.57.1). A class that
 * declares no method of the namespace has an empty table whose base is one past the largest
 * inherited token, i.e. the superclass's base plus count, or zero when nothing is inherited
 * (package tokens are never inherited from another package).
 */
final class MethodTables {

    /** Entry for a method implemented in an imported package (§6.9.2.3). */
    static final int IMPORTED = 0xFFFF;
    private static final int PRIVATE_TOKEN = 0x80;

    private final TypeHierarchy hierarchy;
    private final Map<String, Integer> methodOffsets;

    /**
     * Creates the table builder.
     *
     * @param hierarchy     the package's type hierarchy
     * @param methodOffsets {@code "class:name:descriptor"} to the offset of the method_info in
     *                      the Method component
     */
    MethodTables(TypeHierarchy hierarchy, Map<String, Integer> methodOffsets) {
        this.hierarchy = hierarchy;
        this.methodOffsets = methodOffsets;
    }

    /**
     * One dispatch table.
     *
     * @param base    the {@code *_method_table_base} item (token value, without the high bit)
     * @param entries method offsets for tokens {@code base .. base + entries.size() - 1}
     */
    record Table(int base, List<Integer> entries) {
        int count() {
            return entries.size();
        }
    }

    /**
     * Builds the public ({@code packageNamespace == false}) or package dispatch table of a class.
     *
     * @param ci               the class
     * @param entry            its token assignment (virtual methods incl. inherited ones)
     * @param packageNamespace {@code true} for the package_virtual_method_table
     * @return the table
     */
    Table table(ClassInfo ci, TokenMap.ClassEntry entry, boolean packageNamespace) {
        List<TokenMap.MethodEntry> methods = entry.virtualMethods().stream()
                .filter(m -> isPrivate(m.token()) == packageNamespace).toList();
        int max = methods.stream().mapToInt(m -> m.token() & ~PRIVATE_TOKEN).max().orElse(-1);
        OptionalInt base = methods.stream().filter(m -> declares(ci, m))
                .mapToInt(m -> m.token() & ~PRIVATE_TOKEN).min();
        if (base.isEmpty()) {
            return new Table(max + 1, List.of());
        }
        List<Integer> entries = new ArrayList<>();
        for (int t = base.getAsInt(); t <= max; t++) {
            int token = packageNamespace ? PRIVATE_TOKEN | t : t;
            Optional<TokenMap.MethodEntry> m = methods.stream().filter(e -> e.token() == token).findFirst();
            entries.add(m.isPresent() ? implementation(ci, m.get(), packageNamespace) : IMPORTED);
        }
        return new Table(base.getAsInt(), List.copyOf(entries));
    }

    /**
     * Returns the Method component offset of the implementation of {@code m} seen from class
     * {@code ci}: the declaration in the nearest class of the superclass chain, or
     * {@link #IMPORTED} if that class belongs to an imported package.
     */
    private int implementation(ClassInfo ci, TokenMap.MethodEntry m, boolean packageNamespace) {
        ClassInfo current = ci;
        while (current != null) {
            if (declares(current, m)) {
                Integer offset = methodOffsets.get(current.thisClass() + ":" + m.name() + ":" + m.descriptor());
                if (offset == null) {
                    throw new IllegalStateException("No method_info for " + current.thisClass() + "."
                            + m.name() + m.descriptor());
                }
                return offset;
            }
            current = current.superClass() == null ? null : hierarchy.classInfo(current.superClass());
        }
        if (packageNamespace) {
            throw new IllegalStateException("Package-visible method " + m.name() + m.descriptor()
                    + " of " + ci.thisClass() + " has no implementation in the package (§6.9.2.3)");
        }
        return IMPORTED;
    }

    private static boolean declares(ClassInfo ci, TokenMap.MethodEntry m) {
        for (MethodInfo mi : ci.methods()) {
            if (!mi.isStatic() && !mi.isConstructor() && !mi.isPrivate()
                    && mi.name().equals(m.name()) && mi.descriptor().equals(m.descriptor())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrivate(int token) {
        return (token & PRIVATE_TOKEN) != 0;
    }
}
