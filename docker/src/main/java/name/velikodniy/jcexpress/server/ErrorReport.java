package name.velikodniy.jcexpress.server;

import javacard.framework.CardRuntimeException;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Formats a failure as the payload of a {@link Protocol#STATUS_ERROR} reply.
 *
 * <p>The text is UTF-8, one {@code key: value} per line, readable as-is and parsed by the client:</p>
 * <pre>
 * type: javacard.framework.SystemException
 * reason: 0x6444
 * message: ...
 * cause: java.lang.ClassNotFoundException: audit.BaseApplet
 * missing-class: audit.Helper
 * </pre>
 * <p>{@code reason} is present for {@code javacard.framework.CardRuntimeException} (ISOException, SystemException,
 * CryptoException, ...), {@code message} when the exception has one, one {@code cause} line per element of
 * the cause chain and one {@code missing-class} line per class the applet code needed but the client did not send.
 * Line breaks inside messages are replaced by spaces. Readers ignore keys they do not know.</p>
 */
final class ErrorReport {

    private static final int MAX_CAUSES = 8;

    private ErrorReport() {
    }

    /**
     * Describes a throwable, its cause chain and the classes that were missing while it happened.
     *
     * @param failure        the failure
     * @param missingClasses binary names of classes the applet code needed but the client did not send
     * @return the report text
     */
    static String describe(Throwable failure, Collection<String> missingClasses) {
        StringBuilder text = new StringBuilder();
        text.append("type: ").append(failure.getClass().getName()).append('\n');
        if (failure instanceof CardRuntimeException) {
            text.append("reason: ").append(reason((CardRuntimeException) failure)).append('\n');
        }
        if (failure.getMessage() != null) {
            text.append("message: ").append(oneLine(failure.getMessage())).append('\n');
        }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(failure);
        Throwable cause = failure.getCause();
        for (int i = 0; cause != null && i < MAX_CAUSES && seen.add(cause); i++, cause = cause.getCause()) {
            text.append("cause: ").append(summary(cause)).append('\n');
        }
        for (String name : missingClasses) {
            text.append("missing-class: ").append(oneLine(name)).append('\n');
        }
        return text.toString();
    }

    /**
     * Describes a failure that is not an exception (e.g. a protocol violation).
     *
     * @param type    the exception type the client should report
     * @param message the message
     * @return the report text
     */
    static String describe(Class<? extends Throwable> type, String message) {
        return "type: " + type.getName() + "\nmessage: " + oneLine(message) + '\n';
    }

    private static String summary(Throwable t) {
        StringBuilder s = new StringBuilder(t.getClass().getName());
        if (t instanceof CardRuntimeException) {
            s.append(" (reason ").append(reason((CardRuntimeException) t)).append(')');
        }
        if (t.getMessage() != null) {
            s.append(": ").append(oneLine(t.getMessage()));
        }
        return s.toString();
    }

    private static String reason(CardRuntimeException e) {
        return String.format("0x%04X", e.getReason() & 0xFFFF);
    }

    private static String oneLine(String s) {
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}
