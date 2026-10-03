package name.velikodniy.jcexpress.model;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a scenario class that {@link DeclarativeModelTest} runs through the JUnit Platform test kit; anywhere
 * else (Surefire, an IDE) it is disabled, so scenarios that are meant to fail never break a build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OnlyInTestKit.Condition.class)
@interface OnlyInTestKit {

    /** Configuration parameter set by {@link DeclarativeModelTest}. */
    String PARAMETER = "jcx.test.model";

    /** Enables scenario classes only when {@link #PARAMETER} is set. */
    final class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return context.getConfigurationParameter(PARAMETER).isPresent()
                    ? ConditionEvaluationResult.enabled("run by DeclarativeModelTest")
                    : ConditionEvaluationResult.disabled("scenario class, run only by DeclarativeModelTest");
        }
    }
}
