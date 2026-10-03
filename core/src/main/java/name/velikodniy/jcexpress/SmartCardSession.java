package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.apdu.APDUCommand;
import name.velikodniy.jcexpress.pin.PinSession;

/**
 * Main interface for interacting with a smart card (real or simulated).
 *
 * <p>Provides a unified API for installing applets, sending APDU commands,
 * and managing the card lifecycle. Implementations include the embedded (jCardSim),
 * container (Docker) and PC/SC (real card) backends.</p>
 *
 * <h2>Command encoding</h2>
 * <p>{@link #send(int, int, int, int, byte[], int)} builds a command APDU according to
 * ISO/IEC 7816-4:2005 5.1. The {@code le} argument encodes Ne, the maximum number of response
 * bytes expected:</p>
 * <ul>
 *   <li>{@link #NO_LE} ({@code -1}): the Le field is absent (Ne = 0) &mdash; case 1 or case 3
 *       command. This is what the overloads without {@code le} send.</li>
 *   <li>{@code 1..65536}: Ne. A short Le field is used when Ne &le; 256 and the data field is at most
 *       255 bytes ({@code 256} is coded as {@code '00'}); otherwise extended length fields are used
 *       ({@code 65536} is coded as {@code '0000'}).</li>
 *   <li>{@code 0}: an Le field with all bytes set to {@code '00'}, i.e. "maximum" (ISO/IEC 7816-4:2005
 *       5.1): {@code '00'} (Ne = 256) on a short command, {@code '0000'} (Ne = 65 536) on an extended one.
 *       Kept for compatibility; prefer {@code 256} to request {@code Le = '00'}.</li>
 *   <li>Any other value is rejected with {@link IllegalArgumentException}.</li>
 * </ul>
 * <p>All backends shipped with JavaCard Express encode commands with
 * {@link name.velikodniy.jcexpress.apdu.APDUCodec#encode(int, int, int, int, byte[], int)}, so the same
 * call produces the same bytes on every backend. Header bytes are {@code 0x00} to {@code 0xFF} or the
 * {@code byte} constants of an applet: {@code send(WalletApplet.CLA_WALLET, WalletApplet.INS_GET_BALANCE)} works
 * with {@code static final byte CLA_WALLET = (byte) 0x80}. A command can also be a value, {@link APDUCommand}
 * ({@link #send(APDUCommand)}), or hex text ({@link #sendHex(String)}).</p>
 *
 * <h2>Logical channels</h2>
 * <p>A command refers to a logical channel through its class byte (ISO/IEC 7816-4:2005 5.1.1; see
 * {@link name.velikodniy.jcexpress.apdu.ClassByte} and {@link LogicalChannel}). A backend that cannot deliver
 * a command to the channel its CLA codes throws {@link UnsupportedOperationException} instead of sending it
 * to another channel: {@link name.velikodniy.jcexpress.embedded.EmbeddedSession} and the container backend
 * ({@code ContainerSession}, a remote jCardSim) implement only the basic channel,
 * {@link name.velikodniy.jcexpress.pcsc.PcscSession} supports the channels opened through it with MANAGE
 * CHANNEL OPEN ({@link LogicalChannel#open(SmartCardSession)}).</p>
 */
public interface SmartCardSession extends AutoCloseable {

    /**
     * Value of the {@code le} argument of {@link #send(int, int, int, int, byte[], int)} meaning
     * "no Le field" (Ne = 0). ISO/IEC 7816-4:2005 5.1: "the absence of Le field is the standard way for
     * receiving no response data field".
     */
    int NO_LE = -1;

    // === Lifecycle ===

    /**
     * Installs and selects an applet. AID is generated automatically from the class name.
     *
     * @param appletClass the applet class to install
     */
    void install(Class<? extends Applet> appletClass);

    /**
     * Installs and selects an applet with an explicit AID.
     *
     * @param appletClass the applet class to install
     * @param aid         the AID to assign
     */
    void install(Class<? extends Applet> appletClass, AID aid);

