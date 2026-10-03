package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * {@link ImportedTypes} backed by the export files of the imported packages
 * (JCVM 3.1 Chapter 5).
 */
public final class ExportedTypes implements ImportedTypes {

    /** Export file / class file flag ACC_PUBLIC. */
    private static final int ACC_PUBLIC = 0x0001;
    /** Export file / class file flag ACC_PROTECTED. */
    private static final int ACC_PROTECTED = 0x0004;
    /** Export file / class file flag ACC_STATIC. */
    private static final int ACC_STATIC = 0x0008;
    /** Export file class flag ACC_SHAREABLE (JCVM 3.1 §5.7 Table 5-3). */
    private static final int ACC_SHAREABLE = 0x0800;

    private final List<ImportedPackage> imports;

    /**
     * Creates the lookup.
     *
     * @param imports imported packages with their export files
     */
    public ExportedTypes(List<ImportedPackage> imports) {
        this.imports = List.copyOf(imports);
    }

    /**
     * Finds the export entry of an imported class or interface.
     *
     * @param internalName internal name (e.g. {@code javacard/framework/Applet})
     * @return the export entry, or empty if no imported package exports it
     */
    public Optional<ExportFile.ClassExport> find(String internalName) {
        int slash = internalName.lastIndexOf('/');
        String pkg = slash < 0 ? "" : internalName.substring(0, slash);
        String simple = internalName.substring(slash + 1);
        for (ImportedPackage imp : imports) {
            String impPkg = imp.exportFile().packageName().replace('.', '/');
            if (impPkg.equals(pkg)) {
                try {
                    return Optional.of(imp.exportFile().findClass(simple));
                } catch (NoSuchElementException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only public and protected instance methods are returned: package-visible methods of
     * another package can neither be called nor overridden (JCVM 3.1 §4.3.7.6).
     */
    @Override
    public List<TokenMap.MethodEntry> virtualMethods(String internalName) {
        return find(internalName).map(ce -> ce.methods().stream()
                        .filter(m -> (m.accessFlags() & ACC_STATIC) == 0)
                        .filter(m -> (m.accessFlags() & (ACC_PUBLIC | ACC_PROTECTED)) != 0)
                        .filter(m -> !m.name().startsWith("<"))
                        .map(m -> new TokenMap.MethodEntry(m.name(), m.descriptor(), m.token()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Taken from the {@code interfaces[]} array of the class's export entry, which lists every
     * public superinterface, direct and indirect (JCVM 3.1 §5.7).
     */
    @Override
    public List<String> superInterfaces(String internalName) {
        return find(internalName).map(ExportFile.ClassExport::interfaces).orElse(List.of());
    }

    @Override
    public boolean isShareable(String internalName) {
        return SHAREABLE.equals(internalName)
                || find(internalName).map(ce -> (ce.accessFlags() & ACC_SHAREABLE) != 0).orElse(false);
    }
}
