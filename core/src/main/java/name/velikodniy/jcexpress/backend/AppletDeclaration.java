package name.velikodniy.jcexpress.backend;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Isolation;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * An applet instance a card test needs: the applet class with the AIDs of its package, module and instance, its
 * install parameters and the lifetime of the instance. Created from {@link name.velikodniy.jcexpress.InstallApplet}
 * declarations (and from imperative installs) with the run's {@link AidScheme}.
 *
 * @param appletClass the applet class
 * @param packageAid  the AID of the applet's package (load file)
 * @param moduleAid   the AID of the applet (executable module)
 * @param instanceAid the AID of the instance
 * @param parameters  the install parameters (the applet's {@code install} receives them)
 * @param isolation   the lifetime of the instance
 */
public record AppletDeclaration(Class<? extends Applet> appletClass, AID packageAid, AID moduleAid, AID instanceAid,
                                byte[] parameters, Isolation isolation) {

    /**
     * Copies the parameters.
     *
     * @param appletClass the applet class
     * @param packageAid  the package AID
     * @param moduleAid   the module AID
     * @param instanceAid the instance AID
     * @param parameters  the install parameters (null for none)
     * @param isolation   the lifetime of the instance
     */
    public AppletDeclaration {
        parameters = parameters == null ? new byte[0] : parameters.clone();
    }

    /**
     * Declares an instance with the AIDs of a scheme.
     *
     * @param scheme      the run's AIDs
     * @param appletClass the applet class
     * @param instanceAid the instance AID (null for the module AID)
     * @param parameters  the install parameters (null for none)
     * @param isolation   the lifetime of the instance
     * @return the declaration
     */
    public static AppletDeclaration of(AidScheme scheme, Class<? extends Applet> appletClass, AID instanceAid,
                                       byte[] parameters, Isolation isolation) {
        AID module = scheme.moduleAid(appletClass);
        return new AppletDeclaration(appletClass, scheme.packageAid(appletClass.getPackageName()), module,
                instanceAid == null ? module : instanceAid, parameters, isolation);
    }

    /**
     * Returns the install parameters.
     *
     * @return a copy of the parameters
     */
    @Override
    public byte[] parameters() {
        return parameters.clone();
    }

    /**
     * Returns the Java package of the applet.
     *
     * @return the package name
     */
    public String packageName() {
        return appletClass.getPackageName();
    }

    /**
     * Returns where the applet's class file was loaded from: the output directory of the build
     * ({@code target/classes} in a Maven project) or a jar.
     *
     * @return the class path entry of the applet class
     * @throws IllegalStateException if the class has no code source
     */
    public Path classPathEntry() {
        try {
            return Path.of(appletClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("Cannot find where " + appletClass.getName() + " was loaded from", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AppletDeclaration that && that.appletClass == appletClass
                && that.packageAid.equals(packageAid) && that.moduleAid.equals(moduleAid)
                && that.instanceAid.equals(instanceAid) && Arrays.equals(that.parameters, parameters)
                && that.isolation == isolation;
    }

    @Override
    public int hashCode() {
        return 31 * instanceAid.hashCode() + appletClass.getName().hashCode();
    }

    @Override
    public String toString() {
        return appletClass.getSimpleName() + " as " + instanceAid.toHex();
    }
}
