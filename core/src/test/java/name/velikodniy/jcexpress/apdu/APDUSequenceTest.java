package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.fakes.RecordingSession;
import javacard.framework.Applet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        /** Reaching the limit must not look like a complete response (partial data with SW 61XX). */
        @Test
        void reachingTheChainLimitIsReported() {
            StubSession stub = new StubSession();
            for (int i = 0; i < 6; i++) {
                stub.respond(new byte[]{(byte) i}, 0x6101);
            }

            APDUSequence sequence = APDUSequence.on(stub).maxChain(3);

            assertThatThrownBy(() -> sequence.transmit(Hex.decode("80F20000")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maxChain")
                    .hasMessageContaining("6101");
        }

        /** The default limit lets a response of the largest Ne (65 536 bytes) arrive in 256-byte pieces. */
        @Test
        void defaultChainLimitCoversTheLargestResponse() {
            StubSession stub = new StubSession().respond(0x6100);
            for (int i = 0; i < 255; i++) {
                stub.respond(new byte[256], 0x6100);
            }
            stub.respond(new byte[256], 0x9000);

            APDUResponse full = APDUSequence.on(stub).transmit(Hex.decode("80CA00FE00"));

            assertThat(full.data()).hasSize(65_536);
            assertThat(full.sw()).isEqualTo(0x9000);
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

    /**
     * ISO/IEC 7816-4:2005 5.1.3: after '61XX' "a GET RESPONSE command may be issued with the same CLA and
     * using SW2 (number of data bytes still available) as short Le field"; the class byte also carries the
     * logical channel (5.1.1.2), so the remaining bytes are fetched on the channel of the command.
     */
    @Nested
    class GetResponseClass {

        @Test
        void getResponseUsesTheClassOfTheCommand() {
            RecordingSession card = new RecordingSession().reply("6105").reply("0102030405 9000");

            APDUResponse r = APDUSequence.on(card).transmit(Hex.decode("80CA006600"));

            assertThat(r.data()).containsExactly(1, 2, 3, 4, 5);
            assertThat(card.wireHex(1)).isEqualTo("80 C0 00 00 05");
        }

        @Test
        void getResponseStaysOnTheLogicalChannel() {
            RecordingSession card = new RecordingSession()
                    .reply("6105").reply("0102030405 9000")
                    .reply("6101").reply("01 9000");

            APDUSequence.on(card).transmit(Hex.decode("01CA006600"));
            APDUSequence.on(card).transmit(Hex.decode("41CA006600"));

            assertThat(card.wireHex(1)).isEqualTo("01 C0 00 00 05");
            assertThat(card.wireHex(3)).isEqualTo("41 C0 00 00 01");
        }

        @Test
        void explicitGetResponseClassKeepsTheChannelOfTheCommand() {
            RecordingSession card = new RecordingSession().reply("6105").reply("0102030405 9000");

            APDUSequence.on(card).getResponseCla(0x00).transmit(Hex.decode("82CA006600"));

            assertThat(card.wireHex(1)).isEqualTo("02 C0 00 00 05");
        }

        @Test
        void sw2ZeroAsksFor256Bytes() {
            RecordingSession card = new RecordingSession().reply("6100").reply("9000");

            APDUSequence.on(card).transmit(Hex.decode("80CA006600"));

            assertThat(card.wireHex(1)).isEqualTo("80 C0 00 00 00");
        }

        @Test
        void getResponseClassMustBeAByte() {
            assertThatThrownBy(() -> APDUSequence.on(new RecordingSession()).getResponseCla(0x100))
                    .isInstanceOf(IllegalArgumentException.class);
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

        /**
         * A command protected by a secure channel must not be transmitted twice with the same bytes: the card
         * verified its MAC before answering '6CXX' and moved its MAC chaining value (GPCS v2.3.1 E.4.4,
         * Amendment D 6.2.4). Without Le correction the '6CXX' answer is returned and nothing is re-sent.
         */
        @Test
        void withoutLeCorrection6CXXIsReturnedAndNothingIsResent() {
            RecordingSession card = new RecordingSession().reply("6C10").reply("0011223344 9000");

            APDUResponse r = APDUSequence.on(card).leCorrection(false)
                    .transmit(Hex.decode("84CA006608112233445566778805"));

            assertThat(r.sw()).isEqualTo(0x6C10);
            assertThat(card.wire).hasSize(1);
        }

        @Test
        void withoutLeCorrectionGetResponseChainingStillWorks() {
            RecordingSession card = new RecordingSession().reply("AABB 6102").reply("CCDD 9000");

            APDUResponse r = APDUSequence.on(card).leCorrection(false)
                    .transmit(Hex.decode("84F240020A4F00112233445566778800"));

            assertThat(r.data()).isEqualTo(Hex.decode("AABBCCDD"));
            assertThat(r.sw()).isEqualTo(0x9000);
            assertThat(card.wireHex(1)).isEqualTo("84 C0 00 00 02");
        }

        @Test
        void leCorrectionCanBeSwitchedBackOn() {
            RecordingSession card = new RecordingSession().reply("6C02").reply("AABB 9000");

            APDUResponse r = APDUSequence.on(card).leCorrection(false).leCorrection(true)
                    .transmit(Hex.decode("80CA00CF00"));

            assertThat(r.data()).isEqualTo(Hex.decode("AABB"));
            assertThat(card.wireHex(1)).isEqualTo("80 CA 00 CF 02");
        }
    }

    /**
     * A 270-byte response like SmartPGP's GENERATE ASYMMETRIC KEY PAIR on the validated card (where SunPCSC did
     * the chaining below the transcript): with Le '00', ISO/IEC 7816-4:2005 5.1.3 chaining delivers it as 256 bytes
     * with '610E', then the remaining 14 bytes after GET RESPONSE with Le '0E'.
     */
    @Test
    void responseLongerThan256BytesIsReassembledFrom61XXChaining() {
        byte[] first = new byte[256];
        byte[] rest = new byte[14];
        for (int i = 0; i < first.length; i++) {
            first[i] = (byte) i;
        }
        Arrays.fill(rest, (byte) 0x5A);
        RecordingSession card = new RecordingSession()
                .reply(Hex.encode(first) + "610E")
                .reply(Hex.encode(rest) + "9000");

        APDUResponse r = APDUSequence.on(card).transmit(Hex.decode("00CA006E00"));

        assertThat(r.data()).hasSize(270);
        assertThat(Arrays.copyOfRange(r.data(), 256, 270)).isEqualTo(rest);
        assertThat(r.sw()).isEqualTo(0x9000);
        assertThat(card.wireHex(1)).isEqualTo("00 C0 00 00 0E");
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

        /** '6C00': 256 bytes are available (SW2 is coded like a short Le), not 65 536. */
        @Test
        void sw2ZeroOnAnExtendedCommandAsksFor256Bytes() {
            RecordingSession card = new RecordingSession().reply("6C00").reply("9000");
            byte[] extApdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, new byte[300], 1000);

            APDUSequence.on(card).transmit(extApdu);

            assertThat(Hex.encode(card.wire.get(1))).endsWith("0100");
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

        /** The le argument follows SmartCardSession.send: NO_LE = no Le field, 256 = Le '00'. */
        @Test
        void sendHonoursTheLeContract() {
            RecordingSession card = new RecordingSession();

            APDUSequence.on(card).send(0x80, 0xCA, 0x00, 0x66, null, 256);
            APDUSequence.on(card).send(0x80, 0xCA, 0x00, 0x66, null, SmartCardSession.NO_LE);

            assertThat(card.wireHex(0)).isEqualTo("80 CA 00 66 00");
            assertThat(card.wireHex(1)).isEqualTo("80 CA 00 66");
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
