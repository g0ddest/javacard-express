package name.velikodniy.jcexpress.idea;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;

import java.util.Map;
import java.util.Optional;

/**
 * Where the processor options of a module's main compilation come from, besides the IDE's own compiler settings.
 * The Maven integration is one: the build extension writes the check's options into the compiler plugin's
 * configuration, which the IDE's annotation-processing profile does not always carry over.
 */
public interface CheckOptionsSource {

    /** The extension point of the sources. */
    ExtensionPointName<CheckOptionsSource> EP = ExtensionPointName.create("name.velikodniy.jcexpress.checkOptions");

    /**
     * Returns the processor options the build passes to the main compilation of a module.
     *
     * @param module the module
     * @return the options by key, or empty when this source does not know the module
     */
    Optional<Map<String, String>> options(Module module);
}
