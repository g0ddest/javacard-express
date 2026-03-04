package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.cap.AppletComponent;
import name.velikodniy.jcexpress.converter.cap.CapFileWriter;
import name.velikodniy.jcexpress.converter.cap.ClassComponent;
import name.velikodniy.jcexpress.converter.cap.ConstantPoolComponent;
import name.velikodniy.jcexpress.converter.cap.DescriptorComponent;
import name.velikodniy.jcexpress.converter.cap.DirectoryComponent;
import name.velikodniy.jcexpress.converter.cap.ExportComponent;
import name.velikodniy.jcexpress.converter.cap.HeaderComponent;
import name.velikodniy.jcexpress.converter.cap.ImportComponent;
import name.velikodniy.jcexpress.converter.cap.MethodComponent;
import name.velikodniy.jcexpress.converter.cap.RefLocationComponent;
import name.velikodniy.jcexpress.converter.cap.StaticFieldComponent;
import name.velikodniy.jcexpress.converter.check.SubsetChecker;
import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.exp.ExportFileWriter;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.resolve.CpReference;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.BytecodeTranslator;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Clean-room JavaCard CAP file converter.
 *
 * <p>Converts compiled {@code .class} files into a JavaCard CAP file
 * without requiring Oracle's proprietary converter or SDK.
 * Implementation is based entirely on the publicly available
 * JCVM 3.0.5 specification (Chapters 4-6).
 *
 * <h2>Conversion Pipeline</h2>
 *
 * <p>The {@link #convert()} method executes a seven-stage pipeline:
 *
 * <ol>
 *   <li><b>Load</b> -- Scans the classes directory for {@code .class} files belonging to the
 *       target package, reads them via the JDK ClassFile API ({@code java.lang.classfile}),
 *       and loads any import {@code .exp} files (including built-in JavaCard API exports).</li>
 *   <li><b>Subset check</b> -- Validates that all classes conform to the JavaCard language subset
 *       (JCVM spec Chapter 2). Disallowed constructs such as {@code long}, {@code float},
 *       {@code double}, multidimensional arrays, and threads are rejected as
 *       {@link name.velikodniy.jcexpress.converter.check.Violation Violation}s.</li>
 *   <li><b>Token assignment</b> -- Assigns numeric tokens to packages, classes, methods, and
 *       fields as specified in JCVM spec Section 4.3. Tokens are used for linking on the card
 *       and are the basis for the export file format.</li>
 *   <li><b>Reference resolution</b> -- Resolves symbolic constant-pool references from JVM
 *       {@code .class} files into JCVM constant-pool entries (internal and external references).
 *       External references target imported packages by their token and AID.</li>
 *   <li><b>Bytecode translation</b> -- Translates JVM bytecodes into JCVM bytecodes, including
 *       opcode mapping, operand rewriting, and constant-pool index patching. Produces
 *       {@link name.velikodniy.jcexpress.converter.translate.TranslatedMethod TranslatedMethod}
 *       objects containing the translated instruction bytes.</li>
 *   <li><b>CAP generation</b> -- Assembles all 11 CAP components (Header, Directory, Applet,
 *       Import, ConstantPool, Class, Method, StaticField, ReferenceLocation, Export, Descriptor)
 *       into a JAR/ZIP archive following the layout defined in JCVM spec Chapter 6.</li>
 *   <li><b>Export generation</b> -- Produces a binary {@code .exp} file containing the package's
 *       public API tokens, allowing other packages to import and link against it.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 *
 * <p>Instances are created through the {@link #builder() fluent builder API}:
 *
 * <pre>{@code
 * ConverterResult result = Converter.builder()
 *     .classesDirectory(Path.of("target/classes"))
 *     .packageName("com.example")
 *     .packageAid("A00000006212")
 *     .packageVersion(1, 0)
 *     .applet("com.example.WalletApplet", "A0000000621201")
 *     .build()
 *     .convert();
 *
 * byte[] capBytes = result.capFile();
 * byte[] expBytes = result.exportFile();
 * }</pre>
 *
 * <p>If no package AID is provided, one is generated deterministically from the package name
 * using SHA-1: {@code 0xF0 || SHA-1(name)[0:7]}.
 *
 * @see ConverterResult
 * @see ConverterException
 * @see JavaCardVersion
 */
public final class Converter {

    private static final String INIT_METHOD = "<init>";

    private final Path classesDirectory;
    private final String packageName;
    private final byte[] packageAid;
    private final int pkgMajorVersion;
    private final int pkgMinorVersion;
    private final Map<String, byte[]> applets; // className -> AID
    private final List<Path> importExportFiles;
    private final boolean supportInt32;
    private final boolean generateExport;
    private final boolean oracleCompatibility;
    private final JavaCardVersion javaCardVersion;

    private Converter(Builder builder) {
        this.classesDirectory = builder.classesDirectory;
        this.packageName = builder.packageName;
        this.packageAid = builder.packageAid;
        this.pkgMajorVersion = builder.pkgMajorVersion;
        this.pkgMinorVersion = builder.pkgMinorVersion;
        this.applets = Map.copyOf(builder.applets);
        this.importExportFiles = List.copyOf(builder.importExportFiles);
        this.supportInt32 = builder.supportInt32;
        this.generateExport = builder.generateExport;
        this.oracleCompatibility = builder.oracleCompatibility;
        this.javaCardVersion = builder.javaCardVersion;
    }

    /**
     * Creates a new converter builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the conversion pipeline:
     * <ol>
     *   <li>Load — read .class files and .exp imports</li>
     *   <li>Subset check — validate JavaCard compliance</li>
     *   <li>Token assignment — assign numeric tokens</li>
     *   <li>Reference resolution — connect symbolic refs to tokens</li>
     *   <li>Bytecode translation — JVM → JCVM</li>
     *   <li>CAP generation — produce binary components</li>
     *   <li>Export generation — produce .exp file</li>
     * </ol>
     *
     * @return conversion result containing CAP and EXP bytes
     * @throws ConverterException if validation fails or conversion encounters errors
     */
    public ConverterResult convert() throws ConverterException {
        try {
            return doConvert();
        } catch (ConverterException e) {
            throw e;
        } catch (Exception e) {
            throw new ConverterException("Conversion failed: " + e.getMessage(), e);
        }
    }

    // ── Internal records for passing data between pipeline stages ──

    private record ScanResult(PackageInfo packageInfo, List<ImportedPackage> imports) {}

    private record TranslationResult(
            List<TranslatedMethod> allMethods,
            List<CpReference> allCpRefs,
            Map<String, Integer> installMethodIndices,
            Map<String, Integer> methodIndexMap,
            List<ClassInfo> sortedClasses,
            ReferenceResolver resolver,
            JcvmConstantPool cp) {}

    @SuppressWarnings("java:S112") // Private method; Exception caught and wrapped by convert()
    private ConverterResult doConvert() throws Exception {
        // Stage 1-2: Scan classes and validate JavaCard subset compliance
        ScanResult scan = scanAndValidate();

        // Stage 3: Assign numeric tokens to packages, classes, methods, fields
        TokenMap tokenMap = assignTokens(scan);

        // Stage 4-5: Resolve references and translate JVM bytecodes to JCVM
        TranslationResult translation = translateBytecodes(scan, tokenMap);

        // Stage 6: Assemble all CAP components into a ZIP archive
        byte[] capFile = assembleCap(translation, tokenMap);

        // Stage 7: Generate binary export file
        byte[] exportFile = generateExportFile(translation.sortedClasses(), tokenMap);

        return new ConverterResult(capFile, exportFile, List.of(), capFile.length);
    }

    /**
     * Stages 1-2: Scans the classes directory for the target package, loads import
     * export files, and validates JavaCard language subset compliance.
     */
    @SuppressWarnings("java:S112") // Private method; Exception caught and wrapped by convert()
    private ScanResult scanAndValidate() throws Exception {
        // Stage 1: Load
        PackageInfo packageInfo = PackageScanner.scan(classesDirectory, packageName);
        if (packageInfo.classes().isEmpty()) {
            throw new ConverterException("No classes found in package: " + packageName
                    + " (directory: " + classesDirectory + ")");
        }

        List<ImportedPackage> imports = loadImports();

        // Stage 2: Subset check
        List<Violation> violations = SubsetChecker.check(packageInfo.classes());
        if (!violations.isEmpty()) {
            throw new ConverterException("JavaCard subset violations found", violations);
        }

        return new ScanResult(packageInfo, imports);
    }

    /**
     * Stage 3: Assigns numeric tokens to packages, classes, methods, and fields
     * using inheritance-aware resolution of superclass virtual methods.
     */
    private TokenMap assignTokens(ScanResult scan) {
        return TokenAssigner.assign(scan.packageInfo(), className -> {
            String pkg = className.contains("/")
                    ? className.substring(0, className.lastIndexOf('/'))
                    : "";
            for (ImportedPackage imp : scan.imports()) {
                String impPkg = imp.exportFile().packageName().replace('.', '/');
                if (impPkg.equals(pkg)) {
                    String simpleName = className.substring(className.lastIndexOf('/') + 1);
                    try {
                        ExportFile.ClassExport cls = imp.exportFile().findClass(simpleName);
                        return cls.methods().stream()
                                .filter(m -> (m.accessFlags() & 0x0008) == 0) // not ACC_STATIC
                                .filter(m -> !INIT_METHOD.equals(m.name()) && !"<clinit>".equals(m.name()))
                                .map(m -> new TokenMap.MethodEntry(m.name(), m.descriptor(), m.token()))
                                .toList();
                    } catch (NoSuchElementException e) {
                        return List.of();
                    }
                }
            }
            return List.of();
        });
    }

    /**
     * Stages 4-5: Resolves symbolic constant-pool references and translates
     * JVM bytecodes into JCVM bytecodes for all methods in the package.
     */
    @SuppressWarnings({"java:S112", "java:S3776"}) // Private method with Exception caught by convert(); inherently complex bytecode translation
    private TranslationResult translateBytecodes(ScanResult scan, TokenMap tokenMap) throws Exception {
        JcvmConstantPool cp = new JcvmConstantPool();
        ReferenceResolver resolver = new ReferenceResolver(
                tokenMap, scan.imports(), cp, scan.packageInfo().classes());

        List<ClassInfo> sortedClasses = sortByTokenOrder(scan.packageInfo().classes(), tokenMap);

        // Oracle creates CP entries by processing the applet class first, then other classes
        // in token order. This produces different CP index assignments for multi-class packages.
        // We translate methods in CP order (applet first), then rearrange the method list
        // to token order for the Method component layout.
        List<ClassInfo> cpOrderClasses = sortByCpOrder(sortedClasses);
        boolean optimizePutfieldThis = javaCardVersion.ordinal() >= JavaCardVersion.V3_0_5.ordinal();

        // Phase 1: translate all methods in CP order (creates CP entries in Oracle's order).
        // Oracle processes constructors depth-first: when translating class A's constructor
        // and encountering invokespecial B.<init>, it immediately translates B's constructor
        // before continuing with A. After all constructors, remaining methods are processed.
        record MethodEntry(String key, TranslatedMethod method, String className,
                           String methodName, String methodDesc) {}
        // Parse all class files first (avoid redundant parsing)
        record ParsedClass(ClassInfo info, ClassModel model) {}
        Map<String, ParsedClass> parsedClassMap = new LinkedHashMap<>();
        for (ClassInfo ci : cpOrderClasses) {
            byte[] classBytes = readClassFile(ci);
            parsedClassMap.put(ci.thisClass(), new ParsedClass(ci, ClassFile.of().parse(classBytes)));
        }
        // Translate and store results keyed by method signature
        Map<String, MethodEntry> translatedByKey = new LinkedHashMap<>();
        Set<String> initDone = new HashSet<>();

        // Set up depth-first constructor chaining callback: when the resolver creates
        // an ISM for an internal <init>, immediately translate that class's constructor
        resolver.setOnInternalInitCreated(targetClass -> {
            if (initDone.contains(targetClass) || !parsedClassMap.containsKey(targetClass)) return;
            initDone.add(targetClass);
            ParsedClass target = parsedClassMap.get(targetClass);
            String savedClass = resolver.getCurrentClass();
            resolver.setCurrentClass(targetClass);
            for (MethodModel mm : target.model().methods()) {
                if (!INIT_METHOD.equals(mm.methodName().stringValue())) continue;
                String n = mm.methodName().stringValue();
                String d = mm.methodType().stringValue();
                String k = targetClass + ":" + n + ":" + d;
                TranslatedMethod tm = BytecodeTranslator.translate(
                        mm, target.model(), resolver, supportInt32, optimizePutfieldThis);
                translatedByKey.put(k, new MethodEntry(k, tm, targetClass, n, d));
            }
            resolver.setCurrentClass(savedClass);
        });

        // Pass 1: constructors in CP order (depth-first chaining via callback)
        for (ParsedClass pc : parsedClassMap.values()) {
            if (initDone.contains(pc.info().thisClass())) continue;
            initDone.add(pc.info().thisClass());
            resolver.setCurrentClass(pc.info().thisClass());
            for (MethodModel mm : pc.model().methods()) {
                if (!INIT_METHOD.equals(mm.methodName().stringValue())) continue;
                String name = mm.methodName().stringValue();
                String desc = mm.methodType().stringValue();
                String key = pc.info().thisClass() + ":" + name + ":" + desc;
                TranslatedMethod tm = BytecodeTranslator.translate(
                        mm, pc.model(), resolver, supportInt32, optimizePutfieldThis);
                translatedByKey.put(key, new MethodEntry(key, tm, pc.info().thisClass(), name, desc));
            }
        }
        resolver.setOnInternalInitCreated(null); // disable callback for non-constructor methods

        // Pass 2: remaining methods of all classes (in CP order)
        for (ParsedClass pc : parsedClassMap.values()) {
            resolver.setCurrentClass(pc.info().thisClass());
            for (MethodModel mm : pc.model().methods()) {
                if (INIT_METHOD.equals(mm.methodName().stringValue())) continue;
                String name = mm.methodName().stringValue();
                String desc = mm.methodType().stringValue();
                String key = pc.info().thisClass() + ":" + name + ":" + desc;
                TranslatedMethod tm = BytecodeTranslator.translate(
                        mm, pc.model(), resolver, supportInt32, optimizePutfieldThis);
                translatedByKey.put(key, new MethodEntry(key, tm, pc.info().thisClass(), name, desc));
            }
        }
        // Build methodsByClass in .class file order (for correct Method component layout)
        Map<String, List<MethodEntry>> methodsByClass = new LinkedHashMap<>();
        for (ParsedClass pc : parsedClassMap.values()) {
            List<MethodEntry> classMethods = new ArrayList<>();
            for (MethodModel mm : pc.model().methods()) {
                String key = pc.info().thisClass() + ":" + mm.methodName().stringValue()
                        + ":" + mm.methodType().stringValue();
                classMethods.add(translatedByKey.get(key));
            }
            methodsByClass.put(pc.info().thisClass(), classMethods);
        }

        // Recompute import encounter order based on token order (not CP order).
        // Oracle tracks import encounters in token order even when building CP
        // entries in applet-first order.
        List<String> tokenOrderNames = sortedClasses.stream()
                .map(ClassInfo::thisClass).toList();
        resolver.recomputeEncounterOrder(tokenOrderNames);

        // Phase 2: assemble method list in token order (for Method component layout)
        List<TranslatedMethod> allMethods = new ArrayList<>();
        List<CpReference> allCpRefs = new ArrayList<>();
        Map<String, Integer> installMethodIndices = new HashMap<>();
        Map<String, Integer> methodIndexMap = new LinkedHashMap<>();
        int methodIndex = 0;
        for (ClassInfo ci : sortedClasses) {
            List<MethodEntry> classMethods = methodsByClass.get(ci.thisClass());
            for (MethodEntry me : classMethods) {
                methodIndexMap.put(me.key(), methodIndex);
                allMethods.add(me.method());
                allCpRefs.addAll(me.method().cpReferences());
                if ("install".equals(me.methodName())
                        && "([BSB)V".equals(me.methodDesc())
                        && applets.containsKey(me.className().replace('/', '.'))) {
                    installMethodIndices.put(me.className().replace('/', '.'), methodIndex);
                }
                methodIndex++;
            }
        }

        return new TranslationResult(
                allMethods, allCpRefs, installMethodIndices, methodIndexMap,
                sortedClasses, resolver, cp);
    }

    /**
     * Determines the CP building order: applet classes first, then non-applet classes.
     * Oracle's converter processes the applet class first when creating constant pool entries,
     * resulting in the applet's CP references appearing at lower indices.
     *
     * @param tokenOrderClasses classes sorted by token (the normal layout order)
     * @return classes reordered for CP entry creation
     */
    @SuppressWarnings("java:S3776") // Inherently complex CP ordering logic
    private List<ClassInfo> sortByCpOrder(List<ClassInfo> tokenOrderClasses) {
        if (tokenOrderClasses.size() <= 1) return tokenOrderClasses;

        // Build set of internal class names for inheritance check
        Set<String> internalNames = new HashSet<>();
        for (ClassInfo ci : tokenOrderClasses) {
            internalNames.add(ci.thisClass());
        }

        // Identify applet classes whose superclass is EXTERNAL (not in this package).
        // Only move these to the front — applet classes in an inheritance chain (extending
        // internal classes) must stay in topological order to preserve correct CP building.
        Set<String> appletClassNames = new HashSet<>();
        for (String name : applets.keySet()) {
            appletClassNames.add(name.replace('.', '/'));
        }

        Set<String> movableApplets = new HashSet<>();
        for (ClassInfo ci : tokenOrderClasses) {
            if (appletClassNames.contains(ci.thisClass())) {
                boolean superIsInternal = ci.superClass() != null
                        && internalNames.contains(ci.superClass());
                if (!superIsInternal) {
                    movableApplets.add(ci.thisClass());
                }
            }
        }

        if (movableApplets.isEmpty()) return tokenOrderClasses;

        List<ClassInfo> result = new ArrayList<>(tokenOrderClasses.size());
        // Applet classes first (only those with external superclass)
        for (ClassInfo ci : tokenOrderClasses) {
            if (movableApplets.contains(ci.thisClass())) result.add(ci);
        }
        // Then rest in token order
        for (ClassInfo ci : tokenOrderClasses) {
            if (!movableApplets.contains(ci.thisClass())) result.add(ci);
        }
        return result;
    }

    /**
     * Reads the .class file bytes for the given class, trying the dot-to-slash path first
     * and falling back to the raw internal name if the primary path does not exist.
     */
    private byte[] readClassFile(ClassInfo ci) throws IOException {
        Path primaryPath = classesDirectory.resolve(
                ci.thisClass().replace('.', '/') + ".class");
        try {
            return Files.readAllBytes(primaryPath);
        } catch (NoSuchFileException e) {
            // Fall back to slash notation (thisClass may already use slashes)
            Path fallbackPath = classesDirectory.resolve(ci.thisClass() + ".class");
            return Files.readAllBytes(fallbackPath);
        }
    }

    /**
     * Stage 6: Assembles all CAP components (Header, Directory, Applet, Import,
     * ConstantPool, Class, Method, StaticField, ReferenceLocation, Export, Descriptor)
     * into a JAR/ZIP archive.
     */
    @SuppressWarnings("java:S3776") // Inherently complex CAP assembly with 11 components
    private byte[] assembleCap(TranslationResult translation, TokenMap tokenMap) throws IOException {
        // Finalize imports: remove unreferenced packages, reassign tokens, remap CP
        List<ImportedPackage> finalImports = translation.resolver().finalizeImports(javaCardVersion);

        // Reorder CP entries: instance field refs first, sorted by class token
        // (matches Oracle's ordering). Must happen before MethodComponent.generate()
        // reads bytecode, and before patchInternalRefs() which uses CP indices.
        Map<Integer, Integer> fieldClassTokens = translation.resolver().getInstanceFieldClassTokens();
        int[] cpRemap = translation.cp().reorderInstanceFieldsFirst(fieldClassTokens);
        if (cpRemap.length > 0) {
            remapCpIndicesInBytecode(translation.allMethods(), cpRemap);
            translation.resolver().remapPendingCpIndices(cpRemap);
            translation.resolver().remapCpTypeDescriptors(cpRemap);
        }

        // Generate Method component first -- we need offsets for everything else
        MethodComponent.MethodResult methodResult = MethodComponent.generate(translation.allMethods());
        byte[] methodBytes = methodResult.bytes();
        int[] methodOffsets = methodResult.offsets();

        // Generate StaticField component -- we need field offsets for CP patching
        StaticFieldComponent.StaticFieldResult staticFieldResult =
                StaticFieldComponent.generate(translation.sortedClasses());
        byte[] staticFieldBytes = staticFieldResult.bytes();

        // Generate Class component -- returns class byte offsets for CP patching
        ClassComponent.ClassResult classResult = ClassComponent.generate(
                translation.sortedClasses(), tokenMap, methodOffsets,
                translation.methodIndexMap(), translation.resolver(), javaCardVersion,
                oracleCompatibility);
        byte[] classBytes2 = classResult.bytes();
        int[] classOffsets = classResult.classOffsets();

        // Build method offset map: "className:name:desc" -> Method component offset
        Map<String, Integer> methodOffsetMap = new HashMap<>();
        for (var entry : translation.methodIndexMap().entrySet()) {
            int idx = entry.getValue();
            if (idx < methodOffsets.length) {
                methodOffsetMap.put(entry.getKey(), methodOffsets[idx]);
            }
        }

        // Patch internal CP references with real component offsets
        translation.resolver().patchInternalRefs(
                classOffsets, methodOffsetMap, staticFieldResult.fieldOffsetMap());

        // Now generate ConstantPool component (after patching!)
        byte[] cpBytes = ConstantPoolComponent.generate(translation.cp());

        // Build Applet component
        byte[] appletBytes = null;
        if (!applets.isEmpty()) {
            List<AppletComponent.AppletEntry> appletEntries = new ArrayList<>();
            for (var entry : applets.entrySet()) {
                int installIdx = translation.installMethodIndices().getOrDefault(entry.getKey(), 0);
                int installOffset = methodOffsets.length > installIdx
                        ? methodOffsets[installIdx] : 0;
                appletEntries.add(new AppletComponent.AppletEntry(entry.getValue(), installOffset));
            }
            appletBytes = AppletComponent.generate(appletEntries);
        }

        byte[] importBytes = ImportComponent.generate(finalImports);

        // Build absolute CpReferences for RefLocation component.
        // Per JCVM spec §6.11, offsets are absolute within the Method component info area
        // (after tag+size header), not relative to each method's bytecode start.
        List<CpReference> absoluteRefs = buildAbsoluteRefLocations(
                translation.allMethods(), methodOffsets);
        byte[] refLocationBytes = RefLocationComponent.generate(absoluteRefs);

        // Build class_ref resolver for Descriptor component type nibbles:
        // Internal classes → ClassComponent byte offset; External → (0x80|pkg)<<8|class
        java.util.function.Function<String, Integer> classRefResolver = className -> {
            try {
                int token = tokenMap.classToken(className);
                if (token < classOffsets.length) {
                    return classOffsets[token];
                }
            } catch (Exception ignored) {
                // Not in current package — fall through to external resolution
            }
            return translation.resolver().resolveClassRefDirect(className);
        };

        byte[] descriptorBytes = DescriptorComponent.generate(
                translation.sortedClasses(), tokenMap, methodOffsets,
                translation.methodIndexMap(), classOffsets,
                translation.allMethods(), translation.cp(),
                translation.resolver().cpTypeDescriptors(),
                classRefResolver);

        // Build flags
        int flags = 0;
        if (supportInt32) flags |= HeaderComponent.ACC_INT;
        if (generateExport) flags |= HeaderComponent.ACC_EXPORT;
        if (!applets.isEmpty()) flags |= HeaderComponent.ACC_APPLET;

        String internalPkgName = packageName.replace('.', '/');
        // Per JCVM spec 6.3, package_name_info is optional.
        // Oracle's converter omits it; we do the same for binary compatibility.
        byte[] headerBytes = HeaderComponent.generate(
                packageAid, pkgMajorVersion, pkgMinorVersion, flags, null,
                javaCardVersion);

        // Export component (optional)
        byte[] exportComponentBytes = null;
        if (generateExport) {
            exportComponentBytes = ExportComponent.generate(
                    tokenMap, methodOffsets, classOffsets, translation.methodIndexMap(),
                    staticFieldResult.fieldOffsetMap());
        }

        // Calculate component body sizes for Directory per JCVM spec 6.5:
        // component_sizes stores the u2 size field value (body size, excluding 3-byte tag+size header)
        int[] componentSizes = new int[11];
        componentSizes[0] = headerBytes.length - 3;     // Header (tag=1)
        // componentSizes[1] = Directory (tag=2) -- calculated below
        if (appletBytes != null) componentSizes[2] = appletBytes.length - 3;  // Applet (tag=3)
        componentSizes[3] = importBytes.length - 3;     // Import (tag=4)
        componentSizes[4] = cpBytes.length - 3;         // ConstantPool (tag=5)
        componentSizes[5] = classBytes2.length - 3;     // Class (tag=6)
        componentSizes[6] = methodBytes.length - 3;     // Method (tag=7)
        componentSizes[7] = staticFieldBytes.length - 3; // StaticField (tag=8)
        componentSizes[8] = refLocationBytes.length - 3; // RefLocation (tag=9)
        if (exportComponentBytes != null) componentSizes[9] = exportComponentBytes.length - 3; // Export (tag=10)
        componentSizes[10] = descriptorBytes.length - 3; // Descriptor (tag=11)

        // First pass: generate directory to calculate its size
        byte[] directoryBytes = DirectoryComponent.generate(
                componentSizes,
                staticFieldResult.imageSize(),
                staticFieldResult.arrayInitCount(),
                staticFieldResult.arrayInitSize(),
                finalImports.size(), applets.size(), javaCardVersion);
        // Second pass: now we know the directory size, regenerate with correct self-size
        componentSizes[1] = directoryBytes.length - 3;
        directoryBytes = DirectoryComponent.generate(
                componentSizes,
                staticFieldResult.imageSize(),
                staticFieldResult.arrayInitCount(),
                staticFieldResult.arrayInitSize(),
                finalImports.size(), applets.size(), javaCardVersion);

        // Assemble CAP ZIP
        Map<Integer, byte[]> components = new LinkedHashMap<>();
        components.put(1, headerBytes);
        components.put(2, directoryBytes);
        if (appletBytes != null) components.put(3, appletBytes);
        components.put(4, importBytes);
        components.put(5, cpBytes);
        components.put(6, classBytes2);
        components.put(7, methodBytes);
        components.put(8, staticFieldBytes);
        components.put(9, refLocationBytes);
        if (exportComponentBytes != null) components.put(10, exportComponentBytes);
        components.put(11, descriptorBytes);

        return CapFileWriter.write(internalPkgName, components);
    }

    /**
     * Stage 7: Generates a binary export file (.exp) containing the package's
     * public API tokens for downstream package linking.
     */
    private byte[] generateExportFile(List<ClassInfo> sortedClasses, TokenMap tokenMap) throws IOException {
        return ExportFileWriter.write(
                tokenMap, sortedClasses, packageAid, pkgMajorVersion, pkgMinorVersion);
    }

    private List<ImportedPackage> loadImports() throws IOException {
        List<ImportedPackage> result = new ArrayList<>();
        int token = 0;

        // Load built-in JavaCard API exports (version-aware API versions)
        result.addAll(BuiltinExports.allBuiltinImports(token, javaCardVersion));
        token = result.size();

        // Load user-provided export files
        for (Path expPath : importExportFiles) {
            ExportFile ef = ExportFileReader.readFile(expPath);
            result.add(new ImportedPackage(
                    token++,
                    ef.aid(),
                    ef.majorVersion(),
                    ef.minorVersion(),
                    ef));
        }

        return result;
    }

    /**
     * Builds absolute CpReference positions within the Method component info area.
     *
     * <p>Per JCVM spec §6.11, the RefLocation component records byte positions of all
     * CP references as absolute offsets from the start of the Method component's info
     * (after the tag+size header). This includes:
     * <ul>
     *   <li>CP indices in exception handler {@code catch_type_index} fields</li>
     *   <li>CP indices embedded in bytecode instructions</li>
     * </ul>
     *
     * @param methods       all translated methods in order
     * @param methodOffsets per-method byte offsets within Method component info
     * @return list of CpReferences with absolute offsets
     */
    private static List<CpReference> buildAbsoluteRefLocations(
            List<TranslatedMethod> methods, int[] methodOffsets) {
        List<CpReference> result = new ArrayList<>();

        // 1. Exception handler catch_type_index references.
        // Handler table starts at offset 0: handler_count(u1) + handler_info[](8 bytes each).
        // catch_type_index is at offset 1 + h*8 + 6 within the info area.
        int handlerIdx = 0;
        for (TranslatedMethod m : methods) {
            for (var handler : m.exceptionHandlers()) {
                if (handler.catchTypeIndex() != 0) {
                    int absOffset = 1 + handlerIdx * 8 + 6;
                    result.add(new CpReference(absOffset, handler.catchTypeIndex(), 2));
                }
                handlerIdx++;
            }
        }

        // 2. Bytecode CP references — adjust from per-method to absolute offsets.
        for (int i = 0; i < methods.size(); i++) {
            TranslatedMethod m = methods.get(i);
            if (m.bytecode().length == 0) continue;

            // Header size: 2 bytes for standard, 4 bytes for extended
            int headerSize = m.isExtended() ? 4 : 2;
            int bytecodeBase = methodOffsets[i] + headerSize;

            for (CpReference ref : m.cpReferences()) {
                result.add(new CpReference(
                        bytecodeBase + ref.bytecodeOffset(),
                        ref.cpIndex(),
                        ref.indexSize()));
            }
        }

        return result;
    }

    /**
     * Patches CP index operands in all translated method bytecodes after CP reordering.
     * Uses each method's {@link CpReference} list to locate and rewrite CP indices.
     *
     * @param methods all translated methods
     * @param remap   old CP index → new CP index mapping
     */
    private static void remapCpIndicesInBytecode(List<TranslatedMethod> methods, int[] remap) {
        for (TranslatedMethod tm : methods) {
            byte[] bytecode = tm.bytecode();
            for (CpReference ref : tm.cpReferences()) {
                int offset = ref.bytecodeOffset();
                int oldIndex = ref.cpIndex();
                int newIndex = remap[oldIndex];
                if (oldIndex == newIndex) continue;

                if (ref.indexSize() == 1) {
                    bytecode[offset] = (byte) newIndex;
                } else {
                    bytecode[offset] = (byte) (newIndex >> 8);
                    bytecode[offset + 1] = (byte) newIndex;
                }
            }
        }
    }

    private static List<ClassInfo> sortByTokenOrder(List<ClassInfo> classes, TokenMap tokenMap) {
        List<ClassInfo> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparingInt(ci -> tokenMap.classToken(ci.thisClass())));
        return sorted;
    }

    // ── Builder ──

    /**
     * Fluent builder for configuring a {@link Converter} instance.
     *
     * <p>Required parameters:
     * <ul>
     *   <li>{@link #classesDirectory(Path)} -- directory containing compiled {@code .class} files</li>
     *   <li>{@link #packageName(String)} -- fully qualified Java package name (dot notation)</li>
     * </ul>
     *
     * <p>Optional parameters (with defaults):
     * <ul>
     *   <li>{@link #packageAid(String)} -- auto-generated from package name if omitted</li>
     *   <li>{@link #packageVersion(int, int)} -- defaults to 1.0</li>
     *   <li>{@link #applet(String, String)} -- at least one applet should be registered for installable packages</li>
     *   <li>{@link #importExportFile(Path)} -- additional {@code .exp} files for external package imports</li>
     *   <li>{@link #supportInt32(boolean)} -- defaults to {@code false}</li>
     *   <li>{@link #generateExport(boolean)} -- defaults to {@code false}</li>
     *   <li>{@link #javaCardVersion(JavaCardVersion)} -- defaults to {@link JavaCardVersion#V3_0_5}</li>
     * </ul>
     */
    public static final class Builder {
        private Path classesDirectory;
        private String packageName;
        private byte[] packageAid;
        private int pkgMajorVersion = 1;
        private int pkgMinorVersion = 0;
        private final Map<String, byte[]> applets = new LinkedHashMap<>();
        private final List<Path> importExportFiles = new ArrayList<>();
        private boolean supportInt32 = false;
        private boolean generateExport = false;
        private boolean oracleCompatibility = false;
        private JavaCardVersion javaCardVersion = JavaCardVersion.V3_0_5;

        private Builder() {}

        /**
         * Sets the directory containing compiled .class files.
         */
        public Builder classesDirectory(Path dir) {
            this.classesDirectory = dir;
            return this;
        }

        /**
         * Sets the Java package name (dot notation, e.g. "com.example").
         */
        public Builder packageName(String name) {
            this.packageName = name;
            return this;
        }

        /**
         * Sets the package AID from raw bytes.
         */
        public Builder packageAid(byte[] aid) {
            this.packageAid = aid.clone();
            return this;
        }

        /**
         * Sets the package AID from a hex string (e.g. "A00000006212").
         */
        public Builder packageAid(String hexAid) {
            this.packageAid = hexToBytes(hexAid);
            return this;
        }

        /**
         * Sets the package version.
         */
        public Builder packageVersion(int major, int minor) {
            this.pkgMajorVersion = major;
            this.pkgMinorVersion = minor;
            return this;
        }

        /**
         * Registers an applet with explicit AID.
         *
         * @param className fully qualified class name (dot notation)
         * @param aid       applet AID bytes
         */
        public Builder applet(String className, byte[] aid) {
            this.applets.put(className, aid.clone());
            return this;
        }

        /**
         * Registers an applet with AID from hex string.
         */
        public Builder applet(String className, String hexAid) {
            this.applets.put(className, hexToBytes(hexAid));
            return this;
        }

        /**
         * Adds an import export file (.exp) for resolving external references.
         */
        public Builder importExportFile(Path expFile) {
            this.importExportFiles.add(expFile);
            return this;
        }

        /**
         * Enables 32-bit integer support (ACC_INT flag in CAP header).
         */
        public Builder supportInt32(boolean flag) {
            this.supportInt32 = flag;
            return this;
        }

        /**
         * Enables generation of the Export component in the CAP file.
         */
        public Builder generateExport(boolean flag) {
            this.generateExport = flag;
            return this;
        }

        /**
         * Sets the target JavaCard specification version.
         * Controls the CAP format version in the Header component and
         * API version numbers in import references.
         * Defaults to {@link JavaCardVersion#V3_0_5}.
         */
        public Builder javaCardVersion(JavaCardVersion version) {
            this.javaCardVersion = Objects.requireNonNull(version);
            return this;
        }

        /**
         * Enables Oracle compatibility mode for the Class component dispatch table.
         *
         * <p>When {@code true}, replicates the Oracle converter's off-by-one behavior
         * in the {@code public_virtual_method_table} serialization (JCVM spec §6.9
         * Table 6-16), producing byte-identical Class component output. This is useful
         * for environments that validate CAP files against Oracle reference output.
         *
         * <p>Defaults to {@code false} (spec-correct output).
         *
         * @param flag {@code true} to enable Oracle compatibility mode
         * @return this builder
         * @see <a href="BINARY_COMPATIBILITY.md">Binary Compatibility documentation</a>
         */
        public Builder oracleCompatibility(boolean flag) {
            this.oracleCompatibility = flag;
            return this;
        }

        /**
         * Builds the converter with the configured settings.
         * If no package AID is set, generates one automatically from the package name.
         *
         * @throws IllegalArgumentException if required parameters are missing
         */
        public Converter build() {
            Objects.requireNonNull(classesDirectory, "classesDirectory is required");
            Objects.requireNonNull(packageName, "packageName is required");

            if (packageAid == null) {
                packageAid = generateAid(packageName);
            }

            return new Converter(this);
        }

        /**
         * Generates a deterministic AID from package name using SHA-1:
         * {@code 0xF0 || SHA-1(packageName)[0:7]}
         */
        @SuppressWarnings("java:S4790") // SHA-1 used for deterministic AID generation, not security
        static byte[] generateAid(String packageName) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] hash = md.digest(packageName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                byte[] aid = new byte[8];
                aid[0] = (byte) 0xF0;
                System.arraycopy(hash, 0, aid, 1, 7);
                return aid;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-1 not available", e);
            }
        }

        static byte[] hexToBytes(String hex) {
            hex = hex.replace(" ", "").replace(":", "");
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        }
    }
}
