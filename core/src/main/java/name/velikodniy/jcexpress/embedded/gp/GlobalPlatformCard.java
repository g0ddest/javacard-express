package name.velikodniy.jcexpress.embedded.gp;

import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.base.SimulatorSystem;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The GlobalPlatform environment (the OPEN, GlobalPlatform Card Specification v2.3.1 chapter 6) of one simulated
 * card, as the applets on it see it through {@code org.globalplatform.GPSystem}: the GlobalPlatform Registry with
 * the life cycle state and privileges of every application, the life cycle state of the card, the Security Domain
 * whose secure channel the applications use, the Global Services they registered, the historical bytes of the ATR
 * and the data objects of the Issuer Security Domain.
 *
 * <p>A test reads and changes the card's side directly, for what a card issuer or the card's own management
 * would do:</p>
 * <pre>{@code
 * GlobalPlatformCard gp = card.globalPlatform();                       // EmbeddedSession
 * gp.cardState(GPSystem.CARD_SECURED);
 * gp.application(walletAid).orElseThrow().grant(GPRegistryEntry.PRIVILEGE_CARD_LOCK);
 * gp.securityDomain().scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);
 * }</pre>
 *
 * <p>What is simulated: {@link SecurityDomain the secure channel} (SCP03), the life cycle rules of
 * {@code setCardContentState} and {@code GPRegistryEntry.setState}, {@code lockCard} and {@code terminateCard}
 * with their privileges, {@code setATRHistBytes}, {@code getCardInfo}, and the registration and lookup of Global
 * Services ({@code getService}, {@code getCVM}). The card provides no Global PIN and no other service of its own:
 * {@code getCVM} returns what an application of the test registered for the CVM family, else null. The card life
 * cycle state is a value the applets read; a locked or terminated simulated card still selects its applications.
 * Every application is associated with the one Security Domain, the Issuer Security Domain, which has no registry
 * entry of its own. STORE DATA is not forwarded to {@code Application} or {@code Personalization}.</p>
 *
 * <p>The object is used from the thread that talks to the card, like the session it belongs to. A jCardSim
 * runtime that implements {@link GlobalPlatformHost} reports to it what happens on the card (see there); several
 * runtimes may share one card.</p>
 */
public final class GlobalPlatformCard {

    /** Card life cycle state OP_READY (GPCS v2.3.1 Table 11-6). */
    public static final int OP_READY = 0x01;
    /** Card life cycle state INITIALIZED (GPCS v2.3.1 Table 11-6). */
    public static final int INITIALIZED = 0x07;
    /** Card life cycle state SECURED (GPCS v2.3.1 Table 11-6). */
    public static final int SECURED = 0x0F;
    /** Card life cycle state CARD_LOCKED (GPCS v2.3.1 Table 11-6). */
    public static final int CARD_LOCKED = 0x7F;
    /** Card life cycle state TERMINATED (GPCS v2.3.1 Table 11-6). */
    public static final int TERMINATED = 0xFF;

    private static final List<Integer> CARD_STATES = List.of(OP_READY, INITIALIZED, SECURED, CARD_LOCKED, TERMINATED);

    private final SecurityDomain securityDomain;
    private final CardSecureChannel secureChannel;
    private final Map<AID, CardApplication> registry = new LinkedHashMap<>();
    private final Map<AID, RegistryEntry> entries = new LinkedHashMap<>();
    private final GlobalServices services = new GlobalServices();
    private final Map<Integer, byte[]> dataObjects = new TreeMap<>();
    private int cardState = INITIALIZED;
    private byte[] historicalBytes = new byte[0];
    private byte[] historicalBytesAfterReset;
    private AID installing;

    /**
     * Creates the GlobalPlatform environment of a card: no application, card life cycle state INITIALIZED.
     *
     * @param securityDomain the Security Domain of the card; a domain belongs to one card
     */
    public GlobalPlatformCard(SecurityDomain securityDomain) {
        this.securityDomain = securityDomain;
        this.secureChannel = new CardSecureChannel(securityDomain.session(), this::caller);
    }

    /**
     * Creates the GlobalPlatform environment of a card whose Security Domain has the well-known test key set
     * ({@link SecurityDomain#withTestKeys()}).
     *
     * @return the card
     */
    public static GlobalPlatformCard withTestKeys() {
        return new GlobalPlatformCard(SecurityDomain.withTestKeys());
    }

