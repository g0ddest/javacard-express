package org.globalplatform;

import javacard.framework.Shareable;

/**
 * A Cardholder Verification Method, for example the Global PIN of a card: an application has a value checked
 * against it and asks for its state; applications with the CVM Management privilege change it (GlobalPlatform
 * Card Specification v2.3.1 section 8.2). An application gets the object from {@link GPSystem#getCVM(byte)}.
 * Part of the package since version 1.0.
 *
 * <p>The simulated cards have no Global PIN of their own: {@code getCVM} returns the object a Global Services
 * application of the test registered for the CVM family, or null.</p>
 */
public interface CVM extends Shareable {

    /** Result of {@link #verify}: the value is the CVM value. */
    short CVM_SUCCESS = 0;

    /** Result of {@link #verify}: the value is not the CVM value, or the CVM cannot be verified. */
    short CVM_FAILURE = -1;

    /** Value format: ASCII characters. */
    byte FORMAT_ASCII = (byte) 0x01;

    /** Value format: binary coded decimal digits. */
    byte FORMAT_BCD = (byte) 0x02;

    /** Value format: arbitrary bytes. */
    byte FORMAT_HEX = (byte) 0x03;

    /**
     * Tells whether the CVM has a value and a try limit and can be verified.
     *
     * @return true when the CVM is active
     */
    boolean isActive();

    /**
     * Tells whether a value was submitted in this card session, whatever the result.
     *
     * @return true after a verification attempt
     */
    boolean isSubmitted();

    /**
     * Tells whether the value was verified successfully in this card session.
     *
     * @return true after a successful verification
     */
    boolean isVerified();

    /**
     * Tells whether the try counter is used up.
     *
     * @return true when the CVM is blocked
     */
    boolean isBlocked();

    /**
     * Returns how many failed verifications the CVM still allows.
     *
     * @return the try counter
     */
    byte getTriesRemaining();

    /**
     * Sets the CVM value (CVM Management privilege).
     *
     * @param baBuffer the buffer with the new value
     * @param sOffset  where the value starts
     * @param bLength  the length of the value
     * @param bFormat  the format of the value, one of the {@code FORMAT} constants
     * @return true when the value was set
     */
    boolean update(byte[] baBuffer, short sOffset, byte bLength, byte bFormat);

    /**
     * Resets the state of the CVM to active, so that it has to be verified again.
     *
     * @return true when the state was reset
     */
    boolean resetState();

    /**
     * Blocks the CVM (CVM Management privilege).
     *
     * @return true when the CVM was blocked
     */
    boolean blockState();

    /**
     * Unblocks the CVM and resets its state and try counter (CVM Management privilege).
     *
     * @return true when the CVM was unblocked
     */
    boolean resetAndUnblockState();

    /**
     * Sets the try limit (CVM Management privilege).
     *
     * @param bTryLimit the number of failed verifications allowed
     * @return true when the limit was set
     */
    boolean setTryLimit(byte bTryLimit);

    /**
     * Compares a value with the CVM value and updates state and try counter.
     *
     * @param baBuffer the buffer with the value
     * @param sOffset  where the value starts
     * @param bLength  the length of the value
     * @param bFormat  the format of the value, one of the {@code FORMAT} constants
     * @return {@link #CVM_SUCCESS} or {@link #CVM_FAILURE}
     */
    short verify(byte[] baBuffer, short sOffset, byte bLength, byte bFormat);
}
