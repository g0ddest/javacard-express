package name.velikodniy.jcexpress.sm;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test double for the session below {@link SMSession}: records every raw command and lifecycle call and answers
 * {@link #transmit(byte[])} with queued raw responses (the last one is repeated).
 */
final class RecordingSession implements SmartCardSession {

    final List<byte[]> commands = new ArrayList<>();
    final List<String> calls = new ArrayList<>();
    private final Deque<byte[]> responses = new ArrayDeque<>();
    private byte[] lastResponse;

    RecordingSession(byte[]... responses) {
        for (byte[] response : responses) {
            this.responses.add(response.clone());
        }
    }

    byte[] lastCommand() {
        return commands.getLast();
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        commands.add(rawApdu.clone());
        if (!responses.isEmpty()) {
            lastResponse = responses.poll();
        }
        return lastResponse == null ? new byte[]{0x6F, 0x00} : lastResponse.clone();
    }

    @Override
    public APDUResponse send(int cla, int ins) {
        throw new UnsupportedOperationException("SMSession must use transmit()");
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        throw new UnsupportedOperationException("SMSession must use transmit()");
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        throw new UnsupportedOperationException("SMSession must use transmit()");
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        throw new UnsupportedOperationException("SMSession must use transmit()");
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        calls.add("install");
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        calls.add("install");
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        calls.add("install");
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        calls.add("select");
    }

    @Override
    public void select(AID aid) {
        calls.add("select");
    }

    @Override
    public void reset() {
        calls.add("reset");
    }

    @Override
    public void close() {
        calls.add("close");
    }
}
