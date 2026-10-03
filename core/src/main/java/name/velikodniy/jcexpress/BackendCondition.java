package name.velikodniy.jcexpress;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Optional;

/**
 * Evaluates {@link EnabledOnBackend} and {@link DisabledOnBackend} against the configured backend.
 */
final class BackendCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<AnnotatedElement> element = context.getElement();
        Optional<EnabledOnBackend> enabled = AnnotationSupport.findAnnotation(element, EnabledOnBackend.class);
        Optional<DisabledOnBackend> disabled = AnnotationSupport.findAnnotation(element, DisabledOnBackend.class);
        if (enabled.isEmpty() && disabled.isEmpty()) {
            return ConditionEvaluationResult.enabled("no backend condition");
        }
        Mode backend = Backends.configured(context);
        if (enabled.isPresent() && !Arrays.asList(enabled.get().value()).contains(backend)) {
            return ConditionEvaluationResult.disabled(message("runs only on " + Arrays.toString(enabled.get().value()),
                    backend, enabled.get().reason()));
        }
        if (disabled.isPresent() && Arrays.asList(disabled.get().value()).contains(backend)) {
            return ConditionEvaluationResult.disabled(message("disabled on " + Backends.name(backend), backend,
                    disabled.get().reason()));
        }
        return ConditionEvaluationResult.enabled("runs on " + Backends.name(backend));
    }

    private static String message(String rule, Mode backend, String reason) {
        return rule + "; this run uses " + Backends.SETTING + "=" + Backends.name(backend)
                + (reason.isBlank() ? "" : ": " + reason);
    }
}
