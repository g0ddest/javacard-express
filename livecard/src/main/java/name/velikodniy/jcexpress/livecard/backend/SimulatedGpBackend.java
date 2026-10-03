package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.backend.CardBackend;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import name.velikodniy.jcexpress.livecard.CardConnector;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.LiveCardException;
import name.velikodniy.jcexpress.livecard.LiveCardRun;
import name.velikodniy.jcexpress.livecard.sim.SimulatedApplets;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCard;
import name.velikodniy.jcexpress.livecard.sim.SimulatedTerminal;
import name.velikodniy.jcexpress.pcsc.PcscSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The backend {@code simulated-gp}: the real-card code path without a reader. Every test class run gets a new
 * simulated GlobalPlatform card (SCP03 with the GlobalPlatform test keys, card content management, applets on
 * jCardSim) behind the live-card harness: conversion, the APDU guard, SCP03, LOAD/INSTALL/DELETE and verified
 * cleanup run as on a card. It never touches PC/SC, so CI can run it.
 *
 * <p>Settings (system properties or JUnit configuration parameters): {@value AidScheme#PREFIX_SETTING} (AID prefix,
 * default {@link CardRequest#aidScheme()}), {@value #REGISTERED_RID_SETTING} (the registered RID a prefix outside the
 * proprietary category starts with, named explicitly), {@code jcx.livecard.javaCardVersion} (conversion target;
 * default the build's, from its {@link name.velikodniy.jcexpress.backend.BuildDescriptor}, else 3.0.4),
 * {@value #VERIFIER_SETTING} (an Oracle kit whose off-card verifier checks every CAP file; default none, the card is
 * simulated: the CAP files are converted and loaded unverified, which one INFO message per JVM says),
 * {@code jcx.livecard.transcriptDir}, {@value #INT_SETTING} ({@code true} or {@code false}; default the build's, else
 * no int support).</p>
 *
 * <p>Limits of the simulated card: applets run on the basic channel only; on logical channels 1-3 only the Issuer
 * Security Domain can be selected, and a SELECT of an applet there is answered '6881' with a note in the transcript
 * ({@link SimulatedCard}).</p>
 */
public final class SimulatedGpBackend implements CardBackend {

    /** Setting that converts packages with int support. */
    public static final String INT_SETTING = "jcx.supportInt32";
    /** Setting that names the registered RID of an AID prefix outside the proprietary category. */
    public static final String REGISTERED_RID_SETTING = "jcx.livecard.registeredRid";
    /** Setting that names an Oracle Java Card development kit whose off-card verifier checks every CAP file. */
    public static final String VERIFIER_SETTING = "jcx.livecard.verifierSdk";

    private static final Logger LOG = Logger.getLogger(SimulatedGpBackend.class.getName());
    private static final byte[] TEST_KEY = HexFormat.of().parseHex("404142434445464748494A4B4C4D4E4F");
    /** The one notice per JVM that packages are loaded without off-card verification. */
    static final AtomicBoolean UNVERIFIED_NOTICE_GIVEN = new AtomicBoolean();
    static final String UNVERIFIED_NOTICE = "simulated-gp: packages are converted and loaded onto the simulated card"
            + " without an off-card verifier check, and their classes run on jCardSim; set " + VERIFIER_SETTING
            + " to an Oracle Java Card development kit to verify every CAP file before it is loaded";

    /** Creates the backend ({@link java.util.ServiceLoader} instantiates it). */
    public SimulatedGpBackend() {
        // stateless
    }

    @Override
    public Mode mode() {
        return Mode.SIMULATED_GP;
    }

    @Override
    public Set<Class<?>> parameterTypes() {
        return Set.of(LiveCard.class);
    }

    @Override
    public TestCard open(CardRequest request) {
        Registry registry = new Registry();
        SimulatedCard card = new SimulatedCard(TEST_KEY, registry);
        LiveCard live = LiveCard.connect(config(request), new Connector(card),
                new LiveCardRun(SimulatedGpBackend::noteUnverifiedLoads));
        GpTestCard.transcribe(live, request);
        card.onLimit(live::note);
        Optional<Boolean> intSupport = request.setting(INT_SETTING).map(Boolean::parseBoolean);
        return new GpTestCard(Mode.SIMULATED_GP, live, intSupport, registry::register);
    }

    /**
     * Says once per JVM, before the first CAP file is loaded unverified, that simulated-gp loads CAP files without an
     * off-card verifier unless {@value #VERIFIER_SETTING} names a kit: one INFO record of java.util.logging.
     */
    private static void noteUnverifiedLoads() {
        if (UNVERIFIED_NOTICE_GIVEN.compareAndSet(false, true)) {
            LOG.logp(Level.INFO, SimulatedGpBackend.class.getName(), "open", UNVERIFIED_NOTICE);
        }
    }

    /** The harness settings of the run; an invalid one is named by the setting the run takes it from. */
    private static LiveCardConfig config(CardRequest request) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("aidPrefix", request.aidScheme().prefix());
        request.setting(REGISTERED_RID_SETTING).ifPresent(rid -> settings.put("registeredRid", rid));
        settings.put("verifierSdk", request.setting(VERIFIER_SETTING).orElse("none"));
        request.setting("jcx.livecard.javaCardVersion").or(() -> GpTestCard.buildVersion(request))
                .ifPresent(version -> settings.put("javaCardVersion", version));
        request.setting("jcx.livecard.transcriptDir").ifPresent(dir -> settings.put("transcriptDir", dir));
        try {
            return LiveCardConfig.of(settings);
        } catch (LiveCardException e) {
            throw new LiveCardException("The simulated-gp backend cannot use its settings: "
                    + e.getMessage().replace(" from argument:", ":")
                    + " (simulated-gp takes the AID prefix from " + AidScheme.PREFIX_SETTING + " or the project, the"
                    + " registered RID from " + REGISTERED_RID_SETTING + ", the verifier kit from " + VERIFIER_SETTING
                    + ", javaCardVersion and transcriptDir from jcx.livecard.javaCardVersion and"
                    + " jcx.livecard.transcriptDir)", e);
        }
    }

    /** Which class implements each module, and where the applet classes are. */
    private static final class Registry implements SimulatedApplets {
        private final Map<String, String> classNames = new HashMap<>();
        private final List<Path> classPath = new ArrayList<>();

        synchronized void register(PackageLoad load) {
            load.applets().forEach((className, moduleAid) -> classNames.put(moduleAid.toHex(), className));
            for (Path entry : load.classPath()) {
                if (!classPath.contains(entry)) {
                    classPath.add(entry);
                }
            }
        }

        @Override
        public synchronized Optional<String> className(String moduleAid) {
            return Optional.ofNullable(classNames.get(moduleAid.toUpperCase(Locale.ROOT)));
        }

        @Override
        public synchronized List<Path> classPath() {
            return List.copyOf(classPath);
        }
    }

    /** Connects the harness to this run's simulated card through the project's PcscSession. */
    private record Connector(SimulatedCard card) implements CardConnector {
        @Override
        public Presence probe(LiveCardConfig config) {
            return new Presence(true, "simulated card");
        }

        @Override
        public Connection connect(LiveCardConfig config) {
            PcscSession session = PcscSession.open(new SimulatedTerminal(card), PcscSession.Options.defaults());
            return new Connection(session, SimulatedTerminal.READER, SimulatedCard.ATR,
                    session.getProtocol());
        }
    }
}
