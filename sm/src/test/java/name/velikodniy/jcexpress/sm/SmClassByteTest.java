package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Class byte of protected commands (ISO/IEC 7816-4 5.4.1).
 *
 * <p>In the first interindustry class ({@code 000x xxxx}, Table 2) b5 is command chaining, b4-b3 the secure messaging
 * indication ({@code 11}: SM with authenticated header, i.e. CLA '0C' on the basic channel as required by ICAO Doc
 * 9303-11 9.8.4) and b2-b1 the logical channel; in the further interindustry class ({@code 01xx xxxx}, Table 3) b6 is
 * the SM indication and b4-b1 encode channels 4-19. Chaining and channel bits must survive protection. Vectors:
 * {@code cla.properties}.</p>
 */
class SmClassByteTest {

    private static final SmVectors VECTORS = SmVectors.load("cla.properties");

    static List<SmVectors.Step> steps() {
        return VECTORS.steps();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("steps")
    void channelAndChainingBitsAreKept(SmVectors.Step step) {
        SMContext ctx = VECTORS.context(SMAlgorithm.DES3, step);

        byte[] wrapped = SMCodec.wrapCommand(ctx, step.plain());

        assertThat(Hex.encode(wrapped)).isEqualTo(Hex.encode(step.command()));
        APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());
        assertThat(Hex.encode(response.data())).isEqualTo(Hex.encode(step.data()));
    }

    @ParameterizedTest(name = "CLA {0}")
    @ValueSource(strings = {"20", "3F", "FF"})
    void reservedAndInvalidClassBytesAreRejected(String cla) {
        SMContext ctx = VECTORS.context(SMAlgorithm.DES3, VECTORS.step(0));

        assertThatThrownBy(() -> SMCodec.wrapCommand(ctx, Hex.decode(cla + "B0000004")))
                .isInstanceOf(SMException.class)
                .hasMessageContaining("CLA");
    }
}
