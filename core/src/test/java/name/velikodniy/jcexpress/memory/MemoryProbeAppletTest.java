package name.velikodniy.jcexpress.memory;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MemoryProbeApplet} and {@link MemoryInfo}.
 *
 * <p>Java Card API {@code JCSystem.getAvailableMemory(byte)} is capped at 32767 bytes, so the probe uses
 * {@code getAvailableMemory(short[], short, byte)} (since Java Card 3.0.4), which reports a 32-bit amount.
 * jCardSim implements neither meaningfully (a constant 32767, and nothing written into the buffer), so on
 * the embedded backend the probe reports "not reported" and {@link MemoryInfo#from} refuses to make up
 * figures.</p>
 */
class MemoryProbeAppletTest {

    @Nested
    class OnJcardsim {

        private EmbeddedSession session;

        @BeforeEach
        void setUp() {
            session = new EmbeddedSession();
            session.install(MemoryProbeApplet.class);
        }

        @AfterEach
        void tearDown() {
            session.close();
        }

        @Test
        void probeAnswersTwelveBytes() {
            APDUResponse response = session.send(0x80, 0x01);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.data()).hasSize(12);
        }

        /** jCardSim does not model memory: the probe says so instead of reporting a constant 32767. */
        @Test
        void jcardsimReportsNoFiguresAndMemoryInfoSaysSo() {
            APDUResponse response = session.send(0x80, 0x01);

            assertThat(Hex.encode(response.data())).isEqualTo("FF".repeat(12));
            assertThatThrownBy(() -> MemoryInfo.from(response))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("does not report")
                    .hasMessageContaining("jCardSim");
        }

        @Test
        void unknownInstructionIsRejected() {
            assertThat(session.send(0x80, 0xFF).sw()).isEqualTo(0x6D00);
        }
    }

    @Nested
    class Decoding {

        /** Amounts above 32767 bytes are reported in full (32-bit, big-endian). */
        @Test
        void decodesThirtyTwoBitAmounts() {
            MemoryInfo info = MemoryInfo.from(new APDUResponse(Hex.decode("000186A0" + "00000800" + "00000400" + "9000")));

            assertThat(info.persistent()).isEqualTo(100_000);
            assertThat(info.transientDeselect()).isEqualTo(2048);
            assertThat(info.transientReset()).isEqualTo(1024);
            assertThat(info.toString()).contains("persistent=100000", "transientDeselect=2048", "transientReset=1024");
        }

        @Test
        void aSingleUnreportedTypeIsNamed() {
            APDUResponse response = new APDUResponse(Hex.decode("000186A0" + "FFFFFFFF" + "00000400" + "9000"));

            assertThatThrownBy(() -> MemoryInfo.from(response))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("transientDeselect");
        }

        @Test
        void rejectsErrorsAndWrongLengths() {
            assertThatThrownBy(() -> MemoryInfo.from(new APDUResponse(Hex.decode("6D00"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("6D00");
            assertThatThrownBy(() -> MemoryInfo.from(new APDUResponse(Hex.decode("00019000"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("12 bytes");
        }
    }
}
