package name.velikodniy.jcexpress.livecard;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUHistory;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SW;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.livecard.guard.ApduGuard;
import name.velikodniy.jcexpress.livecard.guard.GuardViolationException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * A {@link SmartCardSession} to a real card with the {@link ApduGuard} in front of the wire and every exchange
 * in the {@link Transcript}.
 *
 * <p>A decorator: every command goes to the transport's {@link SmartCardSession#transmit(byte[])}
 * ({@link name.velikodniy.jcexpress.pcsc.PcscSession}: exclusive access, logical channels, T=0/T=1).
 * {@link #send(int, int, int, int, byte[], int)} encodes the command once with
 * {@link APDUCodec#encode(int, int, int, int, byte[], int)} (the encoder of every session of this project, also of
 * {@code PcscSession.send}), so the bytes the guard checks and the transcript records are exactly the bytes
 * transmitted. Around each call the guard decides first (a blocked command throws
 * {@link name.velikodniy.jcexpress.livecard.guard.GuardViolationException} and is never sent), the command is
 * written to the transcript, and the response is recorded and shown to the guard.</p>
 *
 * <p>One exchange runs at a time, so the guard sees the commands in the order the card receives them. The session
 * of a {@link LiveCard} accepts commands only from the thread that connected it, which holds the PC/SC exclusive
 * access; a command from another thread is refused before the guard sees it.</p>
 *
 * <p>The {@link #history()} (attached to failed {@code @JavaCardTest} tests and printed by {@code -Djcx.log=true})
 * holds the exchanges in the transcript format with notes: a command the guard blocked, a card reset, and in place
 * of the LOAD commands of a load file (GPCS v2.3.1 11.6) one note with their count; the transcript file keeps every
 * command.</p>
 *
 * <p>Applets are not installed through this session: use {@link LiveCard#deploy}.</p>
 */
public final class GuardedPcscSession implements SmartCardSession {

    private final SmartCardSession transport;
    private final ApduGuard guard;
    private final Transcript transcript;
    private final Thread owner;
    private final APDUHistory history = new APDUHistory();
    private final LoadBlocks loadBlocks = new LoadBlocks();
    private boolean closed;

    /**
     * Creates the session; any thread may use it, as far as the transport allows.
     *
     * @param transport  the session that transmits commands to the card
     * @param guard      the guard deciding about every command
     * @param transcript the transcript
     */
    public GuardedPcscSession(SmartCardSession transport, ApduGuard guard, Transcript transcript) {
        this(transport, guard, transcript, null);
    }

    /**
     * Creates a session that only one thread may use: the thread that holds the transport's exclusive access
     * ({@code Card.beginExclusive} binds it to the thread that connected).
     *
     * @param transport  the session that transmits commands to the card
     * @param guard      the guard deciding about every command
     * @param transcript the transcript
     * @param owner      the only thread that may send commands and reset the card, or null for any thread
     */
    GuardedPcscSession(SmartCardSession transport, ApduGuard guard, Transcript transcript, Thread owner) {
        this.transport = transport;
        this.guard = guard;
        this.transcript = transcript;
        this.owner = owner;
    }

    /**
     * Returns the guard of this session.
     *
     * @return the guard
     */
    public ApduGuard guard() {
        return guard;
    }

    /**
     * {@inheritDoc}
     *
     * @throws name.velikodniy.jcexpress.livecard.guard.GuardViolationException if the guard blocks the command
     */
    @Override
    public byte[] transmit(byte[] rawApdu) {
        return exchange(rawApdu, () -> transport.transmit(rawApdu));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The command is encoded with {@link APDUCodec#encode(int, int, int, int, byte[], int)}; the guard checks
     * these bytes and the transport transmits the same bytes.</p>
     *
     * @throws name.velikodniy.jcexpress.livecard.guard.GuardViolationException if the guard blocks the command
     */
    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, data, le);
        return new APDUResponse(exchange(command, () -> transport.transmit(command))).inReplyTo(command);
    }

    /**
     * One exchange at a time: the guard sees the commands in the order the card receives them.
     */
    private synchronized byte[] exchange(byte[] command, Supplier<byte[]> transmission) {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("The live-card session is closed");
        }
        check(command);
        transcript.command(command);
        long sentAt = System.currentTimeMillis();
        long start = System.nanoTime();
        byte[] response;
        try {
            response = transmission.get();
        } catch (RuntimeException e) {
            guard.transportFailed();
            transcript.note("transport failure: " + e);
            loadBlocks.flush(history, transcript);
            history.note("transport failure: " + e);
            throw e;
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        transcript.response(response, elapsed);
        guard.observe(command, response);
        record(command, response, sentAt, elapsed);
        return response;
    }

    /** The guard's decision; a blocked command is noted in the history too, where it would have been sent. */
    private void check(byte[] command) {
        try {
            guard.check(command);
        } catch (GuardViolationException e) {
            loadBlocks.flush(history, transcript);
            history.note("blocked by the APDU guard, not sent: " + e.command() + " (" + e.reason() + ")");
            throw e;
        }
    }

    /**
     * Records an exchange in the history with its time; LOAD commands are counted and noted once (the transcript has
     * them).
     */
    private void record(byte[] command, byte[] response, long sentAt, Duration elapsed) {
        if (!loadBlocks.add(command, response, history, transcript)) {
            loadBlocks.flush(history, transcript);
            history.record(command, response, sentAt, elapsed);
        }
    }

    /**
     * Writes a note into the transcript and into the {@link #history()}, e.g. what the card content management
     * of the harness is doing.
     *
     * @param message the note, one line
     */
    public synchronized void note(String message) {
        loadBlocks.flush(history, transcript);
        transcript.note(message);
        history.note(message);
    }

    /**
     * {@inheritDoc}
     *
     * <p>SELECT by DF name with Le '00'; '9000' and '61XX' mean selected.</p>
     *
     * @throws SelectException if the card answers another status word; the message says what it means (ISO/IEC
     *                         7816-4:2005 5.1.3, e.g. '6A82' file or application not found)
     */
    @Override
    public void select(AID aid) {
        APDUResponse response = send(0x00, 0xA4, 0x04, 0x00, aid.toBytes(), 256);
        if (response.sw() != 0x9000 && response.sw1() != 0x61) {
            throw new SelectException(aid, response.sw(),
                    String.format("SELECT %s failed: SW=%s", aid.toHex(), SW.format(response.sw())));
        }
    }

    /**
     * Not supported: applets on a real card are addressed by AID.
     *
     * @param appletClass ignored
     * @throws UnsupportedOperationException always
     */
    @Override
    public void select(Class<? extends Applet> appletClass) {
        throw new UnsupportedOperationException("Select applets on a real card by AID: select(AID)");
    }

    /**
     * Resets the card (warm reset and new connection); the guard returns to the default selection.
     */
    @Override
    public synchronized void reset() {
        requireOwner();
        note("card reset");
        try {
            transport.reset();
        } catch (RuntimeException e) {
            guard.transportFailed();
            throw e;
        }
        guard.cardReset();
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        throw installNotSupported();
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        throw installNotSupported();
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        throw installNotSupported();
    }

    /**
     * Returns the history of this session: every command that reached the card through it, with its response, in
     * the order sent, and notes: commands the guard blocked, card resets, the card content management of the
     * harness, and one note in place of the LOAD commands of each load file (the transcript file has them).
     *
     * @return the history
     */
    @Override
    public APDUHistory history() {
        return history;
    }

    /**
     * Closes the transport. Closing twice has no further effect.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            transport.close();
        }
    }

    /**
     * Refuses a command from another thread than the owner before the guard sees it, so the guard's picture of the
     * card stays right.
     *
     * @throws LiveCardException if the current thread is not the owner
     */
    private void requireOwner() {
        Thread current = Thread.currentThread();
        if (owner != null && owner != current) {
            throw new LiveCardException("The live card was connected in thread '" + owner.getName() + "' and is"
                    + " used in thread '" + current.getName() + "': its PC/SC connection holds exclusive access"
                    + " (javax.smartcardio Card.beginExclusive), which only the connecting thread may use. Use the card"
                    + " only from that thread: not inside assertTimeoutPreemptively or a test with @Timeout(threadMode"
                    + " = SEPARATE_THREAD) (use assertTimeout and @Timeout in its default SAME_THREAD mode), not from"
                    + " other threads or executors. Nothing was sent to the card.");
        }
    }

    private static UnsupportedOperationException installNotSupported() {
        return new UnsupportedOperationException("Applets are loaded onto a real card with LiveCard.deploy(...)");
    }
}
