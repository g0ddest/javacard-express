package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ModifierSupport;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The session-per-field model of {@link JavaCardExtension} for classes without {@link JavaCardTest} and
 * {@link InstallApplet}: every {@link SmartCard} field (and every {@link SmartCardSession} parameter) gets its own
 * session of the field's {@link Mode}; see {@link JavaCardExtension} for the lifetimes. The sessions of a failed
 * test attach their recent exchanges to the failure.
 */
final class FieldSessions {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(JavaCardExtension.class);

    private static final int DEFAULT_PERSISTENT_MEMORY = 32768;

    static void beforeAll(ExtensionContext context) {
        for (Field field : smartCardFields(context.getRequiredTestClass())) {
            if (ModifierSupport.isStatic(field)) {
                inject(context, field, null);
            }
        }
        if (context.getTestInstanceLifecycle().orElse(Lifecycle.PER_METHOD) == Lifecycle.PER_CLASS) {
            context.getTestInstances().ifPresent(instances -> injectInstances(context, instances));
        }
    }

    static void beforeEach(ExtensionContext context) {
        injectInstances(context, context.getRequiredTestInstances());
        markTest(context);
    }

    /**
     * Injects the instance fields of the test instance and its enclosing instances, skipping instances
     * that a {@code PER_CLASS} context already handled (their marker is visible through the hierarchical
     * extension store).
     */
    private static void injectInstances(ExtensionContext context, TestInstances instances) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        for (Object instance : instances.getAllInstances()) {
            InjectedInstance marker = new InjectedInstance(instance);
            if (store.get(marker) != null) {
                continue;
            }
            store.put(marker, Boolean.TRUE);
            for (Field field : smartCardFields(instance.getClass())) {
                if (!ModifierSupport.isStatic(field)) {
                    inject(context, field, instance);
                }
            }
        }
    }

    private static List<Field> smartCardFields(Class<?> type) {
        return AnnotationSupport.findAnnotatedFields(type, SmartCard.class, field -> true,
                HierarchyTraversalMode.TOP_DOWN);
    }

    private static void inject(ExtensionContext context, Field field, Object target) {
        SmartCard annotation = field.getAnnotation(SmartCard.class);
        SmartCardSession session = createSession(annotation, field, JavaCardExtension.logEnabled(context));
        context.getStore(NAMESPACE).put(new Object(), new ManagedSession(session));
        sessionsOf(context).add(session);
        try {
            field.setAccessible(true);
            field.set(target, session);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new ExtensionConfigurationException("Cannot inject @SmartCard field " + describe(field), e);
        }
    }

    private static SmartCardSession createSession(SmartCard annotation, Field field, boolean logAll) {
        Class<?> type = field.getType();
        checkMode(annotation.mode(), "field " + describe(field));
        checkFieldType(annotation, field);
        checkPersistentMemory(annotation, field);
        boolean canLog = type.isAssignableFrom(LoggingSession.class);
        if (annotation.log() && !canLog) {
            throw new ExtensionConfigurationException("@SmartCard(log = true) field " + describe(field)
                    + " cannot hold the LoggingSession that wraps the card; declare it as SmartCardSession"
                    + " or LoggingSession");
        }
        SmartCardSession session = annotation.mode() == Mode.CONTAINER
                ? createContainerSession(annotation)
                : new EmbeddedSession();
        boolean wrap = type == LoggingSession.class || (canLog && (annotation.log() || logAll));
        return wrap ? LoggingSession.wrap(session, annotation.log() || logAll) : session;
    }

    /** Accepts SmartCardSession (or a supertype), LoggingSession and the backend class of the mode. */
    private static void checkFieldType(SmartCard annotation, Field field) {
        Class<?> type = field.getType();
        boolean backendType = annotation.mode() == Mode.CONTAINER
                ? type.getName().equals("name.velikodniy.jcexpress.container.ContainerSession")
                : type.isAssignableFrom(EmbeddedSession.class);
        if (!type.isAssignableFrom(SmartCardSession.class) && type != LoggingSession.class && !backendType) {
            throw new ExtensionConfigurationException("@SmartCard field " + describe(field) + " must be"
                    + " declared as SmartCardSession, LoggingSession or the session class of Mode."
                    + annotation.mode());
        }
    }

    /**
     * Rejects the backends that run only {@link JavaCardTest} classes: they install the declared applets on a
     * simulated GlobalPlatform card or a real card, and the run chooses them with {@code jcx.backend}.
     */
    private static void checkMode(Mode mode, String target) {
        if (mode == Mode.SIMULATED_GP || mode == Mode.LIVECARD) {
            String backend = mode == Mode.SIMULATED_GP ? "simulated-gp" : "livecard";
            throw new ExtensionConfigurationException("@SmartCard(mode = Mode." + mode + ") on " + target
                    + ": the backends simulated-gp and livecard run @JavaCardTest classes. Annotate the class with"
                    + " @JavaCardTest, declare its applets with @InstallApplet and choose the backend when the tests"
                    + " run: -Djcx.backend=" + backend + (mode == Mode.LIVECARD ? " (only as a JVM system property)"
                    : "") + ". A @SmartCard field outside @JavaCardTest supports Mode.EMBEDDED and Mode.CONTAINER.");
        }
    }

    /**
     * A parameter receives an embedded session; a {@link SmartCard} annotation must not ask for another one, and
     * {@code log = true} needs a parameter that can hold the {@link LoggingSession} that wraps it.
     */
    private static void checkParameter(ParameterContext parameter) {
        SmartCard annotation = parameter.findAnnotation(SmartCard.class).orElse(null);
        if (annotation == null) {
            return;
        }
        Class<?> type = parameter.getParameter().getType();
        String target = "parameter " + parameter.getIndex() + " (" + type.getName() + ") of "
                + parameter.getDeclaringExecutable().getDeclaringClass().getName() + "."
                + parameter.getDeclaringExecutable().getName();
        checkMode(annotation.mode(), target);
        if (annotation.mode() == Mode.CONTAINER) {
            throw new ExtensionConfigurationException("@SmartCard(mode = Mode.CONTAINER) on " + target
                    + ": a parameter receives an embedded session; declare a @SmartCard(mode = Mode.CONTAINER)"
                    + " field for the container backend");
        }
        if (annotation.log() && !type.isAssignableFrom(LoggingSession.class)) {
            throw new ExtensionConfigurationException("@SmartCard(log = true) " + target + " cannot hold the"
                    + " LoggingSession that wraps the card; declare it as SmartCardSession or LoggingSession");
        }
    }

    @SuppressWarnings("deprecation")
    private static void checkPersistentMemory(SmartCard annotation, Field field) {
        if (annotation.persistentMemory() != DEFAULT_PERSISTENT_MEMORY) {
            throw new ExtensionConfigurationException("@SmartCard(persistentMemory = "
                    + annotation.persistentMemory() + ") on field " + describe(field) + " is not supported:"
                    + " the embedded and container backends run jCardSim, which does not model the size of"
                    + " persistent memory; remove the attribute");
        }
    }

    private static SmartCardSession createContainerSession(SmartCard annotation) {
        // Reflection keeps the core module free of a compile-time dependency on the container module
        Class<?> factory;
        try {
            factory = Class.forName("name.velikodniy.jcexpress.container.ContainerSessionFactory");
            Class.forName("org.testcontainers.containers.GenericContainer");
        } catch (ClassNotFoundException e) {
            throw new ExtensionConfigurationException("@SmartCard(mode = Mode.CONTAINER) needs the container module"
                    + " on the test class path: add the dependency name.velikodniy:javacard-express-container with"
                    + " scope test (it brings Testcontainers; Docker must be running when the tests run). Missing: "
                    + e.getMessage(), e);
        }
        try {
            Method create = factory.getMethod("create", SmartCard.class);
            return (SmartCardSession) create.invoke(null, annotation);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Failed to create container session", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create container session", e);
        }
    }

    private static String describe(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName() + " (" + field.getType().getName() + ")";
    }

    /** Identity key marking a test instance whose fields were injected. */
    private record InjectedInstance(Object instance) {
        @Override
        public boolean equals(Object other) {
            return other instanceof InjectedInstance that && that.instance == instance;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance);
        }
    }

    /**
     * Store value that closes its session once. JUnit 5.13+ closes {@link AutoCloseable} store values,
     * older versions close {@link ExtensionContext.Store.CloseableResource} values.
     */
    @SuppressWarnings("deprecation")
    private static final class ManagedSession implements ExtensionContext.Store.CloseableResource, AutoCloseable {
        private final SmartCardSession session;
        private final AtomicBoolean closed = new AtomicBoolean();

        ManagedSession(SmartCardSession session) {
            this.session = session;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                session.close();
            }
        }
    }

    private static final String MARKS = "transcript-marks";

    private FieldSessions() {
    }

    /** The sessions created for a context (its own list, not its parent's). */
    @SuppressWarnings("unchecked")
    private static List<SmartCardSession> sessionsOf(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent("sessions " + context.getUniqueId(),
                key -> new ArrayList<SmartCardSession>(), List.class);
    }

    /** Remembers where the histories of the class's sessions stand when a test starts. */
    private static void markTest(ExtensionContext context) {
        Map<SmartCardSession, Long> marks = new IdentityHashMap<>();
        for (ExtensionContext ancestor = context.getParent().orElse(null); ancestor != null;
             ancestor = ancestor.getParent().orElse(null)) {
            for (SmartCardSession session : sessionsOf(ancestor)) {
                marks.put(session, session.history().position());
            }
        }
        context.getStore(NAMESPACE).put(MARKS, marks);
    }

    /**
     * Attaches the recent exchanges of the sessions a failed test used to its failure.
     *
     * @param context   the test's context
     * @param throwable the failure
     */
    @SuppressWarnings("unchecked")
    static void attachTranscripts(ExtensionContext context, Throwable throwable) {
        Map<SmartCardSession, Long> marks = context.getStore(NAMESPACE).get(MARKS, Map.class);
        for (ExtensionContext current = context; current != null; current = current.getParent().orElse(null)) {
            for (SmartCardSession session : sessionsOf(current)) {
                long since = marks == null ? 0 : marks.getOrDefault(session, 0L);
                String transcript = session.history().transcriptSince(since, CardTestLifecycle.FAILURE_ENTRIES);
                if (!transcript.isEmpty()) {
                    throwable.addSuppressed(new CardTestLifecycle.CardTranscript(transcript));
                }
            }
        }
    }

    /**
     * Returns whether a parameter is a session this model provides.
     *
     * @param parameter the parameter
     * @return true for {@link SmartCardSession}, {@link LoggingSession} and {@link EmbeddedSession}
     */
    static boolean supports(ParameterContext parameter) {
        Class<?> type = parameter.getParameter().getType();
        return type == SmartCardSession.class || type == LoggingSession.class || type == EmbeddedSession.class;
    }

    /**
     * Creates a new embedded session for a parameter: per test for test and {@code @BeforeEach}/{@code @AfterEach}
     * parameters, per class for {@code @BeforeAll}/{@code @AfterAll} parameters and constructor parameters of
     * {@code PER_CLASS} test instances.
     *
     * @param parameter the parameter
     * @param context   the context the parameter is resolved in
     * @return the session, closed when the context ends; wrapped in a {@link LoggingSession} for a parameter of that
     *         type, and for a {@link SmartCardSession} parameter with {@code @SmartCard(log = true)} or
     *         {@value JavaCardExtension#LOG_PARAMETER}{@code =true} (which also print the exchanges)
     * @throws ExtensionConfigurationException if the parameter's {@link SmartCard} annotation asks for another
     *                                         backend than the embedded one, or for logging a parameter that cannot
     *                                         hold a {@link LoggingSession}
     */
    static Object resolve(ParameterContext parameter, ExtensionContext context) {
        checkParameter(parameter);
        Class<?> type = parameter.getParameter().getType();
        boolean print = JavaCardExtension.logEnabled(context)
                || parameter.findAnnotation(SmartCard.class).map(SmartCard::log).orElse(false);
        SmartCardSession session = new EmbeddedSession();
        if (type == LoggingSession.class || (type == SmartCardSession.class && print)) {
            session = LoggingSession.wrap(session, print);
        }
        context.getStore(NAMESPACE).put(new Object(), new ManagedSession(session));
        sessionsOf(context).add(session);
        return session;
    }
}
