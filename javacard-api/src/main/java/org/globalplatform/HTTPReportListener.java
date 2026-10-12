package org.globalplatform;

import javacard.framework.Shareable;

/**
 * Implemented by an application that wants to learn how an HTTP administration session it asked for ended. The
 * card asks the applet's {@code getShareableInterfaceObject} for the listener with a null client AID and the
 * parameter {@link GPSystem#FAMILY_HTTP_REPORT}.
 *
 * <p>Since version 1.3 of the package.</p>
 *
 * @deprecated Deprecated in version 1.8 of the package; {@link AdminSessionListener} replaces it.
 */
@Deprecated
public interface HTTPReportListener extends Shareable {

    /**
     * Status: the session ended successfully. Since version 1.3 of the package.
     *
     * @deprecated Deprecated in version 1.8 of the package; {@link AdminSessionListener#ADMIN_SESSION_NO_ERROR}
     *             replaces it.
     */
    @Deprecated
    short HTTP_SESSION_NO_ERROR = 0x0001;
    /**
     * Status: the session failed and will not be tried again. Since version 1.3 of the package.
     *
     * @deprecated Deprecated in version 1.8 of the package; {@link AdminSessionListener#ADMIN_SESSION_ERROR}
     *             replaces it.
     */
    @Deprecated
    short HTTP_SESSION_ERROR = (short) 0x8001;

    /**
     * Tells the application that the session it asked for is over. The spelling of the name is the one of the
     * GlobalPlatform API.
     *
     * <p>Since version 1.3 of the package.</p>
     *
     * @param status {@link #HTTP_SESSION_NO_ERROR} or {@link #HTTP_SESSION_ERROR}
     * @deprecated Deprecated in version 1.8 of the package;
     *             {@link AdminSessionListener#administrationSessionReport(short)} replaces it.
     */
    @Deprecated
    void httpAdministationSessionReport(short status);
}
