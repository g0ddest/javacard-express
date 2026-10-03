package name.velikodniy.jcexpress.livecard.guard;

/**
 * Thrown by the {@link ApduGuard} for a command that is not on its allow-list. The command was not sent to
 * the card.
 */
public final class GuardViolationException extends SecurityException {

    private static final long serialVersionUID = 1L;

    /** The blocked command as hex. */
    private final String command;
    /** Why the guard blocked the command. */
    private final String reason;

    /**
     * Creates the exception.
     *
     * @param command the blocked command as hex
     * @param reason  why the guard blocked it
     */
    public GuardViolationException(String command, String reason) {
        super("APDU guard blocked " + command + " (not sent to the card): " + reason);
        this.command = command;
        this.reason = reason;
    }

    /**
     * Returns the blocked command.
     *
     * @return the command APDU as hex
     */
    public String command() {
        return command;
    }

    /**
     * Returns why the command was blocked.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }
}
