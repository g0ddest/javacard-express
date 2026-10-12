package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an applet that accepts data which another application on the card passes on to it, typically the
 * personalization data its Security Domain received for it, and answers with data of its own. A Security Domain
 * uses this interface rather than {@link Application} when the applet implements both.
 *
 * <p>Since version 1.2 of the package.</p>
 */
public interface Personalization extends Shareable {

    /**
     * Processes data forwarded by another application and writes the answer. When a Security Domain calls, both
     * arrays are global arrays, the input is a whole STORE DATA command ({@code inOffset} is the offset of its
     * class byte, {@code inLength} the length of header and data field) and the Security Domain returns the output
     * to the off-card entity. What this method throws becomes the answer to that command: the reason of an
     * {@code ISOException}, otherwise '6F00'. The applet is not the selected application while the method runs.
     *
     * <p>Since version 1.2 of the package.</p>
     *
     * @param inBuffer  the array that holds the input data; a global array
     * @param inOffset  offset of the input data in {@code inBuffer}
     * @param inLength  length of the input data
     * @param outBuffer the array the output data is written to; a global array
     * @param outOffset offset in {@code outBuffer} where the output data starts
     * @return the number of bytes written to {@code outBuffer}
     */
    short processData(byte[] inBuffer, short inOffset, short inLength, byte[] outBuffer, short outOffset);
}
