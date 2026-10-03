package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.guard.AuthenticationBudget;
import name.velikodniy.jcexpress.livecard.guard.GuardSelfCheck;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * State shared by all connections of one live-card run (one JVM): the authentication budget, the guard
 * self-check that runs before the first command, the removal of leftovers of earlier runs, which happens
 * once before the first card content change, and the transcript files written in this run.
 */
public final class LiveCardRun {

    private static final LiveCardRun CURRENT = new LiveCardRun();

    private AuthenticationBudget budget;
    private boolean selfChecked;
    private boolean leftoversRemoved;
    private boolean unverifiedWarned;
    private final Set<Path> transcripts = new HashSet<>();
    private final Runnable unverifiedWarning;

    /**
     * Creates a separate run (tests of this module use one per scenario).
     */
    public LiveCardRun() {
        this(null);
    }

    /**
     * Creates a separate run that says itself that CAP files are loaded without off-card verification, in place of
     * the live card's warning: the backend {@code simulated-gp} opens a run per test class and gives one notice per
     * JVM, worded for the simulated card.
     *
     * @param unverifiedWarning called once, before the first CAP file of the run is loaded without off-card
     *                          verification; null for the live card's warning
     */
    public LiveCardRun(Runnable unverifiedWarning) {
        this.unverifiedWarning = unverifiedWarning;
    }

    /**
     * Returns the run of this JVM.
     *
     * @return the shared run
     */
    public static LiveCardRun current() {
        return CURRENT;
    }

    /**
     * Returns the run's authentication budget, created with the first connection's limit.
     *
     * @param maxFailures the configured limit
     * @return the budget shared by every connection of the run
     */
    public synchronized AuthenticationBudget budget(int maxFailures) {
        if (budget == null) {
            budget = new AuthenticationBudget(maxFailures);
        }
        return budget;
    }

    /**
     * Returns why the run was aborted.
     *
     * @return the reason, or empty while the run may continue
     */
    public synchronized Optional<String> abortReason() {
        return budget == null ? Optional.empty() : budget.abortReason();
    }

    /**
     * Throws if the run was aborted.
     *
     * @throws LiveCardException with the abort reason
     */
    public void requireNotAborted() {
        Optional<String> reason = abortReason();
        if (reason.isPresent()) {
            throw new LiveCardException(reason.get());
        }
    }

    /**
     * Runs {@link GuardSelfCheck#verify()} once per run.
     *
     * @throws IllegalStateException if the guard fails its known-answer checks
     */
    public synchronized void selfCheckGuard() {
        if (!selfChecked) {
            GuardSelfCheck.verify();
            selfChecked = true;
        }
    }

    /**
     * Returns whether the leftovers of earlier runs have been removed in this run.
     *
     * @return true once {@link #markLeftoversRemoved()} was called
     */
    synchronized boolean leftoversRemoved() {
        return leftoversRemoved;
    }

    /**
     * Records that the leftovers of earlier runs have been removed.
     */
    synchronized void markLeftoversRemoved() {
        leftoversRemoved = true;
    }

    /**
     * Records that a transcript file is written in this run.
     *
     * @param file the transcript file
     * @return true for its first use in this run (the file then starts anew), false afterwards (it continues)
     */
    synchronized boolean firstUseOf(Path file) {
        return transcripts.add(file.toAbsolutePath().normalize());
    }

    /**
     * Gives the one warning per run that CAP files are loaded without off-card verification: the run's own
     * ({@link #LiveCardRun(Runnable)}) or the live card's.
     *
     * @param liveCardWarning the live card's warning, for a run without its own
     */
    synchronized void warnUnverified(Runnable liveCardWarning) {
        if (!unverifiedWarned) {
            unverifiedWarned = true;
            (unverifiedWarning != null ? unverifiedWarning : liveCardWarning).run();
        }
    }
}
