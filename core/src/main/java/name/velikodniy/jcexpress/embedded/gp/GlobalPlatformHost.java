package name.velikodniy.jcexpress.embedded.gp;

/**
 * Implemented by the jCardSim runtime of a simulated card that has a GlobalPlatform environment: the classes
 * of {@code org.globalplatform} find the card of the running applet through the runtime jCardSim has
 * activated ({@code SimulatorSystem.instance()}).
 *
 * <p>A runtime tells its card what the card cannot see itself: {@link GlobalPlatformCard#installStarted} before
 * and {@link GlobalPlatformCard#installSucceeded} or {@link GlobalPlatformCard#installFailed} after an
 * applet's install method, {@link GlobalPlatformCard#unregister} when an application is deleted,
 * {@link GlobalPlatformCard#applicationSessionEnded()} whenever jCardSim deselects an applet (override
 * {@code SimulatorRuntime.deselect}, which also runs before another applet or the same one is selected), and
 * {@link GlobalPlatformCard#cardReset()} when the card is reset. Several runtimes may share one card.</p>
 */
public interface GlobalPlatformHost {

    /**
     * Returns the GlobalPlatform environment of the card this runtime belongs to.
     *
     * @return the card, always the same object
     */
    GlobalPlatformCard globalPlatform();
}
