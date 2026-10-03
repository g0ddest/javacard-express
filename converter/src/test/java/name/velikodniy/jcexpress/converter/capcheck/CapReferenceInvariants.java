package name.velikodniy.jcexpress.converter.capcheck;

import name.velikodniy.jcexpress.converter.capcheck.CapImage.ClassExport;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.CpEntry;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.Handler;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.StaticFieldView;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.ClassEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.InterfaceEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.TypeEntry;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.FieldDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static name.velikodniy.jcexpress.converter.capcheck.CapInvariants.hex;
import static name.velikodniy.jcexpress.converter.capcheck.CapInvariants.isExternal;

/**
 * Invariants that relate references between components: constant pool entries (§6.8),
 * static field offsets (§6.11, §6.14.3), exception handler indices (§6.14.4), the Export
 * component (§6.13) and class-level ACC_SHAREABLE (§6.9.2.1).
 */
final class CapReferenceInvariants {

    private CapReferenceInvariants() {}

    static void check(Model m, List<String> v) {
        Set<Integer> staticOffsets = checkStaticFields(m, v);
        checkConstantPool(m, staticOffsets, v);
        checkHandlers(m, v);
        checkExport(m, v);
        checkClassShareable(m, v);
    }

    private static Set<Integer> checkStaticFields(Model m, List<String> v) {
        StaticFieldView sf = m.cap.staticField();
        int refBytes = 2 * sf.referenceCount();
        if (sf.imageSize() != refBytes + sf.defaultValueCount() + sf.nonDefaultValues().length) {
            v.add("static field image_size " + sf.imageSize() + " inconsistent with its segments (6.11)");
        }
        if (sf.arrayInits().size() > sf.referenceCount()) {
            v.add("array_init_count exceeds reference_count (6.11)");
        }
        Set<Integer> offsets = new HashSet<>();
        for (ClassDescriptor cd : m.descriptor.classes()) {
            for (FieldDescriptor f : cd.fields()) {
                if (!f.isStatic()) {
                    continue;
                }
                int off = f.staticOffset();
                if (f.b1() != 0 || off >= Math.max(sf.imageSize(), 1) || !offsets.add(off)) {
                    v.add("static field descriptor of class " + hex(cd.thisClassRef()) + " has invalid or"
                            + " duplicate static_field_ref " + hex(off) + " (6.14.3)");
                }
                if (f.isReference() != (off < refBytes)) {
                    v.add("static field at " + hex(off) + " is in the wrong image segment (6.11 Table 6-13)");
                }
            }
        }
        return offsets;
    }

    private static void checkConstantPool(Model m, Set<Integer> staticOffsets, List<String> v) {
        int importCount = m.cap.imports().size();
        for (CpEntry e : m.cp) {
            String where = "CP[" + e.index() + "]";
            if (e.tag() < CpEntry.CLASSREF || e.tag() > CpEntry.STATIC_METHODREF) {
                v.add(where + ": invalid tag " + e.tag() + " (6.8)");
            } else if (e.isExternal()) {
                if ((e.b1() & 0x7F) >= importCount) {
                    v.add(where + ": package token " + (e.b1() & 0x7F) + " not in Import component (6.8.1)");
                }
            } else if (e.tag() <= CpEntry.SUPER_METHODREF) {
                checkInternalClassRefEntry(m, e, where, v);
            } else if (e.tag() == CpEntry.STATIC_FIELDREF) {
                if (e.b1() != 0 || !staticOffsets.contains(e.internalOffset())) {
                    v.add(where + ": StaticFieldref offset " + hex(e.internalOffset())
                            + " is not a static field described in the Descriptor (6.8.3)");
                }
            } else {
                checkStaticMethodRef(m, e, where, v);
            }
        }
    }

    private static void checkInternalClassRefEntry(Model m, CpEntry e, String where, List<String> v) {
        Optional<TypeEntry> target = m.classComponent.at(e.classRef());
        if (target.isEmpty()) {
            v.add(where + ": class_ref " + hex(e.classRef()) + " is not a Class component entry (6.8.1)");
            return;
        }
        if (e.tag() == CpEntry.CLASSREF) {
            if (e.b3() != 0) {
                v.add(where + ": Classref padding must be 0 (6.8.1)");
            }
            return;
        }
        if (!(target.get() instanceof ClassEntry)) {
            v.add(where + ": field/method ref class_ref must be a class_info (6.8.2)");
            return;
        }
        ClassDescriptor cd = m.descriptor.byClassRef(e.classRef()).orElseThrow();
        if (e.tag() == CpEntry.INSTANCE_FIELDREF) {
            boolean found = cd.fields().stream().anyMatch(f -> !f.isStatic() && f.token() == e.token());
            if (!found) {
                v.add(where + ": InstanceFieldref token " + e.token() + " is not an instance field of "
                        + hex(e.classRef()) + " (6.8.2)");
            }
        } else if (e.tag() == CpEntry.SUPER_METHODREF) {
            ClassEntry ce = (ClassEntry) target.get();
            if (!tokenInHierarchy(m, ce.superClassRef(), e.token())) {
                v.add(where + ": SuperMethodref token " + hex(e.token())
                        + " not defined in the superclass hierarchy of " + hex(e.classRef()) + " (6.8.2)");
            }
        } else if (!tokenInHierarchy(m, e.classRef(), e.token())) {
            v.add(where + ": VirtualMethodref token " + hex(e.token()) + " not defined in the hierarchy of "
                    + hex(e.classRef()) + " (6.8.2)");
        }
    }

