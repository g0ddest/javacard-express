package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an applet that accepts data which another application on the card passes on to it, typically the
 * personalization data its Security Domain received for it, and returns no data. A Security Domain uses
 * {@link Personalization} instead when the applet implements both.
 *
 * <p>Since version 1.0 of the package.</p>
 */
public interface Application extends Shareable {

    /**
     * Processes data forwarded by another application. When a Security Domain calls, the data is a whole STORE
     * DATA command in a global array: {@code sOffset} is the offset of its class byte and {@code sLength} the
     * length of header and data field. What this method throws becomes the answer to that command: the reason of
     * an {@code ISOException}, otherwise '6F00'. The applet is not the selected application while the method runs.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the data; a global array
     * @param sOffset  offset of the data in {@code baBuffer}
     * @param sLength  length of the data
     */
    void processData(byte[] baBuffer, short sOffset, short sLength);
}