    /**
     * Returns the Security Domain every application of this card is associated with.
     *
     * @return the Security Domain
     */
    public SecurityDomain securityDomain() {
        return securityDomain;
    }

    /**
     * Returns the life cycle state of the card, which {@code GPSystem.getCardState()} reports.
     *
     * @return the state as an unsigned value (GPCS v2.3.1 Table 11-6), INITIALIZED on a new card
     */
    public int cardState() {
        return cardState;
    }

    /**
     * Sets the life cycle state of the card, as the card issuer does with SET STATUS.
     *
     * @param state one of {@link #OP_READY}, {@link #INITIALIZED}, {@link #SECURED}, {@link #CARD_LOCKED} and
     *              {@link #TERMINATED}; a {@code byte} constant of {@code GPSystem} is accepted
     * @return this card
     * @throws IllegalArgumentException for any other value
     */
    public GlobalPlatformCard cardState(int state) {
        int value = state & 0xFF;
        if (state < -128 || state > 0xFF || !CARD_STATES.contains(value)) {
            throw new IllegalArgumentException(String.format("%02X is no card life cycle state (GPCS v2.3.1 Table"
                    + " 11-6: 01, 07, 0F, 7F, FF)", value));
        }
        this.cardState = value;
        return this;
    }

    /**
     * Returns the applications in the GlobalPlatform Registry.
     *
     * @return the applications in the order they were registered
     */
    public List<CardApplication> applications() {
        return List.copyOf(registry.values());
    }

    /**
     * Returns an application of the GlobalPlatform Registry.
     *
     * @param aid the instance AID
     * @return the application, or empty when none has this AID
     */
    public Optional<CardApplication> application(AID aid) {
        return Optional.ofNullable(registry.get(aid));
    }

    /**
     * Adds an application to the GlobalPlatform Registry without running an install method, for an application a
     * card simulation knows by other means.
     *
     * @param aid        the instance AID
     * @param state      the life cycle state, 0 to 255
     * @param privileges the privilege bytes as INSTALL carries them: none, one or three
     * @return the application
     * @throws IllegalStateException if an application with this AID is registered
     */
    public CardApplication register(AID aid, int state, byte[] privileges) {
        if (registry.containsKey(aid)) {
            throw new IllegalStateException("The GlobalPlatform Registry already has an application " + aid.toHex());
        }
        CardApplication application = new CardApplication(aid, state, privileges);
        registry.put(aid, application);
        return application;
    }

    /**
     * Removes an application from the GlobalPlatform Registry when it is deleted: its Global Services are
     * deregistered, and a secure channel session it opened ends. Does nothing for an unknown AID.
     *
     * @param aid the instance AID
     */
    public void unregister(AID aid) {
        registry.remove(aid);
        entries.remove(aid);
        services.removeAll(aid);
        if (securityDomain.session().ownedBy(aid)) {
            securityDomain.session().terminate();
        }
    }

    /**
     * Reports that the install method of an applet is about to run: the application enters the GlobalPlatform
     * Registry in the state INSTALLED, and until {@link #installSucceeded} or {@link #installFailed} it is the
     * application on whose behalf {@code GPSystem} is called.
     *
     * @param aid        the instance AID
     * @param privileges the privilege bytes of the INSTALL command: none, one or three
     * @return the application
     * @throws IllegalStateException if an application with this AID is registered
     */
    public CardApplication installStarted(AID aid, byte[] privileges) {
        CardApplication application = register(aid, CardApplication.INSTALLED, privileges);
        installing = aid;
        return application;
    }

    /**
     * Reports that the install method returned and the applet is registered.
     *
     * @param aid   the instance AID
     * @param state the life cycle state the application has now: {@link CardApplication#INSTALLED} after
     *              INSTALL [for install], {@link CardApplication#SELECTABLE} after INSTALL [for install and make
     *              selectable] (GPCS v2.3.1 11.5.2.1)
     */
    public void installSucceeded(AID aid, int state) {
        installing = null;
        CardApplication application = registry.get(aid);
        if (application != null) {
            application.state(state);
        }
    }

    /**
     * Reports that the install method failed: the application leaves the GlobalPlatform Registry.
     *
     * @param aid the instance AID
     */
    public void installFailed(AID aid) {
        installing = null;
        unregister(aid);
    }

