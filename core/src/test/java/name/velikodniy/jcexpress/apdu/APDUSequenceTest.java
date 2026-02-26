package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import javacard.framework.Applet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link APDUSequence}.
 */
class APDUSequenceTest {

    // ── Stub session that returns pre-configured responses ──

    /**
     * Minimal SmartCardSession stub for testing APDUSequence.
     * Returns queued raw responses (data + SW1 + SW2) on each transmit() call.
     */
    static class StubSession implements SmartCardSession {

        private final Deque<byte[]> responses = new ArrayDeque<>();

        /** Queues a response: data bytes followed by SW1 SW2. */
        StubSession respond(byte[] data, int sw) {
            byte[] raw = new byte[data.length + 2];
            System.arraycopy(data, 0, raw, 0, data.length);
            raw[data.length] = (byte) ((sw >> 8) & 0xFF);
            raw[data.length + 1] = (byte) (sw & 0xFF);
            responses.add(raw);
            return this;
        }

        /** Queues a response with no data, just SW. */
        StubSession respond(int sw) {
            return respond(new byte[0], sw);
        }

        @Override
        public byte[] transmit(byte[] rawApdu) {
            if (responses.isEmpty()) {
                throw new IllegalStateException("No more stub responses queued");
            }
            return responses.poll();
        }

