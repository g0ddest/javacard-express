package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.sm.SMAlgorithm;
import name.velikodniy.jcexpress.sm.SMContext;
import name.velikodniy.jcexpress.sm.SMKeys;
import name.velikodniy.jcexpress.sm.SMSession;

import java.util.Objects;

/**
 * Result of a successful PACE protocol execution.
 *
 * <p>Contains the derived session keys and authentication tokens.
 * Provides convenience methods to create {@link SMContext} and {@link SMSession}
 * for subsequent ISO 7816-4 Secure Messaging.</p>
 *
 * @param encKey    the session encryption key (K_enc)
 * @param macKey    the session MAC key (K_mac)
 * @param cardToken the authentication token received from the card
 * @param termToken the authentication token sent to the card
 */
public record PaceResult(byte[] encKey, byte[] macKey, byte[] cardToken, byte[] termToken) {

    /**
     * Creates a PACE result with defensive copies.
     */
    public PaceResult {
        Objects.requireNonNull(encKey, "encKey");
        Objects.requireNonNull(macKey, "macKey");
        Objects.requireNonNull(cardToken, "cardToken");
        Objects.requireNonNull(termToken, "termToken");
        encKey = encKey.clone();
        macKey = macKey.clone();
        cardToken = cardToken.clone();
        termToken = termToken.clone();
    }

    @Override
    public byte[] encKey() {
        return encKey.clone();
    }

    @Override
    public byte[] macKey() {
        return macKey.clone();
    }

    @Override
    public byte[] cardToken() {
        return cardToken.clone();
    }

    @Override
    public byte[] termToken() {
        return termToken.clone();
    }

    /**
     * Creates an {@link SMContext} for ISO 7816-4 Secure Messaging using AES.
     *
     * <p>The initial SSC is 16 bytes of zero (as specified by PACE).</p>
     *
     * @return a new SM context with PACE-derived keys
     */
    public SMContext toSMContext() {
        return new SMContext(SMAlgorithm.AES, new SMKeys(encKey, macKey), new byte[16]);
    }

    /**
     * Creates an {@link SMSession} wrapping the given session with PACE-derived keys.
     *
     * @param session the session to wrap
     * @return a new SM session with PACE-derived Secure Messaging
     */
    public SMSession toSMSession(SmartCardSession session) {
        return SMSession.wrap(session, toSMContext());
    }
}
