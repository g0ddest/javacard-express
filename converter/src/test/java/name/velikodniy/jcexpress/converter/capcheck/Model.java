package name.velikodniy.jcexpress.converter.capcheck;

import name.velikodniy.jcexpress.converter.capcheck.CapImage.CpEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.InterfaceEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.TypeEntry;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsed components shared by the invariant checks.
 */
final class Model {

    /** AID of javacard.framework (JC API, public). */
    private static final byte[] FRAMEWORK_AID = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x01};
    /** Class token of javacard.framework.Shareable in the javacard.framework export file. */
    private static final int SHAREABLE_TOKEN = 2;

    final CapImage cap;
    final ClassComponentView classComponent;
    final DescriptorView descriptor;
    final List<CpEntry> cp;
    private final Map<Integer, MethodDescriptor> methodsByOffset = new HashMap<>();
    private final Map<Integer, ClassDescriptor> ownerByMethodOffset = new HashMap<>();
    private final int shareableRef;

    Model(CapImage cap) {
        this.cap = cap;
        this.classComponent = cap.classComponent();
        this.descriptor = cap.descriptor();
        this.cp = cap.constantPool();
        for (ClassDescriptor cd : descriptor.classes()) {
            if (cd.isInterface()) {
                continue;
            }
            for (MethodDescriptor md : cd.methods()) {
                methodsByOffset.put(md.methodOffset(), md);
                ownerByMethodOffset.put(md.methodOffset(), cd);
            }
        }
        this.shareableRef = frameworkShareableRef(cap.imports());
    }

    private static int frameworkShareableRef(List<CapImage.ImportedPackage> imports) {
        for (int i = 0; i < imports.size(); i++) {
            if (Arrays.equals(imports.get(i).aid(), FRAMEWORK_AID)) {
                return ((0x80 | i) << 8) | SHAREABLE_TOKEN;
            }
        }
        return -1;
    }

    Optional<ClassDescriptor> descriptorOf(TypeEntry entry) {
        return descriptor.byClassRef(entry.offset());
    }

    Optional<MethodDescriptor> methodAt(int offset) {
        return Optional.ofNullable(methodsByOffset.get(offset));
    }

    Optional<ClassDescriptor> ownerOfMethod(int offset) {
        return Optional.ofNullable(ownerByMethodOffset.get(offset));
    }

    Optional<InterfaceEntry> interfaceAt(int classRef) {
        if (CapInvariants.isExternal(classRef)) {
            return Optional.empty();
        }
        return classComponent.at(classRef).filter(InterfaceEntry.class::isInstance)
                .map(InterfaceEntry.class::cast);
    }

    /**
     * Returns whether a class_ref denotes a shareable interface that can be recognised from the
     * CAP file alone: javacard.framework.Shareable itself or an internal interface with
     * ACC_SHAREABLE.
     */
    boolean isShareableRef(int classRef) {
        if (CapInvariants.isExternal(classRef)) {
            return classRef == shareableRef;
        }
        return interfaceAt(classRef).map(TypeEntry::isShareable).orElse(false);
    }
}
