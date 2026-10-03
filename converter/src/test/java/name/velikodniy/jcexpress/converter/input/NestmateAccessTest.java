package name.velikodniy.jcexpress.converter.input;

import name.velikodniy.jcexpress.converter.check.SubsetChecker;
import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.translate.FixtureCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * javac 11+ nestmate access (JEP 181) to private members of another class of the package is
 * mapped to package access (JCVM 3.1 §2.2.1.1.6: the Java Card platform supports the package
 * access control of the Java language; a private member is only accessible inside its class).
 */
class NestmateAccessTest {

    private static final String PKG = "com.example.bytecode.nest";
    private static final String OUTER = "com/example/bytecode/nest/NestApplet";
    private static final int ACC_PRIVATE = 0x0002;

    @TempDir
    Path out;

    @ParameterizedTest(name = "--release {0}")
    @ValueSource(ints = {11, 17, 25})
    void nestmateAccessedPrivateMembersBecomePackageVisible(int release) throws Exception {
        Path classes = FixtureCompiler.compilePackage(PKG, release, out.resolve("r" + release));
        assertThat(helperAccessesOuterFieldsDirectly(classes)).as("javac emits nestmate access").isTrue();

        ClassInfo outer = scan(classes, OUTER);

        assertThat(method(outer, "bump", "(S)S").isPrivate()).isFalse();
        assertThat(method(outer, "<init>", "()V").isPrivate()).isFalse();
        assertThat(field(outer, "secret").accessFlags() & ACC_PRIVATE).isZero();
        assertThat(field(outer, "data").accessFlags() & ACC_PRIVATE).isZero();
    }

    @Test
    void javac8BridgesKeepPrivateMembersPrivate() throws Exception {
        Path classes = FixtureCompiler.compilePackage(PKG, 8, out.resolve("r8"));
        assertThat(helperAccessesOuterFieldsDirectly(classes)).isFalse();

        ClassInfo outer = scan(classes, OUTER);

        assertThat(method(outer, "bump", "(S)S").isPrivate()).isTrue();
        assertThat(field(outer, "secret").accessFlags() & ACC_PRIVATE).isNotZero();
    }

    @Test
    void privateMembersUsedOnlyInsideTheirClassStayPrivate() throws Exception {
        FixtureCompiler.compileSource("p.Solo", """
                package p;
                public class Solo {
                    private short v;
                    private short get() { return v; }
                    short twice() { return (short) (get() + get()); }
                }
                """, 25, out);
        ClassInfo solo = scan(out, "p/Solo");
        assertThat(method(solo, "get", "()S").isPrivate()).isTrue();
        assertThat(field(solo, "v").accessFlags() & ACC_PRIVATE).isNotZero();
    }

    /**
     * A private method is never overridden; a package-visible one would be overridden by the
     * method of the same name and descriptor in {@code Sub}. Such a method stays private and the
     * nestmate access to it is reported with its location instead of being silently changed.
     */
    @Test
    void methodThatASubclassWouldOverrideStaysPrivateAndIsReported() throws Exception {
        FixtureCompiler.compileSource("p.Outer", """
                package p;
                public class Outer {
                    private short m() { return 1; }
                    static class Inner {
                        short call(Outer o) {
                            return o.m();
                        }
                    }
                    static class Sub extends Outer {
                        short m() { return 2; }
                    }
                }
                """, 25, out);
        List<ClassInfo> classes = PackageScanner.scan(out, "p").classes();
        ClassInfo outer = classes.stream().filter(c -> c.thisClass().equals("p/Outer")).findFirst().orElseThrow();
        assertThat(method(outer, "m", "()S").isPrivate()).isTrue();

        List<Violation> violations = SubsetChecker.check(classes);

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.className()).isEqualTo("p/Outer$Inner");
            assertThat(v.context()).isEqualTo("call(Lp/Outer;)S");
            assertThat(v.line()).isEqualTo(6);
            assertThat(v.message()).contains("nestmate access", "p/Outer.m()S", "p/Outer$Sub",
                    "2.2.1.1.6", "--release 10");
        });
    }

    private static boolean helperAccessesOuterFieldsDirectly(Path classes) throws Exception {
        ClassModel helper = ClassFile.of().parse(Files.readAllBytes(classes.resolve(OUTER + "$Helper.class")));
        return helper.methods().stream().flatMap(m -> m.code().stream())
                .flatMap(c -> c.elementStream())
                .anyMatch(e -> e instanceof FieldInstruction f && f.owner().asInternalName().equals(OUTER));
    }

    private static ClassInfo scan(Path classes, String internalName) throws Exception {
        String pkg = internalName.substring(0, internalName.lastIndexOf('/')).replace('/', '.');
        return PackageScanner.scan(classes, pkg).classes().stream()
                .filter(c -> c.thisClass().equals(internalName)).findFirst().orElseThrow();
    }

    private static MethodInfo method(ClassInfo ci, String name, String descriptor) {
        return ci.methods().stream().filter(m -> m.name().equals(name) && m.descriptor().equals(descriptor))
                .findFirst().orElseThrow();
    }

    private static FieldInfo field(ClassInfo ci, String name) {
        return ci.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }
}
