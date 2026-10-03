package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    private static final String OBJECT = "java/lang/Object";

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
     * Statically bound members declared per class: static fields ({@code "name"}) and static
     * methods, constructors and private methods ({@code "name:descriptor"}). javac names the
     * qualifying class of a reference, which may inherit the member (JLS 13.1), so a member not
     * declared in the named class is looked up in its superclasses (JCVM 3.1 §6.8.3: the
     * reference must designate the declaring class).
     */
    private final Map<String, Set<String>> declaredStaticMembers = new HashMap<>();

    /** Information about classes and interfaces of the imported packages (export files). */
    private final ImportedTypes importedTypes;

    /**
     * Deferred patches for internal references. Internal refs use placeholder values during
     * translation because real byte offsets are not yet known. After component generation,
     * {@link #patchInternalRefs(Map, Map, Map)} replaces each placeholder with the actual offset.
     */
    private final InternalRefPatches patches = new InternalRefPatches();

    /** Class component offsets of the package's classes, known after {@link #patchInternalRefs}. */
    private Map<String, Integer> classOffsets = Map.of();

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
    private final Map<String, Integer> superMethodRefCache = new HashMap<>();

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
        this.importedTypes = new ExportedTypes(imports);
        for (ClassInfo ci : classes) {
            indexMembers(ci);
        }
    }

    /**
     * Records the superclass of a class of the package and the members it declares: virtual
     * methods, statically bound members and private instance methods (see the fields).
     */
    private void indexMembers(ClassInfo ci) {
        if (ci.superClass() != null) {
            superclassMap.put(ci.thisClass(), ci.superClass());
        }
        Set<String> declared = new HashSet<>();
        Set<String> statics = new HashSet<>();
        for (MethodInfo mi : ci.methods()) {
            if (!mi.isConstructor() && !mi.isStaticInitializer() && !mi.isStatic() && !mi.isPrivate()) {
                declared.add(mi.name() + ":" + mi.descriptor());
            } else {
                statics.add(mi.name() + ":" + mi.descriptor());
            }
            if (mi.isPrivate() && !mi.isStatic()) {
                privateInstanceMethods.add(ci.thisClass() + ":" + mi.name() + ":" + mi.descriptor());
            }
        }
        ci.fields().stream().filter(f -> f.isStatic()).forEach(f -> statics.add(f.name()));
        declaredVirtualMethods.put(ci.thisClass(), declared);
        declaredStaticMembers.put(ci.thisClass(), statics);
    }

    /**
     * Returns what the export files of the imported packages say about their classes and
     * interfaces (inherited virtual method tokens, interface methods, ACC_SHAREABLE).
     *
     * @return imported type information
     */
    public ImportedTypes importedTypes() {
        return importedTypes;
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
     * Whether {@code invokeinterface owner.name desc} calls a public instance method of {@code Object} the
     * interface does not have: an interface's members include them (JLS 9.2, JVMS 5.4.3.4), but its tokens
     * cover only its own and superinterface methods (JCVM 3.1 §4.3.7.7, §5.7): invokevirtual (§7.5.57).
     *
     * @param owner interface named by the reference (internal name)
     * @param name  method name
     * @param desc  method descriptor
     * @return {@code true} if the call must be an invokevirtual of the {@code java.lang.Object} method
     */
    public boolean isObjectMethodOfInterface(String owner, String name, String desc) {
        boolean declared = isCurrentPackage(owner)
                ? tokenMap.findClass(owner).virtualMethods().stream()
                        .anyMatch(m -> m.name().equals(name) && m.descriptor().equals(desc))
                : ExportMembers.declaresMethod(exportedClass(owner), name, desc);
        return !declared && ExportMembers.declaresPublicInstanceMethod(exportedClass(OBJECT), name, desc);
    }

    private ExportFile.ClassExport exportedClass(String internalName) {
        return findImportedPackage(internalName).exportFile().findClass(simpleName(internalName));
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

            int cpIdx = cp.addInternalClassRef(patches.nextPlaceholder());
            internalClassRefCache.put(internalName, cpIdx);
            patches.add(new InternalRefPatches.Patch(cpIdx, InternalRefPatches.Kind.CLASS, internalName,
                    null, null, 0));
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
        if (isSuperInvocation(owner, name, desc, kind)) {
            cpIdx = resolveSuperMethodRef(owner, name, desc);
            cpTypeDescriptors.put(cpIdx, desc);
            return cpIdx;
        }
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

    /**
     * Returns whether an {@code invokespecial} is a super invocation ({@code super.m()}): it names
     * neither a constructor nor a private method of the named class. JCVM 3.1 §7.5.55 requires a
     * CONSTANT_SuperMethodref for it; CONSTANT_StaticMethodref is only for constructors and
     * private methods.
     */
    private boolean isSuperInvocation(String owner, String name, String desc, InvokeKind kind) {
        return kind == InvokeKind.SPECIAL && !"<init>".equals(name)
                && !privateInstanceMethods.contains(owner + ":" + name + ":" + desc);
    }

    /**
     * Resolves {@code super.m()} to a CONSTANT_SuperMethodref (JCVM 3.1 §6.8.2): the class item is
     * the class containing the super invocation, the token is the virtual method token of the
     * method in the hierarchy of that class's superclass (the class javac names as owner).
     */
    private int resolveSuperMethodRef(String owner, String name, String desc) {
        String caller = currentTranslatingClass;
        if (caller == null || !isCurrentPackage(caller)) {
            throw new IllegalStateException("super invocation of " + owner + "." + name + desc
                    + " outside a class of the converted package");
        }
        String key = caller + ":" + name + ":" + desc;
        Integer cached = superMethodRefCache.get(key);
        if (cached != null) return cached;

        int token = superMethodToken(owner, name, desc);
        int cpIdx = cp.addSuperMethodRef(patches.nextPlaceholder(), token);
        superMethodRefCache.put(key, cpIdx);
        patches.add(new InternalRefPatches.Patch(cpIdx, InternalRefPatches.Kind.SUPER_METHOD, caller,
                name, desc, token));
        return cpIdx;
    }

    /** Virtual method token of {@code name desc} in the hierarchy of {@code superClass}. */
    private int superMethodToken(String superClass, String name, String desc) {
        List<TokenMap.MethodEntry> methods = isCurrentPackage(superClass)
                ? tokenMap.findClass(superClass).virtualMethods()
                : importedTypes.virtualMethods(superClass);
        return methods.stream()
                .filter(m -> m.name().equals(name) && m.descriptor().equals(desc))
                .mapToInt(TokenMap.MethodEntry::token)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("super invocation of " + superClass + "."
                        + name + desc + ": no such virtual method in the superclass hierarchy"));
    }

    /**
     * Throws {@link NoSuchElementException} if {@code owner} does not declare the statically bound
     * member, so that the caller continues the lookup in the superclass (JLS 13.1, JCVM 3.1 §6.8.3).
     */
    private void requireDeclaredStatic(String owner, String member) {
        Set<String> declared = declaredStaticMembers.get(owner);
        if (declared != null && !declared.contains(member)) {
            throw new NoSuchElementException("Member inherited but not declared in " + owner + ": " + member);
        }
    }

    // ── Internal reference resolution ──

    private int resolveInternalFieldRef(String owner, String name, boolean isStatic) {
        TokenMap.ClassEntry classEntry = tokenMap.findClass(owner);
        if (isStatic) {
            requireDeclaredStatic(owner, name);
            String key = owner + ":" + name;
            Integer cached = internalStaticFieldRefCache.get(key);
            if (cached != null) return cached;

            int cpIdx = cp.addInternalStaticFieldRef(patches.nextPlaceholder());
            internalStaticFieldRefCache.put(key, cpIdx);
            patches.add(new InternalRefPatches.Patch(cpIdx, InternalRefPatches.Kind.STATIC_FIELD, owner,
                    name, null, 0));
            return cpIdx;
        }
        // Instance field: the declaring class's Class component offset (deferred), JCVM 3.1 §6.8.2
        String key = owner + ":" + name;
        Integer cached = internalInstanceFieldRefCache.get(key);
        if (cached != null) return cached;

        TokenMap.FieldEntry field = classEntry.findInstanceField(name);
        int cpIdx = cp.addInstanceFieldRef(patches.nextPlaceholder(), field.token());
        internalInstanceFieldRefCache.put(key, cpIdx);
        patches.add(new InternalRefPatches.Patch(cpIdx, InternalRefPatches.Kind.INSTANCE_FIELD, owner,
                name, null, field.token()));
        return cpIdx;
    }

    private int resolveInternalMethodRef(String owner, String name, String desc, InvokeKind kind) {
        TokenMap.ClassEntry classEntry = tokenMap.findClass(owner);
        if (kind == InvokeKind.STATIC || kind == InvokeKind.SPECIAL) {
            return resolveInternalStaticMethodRef(owner, name, desc);
        }
        return resolveInternalVirtualMethodRef(classEntry, owner, name, desc);
    }

    /**
     * invokestatic, and invokespecial of constructors and private methods, use a
     * CONSTANT_StaticMethodref with the Method component offset (JCVM 3.1 §6.8.3, §7.5.55);
     * super invocations were handled by {@link #resolveSuperMethodRef}.
     */
    private int resolveInternalStaticMethodRef(String owner, String name, String desc) {
        requireDeclaredStatic(owner, name + ":" + desc);
        String key = owner + ":" + name + ":" + desc;
        Integer cached = internalStaticMethodRefCache.get(key);
        if (cached != null) return cached;

        int cpIdx = cp.addInternalStaticMethodRef(patches.nextPlaceholder());
        internalStaticMethodRefCache.put(key, cpIdx);
        patches.add(new InternalRefPatches.Patch(cpIdx, InternalRefPatches.Kind.STATIC_METHOD, owner,
                name, desc, 0));
        return cpIdx;
    }

    /** CONSTANT_VirtualMethodref of a method declared by a class of the package (JCVM 3.1 §6.8.2). */
    private int resolveInternalVirtualMethodRef(TokenMap.ClassEntry classEntry, String owner, String name,
                                                String desc) {
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
        int cpIdx = cp.addVirtualMethodRef(patches.nextPlaceholder(), method.token());
        internalVirtualMethodRefCache.put(key, cpIdx);
        patches.add(new InternalRefPatches.Patch(cpIdx, InternalRefPatches.Kind.VIRTUAL_METHOD, owner,
                name, desc, method.token()));
        return cpIdx;
    }

    // ── External reference resolution ──

    private int resolveExternalFieldRef(String owner, String name, boolean isStatic) {
        // The field may be inherited by the named class: reference the declaring class
        // (JCVM 3.1 §6.8.2, §6.8.3), which may be in another imported package
        DeclaringExport declaring = declaringExport(owner, ce -> ExportMembers.declaresField(ce, name));
        ImportedPackage pkg = declaring.pkg();
        markPackageReferenced(pkg.token());
        ExportFile.FieldExport field = ExportMembers.field(declaring.cls(), name);
        if (isStatic) {
            return cp.addExternalStaticFieldRef(pkg.token(), declaring.cls().token(), field.token());
        }
        // External instance field: direct encoding (pkg|0x80, class_token, field_token)
        // per JCVM spec 6.8.2 — no intermediate ClassRef entry needed
        return cp.addExternalInstanceFieldRef(pkg.token(), declaring.cls().token(), field.token());
    }

    /** The imported class declaring a member named through {@code owner} (see {@link DeclaringExport}). */
    private DeclaringExport declaringExport(String owner, Predicate<ExportFile.ClassExport> declares) {
        ImportedPackage pkg = findImportedPackage(owner);
        return DeclaringExport.find(imports, pkg, pkg.exportFile().findClass(simpleName(owner)), declares);
    }

    /**
     * Result of resolving an interface method reference for {@code invokeinterface}.
     * Per JCVM 3.1 §7.5.54, invokeinterface uses a ClassRef CP index + separate method token byte.
     *
     * @param cpIndex     CP index pointing to a ClassRef entry for the interface
     * @param methodToken interface method token (0-based within the interface)
     */
    public record InterfaceMethodRef(int cpIndex, int methodToken) {}

    /**
     * Resolves an interface method call to a ClassRef CP index and method token. Per JCVM 3.1
     * §7.5.54, invokeinterface encodes the interface as a ClassRef and the token as a separate byte.
     */
    public InterfaceMethodRef resolveInterfaceMethodRef(String owner, String name, String desc) {
        if (isCurrentPackage(owner)) {
            // Internal interface: internal class ref + interface method token, which covers the
            // methods inherited from superinterfaces as well (JCVM 3.1 §4.3.7.7)
            int cpIndex = resolveClassRef(owner);
            TokenMap.ClassEntry entry = tokenMap.findClass(owner);
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
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName(owner));
        ExportFile.MethodExport method = ExportMembers.method(classExport, name, desc);
        int cpIndex = cp.addExternalClassRef(pkg.token(), classExport.token());
        return new InterfaceMethodRef(cpIndex, method.token());
    }

    private int resolveExternalMethodRef(String owner, String name, String desc, InvokeKind kind) {
        if (kind == InvokeKind.STATIC) {
            // A static method may be inherited by the named class: reference the class that defines
            // it (JCVM 3.1 §6.8.3). Encoding: (pkg|0x80, class_token, method_token)
            DeclaringExport declaring = declaringExport(owner, ce -> ExportMembers.declaresMethod(ce, name, desc));
            markPackageReferenced(declaring.pkg().token());
            ExportFile.MethodExport method = ExportMembers.method(declaring.cls(), name, desc);
            return cp.addExternalStaticMethodRef(declaring.pkg().token(), declaring.cls().token(), method.token());
        }
        ImportedPackage pkg = findImportedPackage(owner);
        markPackageReferenced(pkg.token());
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName(owner));
        ExportFile.MethodExport method = ExportMembers.method(classExport, name, desc);
        if (kind == InvokeKind.SPECIAL) {
            // invokespecial of a constructor (never inherited) uses a StaticMethodref (JCVM 3.1 §7.5.55)
            return cp.addExternalStaticMethodRef(pkg.token(), classExport.token(), method.token());
        }
        // VIRTUAL: the named class, whose export entry lists inherited virtual methods with their
        // tokens (JCVM 3.1 §5.9, §6.8.2); direct encoding (pkg|0x80, class_token, method_token)
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
        return packageOf(internalName).equals(currentPackage);
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

    /**
     * Returns the packages imported even when nothing in the CAP file references them.
     *
     * <p>Only {@code java/lang}, starting from JC 2.2.2: Oracle's converters import it for every
     * package of those versions (their JC 2.1.2 and 2.2.1 converters omit it if not referenced),
     * and every card has it. Any other package, {@code javacard/framework} included, is imported
     * only when referenced (JCVM 3.1 §6.7: the Import component lists the packages imported by the
     * classes of the CAP file): a library that does not use the framework must not depend on a
     * framework version.
     */
    private static Set<String> mandatoryPackages(JavaCardVersion version) {
        if (version == JavaCardVersion.V2_1_2 || version == JavaCardVersion.V2_2_1) {
            return Set.of();
        }
        return Set.of("java/lang");
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
        return finalizeImports(version, List.of());
    }

    /**
     * Finalizes the import list like {@link #finalizeImports(JavaCardVersion)}, additionally
     * keeping the packages of classes that the Class and Descriptor components name outside the
     * constant pool: superclasses, implemented interfaces and descriptor types (JCVM 3.1 §6.7,
     * §6.9, §6.14). Such packages are appended after the packages referenced by bytecode.
     *
     * @param version             the target JavaCard version (determines mandatory imports)
     * @param structuralClassRefs internal names of classes named by class or descriptor
     *                            structures; classes of this package and of packages without an
     *                            export file are ignored
     * @return the finalized list of imported packages with reassigned contiguous tokens
     */
    public List<ImportedPackage> finalizeImports(JavaCardVersion version,
                                                 Collection<String> structuralClassRefs) {
        markStructurallyReferenced(structuralClassRefs);
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

        return reassignTokens(filtered);
    }

    /**
     * Gives the kept packages the contiguous tokens 0..n-1 in their order (JCVM 3.1 §4.3.7.1) and
     * remaps the external constant pool entries to them.
     */
    private List<ImportedPackage> reassignTokens(List<ImportedPackage> filtered) {
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

    /** Marks the imported packages of classes named outside the constant pool as referenced. */
    private void markStructurallyReferenced(Collection<String> structuralClassRefs) {
        for (String className : structuralClassRefs) {
            if (isCurrentPackage(className)) continue;
            String pkg = packageOf(className);
            for (ImportedPackage imp : imports) {
                if (imp.exportFile().packageName().equals(pkg) && referencedPackages.add(imp.token())) {
                    packageEncounterOrder.add(imp.token());
                }
            }
        }
    }

    /**
     * Patches all internal CP references with their final component offsets (JCVM 3.1 §6.8).
     * Must be called after the Class, Method and Static Field components have been laid out.
     *
     * @param classOffsets         internal class name to the offset of its {@code interface_info}
     *                             or {@code class_info} in the Class component
     * @param methodOffsetMap      {@code "class:name:descriptor"} to Method component offset
     * @param staticFieldOffsetMap {@code "class:field"} to static field image offset
     * @throws IllegalStateException if an internal reference names an item without an offset
     */
    public void patchInternalRefs(Map<String, Integer> classOffsets,
                                  Map<String, Integer> methodOffsetMap,
                                  Map<String, Integer> staticFieldOffsetMap) {
        this.classOffsets = Map.copyOf(classOffsets);
        patches.apply(cp, this.classOffsets, methodOffsetMap, staticFieldOffsetMap);
    }

    /**
     * Patches all internal CP references; class offsets are given per class token.
     *
     * @param classOffsets         Class component offset per class token
     * @param methodOffsetMap      {@code "class:name:descriptor"} to Method component offset
     * @param staticFieldOffsetMap {@code "class:field"} to static field image offset
     * @deprecated package-visible classes have no class token (JCVM 3.1 §4.3.7.2), so their
     *             offsets cannot be passed this way; use {@link #patchInternalRefs(Map, Map, Map)}
     */
    @Deprecated
    public void patchInternalRefs(int[] classOffsets,
                                  Map<String, Integer> methodOffsetMap,
                                  Map<String, Integer> staticFieldOffsetMap) {
        Map<String, Integer> byName = new HashMap<>();
        for (TokenMap.ClassEntry ce : tokenMap.classes()) {
            if (ce.token() != TokenMap.NO_TOKEN && ce.token() < classOffsets.length) {
                byName.put(ce.internalName(), classOffsets[ce.token()]);
            }
        }
        patchInternalRefs(byName, methodOffsetMap, staticFieldOffsetMap);
    }

    /**
     * Remaps CP indices in pending internal reference patches after constant pool reordering.
     * Must be called after {@link JcvmConstantPool#reorderInstanceFieldsFirst(Map) reorderInstanceFieldsFirst()}
     * and before {@link #patchInternalRefs(Map, Map, Map)} so that deferred patches target the
     * correct (reordered) entries.
     *
     * @param remap old CP index → new CP index mapping
     */
    public void remapPendingCpIndices(int[] remap) {
        patches.remap(remap);
    }

    /**
     * Returns, for every internal CONSTANT_InstanceFieldref entry, the position of the declaring
     * class in the Class component order of {@link TokenMap#classes()}. Used as the sort key by
     * {@link JcvmConstantPool#reorderInstanceFieldsFirst(Map)}.
     *
     * @return CP index to Class component position of the declaring class
     */
    public Map<Integer, Integer> instanceFieldClassOrder() {
        List<String> order = tokenMap.classes().stream().map(TokenMap.ClassEntry::internalName).toList();
        return patches.instanceFieldKeys(order::indexOf);
    }

    /**
     * Returns, for every internal CONSTANT_InstanceFieldref entry, the class token of the declaring
     * class.
     *
     * @return CP index to class token
     * @deprecated package-visible classes share the token value 0xFF (JCVM 3.1 §4.3.7.2); use
     *             {@link #instanceFieldClassOrder()}
     */
    @Deprecated
    public Map<Integer, Integer> getInstanceFieldClassTokens() {
        return patches.instanceFieldKeys(tokenMap::classToken);
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
     * Resolves a class reference to its {@code class_ref} value (JCVM 3.1 §6.8.1), as used outside
     * the constant pool (Class and Descriptor components):
     * <ul>
     *   <li>Internal: the offset of the class or interface in the Class component, available once
     *       {@link #patchInternalRefs(Map, Map, Map)} has been called</li>
     *   <li>External: {@code (0x80 | package_token) << 8 | class_token}</li>
     * </ul>
     *
     * @param internalName class name in internal format
     * @return the class_ref value
     * @throws IllegalStateException if an internal class is requested before its Class component
     *                               offset is known
     */
    public int resolveClassRefDirect(String internalName) {
        if (isCurrentPackage(internalName)) {
            Integer offset = classOffsets.get(internalName);
            if (offset == null) {
                throw new IllegalStateException("Class component offset of " + internalName
                        + " is not known yet (JCVM 3.1 §6.8.1: internal class_ref is an offset)");
            }
            return offset;
        }

        ImportedPackage pkg = findImportedPackage(internalName);
        markPackageReferenced(pkg.token());
        String simpleName = simpleName(internalName);
        ExportFile.ClassExport classExport = pkg.exportFile().findClass(simpleName);
        // External: 0x80 | pkg_token in high byte, class_token in low byte
        return ((0x80 | pkg.token()) << 8) | classExport.token();
    }

    /**
     * Returns the methods of an interface with their interface method tokens.
     *
     * @param internalName interface name in internal format
     * @return method entries, empty if the interface has no methods
     * @deprecated the Class component now derives interface tables from {@link TokenMap} and
     *             {@link #importedTypes()}; kept for source compatibility
     */
    @Deprecated
    public List<TokenMap.MethodEntry> getInterfaceMethods(String internalName) {
        return isCurrentPackage(internalName)
                ? tokenMap.findClass(internalName).virtualMethods()
                : importedTypes.virtualMethods(internalName);
    }

    /**
     * Returns the number of public virtual method tokens of a class (largest public token + 1).
     *
     * @param internalName class name in internal format
     * @return largest public virtual method token plus one, 0 if none
     * @deprecated no longer used by the converter; kept for source compatibility
     */
    @Deprecated
    public int getVirtualMethodCount(String internalName) {
        List<TokenMap.MethodEntry> methods = isCurrentPackage(internalName)
                ? tokenMap.findClass(internalName).virtualMethods()
                : importedTypes.virtualMethods(internalName);
        return methods.stream().mapToInt(TokenMap.MethodEntry::token)
                .filter(t -> (t & 0x80) == 0).max().orElse(-1) + 1;
    }

    /**
     * Invocation kind for method reference resolution, corresponding to JVM invoke opcodes.
     *
     * <p>Determines the CP entry tag used in the JCVM constant pool (JCVM 3.1 §6.8):
     * <ul>
     *   <li>{@link #VIRTUAL} produces {@code CONSTANT_VirtualMethodref} (tag 3) entries, encoding
     *       (class_offset, method_token) for internal or (pkg|0x80, class_token, method_token)
     *       for external references; {@link #INTERFACE} produces a class reference plus the
     *       interface method token of the {@code invokeinterface} instruction.</li>
     *   <li>{@link #STATIC}, and {@link #SPECIAL} for constructors and private methods, produce
     *       {@code CONSTANT_StaticMethodref} (tag 6) entries, encoding a Method component offset
     *       for internal or (pkg|0x80, class_token, method_token) for external references.</li>
     *   <li>{@link #SPECIAL} for any other method is a super invocation and produces a
     *       {@code CONSTANT_SuperMethodref} (tag 4) entry (§7.5.55).</li>
     * </ul>
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
}
