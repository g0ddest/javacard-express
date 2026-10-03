package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rejects the remote classes and interfaces of Java Card RMI (JCVM 3.1 §2.2.6), which javacard-express
 * does not support (fail-closed).
 *
 * <p>§2.2.6.1: "A class is remote if it or any of its superclasses implements a remote interface"; a
 * remote interface is {@code java.rmi.Remote} or extends it. The Class component flags those types
 * ACC_REMOTE, which "must be one if and only if the class or interface satisfies the requirements defined
 * in 2.2.6.1" (§6.9.2.1 Table 6-11), and describes them with {@code interface_name_info} and
 * {@code remote_interface_info} (§6.9.2.6), structures of CAP format 2.2. This converter writes neither,
 * so the Java Card RE could not dispatch remote calls to the CAP file; the package is rejected instead.
 * Packages that merely call methods of imported remote interfaces are not affected.
 */
final class RemoteTypeRules {

    static final String REMOTE = "java/rmi/Remote";

    private RemoteTypeRules() {}

    /**
     * Checks that the package defines no remote class or interface.
     *
     * @param pkg       the package being converted
     * @param hierarchy the package's type hierarchy, including the imported types
     * @throws ConverterException listing every remote class and interface of the package
     */
    static void check(PackageInfo pkg, PackageHierarchy hierarchy) throws ConverterException {
        List<Violation> remote = new ArrayList<>();
        for (ClassInfo ci : pkg.classes()) {
            if (hierarchy.isSubtypeOf(ci.thisClass(), REMOTE)) {
                String kind = ci.isInterface() ? "remote interface" : "remote class";
                remote.add(new Violation(ci.thisClass(), kind, -1, "a Java Card RMI " + kind
                        + " (it is or implements java.rmi.Remote, JCVM 3.1 §2.2.6.1) needs ACC_REMOTE and the"
                        + " remote_interface_info of CAP format 2.2 (§6.9.2.1, §6.9.2.6), which javacard-express"
                        + " does not write", ci.sourceFile().orElse(null), -1));
            }
        }
        remote.sort(Comparator.comparing(Violation::className));
        if (!remote.isEmpty()) {
            throw new ConverterException("Java Card RMI is not supported by javacard-express (JCVM 3.1 §2.2.6)",
                    remote);
        }
    }
}
