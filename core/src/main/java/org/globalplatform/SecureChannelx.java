package org.globalplatform;

/**
 * A {@link SecureChannel} whose current security level the application can change during a session. An
 * application casts the object of {@link GPSystem#getSecureChannel()}; whether a card's object implements the
 * interface depends on the card (the simulated cards' object does). Part of the package since version 1.1.
 */
public interface SecureChannelx extends SecureChannel {

    /**
     * Sets the current security level, which can never be lower than the level the session was opened with.
     *
     * @param bSecurityLevel the new level, a combination of the security level constants
     */
    void setSecurityLevel(byte bSecurityLevel);
}
