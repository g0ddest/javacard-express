package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.check.Violation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown by {@link TokenAssigner} when the package cannot be represented with Java Card tokens:
 * a token range of JCVM 3.1 §4.3.7 (Table 4-2) would be exceeded, or the classes break a rule of
 * the Java Card language subset that only shows up when tokens are assigned (for example a public
 * method overriding a package-visible one, §2.2.1.1).
 *
 * <p>The converter reports the violations through
 * {@link name.velikodniy.jcexpress.converter.ConverterException#violations()}.
 */
public class TokenAssignmentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<Violation> violations;

    /**
     * Creates the exception.
     *
     * @param violations the problems found (non-empty)
     */
    public TokenAssignmentException(List<Violation> violations) {
        super("Token assignment failed:\n" + violations.stream().map(v -> "  - " + v)
                .collect(Collectors.joining("\n")));
        this.violations = List.copyOf(violations);
    }

    /**
     * Returns the problems that prevent token assignment.
     *
     * @return unmodifiable list of violations
     */
    public List<Violation> violations() {
        return violations;
    }
}
