package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUHistory;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.AppletInstallParameters;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.SystemException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Embedded smart card session backed by jCardSim (in-process simulation).
 *
 * <p>Each session is a card of its own: it creates its own {@link CardSimulator} and loads the applet classes
 * it installs with its own class loader, so a new session starts with fresh static fields, as a card on which the
 * applet packages were just loaded. The applets installed in one session share their static fields, as the
 * applets of one package on a card. A test's own references to an applet class (for example
 * {@code MyApplet.counter}) see the test's copy of the class, not the session's. Every class the applet code
 * needs is loaded again except the platform: the JDK, the Java Card API and jCardSim. The system property
 * {@value #SHARED_STATICS_PROPERTY}{@code =true} restores the behaviour of earlier releases, where all sessions ran the
 * test's classes and shared their static fields; it is deprecated and will be removed in the next release.</p>
 *
 * <ul>
 *   <li>{@link #install(Class, AID, byte[])} installs and selects an applet,
 *       {@link #installWithoutSelecting(Class, AID, byte[])} only installs it, as a GlobalPlatform card does; both
 *       pass the parameters in the Java Card {@code Applet.install} layout (see {@link AppletInstallParameters}).</li>
 *   <li>{@link #deselect()} deselects the selected applet without selecting another one.</li>
 *   <li>{@link #send(int, int, int, int, byte[], int)} returns '61XX' and '6CXX' as the applet answered them;
 *       {@link name.velikodniy.jcexpress.apdu.APDUSequence} completes them (the card of
 *       {@link name.velikodniy.jcexpress.JavaCardTest} classes does so in its {@code send}).</li>
 *   <li>An exception that escapes the applet's {@code process} or {@code select} (jCardSim answers '6F00' or
 *       '6999') or its install method is noted in {@link #history()} after the exchange, with the first frame of
 *       the applet's package: {@code # applet threw java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds
 *       for length 4 at com.example.WalletApplet.process(WalletApplet.java:42)}.</li>
 *   <li>{@link #reset()} is a card reset: installed applets and their persistent state survive,
 *       {@code CLEAR_ON_RESET} transient memory is cleared and no applet is selected afterwards.</li>
 *   <li>{@link #select(AID)} throws {@link SelectException} when the applet is not selected.</li>
 *   <li>{@link #delete(AID)} deletes an applet instance; its classes stay loaded (static fields keep their
 *       values) and the AID can be installed again.</li>
 *   <li>A class the applet code needs but the test class path lacks is named: installing throws
 *       {@link IllegalStateException} with the class and the cause (jCardSim itself reports only a
 *       {@code SystemException}); during a command, jCardSim answers {@code 6F00} and {@link #history()} notes
 *       the class.</li>
 *   <li>Only the basic logical channel exists (jCardSim): commands for other channels and MANAGE CHANNEL
 *       throw {@link UnsupportedOperationException}.</li>
 *   <li>{@link #history()} records every exchange (SELECT included) with notes for installs and card
 *       resets.</li>
 *   <li>After {@link #close()} every operation throws {@link IllegalStateException}.</li>
 *   <li>Creating a session throws {@link IllegalStateException} when the {@code javacard.framework} classes
 *       on the classpath are not jCardSim's (for example compile-only API stubs declared first).</li>
 * </ul>
 */
public class EmbeddedSession implements SmartCardSession {

    /**
     * System property that makes sessions run the classes they are given, so that all sessions share the static
     * fields of applet classes (the behaviour of earlier releases). Deprecated; it will be removed in the next release.
     */
    static final String SHARED_STATICS_PROPERTY = "jcx.embedded.sharedStatics";

    private final SessionRuntime runtime = newRuntime();
    private final CardSimulator simulator = new CardSimulator(runtime);
    private final SessionClasses classes = SessionClasses.fromSystemProperty();
    /** Installed applets in installation order: instance AID to class name. */
    private final Map<AID, String> installed = new LinkedHashMap<>();
    private final APDUHistory history = new APDUHistory();
    private boolean closed;

    /**
     * Creates a new embedded session with an empty simulated card.
     *
     * @throws IllegalStateException if {@code javacard.framework.APDU} is not loaded from the jCardSim jar
     */
    public EmbeddedSession() {
        // the simulator is created with the fields
    }

    /**
     * Creates a new embedded session.
     *
     * @param logging ignored
     * @deprecated the flag never had an effect; use {@link #EmbeddedSession()} and {@link #logged()}
     *             (or {@link #logged(boolean)}) for APDU logging.
     */
    @Deprecated
    public EmbeddedSession(boolean logging) {
        this();
    }

    /**
     * Creates a new embedded session.
     *
     * @param logging          ignored
     * @param persistentMemory ignored: jCardSim does not model the size of persistent memory
     * @deprecated neither parameter ever had an effect; use {@link #EmbeddedSession()}.
     */
    @Deprecated
    public EmbeddedSession(boolean logging, int persistentMemory) {
        this();
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
     * <p>{@code installParams} is the applet specific data (the La part of the Java Card install
     * parameters, GlobalPlatform tag 'C9'); the applet receives
     * {@code [Li][aid][00][La][installParams]} (see {@link AppletInstallParameters}). The applet is selected
     * afterwards; {@link #installWithoutSelecting(Class, AID, byte[])} installs without selecting.</p>
     *
     * @throws IllegalArgumentException if the parameters do not fit into 127 bytes
     * @throws IllegalStateException    if an applet with this AID is already installed, or the applet code
     *                                  needs a class the test class path lacks
     * @throws InstallException         if the applet's install method fails (the message names the applet, the
     *                                  AID, the parameters and what the install method threw; the AID stays free)
     * @throws SelectException          if the new applet cannot be selected
     */
    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        installWithoutSelecting(appletClass, aid, installParams);
        select(aid);
    }

    /**
     * Installs an applet without selecting it, as INSTALL [for install and make selectable] does on a
     * GlobalPlatform card (GPCS v2.3.1 11.5: the application becomes selectable): the applet selected before stays
     * selected, and the new applet's {@code select()} runs at the first SELECT that names it. The card of
     * {@link name.velikodniy.jcexpress.JavaCardTest} classes installs declared applets this way on jCardSim, so that
     * {@code select()} and {@code deselect()} run as often as on a card.
     *
     * <p>{@code installParams} is the applet specific data, as for {@link #install(Class, AID, byte[])}.</p>
     *
     * @param appletClass   the applet class to install
     * @param aid           the instance AID
     * @param installParams the install parameters (null or empty for none)
     * @throws IllegalArgumentException if the parameters do not fit into 127 bytes
     * @throws IllegalStateException    if an applet with this AID is already installed, or the applet code needs a
     *                                  class the test class path lacks
     * @throws InstallException         if the applet's install method fails (the message names the applet, the
     *                                  AID, the parameters and what the install method threw; the AID stays free)
     */
    public void installWithoutSelecting(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        ensureOpen();
        byte[] bArray = AppletInstallParameters.encode(aid, installParams);
        javacard.framework.AID jcAid = AIDUtil.create(aid.toBytes());
        if (runtime.lookupApplet(jcAid) != null) {
            throw new IllegalStateException("An applet with AID " + aid.toHex() + " is already installed on"
                    + " this card (" + describeInstalled() + "); delete(AID) removes it, reset() keeps installed"
                    + " applets");
        }
        history.note("install " + appletClass.getName() + " as " + aid.toHex()
                + (installParams == null || installParams.length == 0 ? "" : " with parameters "
                + Hex.encode(installParams)));
        try {
            installApplet(jcAid, classes.load(appletClass), bArray);
        } catch (NoClassDefFoundError e) {
            runtime.forgetLoadFile(jcAid);
            Set<String> missing = classes.takeMissingClasses();
            if (missing.isEmpty()) {
                throw e;
            }
            throw missingClasses(appletClass, missing, e);
        } catch (ISOException | SystemException | IllegalArgumentException e) {
            runtime.forgetLoadFile(jcAid);
            throw installFailure(appletClass, aid, installParams, e);
        }
        installed.put(aid, appletClass.getName());
    }

    /**
     * Installs like {@code Simulator.installApplet}, except that the runtime passes on an {@code ISOException} of the
     * applet's install method with its reason (the simulator turns every failure into a {@code SystemException}).
     */
    private void installApplet(javacard.framework.AID aid, Class<? extends Applet> type, byte[] bArray) {
        synchronized (runtime) {
            simulator.loadApplet(aid, type);
            runtime.installApplet(aid, bArray, (short) 0, (byte) bArray.length);
        }
    }

    /**
     * Explains a failed installation ({@link InstallException#onJCardSim}). jCardSim reports a class missing inside
     * the install method as {@code SystemException}, so the classes the applet code looked for in vain are named
     * first; an exception the install method threw is noted and named (jCardSim reports it as
     * {@code SystemException} without the cause).
     */
    private IllegalStateException installFailure(Class<? extends Applet> appletClass, AID aid, byte[] installParams,
                                                 RuntimeException failure) {
        Optional<AppletFailure> thrown = runtime.takeFailure();
        Set<String> missing = classes.takeMissingClasses();
        if (!missing.isEmpty()) {
            return missingClasses(appletClass, missing, failure);
        }
        thrown.ifPresent(applet -> history.note(applet.describe()));
        InstallException explained = thrown.map(applet -> applet.installException(aid, installParams, failure))
                .orElseGet(() -> InstallException.onJCardSim(appletClass.getName(), aid, installParams, failure));
        history.note("install failed: " + explained.reason());
        return explained;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Selects the applet of this class installed last in this session.</p>
     *
     * @throws SelectException if no applet of this class is installed in this session (nothing is sent then,
     *                         {@link SelectException#sw()} is 0; the message lists the installed applets) or
     *                         the SELECT fails
     */
    @Override
    public void select(Class<? extends Applet> appletClass) {
        ensureOpen();
        AID aid = null;
        for (Map.Entry<AID, String> applet : installed.entrySet()) {
            if (applet.getValue().equals(appletClass.getName())) {
                aid = applet.getKey();
            }
        }
        if (aid == null) {
            throw new SelectException(AID.auto(appletClass), 0, appletClass.getName() + " is not installed on"
                    + " this card (" + describeInstalled() + ")");
        }
        select(aid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Like a Java Card runtime, jCardSim passes a SELECT whose AID matches no installed applet to the selected
     * applet, so the status word is that applet's answer to an unknown command (for example {@code 6D00} or
     * {@code 6E00}), {@code 6999} when no applet is selected, and may even be {@code 9000}; the exception
     * explains this and lists the installed applets.</p>
     *
     * @throws SelectException if the card answers an error status or another applet stays selected
     */
    @Override
    public void select(AID aid) {
        ensureOpen();
        String selected = selectedAppletName();
        byte[] result = exchange(APDUCodec.encode(0x00, 0xA4, 0x04, 0x00, aid.toBytes(), 256));
        int sw = ((result[result.length - 2] & 0xFF) << 8) | (result[result.length - 1] & 0xFF);
        if (sw == 0x9000 && isSelected(aid)) {
            return;
        }
        boolean known = installed.keySet().stream().anyMatch(installedAid -> installedAid.startsWith(aid));
        String message = sw == 0x9000
                ? "SELECT " + aid.toHex() + " returned 9000, but an applet with this AID is not installed"
                : String.format("SELECT %s failed: SW=%04X", aid.toHex(), sw);
        if (!known) {
            message += (sw == 0x9000 ? "" : ". An applet with this AID is not installed") + " on this card ("
                    + describeInstalled() + "); " + forwarded(selected, sw);
        }
        throw new SelectException(aid, sw, message);
    }

    /**
     * Resets the card like removing and reinserting it: installed applets and their persistent objects
     * are kept, transient arrays of type {@code CLEAR_ON_RESET} are cleared and no applet is selected
     * until the next {@link #select(AID)}.
     */
    @Override
    public void reset() {
        ensureOpen();
        history.note("card reset");
        simulator.reset();
    }

    /**
     * Deselects the selected applet, as selecting another application does: its {@code deselect()} method runs, an
     * open transaction is aborted and CLEAR_ON_DESELECT memory is cleared (JCRE 3.0.5 chapter 3, the method
     * deselect). No applet is selected afterwards, so commands other than SELECT answer {@code 6986} until the next
     * {@link #select(AID)}. Nothing is sent; the history notes the deselected applet. Without a selected applet it
     * does nothing.
     */
    @Override
    public void deselect() {
        ensureOpen();
        javacard.framework.AID deselected = runtime.deselectCurrent();
        if (deselected != null) {
            history.note("deselect " + hex(deselected));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>jCardSim calls {@code uninstall()} of an applet that implements {@code AppletEvent}. The classes stay
     * loaded in this session: installing an applet again, under this AID or another one, sees the static fields
     * as the deleted applet left them.</p>
     *
     * @throws IllegalStateException if no applet with this AID is installed (the message lists the installed
     *                               applets)
     */
    @Override
    public void delete(AID aid) {
        ensureOpen();
        javacard.framework.AID jcAid = AIDUtil.create(aid.toBytes());
        if (runtime.lookupApplet(jcAid) == null) {
            throw new IllegalStateException("No applet with AID " + aid.toHex() + " is installed on this card ("
                    + describeInstalled() + ")");
        }
        history.note("delete " + aid.toHex());
        simulator.deleteApplet(jcAid);
        installed.remove(aid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The command is encoded with {@link APDUCodec#encode(int, int, int, int, byte[], int)}; the response knows
     * it ({@link APDUResponse#inReplyTo(byte[])}).</p>
     */
    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, data, le);
        return new APDUResponse(transmit(command)).inReplyTo(command);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException      if the bytes are not a command APDU (ISO/IEC 7816-4:2005 5.1)
     * @throws UnsupportedOperationException if the command addresses a logical channel other than the basic
     *                                       channel or is MANAGE CHANNEL (jCardSim implements only the basic
     *                                       channel)
     */
    @Override
    public byte[] transmit(byte[] rawApdu) {
        ensureOpen();
        return exchange(BasicChannel.checked(rawApdu));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Records every command with its response (the SELECT commands of {@link #select(AID)} and of the install
     * methods included), installs and card resets.</p>
     */
    @Override
    public APDUHistory history() {
        return history;
    }

    /**
     * Closes the session and discards the simulated card. Further calls throw
     * {@link IllegalStateException}; closing twice is allowed.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            simulator.resetRuntime();
            classes.close();
        }
    }

    /**
     * Sends a command to the simulated card and records the exchange with its time, with a note of what the applet
     * threw (jCardSim answers it with '6F00', or '6999' for {@code select}) and one for each class the applet code
     * needed but did not find (jCardSim answers such a failure with '6F00').
     */
    private byte[] exchange(byte[] command) {
        byte[] response = history.exchange(command, () -> simulator.transmitCommand(command));
        Optional<AppletFailure> thrown = runtime.takeFailure();
        Set<String> missing = classes.takeMissingClasses();
        if (missing.isEmpty()) {
            thrown.ifPresent(failure -> history.note(failure.describe()));
        }
        for (String name : missing) {
            history.note("the applet code needs " + name + ", which is not on the test class path");
        }
        return response;
    }

    /** Checks the classpath first: with shadowed API classes jCardSim fails with "Internal reflection error". */
    private static SessionRuntime newRuntime() {
        SimulatorClasspath.verify();
        return new SessionRuntime();
    }

    /**
     * Explains a failed installation: jCardSim reports any failure inside {@code Applet.install} as
     * {@code SystemException}, so the classes the applet code looked for in vain are named.
     */
    private IllegalStateException missingClasses(Class<? extends Applet> appletClass, Set<String> missing,
                                                  Throwable failure) {
        missing.forEach(name -> history.note("the applet code needs " + name + ", which is not on the test"
                + " class path"));
        return new IllegalStateException(MissingClasses.describe(appletClass.getName(), missing), failure);
    }

    /** The class name of the selected applet, or null when no applet is selected. */
    private String selectedAppletName() {
        javacard.framework.AID current = runtime.getAID();
        if (current == null) {
            return null;
        }
        return installed.getOrDefault(AID.fromHex(hex(current)), "an applet");
    }

    /** A jCardSim AID as hex. */
    private static String hex(javacard.framework.AID aid) {
        byte[] bytes = new byte[16];
        byte length = aid.getBytes(bytes, (short) 0);
        return Hex.encode(Arrays.copyOf(bytes, length));
    }

    /** What jCardSim did with a SELECT that matches no installed applet. */
    private static String forwarded(String selected, int sw) {
        if (selected == null) {
            return String.format("no applet was selected, so jCardSim answered %04X (the Issuer Security Domain of"
                    + " a card answers 6A82)", sw);
        }
        if (sw == 0x9000) {
            return "jCardSim passed the SELECT to the selected applet, " + selected + ", which answered 9000 and"
                    + " stays selected";
        }
        return String.format("jCardSim passed the SELECT to the selected applet, %s, which answered %04X (the"
                + " Issuer Security Domain of a card answers 6A82)", selected, sw);
    }

    /** The installed applets, for messages: "installed: com.example.A as F0..., ..." or "nothing is installed". */
    private String describeInstalled() {
        if (installed.isEmpty()) {
            return "nothing is installed";
        }
        StringJoiner list = new StringJoiner(", ", "installed: ", "");
        installed.forEach((aid, name) -> list.add(name + " as " + aid.toHex()));
        return list.toString();
    }

    private boolean isSelected(AID aid) {
        javacard.framework.AID current = runtime.getAID();
        if (current == null) {
            return false;
        }
        byte[] buffer = new byte[16];
        int length = current.getBytes(buffer, (short) 0);
        byte[] requested = aid.toBytes();
        return length >= requested.length
                && Arrays.equals(buffer, 0, requested.length, requested, 0, requested.length);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("EmbeddedSession is closed");
        }
    }
}
