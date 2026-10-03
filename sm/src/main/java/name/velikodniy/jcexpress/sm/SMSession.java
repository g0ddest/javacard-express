package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import javacard.framework.Applet;

/**
 * A decorator that applies ISO 7816-4 Secure Messaging to all APDU exchanges.
 *
 * <p>Wraps any {@link SmartCardSession} and automatically applies SM command
 * wrapping (via {@link SMCodec#wrapCommand}) before sending, and response
 * unwrapping (via {@link SMCodec#unwrapResponse}) after receiving.</p>
 *
 * <p>Lifecycle methods ({@code install}, {@code select}, {@code reset}, {@code close}) are delegated without
 * SM wrapping and end the Secure Messaging session: a plain APDU such as the SELECT sent by {@code select}
 * makes the chip abort Secure Messaging and delete its session keys (ICAO Doc 9303-11, 9.8.3), and a reset
 * de-powers it (9.8.5). The {@link SMContext} is therefore {@linkplain SMContext#terminate(String) terminated}
 * and every later {@code send}/{@code transmit} fails with an {@link SMException} that names the cause. To select
 * a file or application without leaving Secure Messaging, send the SELECT command through {@link #send}.
 * The context is also terminated when the chip answers with a bare status word (an SM error, 9.8.5); that
 * response is still returned once.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * SMKeys keys = new SMKeys(encKey, macKey);
 * SMContext ctx = new SMContext(SMAlgorithm.DES3, keys, initialSsc);
 * SMSession secure = SMSession.wrap(card, ctx);
 *
 * // All send() calls are now SM-protected
 * secure.send(0x00, 0xA4, 0x02, 0x0C, Hex.decode("011E"));             // SELECT EF.COM
 * APDUResponse resp = secure.send(0x00, 0xB0, 0x00, 0x00, null, 4);   // READ BINARY, Ne = 4
 * </pre>
 *
 * @see SMCodec
 * @see SMContext
 */
public final class SMSession implements SmartCardSession {

    private final SmartCardSession delegate;
    private final SMContext context;

    private SMSession(SmartCardSession delegate, SMContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    /**
     * Wraps a session with ISO 7816-4 Secure Messaging.
     *
     * @param session the session to wrap
     * @param context the SM context (algorithm, keys, SSC)
     * @return a secure messaging session
     */
    public static SMSession wrap(SmartCardSession session, SMContext context) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        return new SMSession(session, context);
    }

    /**
     * Returns the underlying (unwrapped) session.
     *
     * @return the delegate session
     */
    public SmartCardSession delegate() {
        return delegate;
    }

    /**
     * Returns the SM context (for SSC inspection or algorithm info).
     *
     * @return the SM context
     */
    public SMContext context() {
        return context;
    }

    // ── SmartCardSession delegation (plain, ends Secure Messaging) ──

    @Override
    public void install(Class<? extends Applet> appletClass) {
        endingSession("install()", () -> delegate.install(appletClass));
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        endingSession("install()", () -> delegate.install(appletClass, aid));
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        endingSession("install()", () -> delegate.install(appletClass, aid, installParams));
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        endingSession("select()", () -> delegate.select(appletClass));
    }

    @Override
    public void select(AID aid) {
        endingSession("select()", () -> delegate.select(aid));
    }

    @Override
    public void reset() {
        try {
            delegate.reset();
        } finally {
            context.terminate("the card was reset, which ends Secure Messaging (ICAO 9303-11 9.8.5)");
        }
    }

    private void endingSession(String operation, Runnable plainOperation) {
        try {
            plainOperation.run();
        } finally {
            context.terminate(operation + " selects an applet with a plain APDU, which ends Secure Messaging"
                    + " (ICAO 9303-11 9.8.3); send SELECT through send() to stay protected");
        }
    }

    // ── APDU methods (SM-wrapped) ──

    @Override
    public APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0, null, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, -1);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] plainApdu = APDUCodec.encode(cla, ins, p1, p2, data, le);
        byte[] wrappedApdu = SMCodec.wrapCommand(context, plainApdu);
        byte[] rawResponse = delegate.transmit(wrappedApdu);
        return SMCodec.unwrapResponse(context, rawResponse);
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        byte[] wrappedApdu = SMCodec.wrapCommand(context, rawApdu);
        byte[] rawResponse = delegate.transmit(wrappedApdu);
        APDUResponse unwrapped = SMCodec.unwrapResponse(context, rawResponse);

        // Re-encode as raw bytes: data || SW1 || SW2
        byte[] responseData = unwrapped.data();
        byte[] result = new byte[responseData.length + 2];
        System.arraycopy(responseData, 0, result, 0, responseData.length);
        result[result.length - 2] = (byte) ((unwrapped.sw() >> 8) & 0xFF);
        result[result.length - 1] = (byte) (unwrapped.sw() & 0xFF);
        return result;
    }

    /**
     * Closes the underlying session and terminates the SM context, wiping its key copies.
     */
    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            context.terminate("the session was closed");
        }
    }
}
