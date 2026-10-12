package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an application that receives personalization data through its Security Domain: after
 * INSTALL [for personalization] the Security Domain forwards the STORE DATA commands it unwrapped to this
 * interface (GlobalPlatform Card Specification v2.3.1 section 7.3.2). Part of the package since version 1.0.
 *
 * <p>The simulated cards do not forward STORE DATA yet; an applet can implement the interface, and nothing
 * calls it.</p>
 */
public interface Application extends Shareable {

    /**
     * Receives one forwarded STORE DATA command.
     *
     * @param baBuffer the buffer with the command
     * @param sOffset  the offset of the class byte
     * @param sLength  the length of the command (header and data field)
     */
    void processData(byte[] baBuffer, short sOffset, short sLength);
}
