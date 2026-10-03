package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.CardConnector;
import name.velikodniy.jcexpress.livecard.ConfigSources;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.LiveCardException;
import name.velikodniy.jcexpress.livecard.LiveCardRun;
import name.velikodniy.jcexpress.livecard.PcscConnector;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JUnit Jupiter extension behind {@link LiveCardTest}.
 *
 * <ul>
 *   <li><b>Condition</b>: a class runs only if live-card tests are switched on (the JVM system property
 *       {@value #ENABLED_PARAMETER}{@code =true} for this run; not a JUnit configuration parameter, an environment
 *       variable or a settings file), the run was not aborted and a card is present. Otherwise it is skipped with
 *       the reason. With live-card tests disabled nothing touches the PC/SC subsystem.</li>
 *   <li><b>Before all</b>: connects ({@link LiveCard#connect(LiveCardConfig, CardConnector, LiveCardRun)}),
 *       unless a constructor parameter already did (with {@code @TestInstance(PER_CLASS)} JUnit creates the instance
 *       before the before-all callbacks); a {@code @Nested} class shares its enclosing class's connection.</li>
 *   <li><b>Transcripts</b>: {@code <transcriptDir>/<fully qualified test class>/before-all.txt},
 *       {@code <test method>.txt} per test and {@code after-all.txt}, with a directory per {@code @Nested} class below
 *       it; every file starts anew in each run. Each test's transcript is published with the test's result
 *       ({@code apdu-transcript.txt}), a class's set-up and cleanup transcripts with the class's.</li>
 *   <li><b>After all</b>: {@link LiveCard#cleanup()} and disconnect, whatever happened before; when JUnit runs
 *       no after-all callback (a constructor failed), closing its store does the same.</li>
 *   <li><b>Threads</b>: the card belongs to the thread that connected it (javax.smartcardio's exclusive access);
 *       a test or lifecycle method that JUnit runs in another thread ({@code @Timeout} with
 *       {@code threadMode = SEPARATE_THREAD}) is rejected with an explanation before it runs.</li>
 * </ul>
 * <p>JUnit configuration parameters for this module's own tests: {@value #CONNECTOR_PARAMETER} (class name of
 * a {@link CardConnector}, default {@link PcscConnector}) with {@value #ENABLED_PARAMETER} (switches a connector
 * other than the PC/SC one on), {@value #ISOLATED_RUN_PARAMETER} ({@code true}: a {@link LiveCardRun} per test plan
 * instead of the JVM-wide one) and {@value #SETTINGS_FILE_PARAMETER} (read the settings only from this file and the
 * defaults). None of them can open the PC/SC reader: {@link PcscConnector} checks the JVM's own system
 * property.</p>
 */
public final class LiveCardExtension implements ExecutionCondition, BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, ParameterResolver, InvocationInterceptor {

    /**
     * The JVM system property that switches live-card tests on ({@code true}) for one run; as a JUnit configuration
     * parameter it only switches a test connector ({@link #CONNECTOR_PARAMETER}), never the PC/SC reader.
     */
    public static final String ENABLED_PARAMETER = "jcx.livecard.enabled";
    /** Configuration parameter naming the {@link CardConnector} implementation. */
    public static final String CONNECTOR_PARAMETER = "jcx.livecard.connector";
    /** Configuration parameter that gives the test plan its own {@link LiveCardRun}. */
    public static final String ISOLATED_RUN_PARAMETER = "jcx.livecard.isolatedRun";
    /** Configuration parameter naming the only settings file to read (besides the defaults). */
    public static final String SETTINGS_FILE_PARAMETER = "jcx.livecard.settingsFile";

    private static final Namespace NAMESPACE = Namespace.create(LiveCardExtension.class);
    private static final String CARD = "card";
    private static final String CONFIG = "config";
    private static final String RUN = "run";

    /**
     * The connection of a class, owned by the context that opened it, and the thread that connected (the only one
     * that may use it). JUnit closes it with the store when {@link #afterAll(ExtensionContext)} did not take it.
     */
    private record CardResource(LiveCard card, String ownerId, Thread thread) implements AutoCloseable {
        boolean ownedBy(ExtensionContext context) {
            return ownerId.equals(context.getUniqueId());
        }

        /** Deletes what the class created and disconnects, as {@link #afterAll(ExtensionContext)} does. */
        @Override
        public void close() {
            try {
                card.cleanup();
            } finally {
                card.close();
            }
        }
    }

    /**
     * Creates the extension (JUnit instantiates it through {@link LiveCardTest}).
     */
    public LiveCardExtension() {
        // stateless: state lives in the extension context stores
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<String> disabled = LiveMode.disabledReason(context);
        if (disabled.isPresent()) {
            return ConditionEvaluationResult.disabled(disabled.get());
        }
        Optional<String> aborted = run(context).abortReason();
        if (aborted.isPresent()) {
            return ConditionEvaluationResult.disabled(aborted.get());
        }
        if (context.getTestMethod().isPresent() || findCard(context).isPresent()) {
            return ConditionEvaluationResult.enabled("live card connected");
        }
        CardConnector.Presence presence = connector(context).probe(config(context));
        return presence.present() ? ConditionEvaluationResult.enabled(presence.description())
                : ConditionEvaluationResult.disabled("live-card test skipped: " + presence.description());
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        CardResource resource = resource(context);
        if (!resource.ownedBy(context)) {
            // a @Nested class: the enclosing class's connection, the nested class's own set-up transcript
            resource.card().transcriptTo(TranscriptFiles.classDirectory(context).resolve(TranscriptFiles.BEFORE_ALL),
                    context.getDisplayName() + " @BeforeAll");
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        card(context).transcriptTo(TranscriptFiles.testFile(context),
                context.getRequiredTestClass().getSimpleName() + " " + context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        LiveCard card = card(context);
        Path testFile = card.transcript().file();
        card.transcriptTo(TranscriptFiles.classDirectory(context).resolve(TranscriptFiles.AFTER_ALL),
                "after " + context.getDisplayName());
        TranscriptFiles.publish(context, testFile, "apdu-transcript.txt");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Optional<CardResource> resource = findResource(context);
        if (resource.isEmpty()) {
            return;
        }
        if (resource.get().ownedBy(context)) {
            closeOwnConnection(context, resource.get().card());
        } else {
            leaveNestedClass(context, resource.get().card());
        }
    }

    /** Cleanup and disconnect of the connection the class opened, whatever happened before. */
    private static void closeOwnConnection(ExtensionContext context, LiveCard card) {
        context.getStore(NAMESPACE).remove(CARD);
        Path directory = TranscriptFiles.classDirectory(context);
        try {
            card.transcriptTo(directory.resolve(TranscriptFiles.AFTER_ALL), context.getDisplayName()
                    + " @AfterAll cleanup");
            card.cleanup();
        } finally {
            card.close();
            publishClassTranscripts(context, card.config().transcriptDir().resolve(directory));
        }
    }

    /** The end of a {@code @Nested} class: the transcript continues in the enclosing class's after-all file. */
    private static void leaveNestedClass(ExtensionContext context, LiveCard card) {
        Path enclosing = TranscriptFiles.classDirectory(context.getParent().orElseThrow());
        card.transcriptTo(enclosing.resolve(TranscriptFiles.AFTER_ALL), "after " + context.getDisplayName());
        publishClassTranscripts(context, card.config().transcriptDir().resolve(TranscriptFiles.classDirectory(context)));
    }

    private static void publishClassTranscripts(ExtensionContext context, Path directory) {
        TranscriptFiles.publish(context, directory.resolve(TranscriptFiles.BEFORE_ALL), "apdu-transcript-before-all.txt");
        TranscriptFiles.publish(context, directory.resolve(TranscriptFiles.AFTER_ALL), "apdu-transcript-after-all.txt");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == LiveCard.class;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Connects if the class has no connection yet: with {@code @TestInstance(PER_CLASS)} JUnit resolves the
     * constructor's parameters before {@link #beforeAll(ExtensionContext)}.</p>
     */
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return card(extensionContext);
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                         ExtensionContext context) throws Throwable {
        inCardThread(invocation, method, context);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                          ExtensionContext context) throws Throwable {
        inCardThread(invocation, method, context);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                    ExtensionContext context) throws Throwable {
        inCardThread(invocation, method, context);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                            ExtensionContext context) throws Throwable {
        inCardThread(invocation, method, context);
    }

    @Override
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation, ReflectiveInvocationContext<Method> method,
                                           ExtensionContext context) throws Throwable {
        return inCardThread(invocation, method, context);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                         ExtensionContext context) throws Throwable {
        inCardThread(invocation, method, context);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> method,
                                        ExtensionContext context) throws Throwable {
        inCardThread(invocation, method, context);
    }

    /**
     * Runs a method if it runs in the thread of the card: JUnit runs it in another thread for a timeout with
     * {@code threadMode = SEPARATE_THREAD}, and javax.smartcardio admits only the thread that holds the card's
     * exclusive access.
     */
    private static <T> T inCardThread(Invocation<T> invocation, ReflectiveInvocationContext<Method> method,
                                      ExtensionContext context) throws Throwable {
        Optional<CardResource> resource = findResource(context);
        Thread current = Thread.currentThread();
        if (resource.isPresent() && resource.get().thread() != current) {
            invocation.skip();
            throw new ExtensionConfigurationException(method.getExecutable().getName() + " runs in thread '"
                    + current.getName() + "', but the live card is bound to thread '" + resource.get().thread()
                    .getName() + "', which connected it: javax.smartcardio admits only that thread while it holds"
                    + " exclusive access to the card (Card.beginExclusive). A timeout with threadMode ="
                    + " SEPARATE_THREAD (@Timeout, or junit.jupiter.execution.timeout.thread.mode.default) runs"
                    + " tests in another thread: use @Timeout in its default SAME_THREAD mode, and assertTimeout"
                    + " instead of assertTimeoutPreemptively. Nothing was sent to the card.");
        }
        return invocation.proceed();
    }

    private static Optional<CardResource> findResource(ExtensionContext context) {
        return Optional.ofNullable(context.getStore(NAMESPACE).get(CARD, CardResource.class));
    }

    private static Optional<LiveCard> findCard(ExtensionContext context) {
        return findResource(context).map(CardResource::card);
    }

    private static LiveCard card(ExtensionContext context) {
        return resource(context).card();
    }

    /**
     * Returns the connection of the context's class (or of an enclosing class), connecting now if there is none; the
     * connection then belongs to the class (its after-all callback cleans up and disconnects).
     */
    private static CardResource resource(ExtensionContext context) {
        return findResource(context).orElseGet(() -> connect(classContext(context)));
    }

    private static CardResource connect(ExtensionContext owner) {
        LiveCard card = LiveCard.connect(config(owner), connector(owner), run(owner));
        CardResource resource = new CardResource(card, owner.getUniqueId(), Thread.currentThread());
        owner.getStore(NAMESPACE).put(CARD, resource);
        card.transcriptTo(TranscriptFiles.classDirectory(owner).resolve(TranscriptFiles.BEFORE_ALL),
                owner.getDisplayName() + " @BeforeAll");
        owner.publishReportEntry("livecard.reader", card.reader());
        owner.publishReportEntry("livecard.atr", card.atr());
        return resource;
    }

    /** The context of the test class that a context belongs to (itself for a class context). */
    private static ExtensionContext classContext(ExtensionContext context) {
        ExtensionContext current = context;
        while (current.getTestMethod().isPresent() && current.getParent().isPresent()) {
            current = current.getParent().get();
        }
        return current;
    }

    private static LiveCardConfig config(ExtensionContext context) {
        Optional<String> file = context.getConfigurationParameter(SETTINGS_FILE_PARAMETER);
        return context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(CONFIG, key -> file
                .map(path -> LiveCardConfig.load(new ConfigSources(Map.of(), Map.of(), List.of(Path.of(path)))))
                .orElseGet(LiveCardConfig::load), LiveCardConfig.class);
    }

    private static LiveCardRun run(ExtensionContext context) {
        boolean isolated = context.getConfigurationParameter(ISOLATED_RUN_PARAMETER).map(Boolean::parseBoolean)
                .orElse(false);
        return isolated ? context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(RUN, key -> new LiveCardRun(), LiveCardRun.class) : LiveCardRun.current();
    }

    private static CardConnector connector(ExtensionContext context) {
        Optional<String> name = context.getConfigurationParameter(CONNECTOR_PARAMETER);
        if (name.isEmpty()) {
            return new PcscConnector();
        }
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return Class.forName(name.get(), true, loader).asSubclass(CardConnector.class)
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new LiveCardException("Cannot create the card connector " + name.get() + ": " + e, e);
        }
    }
}
