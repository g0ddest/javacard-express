package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Build tools that run processors through a wrapper of their own hand them a proxy of javac's processing environment
 * (IntelliJ IDEA's build does). The check finds javac's environment behind it; where it cannot, it says that it did
 * not run.
 */
class WrappedEnvironmentTest {

    private static final String LEDGER = """
            package p;

            public class Ledger {
                long total;
            }
            """;

    @TempDir
    Path project;

    @Test
    void behindAProxyOfTheProcessingEnvironmentTheCheckRuns_jcvm31_2_2_1_3() {
        Result result = Javac.compileWithProcessor(project, Map.of("p/Ledger.java", LEDGER),
                new Wrapping(environment -> new Delegating(environment)));

        assertThat(result.errors()).containsExactly(
                "Ledger.java:4:10: Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)");
    }

    @Test
    void anEnvironmentThatHidesJavacsSaysThatTheCheckDidNotRun() {
        Result result = Javac.compileWithProcessor(project, Map.of("p/Ledger.java", LEDGER),
                new Wrapping(environment -> new Hiding(environment)));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(note -> {
            assertThat(note.getKind()).isEqualTo(Diagnostic.Kind.NOTE);
            assertThat(note.getMessage(Locale.ROOT)).startsWith("Java Card: the check did not run: the processing"
                    + " environment is ").endsWith(", not javac's");
        });
    }

    /** A processor that runs the check with a proxy of the environment, as a build tool's wrapper does. */
    @SupportedAnnotationTypes("*")
    private static final class Wrapping extends AbstractProcessor {

        private final JavaCardCheckProcessor check = new JavaCardCheckProcessor();
        private final Function<ProcessingEnvironment, InvocationHandler> handler;

        Wrapping(Function<ProcessingEnvironment, InvocationHandler> handler) {
            this.handler = handler;
        }

        @Override
        public synchronized void init(ProcessingEnvironment environment) {
            super.init(environment);
            check.init((ProcessingEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {ProcessingEnvironment.class}, handler.apply(environment)));
        }

        @Override
        public Set<String> getSupportedOptions() {
            return check.getSupportedOptions();
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            return false;
        }
    }

    /** Forwards every call to the environment it holds. */
    private record Delegating(ProcessingEnvironment delegate) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            return forward(delegate, method, arguments);
        }
    }

    /** Forwards every call to an environment it keeps where no field of that type shows it. */
    private static final class Hiding implements InvocationHandler {

        private final Map<String, Object> state = new HashMap<>();

        Hiding(ProcessingEnvironment environment) {
            state.put("environment", environment);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            return forward(state.get("environment"), method, arguments);
        }
    }

    private static Object forward(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
