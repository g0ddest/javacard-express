package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Lets an application ask its Security Domain for a remote administration session over HTTP or CoAP
 * (GlobalPlatform Card Specification Amendments B and M). Part of the package since version 1.8.
 *
 * <p>The simulated cards provide no such service.</p>
 */
public interface AdministrationSession extends Shareable {

    /**
     * Asks for an administration session.
     *
     * @param triggeringParameters the buffer with the parameters of the session
     * @param offset               where the parameters start
     * @param length               the length of the parameters
     */
    void requestAdministrationSession(byte[] triggeringParameters, short offset, short length);
}
