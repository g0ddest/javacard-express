package name.velikodniy.jcexpress.livecard.guard;

/**
 * What the guard knows about one logical channel. A new state means "unknown application selected": only the
 * strict allow-list for an unknown application applies until the channel's next standard SELECT. A state that
 * became unknown through a command or event keeps why ({@link #unknownSince()}), for the reason of later blocks.
 */
final class ChannelState {

    /** Which application the guard knows to be selected on the channel. */
    enum Context {
        /** Not known: a fresh or reset channel, a failed or unusual SELECT, another application. */
        UNKNOWN,
        /** The Issuer Security Domain, selected explicitly by its exact AID. */
        ISSUER_SECURITY_DOMAIN,
        /** One of the tests' own applets: an AID under the prefix. */
        TEST_APPLET
    }

    private Context context = Context.UNKNOWN;
    /** The SCP03 handshake in progress or the last one, null if none. */
    private Scp03Handshake handshake;
    /** Security level of the established secure channel session, -1 if none. */
    private int sessionLevel = -1;
    /** Whether an accepted INSTALL [for load] waits for its LOAD blocks. */
    private boolean loadPending;
    /** Why the context is unknown, null if it is known or never was. */
    private String unknownSince;

    /**
     * Creates the state of a channel whose selection became unknown.
     *
     * @param cause why: the command or event after which the guard cannot know the selected application
     * @return the state
     */
    static ChannelState unknown(String cause) {
        ChannelState state = new ChannelState();
        state.unknownSince = cause;
        return state;
    }

    /**
     * Records the result of a SELECT: the context it established. Selecting ends any secure channel session
     * (GlobalPlatform Card Specification v2.3.1 chapter 10: the session terminates when its application is
     * deselected).
     *
     * @param selected the context after the selection, {@link Context#UNKNOWN} when unsure
     */
    void selected(Context selected) {
        context = selected;
        handshake = null;
        sessionLevel = -1;
        loadPending = false;
        unknownSince = null;
    }

    /**
     * Records a selection the guard cannot follow: the context is unknown from now on.
     *
     * @param cause the command or event after which the selected application is unknown
     */
    void unknownAfter(String cause) {
        selected(Context.UNKNOWN);
        unknownSince = cause;
    }

    /**
     * Returns why the selected application is unknown.
     *
     * @return the command or event, or null while the context is known or nothing happened yet
     */
    String unknownSince() {
        return context == Context.UNKNOWN ? unknownSince : null;
    }

    Context context() {
        return context;
    }

    boolean testApplet() {
        return context == Context.TEST_APPLET;
    }

    boolean issuerSecurityDomain() {
        return context == Context.ISSUER_SECURITY_DOMAIN;
    }

    Scp03Handshake handshake() {
        return handshake;
    }

    /**
     * Starts a new handshake; a new INITIALIZE UPDATE ends the current secure channel session.
     *
     * @param started the handshake
     */
    void handshake(Scp03Handshake started) {
        handshake = started;
        sessionLevel = -1;
    }

    /**
     * Ends a pending handshake: another command reached the channel between INITIALIZE UPDATE and EXTERNAL
     * AUTHENTICATE, so the card may have abandoned the initiation (Amendment D 7.1.1, 7.1.2).
     */
    void endHandshake() {
        handshake = null;
    }

    /**
     * Ends the handshake after EXTERNAL AUTHENTICATE was answered.
     *
     * @param level the established level, or -1 when the card rejected the authentication
     */
    void authenticated(int level) {
        handshake = null;
        sessionLevel = level;
    }

    int sessionLevel() {
        return sessionLevel;
    }

    boolean loadPending() {
        return loadPending;
    }

    void loadPending(boolean pending) {
        loadPending = pending;
    }
}