    /**
     * Returns whether the virtual token is defined by a class in the hierarchy starting at
     * {@code classRef}; returns {@code true} when the walk reaches an imported class whose
     * tokens are not visible in this CAP file.
     */
    private static boolean tokenInHierarchy(Model m, int classRef, int token) {
        int ref = classRef;
        while (ref != 0xFFFF) {
            if (isExternal(ref)) {
                return (token & 0x80) == 0;
            }
            Optional<ClassDescriptor> cd = m.descriptor.byClassRef(ref);
            if (cd.isEmpty()) {
                return false;
            }
            if (cd.get().methods().stream().anyMatch(md -> md.isVirtual() && md.token() == token)) {
                return true;
            }
            ref = m.classComponent.at(ref).filter(ClassEntry.class::isInstance)
                    .map(t -> ((ClassEntry) t).superClassRef()).orElse(0xFFFF);
        }
        return false;
    }

    private static void checkStaticMethodRef(Model m, CpEntry e, String where, List<String> v) {
        Optional<MethodDescriptor> md = m.methodAt(e.internalOffset());
        boolean ok = e.b1() == 0 && md.isPresent()
                && (md.get().isStatic() || md.get().isInit() || md.get().isPrivate());
        if (!ok) {
            v.add(where + ": StaticMethodref offset " + hex(e.internalOffset())
                    + " is not a static/private method or constructor (6.8.3, 7.5.55)");
        }
    }

    private static void checkHandlers(Model m, List<String> v) {
        List<Handler> handlers = m.cap.exceptionHandlers();
        byte[] methodInfo = m.cap.requireBody(CapImage.TAG_METHOD);
        int described = 0;
        for (ClassDescriptor cd : m.descriptor.classes()) {
            if (cd.isInterface()) {
                continue;
            }
            for (MethodDescriptor md : cd.methods()) {
                described += md.handlerCount();
                checkMethodHandlers(md, handlers, methodInfo, v);
            }
        }
        if (described != handlers.size()) {
            v.add("Descriptor describes " + described + " exception handlers, Method component has "
                    + handlers.size() + " (6.14.4)");
        }
    }

    private static void checkMethodHandlers(MethodDescriptor md, List<Handler> handlers, byte[] methodInfo,
                                            List<String> v) {
        if (md.handlerCount() == 0) {
            if (md.handlerIndex() != 0) {
                v.add("method " + hex(md.methodOffset()) + ": exception_handler_index must be 0 (6.14.4)");
            }
            return;
        }
        int header = (methodInfo[md.methodOffset()] & 0x80) != 0 ? 4 : 2;
        int start = md.methodOffset() + header;
        int end = start + md.bytecodeCount();
        for (int i = md.handlerIndex(); i < md.handlerIndex() + md.handlerCount(); i++) {
            if (i >= handlers.size() || handlers.get(i).startOffset() < start
                    || handlers.get(i).startOffset() >= end) {
                v.add("method " + hex(md.methodOffset()) + ": exception_handler_index " + md.handlerIndex()
                        + " does not select this method's handlers (6.14.4)");
                return;
            }
        }
    }

    private static void checkExport(Model m, List<String> v) {
        boolean flag = (m.cap.headerFlags() & 0x02) != 0;
        if (flag != m.cap.has(CapImage.TAG_EXPORT)) {
            v.add("Header ACC_EXPORT=" + flag + " but Export component present=" + !flag + " (6.4)");
        }
        List<ClassExport> exports = m.cap.exports();
        for (int i = 0; i < exports.size(); i++) {
            ClassExport ex = exports.get(i);
            Optional<TypeEntry> entry = m.classComponent.at(ex.classOffset());
            Optional<ClassDescriptor> cd = m.descriptor.byClassRef(ex.classOffset());
            if (entry.isEmpty() || cd.isEmpty() || cd.get().token() != i) {
                v.add("Export entry " + i + " does not point to the class with token " + i + " (6.13)");
                continue;
            }
            boolean sharedIface = entry.get() instanceof InterfaceEntry && entry.get().isShareable();
            if (m.cap.hasApplets() && !sharedIface) {
                v.add("Export entry " + i + ": application packages may export only shareable interfaces (6.13)");
            }
            if (entry.get() instanceof InterfaceEntry
                    && (!ex.staticFieldOffsets().isEmpty() || !ex.staticMethodOffsets().isEmpty())) {
                v.add("Export entry " + i + ": interfaces export no static members (6.13)");
            }
        }
        if (m.cap.has(CapImage.TAG_EXPORT) && exports.isEmpty()) {
            v.add("Export component with class_count 0 (6.13)");
        }
    }

    private static void checkClassShareable(Model m, List<String> v) {
        for (ClassEntry ce : m.classComponent.classes()) {
            boolean viaInterface = ce.interfaces().stream().anyMatch(ii -> m.isShareableRef(ii.interfaceRef()));
            int sup = ce.superClassRef();
            boolean internalSuper = sup != 0xFFFF && !isExternal(sup);
            boolean viaSuper = internalSuper && m.classComponent.at(sup).map(TypeEntry::isShareable).orElse(false);
            boolean expected = viaInterface || viaSuper;
            if (expected && !ce.isShareable()) {
                v.add("class " + hex(ce.offset()) + ": implements a shareable interface but ACC_SHAREABLE"
                        + " is not set (6.9.2.1)");
            }
            if (!expected && ce.isShareable() && (sup == 0xFFFF || internalSuper)) {
                v.add("class " + hex(ce.offset()) + ": ACC_SHAREABLE set but no shareable interface (6.9.2.1)");
            }
        }
    }
}
