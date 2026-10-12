package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an application that wants to learn how an administration session it asked for ended. The card
 * asks the applet's {@code getShareableInterfaceObject} for the listener with a null client AID and the parameter
 * {@link #SERVICE_ADMIN_SESSION_REPORT}.
 *
 * <p>Since version 1.8 of the package.</p>
 */
public interface AdminSessionListener extends Shareable {

    /**
     * The parameter with which the card asks {@code getShareableInterfaceObject} for the listener. Since version
     * 1.8 of the package.
     */
    byte SERVICE_ADMIN_SESSION_REPORT = 0x01;
    /** Status: the session ended successfully. Since version 1.8 of the package. */
    short ADMIN_SESSION_NO_ERROR = 0x0001;
    /** Status: the session failed and will not be tried again. Since version 1.8 of the package. */
    short ADMIN_SESSION_ERROR = (short) 0x8001;

    /**
     * Tells the application that the session it asked for is over.
     *
     * <p>Since version 1.8 of the package.</p>
     *
     * @param status {@link #ADMIN_SESSION_NO_ERROR} or {@link #ADMIN_SESSION_ERROR}
     */
    void administrationSessionReport(short status);
}
