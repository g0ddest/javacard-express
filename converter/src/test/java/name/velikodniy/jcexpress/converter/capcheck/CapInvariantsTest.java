package name.velikodniy.jcexpress.converter.capcheck;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.TestFixtures;
import name.velikodniy.jcexpress.converter.TestFixtures.Fixture;
import name.velikodniy.jcexpress.converter.testutil.OracleReferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Converts every test package in the DEFAULT mode (the mode the Maven plugin uses) and checks
 * the resulting CAP file against the structural invariants of JCVM 3.1 Chapter 6.
 */
class CapInvariantsTest {

    static final List<Fixture> APPLETS = TestFixtures.APPLETS;

    @ParameterizedTest(name = "{0}")
    @FieldSource("APPLETS")
    void defaultModeCapSatisfiesSpecInvariants(Fixture fixture) throws Exception {
        CapImage cap = CapImage.parse(fixture.convert().capFile());

        assertThat(CapInvariants.check(cap)).as(fixture.label()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @FieldSource("APPLETS")
    void capFormat23SatisfiesSpecInvariants(Fixture fixture) throws Exception {
        CapImage cap = CapImage.parse(fixture.builder(JavaCardVersion.V3_2_0).build().convert().capFile());

        assertThat(CapInvariants.check(cap)).as(fixture.label() + " (CAP 2.3)").isEmpty();
    }

    @Test
    void libraryCapSatisfiesSpecInvariants() throws Exception {
        CapImage cap = CapImage.parse(TestFixtures.LIBRARY.convert().capFile());

        assertThat(CapInvariants.check(cap)).isEmpty();
    }

    /**
     * Sanity check of the checker itself: CAP files produced by the Oracle converter (black-box
     * reference output, generated only in local checkouts, see {@link OracleReferences}) must not be
     * reported.
     */
    @Test
    void oracleReferenceCapsSatisfyTheCheckerWhenPresent() throws IOException {
        Path referenceDir = OracleReferences.directory();
        assumeTrue(Files.isDirectory(referenceDir), "Oracle reference CAPs not available");
        List<Path> caps;
        try (Stream<Path> files = Files.list(referenceDir)) {
            caps = files.filter(p -> p.toString().endsWith(".cap")).sorted().toList();
        }
        assumeTrue(!caps.isEmpty(), "Oracle reference CAPs not available");
        for (Path p : caps) {
            assertThat(CapInvariants.check(CapImage.parse(Files.readAllBytes(p))))
                    .as(p.getFileName().toString()).isEmpty();
        }
    }
}
