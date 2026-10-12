package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an application that wants to hear how a remote administration session ended (GlobalPlatform
 * Card Specification Amendments B and M). Part of the package since version 1.8.
 *
 * <p>The simulated cards never call it.</p>
 */
public interface AdminSessionListener extends Shareable {

    /** The parameter of {@code Applet.getShareableInterfaceObject} that asks for this listener. */
    byte SERVICE_ADMIN_SESSION_REPORT = (byte) 0x01;

    /** Report: the session ended successfully. */
    short ADMIN_SESSION_NO_ERROR = (short) 0x0001;

    /** Report: the session failed. */
    short ADMIN_SESSION_ERROR = (short) 0x8001;

    /**
     * Receives the result of a session.
     *
     * @param status one of the report constants
     */
    void administrationSessionReport(short status);
}