    /**
     * Installs and selects an applet with an explicit AID and install parameters.
     *
     * @param appletClass   the applet class to install
     * @param aid           the AID to assign
     * @param installParams installation parameters
     */
    void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams);

    /**
     * Selects a previously installed applet by class (uses the AID assigned during install).
     *
     * <p>The card of a {@link JavaCardTest} class selects the one instance of the class installed for the current
     * test, as {@link #aid(Class)} names it: with no instance or more than one it throws
     * {@link IllegalStateException}, whose message names each instance with its AID suffix ({@code
     * card.select(card.aid("0102"))} selects one of several).</p>
     *
     * @param appletClass the applet class to select
     */
    void select(Class<? extends Applet> appletClass);

    /**
     * Selects a previously installed applet by AID: SELECT by DF name with Le {@code '00'}
     * ({@link APDUCommand#select(AID)}, ISO/IEC 7816-4:2005 7.1.1). The answer to SELECT is not returned; to assert
     * it (an FCI, an application property template), send the command:
     * {@code assertThat(card.send(APDUCommand.select(aid))).isSuccess().tlv().tag(0x6F)}.
     *
     * @param aid the AID of the applet to select
     */
    void select(AID aid);

    /**
     * Resets the card (equivalent to removing and reinserting the card).
     */
    void reset();

    /**
     * Deletes an applet instance (as a GlobalPlatform DELETE of the application). Its package stays loaded: the
     * static fields of its classes keep their values, and an applet can be installed under the same AID again.
     * When the deleted applet was selected, no applet is selected afterwards.
     *
     * <p>{@link name.velikodniy.jcexpress.embedded.EmbeddedSession} implements it; the default implementation,
     * used by backends that cannot delete applets (the container backend, a PC/SC reader without a secure
     * channel), throws {@link UnsupportedOperationException}.</p>
     *
     * @param aid the AID of the applet instance
     * @throws UnsupportedOperationException if this session cannot delete applets
     * @throws IllegalStateException         if no applet with this AID is installed
     */
    default void delete(AID aid) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " cannot delete applets: delete(AID)"
                + " is not supported by this backend");
    }

    /**
     * Returns the AID of the instance of an applet class that this card installed for the current test. In
     * {@link JavaCardTest} classes the AID depends on the run's AID prefix, so tests ask for it instead of writing it
     * down. A class installed more than once names no instance (as Spring does not pick one of two beans of a type):
     * the exception lists each instance with its AID suffix, and {@link #aid(String)} names one,
     * {@code card.aid("0102")}.
     *
     * <p>The card injected into {@link JavaCardTest} classes implements it; the default implementation throws
     * {@link UnsupportedOperationException}. An instance declared with {@link Isolation#PER_TEST} (the default) or on
     * a test method exists only while a test runs, so in {@code @BeforeAll} and {@code @AfterAll} methods there is
     * none; the message then says so.</p>
     *
     * @param appletClass the applet class
     * @return the instance AID
     * @throws UnsupportedOperationException if this session does not track its applets by class
     * @throws IllegalStateException         if no instance or more than one instance of the class is installed
     */
    default AID aid(Class<? extends Applet> appletClass) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not track applets by class:"
                + " aid(Class) is available on the card of a @JavaCardTest class");
    }

    /**
     * Returns the AID made of the run's AID prefix and a hex suffix: the AID an {@link InstallApplet#aid()} suffix
     * stands for. The card injected into {@link JavaCardTest} classes implements it; the default implementation
     * throws {@link UnsupportedOperationException}.
     *
     * @param suffixHex the suffix (hex)
     * @return the AID
     * @throws UnsupportedOperationException if this session has no AID prefix
     */
    default AID aid(String suffixHex) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no AID prefix: aid(String) is"
                + " available on the card of a @JavaCardTest class");
    }

    /**
     * Deselects the selected applet (its {@code deselect()} method runs and CLEAR_ON_DESELECT memory is cleared):
     * on a GlobalPlatform card by selecting the Issuer Security Domain, on jCardSim by selecting an empty applet
     * of the backend. The card injected into {@link JavaCardTest} classes implements it, and selects the nearest
     * applet again before the next test; {@link name.velikodniy.jcexpress.embedded.EmbeddedSession} deselects without
     * selecting another applet. The default implementation throws {@link UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException if this session cannot deselect
     */
    default void deselect() {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " cannot deselect: deselect() is"
                + " available on the card of a @JavaCardTest class");
    }

    // === APDU ===

    /**
     * Sends a command with CLA and INS only (P1 = P2 = '00', no data, no Le field: ISO case 1).
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @return the response
     */
    default APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0, null, NO_LE);
    }

    /**
     * Sends a command with CLA, INS, P1 and P2 (no data, no Le field: ISO case 1).
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the response
     */
    default APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, NO_LE);
    }

    /**
     * Sends a command with CLA, INS, P1, P2 and data, without Le field (ISO case 3, or case 1 when
     * {@code data} is {@code null} or empty).
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @return the response
     */
    default APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, NO_LE);
    }

    /**
     * Sends a command with CLA, INS, P1, P2, data and Le.
     *
     * <p>See the class documentation for the meaning of {@code le}: {@link #NO_LE} for no Le field,
     * {@code 1..65536} for Ne ({@code 256} gives the short Le {@code '00'}), {@code 0} for an Le field of
     * {@code '00'} bytes.</p>
     *
     * <p>The card of a {@link JavaCardTest} class completes '61XX' with GET RESPONSE and repeats a command answered
     * '6CXX' with the exact Le (ISO/IEC 7816-4:2005 5.1.3), on every backend, as the PC/SC provider of the JDK does
     * on a reader; its history holds every exchange. Sessions used directly return '61XX' and '6CXX' as the card
     * answered unless their transport completes them (see {@link SW}).</p>
     *
     * @param cla  the CLA byte: 0x00-0xFF, or a {@code byte} constant ({@code (byte) 0x80} is -128)
     * @param ins  the INS byte: 0x00-0xFF, or a {@code byte} constant
     * @param p1   the P1 byte: 0x00-0xFF, or a {@code byte} constant
     * @param p2   the P2 byte: 0x00-0xFF, or a {@code byte} constant
     * @param data the command data (may be null; at most 65 535 bytes)
     * @param le   Ne, {@code 0} or {@link #NO_LE}
     * @return the response
     * @throws IllegalArgumentException if a header byte (outside -128 to 0xFF), the data length or {@code le} is
     *                                  out of range
     */
    APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le);

    /**
     * Sends a command given as a value, e.g. a constant of the test class:
     *
     * <pre>
     * static final APDUCommand GET_BALANCE = APDUCommand.of(0x80, 0x52).le(2);
     *
     * assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(70);
     * </pre>
     *
     * <p>The default implementation calls {@link #send(int, int, int, int, byte[], int)}, so the command takes the
     * same path as any other: the same encoding, history and transcripts, the completion of '61XX' and '6CXX' where
     * the session does it (the card of a {@link JavaCardTest} class, see {@link SW}), logging and secure messaging
     * of decorating sessions.</p>
     *
     * @param command the command
     * @return the response
     */
    default APDUResponse send(APDUCommand command) {
        return send(command.cla(), command.ins(), command.p1(), command.p2(), command.data(), command.le());
    }

    /**
     * Sends a command written as hex, as in a specification or a trace: {@code card.sendHex("80 30 00 00 02 0064")}.
     * The text is parsed with {@link APDUCommand#fromHex(String)} (spaces are ignored, all cases of ISO/IEC
     * 7816-4:2005 5.1 are read) and sent with {@link #send(APDUCommand)}, so it takes the path of
     * {@link #send(int, int, int, int, byte[], int)}; {@link #transmit(byte[])} instead sends bytes unchanged and
     * returns the raw response.
     *
     * @param apdu the command APDU as hex
     * @return the response
     * @throws IllegalArgumentException if the text is not hex or its length fields do not match its bytes
     */
    default APDUResponse sendHex(String apdu) {
        return send(APDUCommand.fromHex(apdu));
    }

    /**
     * Sends raw APDU bytes and returns raw response bytes. Nothing is completed: '61XX' and '6CXX' come back as the
     * card answered them (on a PC/SC reader the JDK may have completed them below the session, see {@link SW});
     * {@link name.velikodniy.jcexpress.apdu.APDUSequence} completes them.
     *
     * @param rawApdu the raw APDU bytes
     * @return the raw response bytes (including SW)
     */
    byte[] transmit(byte[] rawApdu);

    // === Diagnostics ===

    /**
     * Returns the recent exchanges of this session: every command with its response (SELECT commands included)
     * and notes such as applet installations and card resets, in a bounded buffer
     * ({@link APDUHistory#DEFAULT_CAPACITY} entries). {@link JavaCardExtension} attaches the entries of a failed
     * test to its failure; for the card of a {@link JavaCardTest} class it also publishes them as the file
     * {@code apdu-transcript.txt} of the test.
     *
     * <p>{@link name.velikodniy.jcexpress.embedded.EmbeddedSession}, the container backend
     * ({@code ContainerSession}) and {@link name.velikodniy.jcexpress.pcsc.PcscSession} record their exchanges;
     * {@link LoggingSession} returns the history of the session it wraps. The default implementation records
     * nothing.</p>
     *
     * <p>The card of a {@link JavaCardTest} class returns, inside a test (its {@code @BeforeEach} and
     * {@code @AfterEach} methods included), the entries of that test: from its start on, after the installs of the
     * test and from the SELECT of the nearest applet; in {@code @BeforeAll} and {@code @AfterAll} methods those since
     * the class started (after its PER_CLASS installs). The transcripts attached to failures and published as files
     * still cover everything since the test started, installs included.</p>
     *
     * @return the history, never null; {@link APDUHistory#none()} for a session that keeps none
     */
    default APDUHistory history() {
        return APDUHistory.none();
    }

    // === Decorators ===

    /**
     * Wraps this session with APDU logging.
     *
     * @return a logging-enabled session
     */
    default LoggingSession logged() {
        return LoggingSession.wrap(this);
    }

    /**
     * Wraps this session with APDU logging.
     *
     * @param printToLog true to also print the exchanges, one line each on standard output ({@code [JCX] C: ...},
     *                   through java.util.logging; see {@link LoggingSession} for configuring it)
     * @return a logging-enabled session
     */
    default LoggingSession logged(boolean printToLog) {
        return LoggingSession.wrap(this, printToLog);
    }

    /**
     * Creates a PIN helper on this session.
     *
     * @return a PIN session for verify/change/unblock operations
     */
    default PinSession pin() {
        return PinSession.on(this);
    }

    /**
     * Closes this session and releases resources. Closing twice has no further effect;
     * {@link name.velikodniy.jcexpress.embedded.EmbeddedSession} and
     * {@link name.velikodniy.jcexpress.pcsc.PcscSession} throw {@link IllegalStateException} when used after
     * {@code close()}.
     */
    @Override
    void close();
}
