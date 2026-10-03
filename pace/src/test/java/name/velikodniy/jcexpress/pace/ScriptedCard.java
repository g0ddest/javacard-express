package name.velikodniy.jcexpress.pace;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;

import javax.smartcardio.CommandAPDU;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Replays a published APDU transcript (e.g. ICAO Doc 9303-11 App. D.3/D.4 or G.1).
 *
 * <p>Every command must equal the next expected command byte for byte; it is answered with the recorded
 * response. Commands passed to {@code send(...)} are encoded with {@link CommandAPDU}, exactly as the embedded
 * (jCardSim) and PC/SC backends encode them, so the presence and value of the Le field is checked too
 * ({@code le < 0}: no Le, otherwise {@code Ne = le}).</p>
 */
final class ScriptedCard implements SmartCardSession {

    private record Exchange(String command, String response) {
    }

    private final Deque<Exchange> script = new ArrayDeque<>();
    final List<String> received = new ArrayList<>();

    /**
     * Adds an expected exchange.
     *
     * @param command  the expected command APDU in hex (spaces allowed)
     * @param response the response APDU in hex (data and SW1-SW2)
     * @return this card
     */
    ScriptedCard expect(String command, String response) {
        script.add(new Exchange(compact(command), compact(response)));
        return this;
    }

    boolean isFinished() {
        return script.isEmpty();
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        String command = Hex.encode(rawApdu);
        received.add(command);
        Exchange next = script.poll();
        if (next == null) {
            throw new AssertionError("Unexpected command #" + received.size() + ": " + command);
        }
        if (!next.command().equals(command)) {
            throw new AssertionError("Command #" + received.size() + " differs from the transcript"
                    + "\n  expected: " + next.command() + "\n  actual:   " + command);
        }
        return Hex.decode(next.response());
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        CommandAPDU command = le < 0
                ? new CommandAPDU(cla, ins, p1, p2, data)
                : new CommandAPDU(cla, ins, p1, p2, data, le);
        return new APDUResponse(transmit(command.getBytes()));
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
    }

    private static String compact(String hex) {
        return hex.replace(" ", "").toUpperCase();
    }
}
