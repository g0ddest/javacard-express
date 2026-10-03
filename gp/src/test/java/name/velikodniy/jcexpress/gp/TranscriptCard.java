package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A card stand-in that replays a known-answer {@link ScpTranscript} strictly.
 *
 * <p>The host must send exactly the INITIALIZE UPDATE command, the EXTERNAL AUTHENTICATE command and
 * the wrapped commands of the transcript, in this order. Like a real card, the stand-in answers a
 * command it does not expect with '6982' (security status not satisfied); every deviation is recorded
 * in {@link #mismatches()}.</p>
 */
public final class TranscriptCard extends StubSession {

    private final ScpTranscript transcript;
    private final List<String> mismatches = new ArrayList<>();
    private boolean addedLeAllowed;
    private int step;

    /**
     * Creates a stand-in for one transcript.
     *
     * @param transcript the known-answer session to replay
     */
    public TranscriptCard(ScpTranscript transcript) {
        this.transcript = transcript;
    }

    /**
     * Also accepts a command that equals the transcript's command with an Le '00' appended. Some public
     * transcripts (GlobalPlatformPro on T=1) omit the Le that GPCS v2.3.1 Tables 11-40 (INSTALL) and 11-56
     * (LOAD) require; the C-MAC does not cover Le (E.4.4), so the rest of the session is unaffected.
     *
     * @return this stand-in
     */
    public TranscriptCard allowAddedLe() {
        this.addedLeAllowed = true;
        return this;
    }

    /**
     * Returns the deviations from the transcript observed so far.
     *
     * @return human-readable mismatch descriptions (empty when the host behaved exactly as expected)
     */
    public List<String> mismatches() {
        return mismatches;
    }

    /**
     * Returns true when every command of the transcript has been consumed.
     *
     * @return true if the whole session was replayed
     */
    public boolean completed() {
        return step == transcript.commands().size() + 2;
    }

    @Override
    protected byte[] respond(byte[] apdu) {
        int current = step++;
        if (current == 0) {
            return expect("INITIALIZE UPDATE", transcript.initUpdateCommand(), apdu,
                    concat(transcript.initUpdate(), new byte[]{(byte) 0x90, 0x00}));
        }
        if (current == 1) {
            return expect("EXTERNAL AUTHENTICATE", transcript.extAuth(), apdu, new byte[]{(byte) 0x90, 0x00});
        }
        int index = current - 2;
        if (index >= transcript.commands().size()) {
            mismatches.add("unexpected extra command " + Hex.encode(apdu));
            return new byte[]{0x6D, 0x00};
        }
        ScpTranscript.Command command = transcript.commands().get(index);
        return expect("command #" + index + " (" + command + ")", command.wrapped(), apdu, command.cardResponse());
    }

    private byte[] expect(String what, byte[] expected, byte[] actual, byte[] response) {
        if (Arrays.equals(expected, actual) || (addedLeAllowed && isWithAddedLe(expected, actual))) {
            return response;
        }
        mismatches.add(what + ": expected " + Hex.encode(expected) + " but host sent " + Hex.encode(actual));
        return new byte[]{0x69, (byte) 0x82};
    }

    private static boolean isWithAddedLe(byte[] expected, byte[] actual) {
        boolean expectedHasNoLe = expected.length == 5 + (expected[4] & 0xFF);
        return expectedHasNoLe && actual.length == expected.length + 1 && actual[expected.length] == 0
                && Arrays.equals(expected, Arrays.copyOf(actual, expected.length));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
