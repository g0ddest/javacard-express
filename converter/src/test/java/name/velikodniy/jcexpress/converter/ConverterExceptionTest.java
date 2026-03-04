package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.Violation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConverterException}.
 */
class ConverterExceptionTest {

    @Test
    void messageOnlyConstructor_shouldSetMessageAndEmptyViolations() {
        var ex = new ConverterException("conversion failed");

        assertThat(ex.getMessage()).isEqualTo("conversion failed");
        assertThat(ex.violations()).isEmpty();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void violationsConstructor_shouldStoreViolations() {
        var v1 = new Violation("com/example/A", "field x", "float not supported");
        var v2 = new Violation("com/example/A", "getDouble()D", 3, "double not supported");
        var ex = new ConverterException("subset violations found", List.of(v1, v2));

        assertThat(ex.violations()).containsExactly(v1, v2);
    }

    @Test
    void violationsConstructor_shouldFormatMessageWithViolationDetails() {
        var v = new Violation("com/example/A", "field f", "float not supported");
        var ex = new ConverterException("check failed", List.of(v));

        assertThat(ex.getMessage()).contains("check failed")
                .contains("com/example/A")
                .contains("float not supported");
    }

    @Test
    void causeConstructor_shouldSetCauseAndEmptyViolations() {
        var cause = new IOException("file not found");
        var ex = new ConverterException("read error", cause);

        assertThat(ex.getMessage()).isEqualTo("read error");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.violations()).isEmpty();
    }

    @Test
    void violations_shouldReturnUnmodifiableList() {
        var v = new Violation("A", "ctx", "msg");
        var ex = new ConverterException("test", List.of(v));

        // The list should be unmodifiable (List.copyOf)
        assertThat(ex.violations()).isUnmodifiable();
    }

    @Test
    void violationsWithBci_shouldAppearInMessage() {
        var v = new Violation("com/example/B", "process(LAPDU;)V", 42, "long not supported");
        var ex = new ConverterException("errors", List.of(v));

        assertThat(ex.getMessage()).contains("bci 42");
    }

    @Test
    void emptyViolationList_shouldNotThrow() {
        var ex = new ConverterException("no violations", List.of());

        assertThat(ex.violations()).isEmpty();
    }

    @Test
    void multipleViolations_shouldAllAppearInMessage() {
        var violations = List.of(
                new Violation("A", "x", "msg1"),
                new Violation("B", "y", "msg2"),
                new Violation("C", "z", "msg3")
        );
        var ex = new ConverterException("found issues", violations);

        assertThat(ex.getMessage()).contains("msg1", "msg2", "msg3");
    }

    @Test
    void exceptionIsCheckedException() {
        var ex = new ConverterException("test");
        assertThat(ex).isInstanceOf(Exception.class);
        assertThat(ex).isNotInstanceOf(RuntimeException.class);
    }
}
