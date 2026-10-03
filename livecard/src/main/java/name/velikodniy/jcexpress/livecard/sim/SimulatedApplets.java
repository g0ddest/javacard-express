package name.velikodniy.jcexpress.livecard.sim;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The applets a {@link SimulatedCard} can run: the class that implements a module (applet) AID of a loaded package,
 * and the class path entries the applet classes are loaded from (each jCardSim card of the simulated card loads
 * them itself, so static fields start fresh with every load file).
 */
public interface SimulatedApplets {

    /**
     * Finds the applet class of a module AID.
     *
     * @param moduleAid the module AID (upper-case hex)
     * @return the fully qualified class name, or empty when the module is unknown
     */
    Optional<String> className(String moduleAid);

    /**
     * Returns where the applet classes are loaded from, read when a package is loaded.
     *
     * @return directories or jars
     */
    List<Path> classPath();

    /**
     * Creates the applets from a lookup function and a class path.
     *
     * @param classNames finds the applet class name of a module AID (hex)
     * @param classPath  where the applet classes are loaded from
     * @return the applets
     */
    static SimulatedApplets of(Function<String, Optional<String>> classNames, List<Path> classPath) {
        List<Path> copy = List.copyOf(classPath);
        return new SimulatedApplets() {
            @Override
            public Optional<String> className(String moduleAid) {
                return classNames.apply(moduleAid);
            }

            @Override
            public List<Path> classPath() {
                return copy;
            }
        };
    }
}
