package org.globalplatform;

import javacard.framework.Shareable;

/**
 * A service that lets an application ask for an administration session over HTTP with its remote server
 * (GlobalPlatform Amendment B). It is offered as a Global Service of the family
 * {@link GPSystem#FAMILY_HTTP_ADMINISTRATION}.
 *
 * <p>Since version 1.3 of the package.</p>
 *
 * @deprecated Deprecated in version 1.8 of the package; {@link AdministrationSession} replaces it.
 */
@Deprecated
public interface HTTPAdministration extends Shareable {

    /**
     * Asks for a new administration session; the Security Domain of the calling application secures the
     * connection. An application that implements {@link HTTPReportListener} is told the outcome. Failures are
     * reported as an {@code ISOException}: '6A80' for malformed parameters, '6985' when the request cannot be
     * carried out.
     *
     * <p>Since version 1.3 of the package.</p>
     *
     * @param triggeringParameters the array that holds the parameters that trigger the session
     * @param offset               offset of the parameters
     * @param length               length of the parameters
     * @deprecated Deprecated in version 1.8 of the package;
     *             {@link AdministrationSession#requestAdministrationSession(byte[], short, short)} replaces it.
     */
    @Deprecated
    void requestHTTPAdministrationSession(byte[] triggeringParameters, short offset, short length);
}
