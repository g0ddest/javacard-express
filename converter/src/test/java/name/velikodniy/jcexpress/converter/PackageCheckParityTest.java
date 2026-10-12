package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.PackageCheck.Settings;
import name.velikodniy.jcexpress.converter.TestFixtures.Fixture;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PackageCheck} against the conversion it stands for, on the converter's test packages and every target
 * platform: a package the conversion accepts has no problem (the check must never stop a build the conversion
 * accepts), and a package the conversion rejects at the subset or the link stage has at least one. Where the check is
 * deliberately more lenient than the conversion, it says so in a test of its own.
 */
class PackageCheckParityTest {

    private static final String SUBSET = "JavaCard subset violations found";
    private static final String LINK = "Cannot link";
    private static final Settings TARGET_303 = new Settings(JavaCardVersion.V3_0_3, false, null);

    @TempDir
    Path classes;

    @Test
    void theCheckAgreesWithTheConversionOnEveryTestPackageAndPlatform_jcvm31_2_2_and_4_5_2() throws Exception {
        List<Fixture> fixtures = new ArrayList<>(TestFixtures.APPLETS);
        fixtures.add(TestFixtures.LIBRARY);
        fixtures.add(TestFixtures.GLOBAL_PLATFORM);
        fixtures.addAll(TestFixtures.REJECTED);
        List<String> disagreements = new ArrayList<>();
        int compared = 0;
        for (Fixture fixture : fixtures) {
            List<byte[]> files = classFiles(TestFixtures.CLASSES_DIR, fixture.pkg());
            for (JavaCardVersion version : JavaCardVersion.values()) {
                String conversion = conversion(fixture, version);
                List<PackageCheck.Problem> problems = PackageCheck.check(fixture.pkg(), files,
                        new Settings(version, fixture.int32(), null));
                boolean rejectedHere = conversion.startsWith(SUBSET) || conversion.startsWith(LINK);
                if (conversion.isEmpty() && !problems.isEmpty() || rejectedHere && problems.isEmpty()) {
                    disagreements.add(fixture + " " + version.specVersion() + ": conversion "
                            + (conversion.isEmpty() ? "OK" : conversion.lines().findFirst().orElse("")) + ", check "
                            + problems);
                }
                compared++;
            }
        }

        assertThat(compared).isEqualTo(fixtures.size() * JavaCardVersion.values().length);
        assertThat(disagreements).isEmpty();
    }

    /**
     * A method a class inherits from a built-in supertype while it also has a supertype in another package of the
     * build: only that package's export file tells whether it declares the method (JCVM 3.1 §4.3.3), so the check
     * leaves the reference to the conversion even where the built-in supertype lacks the method on the target. The
     * conversion, given the library's export file, reports it; the check misses this early error by design.
     */
    @Test
    void aMethodOfABuiltInSupertypeIsLeftToTheConversionNextToALibrarySupertype_jcvm31_4_3_3() throws Exception {
        JavaSources.compile(classes, Map.of("libm.Marker", """
                package libm;
                public interface Marker extends javacard.framework.Shareable {
                }
                """, "fn.FnApplet", fnApplet(" implements libm.Marker")));

        assertThat(PackageCheck.check("fn", classFiles(classes, "fn"), TARGET_303)).isEmpty();
    }

    /** The same call without the library supertype: the built-in data answers, as in the conversion (§4.5.2). */
    @Test
    void withoutALibrarySupertypeTheMethodTheTargetLacksIsAProblem_jcvm31_4_5_2() throws Exception {
        JavaSources.compile(classes, Map.of("fn.FnApplet", fnApplet("")));

        assertThat(PackageCheck.check("fn", classFiles(classes, "fn"), TARGET_303)).singleElement()
                .satisfies(problem -> assertThat(problem.message()).contains("reSelectingApplet")
                        .contains("introduced in Java Card 3.0.4"));
    }

    /** An applet that asks whether it is reselected, an API of Java Card 3.0.4 and later. */
    private static String fnApplet(String implementsClause) {
        return """
                package fn;
                import javacard.framework.*;
                public class FnApplet extends Applet%s {
                    public static void install(byte[] b, short o, byte l) { new FnApplet().register(); }
                    public void process(APDU apdu) {
                        if (reSelectingApplet()) {
                            return;
                        }
                    }
                }
                """.formatted(implementsClause);
    }

    /** The conversion's failure message, or the empty string when it converts. */
    private static String conversion(Fixture fixture, JavaCardVersion version) {
        try {
            fixture.builder(version).build().convert();
            return "";
        } catch (ConverterException e) {
            return e.getMessage();
        }
    }

    private static List<byte[]> classFiles(Path root, String packageName) throws Exception {
        List<byte[]> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(root.resolve(packageName.replace('.', '/')))) {
            for (Path file : list.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                files.add(Files.readAllBytes(file));
            }
        }
        return files;
    }
}
