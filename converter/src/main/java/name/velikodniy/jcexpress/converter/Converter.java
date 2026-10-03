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
import name.velikodniy.jcexpress.converter.clinit.ClinitInterpreter;
import name.velikodniy.jcexpress.converter.clinit.StaticValue;
import name.velikodniy.jcexpress.converter.exp.ExportFileWriter;
import name.velikodniy.jcexpress.converter.exp.ExportInput;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences;
import name.velikodniy.jcexpress.converter.resolve.CpReference;
import name.velikodniy.jcexpress.converter.resolve.ExportedTypes;
import name.velikodniy.jcexpress.converter.resolve.ImportLoader;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.resolve.LinkChecker;
import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;
import name.velikodniy.jcexpress.converter.resolve.StructuralReferences;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenAssignmentException;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.BytecodeTranslator;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *       target package, reads them via the JDK ClassFile API ({@code java.lang.classfile}), inlines
 *       {@code jsr}/{@code ret} subroutines (JCVM 3.1 §2.3.2.2) and loads any import {@code .exp}
 *       files (including built-in JavaCard API exports).</li>
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
    private final boolean packageAidGenerated;
    private final int pkgMajorVersion;
    private final int pkgMinorVersion;
    private final Map<String, byte[]> applets; // className -> AID
    private final List<Path> importExportFiles;
    private final List<Path> exportPath;
    private final boolean supportInt32;
    private final boolean generateExport;
    private final JavaCardVersion javaCardVersion;

    private Converter(Builder builder) {
        this.classesDirectory = builder.classesDirectory;
        this.packageName = builder.packageName;
        // no AID configured: a deterministic one is generated from the package name (warned about)
        this.packageAidGenerated = builder.packageAid == null;
        this.packageAid = packageAidGenerated ? Builder.generateAid(builder.packageName) : builder.packageAid.clone();
        this.pkgMajorVersion = builder.pkgMajorVersion;
        this.pkgMinorVersion = builder.pkgMinorVersion;
        // registration order is kept: it is the order of the Applet component (deterministic output)
        this.applets = Collections.unmodifiableMap(new LinkedHashMap<>(builder.applets));
        this.importExportFiles = List.copyOf(builder.importExportFiles);
        this.exportPath = List.copyOf(builder.exportPath);
        this.supportInt32 = builder.supportInt32;
        this.generateExport = builder.generateExport;
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

    private record ScanResult(PackageInfo packageInfo, List<ImportedPackage> imports,
                              List<ClassReferences.Reference> references,
                              List<AppletRules.AppletDefinition> applets, List<String> warnings,
                              Map<String, byte[]> classFiles) {}

    private record TranslationResult(
            List<TranslatedMethod> allMethods,
            List<CpReference> allCpRefs,
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

        // Stage 3b: Evaluate <clinit> methods into static field initial values (JCVM 3.1 §2.2.4.6)
        Map<String, StaticValue> staticInit = interpretStaticInitializers(scan);

        // Stage 4-5: Resolve references and translate JVM bytecodes to JCVM
        TranslationResult translation = translateBytecodes(scan, tokenMap);

        // Stage 6: Assemble all CAP components into a ZIP archive
        byte[] capFile = assembleCap(translation, tokenMap, scan, staticInit);

        // Stage 7: Generate binary export file
        byte[] exportFile = generateExportFile(scan, tokenMap);

        return new ConverterResult(capFile, exportFile, scan.warnings(), capFile.length);
    }

    /**
     * Stages 1-2: Scans the classes directory for the target package, inlines jsr/ret subroutines (JCVM 3.1
     * §2.3.2.2), loads import export files, and validates JavaCard language subset compliance.
     */
    @SuppressWarnings("java:S112") // Private method; Exception caught and wrapped by convert()
    private ScanResult scanAndValidate() throws Exception {
        Map<String, byte[]> classFiles = SubroutineInliner.inline(
                PackageScanner.classFiles(classesDirectory, packageName));
        return scanAndValidate(checkSubset(PackageScanner.read(packageName, classFiles.values())), classFiles, true);
    }

    @SuppressWarnings("java:S112") // Private method; Exception caught and wrapped by convert()
    private ScanResult scanAndValidate(PackageInfo packageInfo, Map<String, byte[]> classFiles,
                                       boolean declareAbstractMethods) throws Exception {
        // Imports: explicit export files, export path, built-in API of the target (JCVM 3.1 §4.3.3);
        // every external reference must be linkable against them (§4.3.5, §4.5.2)
        List<ClassReferences.Reference> references = ClassReferences.scan(List.copyOf(classFiles.values()));
        Set<String> referencedPackages = ClassReferences.packages(references, packageInfo.internalName());
        List<ImportedPackage> imports = ImportLoader.load(javaCardVersion, importExportFiles, exportPath,
                referencedPackages);
        // §6.9.2.5: abstract classes declare the interface methods they leave to subclasses; scan again
        Map<String, byte[]> completed = declareAbstractMethods
                ? AbstractMethodDeclarations.complete(packageInfo, classFiles, new ExportedTypes(imports)) : classFiles;
        if (completed != classFiles) {
            PackageInfo completedInfo = checkSubset(PackageScanner.read(packageName, completed.values()));
            return scanAndValidate(completedInfo, completed, false);
        }
        LinkChecker.check(references, imports, packageInfo.classes(), javaCardVersion);
        RemoteTypeRules.check(packageInfo, new PackageHierarchy(packageInfo, imports)); // §2.2.6: no RMI

        // Applets and AIDs (JCVM 3.1 §4.2, §6.6)
        List<String> warnings = new ArrayList<>(AidRules.check(
                packageInfo.internalName(), packageAid, applets, imports, referencedPackages));
        if (packageAidGenerated) warnings.add(AidRules.generatedAidWarning(packageAid));
        List<AppletRules.AppletDefinition> appletDefinitions = AppletRules.check(applets, packageInfo, imports);

        return new ScanResult(packageInfo, imports, references, appletDefinitions, List.copyOf(warnings),
                classFiles);
    }

    /**
     * Stage 2: Checks the classes of the package against the Java Card language subset (JCVM 3.1
     * §2.2; the int rules of §2.2.3.1 depend on int support).
     *
     * @throws ConverterException if the package has no classes or violates the subset
     */
    private PackageInfo checkSubset(PackageInfo packageInfo) throws ConverterException {
        if (packageInfo.classes().isEmpty()) {
            throw new ConverterException("No classes found in package: " + packageName
                    + " (directory: " + classesDirectory + ")");
        }
        List<Violation> violations = SubsetChecker.check(packageInfo.classes(), supportInt32);
        if (!violations.isEmpty()) {
            throw new ConverterException("JavaCard subset violations found", violations);
        }
        return packageInfo;
    }

    /**
     * Stage 3: Assigns the tokens of JCVM 3.1 §4.3.7 to the package's classes, methods and fields,
     * using the export files of the imported packages for inherited virtual method tokens and
     * superinterfaces.
     *
     * @throws ConverterException if the package cannot be represented with tokens (token ranges
     *                            of Table 4-2, forbidden overrides, interfaces with code)
     */
    private TokenMap assignTokens(ScanResult scan) throws ConverterException {
        try {
            return TokenAssigner.assign(scan.packageInfo(), new ExportedTypes(scan.imports()));
        } catch (TokenAssignmentException e) {
            throw new ConverterException("Token assignment failed (JCVM 3.1 §4.3.7)", e.violations());
        }
    }

    /**
     * Stage 3b: Evaluates every {@code <clinit>} method; a Java Card VM never runs them, so their
     * effect becomes part of the Static Field component (JCVM 3.1 §2.2.4.6, §6.11).
     *
     * @return {@code "class:field"} to the value assigned by {@code <clinit>}
     * @throws ConverterException if a static initializer does more than §2.2.4.6 allows
     */
    private Map<String, StaticValue> interpretStaticInitializers(ScanResult scan) throws ConverterException {
        Map<String, StaticValue> values = new HashMap<>();
        List<Violation> violations = new ArrayList<>();
        for (ClassInfo ci : scan.packageInfo().classes()) {
            ClassModel model = ClassFile.of().parse(scan.classFiles().get(ci.thisClass()));
            ClinitInterpreter.interpret(model, applets.isEmpty(), violations)
                    .forEach((field, value) -> values.put(ci.thisClass() + ":" + field, value));
        }
        if (!violations.isEmpty()) {
            throw new ConverterException("Unsupported static field initialization", violations);
        }
        return values;
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

        List<ClassInfo> sortedClasses = componentOrder(scan.packageInfo().classes(), tokenMap);

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
            byte[] classBytes = scan.classFiles().get(ci.thisClass());
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
            for (MethodModel mm : methodComponentMembers(pc.info(), pc.model())) {
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
            for (MethodModel mm : methodComponentMembers(pc.info(), pc.model())) {
                String key = pc.info().thisClass() + ":" + mm.methodName().stringValue()
                        + ":" + mm.methodType().stringValue();
                MethodEntry me = translatedByKey.get(key);
                if (me == null) {
                    throw new ConverterException("Method not translated: " + key + " (likely uses unsupported type)");
                }
                classMethods.add(me);
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
        Map<String, Integer> methodIndexMap = new LinkedHashMap<>();
        int methodIndex = 0;
        for (ClassInfo ci : sortedClasses) {
            List<MethodEntry> classMethods = methodsByClass.get(ci.thisClass());
            for (MethodEntry me : classMethods) {
                methodIndexMap.put(me.key(), methodIndex);
                allMethods.add(me.method());
                allCpRefs.addAll(me.method().cpReferences());
                methodIndex++;
            }
        }

        return new TranslationResult(
                allMethods, allCpRefs, methodIndexMap,
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
     * Stage 6: Assembles all CAP components (Header, Directory, Applet, Import,
     * ConstantPool, Class, Method, StaticField, ReferenceLocation, Export, Descriptor)
     * into a JAR/ZIP archive.
     */
    @SuppressWarnings("java:S3776") // Inherently complex CAP assembly with 11 components
    private byte[] assembleCap(TranslationResult translation, TokenMap tokenMap, ScanResult scan,
                               Map<String, StaticValue> staticInit) throws IOException {
        // Finalize imports: remove unreferenced packages, reassign tokens, remap CP. Packages named
        // only by superclass/interface lists or descriptors are kept (JCVM 3.1 §6.7, §6.9, §6.14).
        List<ImportedPackage> finalImports = translation.resolver().finalizeImports(javaCardVersion,
                StructuralReferences.classes(scan.references(), scan.imports()));

        // Reorder CP entries: instance field refs first, ordered by declaring class in Class
        // component order (matches Oracle's ordering; getfield_<t>/putfield_<t> take a one-byte
        // index, JCVM 3.1 §6.12), and no catch type at index 0 (JCVM 3.1 §6.10.3). Must happen
        // before MethodComponent.generate() reads bytecode, and before patchInternalRefs() which
        // uses CP indices. Methods are re-encoded with the final indices (instruction widths,
        // branch offsets and catch types follow).
        Map<Integer, Integer> fieldClassOrder = translation.resolver().instanceFieldClassOrder();
        int[] cpRemap = translation.cp().reorderInstanceFieldsFirst(fieldClassOrder,
                TranslatedMethod.catchTypeIndices(translation.allMethods()));
        if (cpRemap.length > 0) {
            TranslatedMethod.remapAll(translation.allMethods(), cpRemap);
            translation.resolver().remapPendingCpIndices(cpRemap);
            translation.resolver().remapCpTypeDescriptors(cpRemap);
        }

        // Generate Method component first -- we need offsets for everything else
        MethodComponent.MethodResult methodResult = MethodComponent.generate(translation.allMethods());
        byte[] methodBytes = methodResult.bytes();
        int[] methodOffsets = methodResult.offsets();

        // Generate StaticField component (with the <clinit> initial values, JCVM 3.1 §6.11)
        // -- we need field offsets for CP patching
        StaticFieldComponent.StaticFieldResult staticFieldResult =
                StaticFieldComponent.generate(translation.sortedClasses(), staticInit);
        byte[] staticFieldBytes = staticFieldResult.bytes();

        // Generate Class component (JCVM 3.1 §6.9) -- its entry offsets are the internal class_refs
        ClassComponent.ClassResult classResult = ClassComponent.generate(
                translation.sortedClasses(), tokenMap, methodOffsets,
                translation.methodIndexMap(), translation.resolver(), javaCardVersion);
        byte[] classBytes2 = classResult.bytes();
        Map<String, Integer> classOffsets = new HashMap<>();
        for (int i = 0; i < translation.sortedClasses().size(); i++) {
            classOffsets.put(translation.sortedClasses().get(i).thisClass(), classResult.classOffsets()[i]);
        }

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

        // Build Applet component: install_method_offset of each validated applet (JCVM 3.1 §6.6)
        byte[] appletBytes = null;
        if (!scan.applets().isEmpty()) {
            appletBytes = AppletComponent.generate(AppletRules.entries(
                    scan.applets(), translation.methodIndexMap(), methodOffsets));
        }

        byte[] importBytes = ImportComponent.generate(finalImports);

        // Build absolute CpReferences for RefLocation component.
        // Per JCVM spec §6.11, offsets are absolute within the Method component info area
        // (after tag+size header), not relative to each method's bytecode start.
        List<CpReference> absoluteRefs = buildAbsoluteRefLocations(
                translation.allMethods(), methodOffsets);
        byte[] refLocationBytes = RefLocationComponent.generate(absoluteRefs);

        // Descriptor component (JCVM 3.1 §6.14); class_refs in type descriptors: internal classes
        // by Class component offset, external ones by (0x80|pkg)<<8|class token
        byte[] descriptorBytes = DescriptorComponent.generate(new DescriptorComponent.Input(
                translation.sortedClasses(), tokenMap, classOffsets, methodOffsets,
                translation.methodIndexMap(), translation.allMethods(), translation.cp(),
                translation.resolver().cpTypeDescriptors(), translation.resolver()::resolveClassRefDirect,
                staticFieldResult.fieldOffsetMap(), translation.resolver().importedTypes()));

        // Export component (optional, JCVM 3.1 §6.2, §6.13): omitted when the package exports nothing
        byte[] exportComponentBytes = !generateExport ? null : ExportComponent.generate(new ExportComponent.Input(
                translation.sortedClasses(), tokenMap, classOffsets, methodOffsetMap,
                staticFieldResult.fieldOffsetMap(), !applets.isEmpty(), translation.resolver().importedTypes()))
                .orElse(null);

        // Build flags (ACC_EXPORT if and only if the Export component is present, §6.4)
        int flags = 0;
        // JCVM 3.1 §6.4: ACC_INT reflects the use of int, not the supportInt32 option
        if (HeaderComponent.usesInt(translation.sortedClasses(), translation.allMethods())) {
            flags |= HeaderComponent.ACC_INT;
        }
        if (exportComponentBytes != null) flags |= HeaderComponent.ACC_EXPORT;
        if (!applets.isEmpty()) flags |= HeaderComponent.ACC_APPLET;

        String internalPkgName = packageName.replace('.', '/');
        // Per JCVM spec 6.3, package_name_info is optional; Oracle's converter omits it, and so do we.
        byte[] headerBytes = HeaderComponent.generate(packageAid, pkgMajorVersion, pkgMinorVersion, flags, null,
                javaCardVersion);

        // Directory component_sizes (JCVM 3.1 §6.5): u2 body sizes, without the 3-byte tag and size header
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
        byte[] directoryBytes = DirectoryComponent.generate(componentSizes, staticFieldResult.imageSize(),
                staticFieldResult.arrayInitCount(), staticFieldResult.arrayInitSize(),
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
     * Stage 7: Generates the export file (.exp) of the package for downstream package linking
     * (JCVM 3.1 Chapter 5): all public types of a library, the shareable interfaces of an applet
     * package.
     */
    private byte[] generateExportFile(ScanResult scan, TokenMap tokenMap) throws ConverterException {
        return ExportFileWriter.write(new ExportInput(packageName, packageAid, pkgMajorVersion, pkgMinorVersion,
                applets.isEmpty(), scan.packageInfo().classes(), tokenMap, scan.imports(), javaCardVersion));
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
     * Returns the classes in Class component order, the order of {@link TokenMap#classes()}
     * (JCVM 3.1 §6.9: interfaces first, supertypes before subtypes). The Method and Descriptor
     * components list the classes' methods in the same order.
     */
    private static List<ClassInfo> componentOrder(List<ClassInfo> classes, TokenMap tokenMap) {
        Map<String, ClassInfo> byName = new HashMap<>();
        classes.forEach(ci -> byName.put(ci.thisClass(), ci));
        return tokenMap.classes().stream().map(ce -> byName.get(ce.internalName())).toList();
    }

    /**
     * Returns the methods of a class that are represented in the Method component, in class file
     * order: all methods except {@code <clinit>} (its effect is in the Static Field component) and
     * interface method declarations (JCVM 3.1 §6.10).
     */
    private static List<MethodModel> methodComponentMembers(ClassInfo owner, ClassModel model) {
        if (owner.isInterface()) {
            return List.of();
        }
        return model.methods().stream().filter(mm -> !"<clinit>".equals(mm.methodName().stringValue())).toList();
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
     *   <li>{@link #importExportFile(Path)} -- {@code .exp} files of imported packages</li>
     *   <li>{@link #exportPath(Path...)} -- directories/JARs searched for {@code .exp} files of imported packages</li>
     *   <li>{@link #supportInt32(boolean)} -- defaults to {@code false}</li>
     *   <li>{@link #generateExport(boolean)} -- defaults to {@code true}</li>
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
        private final List<Path> exportPath = new ArrayList<>();
        private boolean supportInt32 = false;
        private boolean generateExport = true;
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
         * Sets the Java package name (dot notation, e.g. "com.example"; the internal form
         * "com/example" is accepted and converted to dot notation).
         *
         * @throws IllegalArgumentException for the unnamed package, a name that is not a sequence
         *                                  of Java identifiers, or one longer than 255 UTF-8 bytes
         *                                  (JCVM 3.1 §4.1.3, §5.6.1, §2.2.4.1.3)
         */
        public Builder packageName(String name) {
            this.packageName = PackageRules.packageName(name);
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
         *
         * @throws IllegalArgumentException if a number is outside 0..255 (u1, JCVM 3.1 §4.5, §6.4)
         */
        public Builder packageVersion(int major, int minor) {
            PackageRules.packageVersion(major, minor);
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
            this.applets.put(Objects.requireNonNull(className, "className"), aid.clone());
            return this;
        }

        /**
         * Registers an applet with AID from hex string.
         */
        public Builder applet(String className, String hexAid) {
            this.applets.put(Objects.requireNonNull(className, "className"), hexToBytes(hexAid));
            return this;
        }

        /**
         * Adds an export file (.exp) of an imported package (JCVM 3.1 §4.3.3).
         *
         * <p>An export file given here takes precedence over the export path and over the
         * converter's built-in API data for the same package; the version it declares is the
         * one recorded in the Import component (JCVM 3.1 §4.5.2).
         *
         * @param expFile path of the export file
         * @return this builder
         */
        public Builder importExportFile(Path expFile) {
            this.importExportFiles.add(Objects.requireNonNull(expFile));
            return this;
        }

        /**
         * Adds export path entries, searched in order for the export files of the packages the
         * classes reference.
         *
         * <p>Each entry is a directory or a JAR/ZIP file that holds export files at
         * {@code <package>/javacard/<last name component>.exp} (JCVM 3.1 §4.1.1, §5.1, §5.2), for
         * example a kit's {@code api_export_files} directory, a directory of GlobalPlatform API
         * export files, or the output of another package's conversion. Export files found here
         * take precedence over the built-in API data.
         *
         * @param entries directories or JAR files
         * @return this builder
         */
        public Builder exportPath(Path... entries) {
            for (Path entry : entries) this.exportPath.add(Objects.requireNonNull(entry));
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
         * Whether the CAP file gets the Export component (JCVM 3.1 §6.13), which other packages need to link
         * against this one on the card. With {@code true}, the default, it is written when the package has
         * something to export (§6.2): a library's public classes and interfaces, an applet package's public
         * shareable interfaces; {@code false} omits it (a library converted so is private, §6.13, and no other
         * package can use it). The export file ({@link ConverterResult#exportFile()}) is produced in any case.
         *
         * @param flag whether to write the Export component when the package exports something
         * @return this builder
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
         * Former switch between two Class component layouts; it has no effect.
         *
         * <p>The Class component is always written in the {@code class_info} layout of JCVM 3.1
         * §6.9.2 ({@code public_method_table_base}, {@code public_method_table_count},
         * {@code package_method_table_base}, {@code package_method_table_count}, then both
         * method tables), which is also what Oracle's converter produces. The non-compatible
         * layout of earlier versions placed the package table items after the public table and
         * could not be loaded or verified.
         *
         * @param flag ignored
         * @return this builder
         * @deprecated the output always follows the specification; remove the call
         */
        @Deprecated
        @SuppressWarnings("java:S1172") // parameter kept for source compatibility
        public Builder oracleCompatibility(boolean flag) {
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

        /**
         * Parses an AID given as hex digits, optionally separated by spaces or colons.
         *
         * @throws IllegalArgumentException if the text is not an even number of hex digits
         */
        static byte[] hexToBytes(String hex) {
            String digits = hex.replace(" ", "").replace(":", "");
            try {
                return java.util.HexFormat.of().parseHex(digits);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid AID '" + hex
                        + "': expected an even number of hexadecimal digits", e);
            }
        }
    }
}
