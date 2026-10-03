package name.velikodniy.jcexpress.converter.capcheck;

import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.ClassEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.ImplementedInterface;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.InterfaceEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.TypeEntry;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.FieldDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Structural invariants of a CAP file derived from the JCVM 3.1 specification, Chapter 6.
 *
 * <p>The checks cross-validate the Class, Descriptor, Constant Pool, Static Field, Method and
 * Export components against each other (for example: every dispatch-table entry must point at a
 * method whose Descriptor token matches the table slot, §6.9.2.3; instance field tokens must
 * follow §4.3.7.5). They are meant to catch silently wrong output that a green unit test would
 * otherwise miss, without requiring Oracle's off-card verifier.
 *
 * <p>Checks that depend on the content of imported packages (external superclasses) are
 * skipped where the information is not in the CAP file.
 */
public final class CapInvariants {

    private CapInvariants() {}

    /**
     * Checks all invariants.
     *
     * @param cap parsed CAP file
     * @return human-readable descriptions of every violated invariant (empty if the CAP is valid)
     */
    public static List<String> check(CapImage cap) {
        List<String> v = new ArrayList<>();
        Model m;
        try {
            m = new Model(cap);
        } catch (RuntimeException e) {
            v.add("unparseable CAP: " + e.getMessage());
            return v;
        }
        checkDescriptorLinks(m, v);
        checkOrderingAndRefs(m, v);
        checkClassTokens(m, v);
        for (ClassEntry ce : m.classComponent.classes()) {
            m.descriptorOf(ce).ifPresent(cd -> {
                checkInstanceFields(ce, cd, v);
                checkDispatchTables(m, ce, cd, v);
                checkImplementedInterfaces(m, ce, cd, v);
            });
        }
        for (InterfaceEntry ie : m.classComponent.interfaces()) {
            m.descriptorOf(ie).ifPresent(cd -> checkInterface(m, ie, cd, v));
        }
        CapReferenceInvariants.check(m, v);
        return v;
    }

    private static void checkDescriptorLinks(Model m, List<String> v) {
        for (ClassDescriptor cd : m.descriptor.classes()) {
            Optional<TypeEntry> entry = m.classComponent.at(cd.thisClassRef());
            if (entry.isEmpty()) {
                v.add("Descriptor this_class_ref " + hex(cd.thisClassRef()) + " is not a Class component entry");
            } else if ((entry.get() instanceof InterfaceEntry) != cd.isInterface()) {
                v.add("Descriptor " + hex(cd.thisClassRef()) + ": ACC_INTERFACE differs from Class component");
            }
        }
        for (TypeEntry e : m.classComponent.entries()) {
            if (m.descriptor.byClassRef(e.offset()).isEmpty()) {
                v.add("Class component entry at " + hex(e.offset()) + " has no class_descriptor_info");
            }
        }
    }

    private static void checkOrderingAndRefs(Model m, List<String> v) {
        boolean seenClass = false;
        for (TypeEntry e : m.classComponent.entries()) {
            if (e instanceof InterfaceEntry ie) {
                if (seenClass) {
                    v.add("interface_info at " + hex(e.offset()) + " follows a class_info (6.9)");
                }
                for (int ref : ie.superinterfaces()) {
                    requireEarlierInterface(m, ref, ie.offset(), "superinterface of " + hex(ie.offset()), v);
                }
            } else if (e instanceof ClassEntry ce) {
                seenClass = true;
                int sup = ce.superClassRef();
                if (sup != 0xFFFF && !isExternal(sup)) {
                    Optional<TypeEntry> s = m.classComponent.at(sup);
                    if (s.isEmpty() || !(s.get() instanceof ClassEntry) || sup >= ce.offset()) {
                        v.add("class " + hex(ce.offset()) + ": super_class_ref " + hex(sup)
                                + " is not an earlier class_info (6.9, 6.9.2.3)");
                    }
                }
                for (ImplementedInterface ii : ce.interfaces()) {
                    requireEarlierInterface(m, ii.interfaceRef(), Integer.MAX_VALUE,
                            "implemented interface of " + hex(ce.offset()), v);
                }
            }
        }
    }

