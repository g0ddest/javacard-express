package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compares the {@code org.globalplatform} stubs with a jar of the GlobalPlatform Card API named by the system
 * property {@value ApiLocations#GLOBALPLATFORM_REFERENCE_PROPERTY}, as data: the class files of both sides are
 * parsed, never loaded. The stubs describe version 1.8 of the package, so the jar to compare with is the one of
 * that version.
 *
 * <p>Without the property the tests are skipped: no dependency of the build contains the package (jCardSim does
 * not implement it), and GlobalPlatform's jar is not redistributed with this project. The independent check that
 * runs in every build is {@link GlobalPlatformKnownAnswerTest}.</p>
 *
 * <p>Everything javac or an applet's behaviour can depend on is compared in both directions: the types with their
 * modifiers and supertypes, the fields with type, modifiers and constant value (javac copies the value into the
 * applet, JLS 13.1), the methods with descriptor, modifiers and the exceptions they declare, and which elements are
 * deprecated.</p>
 */
class GlobalPlatformApiReferenceTest {

    private static final List<String> PACKAGE = List.of("org/globalplatform");

    private static Map<String, ApiSurface.Type> stubs;
    private static Map<String, ApiSurface.Type> reference;
    private static Set<String> stubDeprecations;
    private static Set<String> referenceDeprecations;

    @BeforeAll
    static void readSurfaces() {
        Optional<Path> jar = ApiLocations.globalPlatformReferenceJar();
        if (jar.isEmpty()) {
            return;
        }
        Path stubClasses = ApiLocations.stubClasses();
        stubs = ApiSurface.read(stubClasses, ApiSurface.GLOBALPLATFORM_PACKAGE).inPackages(PACKAGE);
        reference = ApiSurface.read(jar.get(), ApiSurface.GLOBALPLATFORM_PACKAGE).inPackages(PACKAGE);
        stubDeprecations = inPackage(ApiSurface.deprecated(stubClasses, ApiSurface.GLOBALPLATFORM_PACKAGE));
        referenceDeprecations = inPackage(ApiSurface.deprecated(jar.get(), ApiSurface.GLOBALPLATFORM_PACKAGE));
        assertThat(reference).as("types of org.globalplatform in " + jar.get()).isNotEmpty();
    }

    /** Skips every test, with the way to enable it, when no reference jar is named. */
    @BeforeEach
    void needsAReferenceJar() {
        assumeTrue(reference != null, "no GlobalPlatform Card API jar to compare with: run with -D"
                + ApiLocations.GLOBALPLATFORM_REFERENCE_PROPERTY + "=<jar of org.globalplatform 1.8>, for example"
                + " ./mvnw test -pl javacard-api -D" + ApiLocations.GLOBALPLATFORM_REFERENCE_PROPERTY
                + "=/path/to/gpapi-globalplatform.jar");
    }

    @Test
    void theStubsDeclareTheTypesOfTheReference() {
        assertThat(stubs.keySet()).as("public types of org.globalplatform").isEqualTo(reference.keySet());
    }

    @Test
    void typeDeclarationsEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> {
            if (stub.flags() != ref.flags()) {
                problems.add(stub.name() + ": " + Modifier.toString(stub.flags()) + ", reference: "
                        + Modifier.toString(ref.flags()));
            }
            if (!Objects.equals(stub.superName(), ref.superName()) || !stub.interfaces().equals(ref.interfaces())) {
                problems.add(stub.name() + " extends " + stub.superName() + " implements " + stub.interfaces()
                        + ", reference extends " + ref.superName() + " implements " + ref.interfaces());
            }
        });
        assertThat(problems).as("types whose declaration differs from the reference").isEmpty();
    }

    @Test
    void fieldsEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> {
            problems.addAll(onlyIn("stubs", stub.name(), stub.fields().keySet(), ref.fields().keySet()));
            problems.addAll(onlyIn("reference", stub.name(), ref.fields().keySet(), stub.fields().keySet()));
            stub.fields().forEach((name, declared) -> {
                ApiSurface.Field expected = ref.fields().get(name);
                if (expected != null && !expected.equals(declared)) {
                    problems.add(stub.name() + "." + name + ": " + describe(declared) + ", reference: "
                            + describe(expected));
                }
            });
        });
        assertThat(problems).as("fields that differ from the reference (type, modifiers or constant value)")
                .isEmpty();
    }

    @Test
    void methodsEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> {
            problems.addAll(onlyIn("stubs", stub.name(), stub.methods().keySet(), ref.methods().keySet()));
            problems.addAll(onlyIn("reference", stub.name(), ref.methods().keySet(), stub.methods().keySet()));
            stub.methods().forEach((key, declared) -> {
                ApiSurface.Method expected = ref.methods().get(key);
                if (expected != null && !expected.equals(declared)) {
                    problems.add(stub.name() + "." + key + ": " + describe(declared) + ", reference: "
                            + describe(expected));
                }
            });
        });
        assertThat(problems).as("methods that differ from the reference (descriptor, modifiers or declared"
                + " exceptions)").isEmpty();
    }

    @Test
    void theSameElementsAreDeprecated() {
        assertThat(stubDeprecations).as("deprecated types and members of org.globalplatform")
                .isEqualTo(referenceDeprecations);
    }

    private static void forEachCommonType(BiConsumer<ApiSurface.Type, ApiSurface.Type> action) {
        stubs.forEach((name, stub) -> {
            ApiSurface.Type ref = reference.get(name);
            if (ref != null) {
                action.accept(stub, ref);
            }
        });
    }

    /** The members one side declares and the other does not, each as a problem line. */
    private static List<String> onlyIn(String side, String type, Set<String> members, Set<String> others) {
        return members.stream().filter(member -> !others.contains(member))
                .map(member -> type + "." + member + " is only in the " + side).toList();
    }

    private static String describe(ApiSurface.Field field) {
        return (Modifier.toString(field.flags()) + " " + field.descriptor() + " = " + field.constant()).strip();
    }

    private static String describe(ApiSurface.Method method) {
        return (Modifier.toString(method.flags()) + " " + method.descriptor() + " throws " + method.exceptions())
                .strip();
    }

    /** Keeps the elements of types that are directly in the package, not in a subpackage. */
    private static Set<String> inPackage(Set<String> elements) {
        String prefix = PACKAGE.get(0) + "/";
        return elements.stream()
                .filter(element -> element.startsWith(prefix) && typeName(element, prefix).indexOf('/') < 0)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** The simple type name of {@code type}, {@code type.field} or {@code type.method(descriptor)}. */
    private static String typeName(String element, String prefix) {
        String rest = element.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
