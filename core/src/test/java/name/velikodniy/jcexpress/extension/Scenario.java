package name.velikodniy.jcexpress.extension;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a user-style test class that is executed through the JUnit Platform test kit by
 * {@link JavaCardExtensionTest}. Outside of such a run (IDE, plain discovery) the class is disabled, so
 * scenarios that are meant to fail never break a normal build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(Scenario.OnlyInTestKit.class)
@interface Scenario {

    /** Configuration parameter set by {@link JavaCardExtensionTest} when it runs a scenario. */
    String PARAMETER = "jcx.test.scenario";

    /** Enables scenario classes only when {@link #PARAMETER} is set. */
    final class OnlyInTestKit implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return context.getConfigurationParameter(PARAMETER).isPresent()
                    ? ConditionEvaluationResult.enabled("run by JavaCardExtensionTest")
                    : ConditionEvaluationResult.disabled("scenario class, run only by JavaCardExtensionTest");
        }
    }
}
