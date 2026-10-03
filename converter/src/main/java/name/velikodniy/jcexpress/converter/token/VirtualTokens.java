package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Virtual method tokens (JCVM 3.1 §4.3.7.6) and interface method tokens (§4.3.7.7).
 */
final class VirtualTokens {

    /** High bit of a private (package-visible) virtual method token (§4.3.7.6). */
    static final int PRIVATE_TOKEN = 0x80;
    /** Largest virtual or interface method token value (JCVM 3.1 Table 4-2). */
    static final int MAX_METHOD_TOKEN = 127;

    private VirtualTokens() {}

    static boolean isPrivateToken(int token) {
        return (token & PRIVATE_TOKEN) != 0;
    }

    /** A method that receives a virtual method token: an instance method that is not private. */
    static boolean isVirtual(MethodInfo mi) {
        return !mi.isStatic() && !mi.isConstructor() && !mi.isStaticInitializer() && !mi.isPrivate();
    }

    /**
     * Assigns the virtual method tokens of a class.
     *
     * <p>Public and private tokens come from different namespaces. A public or protected method
     * that overrides an inherited public token keeps it, otherwise it gets the next public token.
     * A package-visible method overrides only a package-visible method of an in-package
     * superclass (private token, high bit set); new ones are numbered after the superclass's
     * highest private token, or from zero when the superclass is in another package (the
     * inherited list then holds public tokens only). A public or protected method may not
     * override a package-visible one (§2.2.1.1).
     *
     * @param ci         the class
     * @param inherited  virtual methods of the superclass hierarchy, with their tokens
     * @param violations collects rule violations
     * @return inherited methods followed by the methods this class introduces
     */
    static List<TokenMap.MethodEntry> forClass(ClassInfo ci, List<TokenMap.MethodEntry> inherited,
                                               List<Violation> violations) {
        List<TokenMap.MethodEntry> result = new ArrayList<>(inherited);
        int nextPublic = maxToken(inherited, false) + 1;
        int nextPrivate = maxToken(inherited, true) + 1;
        for (MethodInfo mi : ci.methods()) {
            if (!isVirtual(mi)) {
                continue;
            }
            boolean packageVisible = !MemberTokens.isExternallyVisible(mi.accessFlags());
            if (find(inherited, mi, packageVisible).isPresent()) {
                continue; // override: keeps the inherited token
            }
            if (!packageVisible && find(inherited, mi, true).isPresent()) {
                violations.add(new Violation(ci.thisClass(), mi.name() + mi.descriptor(),
                        "overrides a package-visible method and makes it public or protected,"
                                + " which the Java Card language subset forbids (JCVM 3.1 §2.2.1.1)"));
                continue;
            }
            int token = packageVisible ? PRIVATE_TOKEN | nextPrivate++ : nextPublic++;
            result.add(new TokenMap.MethodEntry(mi.name(), mi.descriptor(), token));
        }
        if (nextPublic - 1 > MAX_METHOD_TOKEN || nextPrivate - 1 > MAX_METHOD_TOKEN) {
            violations.add(new Violation(ci.thisClass(), "virtual methods",
                    "more than 128 public or 128 package-visible virtual methods in the hierarchy"
                            + " (JCVM 3.1 §4.3.7.6)"));
        }
        return List.copyOf(result);
    }

