package name.velikodniy.jcexpress.sm;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SMSession}.
 */
class SMSessionTest {

    private static final byte[] ENC_KEY = Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5");
    private static final byte[] MAC_KEY = Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8");

    private SMContext context() {
        return new SMContext(SMAlgorithm.DES3,
                new SMKeys(ENC_KEY, MAC_KEY),
                new byte[8]);
    }

    /**
     * Stub session that records transmit calls and returns pre-configured responses.
     */
    private static class StubSession implements SmartCardSession {
        final List<byte[]> transmitted = new ArrayList<>();
        byte[] nextResponse;
        boolean installCalled;
        boolean selectCalled;
        boolean resetCalled;
        boolean closeCalled;

        @Override
        public void install(Class<? extends Applet> appletClass) {
            installCalled = true;
        }

        @Override
        public void install(Class<? extends Applet> appletClass, AID aid) {
            installCalled = true;
        }

        @Override
        public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
            installCalled = true;
        }

        @Override
        public void select(Class<? extends Applet> appletClass) {
            selectCalled = true;
        }

        @Override
        public void select(AID aid) {
            selectCalled = true;
        }

        @Override
        public void reset() {
            resetCalled = true;
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            throw new UnsupportedOperationException("Use transmit in SM");
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            throw new UnsupportedOperationException("Use transmit in SM");
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2) {
            throw new UnsupportedOperationException("Use transmit in SM");
        }

        @Override
        public APDUResponse send(int cla, int ins) {
            throw new UnsupportedOperationException("Use transmit in SM");
        }

        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmitted.add(rawApdu.clone());
            return nextResponse != null ? nextResponse : new byte[]{(byte) 0x6A, (byte) 0x82};
        }

        @Override
        public void close() {
            closeCalled = true;
        }
    }

    @Test
    void wrapShouldCreateSMSession() {
        StubSession stub = new StubSession();
        SMSession session = SMSession.wrap(stub, context());
        assertThat(session).isNotNull();
        assertThat(session.delegate()).isSameAs(stub);
    }

    @Test
    void nullSessionShouldThrow() {
        assertThatThrownBy(() -> SMSession.wrap(null, context()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullContextShouldThrow() {
        assertThatThrownBy(() -> SMSession.wrap(new StubSession(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendShouldWrapAndTransmit() {
        StubSession stub = new StubSession();
        // Provide a non-SM error response (will be passed through)
        stub.nextResponse = new byte[]{(byte) 0x6A, (byte) 0x82};

        SMContext ctx = context();
        SMSession session = SMSession.wrap(stub, ctx);

        // send Case 1 — should call transmit with SM-wrapped APDU
        session.send(0x00, 0x70, 0x00, 0x00);

        assertThat(stub.transmitted).hasSize(1);
        byte[] wrapped = stub.transmitted.get(0);
        // Wrapped APDU should have SM CLA
        assertThat(wrapped[0]).isEqualTo((byte) 0x0C);
    }

    @Test
    void installShouldDelegateDirectly() {
        StubSession stub = new StubSession();
        SMSession session = SMSession.wrap(stub, context());
        session.install(null); // Will just set flag
        assertThat(stub.installCalled).isTrue();
    }

    @Test
    void selectShouldDelegateDirectly() {
        StubSession stub = new StubSession();
        SMSession session = SMSession.wrap(stub, context());
        session.select(AID.fromHex("A000000001"));
        assertThat(stub.selectCalled).isTrue();
    }

    @Test
    void resetShouldDelegateDirectly() {
        StubSession stub = new StubSession();
        SMSession session = SMSession.wrap(stub, context());
        session.reset();
        assertThat(stub.resetCalled).isTrue();
    }

    @Test
    void closeShouldDelegateDirectly() {
        StubSession stub = new StubSession();
        SMSession session = SMSession.wrap(stub, context());
        session.close();
        assertThat(stub.closeCalled).isTrue();
    }

    @Test
    void delegateShouldReturnOriginal() {
        StubSession stub = new StubSession();
        SMSession session = SMSession.wrap(stub, context());
        assertThat(session.delegate()).isSameAs(stub);
    }

    @Test
    void contextShouldReturnContext() {
        StubSession stub = new StubSession();
        SMContext ctx = context();
        SMSession session = SMSession.wrap(stub, ctx);
        assertThat(session.context()).isSameAs(ctx);
    }
}
