package name.velikodniy.jcexpress.container;

/**
 * A command failed inside the simulator server.
 *
 * <p>When the failure has a local equivalent ({@code javacard.framework.CardRuntimeException} subclasses with
 * their reason code, {@code java.lang} runtime exceptions with their message), {@link ContainerSession} throws that
 * type, exactly as embedded mode would, with a {@code SimulatorException} as its cause. Other failures, such as a
 * {@link LinkageError} while defining the shipped applet classes, are thrown as {@code SimulatorException}. The
 * message always names the remote exception type, the reason code (if any) and the remote cause chain.</p>
 */
public class SimulatorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String remoteType;

    /**
     * Creates the exception.
     *
     * @param message    description of the remote failure
     * @param remoteType fully qualified class name of the exception thrown in the simulator, or {@code null}
     *                   if the server did not report it
     */
    public SimulatorException(String message, String remoteType) {
        super(message);
        this.remoteType = remoteType;
    }

    /**
     * Returns the type of the exception thrown in the simulator.
     *
     * @return its fully qualified class name, or {@code null} if unknown (older servers report text only)
     */
    public String remoteType() {
        return remoteType;
    }
}
