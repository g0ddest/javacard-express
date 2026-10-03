package name.velikodniy.jcexpress.livecard.model;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a scenario class that {@link SameTestOnEveryBackendTest} runs through the JUnit Platform test kit with a
 * chosen backend; anywhere else it is disabled.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OnlyInTestKit.Condition.class)
@interface OnlyInTestKit {

    /** Configuration parameter set by the test kit runs. */
    String PARAMETER = "jcx.test.backends";

    /** Enables scenario classes only when {@link #PARAMETER} is set. */
    final class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return context.getConfigurationParameter(PARAMETER).isPresent()
                    ? ConditionEvaluationResult.enabled("run by SameTestOnEveryBackendTest")
                    : ConditionEvaluationResult.disabled("scenario class, run only by SameTestOnEveryBackendTest");
        }
    }
}
