package name.velikodniy.jcexpress;

/**
 * Backend mode for smart card simulation.
 */
public enum Mode {
    /** jCardSim in-process simulation (fast, ~50ms startup). */
    EMBEDDED,
    /** Docker container with PC/SC stack (slower, ~5s startup). */
    CONTAINER
}
