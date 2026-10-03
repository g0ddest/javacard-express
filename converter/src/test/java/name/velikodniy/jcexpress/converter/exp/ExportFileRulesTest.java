package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Cls;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Field;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Method;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Pkg;
import name.velikodniy.jcexpress.converter.testutil.ExportFileRules;
import name.velikodniy.jcexpress.converter.testutil.OracleSdkExports;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The export file checker used by {@link GeneratedExportFilesTest} detects each rule it claims
 * to check (JCVM 3.1 Chapter 5, §4.3.7); the fixtures are written by the independent
 * {@link ExpFixture}.
 */
class ExportFileRulesTest {

    private static final String OBJECT = "java/lang/Object";
    private static final String SHAREABLE = "javacard/framework/Shareable";
    private static final Pkg LIB = new Pkg("p", 1, 1, 0, "A0000000FF01");
    private static final Pkg APPLETS = new Pkg("p", 0, 1, 0, "A0000000FF01");
    private static final Pkg LANG = new Pkg("java/lang", 1, 1, 0, "A0000000620001");
    private static final Pkg FRAMEWORK = new Pkg("javacard/framework", 1, 1, 6, "A0000000620101");

    /** A conforming public class: constructor, static and virtual method, constant, fields. */
    private static Cls goodClass() {
        return new Cls(0, 0x0001, "p/C", List.of(OBJECT), List.of(),
                List.of(new Field(0, 0x0001, "flag", "B", null), new Field(1, 0x0004, "data", "[B", null),
                        new Field(0, 0x0009, "table", "[S", null), new Field(0xFF, 0x0019, "K", "S", 7)),
                List.of(new Method(0, 0x0001, "<init>", "()V"), new Method(1, 0x0009, "make", "()Lp/C;"),
                        new Method(0, 0x0001, "equals", "(Ljava/lang/Object;)Z"), new Method(1, 0x0004, "run", "()V")),
                2);
    }

    /** A conforming shareable interface. */
    private static Cls goodInterface(int token) {
        return new Cls(token, 0x0E01, "p/I", List.of(OBJECT), List.of(SHAREABLE), List.of(),
                List.of(new Method(0, 0x0401, "ping", "(S)S")), 0);
    }

    private static List<String> check(int formatMinor, Pkg pkg, List<Pkg> refs, Cls... classes) throws Exception {
        byte[] exp = ExpFixture.write(2, formatMinor, pkg, refs, List.of(classes));
        return ExportFileRules.check(ExportFileReader.read(exp), pkg.flags() == 1, formatMinor);
    }

    @Test
    void conformingFilesHaveNoViolations() throws Exception {
        assertThat(check(1, LIB, List.of(), goodClass(), goodInterface(1))).isEmpty();
        assertThat(check(3, LIB, List.of(LANG, FRAMEWORK), goodClass(), goodInterface(1))).isEmpty();
        assertThat(check(1, APPLETS, List.of(), goodInterface(4))).isEmpty();
    }

    @Test
    void packageRulesAreChecked() throws Exception {
        assertThat(ExportFileRules.check(ExportFileReader.read(ExpFixture.write(2, 1, LIB, List.of(),
                List.of(goodClass()))), true, 3)).containsExactly("5.5 export file format 2.1, expected 2.3");
        assertThat(ExportFileRules.check(ExportFileReader.read(ExpFixture.write(2, 1, APPLETS, List.of(),
                List.of(goodInterface(0)))), true, 1)).containsExactly("5.6.1 package flags 0x0 for a library");
        assertThat(check(1, APPLETS, List.of(), goodClass()))
                .containsExactly("5.5 p/C: a package with applets exports only shareable interfaces");
        assertThat(check(1, LIB, List.of(), goodClass(), goodInterface(2)))
                .containsExactly("4.3.7.2 class tokens [0, 2] of a library are not 0..1");
    }

    @Test
    void classRulesAreChecked() throws Exception {
        Cls notPublic = new Cls(0, 0x0000, "p/C", List.of(OBJECT), List.of(), List.of(), List.of(), 0);
        Cls ifaceSupers = new Cls(0, 0x0E01, "p/I", List.of(OBJECT, "p/X"), List.of(SHAREABLE), List.of(),
                List.of(), 0);
        Cls notFlaggedShareable = new Cls(0, 0x0601, "p/I", List.of(OBJECT), List.of(SHAREABLE), List.of(),
                List.of(), 0);

        assertThat(check(1, LIB, List.of(), notPublic)).singleElement().asString()
                .startsWith("5.7 p/C: class flags 0x0");
        assertThat(check(1, LIB, List.of(), ifaceSupers)).singleElement().asString()
                .startsWith("5.7 p/I: the supers of an interface are [java/lang/Object]");
        assertThat(check(1, LIB, List.of(), notFlaggedShareable)).singleElement().asString()
                .startsWith("5.7 p/I: ACC_SHAREABLE false");
    }

