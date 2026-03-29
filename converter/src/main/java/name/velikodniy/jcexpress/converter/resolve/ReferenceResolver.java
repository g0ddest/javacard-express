package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Resolves symbolic class/method/field references from JVM bytecode
 * to numeric JCVM constant pool entries.
 *
 * <p>This is the core component of <strong>Stage 4: Reference Resolution</strong> in the
 * converter pipeline. The JVM constant pool uses fully-qualified string names for all
 * references (e.g. {@code "javacard/framework/Applet"}), whereas the JCVM constant pool
 * uses compact numeric tokens and byte offsets as specified in JCVM 3.0.5 spec sections
 * 6.8 (Constant Pool Component) and 6.9 (Reference Location Component).
 *
 * <h2>Reference Encoding</h2>
 * <ul>
 *   <li><strong>Internal references</strong> (within the current package): encoded using
 *       byte offsets into the ClassComponent, MethodComponent, or StaticFieldComponent.
 *       These offsets are not known at translation time, so placeholder values are inserted
 *       and later patched via {@link #patchInternalRefs}.</li>
 *   <li><strong>External references</strong> (from imported packages): encoded using a
 *       triple of (package_token | 0x80, class_token, member_token), where tokens are
 *       looked up from the imported package's {@link name.velikodniy.jcexpress.converter.token.ExportFile}.</li>
 * </ul>
 *
 * <h2>Deferred Patching</h2>
 * <p>Internal references require byte offsets that are only available after component
 * generation. This class uses a deferred patching mechanism: during translation, unique
 * placeholder values (starting at {@code 0x7F00}) are written into the constant pool.
 * After the ClassComponent, MethodComponent, and StaticFieldComponent have been generated,
 * {@link #patchInternalRefs} replaces all placeholders with their real offsets.
 *
 * <h2>Import Finalization</h2>
 * <p>The resolver tracks which imported packages are actually referenced during translation.
 * After all bytecode has been processed, {@link #finalizeImports(JavaCardVersion) finalizeImports()} prunes unreferenced
 * packages and reassigns contiguous package tokens, then remaps all external CP entries
 * to use the new token values.
 *
 * @see CpReference
 * @see ImportedPackage
 * @see BuiltinExports
 * @see name.velikodniy.jcexpress.converter.translate.JcvmConstantPool
 */
public final class ReferenceResolver {

    /** Token assignments for the current (being-converted) package. */
    private final TokenMap tokenMap;

    /** Imported packages with their export files; mutable (pruned by {@link #finalizeImports()}). */
    private final List<ImportedPackage> imports;

    /** The JCVM constant pool being built during translation. */
    private final JcvmConstantPool cp;

    /** Current package name in internal (slash-separated) format, e.g. {@code "com/example/myapplet"}. */
    private final String currentPackage;

    /** Maps each class internal name to its superclass internal name (for inheritance chain walking). */
    private final Map<String, String> superclassMap;

    /**
     * Locally declared virtual methods per class, keyed by {@code "name:descriptor"}.
     * Used to distinguish inherited vs overridden methods: if a method is not in this set
     * for a given class, it is inherited and the resolver walks up the superclass chain
     * to find the declaring class (which may be external).
     */
    private final Map<String, Set<String>> declaredVirtualMethods;
    private final Set<String> privateInstanceMethods;

    /**
     * Deferred patches for internal references. Internal refs use placeholder values during
     * translation because real byte offsets are not yet known. After component generation,
     * {@link #patchInternalRefs} replaces each placeholder with the actual offset.
     */
    private final List<PendingPatch> pendingPatches = new ArrayList<>();

    /** Next unique placeholder value for deferred internal references. Starts at {@code 0x7F00}. */
    private int nextPlaceholder = 0x7F00;

    /*
     * Caches for internal reference deduplication. Each cache maps a unique key
     * (class name, or class:member:descriptor) to its CP index. This prevents
     * creating duplicate CP entries when the same internal reference appears in
     * multiple methods or bytecode locations.
     */
    private final Map<String, Integer> internalClassRefCache = new HashMap<>();
    private final Map<String, Integer> internalStaticMethodRefCache = new HashMap<>();
    private final Map<String, Integer> internalStaticFieldRefCache = new HashMap<>();
    private final Map<String, Integer> internalInstanceFieldRefCache = new HashMap<>();
    private final Map<String, Integer> internalVirtualMethodRefCache = new HashMap<>();

    /**
     * Tracks which imported package tokens were actually referenced during bytecode
     * translation. Used by {@link #finalizeImports()} to prune unreferenced packages.
     */
    private final Set<Integer> referencedPackages = new HashSet<>();

    /**
     * Tracks the order in which imported packages are first referenced during
     * bytecode translation. Oracle's converter assigns import tokens by encounter
     * order, so we preserve this ordering in {@link #finalizeImports()}.
     */
    private final List<Integer> packageEncounterOrder = new ArrayList<>();

    /**
     * Tracks per-class package encounter order. When classes are processed in a
     * different order for CP building vs Method layout, the import encounter order
     * must still follow token (layout) order. This map records each class's
     * package encounters independently so we can recompute the global order.
     */
    private final Map<String, List<Integer>> perClassPackageEncounters = new LinkedHashMap<>();

    /** The class currently being translated (set by {@link #setCurrentClass}). */
    private String currentTranslatingClass;

    /**
     * Optional callback invoked when an ISM entry for an internal {@code <init>} is created.
     * Oracle's converter processes referenced constructors depth-first: when translating
     * class A's constructor and encountering {@code invokespecial B.<init>}, it immediately
     * translates B's constructor before continuing with A. This callback enables the same
     * behavior by allowing the Converter to trigger inline constructor translation.
     */
    private Consumer<String> onInternalInitCreated;

    /**
     * Maps each CP entry index to its JVM type descriptor (field type or method signature).
     * Used by the DescriptorComponent to generate the type_descriptor_info table
     * (JCVM spec 6.13).
     */
    private final Map<Integer, String> cpTypeDescriptors = new HashMap<>();

    /**
     * Creates a new reference resolver.
     *
     * @param tokenMap current package's token assignments
     * @param imports  imported packages with their export files
     * @param cp       the JCVM constant pool to add entries to
     * @param classes  classes in the current package (for superclass hierarchy navigation)
     */
    public ReferenceResolver(TokenMap tokenMap, List<ImportedPackage> imports,
                             JcvmConstantPool cp, List<ClassInfo> classes) {
        this.tokenMap = tokenMap;
        this.imports = imports;
        this.cp = cp;
        this.currentPackage = tokenMap.packageName().replace('.', '/');
        this.superclassMap = new HashMap<>();
        this.declaredVirtualMethods = new HashMap<>();
        this.privateInstanceMethods = new HashSet<>();
        for (ClassInfo ci : classes) {
            if (ci.superClass() != null) {
                superclassMap.put(ci.thisClass(), ci.superClass());
            }
            Set<String> declared = new HashSet<>();
            for (MethodInfo mi : ci.methods()) {
                if (!mi.isConstructor() && !mi.isStaticInitializer() && !mi.isStatic() && !mi.isPrivate()) {
                    declared.add(mi.name() + ":" + mi.descriptor());
                }
                if (mi.isPrivate() && !mi.isStatic()) {
                    privateInstanceMethods.add(ci.thisClass() + ":" + mi.name() + ":" + mi.descriptor());
                }
            }
            declaredVirtualMethods.put(ci.thisClass(), declared);
        }
    }

    /**
     * Returns {@code true} if the given method is a private instance method
     * in the current package. Java 25+ (JEP 181) compiles calls to such methods
     * as {@code invokevirtual}, but JCVM requires {@code invokespecial}.
     */
    public boolean isPrivateInstanceMethod(String owner, String name, String desc) {
        return privateInstanceMethods.contains(owner + ":" + name + ":" + desc);
    }

    /**
     * Returns the list of imported packages (for ImportComponent generation).
     */
    public List<ImportedPackage> imports() {
        return imports;
    }

    /**
     * Returns only the imported packages that were actually referenced
     * during bytecode translation. Packages that were loaded but never
     * referenced are excluded.
     * <p>
     * Must be called AFTER all bytecode has been translated.
     */
    public List<ImportedPackage> referencedImports() {
        return imports.stream()
                .filter(imp -> referencedPackages.contains(imp.token()))
                .toList();
    }

    /**
     * Returns the underlying constant pool.
     */
    public JcvmConstantPool constantPool() {
        return cp;
    }

    /**
     * Returns the JVM type descriptors tracked for each CP entry.
     * Maps CP index → JVM descriptor (field type or method signature).
     * Used by DescriptorComponent to generate the type_descriptor_info table.
     */
    public Map<Integer, String> cpTypeDescriptors() {
        return Map.copyOf(cpTypeDescriptors);
    }

    /**
     * Sets the class currently being translated. This enables per-class
     * tracking of import encounters, which is needed when the CP building order
     * differs from the token (layout) order.
     *
     * @param className internal name of the class being translated
     */
    public void setCurrentClass(String className) {
        this.currentTranslatingClass = className;
        perClassPackageEncounters.computeIfAbsent(className, k -> new ArrayList<>());
    }

    /**
     * Returns the class currently being translated.
     *
     * @return internal name of the current class, or {@code null} if not set
     */
    public String getCurrentClass() {
        return currentTranslatingClass;
    }

    /**
     * Sets a callback invoked when an internal {@code <init>} static method ref is created.
     * The callback receives the target class name and should translate that class's constructor
     * immediately, enabling depth-first CP entry creation matching Oracle's ordering.
     *
     * @param callback consumer receiving the target class internal name, or {@code null} to disable
     */
    public void setOnInternalInitCreated(Consumer<String> callback) {
        this.onInternalInitCreated = callback;
    }

    /**
     * Recomputes the global package encounter order based on token-order class
     * processing. Oracle tracks import encounters in token order even when CP
     * entries are created in a different order, so this method restores the
     * correct encounter sequence after all bytecodes have been translated.
     *
     * @param tokenOrderClassNames class names in token (layout) order
     */
    public void recomputeEncounterOrder(List<String> tokenOrderClassNames) {
        packageEncounterOrder.clear();
        Set<Integer> seen = new HashSet<>();
        for (String className : tokenOrderClassNames) {
            List<Integer> classEncounters = perClassPackageEncounters.getOrDefault(
                    className, List.of());
            for (int token : classEncounters) {
                if (seen.add(token)) {
                    packageEncounterOrder.add(token);
                }
            }
        }
    }

    /**
     * Resolves a class reference to a CP index.
     *
     * @param internalName class name in internal format (e.g. "com/example/MyApplet")
     * @return CP index for the class reference
     */
    public int resolveClassRef(String internalName) {
        if (isCurrentPackage(internalName)) {
            Integer cached = internalClassRefCache.get(internalName);
            if (cached != null) return cached;

            int placeholder = nextPlaceholder++;
            int cpIdx = cp.addInternalClassRef(placeholder);
            internalClassRefCache.put(internalName, cpIdx);
            pendingPatches.add(new PendingPatch(cpIdx, PatchKind.CLASS, internalName, null, null));
            return cpIdx;
        }

        ImportedPackage pkg = findImportedPackage(internalName);
        markPackageReferenced(pkg.token());
        String simpleName = simpleName(internalName);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);
        return cp.addExternalClassRef(pkg.token(), classExport.token());
    }

    /**
     * Resolves a field reference to a CP index.
     * If the field is not declared in the owner class, walks up the superclass chain.
     *
     * @param owner    class that declares the field (internal name)
     * @param name     field name
     * @param desc     field descriptor (e.g. "S", "[B", "Ljavacard/framework/AID;")
     * @param isStatic true for static fields
     * @return CP index for the field reference
     */
    public int resolveFieldRef(String owner, String name, String desc, boolean isStatic) {
        int cpIdx;
        if (isCurrentPackage(owner)) {
            try {
                cpIdx = resolveInternalFieldRef(owner, name, isStatic);
            } catch (NoSuchElementException e) {
                // Field not declared in this class — try superclass chain
                String superClass = superclassMap.get(owner);
                if (superClass != null) {
                    return resolveFieldRef(superClass, name, desc, isStatic);
                }
                throw e;
            }
        } else {
            cpIdx = resolveExternalFieldRef(owner, name, isStatic);
        }
        cpTypeDescriptors.put(cpIdx, desc);
        return cpIdx;
    }

    /**
     * Resolves a method reference to a CP index.
     * If the method is not declared in the owner class, walks up the superclass chain
     * (handles inherited methods where JVM bytecode uses the subclass as owner).
     *
     * @param owner class that declares the method (internal name)
     * @param name  method name
     * @param desc  method descriptor
     * @param kind  invocation kind (virtual, static, special, interface)
     * @return CP index for the method reference
     */
    public int resolveMethodRef(String owner, String name, String desc, InvokeKind kind) {
        int cpIdx;
        if (isCurrentPackage(owner)) {
            try {
                cpIdx = resolveInternalMethodRef(owner, name, desc, kind);
            } catch (NoSuchElementException e) {
                // Method not declared in this class — try superclass chain
                String superClass = superclassMap.get(owner);
                if (superClass != null) {
                    return resolveMethodRef(superClass, name, desc, kind);
                }
                throw e;
            }
            // Depth-first constructor chaining must be invoked OUTSIDE the try-catch
            // to prevent NoSuchElementExceptions from recursive bytecode translation
            // (via the callback) being caught and misinterpreted as "method not found".
            if ("<init>".equals(name) && onInternalInitCreated != null) {
                onInternalInitCreated.accept(owner);
            }
        } else {
            cpIdx = resolveExternalMethodRef(owner, name, desc, kind);
        }
        cpTypeDescriptors.put(cpIdx, desc);
        return cpIdx;
    }

    // ── Internal reference resolution ──

    private int resolveInternalFieldRef(String owner, String name, boolean isStatic) {
        TokenMap.ClassEntry classEntry = tokenMap.findClass(owner);
        if (isStatic) {
            String key = owner + ":" + name;
            Integer cached = internalStaticFieldRefCache.get(key);
            if (cached != null) return cached;

            int placeholder = nextPlaceholder++;
            int cpIdx = cp.addInternalStaticFieldRef(placeholder);
            internalStaticFieldRefCache.put(key, cpIdx);
            pendingPatches.add(new PendingPatch(cpIdx, PatchKind.STATIC_FIELD, owner, name, null));
            return cpIdx;
        }
        // Instance field: use ClassComponent byte offset (deferred) per JCVM spec 6.8.2
        String key = owner + ":" + name;
        Integer cached = internalInstanceFieldRefCache.get(key);
        if (cached != null) return cached;

        TokenMap.FieldEntry field = classEntry.findInstanceField(name);
        int placeholder = nextPlaceholder++;
        int cpIdx = cp.addInstanceFieldRef(placeholder, field.token());
        internalInstanceFieldRefCache.put(key, cpIdx);
        pendingPatches.add(new PendingPatch(cpIdx, PatchKind.INSTANCE_FIELD, owner, name, null));
        return cpIdx;
    }

    private int resolveInternalMethodRef(String owner, String name, String desc, InvokeKind kind) {
        TokenMap.ClassEntry classEntry = tokenMap.findClass(owner);

        if (kind == InvokeKind.STATIC || kind == InvokeKind.SPECIAL) {
            // Both invokestatic and invokespecial use StaticMethodRef (tag=6)
            // per JCVM spec 7.5.12. This covers constructors (<init>) and
            // super.method() calls — all resolved to specific method offsets.
            String key = owner + ":" + name + ":" + desc;
            Integer cached = internalStaticMethodRefCache.get(key);
            if (cached != null) return cached;

            int placeholder = nextPlaceholder++;
            int cpIdx = cp.addInternalStaticMethodRef(placeholder);
            internalStaticMethodRefCache.put(key, cpIdx);
            pendingPatches.add(new PendingPatch(cpIdx, PatchKind.STATIC_METHOD, owner, name, desc));
            return cpIdx;
        }

        // VIRTUAL or INTERFACE: check if method is declared/overridden locally.
        // If inherited (not locally declared), let the caller walk up the hierarchy
        // to the declaring class — this matches Oracle's encoding where inherited
        // methods reference the declaring (often external) class.
        Set<String> declared = declaredVirtualMethods.get(owner);
        if (declared != null && !declared.contains(name + ":" + desc)) {
            throw new NoSuchElementException(
                    "Method inherited but not declared in " + owner + ": " + name + desc);
        }

        String key = owner + ":" + name + ":" + desc;
        Integer cached = internalVirtualMethodRefCache.get(key);
        if (cached != null) return cached;

        TokenMap.MethodEntry method = classEntry.findVirtualMethod(name, desc);
        int placeholder = nextPlaceholder++;
        int cpIdx = cp.addVirtualMethodRef(placeholder, method.token());
        internalVirtualMethodRefCache.put(key, cpIdx);
        pendingPatches.add(new PendingPatch(cpIdx, PatchKind.VIRTUAL_METHOD, owner, name, desc));
        return cpIdx;
    }

    // ── External reference resolution ──

    private int resolveExternalFieldRef(String owner, String name, boolean isStatic) {
        ImportedPackage pkg = findImportedPackage(owner);
        markPackageReferenced(pkg.token());
        String simpleName = simpleName(owner);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);

        if (isStatic) {
            ExportFile.FieldExport field = findField(classExport, name);
            return cp.addExternalStaticFieldRef(pkg.token(), classExport.token(), field.token());
        }

        // External instance field: direct encoding (pkg|0x80, class_token, field_token)
        // per JCVM spec 6.8.2 — no intermediate ClassRef entry needed
        ExportFile.FieldExport field = findField(classExport, name);
        return cp.addExternalInstanceFieldRef(pkg.token(), classExport.token(), field.token());
    }

    /**
     * Result of resolving an interface method reference for {@code invokeinterface}.
     * Per JCVM §7.5.49, invokeinterface uses a ClassRef CP index + separate method token byte.
     *
     * @param cpIndex     CP index pointing to a ClassRef entry for the interface
     * @param methodToken interface method token (0-based within the interface)
     */
    public record InterfaceMethodRef(int cpIndex, int methodToken) {}

    /**
     * Resolves an interface method call to a ClassRef CP index and method token.
     * Per JCVM §7.5.49, invokeinterface encodes the interface class as a ClassRef
     * and the method token as a separate byte in the instruction.
     */
    public InterfaceMethodRef resolveInterfaceMethodRef(String owner, String name, String desc) {
        // Check if the owner is in the current package
        String ownerPkg = owner.contains("/")
                ? owner.substring(0, owner.lastIndexOf('/'))
                : "";
        if (ownerPkg.equals(currentPackage)) {
            // Internal interface — use internal class ref + token from token map
            int cpIndex = resolveClassRef(owner);
            String simpleName = simpleName(owner);
            var entry = tokenMap.classes().stream()
                    .filter(c -> c.internalName().endsWith("/" + simpleName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Internal interface not found: " + owner));
            int methodToken = entry.virtualMethods().stream()
                    .filter(m -> m.name().equals(name) && m.descriptor().equals(desc))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Interface method not found: " + owner + "." + name + desc))
                    .token();
            return new InterfaceMethodRef(cpIndex, methodToken);
        }

        ImportedPackage pkg = findImportedPackage(owner);
        markPackageReferenced(pkg.token());
        String simpleName = simpleName(owner);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);
        ExportFile.MethodExport method = findMethod(classExport, name, desc);
        int cpIndex = cp.addExternalClassRef(pkg.token(), classExport.token());
        return new InterfaceMethodRef(cpIndex, method.token());
    }

    private int resolveExternalMethodRef(String owner, String name, String desc, InvokeKind kind) {
        ImportedPackage pkg = findImportedPackage(owner);
        markPackageReferenced(pkg.token());
        String simpleName = simpleName(owner);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);

        if (kind == InvokeKind.STATIC || kind == InvokeKind.SPECIAL) {
            // Both invokestatic and invokespecial use StaticMethodRef (tag=6)
            // per JCVM spec 7.5.12. External encoding: (pkg|0x80, class_token, method_token)
            ExportFile.MethodExport method = findMethod(classExport, name, desc);
            return cp.addExternalStaticMethodRef(pkg.token(), classExport.token(), method.token());
        }

        // VIRTUAL: direct encoding (pkg|0x80, class_token, method_token)
        // per JCVM spec 6.8.3 — no intermediate ClassRef entry needed
        ExportFile.MethodExport method = findMethod(classExport, name, desc);
        return cp.addExternalVirtualMethodRef(pkg.token(), classExport.token(), method.token());
    }

    // ── Helpers ──

    /**
     * Records that a package was referenced. Tracks both the set of referenced packages
     * and the order of first encounter (for import token assignment matching Oracle).
     */
    private void markPackageReferenced(int token) {
        if (referencedPackages.add(token)) {
            packageEncounterOrder.add(token);
        }
        // Track per-class encounters for recomputeEncounterOrder
        if (currentTranslatingClass != null) {
            List<Integer> classEncounters = perClassPackageEncounters.get(currentTranslatingClass);
            if (classEncounters != null && !classEncounters.contains(token)) {
                classEncounters.add(token);
            }
        }
    }

    private boolean isCurrentPackage(String internalName) {
        String packagePart = packageOf(internalName);
        return packagePart.equals(currentPackage);
    }

    private ImportedPackage findImportedPackage(String classInternalName) {
        String packageName = packageOf(classInternalName);
        for (ImportedPackage imp : imports) {
            String impPkg = imp.exportFile().packageName();
            // ExportFile may store package name in slash or dot notation
            if (impPkg.equals(packageName) || impPkg.replace('/', '.').equals(packageName.replace('/', '.'))) {
                return imp;
            }
        }
        throw new NoSuchElementException(
                "No import found for package of class: " + classInternalName
                        + " (package: " + packageName + ")");
    }

    private static String packageOf(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(0, lastSlash) : "";
    }

    private static String simpleName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private static ExportFile.FieldExport findField(ExportFile.ClassExport classExport, String name) {
        for (ExportFile.FieldExport fe : classExport.fields()) {
            if (fe.name().equals(name)) return fe;
        }
        throw new NoSuchElementException(
                "Field not found in export: " + classExport.name() + "." + name);
    }

    private static ExportFile.MethodExport findMethod(ExportFile.ClassExport classExport,
                                                       String name, String desc) {
        for (ExportFile.MethodExport me : classExport.methods()) {
            if (me.name().equals(name) && me.descriptor().equals(desc)) return me;
        }
        // Try matching by name only (some export files may not include full descriptor)
        for (ExportFile.MethodExport me : classExport.methods()) {
            if (me.name().equals(name)) return me;
        }
        throw new NoSuchElementException(
                "Method not found in export: " + classExport.name() + "." + name + desc);
    }

    /**
     * Returns the set of package names that must always be imported regardless of whether
     * they are explicitly referenced in bytecode, based on the target JavaCard version.
     *
     * <p>{@code javacard/framework} is always mandatory. {@code java/lang} is mandatory
     * starting from JC 2.2.2 — Oracle's JC 2.1.2 and 2.2.1 converters omit it if not
     * explicitly referenced in bytecode.
     */
    private static Set<String> mandatoryPackages(JavaCardVersion version) {
        if (version == JavaCardVersion.V2_1_2 || version == JavaCardVersion.V2_2_1) {
            return Set.of("javacard/framework");
        }
        return Set.of("java/lang", "javacard/framework");
    }

    /**
     * Finalizes the import list after all bytecode has been translated.
     * Removes unreferenced packages (except mandatory ones) and reassigns
     * contiguous tokens (0, 1, 2...). Also remaps all external CP entries
     * to use the new token values via {@link JcvmConstantPool#remapPackageTokens}.
     *
     * <p>Must be called <strong>after</strong> all bytecode translation and
     * <strong>before</strong> ClassComponent generation (since ClassComponent
     * uses {@link #resolveClassRefDirect} which reads import tokens).
     *
     * @param version the target JavaCard version (determines mandatory imports)
     * @return the finalized list of imported packages with reassigned contiguous tokens
     */
    public List<ImportedPackage> finalizeImports(JavaCardVersion version) {
        Set<String> mandatory = mandatoryPackages(version);

        // Include referenced + mandatory packages
        Set<Integer> keep = new HashSet<>(referencedPackages);
        for (ImportedPackage imp : imports) {
            if (mandatory.contains(imp.exportFile().packageName())) {
                keep.add(imp.token());
            }
        }

        // Build a map from token to ImportedPackage for quick lookup
        Map<Integer, ImportedPackage> byToken = new HashMap<>();
        for (ImportedPackage imp : imports) {
            byToken.put(imp.token(), imp);
        }

        // Order by first-encounter during bytecode translation (matches Oracle).
        // Packages referenced during translation appear in encounter order;
        // mandatory-but-unreferenced packages are appended at the end.
        List<ImportedPackage> filtered = new ArrayList<>();
        Set<Integer> added = new HashSet<>();
        for (int token : packageEncounterOrder) {
            if (keep.contains(token) && added.add(token)) {
                ImportedPackage imp = byToken.get(token);
                if (imp != null) filtered.add(imp);
            }
        }
        // Append mandatory packages not yet encountered (in their original order)
        for (ImportedPackage imp : imports) {
            if (keep.contains(imp.token()) && added.add(imp.token())) {
                filtered.add(imp);
            }
        }

        Map<Integer, Integer> remap = new HashMap<>();
        List<ImportedPackage> reassigned = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            ImportedPackage orig = filtered.get(i);
            remap.put(orig.token(), i);
            reassigned.add(new ImportedPackage(
                    i, orig.aid(), orig.majorVersion(), orig.minorVersion(), orig.exportFile()));
        }

        cp.remapPackageTokens(remap);

        imports.clear();
        imports.addAll(reassigned);

        return reassigned;
    }

    /**
     * Patches all internal CP references with real component offsets.
     * Must be called after ClassComponent, MethodComponent, and StaticFieldComponent
     * have been generated so that byte offsets are known.
     *
     * @param classOffsets           byte offset of each class in ClassComponent info, indexed by class token
     * @param methodOffsetMap        map from "className:methodName:methodDesc" → Method component offset
     * @param staticFieldOffsetMap   map from "className:fieldName" → StaticField image offset
     */
    public void patchInternalRefs(int[] classOffsets,
                                   Map<String, Integer> methodOffsetMap,
                                   Map<String, Integer> staticFieldOffsetMap) {
        for (PendingPatch p : pendingPatches) {
            switch (p.kind) {
                case CLASS -> {
                    int token = tokenMap.classToken(p.className);
                    int offset = classOffsets[token];
                    // JCVM spec 6.8.1 Table 6-18: internal = u2 internal_class_ref + u1 padding
                    // Note: differs from StaticMethodRef/StaticFieldRef which are padding + u2
                    cp.replaceEntry(p.cpIndex, new JcvmConstantPool.CpEntry(
                            JcvmConstantPool.TAG_CLASSREF,
                            (byte) ((offset >> 8) & 0xFF),
                            (byte) (offset & 0xFF),
                            (byte) 0));
                }
                case STATIC_METHOD -> {
                    String key = p.className + ":" + p.memberName + ":" + p.memberDesc;
                    Integer offset = methodOffsetMap.get(key);
                    if (offset != null) {
                        // JCVM spec 6.8.6 Table 6-26: internal = padding(0) + u2 offset
                        cp.replaceEntry(p.cpIndex, new JcvmConstantPool.CpEntry(
                                JcvmConstantPool.TAG_STATIC_METHODREF,
                                (byte) 0,
                                (byte) ((offset >> 8) & 0xFF),
                                (byte) (offset & 0xFF)));
                    }
                }
                case STATIC_FIELD -> {
                    String key = p.className + ":" + p.memberName;
                    Integer offset = staticFieldOffsetMap.get(key);
                    if (offset != null) {
                        // JCVM spec 6.8.5 Table 6-24: internal = padding(0) + u2 offset
                        cp.replaceEntry(p.cpIndex, new JcvmConstantPool.CpEntry(
                                JcvmConstantPool.TAG_STATIC_FIELDREF,
                                (byte) 0,
                                (byte) ((offset >> 8) & 0xFF),
                                (byte) (offset & 0xFF)));
                    }
                }
                case INSTANCE_FIELD -> {
                    // Patch class offset in InstanceFieldRef (b1:b2 = class offset, b3 = field token)
                    int classToken = tokenMap.classToken(p.className);
                    int classOffset = classOffsets[classToken];
                    TokenMap.ClassEntry classEntry = tokenMap.findClass(p.className);
                    TokenMap.FieldEntry field = classEntry.findInstanceField(p.memberName);
                    cp.replaceEntry(p.cpIndex, new JcvmConstantPool.CpEntry(
                            JcvmConstantPool.TAG_INSTANCE_FIELDREF,
                            (byte) ((classOffset >> 8) & 0xFF),
                            (byte) (classOffset & 0xFF),
                            (byte) field.token()));
                }
                case VIRTUAL_METHOD -> {
                    // Patch class offset in VirtualMethodRef (b1:b2 = class offset, b3 = method token)
                    int classToken = tokenMap.classToken(p.className);
                    int classOffset = classOffsets[classToken];
                    TokenMap.ClassEntry classEntry = tokenMap.findClass(p.className);
                    TokenMap.MethodEntry method = classEntry.findVirtualMethod(p.memberName, p.memberDesc);
                    cp.replaceEntry(p.cpIndex, new JcvmConstantPool.CpEntry(
                            JcvmConstantPool.TAG_VIRTUAL_METHODREF,
                            (byte) ((classOffset >> 8) & 0xFF),
                            (byte) (classOffset & 0xFF),
                            (byte) method.token()));
                }
            }
        }
    }

    /**
     * Remaps CP indices in pending internal reference patches after constant pool reordering.
     * Must be called after {@link JcvmConstantPool#reorderInstanceFieldsFirst(Map) reorderInstanceFieldsFirst()} and before
     * {@link #patchInternalRefs} so that deferred patches target the correct (reordered) entries.
     *
     * @param remap old CP index → new CP index mapping
     */
    public void remapPendingCpIndices(int[] remap) {
        for (int i = 0; i < pendingPatches.size(); i++) {
            PendingPatch p = pendingPatches.get(i);
            int newIndex = remap[p.cpIndex()];
            if (newIndex != p.cpIndex()) {
                pendingPatches.set(i, new PendingPatch(
                        newIndex, p.kind(), p.className(), p.memberName(), p.memberDesc()));
            }
        }
    }

    /**
     * Returns a mapping from CP index to class token for all pending instance field patches.
     * Used by {@link JcvmConstantPool#reorderInstanceFieldsFirst(Map)} to sort instance
     * field refs by class token order (matching Oracle's ordering).
     *
     * @return map from CP index to class token for INSTANCE_FIELD patches
     */
    public Map<Integer, Integer> getInstanceFieldClassTokens() {
        Map<Integer, Integer> result = new HashMap<>();
        for (PendingPatch p : pendingPatches) {
            if (p.kind() == PatchKind.INSTANCE_FIELD) {
                result.put(p.cpIndex(), tokenMap.classToken(p.className()));
            }
        }
        return result;
    }

    /**
     * Remaps CP type descriptor keys after {@link JcvmConstantPool#reorderInstanceFieldsFirst(Map)}.
     * The type descriptors are keyed by CP index, so when entries move we must update keys
     * to match the new indices. Without this, DescriptorComponent generates a type table
     * with stale offsets.
     *
     * @param remap old CP index → new CP index mapping
     */
    public void remapCpTypeDescriptors(int[] remap) {
        Map<Integer, String> remapped = new HashMap<>();
        for (var entry : cpTypeDescriptors.entrySet()) {
            int oldIdx = entry.getKey();
            int newIdx = (oldIdx < remap.length) ? remap[oldIdx] : oldIdx;
            remapped.put(newIdx, entry.getValue());
        }
        cpTypeDescriptors.clear();
        cpTypeDescriptors.putAll(remapped);
    }

    /**
     * Resolves a class reference to a direct 2-byte encoding, as used in ClassComponent
     * (super_class_ref, interface_ref). This is NOT a CP index — it uses the same
     * binary encoding as CONSTANT_Classref_info entries:
     * <ul>
     *   <li>Internal: high bit = 0, value = byte offset into ClassComponent info</li>
     *   <li>External: high bit = 1, b1 = 0x80 | package_token, b2 = class_token</li>
     * </ul>
     *
     * @param internalName class name in internal format
     * @return 2-byte direct class reference encoding
     */
    public int resolveClassRefDirect(String internalName) {
        if (isCurrentPackage(internalName)) {
            // Internal: will be patched with ClassComponent offset later
            // Use token as placeholder (patched in Converter after ClassComponent gen)
            return tokenMap.classToken(internalName);
        }

        ImportedPackage pkg = findImportedPackage(internalName);
        markPackageReferenced(pkg.token());
        String simpleName = simpleName(internalName);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);
        // External: 0x80 | pkg_token in high byte, class_token in low byte
        return ((0x80 | pkg.token()) << 8) | classExport.token();
    }

    /**
     * Returns the virtual method declarations for an interface, ordered by token.
     * Used by ClassComponent for interface-to-method mapping tables.
     *
     * @param internalName interface name in internal format (e.g. "javacard/framework/Shareable")
     * @return list of method entries, empty if interface has no methods
     */
    public List<TokenMap.MethodEntry> getInterfaceMethods(String internalName) {
        if (isCurrentPackage(internalName)) {
            // Internal interface — get methods from token map
            TokenMap.ClassEntry classEntry = tokenMap.findClass(internalName);
            return classEntry.virtualMethods();
        }

        // External interface — get methods from export file
        ImportedPackage pkg = findImportedPackage(internalName);
        String simpleName = simpleName(internalName);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);

        return classExport.methods().stream()
                .filter(m -> (m.accessFlags() & 0x0008) == 0) // not ACC_STATIC
                .filter(m -> !"<init>".equals(m.name()) && !"<clinit>".equals(m.name()))
                .map(m -> new TokenMap.MethodEntry(m.name(), m.descriptor(), m.token()))
                .toList();
    }

    /**
     * Returns the number of virtual method tokens for a class (max_token + 1).
     * Used by ClassComponent to compute the VMMT (Virtual Method Mapping Table)
     * in CAP format 2.3 (JCVM 3.1.0+).
     *
     * @param internalName class name in internal format (e.g. "javacard/framework/Applet")
     * @return number of virtual method tokens (max_token + 1), or 0 if no virtual methods
     */
    public int getVirtualMethodCount(String internalName) {
        if ("java/lang/Object".equals(internalName)) {
            return 1; // equals at token 0
        }
        if (isCurrentPackage(internalName)) {
            TokenMap.ClassEntry entry = tokenMap.findClass(internalName);
            return entry.virtualMethods().stream()
                    .mapToInt(TokenMap.MethodEntry::token).max().orElse(-1) + 1;
        }
        ImportedPackage pkg = findImportedPackage(internalName);
        String simpleName = simpleName(internalName);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);
        return classExport.methods().stream()
                .filter(m -> (m.accessFlags() & 0x0008) == 0) // not ACC_STATIC
                .filter(m -> !"<init>".equals(m.name()) && !"<clinit>".equals(m.name()))
                .mapToInt(ExportFile.MethodExport::token).max().orElse(-1) + 1;
    }

    /**
     * Invocation kind for method reference resolution, corresponding to JVM invoke opcodes.
     *
     * <p>Determines the CP entry tag used in the JCVM constant pool:
     * <ul>
     *   <li>{@link #VIRTUAL} and {@link #INTERFACE} produce {@code CONSTANT_VirtualMethodref}
     *       (tag 3) entries, encoding (class_offset, method_token) for internal or
     *       (pkg|0x80, class_token, method_token) for external references.</li>
     *   <li>{@link #STATIC} and {@link #SPECIAL} produce {@code CONSTANT_StaticMethodref}
     *       (tag 6) entries, encoding a method component offset for internal or
     *       (pkg|0x80, class_token, method_token) for external references.</li>
     * </ul>
     *
     * @see <a href="https://docs.oracle.com/javacard/3.0.5/JCVM/jcvm-spec-3_0_5.pdf">JCVM 3.0.5 spec, section 6.8.3</a>
     */
    public enum InvokeKind {
        /** JVM {@code invokevirtual} -- dispatched via virtual method table. */
        VIRTUAL,
        /** JVM {@code invokestatic} -- resolved to a specific method offset. */
        STATIC,
        /** JVM {@code invokespecial} -- constructors, super calls, private methods. */
        SPECIAL,
        /** JVM {@code invokeinterface} -- dispatched via interface method table. */
        INTERFACE
    }

    /**
     * Discriminates the kind of internal reference that needs deferred patching.
     * Each kind requires a different byte layout in the final CP entry.
     */
    private enum PatchKind {
        /** Class reference: 2-byte offset into ClassComponent info. */
        CLASS,
        /** Static or special method: 2-byte offset into MethodComponent. */
        STATIC_METHOD,
        /** Static field: 2-byte offset into StaticFieldComponent image. */
        STATIC_FIELD,
        /** Instance field: 2-byte class offset + 1-byte field token. */
        INSTANCE_FIELD,
        /** Virtual method: 2-byte class offset + 1-byte method token. */
        VIRTUAL_METHOD
    }

    /**
     * A deferred patch record: captures the CP index that holds a placeholder value
     * along with enough information (class name, member name/descriptor) to compute
     * the real byte offset once components have been generated.
     *
     * @param cpIndex    index of the CP entry containing a placeholder value
     * @param kind       what kind of reference this is (determines patching logic)
     * @param className  internal name of the owning class
     * @param memberName field or method name (null for CLASS patches)
     * @param memberDesc method descriptor (null for CLASS and field patches)
     */
    private record PendingPatch(int cpIndex, PatchKind kind, String className,
                                String memberName, String memberDesc) {}
}
