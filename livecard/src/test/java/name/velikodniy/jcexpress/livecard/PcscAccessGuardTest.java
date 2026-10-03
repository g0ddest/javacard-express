package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.junit.LiveCardExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps physical readers behind two doors in the whole build. The compiled classes of every module listed in the
 * root POM ({@code <module>/target/classes} and {@code <module>/target/test-classes}) are read, and every call and
 * method reference to these entry points is checked (a lambda body counts for its enclosing method, a nested
 * class for its top-level class):
 * <ul>
 *   <li><b>a physical reader</b>: {@code javax.smartcardio.TerminalFactory}, and the {@code PcscSession.open}
 *       overloads that look up readers through it ({@code open()}, {@code open(Options)}, {@code open(String)},
 *       {@code open(String, Options)}). Only core's {@code PcscSession} (the implementation) and
 *       {@link PcscConnector} (behind the live-card opt-in) reach it; the one other site is gp's README snippet
 *       {@code RootReadmeSnippets.loadOntoRealCard}, which is compiled and never run;</li>
 *   <li>{@code RootReadmeSnippets.loadOntoRealCard}: nothing calls or references it, so the snippet's exception
 *       holds only while it stays compile-only;</li>
 *   <li>{@code new PcscConnector()}: only {@link LiveCard} and {@link LiveCardExtension};</li>
 *   <li>{@link LiveCard#connect(LiveCardConfig)}, which connects through a {@link PcscConnector}: no test
 *       class.</li>
 * </ul>
 * <p>The overloads {@code PcscSession.open(CardTerminal, ...)} and {@code open(CardTerminals, ...)} take the
 * reader from the caller (fakes, simulated readers) and are not restricted. A module that is not built in this
 * run has no class directories: it is named in the output, and core, gp and livecard must be there. Tests against
 * a real card belong in this module ({@code @LiveCardTest}, run only by {@code ./mvnw -Plivecard verify -pl livecard -am}).</p>
 */
class PcscAccessGuardTest {

    private static final String TERMINAL_FACTORY = "javax/smartcardio/TerminalFactory";
    private static final String PCSC_SESSION = "name/velikodniy/jcexpress/pcsc/PcscSession";
    private static final String PCSC_CONNECTOR = internalName(PcscConnector.class);
    private static final String LIVE_CARD = internalName(LiveCard.class);
    private static final String EXTENSION = internalName(LiveCardExtension.class);
    /** {@link LiveCard#connect(LiveCardConfig)}: the overload that uses a {@link PcscConnector}. */
    private static final String CONNECT_THROUGH_PCSC = "(L" + internalName(LiveCardConfig.class) + ";)L" + LIVE_CARD
            + ";";
    private static final String README_SNIPPETS = "name/velikodniy/jcexpress/gp/RootReadmeSnippets";
    private static final String README_SNIPPET = "loadOntoRealCard";
    /** Descriptor starts of the {@code PcscSession.open} overloads that take the reader(s) from the caller. */
    private static final List<String> GIVEN_READERS = List.of("(Ljavax/smartcardio/CardTerminal;",
            "(Ljavax/smartcardio/CardTerminals;");
    private static final Pattern LAMBDA = Pattern.compile("lambda\\$(.+)\\$\\d+");
    private static final String REAL_CARD_TESTS = "tests against a real card belong in the livecard module"
            + " (@LiveCardTest, run only by ./mvnw -Plivecard verify -pl livecard -am, LIVE_CARD_TESTING.md)";

    /** What a call or method reference reaches. */
    enum Entry { READER, README_SNIPPET, NEW_CONNECTOR, CONNECT_THROUGH_PCSC }

    /**
     * Compiled classes of a module.
     *
     * @param module the module directory
     * @param test   {@code target/test-classes} if true, {@code target/classes} otherwise
     * @param path   the directory
     */
    record ClassDirectory(String module, boolean test, Path path) {
        String name() {
            return module + (test ? "/test" : "/main");
        }
    }

    /**
     * A call or method reference in compiled code.
     *
     * @param method     the calling method (for a lambda body: the method that contains the lambda)
     * @param owner      the internal name of the called method's class
     * @param name       the called method
     * @param descriptor the called method's descriptor
     */
    record Reference(String method, String owner, String name, String descriptor) {
        boolean is(String type, String member) {
            return owner.equals(type) && name.equals(member);
        }
    }

    /**
     * A reference to an entry point.
     *
     * @param directory where the calling class is
     * @param type      the calling top-level class (internal name)
     * @param reference the call
     * @param entry     what it reaches
     */
    record CallSite(ClassDirectory directory, String type, Reference reference, Entry entry) {
        boolean in(String module, boolean test, String className) {
            return directory.module().equals(module) && directory.test() == test && type.equals(className);
        }

        @Override
        public String toString() {
            return directory.name() + " " + type + "." + reference.method() + " -> " + reference.owner() + "."
                    + reference.name() + reference.descriptor();
        }
    }

    /** Positive control of the scan: {@link #target()} referenced by a method reference and in a lambda body. */
    static final class ScanProbe {
        private ScanProbe() {
        }

        static void target() {
            // only referenced
        }

        static Runnable byMethodReference() {
            return ScanProbe::target;
        }

        @SuppressWarnings("java:S1612") // the lambda body is what this probe needs, not a method reference
        static Runnable byLambda() {
            return () -> target();
        }

        static Runnable setsAProperty() {
            return () -> System.setProperty("jcx.livecard.probe", "unused");
        }
    }

    /**
     * The string constants a method loads and the methods it calls ({@code owner.name}), lambda bodies included.
     *
     * @param directory where the class is
     * @param type      the top-level class
     * @param method    the method
     * @param strings   string constants
     * @param calls     called methods
     */
    record MethodFacts(ClassDirectory directory, String type, String method, Set<String> strings, Set<String> calls) {
    }

    private static final String ENABLED = LiveCardExtension.ENABLED_PARAMETER;
    private static final String CONNECTOR = LiveCardExtension.CONNECTOR_PARAMETER;
    private static final Set<String> LIVE_MODE_PROPERTIES = Set.of(ENABLED, LiveCardConfig.ALLOW_CI_PROPERTY);
    private static final Set<String> PROPERTY_SETTERS = Set.of("java/lang/System.setProperty",
            "java/lang/System.setProperties", "java/lang/System.getProperties", "java/util/Properties.setProperty",
            "java/util/Properties.put", "java/util/Hashtable.put");
    private static final String TEST_KIT_PARAMETER = "org/junit/platform/testkit/engine/EngineTestKit$Builder"
            + ".configurationParameter";

    private static List<ClassDirectory> scanned;
    private static List<String> notBuilt;
    private static List<CallSite> sites;
    private static List<MethodFacts> methods;

    @BeforeAll
    static void scanTheBuild() throws Exception {
        Path root = ProjectRoot.find();
        scanned = new ArrayList<>();
        notBuilt = new ArrayList<>();
        sites = new ArrayList<>();
        methods = new ArrayList<>();
        for (String module : ProjectRoot.modules(root)) {
            for (boolean test : List.of(false, true)) {
                Path path = root.resolve(module).resolve("target").resolve(test ? "test-classes" : "classes");
                ClassDirectory directory = new ClassDirectory(module, test, path);
                if (Files.isDirectory(path)) {
                    scanned.add(directory);
                    sites.addAll(callSites(directory));
                } else {
                    notBuilt.add(directory.name());
                }
            }
        }
        System.out.println("PcscAccessGuardTest: scanned " + scanned.stream().map(ClassDirectory::name).toList()
                + "; not built in this run, so not scanned: " + (notBuilt.isEmpty() ? "none" : notBuilt));
    }

    @Test
    void coreGpAndLivecardAreScannedAndModulesNotBuiltAreNamed(TestReporter reporter) {
        reporter.publishEntry("not built in this run, so not scanned", notBuilt.isEmpty() ? "none"
                : String.join(", ", notBuilt));

        assertThat(scanned).extracting(ClassDirectory::name)
                .as("compiled classes of the build (build them in the same run: ./mvnw verify -pl livecard -am);"
                        + " not built: %s", notBuilt)
                .contains("core/main", "core/test", "gp/main", "gp/test", "livecard/main", "livecard/test");
    }

    @Test
    void onlyPcscSessionAndPcscConnectorReachAPhysicalReader() {
        List<CallSite> reader = sites(Entry.READER);

        assertThat(reader).as("the implementations' own reader calls (guards against scanning nothing)")
                .anyMatch(site -> site.in("core", false, PCSC_SESSION))
                .anyMatch(site -> site.in("livecard", false, PCSC_CONNECTOR));
        assertThat(reader).filteredOn(site -> !site.in("core", false, PCSC_SESSION)
                        && !site.in("livecard", false, PCSC_CONNECTOR) && !isReadmeSnippet(site))
                .as("calls that reach a physical reader outside PcscSession and PcscConnector; " + REAL_CARD_TESTS)
                .isEmpty();
    }

    @Test
    void theReadmeSnippetThatOpensTheFirstReaderIsCompiledButNeverCalled() {
        assertThat(sites(Entry.READER)).as("the snippet's PcscSession.open() in gp's test classes")
                .anyMatch(PcscAccessGuardTest::isReadmeSnippet);
        assertThat(sites(Entry.README_SNIPPET))
                .as("calls and method references to %s.%s, which opens the first reader with a card: it must stay"
                        + " compile-only; %s", README_SNIPPETS, README_SNIPPET, REAL_CARD_TESTS)
                .isEmpty();
    }

    @Test
    void onlyLiveCardAndTheExtensionCreateAPcscConnector() {
        assertThat(sites(Entry.NEW_CONNECTOR)).as("new PcscConnector() in LiveCard.connect(LiveCardConfig)")
                .anyMatch(site -> site.in("livecard", false, LIVE_CARD));
        assertThat(sites(Entry.NEW_CONNECTOR))
                .filteredOn(site -> !site.in("livecard", false, LIVE_CARD) && !site.in("livecard", false, EXTENSION))
                .as("PcscConnector created outside LiveCard and LiveCardExtension")
                .isEmpty();
    }

    @Test
    void noTestConnectsThroughPcsc() {
        assertThat(sites(Entry.CONNECT_THROUGH_PCSC)).filteredOn(site -> site.directory().test())
                .as("test classes that call LiveCard.connect(LiveCardConfig); tests use simulated connectors")
                .isEmpty();
    }

    /**
     * Live mode is switched on only for a run, by the JVM system property {@code jcx.livecard.enabled} (given on the
     * command line; -Plivecard sets it): no class of the build, main or test, sets it or
     * {@code jcx.livecard.allowCi} as a system property, where PcscConnector's check of the JVM's own settings would
     * see it.
     */
    @Test
    void noCodeSetsTheLiveModeSystemProperties() {
        assertThat(methods).filteredOn(method -> method.strings().stream().anyMatch(LIVE_MODE_PROPERTIES::contains)
                        && method.calls().stream().anyMatch(PROPERTY_SETTERS::contains))
                .as("methods that set %s through %s", LIVE_MODE_PROPERTIES, PROPERTY_SETTERS)
                .isEmpty();
    }

    /** A test-kit launch that enables live-card tests names its (simulated) connector, never the PC/SC default. */
    @Test
    void everyTestKitLaunchThatEnablesLiveTestsNamesItsConnector() {
        List<MethodFacts> launches = methods.stream().filter(method -> method.directory().test()
                && method.calls().contains(TEST_KIT_PARAMETER) && method.strings().contains(ENABLED)).toList();

        assertThat(launches).as("the extension's own tests launch live classes through the test kit").isNotEmpty();
        assertThat(launches).filteredOn(method -> !method.strings().contains(CONNECTOR))
                .as("test-kit launches with %s but without %s", ENABLED, CONNECTOR).isEmpty();
    }

    @Test
    void theScanSeesPropertySetters() throws IOException, URISyntaxException {
        Path classes = Path.of(ScanProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassModel probe = ClassFile.of().parse(classes.resolve(internalName(ScanProbe.class) + ".class"));

        assertThat(facts(new ClassDirectory("livecard", true, classes), probe))
                .filteredOn(method -> method.method().equals("setsAProperty")).singleElement()
                .satisfies(method -> assertThat(method.strings()).contains("jcx.livecard.probe"))
                .satisfies(method -> assertThat(method.calls()).contains("java/lang/System.setProperty"));
    }

    @Test
    void theScanSeesMethodReferencesAndLambdaBodies() throws IOException, URISyntaxException {
        Path classes = Path.of(ScanProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassModel probe = ClassFile.of().parse(classes.resolve(internalName(ScanProbe.class) + ".class"));

        assertThat(references(probe)).filteredOn(reference -> reference.is(internalName(ScanProbe.class), "target"))
                .extracting(Reference::method)
                .containsExactlyInAnyOrder("byMethodReference", "byLambda");
    }

    private static List<CallSite> sites(Entry entry) {
        return sites.stream().filter(site -> site.entry() == entry).toList();
    }

    private static boolean isReadmeSnippet(CallSite site) {
        return site.in("gp", true, README_SNIPPETS) && site.reference().method().equals(README_SNIPPET);
    }

    private static List<CallSite> callSites(ClassDirectory directory) throws IOException {
        List<CallSite> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(directory.path())) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassModel model = ClassFile.of().parse(file);
                String type = model.thisClass().asInternalName().replaceAll("\\$.*", "");
                for (Reference reference : references(model)) {
                    entry(reference).ifPresent(entry -> found.add(new CallSite(directory, type, reference, entry)));
                }
                methods.addAll(facts(directory, model));
            }
        }
        return found;
    }

    /** The string constants and calls of each method of a class (a lambda body counts for its method). */
    private static List<MethodFacts> facts(ClassDirectory directory, ClassModel model) {
        String type = model.thisClass().asInternalName().replaceAll("\\$.*", "");
        Map<String, MethodFacts> byMethod = new LinkedHashMap<>();
        for (MethodModel method : model.methods()) {
            String name = enclosingMethod(method.methodName().stringValue());
            MethodFacts facts = byMethod.computeIfAbsent(name, key -> new MethodFacts(directory, type, key,
                    new HashSet<>(), new HashSet<>()));
            method.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof ConstantInstruction constant && constant.constantValue() instanceof String text) {
                    facts.strings().add(text);
                } else if (element instanceof InvokeInstruction call) {
                    facts.calls().add(call.owner().asInternalName() + "." + call.name().stringValue());
                }
            }));
        }
        return List.copyOf(byMethod.values());
    }

    /** Every call and method reference in the code of a class. */
    private static List<Reference> references(ClassModel model) {
        List<Reference> references = new ArrayList<>();
        for (MethodModel method : model.methods()) {
            String caller = enclosingMethod(method.methodName().stringValue());
            method.code().ifPresent(code -> code.forEach(element -> targets(caller, element).forEach(references::add)));
        }
        return references;
    }

    /**
     * The methods an instruction calls or references: an invocation, or the method handles of an invokedynamic
     * (a method reference, the body of a lambda).
     */
    private static Stream<Reference> targets(String caller, CodeElement element) {
        return switch (element) {
            case InvokeInstruction call -> Stream.of(new Reference(caller, call.owner().asInternalName(),
                    call.name().stringValue(), call.type().stringValue()));
            case InvokeDynamicInstruction indy -> Stream.concat(Stream.of(indy.bootstrapMethod()),
                            indy.bootstrapArgs().stream())
                    .filter(DirectMethodHandleDesc.class::isInstance)
                    .map(DirectMethodHandleDesc.class::cast)
                    .map(handle -> new Reference(caller, internalName(handle.owner()), handle.methodName(),
                            handle.lookupDescriptor()));
            default -> Stream.empty();
        };
    }

    private static Optional<Entry> entry(Reference target) {
        boolean defaultReader = target.is(PCSC_SESSION, "open")
                && GIVEN_READERS.stream().noneMatch(target.descriptor()::startsWith);
        if (target.owner().equals(TERMINAL_FACTORY) || defaultReader) {
            return Optional.of(Entry.READER);
        }
        if (target.is(README_SNIPPETS, README_SNIPPET)) {
            return Optional.of(Entry.README_SNIPPET);
        }
        if (target.is(PCSC_CONNECTOR, "<init>")) {
            return Optional.of(Entry.NEW_CONNECTOR);
        }
        boolean throughPcsc = target.is(LIVE_CARD, "connect") && target.descriptor().equals(CONNECT_THROUGH_PCSC);
        return throughPcsc ? Optional.of(Entry.CONNECT_THROUGH_PCSC) : Optional.empty();
    }

    private static String enclosingMethod(String method) {
        Matcher lambda = LAMBDA.matcher(method);
        return lambda.matches() ? lambda.group(1) : method;
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.startsWith("L") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }
}
