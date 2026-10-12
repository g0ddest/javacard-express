package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Like {@link Application}, for an application that also answers the forwarded STORE DATA commands with
 * response data (GlobalPlatform Card Specification v2.3.1 section 7.3.2). Part of the package since version 1.2.
 *
 * <p>The simulated cards do not forward STORE DATA yet; an applet can implement the interface, and nothing
 * calls it.</p>
 */
public interface Personalization extends Shareable {

    /**
     * Receives one forwarded STORE DATA command and writes the response data.
     *
     * @param inBuffer  the buffer with the command
     * @param inOffset  the offset of the class byte
     * @param inLength  the length of the command (header and data field)
     * @param outBuffer the buffer for the response data
     * @param outOffset where the response data start
     * @return the number of bytes written to {@code outBuffer}
     */
    short processData(byte[] inBuffer, short inOffset, short inLength, byte[] outBuffer, short outOffset);
}
