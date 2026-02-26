package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LogicalChannel}.
 */
class LogicalChannelTest {

    // ── CLA encoding ──

    @Nested
    class ClaEncoding {

        @Test
        void channel0ShouldNotModifyCla() {
            assertThat(LogicalChannel.encodeCla(0x00, 0)).isEqualTo(0x00);
            assertThat(LogicalChannel.encodeCla(0x80, 0)).isEqualTo(0x80);
            assertThat(LogicalChannel.encodeCla(0xFF, 0)).isEqualTo(0xFF);
        }

        @Test
        void basicChannel1() {
            assertThat(LogicalChannel.encodeCla(0x00, 1)).isEqualTo(0x01);
        }

        @Test
        void basicChannel2() {
            assertThat(LogicalChannel.encodeCla(0x00, 2)).isEqualTo(0x02);
        }

        @Test
        void basicChannel3() {
            assertThat(LogicalChannel.encodeCla(0x00, 3)).isEqualTo(0x03);
        }

        @Test
        void shouldPreserveHighBits() {
            // GP CLA 0x80 + channel 2 = 0x82
            assertThat(LogicalChannel.encodeCla(0x80, 2)).isEqualTo(0x82);
            // CLA 0xC0 + channel 1 = 0xC1
            assertThat(LogicalChannel.encodeCla(0xC0, 1)).isEqualTo(0xC1);
        }

        @Test
        void shouldPreserveSecureMessagingBit() {
            // Secure messaging bit (0x04) + channel 1 = 0x05
            assertThat(LogicalChannel.encodeCla(0x04, 1)).isEqualTo(0x05);
            // GP secure (0x84) + channel 3 = 0x87
            assertThat(LogicalChannel.encodeCla(0x84, 3)).isEqualTo(0x87);
        }

        @Test
        void shouldOverwriteExistingChannelBits() {
            // CLA 0x03 (channel 3) → recode to channel 1 = 0x01
            assertThat(LogicalChannel.encodeCla(0x03, 1)).isEqualTo(0x01);
        }
    }

    // ── Stub session for testing ──

    /**
     * Minimal stub that records transmitted APDUs and returns configurable responses.
     */
    static class StubSession implements SmartCardSession {
        private final List<byte[]> transmitted = new ArrayList<>();
        private final List<APDUResponse> responses = new ArrayList<>();
        private int responseIdx = 0;

        void queueResponse(byte[] data, int sw) {
            responses.add(new APDUResponse(data, sw));
        }

        void queueResponse(int sw) {
            responses.add(new APDUResponse(new byte[0], sw));
        }

        List<byte[]> transmitted() {
            return transmitted;
        }

        byte[] lastTransmitted() {
            return transmitted.get(transmitted.size() - 1);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            byte[] apdu = new byte[]{(byte) cla, (byte) ins, (byte) p1, (byte) p2};
            transmitted.add(apdu);
            if (responseIdx < responses.size()) {
                return responses.get(responseIdx++);
            }
            return new APDUResponse(new byte[0], 0x9000);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            return send(cla, ins, p1, p2, data, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2) {
            return send(cla, ins, p1, p2, null, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins) {
            return send(cla, ins, 0, 0, null, -1);
        }

        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmitted.add(rawApdu.clone());
            if (responseIdx < responses.size()) {
                APDUResponse r = responses.get(responseIdx++);
                byte[] result = new byte[r.data().length + 2];
                System.arraycopy(r.data(), 0, result, 0, r.data().length);
                result[result.length - 2] = (byte) (r.sw() >> 8);
                result[result.length - 1] = (byte) (r.sw());
                return result;
            }
            return new byte[]{(byte) 0x90, 0x00};
        }

        @Override public void install(Class<? extends Applet> c) {}
        @Override public void install(Class<? extends Applet> c, AID a) {}
        @Override public void install(Class<? extends Applet> c, AID a, byte[] p) {}
        @Override public void select(Class<? extends Applet> c) {}
        @Override public void select(AID a) {}
        @Override public void reset() {}
        @Override public void close() {}
    }

    // ── Basic channel ──

    @Nested
    class BasicChannel {

        @Test
        void shouldEncodeClaDuringSend() {
            StubSession stub = new StubSession();
            LogicalChannel ch = LogicalChannel.basic(stub, 2);

            ch.send(0x00, 0xA4, 0x04, 0x00);

            byte[] apdu = stub.lastTransmitted();
            assertThat(apdu[0] & 0xFF).isEqualTo(0x02); // CLA with channel 2
            assertThat(apdu[1] & 0xFF).isEqualTo(0xA4); // INS preserved
        }

        @Test
        void closeShouldBeNoop() {
            StubSession stub = new StubSession();
            LogicalChannel ch = LogicalChannel.basic(stub, 1);

            ch.close();

            assertThat(stub.transmitted()).isEmpty();
        }

