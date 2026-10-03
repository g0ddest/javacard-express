package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.input.ClassInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Orders the classes and interfaces of a package the way they appear in the Class component.
 *
 * <p>JCVM 3.1 §6.9: interfaces precede classes; "the entries are ordered based on hierarchy such
 * that a superinterface has a lower index than any of its subinterfaces" and "a superclass has a
 * lower index than any of its subclasses". Any other order is unspecified; this implementation
 * starts from the names in lexicographic order and moves a type behind its in-package supertypes
 * by swapping, which gives a deterministic result independent of the file system.
 */
final class ClassOrder {

    private ClassOrder() {}

    /**
     * Returns the Class component order of the given types.
     *
     * @param classes all classes and interfaces of the package
     * @return interfaces (superinterfaces first) followed by classes (superclasses first)
     */
    static List<ClassInfo> componentOrder(List<ClassInfo> classes) {
        List<ClassInfo> interfaces = new ArrayList<>();
        List<ClassInfo> plain = new ArrayList<>();
        for (ClassInfo ci : classes) {
            (ci.isInterface() ? interfaces : plain).add(ci);
        }
        List<ClassInfo> result = new ArrayList<>(classes.size());
        result.addAll(hierarchyOrder(interfaces, ClassInfo::interfaces));
        result.addAll(hierarchyOrder(plain, ci -> ci.superClass() == null ? List.of() : List.of(ci.superClass())));
        return result;
    }

    private static List<ClassInfo> hierarchyOrder(List<ClassInfo> types, Function<ClassInfo, List<String>> supers) {
        List<ClassInfo> order = new ArrayList<>(types);
        order.sort(Comparator.comparing(ClassInfo::thisClass));
        int swapsLeft = order.size() * order.size() + 1;
        int i = 0;
        while (i < order.size()) {
            int later = laterSupertype(order, i, supers);
            if (later < 0) {
                i++;
            } else if (swapsLeft-- == 0) {
                throw new IllegalStateException("Cyclic inheritance involving " + order.get(i).thisClass());
            } else {
                Collections.swap(order, i, later);
            }
        }
        return order;
    }

    /** Returns the index of a supertype of {@code order[i]} that appears after it, or -1. */
    private static int laterSupertype(List<ClassInfo> order, int i, Function<ClassInfo, List<String>> supers) {
        for (String name : supers.apply(order.get(i))) {
            for (int k = i + 1; k < order.size(); k++) {
                if (order.get(k).thisClass().equals(name)) {
                    return k;
                }
            }
        }
        return -1;
    }
}
