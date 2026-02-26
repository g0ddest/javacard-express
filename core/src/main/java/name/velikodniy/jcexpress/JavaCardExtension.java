package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * JUnit 5 extension that manages {@link SmartCardSession} lifecycle.
 *
 * <p>Discovers fields annotated with {@link SmartCard} and injects
 * an appropriate session implementation. Sessions are created before
 * each test and closed after each test.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * {@literal @}ExtendWith(JavaCardExtension.class)
 * class MyAppletTest {
 *     {@literal @}SmartCard
 *     SmartCardSession card;
 * }
 * </pre>
 */
public class JavaCardExtension implements TestInstancePostProcessor, BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(JavaCardExtension.class);

    private static final String SESSIONS_KEY = "sessions";

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        List<SmartCardSession> sessions = new ArrayList<>();

        for (Field field : testInstance.getClass().getDeclaredFields()) {
            SmartCard annotation = field.getAnnotation(SmartCard.class);
            if (annotation == null) {
                continue;
            }

            if (!SmartCardSession.class.isAssignableFrom(field.getType())) {
                throw new IllegalStateException(
                        "@SmartCard can only be applied to SmartCardSession fields, found: " + field.getType().getName());
            }

            SmartCardSession session = createSession(annotation);
            sessions.add(session);

            field.setAccessible(true);
            field.set(testInstance, session);
        }

        context.getStore(NAMESPACE).put(SESSIONS_KEY, sessions);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        // Session creation happens in postProcessTestInstance
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterEach(ExtensionContext context) {
        List<SmartCardSession> sessions =
                (List<SmartCardSession>) context.getStore(NAMESPACE).get(SESSIONS_KEY);
        if (sessions != null) {
            for (SmartCardSession session : sessions) {
                session.close();
            }
        }
    }

    private SmartCardSession createSession(SmartCard annotation) {
        SmartCardSession session;
        if (annotation.mode() == Mode.CONTAINER) {
            session = createContainerSession(annotation);
        } else {
            session = new EmbeddedSession(false, annotation.persistentMemory());
        }
        if (annotation.log() || "true".equalsIgnoreCase(System.getProperty("jcx.log"))) {
            session = LoggingSession.wrap(session, true);
        }
        return session;
    }

    private SmartCardSession createContainerSession(SmartCard annotation) {
        try {
            Class.forName("org.testcontainers.containers.GenericContainer");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Container mode requires Testcontainers on the classpath. " +
                    "Add org.testcontainers:testcontainers dependency.");
        }
        // Use reflection to avoid compile-time dependency on container module
        try {
            Class<?> factory = Class.forName(
                    "name.velikodniy.jcexpress.container.ContainerSessionFactory");
            Method create = factory.getMethod("create", SmartCard.class);
            return (SmartCardSession) create.invoke(null, annotation);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create container session", e);
        }
    }
}
