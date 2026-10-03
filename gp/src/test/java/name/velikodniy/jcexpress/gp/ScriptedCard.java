package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * A card stand-in for command-level {@link GPSession} tests.
 *
 * <p>The mutual authentication is answered from a public real-card {@link ScpTranscript} (so
 * {@link GPSession#open()} runs against cryptograms that were NOT produced by javacard-express); the
 * stand-in checks that the host sends exactly the transcript's INITIALIZE UPDATE and EXTERNAL AUTHENTICATE
 * commands. Every later command is recorded and answered with the next scripted response ('9000' when
 * none is queued). The sessions use security level '01' (C-MAC only), so the command data is sent in clear
 * and {@link #plainCommands()} can recover the commands as GPSession built them before secure messaging,
 * for comparison with the GPCS v2.3.1 command tables. (The C-MAC values themselves are verified by the
 * known-answer tests.)</p>
 */
public final class ScriptedCard extends StubSession {

    /** Real SCP02 card, i=15, level '01' (GPCS v2.3.1 Appendix E). */
    public static final String SCP02_REAL_CARD = "SCP02_real_card_i15_session0";

    /** Real NXP JCOP4 SCP03 card, i=70, level '01' (Amendment D). */
    public static final String SCP03_REAL_CARD = "SCP03_real_JCOP4_i70";

    private static final int MAC_LENGTH = 8;

    private final ScpTranscript handshake;
    private final Deque<byte[]> responses = new ArrayDeque<>();
    private final List<String> mismatches = new ArrayList<>();
    private int step;

    private ScriptedCard(ScpTranscript handshake) {
        this.handshake = handshake;
    }

    /**
     * Creates a stand-in that authenticates like the real SCP02 card of {@link #SCP02_REAL_CARD}.
     *
     * @return the stand-in
     */
    public static ScriptedCard scp02() {
        return new ScriptedCard(ScpTranscript.named(SCP02_REAL_CARD));
    }

    /**
     * Creates a stand-in that authenticates like the real SCP03 card of {@link #SCP03_REAL_CARD}.
     *
     * @return the stand-in
     */
    public static ScriptedCard scp03() {
        return new ScriptedCard(ScpTranscript.named(SCP03_REAL_CARD));
    }

    /**
     * Creates a stand-in that authenticates like the given transcript (any security level; with C-ENC the
     * data fields are encrypted, so use {@link #wrappedCommands()} instead of {@link #plainCommands()}).
     *
     * @param transcriptName the name of a known-answer transcript
     * @return the stand-in
     */
    public static ScriptedCard of(String transcriptName) {
        return new ScriptedCard(ScpTranscript.named(transcriptName));
    }

    /**
     * Returns the real SCP02 card transcript whose handshake {@link #scp02()} answers.
     *
     * @return the transcript
     */
    public static ScpTranscript scp02Handshake() {
        return ScpTranscript.named(SCP02_REAL_CARD);
    }

    /**
     * Returns a GPSession configured with the handshake's keys, key version, host challenge and level.
     *
     * @return the configured, not yet opened session
     */
    public GPSession session() {
        return GPSession.on(this)
                .keys(handshake.keys())
                .keyVersion(handshake.keyVersion())
                .hostChallenge(handshake.hostChallenge())
                .securityLevel(handshake.level());
    }

    /**
     * Returns an opened GPSession; fails if the handshake did not match the transcript.
     *
     * @return the opened session
     */
    public GPSession open() {
        GPSession gp = session().open();
        if (!mismatches.isEmpty()) {
            throw new AssertionError("handshake mismatch: " + mismatches);
        }
        return gp;
    }

    /**
     * Queues the raw response (data and status word) of the next command after the handshake.
     *
     * @param hexResponse the response as hex
     * @return this stand-in
     */
    public ScriptedCard thenAnswer(String hexResponse) {
        responses.add(Hex.decode(hexResponse));
        return this;
    }

    /**
     * Returns the commands received after EXTERNAL AUTHENTICATE, without secure messaging: class byte
     * without the secure messaging bit, the 8-byte C-MAC removed and Lc adjusted (GPCS v2.3.1 E.4.4,
     * Amendment D 6.2.4; level '01' leaves the data field in clear).
     *
     * @return the plain commands as hex strings
     */
    public List<String> plainCommands() {
        List<byte[]> sent = sent();
        List<String> plain = new ArrayList<>();
        for (byte[] wrapped : sent.subList(Math.min(2, sent.size()), sent.size())) {
            plain.add(Hex.encode(strip(wrapped)));
        }
        return plain;
    }

    /**
     * Returns the commands received after EXTERNAL AUTHENTICATE as sent on the wire.
     *
     * @return the wrapped commands as hex strings
     */
    public List<String> wrappedCommands() {
        List<byte[]> sent = sent();
        return sent.subList(Math.min(2, sent.size()), sent.size()).stream().map(Hex::encode).toList();
    }

    /**
     * Returns the number of commands received after EXTERNAL AUTHENTICATE.
     *
     * @return the number of post-handshake commands
     */
    public int commandCount() {
        return Math.max(0, sent().size() - 2);
    }

    @Override
    protected byte[] respond(byte[] apdu) {
        int current = step++;
        if (current == 0) {
            return expect("INITIALIZE UPDATE", handshake.initUpdateCommand(), apdu,
                    concat(handshake.initUpdate(), new byte[]{(byte) 0x90, 0x00}));
        }
        if (current == 1) {
            return expect("EXTERNAL AUTHENTICATE", handshake.extAuth(), apdu, new byte[]{(byte) 0x90, 0x00});
        }
        return responses.isEmpty() ? new byte[]{(byte) 0x90, 0x00} : responses.poll();
    }

    private byte[] expect(String what, byte[] expected, byte[] actual, byte[] response) {
        if (Arrays.equals(expected, actual)) {
            return response;
        }
        mismatches.add(what + ": expected " + Hex.encode(expected) + " but host sent " + Hex.encode(actual));
        return new byte[]{0x69, (byte) 0x82};
    }

    private static byte[] strip(byte[] wrapped) {
        int lc = wrapped[4] & 0xFF;
        int dataLength = lc - MAC_LENGTH;
        boolean hasLe = wrapped.length == 6 + lc;
        byte[] plain = new byte[4 + (dataLength > 0 ? 1 + dataLength : 0) + (hasLe ? 1 : 0)];
        plain[0] = (byte) (wrapped[0] & ~0x04);
        System.arraycopy(wrapped, 1, plain, 1, 3);
        int offset = 4;
        if (dataLength > 0) {
            plain[offset++] = (byte) dataLength;
            System.arraycopy(wrapped, 5, plain, offset, dataLength);
            offset += dataLength;
        }
        if (hasLe) {
            plain[offset] = wrapped[wrapped.length - 1];
        }
        return plain;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
