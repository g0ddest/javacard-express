package name.velikodniy.jcexpress;

/**
 * The backend a card test runs on: the {@code jcx.backend} setting of {@link JavaCardTest} classes
 * ({@code embedded}, {@code container}, {@code simulated-gp}, {@code livecard}), and the mode of a
 * {@link SmartCard} field.
 */
public enum Mode {
    /** jCardSim in-process simulation (fast, ~50ms startup). */
    EMBEDDED,
    /**
     * jCardSim in a Docker container, reached over a small TCP protocol (no PC/SC stack; slower, a few seconds
     * startup). Requires {@code javacard-express-container} and Docker.
     */
    CONTAINER,
    /**
     * The real-card code path offline: a simulated GlobalPlatform card (SCP03, card content management) behind the
     * live-card harness, with conversion, the APDU guard, LOAD/INSTALL/DELETE and verified cleanup. The CAP file is
     * converted and loaded, but the applets run from their class files on jCardSim, as on {@link #EMBEDDED}.
     * Requires {@code javacard-express-livecard}; never touches PC/SC ({@code -Djcx.backend=simulated-gp}).
     */
    SIMULATED_GP,
    /**
     * The card in a PC/SC reader, through the live-card harness and its safety rules (APDU guard, own AID prefix,
     * authentication budget, verified cleanup, refusal on CI). Requires {@code javacard-express-livecard} and is
     * selected only with the JVM system property {@code -Djcx.backend=livecard}.
     */
    LIVECARD
}
