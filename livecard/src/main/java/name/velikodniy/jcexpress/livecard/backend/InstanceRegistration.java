package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Whether an applet registers its instances with the instance AID of INSTALL [for install]. GlobalPlatform requires
 * an applet to pass that AID, from its install parameters, to {@code register(byte[], short, byte)} (GPCS v2.3.1
 * A.1, "Installation"); {@code register()} without arguments assigns the applet's own AID, its Java Card platform name
 * (Java Card API, {@code Applet.register()}). jCardSim registers an instance under the AID of its installation either
 * way, so an applet that registers without arguments works on the simulators under any instance AID, while a card may
 * refuse such an instance or register it under the applet's own AID.
 */
final class InstanceRegistration {

    private static final String REGISTER = "register";
    private static final String NO_ARGUMENTS = "()V";

    private InstanceRegistration() {
    }

    /**
     * Returns a note for an instance whose applet registers without arguments while the instance AID is not the
     * applet's own AID (its module AID in the CAP file of the run).
     *
     * @param applet the instance to install
     * @return the note, or empty if the instance registers as a card expects
     */
    static Optional<String> note(AppletDeclaration applet) {
        if (applet.instanceAid().equals(applet.moduleAid()) || !callsRegisterWithoutArguments(applet.appletClass())) {
            return Optional.empty();
        }
        String own = applet.moduleAid().toHex();
        return Optional.of(applet.appletClass().getName() + " calls register() without arguments, which registers an"
                + " instance under the applet's own AID " + own + " (Java Card API, Applet.register()); a GlobalPlatform"
                + " card needs register(bArray, (short) (bOffset + 1), bArray[bOffset]) with the instance AID of"
                + " INSTALL [for install] (GPCS v2.3.1 A.1, Installation). jCardSim accepts the instance AID "
                + applet.instanceAid().toHex() + " either way; a card may refuse the install or register the instance"
                + " under " + own);
    }

    /**
     * Returns whether an applet class, or one of its superclasses outside the Java Card API, calls
     * {@code register()} without arguments.
     *
     * @param appletClass the applet class
     * @return true if a class file of the chain invokes {@code register()V}
     */
    static boolean callsRegisterWithoutArguments(Class<?> appletClass) {
        for (Class<?> type = appletClass; type != null && !type.getName().startsWith("javacard.");
             type = type.getSuperclass()) {
            byte[] classFile = classFile(type);
            if (classFile.length > 0 && ClassReferences.scan(List.of(classFile)).stream().anyMatch(
                    reference -> REGISTER.equals(reference.name()) && NO_ARGUMENTS.equals(reference.descriptor()))) {
                return true;
            }
        }
        return false;
    }

    /** The class file of a class from its loader, empty if the loader has none. */
    private static byte[] classFile(Class<?> type) {
        String name = type.getName();
        try (InputStream in = type.getResourceAsStream(name.substring(name.lastIndexOf('.') + 1) + ".class")) {
            return in == null ? new byte[0] : in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
