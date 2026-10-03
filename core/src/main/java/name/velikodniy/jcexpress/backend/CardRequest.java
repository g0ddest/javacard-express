package name.velikodniy.jcexpress.backend;

import javacard.framework.Applet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * What a backend gets to open a card for a test class run.
 *
 * @param testClass the top-level test class the card is for
 * @param settings  the configuration of the run: JUnit configuration parameters and system properties
 *                  ({@code jcx.*}), looked up by key
 * @param applets   the applet classes the run declares ({@code @InstallApplet} on the class, its methods and its
 *                  nested classes), in declaration order
 */
public record CardRequest(Class<?> testClass, Function<String, Optional<String>> settings,
                          List<Class<? extends Applet>> applets) {

    /**
     * Copies the applet classes.
     *
     * @param testClass the top-level test class
     * @param settings  the configuration of the run
     * @param applets   the applet classes the run declares
     */
    public CardRequest {
        applets = List.copyOf(applets);
    }

    /**
     * Creates a request for a run that declares no applets.
     *
     * @param testClass the top-level test class
     * @param settings  the configuration of the run
     */
    public CardRequest(Class<?> testClass, Function<String, Optional<String>> settings) {
        this(testClass, settings, List.of());
    }

    /**
     * Returns a setting of the run.
     *
     * @param key the key, for example {@value AidScheme#PREFIX_SETTING}
     * @return the value, if set
     */
    public Optional<String> setting(String key) {
        return settings.apply(key).map(String::strip).filter(value -> !value.isEmpty());
    }

    /**
     * Returns the build descriptors of the packages of the declared applets, one per package that the Maven plugin
     * built, in declaration order.
     *
     * @return the descriptors
     * @throws IllegalStateException if a descriptor is invalid
     */
    public List<BuildDescriptor> buildDescriptors() {
        Set<String> packages = new LinkedHashSet<>();
        List<BuildDescriptor> descriptors = new ArrayList<>();
        for (Class<? extends Applet> applet : applets) {
            if (packages.add(applet.getPackageName())) {
                BuildDescriptor.of(applet).ifPresent(descriptors::add);
            }
        }
        return descriptors;
    }

    /**
     * Returns the AID scheme of the run: the prefix from {@value AidScheme#PREFIX_SETTING}; without it the prefix of
     * the project that built the first declared applet with a build descriptor ({@link AidScheme#forProject}), or
     * {@value AidScheme#DEFAULT_PREFIX} when no declared applet has one.
     *
     * @return the scheme
     */
    public AidScheme aidScheme() {
        Optional<String> prefix = setting(AidScheme.PREFIX_SETTING);
        if (prefix.isPresent()) {
            return AidScheme.of(prefix.get());
        }
        return buildDescriptors().stream().findFirst().map(descriptor -> AidScheme.forProject(descriptor.project()))
                .orElseGet(() -> AidScheme.of(AidScheme.DEFAULT_PREFIX));
    }
}
