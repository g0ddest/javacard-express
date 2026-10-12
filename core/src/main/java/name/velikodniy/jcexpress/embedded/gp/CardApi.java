package name.velikodniy.jcexpress.embedded.gp;

import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.base.SimulatorSystem;
import javacard.framework.Shareable;
import name.velikodniy.jcexpress.AID;
import org.globalplatform.CVM;
import org.globalplatform.GPRegistryEntry;
import org.globalplatform.GPSystem;
import org.globalplatform.GlobalService;
import org.globalplatform.SecureChannel;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What {@code org.globalplatform.GPSystem} does on a simulated card: each static method of the API is answered by
 * the {@link GlobalPlatformCard} of the applet that calls it, on behalf of that applet (GlobalPlatform Card API
 * 1.8, class GPSystem; GlobalPlatform Card Specification v2.3.1 Annex A.1).
 *
 * <p>The card is the one of the jCardSim runtime that is active on the calling thread, which must be a
 * {@link GlobalPlatformHost}; the calling application is the one whose install method runs, else the applet
 * selected on that runtime. A call from anywhere else (no card, or a jCardSim runtime of another origin) throws
 * {@link IllegalStateException}. Applets do not call this class: they call {@code GPSystem}.</p>
 */
public final class CardApi {

    private CardApi() {
    }

    /**
     * Answers {@code GPSystem.getCardContentState()}.
     *
     * @return the life cycle state of the calling application
     * @throws IllegalStateException if the caller has no entry in the GlobalPlatform Registry
     */
    public static byte getCardContentState() {
        return (byte) Call.of("getCardContentState").registered().state();
    }

    /**
     * Answers {@code GPSystem.getCardState()}.
     *
     * @return the life cycle state of the card
     */
    public static byte getCardState() {
        return (byte) Call.of("getCardState").card().cardState();
    }

    /**
     * Answers {@code GPSystem.getCVM(byte)}: the Global Service of the CVM family for the identifier is asked
     * for its service object, with the registry entry of the caller, the service name, and no parameters; the
     * result is the CVM, or null when any step gives none (GlobalPlatform Card API 1.8, GPSystem.getCVM).
     *
     * @param identifier the CVM identifier
     * @return the CVM, or null
     */
    public static CVM getCVM(byte identifier) {
        Call call = Call.of("getCVM");
        short name = (short) ((GPSystem.FAMILY_CVM << 8) | (identifier & 0xFF));
        GlobalService service = service(call, null, name);
        if (service == null) {
            return null;
        }
        GPRegistryEntry client = call.entry().map(call.card()::entry).orElse(null);
        Shareable provided = service.getServiceInterface(client, name, null, (short) 0, (short) 0);
        return provided instanceof CVM cvm ? cvm : null;
    }

    /**
     * Answers {@code GPSystem.getSecureChannel()}.
     *
     * @return the secure channel of the card's Security Domain
     */
    public static SecureChannel getSecureChannel() {
        return Call.of("getSecureChannel").card().secureChannel();
    }

    /**
     * Answers {@code GPSystem.lockCard()}: with the Card Lock privilege a SECURED card becomes CARD_LOCKED (GPCS
     * v2.3.1 5.1.1.4: the transition starts from SECURED).
     *
     * @return true when the card was locked
     */
    public static boolean lockCard() {
        Call call = Call.of("lockCard");
        boolean allowed = call.privileged(GPRegistryEntry.PRIVILEGE_CARD_LOCK)
                && call.card().cardState() == GlobalPlatformCard.SECURED;
        if (allowed) {
            call.card().cardState(GlobalPlatformCard.CARD_LOCKED);
        }
        return allowed;
    }

    /**
     * Answers {@code GPSystem.terminateCard()}: with the Card Terminate privilege the card becomes TERMINATED
     * from any state (GPCS v2.3.1 5.1.1.5).
     *
     * @return true when the card was terminated
     */
    public static boolean terminateCard() {
        Call call = Call.of("terminateCard");
        boolean allowed = call.privileged(GPRegistryEntry.PRIVILEGE_CARD_TERMINATE);
        if (allowed) {
            call.card().cardState(GlobalPlatformCard.TERMINATED);
        }
        return allowed;
    }

    /**
     * Answers {@code GPSystem.setATRHistBytes}: with the Card Reset privilege and 1 to 15 bytes, the bytes
     * become the historical bytes at the next card reset.
     *
     * @param buffer the buffer with the bytes
     * @param offset where they start
     * @param length their number
     * @return true when the bytes were accepted
     */
    public static boolean setATRHistBytes(byte[] buffer, short offset, byte length) {
        Call call = Call.of("setATRHistBytes");
        if (!call.privileged(GPRegistryEntry.PRIVILEGE_CARD_RESET) || length <= 0 || length > 15) {
            return false;
        }
        if (offset < 0 || offset + length > buffer.length) {
            throw new ArrayIndexOutOfBoundsException("The historical bytes are not inside the buffer");
        }
        call.card().historicalBytesAfterReset(Arrays.copyOfRange(buffer, offset, offset + length));
        return true;
    }

    /**
     * Answers {@code GPSystem.setCardContentState(byte)} with the transitions an application may make itself
     * ({@link LifeCycle#ofItself}).
     *
     * @param state the requested state
     * @return true when the state changed
     * @throws IllegalStateException if the caller has no entry in the GlobalPlatform Registry
     */
    public static boolean setCardContentState(byte state) {
        CardApplication application = Call.of("setCardContentState").registered();
        OptionalInt next = LifeCycle.ofItself(application.state(), state & 0xFF);
        next.ifPresent(application::state);
        return next.isPresent();
    }

