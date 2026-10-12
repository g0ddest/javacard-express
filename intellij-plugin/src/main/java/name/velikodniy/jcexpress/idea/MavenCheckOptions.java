package name.velikodniy.jcexpress.idea;

import com.intellij.openapi.module.Module;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The check's options of a Maven module: the {@code -A} arguments of the compiler plugin's {@code compile} goal, as
 * the build passes them (the build extension adds them there).
 */
final class MavenCheckOptions implements CheckOptionsSource {

    @Override
    public Optional<Map<String, String>> options(Module module) {
        MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(module.getProject());
        MavenProject project = manager == null ? null : manager.findProject(module);
        Element compile = project == null ? null
                : project.getPluginGoalConfiguration("org.apache.maven.plugins", "maven-compiler-plugin", "compile");
        if (compile == null) {
            return Optional.empty();
        }
        Map<String, String> options = new LinkedHashMap<>();
        Element arguments = compile.getChild("compilerArgs");
        if (arguments != null) {
            ProcessorOptions.addFrom(arguments.getChildren().stream().map(Element::getTextTrim).toList(), options);
        }
        String single = compile.getChildTextTrim("compilerArgument");
        if (single != null) {
            ProcessorOptions.addFrom(List.of(single.split("\\s+")), options);
        }
        return Optional.of(options);
    }
}
