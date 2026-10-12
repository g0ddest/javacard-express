package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The service names that Global Services applications registered uniquely in the GlobalPlatform Registry
 * (GlobalPlatform Card Specification v2.3.1 section 8.1.1; GlobalPlatform Card API 1.8,
 * GPRegistryEntry.registerService and GPSystem.getService). A service name is two bytes: the family and the
 * service within it, '00' for the whole family.
 */
final class GlobalServices {

    /** Registered names in registration order, each with the application that registered it. */
    private final Map<Integer, AID> registered = new LinkedHashMap<>();

    /**
     * Registers a service name for an application.
     *
     * @param application the Global Services application
     * @param serviceName the service name
     * @return false when the name is taken: it is registered, a service of its family is registered and it names
     *         the whole family, or its whole family is registered
     */
    boolean register(AID application, short serviceName) {
        int name = serviceName & 0xFFFF;
        boolean taken = registered.keySet().stream().anyMatch(other -> other == name
                || (family(other) == family(name) && (wholeFamily(other) || wholeFamily(name))));
        if (taken) {
            return false;
        }
        registered.put(name, application);
        return true;
    }

    /**
     * Removes a service name an application registered.
     *
     * @param application the application that asks
     * @param serviceName the service name
     * @return false when the application did not register this name
     */
    boolean deregister(AID application, short serviceName) {
        return registered.remove(serviceName & 0xFFFF, application);
    }

    /** Forgets the names of a deleted application. */
    void removeAll(AID application) {
        registered.values().removeIf(application::equals);
    }

    /**
     * Finds the application that provides a service: the one that registered the name; for a name of a whole
     * family the first that registered a service of the family; for a service the one that registered its whole
     * family (GPSystem.getService with a null AID, GPSystem.getCVM).
     *
     * @param serviceName the requested service name
     * @return the application, or empty when nobody registered such a service
     */
    Optional<AID> provider(short serviceName) {
        int name = serviceName & 0xFFFF;
        AID exact = registered.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        return registered.entrySet().stream()
                .filter(entry -> family(entry.getKey()) == family(name)
                        && (wholeFamily(name) || wholeFamily(entry.getKey())))
                .map(Map.Entry::getValue).findFirst();
    }

    private static int family(int name) {
        return name >> 8;
    }

    private static boolean wholeFamily(int name) {
        return (name & 0xFF) == 0;
    }
}
