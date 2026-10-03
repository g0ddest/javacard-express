package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.translate.FixtureCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JCVM 3.1 §2.2.1.1.6, Access Control in Java Packages: the Java Card subset supports package
 * access control "however, the cases that are not supported are" the public API of a public class
 * exposing a package-visible class, a public class extending a package-visible class that declares
 * public or protected members, a public class implementing a package-visible interface with fields,
 * and a public interface extending a package-visible interface.
 */
class AccessRulesTest {

    @TempDir
    Path out;

    private static final String HIDDEN = "package p;\nclass Hidden { short v; }\n";

    @Test
    void publicFieldOfAPackageVisibleType() throws Exception {
        assertThat(check(Map.of("Hidden", HIDDEN, "Pub", """
                package p;
                public class Pub { public Hidden leak = new Hidden(); }
                """))).singleElement().satisfies(v -> {
                    assertThat(v.className()).isEqualTo("p/Pub");
                    assertThat(v.context()).isEqualTo("field leak");
                    assertThat(v.message()).contains("p/Hidden", "2.2.1.1.6");
                });
    }

    @Test
    void publicMethodReturningAPackageVisibleType() throws Exception {
        assertThat(check(Map.of("Hidden", HIDDEN, "Pub", """
                package p;
                public class Pub { private final Hidden h = new Hidden(); public Hidden expose() { return h; } }
                """))).singleElement().satisfies(v -> assertThat(v.context()).isEqualTo("expose()Lp/Hidden;"));
    }

    @Test
    void protectedMethodWithAPackageVisibleParameterOrArray() throws Exception {
        assertThat(check(Map.of("Hidden", HIDDEN, "Pub", """
                package p;
                public class Pub {
                    protected void bump(Hidden x) { x.v++; }
                    public void all(Hidden[] xs) { }
                }
                """))).extracting(Violation::context).containsExactlyInAnyOrder("bump(Lp/Hidden;)V", "all([Lp/Hidden;)V");
    }

    /**
     * A constructor is a method ({@code <init>}) of the public API as well: export files list
     * constructors (JCVM 3.1 §5.9), so a public or protected constructor must not take a
     * package-visible type either. (The Oracle 3.0.5 converter, run as a black box, rejects the
     * public and protected cases and accepts the package-visible constructor too.)
     */
    @Test
    void publicOrProtectedConstructorWithAPackageVisibleParameter() throws Exception {
        assertThat(check(Map.of("Hidden", HIDDEN, "Pub", """
                package p;
                public class Pub {
                    public Pub(Hidden h) { }
                    protected Pub(Hidden[] hs) { }
                    Pub(short s, Hidden h) { }
                }
                """))).extracting(Violation::context)
                .containsExactlyInAnyOrder("<init>(Lp/Hidden;)V", "<init>([Lp/Hidden;)V");
    }

    @Test
    void packageVisibleSuperclassWithAPublicConstructor() throws Exception {
        assertThat(check(Map.of("Base", """
                package p;
                abstract class Base { public Base() { } }
                """, "Pub", """
                package p;
                public class Pub extends Base { }
                """))).singleElement().satisfies(v -> assertThat(v.message()).contains("package-visible class p/Base"));
    }

    @Test
    void packageVisibleSuperclassWithPublicMembers() throws Exception {
        assertThat(check(Map.of("Base", """
                package p;
                abstract class Base { public short helper() { return 1; } }
                """, "Pub", """
                package p;
                public class Pub extends Base { }
                """))).singleElement().satisfies(v -> {
                    assertThat(v.className()).isEqualTo("p/Pub");
                    assertThat(v.message()).contains("package-visible class p/Base");
                });
    }

    @Test
    void packageVisibleInterfaceWithFieldsImplementedByAPublicClass() throws Exception {
        assertThat(check(Map.of("Hid", """
                package p;
                interface Hid { short K = 5; void run(); }
                """, "Pub", """
                package p;
                public class Pub implements Hid { public void run() { } }
                """))).singleElement().satisfies(v -> assertThat(v.message()).contains("p/Hid", "fields"));
    }

    @Test
    void publicInterfaceExtendingAPackageVisibleInterface() throws Exception {
        assertThat(check(Map.of("Hid", """
                package p;
                interface Hid { void run(); }
                """, "Api", """
                package p;
                public interface Api extends Hid { void go(); }
                """))).singleElement().satisfies(v -> assertThat(v.message()).contains("public interface p/Api"));
    }

    /** Package-visible types used privately, by package-visible members or by private classes are fine. */
    @Test
    void packagePrivateUseIsAllowed() throws Exception {
        assertThat(check(Map.of("Hidden", HIDDEN, "Pub", """
                package p;
                public class Pub {
                    private Hidden h = new Hidden();
                    Hidden mine() { return h; }
                    private static class Helper { public Hidden get() { return null; } }
                    public Pub() { }
                    public short value() { return h.v; }
                }
                """, "Base", """
                package p;
                abstract class Base { short helper() { return 1; } }
                """, "Sub", """
                package p;
                public class Sub extends Base { public short twice() { return (short) (helper() * 2); } }
                """))).isEmpty();
    }

    private List<Violation> check(Map<String, String> sources) throws Exception {
        Path classes = out.resolve("c" + Math.abs(sources.hashCode()));
        Map<String, String> units = new HashMap<>();
        sources.forEach((name, text) -> units.put("p." + name, text));
        FixtureCompiler.compileSources(units, 8, classes);
        List<ClassInfo> infos = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                infos.add(ClassFileReader.readFile(p));
            }
        }
        return SubsetChecker.check(infos);
    }
}