        @Override public void install(Class<? extends Applet> c) { }
        @Override public void install(Class<? extends Applet> c, AID aid) { }
        @Override public void install(Class<? extends Applet> c, AID aid, byte[] p) { }
        @Override public void select(Class<? extends Applet> c) { }
        @Override public void select(AID aid) { }
        @Override public void reset() { }
        @Override public APDUResponse send(int cla, int ins) { return null; }
        @Override public APDUResponse send(int cla, int ins, int p1, int p2) { return null; }
        @Override public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) { return null; }
        @Override public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) { return null; }
        @Override public void close() { }
    }

    // ── GET RESPONSE chaining (SW=61XX) ──

    @Nested
    class GetResponseChaining {

        @Test
        void shouldChainSingleGetResponse() {
            byte[] data1 = Hex.decode("AABBCC");
            byte[] data2 = Hex.decode("DDEEFF");

            StubSession stub = new StubSession()
                    .respond(data1, 0x6103)    // 3 more bytes available
                    .respond(data2, 0x9000);   // GET RESPONSE returns remaining

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("80F20000"));

            assertThat(full.data()).isEqualTo(Hex.decode("AABBCCDDEEFF"));
            assertThat(full.isSuccess()).isTrue();
        }

        @Test
        void shouldChainMultipleGetResponses() {
            byte[] part1 = Hex.decode("0102030405");
            byte[] part2 = Hex.decode("0607080910");
            byte[] part3 = Hex.decode("1112");

            StubSession stub = new StubSession()
                    .respond(part1, 0x6105)    // 5 more
                    .respond(part2, 0x6102)    // 2 more
                    .respond(part3, 0x9000);   // done

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("80F24000"));

            assertThat(full.data()).isEqualTo(Hex.decode("01020304050607080910" + "1112"));
            assertThat(full.sw()).isEqualTo(0x9000);
        }

        @Test
        void shouldRespectMaxChainLimit() {
            // Stub that always returns 61XX (infinite chain)
            StubSession stub = new StubSession();
            for (int i = 0; i < 5; i++) {
                stub.respond(new byte[]{(byte) i}, 0x6101);
            }
            // 6th response is terminal
            stub.respond(new byte[]{0x05}, 0x6101);

            APDUResponse full = APDUSequence.on(stub)
                    .maxChain(3) // limit to 3 GET RESPONSE calls
                    .transmit(Hex.decode("80F20000"));

            // Should have: initial data + 3 chained responses = 4 bytes
            assertThat(full.data()).hasSize(4);
        }

        @Test
        void shouldHandleEmptyDataIn61XXResponse() {
            StubSession stub = new StubSession()
                    .respond(new byte[0], 0x6105) // no data yet, 5 more bytes
                    .respond(Hex.decode("0102030405"), 0x9000);

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("80CA00CF00"));

            assertThat(full.data()).isEqualTo(Hex.decode("0102030405"));
        }
    }

    // ── Le correction (SW=6CXX) ──

    @Nested
    class LeCorrection {

        @Test
        void shouldRetryWithCorrectLe() {
            byte[] data = Hex.decode("AABBCCDD");

            StubSession stub = new StubSession()
                    .respond(0x6C04)           // wrong Le, correct is 4
                    .respond(data, 0x9000);    // retry succeeds

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("80CA00CF00")); // original Le=0

            assertThat(full.data()).isEqualTo(data);
            assertThat(full.isSuccess()).isTrue();
        }

        @Test
        void shouldHandle6CXXFollowedBy61XX() {
            byte[] data1 = Hex.decode("AABB");
            byte[] data2 = Hex.decode("CCDD");

            StubSession stub = new StubSession()
                    .respond(0x6C04)           // wrong Le
                    .respond(data1, 0x6102)    // retry returns partial + 61XX
                    .respond(data2, 0x9000);   // GET RESPONSE returns rest

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("80CA00CF00"));

            assertThat(full.data()).isEqualTo(Hex.decode("AABBCCDD"));
        }
    }

    // ── Pass-through (no chaining needed) ──

    @Nested
    class PassThrough {

        @Test
        void shouldReturnDirectlyOnSuccess() {
            byte[] data = Hex.decode("48656C6C6F");

            StubSession stub = new StubSession()
                    .respond(data, 0x9000);

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("80010000"));

            assertThat(full.data()).isEqualTo(data);
            assertThat(full.isSuccess()).isTrue();
        }

        @Test
        void shouldReturnDirectlyOnError() {
            StubSession stub = new StubSession()
                    .respond(0x6A82); // file not found

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("00A4040007A0000000031010"));

            assertThat(full.data()).isEmpty();
            assertThat(full.sw()).isEqualTo(0x6A82);
        }

        @Test
        void shouldReturnDataOnNon9000Success() {
            byte[] data = Hex.decode("0102");

            StubSession stub = new StubSession()
                    .respond(data, 0x6283); // warning: selected file deactivated

            APDUResponse full = APDUSequence.on(stub)
                    .transmit(Hex.decode("00A40400"));

            assertThat(full.data()).isEqualTo(data);
            assertThat(full.sw()).isEqualTo(0x6283);
        }
    }

    // ── Extended APDU Le correction ──

    @Nested
    class ExtendedLeCorrection {

        @Test
        void shouldCorrectLeInExtendedApdu() {
            byte[] data = Hex.decode("AABB");

            StubSession stub = new StubSession()
                    .respond(0x6C02)           // wrong Le, correct is 2
                    .respond(data, 0x9000);    // retry succeeds

            // Build an extended APDU (300 bytes data + Le=1000)
            byte[] extApdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, new byte[300], 1000);

            APDUResponse full = APDUSequence.on(stub).transmit(extApdu);

            assertThat(full.data()).isEqualTo(data);
            assertThat(full.isSuccess()).isTrue();
        }

        @Test
        void shouldCorrectLeInExtendedCase2() {
            byte[] data = Hex.decode("CCDD");

            StubSession stub = new StubSession()
                    .respond(0x6C02)
                    .respond(data, 0x9000);

            // Extended Case 2E: Le=1000
            byte[] extApdu = APDUCodec.encode(0x00, 0xCA, 0x00, 0xCF, null, 1000);

            APDUResponse full = APDUSequence.on(stub).transmit(extApdu);

            assertThat(full.data()).isEqualTo(data);
            assertThat(full.isSuccess()).isTrue();
        }
    }

    // ── Convenience send() methods ──

    @Nested
    class SendMethods {

        @Test
        void sendWithDataShouldWork() {
            StubSession stub = new StubSession()
                    .respond(Hex.decode("01"), 0x9000);

            APDUResponse r = APDUSequence.on(stub)
                    .send(0x80, 0xF2, 0x40, 0x00, Hex.decode("4F00"));

            assertThat(r.isSuccess()).isTrue();
        }

        @Test
        void sendWithoutDataShouldWork() {
            StubSession stub = new StubSession()
                    .respond(Hex.decode("01"), 0x9000);

            APDUResponse r = APDUSequence.on(stub)
                    .send(0x80, 0x01, 0x00, 0x00);

            assertThat(r.isSuccess()).isTrue();
        }
    }
}
