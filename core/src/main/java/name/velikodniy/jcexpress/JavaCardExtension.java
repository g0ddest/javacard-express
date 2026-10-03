package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * The JUnit 5 extension of JavaCard Express. It runs two models; a test class uses one of them.
 *
 * <h2>Declarative model: {@link JavaCardTest} and {@link InstallApplet}</h2>
 * <pre>
 * {@literal @}JavaCardTest
 * {@literal @}InstallApplet(CounterApplet.class)
 * class CounterAppletTest {
 *     {@literal @}Test
 *     void startsAtZero(SmartCardSession card) { ... }
 * }
 * </pre>
 * <p>One card per test class run (the class and its {@code @Nested} classes) on the backend {@code jcx.backend}
 * names; declared applets are installed before their scope and deleted after it (see {@link InstallApplet} and
 * {@link Isolation}); before every test the nearest declared applet is selected unless it still is; tests,
 * lifecycle methods and constructors receive the card as a {@link SmartCardSession} parameter, and
 * {@link SmartCard} fields of type {@link SmartCardSession} get the same card. The card completes '61XX' and
 * '6CXX' in {@code send} like a PC/SC reader (see {@link SW}), and its {@code history()} holds the exchanges of
 * the current test. A failed test, and a failure in a {@code @BeforeAll}, {@code @BeforeEach}, {@code @AfterEach}
 * or {@code @AfterAll} method or in a declared install, gets the APDU exchanges attached to the failure (a test's
 * also published as a file, {@code apdu-transcript.txt}); when the run installs no applet at all, the transcript
 * starts with a note that says so. A note {@code # test body: <test>} in the transcript marks where the test
 * method starts, after the installs, the automatic SELECT and the {@code @BeforeEach} methods. With
 * {@value #LOG_PARAMETER}{@code =true} the exchanges of the card are also printed while the tests run, one line each
 * on standard output in the transcript format, through the channel of
 * {@link SmartCardSession#logged(boolean) logged(true)}, with a title line per class and test.</p>
 *
 * <h2>Session-per-field model: {@link SmartCard} fields in other classes</h2>
 * <pre>
 * {@literal @}ExtendWith(JavaCardExtension.class)       // optional: {@literal @}SmartCard registers the extension itself
 * class MyAppletTest {
 *     {@literal @}SmartCard
 *     SmartCardSession card;
 * }
 * </pre>
 * <ul>
 *   <li><b>static fields</b>: one session per test class, injected before the {@code @BeforeAll}
 *       methods run and closed after the {@code @AfterAll} methods;</li>
 *   <li><b>instance fields</b> (default {@code Lifecycle.PER_METHOD}): one session per test method
 *       invocation (each parameterized or repeated invocation gets its own), injected before the
 *       {@code @BeforeEach} methods and closed after the {@code @AfterEach} methods. Enclosing instances
 *       of {@code @Nested} tests are handled the same way;</li>
 *   <li><b>instance fields</b> with {@code @TestInstance(Lifecycle.PER_CLASS)}: one session per test
 *       instance, injected before the {@code @BeforeAll} methods and shared by all tests of the class
 *       (card state carries over from one test to the next);</li>
 *   <li><b>parameters</b> of type {@link SmartCardSession}, {@link LoggingSession} or {@link EmbeddedSession}: a new
 *       embedded session per test method (per class for {@code @BeforeAll}/{@code @AfterAll} parameters), separate
 *       from the field sessions.</li>
 * </ul>
 * <p>Fields declared in superclasses are injected as well. Every session is registered in the JUnit
 * extension store of the context it belongs to and closed by JUnit when that context ends, so tests that
 * run concurrently never close each other's sessions. A field may be declared as {@link SmartCardSession}, as
 * {@link LoggingSession} (the session is then always wrapped for logging) or as the backend class
 * ({@link EmbeddedSession} for {@link Mode#EMBEDDED}). {@code @SmartCard(log = true)} needs a field or parameter
 * that can hold a {@link LoggingSession}. The JUnit configuration parameter or system property
 * {@value #LOG_PARAMETER}{@code =true} logs every field and parameter that can hold a {@link LoggingSession}. Other
 * field types are rejected with an {@link ExtensionConfigurationException}.</p>
 */
public class JavaCardExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback,
        BeforeTestExecutionCallback, AfterEachCallback, ParameterResolver, TestExecutionExceptionHandler,
        LifecycleMethodExecutionExceptionHandler {

    /**
     * Configuration parameter (or system property) that enables APDU logging for all sessions: {@code true} prints
     * the exchanges of the card of {@link JavaCardTest} classes and wraps the {@link SmartCard} fields and session
     * parameters of other classes that can hold a {@link LoggingSession}. The lines appear on standard output, one
     * each ({@code [JCX] C: 80300000020064}), through java.util.logging (logger {@code name.velikodniy.jcexpress},
     * level INFO; {@link LoggingSession} says how to route them elsewhere).
     */
    public static final String LOG_PARAMETER = "jcx.log";

    /** Creates the extension (JUnit instantiates it). */
    public JavaCardExtension() {
        // stateless: the state of a run lives in the JUnit extension store
    }

    /**
     * Returns whether a run logs APDUs.
     *
     * @param context any context of the run
     * @return true if {@value #LOG_PARAMETER} is {@code true}
     */
    static boolean logEnabled(ExtensionContext context) {
        return context.getConfigurationParameter(LOG_PARAMETER).map(Boolean::parseBoolean).orElse(false);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (CardTestLifecycle.applies(context)) {
            CardTestLifecycle.beforeAll(context);
        } else {
            FieldSessions.beforeAll(context);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (CardTestLifecycle.applies(context)) {
            CardTestLifecycle.afterAll(context);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (CardTestLifecycle.applies(context)) {
            CardTestLifecycle.beforeEach(context);
        } else {
            FieldSessions.beforeEach(context);
        }
    }

    /**
     * Marks in the card's history where the test method starts (declarative model only), after the installs, the
     * automatic SELECT and the {@code @BeforeEach} methods.
     *
     * @param context the test's context
     */
    @Override
    public void beforeTestExecution(ExtensionContext context) {
        if (CardTestLifecycle.applies(context)) {
            CardTestLifecycle.beforeTestExecution(context);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (CardTestLifecycle.applies(context)) {
            CardTestLifecycle.afterEach(context);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return CardTestLifecycle.applies(context) ? CardTestLifecycle.supports(parameter, context)
                : FieldSessions.supports(parameter);
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return CardTestLifecycle.applies(context) ? CardTestLifecycle.resolve(parameter, context)
                : FieldSessions.resolve(parameter, context);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        attachTranscript(context, throwable);
        throw throwable;
    }

    /**
     * Attaches the APDU exchanges to a failure of a {@code @BeforeAll} method, as to a failed test.
     *
     * @param context   the class's context
     * @param throwable the failure
     * @throws Throwable the failure, with the exchanges as a suppressed exception
     */
    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        attachTranscript(context, throwable);
        throw throwable;
    }

    /**
     * Attaches the APDU exchanges to a failure of a {@code @BeforeEach} method, as to a failed test.
     *
     * @param context   the test's context
     * @param throwable the failure
     * @throws Throwable the failure, with the exchanges as a suppressed exception
     */
    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        attachTranscript(context, throwable);
        throw throwable;
    }

    /**
     * Attaches the APDU exchanges to a failure of an {@code @AfterEach} method, as to a failed test.
     *
     * @param context   the test's context
     * @param throwable the failure
     * @throws Throwable the failure, with the exchanges as a suppressed exception
     */
    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        attachTranscript(context, throwable);
        throw throwable;
    }

    /**
     * Attaches the APDU exchanges to a failure of an {@code @AfterAll} method, as to a failed test.
     *
     * @param context   the class's context
     * @param throwable the failure
     * @throws Throwable the failure, with the exchanges as a suppressed exception
     */
    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        attachTranscript(context, throwable);
        throw throwable;
    }

    /** Attaches the exchanges of the context's card or sessions; reporting never replaces the failure. */
    private static void attachTranscript(ExtensionContext context, Throwable throwable) {
        try {
            if (CardTestLifecycle.applies(context)) {
                CardTestLifecycle.attachTranscript(context, throwable);
            } else {
                FieldSessions.attachTranscripts(context, throwable);
            }
        } catch (RuntimeException e) {
            throwable.addSuppressed(e);
        }
    }
}
