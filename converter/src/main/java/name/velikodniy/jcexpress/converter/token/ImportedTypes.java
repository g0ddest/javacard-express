package name.velikodniy.jcexpress.converter.token;

import java.util.List;
import java.util.function.Function;

/**
 * What the converter knows about classes and interfaces of <em>imported</em> packages: the
 * information published in their export files (JCVM 3.1 §5.7 {@code class_info}).
 *
 * <p>Token assignment and the Class/Descriptor components need it whenever a type of the package
 * being converted extends or implements an imported type: inherited virtual method tokens
 * (§4.3.7.6), interface method tokens (§4.3.7.7), the superinterfaces of an imported interface
 * (§6.9.2.3 requires the whole interface hierarchy) and whether an imported interface is
 * shareable (§6.9.2.1 ACC_SHAREABLE).
 */
public interface ImportedTypes {

    /** Internal name of {@code javacard.framework.Shareable}. */
    String SHAREABLE = "javacard/framework/Shareable";

    /**
     * Returns the externally visible virtual methods of an imported class, including inherited
     * ones, or the methods of an imported interface including those of its superinterfaces,
     * each with its public token (JCVM 3.1 §5.7 {@code methods[]}, §5.9).
     *
     * @param internalName internal name of the imported class or interface
     * @return virtual/interface methods with tokens; empty if the type is unknown
     */
    List<TokenMap.MethodEntry> virtualMethods(String internalName);

    /**
     * Returns the public superinterfaces (direct and indirect) of an imported interface, or the
     * public interfaces implemented by an imported class (JCVM 3.1 §5.7 {@code interfaces[]}).
     *
     * @param internalName internal name of the imported class or interface
     * @return superinterfaces in export order; empty if none or unknown
     */
    List<String> superInterfaces(String internalName);

    /**
     * Returns whether an imported interface is shareable: it is
     * {@code javacard.framework.Shareable} or extends it (JCVM 3.1 §5.7 ACC_SHAREABLE).
     *
     * @param internalName internal name of the imported interface
     * @return {@code true} if the interface is shareable
     */
    boolean isShareable(String internalName);

    /**
     * Adapts a plain virtual-method lookup (the original {@link TokenAssigner} input) that knows
     * nothing about superinterfaces; only {@code javacard.framework.Shareable} itself is treated as
     * shareable.
     *
     * @param virtualMethods lookup of imported virtual methods
     * @return imported type information backed by the lookup
     */
    static ImportedTypes of(Function<String, List<TokenMap.MethodEntry>> virtualMethods) {
        return new ImportedTypes() {
            @Override
            public List<TokenMap.MethodEntry> virtualMethods(String internalName) {
                List<TokenMap.MethodEntry> methods = virtualMethods.apply(internalName);
                return methods == null ? List.of() : methods;
            }

            @Override
            public List<String> superInterfaces(String internalName) {
                return List.of();
            }

            @Override
            public boolean isShareable(String internalName) {
                return SHAREABLE.equals(internalName);
            }
        };
    }
}
