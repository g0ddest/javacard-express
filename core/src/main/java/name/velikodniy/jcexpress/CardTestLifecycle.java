package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.CardBackend;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ModifierSupport;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The declarative model of {@link JavaCardTest} classes, run by {@link JavaCardExtension}: one card per test class
 * run, {@link InstallApplet} instances installed and deleted per scope, the first PER_CLASS applet of a class
 * selected after its installs, the nearest applet selected before each test unless it still is, the card injected
 * into parameters and {@link SmartCard} fields, and the APDU transcript of a failure (of a test, a lifecycle method
 * or one of this class's steps, such as a declared install) attached to it; a failed test's transcript is also
 * published as a file. With {@value JavaCardExtension#LOG_PARAMETER}{@code =true} the card's history is printed as
 * it is recorded, after a title line per class and test.
 */
final class CardTestLifecycle {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(CardTestLifecycle.class);
    private static final String CARD = "card";
    private static final String MARK = "transcript-start";

    /** Number of history entries a failed test shows in its failure. */
    static final int FAILURE_ENTRIES = 40;
    /** The line of a failure transcript that says how many earlier entries it leaves out. */
    private static final Pattern NOT_SHOWN = Pattern.compile("(?m)^# \\d+ earlier entr(?:y|ies) not shown$");

    private CardTestLifecycle() {
    }

    /**
     * Returns whether a context belongs to the declarative model.
     *
     * @param context a class or method context
     * @return true for {@link JavaCardTest} classes and classes with {@link InstallApplet}
     */
    static boolean applies(ExtensionContext context) {
        return context.getTestClass().map(AppletPlan::declares).orElse(false);
    }

    static void beforeAll(ExtensionContext context) {
        Class<?> type = context.getRequiredTestClass();
        printTitle(context, () -> classTitle(context));
        ManagedCard card = card(context);
        context.getStore(NAMESPACE).put(MARK, card.fullHistory().position());
        card.openScope("class " + type.getSimpleName());
        withTranscript(context, () -> {
            List<AppletDeclaration> perClass = AppletPlan.ofClass(type, card.card().aids()).stream()
                    .filter(applet -> applet.isolation() == Isolation.PER_CLASS).toList();
            perClass.forEach(card::install);
            card.historyStartsNow();
            if (!perClass.isEmpty()) {
                card.selectForClass(perClass.getFirst());
            }
            injectFields(card, type, null);
            if (context.getTestInstanceLifecycle().orElse(Lifecycle.PER_METHOD) == Lifecycle.PER_CLASS) {
                context.getTestInstances().ifPresent(instances -> instances.getAllInstances()
                        .forEach(instance -> injectFields(card, instance.getClass(), instance)));
            }
        });
    }

    static void afterAll(ExtensionContext context) {
        existingCard(context).ifPresent(card -> withTranscript(context, card::closeScope));
    }

    static void beforeEach(ExtensionContext context) {
        printTitle(context, () -> classTitle(context) + " > " + testName(context));
        ManagedCard card = card(context);
        context.getStore(NAMESPACE).put(MARK, card.fullHistory().position());
        card.openScope("test " + context.getRequiredTestMethod().getName() + "()");
        withTranscript(context, () -> {
            AidScheme aids = card.card().aids();
            List<Class<?>> chain = classChain(context);
            installForTest(context, card, chain, aids);
            card.historyStartsNow();
            nearest(context, chain, aids).ifPresent(applet -> card.selectUnlessSelected(applet.instanceAid()));
            for (Object instance : context.getRequiredTestInstances().getAllInstances()) {
                injectFields(card, instance.getClass(), instance);
            }
        });
    }

    /** Installs the PER_TEST instances of the test's classes, outermost first, then those of the method. */
    private static void installForTest(ExtensionContext context, ManagedCard card, List<Class<?>> chain,
                                       AidScheme aids) {
        for (Class<?> type : chain) {
            for (AppletDeclaration applet : AppletPlan.ofClass(type, aids)) {
                if (applet.isolation() == Isolation.PER_TEST) {
                    card.install(applet);
                }
            }
        }
        AppletPlan.ofMethod(context.getRequiredTestMethod(), aids).forEach(card::install);
    }

    /** Notes in the card's history where the test method starts. */
    static void beforeTestExecution(ExtensionContext context) {
        existingCard(context).ifPresent(card -> card.history().note("test body: " + testName(context)));
    }

    static void afterEach(ExtensionContext context) {
        Optional<ManagedCard> card = existingCard(context);
        if (card.isEmpty()) {
            return;
        }
        try {
            withTranscript(context, card.get()::closeScope);
        } finally {
            if (context.getExecutionException().isPresent()) {
                publishTranscript(context, card.get());
            }
        }
    }

    /**
     * Attaches the last exchanges since the test (or, for a class's lifecycle methods, the class) started to a
     * failure, once; when the run installs no applet, the transcript starts with a hint.
     *
     * @param context   the test's or class's context
     * @param throwable the failure
     */
    static void attachTranscript(ExtensionContext context, Throwable throwable) {
        if (Arrays.stream(throwable.getSuppressed()).anyMatch(CardTranscript.class::isInstance)) {
            return;
        }
        existingCard(context).ifPresent(card -> throwable.addSuppressed(new CardTranscript(scope(context),
                pointToFile(context, transcript(context, card, FAILURE_ENTRIES)))));
    }

    /**
     * Adds to the shortened transcript of a failed test where the whole one is: the test publishes it as the file
     * {@code apdu-transcript.txt} ({@link #publishTranscript}). Lifecycle methods publish no file.
     */
    private static String pointToFile(ExtensionContext context, String transcript) {
        Matcher notShown = NOT_SHOWN.matcher(transcript);
        if (context.getTestMethod().isEmpty() || !notShown.find()) {
            return transcript;
        }
        return transcript.substring(0, notShown.end()) + "\n" + TranscriptFormat.note("the test's whole transcript"
                + " is the file apdu-transcript.txt among its file entries (with Maven Surefire below"
                + " target/junit-jupiter/)") + transcript.substring(notShown.end());
    }

    /** Runs a step of the extension's own callbacks; a failure (a declared install) gets the transcript. */
    private static void withTranscript(ExtensionContext context, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            attachTranscript(context, e);
            throw e;
        }
    }

    /** The last entries since the mark of a context, after the hint when the run has no applet at all. */
    private static String transcript(ExtensionContext context, ManagedCard card, int maxEntries) {
        String exchanges = card.fullHistory().transcriptSince(mark(context), maxEntries);
        if (!card.nothingDeclared()) {
            return exchanges;
        }
        return TranscriptFormat.note("no applet is installed or selected: "
                + context.getRequiredTestClass().getSimpleName() + " declares no @InstallApplet and the test"
                + " installed none; declare one with @InstallApplet(MyApplet.class) on the class or the test method,"
                + " or install one with card.install(MyApplet.class)") + "\n" + exchanges;
    }

    /** What a transcript covers: "the test" or "the class CounterTest". */
    private static String scope(ExtensionContext context) {
        return context.getTestMethod().isPresent() ? "the test"
                : "the class " + context.getRequiredTestClass().getSimpleName();
    }

    static boolean supports(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == SmartCardSession.class
                || Backends.backend(Backends.configured(context)).parameterTypes().contains(type);
    }

    static Object resolve(ParameterContext parameter, ExtensionContext context) {
        parameter.findAnnotation(SmartCard.class).ifPresent(annotation -> checkAnnotation(annotation, "parameter "
                + parameter.getIndex() + " of " + parameter.getDeclaringExecutable()));
        ManagedCard card = card(context);
        Class<?> type = parameter.getParameter().getType();
        if (type == SmartCardSession.class) {
            return card;
        }
        return card.card().resolve(type).orElseThrow(() -> new ParameterResolutionException("The "
                + Backends.name(card.card().mode()) + " backend provides no " + type.getName() + " for "
                + parameter.getParameter()));
    }

    // === the card of the run ===

    private static ManagedCard card(ExtensionContext context) {
        ExtensionContext top = topLevel(context);
        return top.getStore(NAMESPACE).getOrComputeIfAbsent(CARD, key -> open(top), CardHolder.class).card();
    }

    private static Optional<ManagedCard> existingCard(ExtensionContext context) {
        return Optional.ofNullable(topLevel(context).getStore(NAMESPACE).get(CARD, CardHolder.class))
                .map(CardHolder::card);
    }

    private static CardHolder open(ExtensionContext top) {
        Mode mode = Backends.configured(top);
        CardBackend backend = Backends.backend(mode);
        Class<?> topClass = top.getRequiredTestClass();
        TestCard card = backend.open(new CardRequest(topClass, top::getConfigurationParameter,
                AppletPlan.declaredClasses(topClass)));
        try {
            ManagedCard managed = new ManagedCard(card, AppletPlan.ofRun(topClass, card.aids()));
            if (JavaCardExtension.logEnabled(top)) {
                managed.fullHistory().printTo(LoggingSession::print);
            }
            return new CardHolder(managed);
        } catch (RuntimeException e) {
            card.close();
            throw e;
        }
    }

    /** The context of the outermost test class of a context (a nested class's run belongs to it). */
    private static ExtensionContext topLevel(ExtensionContext context) {
        ExtensionContext current = context;
        while (current.getParent().flatMap(ExtensionContext::getTestClass).isPresent()) {
            current = current.getParent().orElseThrow();
        }
        return current;
    }

    private static List<Class<?>> classChain(ExtensionContext context) {
        List<Class<?>> chain = new ArrayList<>(context.getEnclosingTestClasses());
        chain.add(context.getRequiredTestClass());
        return chain;
    }

    /** The applet a test talks to by default: the method's first, else the nearest class's first. */
    private static Optional<AppletDeclaration> nearest(ExtensionContext context, List<Class<?>> chain,
                                                       AidScheme aids) {
        List<AppletDeclaration> method = AppletPlan.ofMethod(context.getRequiredTestMethod(), aids);
        if (!method.isEmpty()) {
            return Optional.of(method.getFirst());
        }
        List<Class<?>> innermostFirst = new ArrayList<>(chain);
        Collections.reverse(innermostFirst);
        return innermostFirst.stream().map(type -> AppletPlan.ofClass(type, aids)).filter(list -> !list.isEmpty())
                .map(List::getFirst).findFirst();
    }

    /** Prints a title line when the run logs APDUs ({@value JavaCardExtension#LOG_PARAMETER}). */
    private static void printTitle(ExtensionContext context, Supplier<String> title) {
        if (JavaCardExtension.logEnabled(context)) {
            LoggingSession.print(TranscriptFormat.title(title.get()));
        }
    }

    /** The simple names of the test class and its enclosing classes, outermost first: {@code Outer > Inner}. */
    private static String classTitle(ExtensionContext context) {
        return String.join(" > ", classChain(context).stream().map(Class::getSimpleName).toList());
    }

    /**
     * The test's method, followed by its display name unless that is JUnit's default ({@code name(Types)}): a
     * {@code @DisplayName} or a parameterized invocation ({@code [1] 42}).
     */
    private static String testName(ExtensionContext context) {
        String method = context.getRequiredTestMethod().getName();
        String display = context.getDisplayName();
        return display.startsWith(method + "(") ? method + "()" : method + "() " + display;
    }

    private static long mark(ExtensionContext context) {
        Long mark = context.getStore(NAMESPACE).get(MARK, Long.class);
        return mark == null ? 0 : mark;
    }

    private static void publishTranscript(ExtensionContext context, ManagedCard card) {
        String transcript = transcript(context, card, card.fullHistory().capacity());
        try {
            context.publishFile("apdu-transcript.txt", MediaType.TEXT_PLAIN_UTF_8,
                    path -> Files.writeString(path, transcript));
        } catch (RuntimeException e) {
            // reporting must not change the outcome of a test (no output directory configured, file system errors)
        }
    }

    // === @SmartCard fields ===

    private static void injectFields(ManagedCard card, Class<?> type, Object instance) {
        boolean statics = instance == null;
        for (Field field : AnnotationSupport.findAnnotatedFields(type, SmartCard.class,
                field -> ModifierSupport.isStatic(field) == statics, HierarchyTraversalMode.TOP_DOWN)) {
            check(field);
            try {
                field.setAccessible(true);
                field.set(instance, card);
            } catch (IllegalAccessException | RuntimeException e) {
                throw new ExtensionConfigurationException("Cannot inject @SmartCard field " + field, e);
            }
        }
    }

    private static void check(Field field) {
        if (!field.getType().isAssignableFrom(SmartCardSession.class)) {
            throw new ExtensionConfigurationException("@SmartCard field " + field + " of a @JavaCardTest class must"
                    + " be declared as SmartCardSession: the card's backend is configuration (jcx.backend)");
        }
        checkAnnotation(field.getAnnotation(SmartCard.class), "field " + field);
    }

    /** In a {@link JavaCardTest} class {@link SmartCard} only marks where the card goes; the run chooses the rest. */
    private static void checkAnnotation(SmartCard annotation, String target) {
        if (annotation.mode() != Mode.EMBEDDED || annotation.log() || !annotation.image().isEmpty()) {
            throw new ExtensionConfigurationException("@SmartCard on " + target + " of a @JavaCardTest class"
                    + " cannot set mode, image or log: the backend is chosen with -Djcx.backend, every test keeps its"
                    + " APDU history (SmartCardSession.history(), attached to failures), and -Djcx.log=true prints"
                    + " the exchanges while the tests run");
        }
    }

    /** The APDU exchanges of a failed test, attached to its failure as a suppressed exception. */
    static final class CardTranscript extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CardTranscript(String transcript) {
            this("the test", transcript);
        }

        CardTranscript(String scope, String transcript) {
            super("APDU exchanges of " + scope + " (most recent last):\n" + transcript, null, false, false);
        }
    }

    /** Store value that closes the card of a test class run once, when the class run ends. */
    @SuppressWarnings("deprecation")
    private record CardHolder(ManagedCard card) implements ExtensionContext.Store.CloseableResource, AutoCloseable {
        @Override
        public void close() {
            card.card().close();
        }
    }
}
