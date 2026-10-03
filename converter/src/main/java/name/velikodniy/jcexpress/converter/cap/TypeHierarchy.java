package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class and interface hierarchy of the package being converted, completed with what the export
 * files say about imported types. Shared by the Class (§6.9), Descriptor (§6.14) and Export
 * (§6.13) components so that all of them describe the same interface sets.
 */
final class TypeHierarchy {

    private final Map<String, ClassInfo> internal = new HashMap<>();
    private final TokenMap tokenMap;
    private final ImportedTypes imported;

    /**
     * Creates the hierarchy view.
     *
     * @param classes  all classes and interfaces of the package
     * @param tokenMap token assignment of the package
     * @param imported information about imported types
     */
    TypeHierarchy(List<ClassInfo> classes, TokenMap tokenMap, ImportedTypes imported) {
        classes.forEach(ci -> internal.put(ci.thisClass(), ci));
        this.tokenMap = tokenMap;
        this.imported = imported;
    }

    boolean isInternal(String name) {
        return internal.containsKey(name);
    }

    ClassInfo classInfo(String name) {
        return internal.get(name);
    }

    /**
     * Returns the given interfaces together with all of their superinterfaces, each listed once
     * and after its own superinterfaces. This is the {@code superinterfaces[]} list of an
     * interface (§6.9.2.2) and the {@code interfaces[]} list of a class (§6.9.2.3, §6.14.2);
     * interfaces implemented only by a superclass are not included.
     *
     * @param direct directly implemented or extended interfaces, in declaration order
     * @return the interface hierarchy
     */
    List<String> interfaceClosure(List<String> direct) {
        Set<String> out = new LinkedHashSet<>();
        for (String iface : direct) {
            out.addAll(interfaceClosure(superInterfaces(iface)));
            out.add(iface);
        }
        return List.copyOf(out);
    }

    private List<String> superInterfaces(String iface) {
        ClassInfo ci = internal.get(iface);
        return ci != null ? ci.interfaces() : imported.superInterfaces(iface);
    }

    /** §6.9.2.1: an interface is shareable if it is or extends {@code javacard.framework.Shareable}. */
    boolean isShareableInterface(String name) {
        return TokenAssigner.isShareableInterface(name, internal, imported);
    }

    /**
     * §6.9.2.1: a class is shareable if it or any of its superclasses implements a shareable
     * interface.
     */
    boolean isShareableClass(ClassInfo ci) {
        if (interfaceClosure(ci.interfaces()).stream().anyMatch(this::isShareableInterface)) {
            return true;
        }
        String sup = ci.superClass();
        if (sup == null) {
            return false;
        }
        ClassInfo superInfo = internal.get(sup);
        return superInfo != null ? isShareableClass(superInfo) : imported.isShareable(sup);
    }

    /**
     * Returns the methods of an interface with their interface method tokens (§4.3.7.7), ordered
     * by token.
     */
    List<TokenMap.MethodEntry> interfaceMethods(String iface) {
        List<TokenMap.MethodEntry> methods = internal.containsKey(iface)
                ? tokenMap.findClass(iface).virtualMethods()
                : imported.virtualMethods(iface);
        List<TokenMap.MethodEntry> sorted = new ArrayList<>(methods);
        sorted.sort(Comparator.comparingInt(TokenMap.MethodEntry::token));
        return sorted;
    }

    /** Returns the virtual methods (with tokens) the class inherits from its superclass. */
    List<TokenMap.MethodEntry> inheritedVirtuals(ClassInfo ci) {
        String sup = ci.superClass();
        if (sup == null) {
            return List.of();
        }
        return internal.containsKey(sup) ? tokenMap.findClass(sup).virtualMethods() : imported.virtualMethods(sup);
    }
}
