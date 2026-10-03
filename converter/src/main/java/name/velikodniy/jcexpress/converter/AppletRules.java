package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.cap.AppletComponent;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates the applets registered for a CAP file and locates their install methods
 * (JCVM 3.1 §6.6, fail-closed).
 *
 * <p>An applet is a non-abstract class of the package that extends
 * {@code javacard.framework.Applet} directly or indirectly; its {@code install_method_offset}
 * must designate the {@code static install(byte[], short, byte)} method, defined in a class that
 * extends {@code Applet} (JCVM 3.1 §6.6). The Runtime Environment calls it as
 * {@code public static void install(byte[] bArray, short bOffset, byte bLength)}. A misspelled
 * class name or a class without such a method is an error: it must never yield an Applet
 * component that points at an arbitrary method.
 *
 * <p>The applets of a CAP file are either all multiselectable or none is (JCVM 3.1 §2.2.5): an applet
 * is multiselectable when it implements {@code javacard.framework.MultiSelectable}, directly, through
 * a superclass of the package or of an imported package (export entry {@code supers[]} and
 * {@code interfaces[]}, §5.7), or through a superinterface.
 */
final class AppletRules {

    static final String APPLET = "javacard/framework/Applet";
    static final String INSTALL = "install";
    static final String INSTALL_DESCRIPTOR = "([BSB)V";
    static final String MULTI_SELECTABLE = "javacard/framework/MultiSelectable";

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;

    /**
     * An applet of the CAP file.
     *
     * @param className        applet class (dot notation, as registered)
     * @param aid              applet AID
     * @param installMethodKey {@code "<internal class>:install:([BSB)V"} of the method to call
     */
    record AppletDefinition(String className, byte[] aid, String installMethodKey) {}

    private final PackageHierarchy hierarchy;
    private final String packageName;
    private final List<String> problems = new ArrayList<>();

    private AppletRules(PackageInfo pkg, List<ImportedPackage> imports) {
        this.hierarchy = new PackageHierarchy(pkg, imports);
        this.packageName = pkg.packageName();
    }

    /**
     * Checks every registered applet.
     *
     * @param applets applet class names (dot notation) and AIDs, in registration order
     * @param pkg     the package being converted
     * @param imports candidate imports, to follow superclasses defined in other packages
     * @return the applet definitions in registration order
     * @throws ConverterException listing every invalid applet
     */
    static List<AppletDefinition> check(Map<String, byte[]> applets, PackageInfo pkg,
                                        List<ImportedPackage> imports) throws ConverterException {
        AppletRules rules = new AppletRules(pkg, imports);
        List<AppletDefinition> result = new ArrayList<>();
        applets.forEach((cls, aid) -> rules.check(cls).ifPresent(key ->
                result.add(new AppletDefinition(cls, aid.clone(), key))));
        rules.checkMultiselection(result);
        if (!rules.problems.isEmpty()) {
            throw new ConverterException("Invalid applet definitions:\n  - "
                    + String.join("\n  - ", rules.problems));
        }
        return List.copyOf(result);
    }

    /**
     * Builds the Applet component entries (JCVM 3.1 §6.6).
     *
     * @param applets        validated applets
     * @param methodIndexMap method key to index in the Method component
     * @param methodOffsets  offsets of the methods in the Method component info
     * @return entries in registration order
     */
    static List<AppletComponent.AppletEntry> entries(List<AppletDefinition> applets,
                                                     Map<String, Integer> methodIndexMap, int[] methodOffsets) {
        List<AppletComponent.AppletEntry> entries = new ArrayList<>();
        for (AppletDefinition a : applets) {
            Integer index = methodIndexMap.get(a.installMethodKey());
            if (index == null) {
                throw new IllegalStateException("install method was not translated: " + a.installMethodKey());
            }
            entries.add(new AppletComponent.AppletEntry(a.aid(), methodOffsets[index]));
        }
        return entries;
    }

    private Optional<String> check(String className) {
        String internal = className.replace('.', '/');
        ClassInfo ci = hierarchy.own(internal);
        if (ci == null) {
            problems.add("applet class " + className + " is not a class of package " + packageName);
        } else if (ci.isInterface() || ci.isAbstract()) {
            problems.add("applet class " + className + " is " + (ci.isInterface() ? "an interface" : "abstract")
                    + "; an applet is a non-abstract subclass of javacard.framework.Applet (JCVM 3.1 §6.6)");
        } else if (!extendsApplet(ci.superClass())) {
            problems.add("applet class " + className
                    + " does not extend javacard.framework.Applet (JCVM 3.1 §6.6)");
        } else {
            return findInstall(className, ci);
        }
        return Optional.empty();
    }

    /** JCVM 3.1 §2.2.5: "All applets within a CAP file shall be multiselectable, or none shall be." */
    private void checkMultiselection(List<AppletDefinition> applets) {
        List<String> multi = new ArrayList<>();
        List<String> single = new ArrayList<>();
        for (AppletDefinition a : applets) {
            boolean multiselectable = hierarchy.isSubtypeOf(a.className().replace('.', '/'), MULTI_SELECTABLE);
            (multiselectable ? multi : single).add(a.className());
        }
        if (!multi.isEmpty() && !single.isEmpty()) {
            problems.add("all applets of a CAP file must be multiselectable (implement"
                    + " javacard.framework.MultiSelectable), or none (JCVM 3.1 §2.2.5); multiselectable: "
                    + String.join(", ", multi) + "; not multiselectable: " + String.join(", ", single));
        }
    }

    private boolean extendsApplet(String superName) {
        String s = superName;
        while (s != null && hierarchy.own(s) != null) {
            s = hierarchy.own(s).superClass();
        }
        if (s == null) return false;
        if (APPLET.equals(s)) return true;
        Optional<ExportFile.ClassExport> external = hierarchy.exported(s);
        return external.isPresent() && external.get().supers().contains(APPLET);
    }

    /** Finds install([BSB)V in the class or the first own superclass declaring it. */
    private Optional<String> findInstall(String className, ClassInfo applet) {
        for (ClassInfo ci = applet; ci != null; ci = hierarchy.own(ci.superClass())) {
            for (MethodInfo m : ci.methods()) {
                if (!INSTALL.equals(m.name()) || !INSTALL_DESCRIPTOR.equals(m.descriptor())) continue;
                if ((m.accessFlags() & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)) {
                    return Optional.of(ci.thisClass() + ":" + INSTALL + ":" + INSTALL_DESCRIPTOR);
                }
                problems.add("applet class " + className + ": install(byte[], short, byte) in "
                        + ci.thisClass().replace('/', '.') + " must be public static (JCVM 3.1 §6.6)");
                return Optional.empty();
            }
        }
        problems.add("applet class " + className + " has no public static void install(byte[], short, byte)"
                + " method (JCVM 3.1 §6.6)");
        return Optional.empty();
    }
}
