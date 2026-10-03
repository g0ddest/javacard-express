package name.velikodniy.jcexpress.converter.testutil;

import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFile.ClassExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.FieldExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.MethodExport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Test-only checker of the rules JCVM 3.1 Chapter 5 and §4.3.7 place on an export file, written
 * from the specification and independent of the production export file writer.
 *
 * <p>{@link #check} returns one message per violated rule, prefixed with the rule's section, so a
 * test can require an empty list for every export file the converter generates. Token numbering
 * rules (prefix {@code "4.3.7"}) are reported separately from the layout rules of Chapter 5 so
 * that a test can tell a token assignment defect from an export file writer defect.
 */
public final class ExportFileRules {

    private static final String OBJECT = "java/lang/Object";
    private static final String SHAREABLE = "javacard/framework/Shareable";
    /** §5.7 Table 5-3: PUBLIC, FINAL, INTERFACE, ABSTRACT, SHAREABLE, REMOTE. */
    private static final int CLASS_FLAGS = 0x1E11;
    /** §5.8 Table 5-4: PUBLIC, PROTECTED, STATIC, FINAL. */
    private static final int FIELD_FLAGS = 0x001D;
    /** §5.9 Table 5-5: PUBLIC, PROTECTED, STATIC, FINAL, ABSTRACT. */
    private static final int METHOD_FLAGS = 0x041D;
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PROTECTED = 0x0004;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_ABSTRACT = 0x0400;
    private static final Pattern CLASS_IN_DESCRIPTOR = Pattern.compile("L([^;]+);");

    private final ExportFile ef;
    private final List<String> violations = new ArrayList<>();

    private ExportFileRules(ExportFile ef) {
        this.ef = ef;
    }

    /**
     * Checks an export file.
     *
     * @param ef                  the parsed export file
     * @param library             whether the package declares no applets (ACC_LIBRARY, §5.6.1)
     * @param expectedFormatMinor export file format minor version expected for the target
     * @return the violated rules, empty if the file conforms
     */
    public static List<String> check(ExportFile ef, boolean library, int expectedFormatMinor) {
        ExportFileRules rules = new ExportFileRules(ef);
        rules.checkPackage(library, expectedFormatMinor);
        for (ClassExport c : ef.classes()) {
            rules.checkClass(c, library);
            rules.checkFields(c);
            rules.checkMethods(c);
        }
        if (library) rules.checkDescriptorTypes();
        rules.checkReferencedPackages();
        return List.copyOf(rules.violations);
    }

    private void fail(String section, String message) {
        violations.add(section + " " + message);
    }

    // ── §5.5, §5.6.1, §4.3.7.2 ──

    private void checkPackage(boolean library, int expectedFormatMinor) {
        if (ef.formatMajor() != 2 || ef.formatMinor() != expectedFormatMinor) {
            fail("5.5", "export file format " + ef.formatMajor() + "." + ef.formatMinor() + ", expected 2."
                    + expectedFormatMinor);
        }
        if (ef.packageFlags() != (library ? ExportFile.ACC_LIBRARY : 0)) {
            fail("5.6.1", "package flags 0x" + Integer.toHexString(ef.packageFlags()) + " for a "
                    + (library ? "library" : "package with applets"));
        }
        List<Integer> tokens = ef.classes().stream().map(ClassExport::token).toList();
        if (new HashSet<>(tokens).size() != tokens.size() || tokens.stream().anyMatch(t -> t > 254)) {
            fail("4.3.7.2", "class tokens " + tokens + " are not distinct values 0..254");
        } else if (library && !isRange(tokens)) {
            fail("4.3.7.2", "class tokens " + sorted(tokens) + " of a library are not 0.." + (tokens.size() - 1));
        }
    }

    // ── §5.7 ──

    private void checkClass(ClassExport c, boolean library) {
        int flags = c.accessFlags();
        if ((flags & ACC_PUBLIC) == 0 || (flags & ~CLASS_FLAGS) != 0) {
            fail("5.7", c.name() + ": class flags 0x" + Integer.toHexString(flags) + " (ACC_PUBLIC required,"
                    + " reserved bits must be zero)");
        }
        if (c.isInterface() && ((flags & ACC_ABSTRACT) == 0 || (flags & ACC_FINAL) != 0)) {
            fail("5.7", c.name() + ": interface flags 0x" + Integer.toHexString(flags) + " (abstract, not final)");
        }
        if (!library && !(c.isInterface() && c.isShareable())) {
            fail("5.5", c.name() + ": a package with applets exports only shareable interfaces");
        }
        boolean shareable = c.name().equals(SHAREABLE) || c.interfaces().contains(SHAREABLE);
        if (c.isShareable() != shareable) {
            fail("5.7", c.name() + ": ACC_SHAREABLE " + c.isShareable() + " but interfaces " + c.interfaces());
        }
        checkSupers(c);
    }

    private void checkSupers(ClassExport c) {
        if (c.isInterface() && !c.supers().equals(List.of(OBJECT))) {
            fail("5.7", c.name() + ": the supers of an interface are [java/lang/Object], not " + c.supers());
        }
        if (!c.isInterface() && !c.name().equals(OBJECT) && !c.supers().contains(OBJECT)) {
            fail("5.7", c.name() + ": supers " + c.supers() + " lack java/lang/Object");
        }
        if (c.supers().contains(c.name()) || new HashSet<>(c.supers()).size() != c.supers().size()
                || new HashSet<>(c.interfaces()).size() != c.interfaces().size()) {
            fail("5.7", c.name() + ": supers " + c.supers() + " / interfaces " + c.interfaces()
                    + " contain duplicates or the class itself");
        }
    }

    // ── §5.8, §5.10.1, §4.3.7.3, §4.3.7.5 ──

    private void checkFields(ClassExport c) {
        Set<String> names = new HashSet<>();
        for (FieldExport f : c.fields()) {
            String where = c.name() + "." + f.name();
            if (!names.add(f.name())) fail("5.7", where + ": field listed twice");
            checkMemberFlags(where, f.accessFlags(), FIELD_FLAGS, "5.8");
            boolean constant = isConstant(f);
            if (constant != (f.constantValue() != null)) {
                fail("5.8", where + ": a ConstantValue attribute is required exactly for static final primitive"
                        + " fields (5.10.1)");
            }
            if (constant && f.token() != ExportFile.CONSTANT_FIELD_TOKEN) {
                fail("5.8", where + ": compile-time constant with token " + f.token() + ", expected 0xFF");
            }
        }
        List<Integer> statics = c.fields().stream().filter(f -> f.isStatic() && !isConstant(f))
                .map(FieldExport::token).toList();
        if (!isRange(statics)) {
            fail("4.3.7.3", c.name() + ": static field tokens " + sorted(statics) + " are not 0.." + (statics.size() - 1));
        }
        checkInstanceFieldTokens(c);
    }

    /** §5.8: a static final field of a primitive type is a compile-time constant. */
    private static boolean isConstant(FieldExport f) {
        return f.isStatic() && (f.accessFlags() & ACC_FINAL) != 0 && f.descriptor().length() == 1;
    }

    private void checkInstanceFieldTokens(ClassExport c) {
        List<FieldExport> fields = c.fields().stream().filter(f -> !f.isStatic())
                .sorted(Comparator.comparingInt(FieldExport::token)).toList();
        int expected = 0;
        boolean seenReference = false;
        for (FieldExport f : fields) {
            boolean reference = f.descriptor().length() > 1;
            if (f.token() != expected || (seenReference && !reference)) {
                fail("4.3.7.5", c.name() + ": instance field tokens " + fields.stream().map(x -> x.name() + "="
                        + x.token()).toList() + " are not consecutive from 0 (int +2), primitive before reference");
                return;
            }
            seenReference |= reference;
            expected += "I".equals(f.descriptor()) ? 2 : 1;
        }
    }

    // ── §5.9, §4.3.7.4, §4.3.7.6, §4.3.7.7 ──

    private void checkMethods(ClassExport c) {
        Set<String> keys = new HashSet<>();
        for (MethodExport m : c.methods()) {
            String where = c.name() + "." + m.name() + m.descriptor();
            if (!keys.add(m.name() + m.descriptor())) fail("5.7", where + ": method listed twice");
            checkMemberFlags(where, m.accessFlags(), METHOD_FLAGS, "5.9");
            if ("<clinit>".equals(m.name()) || ("<init>".equals(m.name()) && (m.accessFlags() & ACC_STATIC) != 0)) {
                fail("5.9", where + ": no static initializer is exported and a constructor is not ACC_STATIC");
            }
            if (c.isInterface() && (m.accessFlags() & (ACC_PUBLIC | ACC_ABSTRACT | ACC_STATIC))
                    != (ACC_PUBLIC | ACC_ABSTRACT)) {
                fail("5.9", where + ": an interface method is public abstract");
            }
        }
        List<Integer> statics = c.methods().stream().filter(MethodExport::isStaticOrConstructor)
                .map(MethodExport::token).toList();
        if (!isRange(statics)) {
            fail("4.3.7.4", c.name() + ": static method tokens " + sorted(statics) + " are not 0.." + (statics.size() - 1));
        }
        List<Integer> virtuals = c.methods().stream().filter(m -> !m.isStaticOrConstructor())
                .map(MethodExport::token).toList();
        if (!isRange(virtuals)) {
            fail(c.isInterface() ? "4.3.7.7" : "4.3.7.6", c.name() + ": " + (c.isInterface() ? "interface" : "virtual")
                    + " method tokens " + sorted(virtuals) + " are not 0.." + (virtuals.size() - 1));
        }
        if (ef.formatMinor() >= 3 && !c.isInterface() && c.cap22InheritableCount() > virtuals.size()) {
            fail("5.7", c.name() + ": CAP22_inheritable_public_method_token_count " + c.cap22InheritableCount()
                    + " exceeds the " + virtuals.size() + " public virtual methods");
        }
    }

    private void checkMemberFlags(String where, int flags, int allowed, String section) {
        boolean oneVisibility = ((flags & ACC_PUBLIC) != 0) != ((flags & ACC_PROTECTED) != 0);
        if (!oneVisibility || (flags & ~allowed) != 0) {
            fail(section, where + ": flags 0x" + Integer.toHexString(flags) + " (exactly one of ACC_PUBLIC and"
                    + " ACC_PROTECTED, reserved bits zero)");
        }
    }

    // ── §5.8, §5.9: descriptor types; §5.5 referenced_packages ──

    private void checkDescriptorTypes() {
        Set<String> exported = new HashSet<>();
        ef.classes().forEach(c -> exported.add(c.name()));
        for (ClassExport c : ef.classes()) {
            for (String type : descriptorTypes(c)) {
                if (packageOf(type).equals(ef.packageName()) && !exported.contains(type)) {
                    fail("5.9", c.name() + ": a descriptor names " + type + ", which is not a public class");
                }
            }
        }
    }

    private void checkReferencedPackages() {
        Set<String> expected = new LinkedHashSet<>();
        if (ef.formatMinor() >= 3) {
            for (ClassExport c : ef.classes()) {
                c.supers().forEach(s -> expected.add(packageOf(s)));
                c.interfaces().forEach(i -> expected.add(packageOf(i)));
                descriptorTypes(c).forEach(t -> expected.add(packageOf(t)));
            }
            expected.remove(ef.packageName());
        }
        Set<String> actual = new HashSet<>();
        ef.referencedPackages().forEach(p -> actual.add(p.name()));
        if (!actual.equals(expected) || actual.size() != ef.referencedPackages().size()) {
            fail("5.5", "referenced_packages " + actual + ", expected " + expected);
        }
    }

    private static Set<String> descriptorTypes(ClassExport c) {
        Set<String> types = new LinkedHashSet<>();
        List<String> descriptors = new ArrayList<>();
        c.fields().forEach(f -> descriptors.add(f.descriptor()));
        c.methods().forEach(m -> descriptors.add(m.descriptor()));
        for (String d : descriptors) {
            Matcher m = CLASS_IN_DESCRIPTOR.matcher(d);
            while (m.find()) types.add(m.group(1));
        }
        return types;
    }

    private static String packageOf(String className) {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash);
    }

    private static boolean isRange(List<Integer> tokens) {
        return sorted(tokens).equals(IntStream.range(0, tokens.size()).boxed().toList());
    }

    private static List<Integer> sorted(List<Integer> tokens) {
        return tokens.stream().sorted().toList();
    }
}
