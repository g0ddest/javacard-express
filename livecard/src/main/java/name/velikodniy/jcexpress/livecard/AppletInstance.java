package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * An applet instance to create with INSTALL [for install and make selectable] (GlobalPlatform Card
 * Specification v2.3.1 11.5.2.3.2, Table 11-43). Privileges are always '00'.
 *
 * @param moduleAid   the Executable Module (applet) AID, uppercase hex
 * @param instanceAid the Application (instance) AID, uppercase hex
 * @param parameters  the application specific parameters (value of tag 'C9'), possibly empty
 */
public record AppletInstance(String moduleAid, String instanceAid, byte[] parameters) {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * Copies the parameters.
     */
    public AppletInstance {
        parameters = parameters == null ? new byte[0] : parameters.clone();
    }

    /**
     * Creates an instance of a module under the module's own AID, without parameters.
     *
     * @param module the module AID
     * @return the instance
     */
    public static AppletInstance of(AID module) {
        return new AppletInstance(module.toHex(), module.toHex(), new byte[0]);
    }

    /**
     * Sets the instance AID.
     *
     * @param instance the instance AID
     * @return a new instance description
     */
    public AppletInstance as(AID instance) {
        return new AppletInstance(moduleAid, instance.toHex(), parameters);
    }

    /**
     * Sets the install parameters (tag 'C9' value, at most the card's limit; the applet receives them as the
     * {@code La} part of its install data).
     *
     * @param installParameters the parameters
     * @return a new instance description
     */
    public AppletInstance withParameters(byte[] installParameters) {
        return new AppletInstance(moduleAid, instanceAid, installParameters);
    }

    /**
     * Returns the install parameters.
     *
     * @return a copy of the application specific parameters (value of tag 'C9'), possibly empty
     */
    @Override
    public byte[] parameters() {
        return parameters.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AppletInstance(String module, String instance, byte[] params)
                && module.equals(moduleAid) && instance.equals(instanceAid) && Arrays.equals(params, parameters);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * moduleAid.hashCode() + instanceAid.hashCode()) + Arrays.hashCode(parameters);
    }

    @Override
    public String toString() {
        return "AppletInstance[module=" + moduleAid + ", instance=" + instanceAid + ", parameters="
                + HEX.formatHex(parameters) + "]";
    }
}
