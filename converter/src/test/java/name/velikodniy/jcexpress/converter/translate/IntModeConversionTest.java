package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversion for a target with int support (supportInt32, JCVM 3.1 §2.2.3.1) through the whole
 * pipeline. Before, the option replaced every JVM int instruction by the JCVM int instruction
 * and set ACC_INT unconditionally, so even int-free applets became unverifiable ("Bad local
 * number") and unloadable on cards without int support.
 */
class IntModeConversionTest {

    static final BytecodeFixture INTMODE = new BytecodeFixture("intmode", "IntModeApplet", 0x0A);

    /** The converter's int arithmetic test applet (int field, +, *, &, negation, shifts). */
    static Converter.Builder intOps() {
        return Converter.builder()
                .classesDirectory(BytecodeFixture.TEST_CLASSES)
                .packageName("com.example.intops")
                .packageAid("A000000062070101")
                .packageVersion(1, 0)
                .applet("com.example.intops.IntOpsApplet", "A00000006207010101");
    }

    /** Int packages checked with the off-card verifier (int instance fields take two cells, JCVM 3.1 §6.9). */
    static Stream<Converter.Builder> intPackages() {
        return Stream.of(INTMODE.builder(BytecodeFixture.TEST_CLASSES), intOps());
    }

    /** IntOpsApplet converts with int support and sets ACC_INT (its int field and arithmetic). */
    @Test
    void intOpsAppletConvertsWithIntSupport() throws Exception {
        CapView cap = CapView.parse(intOps().supportInt32(true).build().convert().capFile());
        assertThat(cap.headerFlags() & 0x01).as("ACC_INT").isEqualTo(1);
        assertThat(cap.disassembledMethods()).anySatisfy(m -> assertThat(m)
                .contains("getfield_i_this #0", "bipush 100", "iadd", "imul", "sipush 255", "iand", "ineg")
                .doesNotContain("sadd", "smul", "sand", "sneg"));
    }

    /**
     * JCVM 3.1 §6.4: without a use of int, ACC_INT is 0, and code without int values is the same
     * as for a target without int support.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("name.velikodniy.jcexpress.converter.translate.BytecodeFixtureConversionTest#verifiableFixtures")
    void intFreePackageIsUnchangedByIntSupport_6_4(BytecodeFixture fixture) throws Exception {
        CapView without = CapView.parse(fixture.builder(BytecodeFixture.TEST_CLASSES).build().convert().capFile());
        CapView with = CapView.parse(fixture.builder(BytecodeFixture.TEST_CLASSES).supportInt32(true)
                .build().convert().capFile());

        assertThat(with.headerFlags() & 0x01).as("ACC_INT").isZero();
        assertThat(with.methodInfo()).as("Method component").isEqualTo(without.methodInfo());
    }

    @Test
    void packageThatUsesIntSetsAccInt_6_4() throws Exception {
        CapView cap = CapView.parse(INTMODE.builder(BytecodeFixture.TEST_CLASSES).supportInt32(true)
                .build().convert().capFile());
        assertThat(cap.headerFlags() & 0x01).as("ACC_INT").isEqualTo(1);
        assertThat(cap.disassembledMethods()).anySatisfy(m -> assertThat(m).contains("icmp", "s2i"));
    }

    @ParameterizedTest
    @MethodSource("intPackages")
    @EnabledIf("name.velikodniy.jcexpress.converter.translate.OracleVerifier#available")
    void intPackagePassesOracleOffCardVerifier(Converter.Builder builder) throws Exception {
        ConverterResult result = builder.supportInt32(true).build().convert();
        String out = OracleVerifier.verify(result.capFile());
        System.out.println("verifycap (int support):\n" + out);
        assertThat(out).contains("0 errors");
    }
}
