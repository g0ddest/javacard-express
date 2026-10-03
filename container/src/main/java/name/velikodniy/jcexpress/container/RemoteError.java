package name.velikodniy.jcexpress.container;

import javacard.framework.CardRuntimeException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An error reply of the simulator server, decoded.
 *
 * <p>Wire format (UTF-8, one {@code key: value} per line): {@code type} (exception class), {@code reason}
 * ({@code 0x....}, for {@code CardRuntimeException}s), {@code message}, one {@code cause} line per element of the
 * cause chain and one {@code missing-class} line per class the applet needed but the request did not contain.
 * Unknown keys are ignored. Text that does not follow the format (servers before it) becomes the message.</p>
 *
 * @param type           fully qualified exception class name, or {@code null} if not reported
 * @param reason         {@code CardRuntimeException} reason code, or {@code -1}
 * @param message        exception message, or {@code null}
 * @param causes         descriptions of the cause chain
 * @param missingClasses classes the applet code looked up in the simulator but that were not sent
 */
record RemoteError(String type, int reason, String message, List<String> causes, List<String> missingClasses) {

    /** Normalizes the lists. */
    RemoteError {
        causes = List.copyOf(causes);
        missingClasses = List.copyOf(missingClasses);
    }

    /**
     * Decodes an error reply payload.
     *
     * @param payload the payload
     * @return the decoded error
     */
    static RemoteError parse(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        if (!text.startsWith("type: ")) {
            return new RemoteError(null, -1, text.isBlank() ? null : text.strip(), List.of(), List.of());
        }
        String type = null;
        int reason = -1;
        String message = null;
        List<String> causes = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String line : text.split("\n")) {
            int colon = line.indexOf(": ");
            if (colon < 0) {
                continue;
            }
            String value = line.substring(colon + 2);
            switch (line.substring(0, colon)) {
                case "type" -> type = value;
                case "reason" -> reason = parseReason(value);
                case "message" -> message = value;
                case "cause" -> causes.add(value);
                case "missing-class" -> missing.add(value);
                default -> { /* unknown keys are ignored (newer servers) */ }
            }
        }
        return new RemoteError(type, reason, message, causes, missing);
    }

    /**
     * Converts the error into the exception the session throws: the local equivalent of the remote exception if
     * there is one (a {@code CardRuntimeException} subclass with the same reason, a {@code java.*} runtime
     * exception with the same message), with a {@link SimulatorException} describing the remote failure as its
     * cause; otherwise the {@link SimulatorException} itself.
     *
     * @param action what the session was doing, e.g. "install applet com.example.MyApplet"
     * @return the exception to throw
     */
    RuntimeException toException(String action) {
        SimulatorException remote = new SimulatorException("Failed to " + action + ": " + describe(), type);
        RuntimeException local = localEquivalent();
        if (local == null) {
            return remote;
        }
        try {
            local.initCause(remote);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return remote;
        }
        return local;
    }

    /**
     * Describes the remote failure on one line.
     *
     * @return e.g. {@code javacard.framework.SystemException (reason 0x6444)}
     */
    String describe() {
        StringBuilder s = new StringBuilder(type != null ? type : "error");
        if (reason >= 0) {
            s.append(String.format(" (reason 0x%04X)", reason));
        }
        if (message != null) {
            s.append(": ").append(message);
        }
        for (String cause : causes) {
            s.append(" <- caused by ").append(cause);
        }
        if (!missingClasses.isEmpty()) {
            s.append(" [the applet needed classes that were not sent to the simulator: ")
                    .append(String.join(", ", missingClasses)).append(']');
        }
        return s.toString();
    }

    private RuntimeException localEquivalent() {
        if (type == null) {
            return null;
        }
        try {
            Class<?> c = Class.forName(type, false, RemoteError.class.getClassLoader());
            if (reason >= 0 && CardRuntimeException.class.isAssignableFrom(c)) {
                return (RuntimeException) c.getConstructor(short.class).newInstance((short) reason);
            }
            if (type.startsWith("java.") && RuntimeException.class.isAssignableFrom(c)) {
                return (RuntimeException) c.getConstructor(String.class).newInstance(message);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static int parseReason(String value) {
        try {
            return Integer.decode(value.trim()) & 0xFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
