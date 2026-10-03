package name.velikodniy.jcexpress.pcsc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No test of this module reaches a physical reader.
 *
 * <p>A call reaches a physical reader when it goes to {@code javax.smartcardio.TerminalFactory} or to one of the
 * {@code PcscSession.open} overloads that look up readers through it ({@code open()}, {@code open(Options)},
 * {@code open(String)}, {@code open(String, Options)}). No call and no method reference in the compiled test
 * classes of this module (lambda bodies included) may do that; tests open sessions on fakes with the overloads
 * that take a {@code CardTerminal} or {@code CardTerminals}. Tests against a real card belong in the
 * {@code livecard} module ({@code @LiveCardTest}, run only by {@code ./mvnw -Plivecard verify -pl livecard -am}, see
 * {@code LIVE_CARD_TESTING.md}), whose {@code PcscAccessGuardTest} checks the classes of every module.</p>
 */
class NoPhysicalReaderInTestsTest {

    private static final String TERMINAL_FACTORY = "javax/smartcardio/TerminalFactory";
    private static final String PCSC_SESSION = "name/velikodniy/jcexpress/pcsc/PcscSession";
    /** Descriptor starts of the {@code PcscSession.open} overloads that take the reader(s) from the caller. */
    private static final List<String> GIVEN_READERS = List.of("(Ljavax/smartcardio/CardTerminal;",
            "(Ljavax/smartcardio/CardTerminals;");

    /**
     * A call or method reference in compiled code.
     *
     * @param location the calling class and method ({@code lambda$...} for a lambda body)
     * @param target   the called method: owner, name and descriptor
     */
    record Reference(String location, String target) {
        @Override
        public String toString() {
            return location + " -> " + target;
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
    }

    @Test
    void noTestReachesAPhysicalReader() throws IOException {
        Path testClasses = classes(NoPhysicalReaderInTestsTest.class);
        assertThat(testClasses.resolve(internalName(PcscSessionTest.class) + ".class"))
                .as("the compiled test classes of this module").isRegularFile();

        assertThat(references(testClasses).filter(NoPhysicalReaderInTestsTest::reachesReader))
                .as("test calls that reach a physical reader; tests against a real card belong in the livecard module"
                        + " (@LiveCardTest, run only by ./mvnw -Plivecard verify -pl livecard -am, LIVE_CARD_TESTING.md)")
                .isEmpty();
    }

    /**
     * The scan sees what {@code PcscSession} itself calls: the default factory and the reader-name overload count,
     * the overload that takes the readers ({@code open(CardTerminals, ...)}) does not.
     */
    @Test
    void theScanFindsTheReaderLookupOfPcscSession() throws IOException {
        List<Reference> calls = references(classes(PcscSession.class))
                .filter(reference -> reference.location().startsWith(PCSC_SESSION + ".")).toList();
        String givenReaders = PCSC_SESSION + ".open(Ljavax/smartcardio/CardTerminals;";

        assertThat(calls).extracting(Reference::target).anyMatch(target -> target.startsWith(givenReaders));
        assertThat(calls).filteredOn(NoPhysicalReaderInTestsTest::reachesReader).extracting(Reference::target)
                .contains(TERMINAL_FACTORY + ".getDefault()L" + TERMINAL_FACTORY + ";",
                        PCSC_SESSION + ".open(Ljava/lang/String;L" + PCSC_SESSION + "$Options;)L" + PCSC_SESSION + ";")
                .noneMatch(target -> target.startsWith(givenReaders));
    }

    @Test
    void theScanSeesMethodReferencesAndLambdaBodies() throws IOException {
        String probe = internalName(ScanProbe.class);
        ClassModel model = ClassFile.of().parse(classes(ScanProbe.class).resolve(probe + ".class"));

        assertThat(references(model)).filteredOn(reference -> reference.target().equals(probe + ".target()V"))
                .extracting(Reference::location)
                .containsExactlyInAnyOrder(probe + ".byMethodReference", probe + ".lambda$byLambda$0");
    }

    private static boolean reachesReader(Reference reference) {
        String target = reference.target();
        boolean defaultReader = target.startsWith(PCSC_SESSION + ".open(")
                && GIVEN_READERS.stream().noneMatch(given -> target.startsWith(PCSC_SESSION + ".open" + given));
        return target.startsWith(TERMINAL_FACTORY + ".") || defaultReader;
    }

    private static Stream<Reference> references(Path classes) throws IOException {
        List<Reference> references = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                references.addAll(references(ClassFile.of().parse(file)));
            }
        }
        return references.stream();
    }

    /** Every call and method reference in the code of a class. */
    private static List<Reference> references(ClassModel model) {
        List<Reference> references = new ArrayList<>();
        for (MethodModel method : model.methods()) {
            String location = model.thisClass().asInternalName() + "." + method.methodName().stringValue();
            method.code().ifPresent(code -> code.forEach(element -> targets(element)
                    .map(target -> new Reference(location, target))
                    .forEach(references::add)));
        }
        return references;
    }

    /**
     * The methods an instruction calls or references: an invocation, or the method handles of an invokedynamic
     * (a method reference, the body of a lambda).
     */
    private static Stream<String> targets(CodeElement element) {
        return switch (element) {
            case InvokeInstruction call -> Stream.of(call.owner().asInternalName() + "." + call.name().stringValue()
                    + call.type().stringValue());
            case InvokeDynamicInstruction indy -> Stream.concat(Stream.of(indy.bootstrapMethod()),
                            indy.bootstrapArgs().stream())
                    .filter(DirectMethodHandleDesc.class::isInstance)
                    .map(DirectMethodHandleDesc.class::cast)
                    .map(handle -> internalName(handle.owner()) + "." + handle.methodName()
                            + handle.lookupDescriptor());
            default -> Stream.empty();
        };
    }

    private static Path classes(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.startsWith("L") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }
}