    private static void requireEarlierInterface(Model m, int ref, int before, String what, List<String> v) {
        if (isExternal(ref)) {
            return;
        }
        Optional<TypeEntry> t = m.classComponent.at(ref);
        if (t.isEmpty() || !(t.get() instanceof InterfaceEntry) || ref >= before) {
            v.add(what + ": class_ref " + hex(ref) + " is not an earlier interface_info (6.8.1, 6.9)");
        }
    }

    private static void checkClassTokens(Model m, List<String> v) {
        List<Integer> publicTokens = new ArrayList<>();
        for (ClassDescriptor cd : m.descriptor.classes()) {
            if (cd.isPublic()) {
                publicTokens.add(cd.token());
            } else if (cd.token() != 0xFF) {
                v.add("package-visible class " + hex(cd.thisClassRef()) + " has token " + cd.token()
                        + " instead of 0xFF (4.3.7.2, 6.14.2)");
            }
        }
        List<Integer> sorted = publicTokens.stream().sorted().toList();
        if (!sorted.equals(IntStream.range(0, sorted.size()).boxed().toList())) {
            v.add("public class tokens are not consecutive from 0 (4.3.7.2): " + sorted);
        }
    }

    private static void checkInstanceFields(ClassEntry ce, ClassDescriptor cd, List<String> v) {
        List<FieldDescriptor> fields = cd.fields().stream().filter(f -> !f.isStatic())
                .sorted(Comparator.comparingInt(FieldDescriptor::token)).toList();
        String where = "class " + hex(ce.offset());
        int cell = 0;
        for (FieldDescriptor f : fields) {
            if (f.token() != cell) {
                v.add(where + ": instance field token " + f.token() + " expected " + cell
                        + " (tokens are consecutive, int takes two, 4.3.7.5)");
            }
            if (f.instanceClassRef() != cd.thisClassRef() || f.instanceToken() != f.token()) {
                v.add(where + ": field_ref of instance field token " + f.token() + " is inconsistent (6.14.3)");
            }
            cell = f.token() + (f.isInt() ? 2 : 1);
        }
        if (ce.declaredInstanceSize() != cell) {
            v.add(where + ": declared_instance_size=" + ce.declaredInstanceSize() + " but fields need "
                    + cell + " cells (6.9.2.3)");
        }
        checkReferenceBlock(ce, fields, where, v);
        checkFieldTokenGroups(fields, where, v);
    }

    private static void checkReferenceBlock(ClassEntry ce, List<FieldDescriptor> fields, String where,
                                            List<String> v) {
        List<Integer> refs = fields.stream().filter(FieldDescriptor::isReference)
                .map(FieldDescriptor::token).toList();
        int expectedFirst = refs.isEmpty() ? 0xFF : refs.getFirst();
        if (ce.firstReferenceToken() != expectedFirst || ce.referenceCount() != refs.size()) {
            v.add(where + ": first_reference_token/reference_count=" + ce.firstReferenceToken() + "/"
                    + ce.referenceCount() + " but reference field tokens are " + refs + " (6.9.2.3)");
        }
        for (int i = 1; i < refs.size(); i++) {
            if (refs.get(i) != refs.get(i - 1) + 1) {
                v.add(where + ": reference instance field tokens are not contiguous: " + refs + " (4.3.7.5)");
                return;
            }
        }
    }

