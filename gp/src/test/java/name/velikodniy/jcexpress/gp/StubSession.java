package name.velikodniy.jcexpress.gp;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for card stand-ins used by the gp tests: every APDU, whichever {@code send} or
 * {@code transmit} variant the code under test uses, ends up in {@link #respond(byte[])} as raw bytes
 * and is recorded.
 */
public abstract class StubSession implements SmartCardSession {

    private final List<byte[]> sent = new ArrayList<>();

    /**
     * Answers one raw command APDU.
     *
     * @param apdu the command as sent on the wire
     * @return the raw response (data and SW)
     */
    protected abstract byte[] respond(byte[] apdu);

    /**
     * Returns every command sent so far, in order.
     *
     * @return the recorded commands
     */
    public List<byte[]> sent() {
        return sent;
    }

    /**
     * Returns the last command sent.
     *
     * @return the last recorded command
     */
    public byte[] lastSent() {
        return sent.getLast();
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        sent.add(rawApdu.clone());
        return respond(rawApdu.clone());
    }

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
        return new APDUResponse(transmit(APDUCodec.encode(cla, ins, p1, p2, data, le)));
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
