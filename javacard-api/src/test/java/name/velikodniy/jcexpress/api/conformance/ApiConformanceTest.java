package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mechanical conformance gate for the API stubs.
 *
 * <p>The stub class files are compared with the class files of jCardSim, an independent Apache-2.0
 * implementation of the same published Java Card 3.0.5 Classic API (with the few deviations listed in
 * {@link ReferenceCorrections}), or with another jar of that API named by {@value ApiLocations#REFERENCE_PROPERTY}.
 * Nothing is restated by hand: every expected fact is read from the reference at test time. The facts compared are the ones that end up in, or decide the meaning of, code compiled
 * against the stubs:
 * <ul>
 *   <li>constant values, which javac copies into the applet's bytecode (JLS 13.1), so a wrong stub value
 *       silently becomes a wrong algorithm, key type or status check on the card;</li>
 *   <li>method descriptors, because applets link against name plus descriptor and the export files of the
 *       card platform identify methods the same way (JCVM 3.1 section 5.9);</li>
 *   <li>modifiers, super types and checked exceptions, which decide what compiles;</li>
 *   <li>completeness: every public type and member of the reference must be present.</li>
 * </ul>
 */
class ApiConformanceTest {

    private static Map<String, ApiSurface.Type> stubs;
    private static Map<String, ApiSurface.Type> reference;

    @BeforeAll
    static void readSurfaces() {
        if (stubs == null) {
            stubs = ApiSurface.read(ApiLocations.stubClasses()).types();
            ApiSurface read = ApiSurface.read(ApiLocations.referenceJar());
            reference = (ApiLocations.isJcardsimReference() ? ReferenceCorrections.apply(read) : read).types();
        }
    }

    @Test
    void constantValuesEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> stub.fields().forEach((name, declared) -> {
            ApiSurface.Field expected = ref.fields().get(name);
            if (expected != null && !Objects.equals(expected.constant(), declared.constant())) {
                problems.add(stub.name() + "." + name + " = " + declared.constant()
                        + ", reference = " + expected.constant());
            }
        }));
        assertThat(problems).as("constant values that differ from the reference API").isEmpty();
    }

    @Test
    void fieldTypesAndModifiersEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> stub.fields().forEach((name, declared) -> {
            ApiSurface.Field expected = ref.fields().get(name);
            if (expected != null && (!expected.descriptor().equals(declared.descriptor())
                    || expected.flags() != declared.flags())) {
                problems.add(stub.name() + "." + name + ": " + Modifier.toString(declared.flags()) + " "
                        + declared.descriptor() + ", reference: " + Modifier.toString(expected.flags()) + " "
                        + expected.descriptor());
            }
        }));
        assertThat(problems).as("fields whose type or modifiers differ from the reference API").isEmpty();
    }

    @Test
    void methodReturnTypesEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> stub.methods().values().forEach(declared -> {
            ref.methods().values().stream()
                    .filter(expected -> expected.parameterKey().equals(declared.parameterKey()))
                    .filter(expected -> !expected.descriptor().equals(declared.descriptor()))
                    .forEach(expected -> problems.add(stub.name() + "." + declared.parameterKey() + " returns "
                            + declared.returnDescriptor() + ", reference returns " + expected.returnDescriptor()));
        }));
        assertThat(problems).as("methods whose return type differs from the reference API").isEmpty();
    }

    @Test
    void methodModifiersEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> stub.methods().forEach((key, declared) -> {
            ApiSurface.Method expected = ref.methods().get(key);
            if (expected != null && expected.flags() != declared.flags()) {
                problems.add(stub.name() + "." + key + ": " + Modifier.toString(declared.flags())
                        + ", reference: " + Modifier.toString(expected.flags()));
            }
        }));
        assertThat(problems).as("methods whose modifiers differ from the reference API").isEmpty();
    }

    @Test
    void checkedExceptionsEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> stub.methods().forEach((key, declared) -> {
            ApiSurface.Method expected = ref.methods().get(key);
            if (expected != null && !checked(expected.exceptions()).equals(checked(declared.exceptions()))) {
                problems.add(stub.name() + "." + key + " throws " + checked(declared.exceptions())
                        + ", reference throws " + checked(expected.exceptions()));
            }
        }));
        assertThat(problems).as("methods whose checked exceptions differ from the reference API").isEmpty();
    }

    @Test
    void typeDeclarationsEqualTheReference() {
        List<String> problems = new ArrayList<>();
        forEachCommonType((stub, ref) -> {
            if (stub.flags() != ref.flags()) {
                problems.add(stub.name() + ": " + Modifier.toString(stub.flags())
                        + ", reference: " + Modifier.toString(ref.flags()));
            }
            if (!Objects.equals(stub.superName(), ref.superName()) || !stub.interfaces().equals(ref.interfaces())) {
                problems.add(stub.name() + " extends " + stub.superName() + " implements " + stub.interfaces()
                        + ", reference extends " + ref.superName() + " implements " + ref.interfaces());
            }
        });
        assertThat(problems).as("types whose declaration differs from the reference API").isEmpty();
    }

    /**
     * Every public type, field and method of the reference must exist in the stubs, package by package, so that
     * any applet written against the published 3.0.5 Classic API compiles against the stubs.
     *
     * @param apiPackage internal package name, for example {@code javacard/framework}
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("referencePackages")
    void stubsDeclareTheCompleteReferenceApi(String apiPackage) {
        List<String> missing = new ArrayList<>();
        reference.values().stream().filter(type -> packageOf(type.name()).equals(apiPackage)).forEach(ref -> {
            ApiSurface.Type stub = stubs.get(ref.name());
            if (stub == null) {
                missing.add("type " + ref.name());
                return;
            }
            ref.fields().keySet().stream().filter(name -> !stub.fields().containsKey(name))
                    .forEach(name -> missing.add("field " + ref.name() + "." + name));
            ref.methods().keySet().stream().filter(key -> !stub.methods().containsKey(key))
                    .forEach(key -> missing.add("method " + ref.name() + "." + key));
        });
        assertThat(missing).as("reference API missing from the stubs in " + apiPackage).isEmpty();
    }

    static Stream<String> referencePackages() {
        readSurfaces();
        return reference.keySet().stream().map(ApiConformanceTest::packageOf).distinct().sorted();
    }

    private static String packageOf(String internalName) {
        return internalName.substring(0, internalName.lastIndexOf('/'));
    }

    @Test
    void stubsDeclareNothingBeyondTheReference() {
        List<String> problems = new ArrayList<>();
        stubs.keySet().stream().filter(name -> !reference.containsKey(name))
                .forEach(name -> problems.add("type " + name));
        forEachCommonType((stub, ref) -> {
            stub.fields().keySet().stream().filter(name -> !ref.fields().containsKey(name))
                    .forEach(name -> problems.add("field " + stub.name() + "." + name));
            stub.methods().keySet().stream().filter(key -> !ref.methods().containsKey(key))
                    .forEach(key -> problems.add("method " + stub.name() + "." + key));
        });
        assertThat(problems).as("public or protected API that the reference does not declare").isEmpty();
    }

    private static void forEachCommonType(BiConsumer<ApiSurface.Type, ApiSurface.Type> action) {
        stubs.forEach((name, stub) -> {
            ApiSurface.Type ref = reference.get(name);
            if (ref != null) {
                action.accept(stub, ref);
            }
        });
    }

    /** Keeps only checked exceptions: unchecked ones in a throws clause do not affect compilation. */
    private static Set<String> checked(Set<String> exceptions) {
        return exceptions.stream().filter(ApiConformanceTest::isChecked)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean isChecked(String exception) {
        String current = exception;
        while (current != null && !current.startsWith("java/")) {
            ApiSurface.Type type = reference.get(current);
            current = type == null ? null : type.superName();
        }
        if (current == null) {
            return true;
        }
        try {
            Class<?> jdkType = Class.forName(current.replace('/', '.'));
            return !RuntimeException.class.isAssignableFrom(jdkType) && !Error.class.isAssignableFrom(jdkType);
        } catch (ClassNotFoundException e) {
            return true;
        }
    }
}
