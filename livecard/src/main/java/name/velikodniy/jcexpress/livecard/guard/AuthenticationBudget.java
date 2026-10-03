package name.velikodniy.jcexpress.livecard.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hard cap on failed secure channel authentications for a whole live-card run.
 *
 * <p>GlobalPlatform cards count failed authentications and block the key set (or the card) after a
 * card-specific limit (GlobalPlatform Card Specification v2.3.1 E.5.2.7 / Amendment D 7.2.2: EXTERNAL
 * AUTHENTICATE failures). The guard never sends EXTERNAL AUTHENTICATE unless it verified the card
 * cryptogram itself, so a wrong key normally costs no try at all. On top of that the run stops at the first
 * sign of trouble: every failure counts (INITIALIZE UPDATE rejected, card cryptogram not matching the
 * configured keys, EXTERNAL AUTHENTICATE blocked by the guard or rejected by the card), and once
 * {@code maxFailures} are reached the budget is exhausted and the guard refuses every further command.</p>
 *
 * <p>One instance is shared by all sessions of a run. Thread-safe.</p>
 */
public final class AuthenticationBudget {

    private final int maxFailures;
    private final List<String> failures = new ArrayList<>();

    /**
     * Creates a budget.
     *
     * @param maxFailures the number of failures that aborts the run (at least 1)
     * @throws IllegalArgumentException if {@code maxFailures} is less than 1
     */
    public AuthenticationBudget(int maxFailures) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be at least 1, got " + maxFailures);
        }
        this.maxFailures = maxFailures;
    }

    /**
     * Records a failed authentication attempt.
     *
     * @param reason what failed, for the abort message
     */
    public synchronized void recordFailure(String reason) {
        failures.add(reason);
    }

    /**
     * Returns whether the run must stop: {@code maxFailures} failures have been recorded.
     *
     * @return true when no further command may be sent in this run
     */
    public synchronized boolean exhausted() {
        return failures.size() >= maxFailures;
    }

    /**
     * Returns the number of failures recorded so far.
     *
     * @return the failure count
     */
    public synchronized int failures() {
        return failures.size();
    }

    /**
     * Returns the configured maximum.
     *
     * @return the number of failures that aborts the run
     */
    public int maxFailures() {
        return maxFailures;
    }

    /**
     * Returns why the run was aborted.
     *
     * @return the recorded failures when the budget is exhausted, otherwise empty
     */
    public synchronized Optional<String> abortReason() {
        if (!exhausted()) {
            return Optional.empty();
        }
        return Optional.of("live-card run aborted after " + failures.size() + " failed authentication(s) (limit "
                + maxFailures + "): " + String.join("; ", failures));
    }
}