    /**
     * Reports that the Application Session on the basic channel ended: the selected application was deselected,
     * or an application (another one or the same) is being selected. A secure channel session ends with it
     * (GPCS v2.3.1 10.2.3; Amendment D 5.6).
     */
    public void applicationSessionEnded() {
        securityDomain.session().terminate();
    }

    /**
     * Reports a card reset: a secure channel session ends (GPCS v2.3.1 10.2.3), and historical bytes an
     * application set with {@code GPSystem.setATRHistBytes} become the ones of the ATR.
     */
    public void cardReset() {
        securityDomain.session().terminate();
        if (historicalBytesAfterReset != null) {
            historicalBytes = historicalBytesAfterReset;
            historicalBytesAfterReset = null;
        }
    }

    /**
     * Returns the historical bytes of the Answer To Reset, which {@code GPSystem.getCardInfo} reports for
     * {@code CARD_INFO_ATR_HISTORICAL_BYTES}.
     *
     * @return a copy of the bytes, empty on a new card (the card then has none to report)
     */
    public byte[] historicalBytes() {
        return historicalBytes.clone();
    }

    /**
     * Sets the historical bytes of the Answer To Reset the card sent last.
     *
     * @param bytes at most 15 bytes (ISO/IEC 7816-3)
     * @return this card
     * @throws IllegalArgumentException if there are more than 15 bytes
     */
    public GlobalPlatformCard historicalBytes(byte[] bytes) {
        if (bytes.length > 15) {
            throw new IllegalArgumentException("An ATR has at most 15 historical bytes, got " + bytes.length);
        }
        this.historicalBytes = bytes.clone();
        return this;
    }

    /**
     * Returns the historical bytes an application set with {@code GPSystem.setATRHistBytes}, which take effect
     * at the next card reset.
     *
     * @return a copy of the bytes, or empty when no application set any since the last reset
     */
    public Optional<byte[]> historicalBytesAfterReset() {
        return Optional.ofNullable(historicalBytesAfterReset).map(byte[]::clone);
    }

    /**
     * Puts a data object into the data store of the Issuer Security Domain, where {@code GPSystem.getCardInfo}
     * finds it by its tag.
     *
     * @param tag   the BER-TLV tag, one or two bytes, for example {@code 0x66} or {@code 0x9F7F}
     * @param value the value of the data object
     * @return this card
     * @throws IllegalArgumentException if the tag is not a BER-TLV tag of one or two bytes
     */
    public GlobalPlatformCard dataObject(int tag, byte[] value) {
        if (!CardInfo.berTlvTag(tag)) {
            throw new IllegalArgumentException(String.format("%X is not a BER-TLV tag of one or two bytes", tag));
        }
        dataObjects.put(tag, value.clone());
        return this;
    }

    /** The secure channel object of the Security Domain. */
    CardSecureChannel secureChannel() {
        return secureChannel;
    }

    GlobalServices services() {
        return services;
    }

    Map<Integer, byte[]> dataObjects() {
        return dataObjects;
    }

    void historicalBytesAfterReset(byte[] bytes) {
        this.historicalBytesAfterReset = bytes;
    }

    /** The one registry entry object of an application, as applets get it. */
    RegistryEntry entry(CardApplication application) {
        return entries.computeIfAbsent(application.aid(), aid -> new RegistryEntry(this, application));
    }

    /**
     * Returns the application on whose behalf {@code org.globalplatform} is called on this thread: the one whose
     * install method runs, else the applet selected on the jCardSim runtime that is active (jCardSim switches no
     * contexts, so the selected applet is the caller).
     *
     * @return the AID, or null when no applet is selected or no runtime is active
     */
    AID caller() {
        if (installing != null) {
            return installing;
        }
        SimulatorRuntime runtime;
        try {
            runtime = SimulatorSystem.instance();
        } catch (AssertionError e) {
            return null;
        }
        return aidOf(runtime.getAID());
    }

    /**
     * Converts an AID of the Java Card API to the AID type of this toolkit, for a runtime that reports to its card.
     *
     * @param aid a Java Card AID, or null
     * @return the same AID, or null for null
     */
    public static AID aidOf(javacard.framework.AID aid) {
        if (aid == null) {
            return null;
        }
        byte[] bytes = new byte[16];
        byte length = aid.getBytes(bytes, (short) 0);
        return AID.fromHex(Hex.encode(Arrays.copyOf(bytes, length)));
    }
}
