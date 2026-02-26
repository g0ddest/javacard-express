package name.velikodniy.jcexpress.apdu;

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
 * Tests for {@link APDUBuilder}.
 */
class APDUBuilderTest {

    // ── Case 1: CLA INS P1 P2 ──

    @Nested
    class Case1NoDataNoLe {

        @Test
        void shouldBuildMinimalApdu() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A40400");
        }

        @Test
        void shouldDefaultToZeros() {
            byte[] apdu = APDUBuilder.command().build();
            assertThat(Hex.encode(apdu)).isEqualTo("00000000");
        }
    }

    // ── Case 2: CLA INS P1 P2 Le ──

    @Nested
    class Case2WithLe {

        @Test
        void shouldAppendLe() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xCA).p1(0x00).p2(0xCF)
                    .le(0)
                    .build();

            // Le=0 means "expect 256 bytes", encoded as 0x00
            assertThat(Hex.encode(apdu)).isEqualTo("00CA00CF00");
        }

        @Test
        void shouldAppendExplicitLe() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x80).ins(0x50).p1(0x00).p2(0x00)
                    .le(8)
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("805000" + "0008");
        }

        @Test
        void shouldEncodeLe256AsZero() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xC0).p1(0x00).p2(0x00)
                    .le(256)
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00C0000000");
        }
    }

    // ── Case 3: CLA INS P1 P2 Lc Data ──

    @Nested
    class Case3WithData {

        @Test
        void shouldBuildWithByteData() {
            byte[] data = Hex.decode("A0000000031010");

            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .data(data)
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000031010");
        }

        @Test
        void shouldBuildWithHexData() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .data("A0000000031010")
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000031010");
        }

        @Test
        void shouldAcceptSpacedHexData() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .data("A0 00 00 00 03 10 10")
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000031010");
        }
    }

    // ── Case 4: CLA INS P1 P2 Lc Data Le ──

    @Nested
    class Case4WithDataAndLe {

        @Test
        void shouldBuildWithDataAndLe() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .data("A0000000031010")
                    .le(0)
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A000000003101000");
        }
    }

    // ── Factory methods ──

    @Nested
    class FactoryMethods {

        @Test
        void selectShouldBuildCorrectApdu() {
            byte[] apdu = APDUBuilder.select("A0000000031010").build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000031010");
        }

        @Test
        void selectWithBytesShouldWork() {
            byte[] aid = Hex.decode("A0000000041010");
            byte[] apdu = APDUBuilder.select(aid).build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000041010");
        }

        @Test
        void getDataShouldBuildCorrectApdu() {
            byte[] apdu = APDUBuilder.getData(0x00, 0xCF).build();

            assertThat(Hex.encode(apdu)).isEqualTo("00CA00CF00");
        }

        @Test
        void getResponseShouldBuildCorrectApdu() {
            byte[] apdu = APDUBuilder.getResponse(32).build();

            assertThat(Hex.encode(apdu)).isEqualTo("00C0000020");
        }
    }

    // ── Validation ──

    @Nested
    class Validation {

        @Test
        void shouldRejectClaOutOfRange() {
            assertThatThrownBy(() -> APDUBuilder.command().cla(0x100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLA");
        }

        @Test
        void shouldRejectNegativeCla() {
            assertThatThrownBy(() -> APDUBuilder.command().cla(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLA");
        }

        @Test
        void shouldRejectInsOutOfRange() {
            assertThatThrownBy(() -> APDUBuilder.command().ins(256))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INS");
        }

        @Test
        void shouldRejectP1OutOfRange() {
            assertThatThrownBy(() -> APDUBuilder.command().p1(300))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("P1");
        }

        @Test
        void shouldRejectP2OutOfRange() {
            assertThatThrownBy(() -> APDUBuilder.command().p2(-5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("P2");
        }

        @Test
        void shouldRejectDataOver65535Bytes() {
            byte[] longData = new byte[65536];
            assertThatThrownBy(() -> APDUBuilder.command().data(longData))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("65535");
        }

        @Test
        void shouldRejectLeOutOfRange() {
            assertThatThrownBy(() -> APDUBuilder.command().le(65537))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Le");
        }

        @Test
        void shouldRejectNegativeLe() {
            assertThatThrownBy(() -> APDUBuilder.command().le(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Le");
        }

        @Test
        void shouldAcceptNullData() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .data((byte[]) null)
                    .build();

            assertThat(Hex.encode(apdu)).isEqualTo("00A40400");
        }
    }

    // ── Logical channel ──

    @Nested
    class LogicalChannelEncoding {

        @Test
        void channelShouldEncodeClaInBuild() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                    .channel(2)
                    .build();

            assertThat(apdu[0] & 0xFF).isEqualTo(0x02); // CLA with channel 2
        }

        @Test
        void channelShouldRejectInvalidValue() {
            assertThatThrownBy(() -> APDUBuilder.command().channel(4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0-3");
        }
    }

    // ── Extended APDU ──

    @Nested
    class ExtendedApdu {

        @Test
        void shouldBuildExtendedWithLargeData() {
            byte[] data = new byte[300];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xFF);

            byte[] apdu = APDUBuilder.command()
                    .cla(0x80).ins(0x01).p1(0x00).p2(0x00)
                    .data(data)
                    .build();

            // Header(4) + marker(1) + Lc(2) + data(300) = 307
            assertThat(apdu.length).isEqualTo(307);
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00); // extended marker
            int lc = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            assertThat(lc).isEqualTo(300);
        }

        @Test
        void shouldBuildExtendedWithLargeLe() {
            byte[] apdu = APDUBuilder.command()
                    .cla(0x00).ins(0xCA).p1(0x00).p2(0xCF)
                    .le(1000)
                    .build();

            assertThat(apdu.length).isEqualTo(7); // Case 2E
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00);
            int le = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            assertThat(le).isEqualTo(1000);
        }

        @Test
        void shouldRejectDataOver65535() {
            byte[] hugeData = new byte[65536];
            assertThatThrownBy(() -> APDUBuilder.command().data(hugeData))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("65535");
        }

        @Test
        void shouldRejectLeOver65536() {
            assertThatThrownBy(() -> APDUBuilder.command().le(65537))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Le");
        }
    }

    // ── toString ──

    @Test
    void toStringShouldReturnSpacedHex() {
        String s = APDUBuilder.command()
                .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                .data("A0000000031010")
                .toString();

        assertThat(s).isEqualTo("00 A4 04 00 07 A0 00 00 00 03 10 10");
    }

    // ── Integration with SmartCardSession ──

    @Nested
    class Integration {

        private EmbeddedSession session;

        @BeforeEach
        void setUp() {
            session = new EmbeddedSession(false);
        }

        @AfterEach
        void tearDown() {
            session.close();
        }

        @Test
        void sendToShouldTransmitAndReturnResponse() {
            // Install HelloWorldApplet and use builder to send command
            session.install(name.velikodniy.jcexpress.HelloWorldApplet.class);

            APDUResponse response = APDUBuilder.command()
                    .cla(0x80).ins(0x01).p1(0x00).p2(0x00)
                    .le(0)
                    .sendTo(session);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.dataAsString()).isEqualTo("Hello");
        }

        @Test
        void shouldSendCase1Apdu() {
            session.install(name.velikodniy.jcexpress.HelloWorldApplet.class);

            // Case 1: just CLA INS P1 P2 - HelloWorldApplet handles INS=0x01
            APDUResponse response = APDUBuilder.command()
                    .cla(0x80).ins(0x01)
                    .sendTo(session);

            assertThat(response.isSuccess()).isTrue();
        }
    }
}