    /**
     * Answers {@code GPSystem.getRegistryEntry}: the caller's own entry for null or its own AID; the entry of
     * another application only with the Global Registry privilege.
     *
     * @param aid the application, or null for the caller
     * @return the entry, or null
     */
    public static GPRegistryEntry getRegistryEntry(javacard.framework.AID aid) {
        Call call = Call.of("getRegistryEntry");
        AID requested = GlobalPlatformCard.aidOf(aid);
        boolean own = requested == null || requested.equals(call.application());
        if (!own && !call.privileged(GPRegistryEntry.PRIVILEGE_GLOBAL_REGISTRY)) {
            return null;
        }
        Optional<CardApplication> application = own ? call.entry() : call.card().application(requested);
        return application.map(call.card()::entry).orElse(null);
    }

    /**
     * Answers {@code GPSystem.getService}.
     *
     * @param serverAid   the Global Services application, or null to search the registered names
     * @param serviceName the service name
     * @return the Global Service of the application that provides the service, or null
     */
    public static GlobalService getService(javacard.framework.AID serverAid, short serviceName) {
        return service(Call.of("getService"), serverAid, serviceName);
    }

    /**
     * Finds the Global Services application (the one that registered the name when no AID is given; a named
     * application needs the Global Service privilege) and asks it for its {@code GlobalService} with
     * {@code getShareableInterfaceObject(caller, GLOBAL_SERVICE_IDENTIFIER)} (GlobalPlatform Card API 1.8,
     * GPSystem.getService). The applet must run on the jCardSim runtime of the caller.
     */
    private static GlobalService service(Call call, javacard.framework.AID serverAid, short serviceName) {
        GlobalPlatformCard card = call.card();
        Optional<AID> provider = serverAid == null ? card.services().provider(serviceName)
                : card.application(GlobalPlatformCard.aidOf(serverAid))
                        .filter(server -> server.hasPrivilege(GPRegistryEntry.PRIVILEGE_GLOBAL_SERVICE))
                        .map(CardApplication::aid);
        if (provider.isEmpty()) {
            return null;
        }
        byte[] aid = provider.get().toBytes();
        Shareable shared = call.runtime().getSharedObject(new javacard.framework.AID(aid, (short) 0,
                (byte) aid.length), GPSystem.GLOBAL_SERVICE_IDENTIFIER);
        return shared instanceof GlobalService service ? service : null;
    }

    /**
     * Answers {@code GPSystem.getCardInfo}.
     *
     * @param buffer the buffer to write to
     * @param offset where to write
     * @param info   the requested information
     * @return {@code offset} plus the number of bytes written
     */
    public static short getCardInfo(byte[] buffer, short offset, short info) {
        byte[] value = CardInfo.of(Call.of("getCardInfo").card(), info & 0xFFFF);
        if (offset < 0 || offset + value.length > buffer.length) {
            throw new ArrayIndexOutOfBoundsException("The card information does not fit at offset " + offset);
        }
        System.arraycopy(value, 0, buffer, offset, value.length);
        return (short) (offset + value.length);
    }

    /**
     * Answers {@code GPSystem.getCardInfoLength}.
     *
     * @param info the requested information
     * @return its length in bytes
     */
    public static short getCardInfoLength(short info) {
        return (short) CardInfo.of(Call.of("getCardInfoLength").card(), info & 0xFFFF).length;
    }

    /**
     * One call of the API: the card and the jCardSim runtime it reached, and the calling application.
     *
     * @param method      the name of the GPSystem method, for messages
     * @param card        the GlobalPlatform environment of the card
     * @param runtime     the active jCardSim runtime
     * @param application the AID of the calling application, or null when none is selected
     */
    private record Call(String method, GlobalPlatformCard card, SimulatorRuntime runtime, AID application) {

        static Call of(String method) {
            SimulatorRuntime runtime;
            try {
                runtime = SimulatorSystem.instance();
            } catch (AssertionError e) {
                throw new IllegalStateException("GPSystem." + method + " was called outside a card: the classes of"
                        + " org.globalplatform work inside applets that run on the simulated cards of this toolkit"
                        + " (EmbeddedSession, the simulated GlobalPlatform card)", e);
            }
            if (!(runtime instanceof GlobalPlatformHost host)) {
                throw new IllegalStateException("GPSystem." + method + " was called on a jCardSim runtime without a"
                        + " GlobalPlatform environment (" + runtime.getClass().getName() + "): the classes of"
                        + " org.globalplatform work on the simulated cards of this toolkit (EmbeddedSession, the"
                        + " simulated GlobalPlatform card)");
            }
            GlobalPlatformCard card = host.globalPlatform();
            return new Call(method, card, runtime, card.caller());
        }

        /** The registry entry of the caller, if it has one. */
        Optional<CardApplication> entry() {
            return card.application(application);
        }

        /** The registry entry of the caller, which the method cannot do without. */
        CardApplication registered() {
            return entry().orElseThrow(() -> new IllegalStateException("GPSystem." + method + " needs the entry of"
                    + " the calling application in the GlobalPlatform Registry, but "
                    + (application == null ? "no application is selected" : application.toHex() + " has none")
                    + " (the card registers an application when its install method starts)"));
        }

        boolean privileged(int privilege) {
            return entry().map(caller -> caller.hasPrivilege(privilege)).orElse(false);
        }
    }
}