    @Test
    void fieldRulesAreChecked() throws Exception {
        List<String> violations = new ArrayList<>();
        violations.addAll(check(1, LIB, List.of(), withFields(new Field(0xFF, 0x0019, "K", "S", null))));
        violations.addAll(check(1, LIB, List.of(), withFields(new Field(0, 0x0019, "K", "S", 7))));
        violations.addAll(check(1, LIB, List.of(), withFields(new Field(0, 0x0005, "x", "S", null))));
        violations.addAll(check(1, LIB, List.of(), withFields(new Field(0, 0x0001, "r", "[B", null),
                new Field(1, 0x0001, "s", "S", null))));
        violations.addAll(check(1, LIB, List.of(), withFields(new Field(1, 0x0009, "t", "[B", null))));

        assertThat(violations).hasSize(5).satisfiesExactly(
                v -> assertThat(v).startsWith("5.8 p/C.K: a ConstantValue attribute is required"),
                v -> assertThat(v).startsWith("5.8 p/C.K: compile-time constant with token 0"),
                v -> assertThat(v).startsWith("5.8 p/C.x: flags 0x5"),
                v -> assertThat(v).startsWith("4.3.7.5 p/C: instance field tokens"),
                v -> assertThat(v).startsWith("4.3.7.3 p/C: static field tokens [1]"));
    }

    @Test
    void methodRulesAreChecked() throws Exception {
        List<String> violations = new ArrayList<>();
        violations.addAll(check(1, LIB, List.of(), withMethods(new Method(0, 0x0009, "<init>", "()V"))));
        violations.addAll(check(1, LIB, List.of(), withMethods(new Method(0, 0x0001, "a", "()V"),
                new Method(2, 0x0001, "b", "()V"))));
        violations.addAll(check(1, LIB, List.of(), withMethods(new Method(1, 0x0009, "s", "()V"))));
        violations.addAll(check(1, LIB, List.of(), new Cls(0, 0x0E01, "p/I", List.of(OBJECT), List.of(SHAREABLE),
                List.of(), List.of(new Method(1, 0x0401, "ping", "(S)S")), 0)));

        assertThat(violations).hasSize(4).satisfiesExactly(
                v -> assertThat(v).startsWith("5.9 p/C.<init>()V: no static initializer is exported"),
                v -> assertThat(v).startsWith("4.3.7.6 p/C: virtual method tokens [0, 2]"),
                v -> assertThat(v).startsWith("4.3.7.4 p/C: static method tokens [1]"),
                v -> assertThat(v).startsWith("4.3.7.7 p/I: interface method tokens [1]"));
    }

    @Test
    void descriptorAndReferencedPackageRulesAreChecked() throws Exception {
        Cls usesHidden = withMethods(new Method(0, 0x0009, "use", "(Lp/Hidden;)V"));

        assertThat(check(1, LIB, List.of(), usesHidden)).singleElement().asString()
                .startsWith("5.9 p/C: a descriptor names p/Hidden");
        assertThat(check(3, LIB, List.of(), goodClass())).containsExactly(
                "5.5 referenced_packages [], expected [java/lang]");
    }

    /**
     * Black-box calibration of the checker: the API export files of the Oracle kits 2.1.2-3.2.0,
     * written by the owner of the API (JCVM 3.1 §5.3), satisfy every rule. Skipped without the kits.
     */
    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    @EnabledIf("name.velikodniy.jcexpress.converter.testutil.OracleSdkExports#allAvailable")
    void apiExportFilesOfTheKitsSatisfyEveryRule(JavaCardVersion version) throws Exception {
        List<String> violations = new ArrayList<>();
        for (var entry : OracleSdkExports.read(version).entrySet()) {
            ExportFile ef = ExportFileReader.read(entry.getValue());
            ExportFileRules.check(ef, true, ef.formatMinor()).forEach(v -> violations.add(entry.getKey() + ": " + v));
        }

        assertThat(violations).isEmpty();
    }

    private static Cls withFields(Field... fields) {
        return new Cls(0, 0x0001, "p/C", List.of(OBJECT), List.of(), List.of(fields), List.of(), 0);
    }

    private static Cls withMethods(Method... methods) {
        return new Cls(0, 0x0001, "p/C", List.of(OBJECT), List.of(), List.of(), List.of(methods), 0);
    }
}
