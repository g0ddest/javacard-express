package name.velikodniy.jcexpress.fakes;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Session stub that records every command as the exact bytes the shipped backends put on the wire (they all
 * encode with {@link APDUCodec#encode(int, int, int, int, byte[], int)}) and answers from a queue of
 * responses (default {@code 9000}).
 */
public final class RecordingSession implements SmartCardSession {

    /** Every command, in order, as it would reach the card. */
    public final List<byte[]> wire = new ArrayList<>();
    private final Deque<byte[]> replies = new ArrayDeque<>();

    /**
     * Queues a response.
     *
     * @param hexDataAndSw response data followed by SW1-SW2, as hex
     * @return this session
     */
    public RecordingSession reply(String hexDataAndSw) {
        replies.add(Hex.decode(hexDataAndSw));
        return this;
    }

    /**
     * Returns a recorded command as spaced hex.
     *
     * @param index the command index
     * @return e.g. {@code "00 70 00 00 01"}
     */
    public String wireHex(int index) {
        return Hex.encodeSpaced(wire.get(index));
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        wire.add(rawApdu.clone());
        return replies.isEmpty() ? new byte[]{(byte) 0x90, 0x00} : replies.poll();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        // nothing to release
    }
}
