package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.ConverterResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regression tests: the bytecode fixtures are converted through the complete pipeline
 * (real constant pool and reference resolution) and the resulting Method component is checked.
 * When the Oracle SDK is present, every fixture CAP must also pass the off-card verifier.
 */
class BytecodeFixtureConversionTest {

    static final BytecodeFixture ARRLEN = new BytecodeFixture("arrlen", "ArrLenApplet", 0x01);
    static final BytecodeFixture DUPX = new BytecodeFixture("dupx", "DupXApplet", 0x02);
    static final BytecodeFixture STATTHIS = new BytecodeFixture("statthis", "StatThisApplet", 0x03);
    static final BytecodeFixture CASTARR = new BytecodeFixture("castarr", "CastArrApplet", 0x04);
    static final BytecodeFixture CATCHREMAP = new BytecodeFixture("catchremap", "CatchRemapApplet", 0x05);
    static final BytecodeFixture CATCH0 = new BytecodeFixture("catch0", "Catch0Applet", 0x06);
    static final BytecodeFixture WIDEBR = new BytecodeFixture("widebr", "WideBranchApplet", 0x07);
    static final BytecodeFixture TRYNEST = new BytecodeFixture("trynest", "TryNestApplet", 0x08);
    static final BytecodeFixture NEST = new BytecodeFixture("nest", "NestApplet", 0x09);
    static final BytecodeFixture IDX = new BytecodeFixture("idx", "IndexApplet", 0x0B);

    /** Fixtures checked with the off-card verifier (all of them, in the default mode). */
    static Stream<BytecodeFixture> verifiableFixtures() {
        return Stream.of(ARRLEN, DUPX, STATTHIS, CASTARR, CATCHREMAP, CATCH0, WIDEBR, TRYNEST, NEST, IDX);
    }

    static Stream<BytecodeFixture> catchFixtures() {
        return Stream.of(CATCHREMAP, CATCH0, TRYNEST);
    }

    /**
     * JCVM 3.1 §6.10.3: a non-zero catch_type_index "must be a valid index into the
     * constant_pool[] array ... a CONSTANT_Classref_info structure", catch blocks are never at
     * index 0, and §6.12: the Reference Location component lists every catch_type_index.
     * The fixtures have no finally blocks except TRYNEST, whose finally handlers are excluded.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("catchFixtures")
    void catchTypesReferToClassrefsAfterConstantPoolReordering_6_10_3(BytecodeFixture fixture) throws Exception {
        CapView cap = CapView.parse(fixture.convert(false).capFile());
        List<CapView.Handler> typed = cap.handlers().stream().filter(h -> h.catchTypeIndex() != 0).toList();
        assertThat(typed).as("typed handlers").hasSize(expectedTypedHandlers(fixture));
        for (CapView.Handler h : typed) {
            assertThat(cap.constantPool().get(h.catchTypeIndex()).tag())
                    .as("catch type #%d of handler at %d", h.catchTypeIndex(), h.handlerOffset())
                    .isEqualTo(1);
        }
        for (int i = 0; i < cap.handlers().size(); i++) {
            if (cap.handlers().get(i).catchTypeIndex() != 0) {
                assertThat(cap.byte2IndexOffsets()).as("RefLocation entry of handler %d", i)
                        .contains(1 + 8 * i + 6);
            }
        }
    }

    private static int expectedTypedHandlers(BytecodeFixture fixture) {
        if (fixture == TRYNEST) {
            return 6; // CryptoException, ISOException, NPE, AIOOBE, ISOException in the loop and before the rethrow
        }
        return 2;
    }

    /** JCVM 3.1 §7.5.38: a conditional jump over more than 127 bytes uses if_scmp&lt;cond&gt;_w. */
    @Test
    void longForwardJumpUsesTwoByteBranchOffset_7_5_38() throws Exception {
        CapView cap = CapView.parse(WIDEBR.convert(false).capFile());
        CapView.MethodBody process = cap.methods().stream()
                .filter(m -> m.lines().stream().anyMatch(l -> l.startsWith("if_scmpne_w")))
                .findFirst().orElseThrow(() -> new AssertionError("no if_scmpne_w in any method"));
        List<JcvmDisassembler.Insn> insns = JcvmDisassembler.disassemble(process.code());
        JcvmDisassembler.Insn branch = insns.stream()
                .filter(i -> i.mnemonic().equals("if_scmpne_w")).findFirst().orElseThrow();
        int target = Integer.parseInt(branch.operands().substring(3));
        assertThat(target - branch.pc()).isGreaterThan(Byte.MAX_VALUE);
        assertThat(insns).as("branch target is an instruction boundary")
                .anySatisfy(i -> assertThat(i.pc()).isEqualTo(target));
    }

