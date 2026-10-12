package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Lets an application ask its Security Domain for a remote administration session over HTTP (GlobalPlatform
 * Card Specification v2.2 Amendment B). Part of the package since version 1.3; version 1.8 deprecates it in
 * favour of {@link AdministrationSession}.
 *
 * <p>The simulated cards provide no such service.</p>
 *
 * @deprecated version 1.8 of the package replaces the interface with {@link AdministrationSession}.
 */
@Deprecated
public interface HTTPAdministration extends Shareable {

    /**
     * Asks for an administration session.
     *
     * @param triggeringParameters the buffer with the parameters of the session
     * @param offset               where the parameters start
     * @param length               the length of the parameters
     */
    void requestHTTPAdministrationSession(byte[] triggeringParameters, short offset, short length);
}
