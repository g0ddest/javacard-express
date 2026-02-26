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
 * <p>Lifecycle methods ({@code install}, {@code select}, {@code reset}, {@code close})
 * are delegated directly without SM wrapping.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * SMKeys keys = new SMKeys(encKey, macKey);
 * SMContext ctx = new SMContext(SMAlgorithm.DES3, keys, initialSsc);
 * SMSession secure = SMSession.wrap(card, ctx);
 *
 * // All send() calls are now SM-protected
 * APDUResponse resp = secure.send(0x00, 0xB0, 0x00, 0x00);
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

    // ── SmartCardSession delegation (no SM wrapping) ──

    @Override
    public void install(Class<? extends Applet> appletClass) {
        delegate.install(appletClass);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        delegate.install(appletClass, aid);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        delegate.install(appletClass, aid, installParams);
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        delegate.select(appletClass);
    }

    @Override
    public void select(AID aid) {
        delegate.select(aid);
    }

    @Override
    public void reset() {
        delegate.reset();
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

    @Override
    public void close() {
        delegate.close();
    }
}
