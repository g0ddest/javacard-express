package name.velikodniy.jcexpress.livecard;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.livecard.guard.ApduGuard;
import name.velikodniy.jcexpress.livecard.guard.AuthenticationBudget;
import name.velikodniy.jcexpress.livecard.guard.GuardListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard decides about exactly the bytes that go to the transport: {@code send} encodes the command once, the
 * guard checks it, and the same bytes are transmitted, whatever the transport's own {@code send} would encode.
 */
class GuardedPcscSessionTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /** Records what is transmitted; its {@code send} must not be used. */
    private static final class RecordingTransport implements SmartCardSession {
        final List<String> transmitted = new ArrayList<>();

        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmitted.add(HEX.formatHex(rawApdu));
            return new byte[]{(byte) 0x90, 0x00};
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            throw new AssertionError("the transport's send could encode other bytes than the guard checked");
        }

        @Override
        public void install(Class<? extends Applet> appletClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void install(Class<? extends Applet> appletClass, AID aid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void select(Class<? extends Applet> appletClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void select(AID aid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reset() {
            // nothing to reset
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    @Test
    void theBytesTheGuardCheckedAreTheBytesTransmitted() {
        RecordingTransport transport = new RecordingTransport();
        ApduGuard guard = new ApduGuard(LiveCardConfig.of(Map.of()).guardPolicy(), new AuthenticationBudget(1),
                GuardListener.NONE);
        try (Transcript transcript = new Transcript()) {
            GuardedPcscSession session = new GuardedPcscSession(transport, guard, transcript);

            APDUResponse response = session.send(0x80, 0xCA, 0x9F, 0x7F, null, 256);

            assertThat(response.sw()).isEqualTo(0x9000);
            assertThat(transport.transmitted).containsExactly("80CA9F7F00");
        }
    }
}
