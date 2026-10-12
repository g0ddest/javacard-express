package org.globalplatform;

/**
 * A {@link SecureChannel} whose Current Security Level the applet can change during a session. Whether the object
 * {@link GPSystem#getSecureChannel()} returns also implements this interface depends on the card; an applet finds
 * out with a cast.
 *
 * <p>Since version 1.1 of the package.</p>
 */
public interface SecureChannelx extends SecureChannel {

    /**
     * Sets the Current Security Level for the following calls of {@link SecureChannel#unwrap} and
     * {@link SecureChannel#wrap}. The level may be raised and lowered, but never below the Session Security Level.
     * Failures are reported as an {@code ISOException}: '6982' when the card does not support the method, '6985'
     * when no session is open, the level is below the session level or the protocol does not have it.
     *
     * <p>Since version 1.1 of the package.</p>
     *
     * @param bSecurityLevel the new level, a combination of the security level constants of {@link SecureChannel}
     */
    void setSecurityLevel(byte bSecurityLevel);
}