    @Test
    void arrayTypeChecksConvertWithRealConstantPool_7_5_16() throws Exception {
        CapView cap = CapView.parse(CASTARR.convert(false).capFile());
        List<String> checks = cap.disassembledMethods().stream().flatMap(List::stream)
                .filter(l -> l.startsWith("checkcast") || l.startsWith("instanceof")).toList();
        assertThat(checks).hasSize(4);
        assertThat(checks).contains("checkcast 11 #0", "instanceof 12 #0", "instanceof 10 #0");
        String refArray = checks.stream().filter(l -> l.startsWith("checkcast 14 ")).findFirst().orElseThrow();
        int cpIndex = Integer.parseInt(refArray.substring(refArray.indexOf('#') + 1));
        assertThat(cap.constantPool().get(cpIndex).tag()).as("CONSTANT_Classref").isEqualTo(1);
    }

    /**
     * javac 11+ nestmate access (the test classes are compiled with the project's release): the
     * nested helper calls the outer class's private method with invokevirtual. The method is made
     * package-visible, so the call stays a virtual call (JCVM 3.1 §7.5.57); before, it became an
     * invokespecial of a private method of another class, which the verifier rejects
     * (§2.2.1.1.6).
     */
    @Test
    void nestmateCallOfAPrivateMethodBecomesAPackageVirtualCall_2_2_1_1_6() throws Exception {
        CapView cap = CapView.parse(NEST.convert(false).capFile());
        List<String> run = cap.disassembledMethods().stream()
                .filter(m -> m.stream().anyMatch(l -> l.startsWith("getfield_a #"))
                        && m.stream().anyMatch(l -> l.startsWith("putfield_s #")))
                .findFirst().orElseThrow(() -> new AssertionError("Helper.run not found"));
        assertThat(run).as("Helper.run: n.secret = 3; return n.bump((short) 2)")
                .anyMatch(l -> l.startsWith("invokevirtual #"))
                .noneMatch(l -> l.startsWith("invokespecial"));
    }

    /**
     * JCVM 3.1 §2.2.1.1.8 and §2.2.3.1: index and size expressions whose value is a short for
     * every value of their operands (a byte nibble, {@code x & 3}, a byte plus one, ...) convert
     * without int support, with short instructions. (With int support the Method component is
     * the same, see IntModeConversionTest.)
     */
    @Test
    void shortValuedIndexExpressionsConvertWithShortInstructions_2_2_1_1_8() throws Exception {
        CapView cap = CapView.parse(IDX.convert(false).capFile());
        assertThat(cap.headerFlags() & 0x01).as("ACC_INT").isZero();
        assertThat(cap.disassembledMethods()).anySatisfy(m -> assertThat(m)
                .contains("sshr", "sand", "sadd", "smul", "newarray 11"));
    }

    @Test
    void arraylengthSurvivesThePipeline_7_5_8() throws Exception {
        CapView cap = CapView.parse(ARRLEN.convert(false).capFile());
        assertThat(cap.disassembledMethods()).anySatisfy(m -> assertThat(m).contains("arraylength"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verifiableFixtures")
    @EnabledIf("name.velikodniy.jcexpress.converter.translate.OracleVerifier#available")
    void fixturePassesOracleOffCardVerifier(BytecodeFixture fixture) throws Exception {
        ConverterResult result = fixture.convert(false);
        assertThat(CapView.parse(result.capFile()).descriptorHandlerRangesFollowSpec())
                .as("Descriptor exception_handler_index/count per JCVM 3.1 6.14").isTrue();
        String out = OracleVerifier.verify(result.capFile());
        System.out.println("verifycap " + fixture + ":\n" + out);
        assertThat(out).as(fixture + " verifycap output").contains("0 errors");
    }
}