        @Test
        void shouldReturnChannelNumber() {
            StubSession stub = new StubSession();
            LogicalChannel ch = LogicalChannel.basic(stub, 3);

            assertThat(ch.channelNumber()).isEqualTo(3);
            assertThat(ch.isManaged()).isFalse();
        }

        @Test
        void selectShouldEncodeChannel() {
            StubSession stub = new StubSession();
            LogicalChannel ch = LogicalChannel.basic(stub, 1);
            AID aid = AID.fromHex("A0000000031010");

            ch.select(aid);

            byte[] apdu = stub.lastTransmitted();
            assertThat(apdu[0] & 0xFF).isEqualTo(0x01); // CLA with channel 1
            assertThat(apdu[1] & 0xFF).isEqualTo(0xA4); // SELECT
        }

        @Test
        void transmitShouldEncodeChannel() {
            StubSession stub = new StubSession();
            LogicalChannel ch = LogicalChannel.basic(stub, 2);

            byte[] rawApdu = Hex.decode("00A40400");
            ch.transmit(rawApdu);

            byte[] sent = stub.lastTransmitted();
            assertThat(sent[0] & 0xFF).isEqualTo(0x02); // CLA modified
        }
    }

    // ── Managed channel (MANAGE CHANNEL) ──

    @Nested
    class ManagedChannel {

        @Test
        void openShouldSendManageChannelOpen() {
            StubSession stub = new StubSession();
            // MANAGE CHANNEL OPEN response: data = channel number
            stub.queueResponse(new byte[]{0x01}, 0x9000);

            LogicalChannel ch = LogicalChannel.open(stub);

            assertThat(ch.channelNumber()).isEqualTo(1);
            assertThat(ch.isManaged()).isTrue();

            byte[] apdu = stub.lastTransmitted();
            assertThat(apdu[0] & 0xFF).isEqualTo(0x00); // CLA
            assertThat(apdu[1] & 0xFF).isEqualTo(0x70); // INS = MANAGE CHANNEL
            assertThat(apdu[2] & 0xFF).isEqualTo(0x00); // P1 = OPEN
            assertThat(apdu[3] & 0xFF).isEqualTo(0x00); // P2 = card assigns
        }

        @Test
        void openSpecificChannelShouldSendP2() {
            StubSession stub = new StubSession();
            stub.queueResponse(0x9000);

            LogicalChannel ch = LogicalChannel.open(stub, 2);

            assertThat(ch.channelNumber()).isEqualTo(2);
            byte[] apdu = stub.lastTransmitted();
            assertThat(apdu[3] & 0xFF).isEqualTo(0x02); // P2 = requested channel
        }

        @Test
        void closeShouldSendManageChannelClose() {
            StubSession stub = new StubSession();
            stub.queueResponse(new byte[]{0x03}, 0x9000); // open → ch3
            stub.queueResponse(0x9000); // close response

            LogicalChannel ch = LogicalChannel.open(stub);
            assertThat(ch.channelNumber()).isEqualTo(3);

            ch.close();

            assertThat(stub.transmitted()).hasSize(2);
            byte[] closeApdu = stub.transmitted().get(1);
            assertThat(closeApdu[0] & 0xFF).isEqualTo(0x03); // CLA with channel 3
            assertThat(closeApdu[1] & 0xFF).isEqualTo(0x70); // INS = MANAGE CHANNEL
            assertThat(closeApdu[2] & 0xFF).isEqualTo(0x80); // P1 = CLOSE
            assertThat(closeApdu[3] & 0xFF).isEqualTo(0x03); // P2 = channel number
        }

        @Test
        void openShouldThrowOnFailure() {
            StubSession stub = new StubSession();
            stub.queueResponse(0x6985); // conditions not satisfied

            assertThatThrownBy(() -> LogicalChannel.open(stub))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MANAGE CHANNEL");
        }

        @Test
        void openChannel0ShouldThrow() {
            StubSession stub = new StubSession();

            assertThatThrownBy(() -> LogicalChannel.open(stub, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("channel 0");
        }
    }

    // ── Validation ──

    @Nested
    class Validation {

        @Test
        void shouldRejectChannelAbove3() {
            StubSession stub = new StubSession();

            assertThatThrownBy(() -> LogicalChannel.basic(stub, 4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0-3");
        }

        @Test
        void shouldRejectNegativeChannel() {
            StubSession stub = new StubSession();

            assertThatThrownBy(() -> LogicalChannel.basic(stub, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNullSession() {
            assertThatThrownBy(() -> LogicalChannel.basic(null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }

        @Test
        void transmitShouldRejectShortApdu() {
            StubSession stub = new StubSession();
            LogicalChannel ch = LogicalChannel.basic(stub, 1);

            assertThatThrownBy(() -> ch.transmit(new byte[]{0x00}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("4 bytes");
        }
    }
}
