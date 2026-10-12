package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an application that wants to hear how a remote administration session over HTTP ended
 * (GlobalPlatform Card Specification v2.2 Amendment B). Part of the package since version 1.3; version 1.8
 * deprecates it in favour of {@link AdminSessionListener}. The method name is spelled as the API spells it.
 *
 * <p>The simulated cards never call it.</p>
 *
 * @deprecated version 1.8 of the package replaces the interface with {@link AdminSessionListener}.
 */
@Deprecated
public interface HTTPReportListener extends Shareable {

    /** Report: the session ended successfully. */
    short HTTP_SESSION_NO_ERROR = (short) 0x0001;

    /** Report: the session failed. */
    short HTTP_SESSION_ERROR = (short) 0x8001;

    /**
     * Receives the result of a session.
     *
     * @param status one of the report constants
     */
    void httpAdministationSessionReport(short status);
}