    /**
     * Assigns the method tokens of an interface: the methods inherited from its superinterfaces
     * (each superinterface in declaration order, in its own token order) followed by the
     * methods it declares, numbered consecutively from zero (§4.3.7.7). Interface methods must
     * be abstract: a Java Card interface cannot contain code.
     *
     * @param ci              the interface
     * @param superInterfaces method lists of the direct superinterfaces, in declaration order
     * @param violations      collects rule violations
     * @return interface methods with tokens 0..n-1
     */
    static List<TokenMap.MethodEntry> forInterface(ClassInfo ci, List<List<TokenMap.MethodEntry>> superInterfaces,
                                                   List<Violation> violations) {
        List<TokenMap.MethodEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<TokenMap.MethodEntry> methods : superInterfaces) {
            methods.stream().sorted(Comparator.comparingInt(TokenMap.MethodEntry::token))
                    .filter(m -> seen.add(m.name() + m.descriptor()))
                    .forEach(m -> result.add(new TokenMap.MethodEntry(m.name(), m.descriptor(), result.size())));
        }
        for (MethodInfo mi : ci.methods()) {
            if (mi.isStaticInitializer()) {
                continue; // static field initialisation, handled with the Static Field component
            }
            if (!mi.isAbstract() || mi.isStatic() || mi.isPrivate()) {
                violations.add(new Violation(ci.thisClass(), mi.name() + mi.descriptor(),
                        "interface methods must be abstract; default, static and private interface"
                                + " methods are not supported by Java Card (JCVM 3.1 §6.10, §4.3.7.7)"));
            } else if (seen.add(mi.name() + mi.descriptor())) {
                result.add(new TokenMap.MethodEntry(mi.name(), mi.descriptor(), result.size()));
            }
        }
        if (result.size() > MAX_METHOD_TOKEN + 1) {
            violations.add(new Violation(ci.thisClass(), "interface methods",
                    "more than 128 interface methods (JCVM 3.1 §4.3.7.7)"));
        }
        return List.copyOf(result);
    }

    /**
     * Checks that every method of the directly implemented interfaces (their method lists include
     * the superinterface methods) is a public virtual method of the class, declared or inherited.
     *
     * <p>The class's {@code implemented_interface_info} maps each interface method token to a
     * virtual method token of the class (JCVM 3.1 §6.9.2.5). For an abstract class that leaves an
     * interface method to its subclasses javac emits no declaration; the converter declares such
     * methods before token assignment ({@link TokenAssigner#undeclaredInterfaceMethods}), so a
     * method reported here is missing from a concrete class (class files compiled separately).
     *
     * @param ci               the class
     * @param virtuals         the class's virtual methods (inherited and declared) with tokens
     * @param interfaceMethods methods of each direct interface, in the order of {@code ci.interfaces()}
     * @param violations       collects the interface methods without an implementation
     */
    static void requireInterfaceMethods(ClassInfo ci, List<TokenMap.MethodEntry> virtuals,
                                        List<List<TokenMap.MethodEntry>> interfaceMethods,
                                        List<Violation> violations) {
        Set<String> reported = new HashSet<>();
        for (int i = 0; i < interfaceMethods.size(); i++) {
            String iface = ci.interfaces().get(i);
            for (TokenMap.MethodEntry m : interfaceMethods.get(i)) {
                if (!hasPublicVirtual(virtuals, m) && reported.add(m.name() + m.descriptor())) {
                    violations.add(new Violation(ci.thisClass(), m.name() + m.descriptor(), "implements " + iface
                            + " but neither declares nor inherits this method; the Class component maps every"
                            + " interface method to a virtual method of the class (JCVM 3.1 §6.9.2.5)"
                            + (ci.isAbstract() ? ": declare it 'public abstract' (the Converter does so)"
                            : ": the class files are inconsistent, recompile the package's classes together")));
                }
            }
        }
    }

    /**
     * Returns the methods of the directly implemented interfaces that are no public virtual method
     * of the class, each once, in interface and token order (see {@link #requireInterfaceMethods}).
     *
     * @param virtuals         the class's virtual methods (inherited and declared) with tokens
     * @param interfaceMethods methods of each direct interface, including superinterface methods
     * @return the interface methods the class neither declares nor inherits
     */
    static List<TokenMap.MethodEntry> unimplemented(List<TokenMap.MethodEntry> virtuals,
                                                    List<List<TokenMap.MethodEntry>> interfaceMethods) {
        Set<String> seen = new HashSet<>();
        List<TokenMap.MethodEntry> result = new ArrayList<>();
        for (List<TokenMap.MethodEntry> methods : interfaceMethods) {
            methods.stream().sorted(Comparator.comparingInt(TokenMap.MethodEntry::token))
                    .filter(m -> !hasPublicVirtual(virtuals, m) && seen.add(m.name() + m.descriptor()))
                    .forEach(result::add);
        }
        return result;
    }

    private static boolean hasPublicVirtual(List<TokenMap.MethodEntry> virtuals, TokenMap.MethodEntry m) {
        return virtuals.stream().anyMatch(v -> !isPrivateToken(v.token())
                && v.name().equals(m.name()) && v.descriptor().equals(m.descriptor()));
    }

    private static int maxToken(List<TokenMap.MethodEntry> methods, boolean privateNamespace) {
        return methods.stream().mapToInt(TokenMap.MethodEntry::token)
                .filter(t -> isPrivateToken(t) == privateNamespace)
                .map(t -> t & ~PRIVATE_TOKEN).max().orElse(-1);
    }

    private static Optional<TokenMap.MethodEntry> find(List<TokenMap.MethodEntry> methods, MethodInfo mi,
                                                       boolean privateNamespace) {
        return methods.stream()
                .filter(m -> isPrivateToken(m.token()) == privateNamespace)
                .filter(m -> m.name().equals(mi.name()) && m.descriptor().equals(mi.descriptor()))
                .findFirst();
    }
}
