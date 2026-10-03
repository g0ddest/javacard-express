package name.velikodniy.jcexpress.embedded;

import java.util.List;
import java.util.Set;

/**
 * Explains an applet installation that failed because the applet code needs classes that the test class path
 * lacks. jCardSim reports such a failure only as {@code SystemException} (reason {@code 0x6444}).
 */
final class MissingClasses {

    private MissingClasses() {
    }

    /**
     * Describes the missing classes and what to do about them.
     *
     * @param appletClass the applet class that was installed
     * @param missing     binary names of the classes that were not found
     * @return the message
     */
    static String describe(String appletClass, Set<String> missing) {
        List<String> api = missing.stream().filter(MissingClasses::isJavaCardApi).toList();
        List<String> library = missing.stream().filter(name -> !isJavaCardApi(name)).toList();
        StringBuilder text = new StringBuilder("Cannot install ").append(appletClass).append(':');
        if (!library.isEmpty()) {
            text.append(" the applet code needs ").append(String.join(", ", library))
                    .append(library.size() == 1 ? ", which is" : ", which are")
                    .append(" not on the test class path. Dependencies with scope provided are not transitive in"
                            + " Maven: declare the library that contains ")
                    .append(library.size() == 1 ? "it" : "them")
                    .append(" as a test dependency of this module.");
        }
        if (!api.isEmpty()) {
            text.append(" The applet code uses ").append(String.join(", ", api))
                    .append(" of the Java Card API, which the simulator (jCardSim) does not implement; test this"
                            + " code on a card.");
        }
        return text.append(" (jCardSim reported only a SystemException.)").toString();
    }

    private static boolean isJavaCardApi(String name) {
        return name.startsWith("javacard.") || name.startsWith("javacardx.");
    }
}