    private static void checkFieldTokenGroups(List<FieldDescriptor> fields, String where, List<String> v) {
        int maxPublic = fields.stream().filter(FieldDescriptor::isExternallyVisible)
                .mapToInt(FieldDescriptor::token).max().orElse(-1);
        int minPrivate = fields.stream().filter(f -> !f.isExternallyVisible())
                .mapToInt(FieldDescriptor::token).min().orElse(Integer.MAX_VALUE);
        if (maxPublic > minPrivate) {
            v.add(where + ": public/protected instance field tokens must be below package/private tokens (4.3.7.5)");
        }
        if (maxToken(fields, true, false) > minToken(fields, true, true)) {
            v.add(where + ": public primitive field tokens must be below public reference tokens (4.3.7.5)");
        }
        if (maxToken(fields, false, true) > minToken(fields, false, false)) {
            v.add(where + ": private reference field tokens must be below private primitive tokens (4.3.7.5)");
        }
    }

    private static int maxToken(List<FieldDescriptor> fields, boolean visible, boolean reference) {
        return fields.stream().filter(f -> f.isExternallyVisible() == visible && f.isReference() == reference)
                .mapToInt(FieldDescriptor::token).max().orElse(-1);
    }

    private static int minToken(List<FieldDescriptor> fields, boolean visible, boolean reference) {
        return fields.stream().filter(f -> f.isExternallyVisible() == visible && f.isReference() == reference)
                .mapToInt(FieldDescriptor::token).min().orElse(Integer.MAX_VALUE);
    }

    private static void checkDispatchTables(Model m, ClassEntry ce, ClassDescriptor cd, List<String> v) {
        String where = "class " + hex(ce.offset());
        for (MethodDescriptor md : cd.methods()) {
            if (!md.isVirtual()) {
                continue;
            }
            boolean pkg = (md.token() & 0x80) != 0;
            int t = md.token() & 0x7F;
            int base = pkg ? ce.packageBase() : ce.publicBase();
            List<Integer> table = pkg ? ce.packageTable() : ce.publicTable();
            if (t < base || t >= base + table.size() || table.get(t - base) != md.methodOffset()) {
                v.add(where + ": " + (pkg ? "package" : "public") + " table has no entry for token "
                        + hex(md.token()) + " -> method " + hex(md.methodOffset()) + " (6.9.2.3)");
            }
        }
        checkTableEntries(m, ce, false, where, v);
        checkTableEntries(m, ce, true, where, v);
        checkTableRanges(m, ce, where, v);
    }

    private static void checkTableEntries(Model m, ClassEntry ce, boolean pkg, String where, List<String> v) {
        List<Integer> table = pkg ? ce.packageTable() : ce.publicTable();
        int base = pkg ? ce.packageBase() : ce.publicBase();
        for (int i = 0; i < table.size(); i++) {
            int offset = table.get(i);
            int token = pkg ? (0x80 | (base + i)) : base + i;
            if (offset == 0xFFFF) {
                if (pkg) {
                    v.add(where + ": package table entry for token " + hex(token) + " is 0xFFFF (6.9.2.3)");
                }
                continue;
            }
            Optional<MethodDescriptor> md = m.methodAt(offset);
            if (md.isEmpty() || !md.get().isVirtual() || md.get().token() != token) {
                v.add(where + ": table entry for token " + hex(token) + " points to " + hex(offset)
                        + " which is not a virtual method with that token (6.9.2.3)");
            }
        }
    }

    private static void checkTableRanges(Model m, ClassEntry ce, String where, List<String> v) {
        int sup = ce.superClassRef();
        Optional<ClassEntry> internalSuper = sup == 0xFFFF || isExternal(sup) ? Optional.empty()
                : m.classComponent.at(sup).filter(ClassEntry.class::isInstance).map(ClassEntry.class::cast);
        if (internalSuper.isPresent()) {
            ClassEntry s = internalSuper.get();
            requireRange(where + " public", ce.publicBase(), ce.publicCount(),
                    s.publicBase() + s.publicCount(), v);
            requireRange(where + " package", ce.packageBase(), ce.packageCount(),
                    s.packageBase() + s.packageCount(), v);
        } else {
            if (sup == 0xFFFF && ce.publicCount() == 0 && ce.publicBase() != 0) {
                v.add(where + ": empty public table of a root class must have base 0 (6.9.2.3)");
            }
            if (ce.packageCount() == 0 && ce.packageBase() != 0) {
                v.add(where + ": empty package table with external/no superclass must have base 0 (6.9.2.3)");
            }
        }
    }

