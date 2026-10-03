package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.AppletInfo;
import name.velikodniy.jcexpress.gp.CAPFile;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.gp.Lifecycle;
import name.velikodniy.jcexpress.livecard.guard.ApduGuard;
import name.velikodniy.jcexpress.livecard.guard.ContentChange;
import name.velikodniy.jcexpress.livecard.guard.GuardListener;
import name.velikodniy.jcexpress.livecard.guard.GuardPolicy;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import name.velikodniy.jcexpress.scp.GP;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A real card for tests: one guarded, exclusive connection with an APDU transcript, GlobalPlatform sessions,
 * deployment of applets converted by this project's converter, and cleanup of everything the tests created.
 *
 * <pre>{@code
 * try (LiveCard card = LiveCard.connect(LiveCardConfig.load())) {
 *     Deployment hello = card.deploy(AppletPackage.of(classes, "com.example.hello", card.aid("0101"))
 *             .withApplet("HelloApplet", card.aid("010101")));
 *     card.session().select(card.aid("010101"));
 *     APDUResponse r = card.session().send(0x80, 0x01, 0x00, 0x00, null, 256);
 *     card.cleanup();
 * }
 * }</pre>
 *
 * <ul>
 *   <li>Every command passes the {@link ApduGuard} (see there for the policy) and is recorded in the
 *       {@link Transcript}.</li>
 *   <li>Card content changes happen only inside {@link #manage(GpAction)} (deploy, install, lock, unlock,
 *       delete): a GlobalPlatform session at security level '01' (C-MAC) with the guard's write access open.
 *       Before the first change of a run, leftovers of earlier runs under the AID prefix are deleted.</li>
 *   <li>{@link #cleanup()} deletes every load file and instance created through this card and verifies with
 *       GET STATUS that none is left.</li>
 *   <li>Notes name what the harness does (load, delete, the secure channel, leftovers, cleanup) in the transcript
 *       and in the session's history, where one note stands for the LOAD blocks of a load file
 *       ({@link #note(String)}).</li>
 * </ul>
 */
public final class LiveCard implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LiveCard.class.getName());

    private final LiveCardConfig config;
    private final LiveCardRun run;
    private final CardConnector.Connection connection;
    private final Transcript transcript = new Transcript();
    private final ContentTracker tracker = new ContentTracker();
    private final ContentRemoval removal = new ContentRemoval(tracker, this::note);
    private final GuardedPcscSession session;
    private final CapVerifier verifier;

    /**
     * An action inside a GlobalPlatform session with write access.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface GpAction<T> {
        /**
         * Runs the action.
         *
         * @param gp the open GlobalPlatform session (security level '01')
         * @return the result
         */
        T apply(GPSession gp);
    }

    private LiveCard(LiveCardConfig config, LiveCardRun run, CardConnector.Connection connection) {
        this.config = config;
        this.run = run;
        this.connection = connection;
        GuardPolicy policy = config.guardPolicy();
        GuardListener listener = new GuardListener() {
            @Override
            public void note(String message) {
                transcript.note(message);
            }

            @Override
            public void contentChanged(ContentChange change) {
                tracker.apply(change);
            }
        };
        ApduGuard guard = new ApduGuard(policy, run.budget(config.maxAuthFailures()), listener);
        this.session = new GuardedPcscSession(connection.transport(), guard, transcript, Thread.currentThread());
        this.verifier = config.verifierSdk() == null ? null : new CapVerifier(config.verifierSdk());
    }

    /**
     * Connects to the card in the configured PC/SC reader (requires {@code enabled=true}).
     *
     * @param config the settings
     * @return the connected card
     * @throws LiveCardException if the run was aborted or no card can be connected
     */
    public static LiveCard connect(LiveCardConfig config) {
        return connect(config, new PcscConnector(), LiveCardRun.current());
    }

    /**
     * Connects to a card through a connector, within a run. The guard's self-check runs before the first
     * connection of the run.
     *
     * @param config    the settings
     * @param connector finds and connects the card
     * @param run       the run (budget, one-time steps)
     * @return the connected card
     * @throws LiveCardException     if the run was aborted or no card can be connected
     * @throws IllegalStateException if the guard fails its self-check
     */
    public static LiveCard connect(LiveCardConfig config, CardConnector connector, LiveCardRun run) {
        run.requireNotAborted();
        run.selfCheckGuard();
        return new LiveCard(config, run, connector.connect(config));
    }

    /**
     * Returns the settings.
     *
     * @return the settings
     */
    public LiveCardConfig config() {
        return config;
    }

    /**
     * Returns the reader name.
     *
     * @return the PC/SC reader
     */
    public String reader() {
        return connection.reader();
    }

    /**
     * Returns the card's Answer To Reset.
     *
     * @return the ATR, hex
     */
    public String atr() {
        return connection.atr();
    }

    /**
     * Returns the transmission protocol.
     *
     * @return e.g. {@code T=1}
     */
    public String protocol() {
        return connection.protocol();
    }

    /**
     * Returns the guarded session for commands to the tests' applets (and read-only commands elsewhere).
     *
     * @return the session
     */
    public GuardedPcscSession session() {
        return session;
    }

    /**
     * Returns the transcript, e.g. for notes of a test.
     *
     * @return the transcript
     */
    public Transcript transcript() {
        return transcript;
    }

    /**
     * Writes a note into the transcript and into the session's history ({@link GuardedPcscSession#note(String)}),
     * which a failed {@code @JavaCardTest} test shows and {@code -Djcx.log=true} prints.
     *
     * @param message the note, one line
     */
    public void note(String message) {
        session.note(message);
    }

    /**
     * Continues the transcript in a file below the configured transcript directory and writes a header (reader,
     * ATR, settings without keys). The first switch to a file in a run starts it anew; later switches in the same
     * run continue it, so a file never mixes runs.
     *
     * @param relativeFile the file, relative to {@link LiveCardConfig#transcriptDir()}
     * @param title        the title line
     */
    public void transcriptTo(Path relativeFile, String title) {
        Path file = config.transcriptDir().resolve(relativeFile);
        transcript.switchTo(file, title, !run.firstUseOf(file));
        transcript.note("reader: " + reader() + "  protocol: " + protocol() + "  ATR: " + atr());
        transcript.note("settings: " + config.describe());
        transcript.note(HarnessTexts.exchangesNote(reader()));
    }

    /**
     * Returns an AID under the configured prefix.
     *
     * @param suffixHex the bytes after the prefix
     * @return the AID
     */
    public AID aid(String suffixHex) {
        return config.aid(suffixHex);
    }

    /**
     * Opens a GlobalPlatform secure channel at the configured security level.
     *
     * @return the open session; close it when done (no command is sent on close)
     */
    public GPSession gp() {
        return gp(config.securityLevel());
    }

    /**
     * Opens a GlobalPlatform secure channel (SELECT ISD, INITIALIZE UPDATE, EXTERNAL AUTHENTICATE).
     *
     * @param securityLevel the security level (Amendment D Table 7-6)
     * @return the open session
     * @throws LiveCardException if the run was aborted
     */
    public GPSession gp(int securityLevel) {
        run.requireNotAborted();
        note(String.format("secure channel to the Issuer Security Domain %s at security level %02X", config.isd(),
                securityLevel));
        return GPSession.on(session).securityDomain(config.isd()).keys(config.keys().toScpKeys())
                .keyVersion(config.keyVersion()).securityLevel(securityLevel).open();
    }

    /**
     * Runs a card content change in a GlobalPlatform session at security level '01' with the guard's write
     * access open. Leftovers of earlier runs under the AID prefix are deleted first, once per run.
     *
     * @param action the change
     * @param <T>    the result type
     * @return the action's result
     */
    public <T> T manage(GpAction<T> action) {
        if (!run.leftoversRemoved()) {
            note("look for leftovers of earlier runs under " + config.aidPrefix() + " (GET STATUS)");
            removeLeftovers();
            run.markLeftoversRemoved();
        }
        return withWriteAccess(action);
    }

    /**
     * Converts a package for the configured Java Card version, runs the off-card verifier if configured, loads
     * the CAP file and installs instances (privileges '00'). Nothing is sent when conversion or verification
     * fails. A package that imports another one is deployed after it, with the other's
     * {@link Deployment#exportPath()} on its export path; {@link #cleanup()} deletes in the reverse order.
     *
     * @param pkg       the package
     * @param instances the instances to create; none = one per applet under its module AID
     * @return what was deployed
     * @throws LiveCardException if an AID is outside the prefix, no verifier is configured and
     *                           {@code verifierSdk=none} is not set, or conversion or verification fails
     */
    public Deployment deploy(AppletPackage pkg, AppletInstance... instances) {
        List<AppletInstance> plan = instances.length > 0 ? List.of(instances) : pkg.defaultInstances();
        requireOwned(pkg, plan);
        if (verifier == null && !config.allowUnverifiedCaps()) {
            throw new LiveCardException(HarnessTexts.NO_VERIFIER);
        }
        PreparedCap prepared = PreparedCap.prepare(pkg, config, verifier);
        if (verifier == null) {
            run.warnUnverified(() -> LOG.warning(HarnessTexts.UNVERIFIED_WARNING));
        }
        note("load " + pkg.packageName() + " as " + pkg.aid() + " (CAP file of " + prepared.cap().length
                + " bytes, verification: " + prepared.verification() + ")");
        CAPFile cap = CAPFile.from(prepared.cap());
        manage(gp -> {
            AppletInstance first = plan.getFirst();
            gp.loadAndInstall(cap, first.moduleAid(), first.instanceAid(), 0x00, first.parameters());
            for (AppletInstance next : plan.subList(1, plan.size())) {
                gp.installForInstall(pkg.aid(), next.moduleAid(), next.instanceAid(), 0x00, next.parameters());
            }
            return null;
        });
        return new Deployment(pkg.aid(), plan, prepared.cap().length, prepared.warnings(), prepared.capFile(),
                prepared.verification(), prepared.exportPath());
    }

    /**
     * Creates another instance of a deployed applet (INSTALL [for install and make selectable]).
     *
     * @param deployment the deployment of the package
     * @param instance   the instance
     * @return the card's response
     */
    public APDUResponse install(Deployment deployment, AppletInstance instance) {
        requireOwned(instance.instanceAid());
        return manage(gp -> gp.installForInstall(deployment.packageAid(), instance.moduleAid(),
                instance.instanceAid(), 0x00, instance.parameters()));
    }

    /**
     * Locks an application (SET STATUS '40' '80').
     *
     * @param aid the application AID
     * @return the card's response
     */
    public APDUResponse lock(AID aid) {
        return manage(gp -> gp.lockApp(aid.toBytes()));
    }

    /**
     * Unlocks an application (SET STATUS '40' '00').
     *
     * @param aid the application AID
     * @return the card's response
     */
    public APDUResponse unlock(AID aid) {
        return manage(gp -> gp.unlockApp(aid.toBytes()));
    }

    /**
     * Deletes an application or load file (DELETE [card content]).
     *
     * @param aid     the AID
     * @param related true to delete a load file together with its applications (P2 '80')
     * @return the card's response
     */
    public APDUResponse delete(AID aid, boolean related) {
        note("delete " + aid.toHex() + (related ? " with its applications" : ""));
        return manage(gp -> gp.deleteAid(aid.toBytes(), related));
    }

    /**
     * Reads the card content with GET STATUS (applications '40' and load files '20').
     *
     * @return the content
     */
    public CardContent content() {
        GPSession gp = gp(GP.SECURITY_C_MAC);
        try {
            return new CardContent(gp.getStatus(Lifecycle.SCOPE_APPS), gp.getLoadFiles());
        } finally {
            gp.close();
        }
    }

    /**
     * Reads the load files with their Executable Modules (GET STATUS '10').
     *
     * @return the entries
     */
    public List<AppletInfo> loadFilesAndModules() {
        GPSession gp = gp(GP.SECURITY_C_MAC);
        try {
            return gp.getStatus(Lifecycle.SCOPE_LOAD_FILES_AND_MODULES);
        } finally {
            gp.close();
        }
    }

    /**
     * Resets the card (warm reset and new connection).
     */
    public void reset() {
        session.reset();
    }

    /**
     * Deletes every load file (with its applications) and every instance created through this card, then
     * checks with GET STATUS that none of them is left.
     *
     * @throws LiveCardException if something could not be deleted or is still on the card
     */
    public void cleanup() {
        if (tracker.isEmpty()) {
            note("cleanup: nothing created through this card is left");
            return;
        }
        run.abortReason().ifPresent(reason -> {
            throw new LiveCardException("Cannot delete " + tracker.created() + ": " + reason + "; the next run"
                    + " deletes leftovers under " + config.aidPrefix() + " before its first deployment");
        });
        List<String> problems = new ArrayList<>();
        withWriteAccess(gp -> {
            removal.deleteLoadFiles(gp, tracker.loadFilesNewestFirst(), true, problems);
            tracker.orphanInstances().forEach(aid -> removal.deleteQuietly(gp, aid, false, problems));
            return null;
        });
        List<String> left = content().aidsUnder(config.aidPrefix()).stream()
                .filter(tracker.created()::contains).toList();
        if (!left.isEmpty() || !problems.isEmpty()) {
            throw new LiveCardException("Cleanup incomplete: still on the card " + left + ", problems " + problems);
        }
        note("cleanup verified with GET STATUS: none of " + tracker.created() + " is on the card");
    }

    /**
     * Resets the card and disconnects. The transcript is closed.
     */
    @Override
    public void close() {
        try {
            session.reset();
        } catch (RuntimeException e) {
            transcript.note("reset before disconnecting failed: " + e.getMessage());
        } finally {
            session.close();
            transcript.note("disconnected; the guard blocked " + session.guard().blockedCount() + " command(s)");
            transcript.close();
        }
    }

    private <T> T withWriteAccess(GpAction<T> action) {
        GPSession gp = gp(GP.SECURITY_C_MAC);
        session.guard().writeAccess(true);
        try {
            return action.apply(gp);
        } finally {
            session.guard().writeAccess(false);
            gp.close();
        }
    }

    /**
     * Deletes what earlier runs left under the prefix ({@link ContentRemoval}), listing it in the transcript
     * first, and checks with GET STATUS that nothing is left under it.
     */
    private void removeLeftovers() {
        ContentRemoval.Leftovers leftovers = ContentRemoval.leftovers(content(), config.aidPrefix());
        if (leftovers.isEmpty()) {
            return;
        }
        note("removing leftovers of earlier runs under " + config.aidPrefix() + ": " + leftovers);
        List<String> problems = new ArrayList<>();
        withWriteAccess(gp -> {
            removal.removeLeftovers(gp, leftovers, problems);
            return null;
        });
        List<String> still = content().aidsUnder(config.aidPrefix());
        if (!still.isEmpty()) {
            throw new LiveCardException("Leftovers under " + config.aidPrefix() + " could not be deleted: " + still
                    + " " + problems + " (a load file with an application outside the prefix is not deleted;"
                    + " delete such content by hand)");
        }
    }

    private void requireOwned(AppletPackage pkg, List<AppletInstance> plan) {
        if (plan.isEmpty()) {
            throw new LiveCardException("Package " + pkg.packageName() + " has no applet to install");
        }
        requireOwned(pkg.aid());
        pkg.modules().forEach(module -> requireOwned(module.aid()));
        plan.forEach(instance -> requireOwned(instance.instanceAid()));
    }

    private void requireOwned(String aid) {
        if (!aid.startsWith(config.aidPrefix())) {
            throw new LiveCardException("AID " + aid + " is outside the configured prefix " + config.aidPrefix()
                    + "; tests may only create AIDs under it (use LiveCard.aid(suffix))");
        }
    }

    /**
     * Returns what the PC/SC session reports about the connection now (no command is sent), for tests of
     * {@link PcscSession} itself.
     *
     * @return the options, protocol and ATR, or empty if the card is not connected through a {@link PcscSession}
     */
    public Optional<PcscView> pcsc() {
        return connection.transport() instanceof PcscSession pcsc
                ? Optional.of(new PcscView(pcsc.options(), pcsc.getProtocol(), Hex.encode(pcsc.getATR())))
                : Optional.empty();
    }
}
