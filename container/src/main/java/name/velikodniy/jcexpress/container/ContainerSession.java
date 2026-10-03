package name.velikodniy.jcexpress.container;

import javacard.framework.Applet;
import javacard.framework.CardRuntimeException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.AppletInstallParameters;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.apdu.ClassByte;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Smart card session backed by the jCardSim simulator server (normally running in a Docker container) over TCP.
 *
 * <p>Each session (TCP connection) has its own simulated card, independent of other sessions on the same server.
 * Behaviour matches the embedded backend:</p>
 * <ul>
 *   <li>{@link #install(Class, AID, byte[])} ships the applet class and every class it needs (superclasses,
 *       interfaces, helper and nested classes, transitively) and passes {@code installParams} as the applet data
 *       of the Java Card {@code Applet.install} parameters ({@code [Li][AID][Lc][control][La][data]}, at most 127
 *       bytes in total, see {@link AppletInstallParameters});</li>
 *   <li>{@link #select(AID)} and the install methods throw {@link SelectException} when the applet is not
 *       selected (ISO/IEC 7816-4:2005 7.1.1);</li>
 *   <li>{@link #send(int, int, int, int, byte[], int)} encodes commands with {@link APDUCodec};</li>
 *   <li>only the basic logical channel exists (jCardSim): commands for other channels and MANAGE CHANNEL are
 *       refused with {@link UnsupportedOperationException} (ISO/IEC 7816-4:2005 5.1.1.2);</li>
 *   <li>{@link #reset()} is a card reset: applets and their persistent state survive.</li>
 * </ul>
 *
 * <p>Failures inside the simulator are rethrown locally: {@code javacard.framework} exceptions with their reason
 * code and {@code java.*} runtime exceptions with their message (exactly what embedded mode throws), each with a
 * {@link SimulatorException} cause describing the remote failure; anything else as {@link SimulatorException}.
 * Transport failures are {@link UncheckedIOException}s. Every request has a reply timeout
 * ({@value #TIMEOUT_PROPERTY} seconds, default 60): an applet stuck in an endless loop fails the call instead of
 * hanging the test, and the session is closed afterwards.</p>
 */
public class ContainerSession implements SmartCardSession {

    /** System property: reply timeout in seconds (default 60). */
    public static final String TIMEOUT_PROPERTY = "jcx.container.timeout";

    /** Reply timeout used when {@link #TIMEOUT_PROPERTY} is not set. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private static final Logger LOG = Logger.getLogger(ContainerSession.class.getName());

    private static final int INS_MANAGE_CHANNEL = 0x70;
    private static final int SW_SUCCESS = 0x9000;

    private final SimulatorConnection connection;
    private final AutoCloseable container;
    private final Map<String, AID> classToAid = new HashMap<>();
    /** AIDs installed on this session's card (each session has its own card; a card reset keeps them). */
    private final List<AID> installed = new ArrayList<>();

    /**
     * Connects to a simulator server with the reply timeout of {@link #TIMEOUT_PROPERTY}.
     *
     * @param host      server host
     * @param port      server port
     * @param container resource closed together with the session (the container), or {@code null}
     * @throws IOException if the connection cannot be established
     */
    public ContainerSession(String host, int port, AutoCloseable container) throws IOException {
        this(host, port, container, configuredTimeout());
    }

    /**
     * Connects to a simulator server.
     *
     * @param host      server host
     * @param port      server port
     * @param container resource closed together with the session (the container), or {@code null}
     * @param timeout   maximum time to wait for the reply to a request
     * @throws IOException if the connection cannot be established
     */
    public ContainerSession(String host, int port, AutoCloseable container, Duration timeout) throws IOException {
        this(host, port, container, timeout, null);
    }

    /**
     * Connects to a running simulator container, presenting its access token if it has one
     * ({@link SmartCardContainer#withAccessToken()}). The container stays running when the session is closed, so
     * several sessions (each with its own card) can share it.
     *
     * @param container the started container
     * @throws IOException if the connection cannot be established
     */
    public ContainerSession(SmartCardContainer container) throws IOException {
        this(container.getHost(), container.getPort(), null, configuredTimeout(), container.accessToken());
    }

    /**
     * Connects and authenticates.
     *
     * @param host        server host
     * @param port        server port
     * @param container   resource closed together with the session, or {@code null}
     * @param timeout     reply timeout
     * @param accessToken token the server requires ({@code JCX_TOKEN}), or {@code null}
     * @throws IOException if the connection cannot be established
     */
    ContainerSession(String host, int port, AutoCloseable container, Duration timeout, String accessToken)
            throws IOException {
        this.connection = new SimulatorConnection(host, port, timeout);
        this.container = container;
        if (accessToken != null) {
            try {
                call(Protocol.CMD_HELLO, accessToken.getBytes(StandardCharsets.UTF_8), "authenticate");
            } catch (RuntimeException e) {
                connection.close();
                throw e;
            }
        }
    }

    /**
     * Connects to a simulator server.
     *
     * @param host      server host
     * @param port      server port
     * @param container resource closed together with the session (the container), or {@code null}
     * @param logging   ignored
     * @throws IOException if the connection cannot be established
     * @deprecated the flag never had an effect; use {@link #ContainerSession(String, int, AutoCloseable)} and
     *             {@link #logged()} for APDU logging.
     */
    @Deprecated
    public ContainerSession(String host, int port, AutoCloseable container, boolean logging) throws IOException {
        this(host, port, container);
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        install(appletClass, AID.auto(appletClass));
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        install(appletClass, aid, new byte[0]);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code installParams} is the applet specific data (La part of the Java Card install parameters); the
     * applet receives {@code [Li][aid][00][La][installParams]}.</p>
     *
     * @throws IllegalArgumentException if the install parameters exceed 127 bytes in total
     * @throws IllegalStateException    if an applet with this AID is already installed on the card
     * @throws InstallException         if the applet's install method fails, as on the embedded backend (the
     *                                  exception the server reported is the cause)
     * @throws SelectException          if the new applet cannot be selected (it stays installed)
     */
    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        byte[] aidBytes = aid.toBytes();
        byte[] bArray = AppletInstallParameters.encode(aid, installParams);
        Map<String, byte[]> classes;
        try {
            classes = AppletClasses.of(appletClass);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the class files of " + appletClass.getName(), e);
        }
        byte[] payload = InstallPayload.encode(aidBytes, appletClass.getName(), classes, bArray);
        byte[] selectResponse;
        try {
            selectResponse = call(Protocol.CMD_INSTALL, payload,
                    "install applet " + appletClass.getName() + " as " + aid.toHex());
        } catch (CardRuntimeException e) {
            throw InstallException.onJCardSim(appletClass.getName(), aid, installParams, e);
        }
        classToAid.put(appletClass.getName(), aid);
        installed.add(aid);
        checkSelected(aid, selectResponse);
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        AID aid = classToAid.get(appletClass.getName());
        select(aid != null ? aid : AID.auto(appletClass));
    }

    /**
     * {@inheritDoc}
     *
     * @throws SelectException if the card answers an error status or no applet installed in this session matches
     *                         the AID (jCardSim then forwards the SELECT to the currently selected applet, which
     *                         stays selected)
     */
    @Override
    public void select(AID aid) {
        checkSelected(aid, call(Protocol.CMD_SELECT, aid.toBytes(), "select " + aid.toHex()));
    }

    /**
     * Resets the card like removing and reinserting it: installed applets and their persistent objects are kept,
     * transient arrays of type {@code CLEAR_ON_RESET} are cleared and no applet is selected until the next
     * {@link #select(AID)}.
     */
    @Override
    public void reset() {
        call(Protocol.CMD_CARD_RESET, new byte[0], "reset the card");
    }

    @Override
    public APDUResponse send(int cla, int ins) {
        return send(cla, ins, 0, 0, null, NO_LE);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2) {
        return send(cla, ins, p1, p2, null, NO_LE);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(cla, ins, p1, p2, data, NO_LE);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The command is encoded with {@link APDUCodec#encode(int, int, int, int, byte[], int)}: {@link #NO_LE}
     * sends no Le field, {@code 256} the short {@code Le = '00'}.</p>
     */
    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        return new APDUResponse(transmit(APDUCodec.encode(cla, ins, p1, p2, data, le)));
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException if the command addresses a logical channel other than the basic
     *                                       channel or is MANAGE CHANNEL (jCardSim implements only the basic
     *                                       channel)
     */
    @Override
    public byte[] transmit(byte[] rawApdu) {
        rejectLogicalChannels(rawApdu);
        return call(Protocol.CMD_TRANSMIT, rawApdu, "transmit APDU");
    }

    /**
     * Closes the connection and then the container passed to the constructor (if any).
     */
    @Override
    public void close() {
        connection.close();
        if (container != null) {
            try {
                container.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to stop the simulator container", e);
            }
        }
    }

    /**
     * ISO/IEC 7816-4:2005 7.1.1: a SELECT that fails leaves the current selection unchanged, so later commands
     * would reach another applet. jCardSim answers the SELECT of an AID it does not know by forwarding the command
     * to the selected applet, whose answer may be '9000'; it matches AIDs like {@code AID.partialEquals}, so the
     * SELECT selects an applet exactly when the requested bytes start the AID of an installed applet.
     */
    private void checkSelected(AID aid, byte[] selectResponse) {
        if (selectResponse.length < 2) {
            throw new SelectException(aid, 0, "SELECT " + aid.toHex() + ": the simulator server returned no SELECT"
                    + " response (a server older than this client?)");
        }
        int sw = ((selectResponse[selectResponse.length - 2] & 0xFF) << 8)
                | (selectResponse[selectResponse.length - 1] & 0xFF);
        if (sw != SW_SUCCESS) {
            throw new SelectException(aid, sw, String.format("SELECT %s failed: SW=%04X", aid.toHex(), sw));
        }
        byte[] requested = aid.toBytes();
        boolean known = installed.stream().map(AID::toBytes).anyMatch(bytes -> bytes.length >= requested.length
                && Arrays.equals(bytes, 0, requested.length, requested, 0, requested.length));
        if (!known) {
            throw new SelectException(aid, sw, "SELECT " + aid.toHex() + " returned 9000, but no applet with this"
                    + " AID is installed: the currently selected applet handled the command");
        }
    }

    /**
     * jCardSim implements only the basic logical channel: it ignores the channel bits of CLA and has no MANAGE
     * CHANNEL. ISO/IEC 7816-4:2005 5.1.1.2 gives every channel its own selection, so such commands are refused
     * instead of reaching the applet selected on the basic channel (same rule as the embedded backend). Byte
     * strings shorter than a command header are left to the server, which rejects them.
     */
    private static void rejectLogicalChannels(byte[] command) {
        if (command == null || command.length < 4) {
            return;
        }
        int cla = command[0] & 0xFF;
        if ((command[1] & 0xFF) == INS_MANAGE_CHANNEL && ClassByte.isInterindustry(cla)) {
            throw new UnsupportedOperationException("MANAGE CHANNEL is not supported by ContainerSession: jCardSim"
                    + " implements only the basic logical channel");
        }
        int channel = ClassByte.channel(cla);
        if (channel != 0) {
            throw new UnsupportedOperationException(String.format("CLA '%02X' addresses logical channel %d, but"
                    + " ContainerSession (jCardSim) implements only the basic channel and would deliver the command"
                    + " to the applet selected there", cla, channel));
        }
    }

    private byte[] call(byte command, byte[] payload, String action) {
        SimulatorConnection.Reply reply;
        try {
            reply = connection.call(command, payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to " + action + ": " + e.getMessage(), e);
        }
        if (reply.status() != Protocol.STATUS_OK) {
            throw RemoteError.parse(reply.payload()).toException(action);
        }
        return reply.payload();
    }

    static Duration configuredTimeout() {
        String value = System.getProperty(TIMEOUT_PROPERTY);
        if (value == null || value.isBlank()) {
            return DEFAULT_TIMEOUT;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds > 0) {
                return Duration.ofSeconds(seconds);
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException("-D" + TIMEOUT_PROPERTY + " must be a positive number of seconds, got '"
                + value + "'");
    }
}
