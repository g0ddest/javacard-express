package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@link InstallApplet} declarations into {@link AppletDeclaration}s with the AIDs of a run.
 */
final class AppletPlan {

    private AppletPlan() {
    }

    /**
     * Returns whether a test class (or a class enclosing it) uses the declarative model.
     *
     * @param testClass the test class
     * @return true for {@link JavaCardTest} classes and classes with {@link InstallApplet}
     */
    static boolean declares(Class<?> testClass) {
        for (Class<?> type = testClass; type != null; type = type.getEnclosingClass()) {
            if (AnnotationSupport.isAnnotated(type, JavaCardTest.class)
                    || !AnnotationSupport.findRepeatableAnnotations(type, InstallApplet.class).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the applets a class declares itself (not those of enclosing classes).
     *
     * @param type the class
     * @param aids the run's AIDs
     * @return the declarations, in declaration order
     */
    static List<AppletDeclaration> ofClass(Class<?> type, AidScheme aids) {
        return of(type, aids, false);
    }

    /**
     * Returns the applets a test method declares; they live for the test.
     *
     * @param method the test method
     * @param aids   the run's AIDs
     * @return the declarations, in declaration order
     */
    static List<AppletDeclaration> ofMethod(Method method, AidScheme aids) {
        return of(method, aids, true);
    }

    /**
     * Returns every applet a test class run declares: the class, its methods and its {@code @Nested} classes,
     * each instance AID once.
     *
     * @param topLevel the top-level test class
     * @param aids     the run's AIDs
     * @return the declarations
     */
    static List<AppletDeclaration> ofRun(Class<?> topLevel, AidScheme aids) {
        Map<String, AppletDeclaration> all = new LinkedHashMap<>();
        collect(topLevel, aids, all);
        return List.copyOf(all.values());
    }

    /**
     * Returns the applet classes a test class run declares (the class, its methods and its {@code @Nested}
     * classes), without AIDs: the backend chooses the run's AID prefix with them.
     *
     * @param topLevel the top-level test class
     * @return the applet classes, each once, in declaration order
     */
    static List<Class<? extends Applet>> declaredClasses(Class<?> topLevel) {
        Set<Class<? extends Applet>> classes = new LinkedHashSet<>();
        collectClasses(topLevel, classes);
        return List.copyOf(classes);
    }

    private static void collectClasses(Class<?> type, Set<Class<? extends Applet>> classes) {
        AnnotationSupport.findRepeatableAnnotations(type, InstallApplet.class).forEach(a -> classes.add(a.value()));
        for (Method method : ReflectionSupport.findMethods(type, method -> true, HierarchyTraversalMode.TOP_DOWN)) {
            AnnotationSupport.findRepeatableAnnotations(method, InstallApplet.class)
                    .forEach(a -> classes.add(a.value()));
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (AnnotationSupport.isAnnotated(nested, Nested.class)) {
                collectClasses(nested, classes);
            }
        }
    }

    private static void collect(Class<?> type, AidScheme aids, Map<String, AppletDeclaration> all) {
        ofClass(type, aids).forEach(applet -> all.putIfAbsent(key(applet), applet));
        for (Method method : ReflectionSupport.findMethods(type, method -> true, HierarchyTraversalMode.TOP_DOWN)) {
            ofMethod(method, aids).forEach(applet -> all.putIfAbsent(key(applet), applet));
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (AnnotationSupport.isAnnotated(nested, Nested.class)) {
                collect(nested, aids, all);
            }
        }
    }

    private static String key(AppletDeclaration applet) {
        return applet.appletClass().getName() + "@" + applet.instanceAid().toHex();
    }

    private static List<AppletDeclaration> of(AnnotatedElement element, AidScheme aids, boolean perTest) {
        List<AppletDeclaration> declared = new ArrayList<>();
        for (InstallApplet annotation : AnnotationSupport.findRepeatableAnnotations(element, InstallApplet.class)) {
            declared.add(declaration(annotation, element, aids, perTest));
        }
        return declared;
    }

    private static AppletDeclaration declaration(InstallApplet annotation, AnnotatedElement element, AidScheme aids,
                                                 boolean perTest) {
        try {
            AID instance = annotation.aid().isBlank() ? null : aids.aid(annotation.aid());
            byte[] parameters = Hex.decode(annotation.params());
            Isolation isolation = perTest ? Isolation.PER_TEST : annotation.isolation();
            return AppletDeclaration.of(aids, annotation.value(), instance, parameters, isolation);
        } catch (IllegalArgumentException e) {
            throw new ExtensionConfigurationException("Invalid @InstallApplet(" + annotation.value().getSimpleName()
                    + ") on " + element + ": " + e.getMessage(), e);
        }
    }
}
