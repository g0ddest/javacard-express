package org.globalplatform;

import javacard.framework.Shareable;

/**
 * A Cardholder Verification Method the card shares between its applications, such as the global PIN: a secret
 * value with a try limit and a try counter. Any application may compare a value and ask for the state; changing
 * the value, the try limit or the blocking needs the CVM Management privilege. An applet gets the object from
 * {@link GPSystem#getCVM(byte)}.
 *
 * <p>The CVM is not ready until both its value and its try limit are set. From then on it is in one of the states
 * ACTIVE, INVALID_SUBMISSION, VALIDATED or BLOCKED (GlobalPlatform Card Specification v2.3.1 section 8.2.2.2).</p>
 *
 * <p>Since version 1.0 of the package.</p>
 */
public interface CVM extends Shareable {

    /** Result of {@link #verify}: the submitted value is the CVM value. Since version 1.0 of the package. */
    short CVM_SUCCESS = 0;
    /** Result of {@link #verify}: the comparison failed. Since version 1.0 of the package. */
    short CVM_FAILURE = -1;
    /** Format of a CVM value: ASCII characters, one per byte. Since version 1.0 of the package. */
    byte FORMAT_ASCII = 0x01;
    /**
     * Format of a CVM value: decimal digits, one per nibble, left justified, with a last nibble 'F' when the number
     * of digits is odd. Since version 1.0 of the package.
     */
    byte FORMAT_BCD = 0x02;
    /** Format of a CVM value: binary data. Since version 1.0 of the package. */
    byte FORMAT_HEX = 0x03;

    /**
     * Tells whether the CVM is ready for use, that is, whether its value and its try limit have been set.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true in the states ACTIVE, INVALID_SUBMISSION, VALIDATED and BLOCKED
     */
    boolean isActive();

    /**
     * Tells whether a value has been compared since the state was last reset.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true in the states INVALID_SUBMISSION and VALIDATED
     */
    boolean isSubmitted();

    /**
     * Tells whether the last comparison succeeded.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true in the state VALIDATED
     */
    boolean isVerified();

    /**
     * Tells whether the CVM is blocked.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true in the state BLOCKED
     */
    boolean isBlocked();

    /**
     * Returns the try counter: how many more comparisons may fail before the CVM is blocked.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return the try counter
     */
    byte getTriesRemaining();

    /**
     * Sets a new CVM value. Refused when the caller lacks the CVM Management privilege, the format is unknown or
     * the value does not fit the format. When the try limit is already set, the try counter is set to it and the
     * state becomes ACTIVE.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the new value; a global array
     * @param sOffset  offset of the new value
     * @param bLength  length of the new value
     * @param bFormat  {@link #FORMAT_ASCII}, {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @return true if the value was set
     */
    boolean update(byte[] baBuffer, short sOffset, byte bLength, byte bFormat);

    /**
     * Sets the state back to ACTIVE, which forgets a comparison. Not possible when the CVM is blocked or not ready.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true if the state was reset
     */
    boolean resetState();

    /**
     * Blocks the CVM. Refused when the caller lacks the CVM Management privilege or the CVM is not ready; true is
     * also the answer when the CVM is blocked already.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true if the CVM is blocked now
     */
    boolean blockState();

    /**
     * Sets the state to ACTIVE even when the CVM is blocked, and the try counter to the try limit. Refused when
     * the caller lacks the CVM Management privilege or the CVM is not ready.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @return true if the state was reset
     */
    boolean resetAndUnblockState();

    /**
     * Sets the try limit and the try counter to the same number. Refused when the caller lacks the CVM Management
     * privilege. When the value is already set, the state becomes ACTIVE.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param bTryLimit how many comparisons in a row may fail
     * @return true if the try limit was set
     */
    boolean setTryLimit(byte bTryLimit);

    /**
     * Compares a value with the CVM value. ASCII and BCD values are converted into each other when the digits
     * allow it; binary values compare only with binary values. A successful comparison sets the state to VALIDATED
     * and the try counter to the try limit; a failed one decrements the counter and sets the state to
     * INVALID_SUBMISSION, or to BLOCKED when the counter reaches zero. A blocked CVM fails every comparison.
     *
     * <p>Since version 1.0 of the package.</p>
     *
     * @param baBuffer the array that holds the submitted value; a global array
     * @param sOffset  offset of the submitted value
     * @param bLength  length of the submitted value
     * @param bFormat  {@link #FORMAT_ASCII}, {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @return {@link #CVM_SUCCESS} or {@link #CVM_FAILURE}
     */
    short verify(byte[] baBuffer, short sOffset, byte bLength, byte bFormat);
}
