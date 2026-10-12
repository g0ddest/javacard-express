package name.velikodniy.jcexpress.javac;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses the packages to check as the Maven plugin chooses the package to convert: the packages named by
 * {@value CheckOptions#PACKAGE_NAME}; otherwise the one package of the class output; with classes in several packages,
 * which the conversion only accepts with a package name, the packages that have an applet (a non-abstract subclass of
 * {@code javacard.framework.Applet}, JCVM 3.1 §6.6). Classes in other packages, such as host code next to the applet,
 * are not checked; neither is the unnamed package, which cannot be converted (§4.1.3).
 */
final class CheckedPackages {

    private static final String APPLET = "javacard.framework.Applet";

    private final List<String> configured;
    private final ClassOutput output;
    private final Elements elements;
    private final Types types;

    CheckedPackages(List<String> configured, ClassOutput output, Elements elements, Types types) {
        this.configured = configured;
        this.output = output;
        this.elements = elements;
        this.types = types;
    }

    /**
     * Selects the packages to check among those this compilation wrote classes in.
     *
     * @param compiled the classes of this compilation
     * @return package names in dot notation
     */
    Set<String> select(Collection<TypeElement> compiled) {
        Set<String> packages = new LinkedHashSet<>();
        compiled.forEach(type -> packages.add(packageOf(type)));
        packages.remove("");
        if (!configured.isEmpty()) {
            packages.retainAll(configured);
            return packages;
        }
        Set<String> inOutput = new LinkedHashSet<>(output.packages());
        compiled.forEach(type -> inOutput.add(packageOf(type)));
        if (inOutput.size() <= 1) {
            return packages;
        }
        packages.removeIf(pkg -> !hasApplet(pkg, compiled));
        return packages;
    }

    private boolean hasApplet(String packageName, Collection<TypeElement> compiled) {
        TypeElement applet = elements.getTypeElement(APPLET);
        if (applet == null) {
            return false;
        }
        if (compiled.stream().anyMatch(type -> packageOf(type).equals(packageName) && isApplet(type, applet))) {
            return true;
        }
        return output.topLevelClasses(packageName).stream().map(elements::getTypeElement)
                .anyMatch(type -> type != null && isApplet(type, applet));
    }

    private boolean isApplet(TypeElement type, TypeElement applet) {
        return type.getKind() == ElementKind.CLASS && !type.getModifiers().contains(Modifier.ABSTRACT)
                && types.isSubtype(types.erasure(type.asType()), types.erasure(applet.asType()));
    }

    private String packageOf(TypeElement type) {
        return elements.getPackageOf(type).getQualifiedName().toString();
    }
}