    private static void requireRange(String what, int base, int count, int superEnd, List<String> v) {
        if (count == 0 && base != superEnd) {
            v.add(what + " table is empty but base=" + base + " != superclass base+count=" + superEnd + " (6.9.2.3)");
        }
        if (count > 0 && base + count < superEnd) {
            v.add(what + " table ends at " + (base + count) + " before the superclass table end "
                    + superEnd + " (6.9.2.3)");
        }
    }

    private static void checkImplementedInterfaces(Model m, ClassEntry ce, ClassDescriptor cd, List<String> v) {
        String where = "class " + hex(ce.offset());
        Set<Integer> refs = new HashSet<>();
        ce.interfaces().forEach(ii -> refs.add(ii.interfaceRef()));
        if (!refs.equals(new HashSet<>(cd.interfaces()))) {
            v.add(where + ": Descriptor interfaces " + cd.interfaces() + " differ from Class component "
                    + refs + " (6.14.2)");
        }
        for (ImplementedInterface ii : ce.interfaces()) {
            m.interfaceAt(ii.interfaceRef()).ifPresent(ie -> {
                if (!refs.containsAll(ie.superinterfaces())) {
                    v.add(where + ": implemented interfaces miss superinterfaces of "
                            + hex(ii.interfaceRef()) + " (6.9.2.3)");
                }
                m.descriptorOf(ie).ifPresent(icd -> {
                    if (ii.index().size() != icd.methods().size()) {
                        v.add(where + ": index count " + ii.index().size() + " for interface "
                                + hex(ii.interfaceRef()) + " != its method count " + icd.methods().size()
                                + " (6.9.2.5)");
                    }
                });
            });
            if (ii.index().contains(0xFF)) {
                v.add(where + ": interface " + hex(ii.interfaceRef()) + " has an unmapped method (6.9.2.5)");
            }
        }
        if (ce.interfaces().size() > 15) {
            v.add(where + ": interface_count > 15 (6.9.2.1)");
        }
    }

    private static void checkInterface(Model m, InterfaceEntry ie, ClassDescriptor cd, List<String> v) {
        String where = "interface " + hex(ie.offset());
        if (!cd.interfaces().isEmpty()) {
            v.add(where + ": Descriptor interface_count must be 0 for an interface (6.14.2)");
        }
        List<Integer> tokens = cd.methods().stream().map(MethodDescriptor::token).sorted().toList();
        if (!tokens.equals(IntStream.range(0, tokens.size()).boxed().toList())) {
            v.add(where + ": interface method tokens are not 0..n-1 (4.3.7.7): " + tokens);
        }
        for (MethodDescriptor md : cd.methods()) {
            if (md.methodOffset() != 0 || md.bytecodeCount() != 0) {
                v.add(where + ": interface method must have method_offset 0 and no bytecode (6.14.4)");
            }
        }
        for (int ref : ie.superinterfaces()) {
            m.interfaceAt(ref).ifPresent(s -> {
                if (!ie.superinterfaces().containsAll(s.superinterfaces())) {
                    v.add(where + ": superinterfaces miss indirect superinterfaces of " + hex(ref) + " (6.9.2.2)");
                }
            });
        }
        if (ie.superinterfaces().size() > 14) {
            v.add(where + ": more than 14 superinterfaces (6.9.2.1)");
        }
        boolean shareable = ie.superinterfaces().stream().anyMatch(m::isShareableRef);
        if (shareable != ie.isShareable()) {
            v.add(where + ": ACC_SHAREABLE=" + ie.isShareable() + " but extends Shareable=" + shareable
                    + " (6.9.2.1)");
        }
    }

    static boolean isExternal(int classRef) {
        return (classRef & 0x8000) != 0;
    }

    static String hex(int value) {
        return String.format("0x%04x", value);
    }
}
