package name.velikodniy.jcexpress.livecard.guard;

/**
 * Receives what the {@link ApduGuard} decides and observes: notes for the APDU transcript and changes of the
 * card content that the tests caused.
 */
public interface GuardListener {

    /** A listener that ignores everything. */
    GuardListener NONE = new GuardListener() {
        @Override
        public void note(String message) {
            // ignored
        }
    };

    /**
     * Receives a note for the transcript (decisions, verification results, blocked commands).
     *
     * @param message the note, one line
     */
    void note(String message);

    /**
     * Receives a card content change confirmed by the card (status word '9000').
     *
     * @param change the change
     */
    default void contentChanged(ContentChange change) {
        // optional
    }
}
