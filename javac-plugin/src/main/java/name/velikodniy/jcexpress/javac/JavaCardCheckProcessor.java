package name.velikodniy.jcexpress.javac;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Optional;
import java.util.Set;

/**
 * Checks Java Card applet code while javac compiles it. Code outside the Java Card language subset (JCVM 3.1 §2.2),
 * and references to API the target platform does not have (§4.5.2), become compile errors at their source lines, in
 * a Maven build and in an IDE build that runs javac, before any test runs on a simulator that would accept them.
 *
 * <p>The rules and messages are the converter's ({@code PackageCheck}), applied to the class files javac has just
 * written, so the check and the conversion cannot disagree. It runs inside javac as an annotation processor that
 * claims no annotation: on the annotation processor path javac finds it without a further option (an
 * {@code -Xplugin} plugin could not read the class files through javac's public API), and it waits for the end of
 * the compilation through a {@link com.sun.source.util.TaskListener}. The options are in {@link CheckOptions}. A
 * compilation whose class output is a test output is not checked, nor are packages the conversion would not
 * convert.</p>
 *
 * <p>The check never stops javac by failing itself: where it cannot run (another compiler, class files kept in
 * memory) it says so in a note, and what it cannot read or check it reports in a warning. The conversion still
 * checks everything.</p>
 */
@SupportedAnnotationTypes("*")
public final class JavaCardCheckProcessor extends AbstractProcessor {

    /** Marks the diagnostics of the check; kept here so that a failure to load the converter can still be reported. */
    static final String PREFIX = "Java Card: ";

    /**
     * Creates the processor; javac does.
     */
    public JavaCardCheckProcessor() {
        super();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return CheckOptions.NAMES;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        try {
            start(environment);
        } catch (RuntimeException | LinkageError e) {
            // nothing here may need the converter's classes: they may be what failed to load
            environment.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    PREFIX + "the check did not run: " + describe(e));
        }
    }

    /** No annotation is claimed: other processors see every annotation. */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        return false;
    }

    /**
     * Reads the options first, so that a check that is turned off touches nothing else, then follows the compilation
     * through javac's environment.
     */
    private static void start(ProcessingEnvironment given) {
        Messager messager = given.getMessager();
        CheckOptions options;
        try {
            options = CheckOptions.parse(given.getOptions());
        } catch (IllegalArgumentException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, PREFIX + e.getMessage());
            return;
        }
        if (!options.enabled()) {
            return;
        }
        Optional<ProcessingEnvironment> javac = JavacEnvironment.of(given);
        if (javac.isEmpty()) {
            note(messager, "the processing environment is " + given.getClass().getName() + ", not javac's");
            return;
        }
        ProcessingEnvironment environment = javac.get();
        Optional<ClassOutput> output = ClassOutput.of(environment.getFiler());
        if (output.isEmpty()) {
            note(messager, "javac does not tell where it writes the class files");
        } else if (!output.get().isTestOutput()) {
            JavacTask.instance(environment).addTaskListener(new CompilationListener(options, output.get(),
                    environment.getMessager(), Trees.instance(environment), environment.getElementUtils(),
                    environment.getTypeUtils()));
        }
    }

    private static void note(Messager messager, String reason) {
        messager.printMessage(Diagnostic.Kind.NOTE, PREFIX + "the check did not run: " + reason);
    }

    /**
     * Describes a failure of the check for a diagnostic.
     *
     * @param failure the failure
     * @return its type and message
     */
    static String describe(Throwable failure) {
        return failure.getClass().getSimpleName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    }
}
