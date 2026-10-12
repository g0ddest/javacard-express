package name.velikodniy.jcexpress.embedded.gp;

import javacard.framework.ISOException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/**
 * The card side of an SCP03 session with 8-byte challenges, as a Security Domain runs it for an application
 * (GlobalPlatform Card Specification v2.3 Amendment D v1.1.2; session and security level rules of GlobalPlatform
 * Card Specification v2.3.1 section 10.2.3 and Amendment D 5.5, 5.6; the reason codes of the GlobalPlatform Card
 * API 1.8, interface SecureChannel).
 *
 * <p>States: no session; initiated (INITIALIZE UPDATE answered, EXTERNAL AUTHENTICATE pending); open; aborted
 * (a command failed its check: the Current Security Level is NO_SECURITY_LEVEL, the Session Security Level is
 * kept and every use is refused until the session is terminated). The session belongs to the application that
 * sent INITIALIZE UPDATE.</p>
 */
final class Scp03Session {

    static final int AUTHENTICATED = 0x80;
    static final int C_MAC = 0x01;
    static final int C_DECRYPTION = 0x02;
    static final int R_MAC = 0x10;
    static final int R_ENCRYPTION = 0x20;

    private static final int SW_WRONG_LENGTH = 0x6700;
    private static final int SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982;
    private static final int SW_CONDITIONS_NOT_SATISFIED = 0x6985;
    private static final int HEADER = 5;

    private enum State { NONE, INITIATED, OPEN, ABORTED }

    private final SecurityDomain domain;
    private final Scp03Initiation initiation;
    private State state = State.NONE;
    private AID owner;
    private int option;
    private byte[] context;
    private byte[] sEnc;
    private byte[] sMac;
    private byte[] sRmac;
    private byte[] macChainingValue;
    private int encryptionCounter;
    private int sessionLevel;
    private int currentLevel;

    Scp03Session(SecurityDomain domain) {
        this.domain = domain;
        this.initiation = new Scp03Initiation(this, domain);
    }

    /** The Current Security Level as an unsigned value. */
    int currentLevel() {
        return currentLevel;
    }

    /** The Session Security Level as an unsigned value; kept while a session is aborted. */
    int sessionLevel() {
        return sessionLevel;
    }

    /**
     * Processes a command of the explicit secure channel initiation (Amendment D 5.2, 7.1).
     *
     * @param caller the calling application
     * @param buffer the buffer with the command
     * @param in     the offset of the class byte
     * @param length the length of header and data field
     * @param out    where response data are written
     * @return the length of the response data
     */
    short process(AID caller, byte[] buffer, int in, int length, int out) {
        return initiation.process(caller, buffer, in, length, out);
    }

    /** Ends the session when a new INITIALIZE UPDATE arrives (Amendment D 5.6) and keeps what it derived. */
    void initiated(AID caller, int optionOfSession, byte[] hostAndCardChallenge, byte[][] sessionKeys) {
        terminate();
        owner = caller;
        option = optionOfSession;
        context = hostAndCardChallenge;
        sEnc = sessionKeys[0];
        sMac = sessionKeys[1];
        sRmac = sessionKeys[2];
        state = State.INITIATED;
    }

    /** Whether EXTERNAL AUTHENTICATE of this application can follow: its INITIALIZE UPDATE was answered. */
    boolean awaitsAuthentication(AID caller) {
        return state == State.INITIATED && Objects.equals(owner, caller);
    }

    byte[] context() {
        return context;
    }

    byte[] sMac() {
        return sMac;
    }

    int option() {
        return option;
    }

    /**
     * Opens the session after a successful EXTERNAL AUTHENTICATE: both levels are AUTHENTICATED with the bits
     * of P1, the C-MAC of the command is the first MAC chaining value and the encryption counter starts before
     * 1 (Amendment D 5.5, 5.6, 6.2.3, 6.2.6).
     */
    void opened(int level, byte[] macOfExternalAuthenticate) {
        sessionLevel = AUTHENTICATED | level;
        currentLevel = sessionLevel;
        macChainingValue = macOfExternalAuthenticate;
        encryptionCounter = 0;
        state = State.OPEN;
    }

    /**
     * Checks and removes the protection of a command (GlobalPlatform Card API 1.8, SecureChannel.unwrap).
     *
     * @return the length of the command without its protection
     */
    short unwrap(AID caller, byte[] buffer, int offset, int length) {
        if (state == State.NONE || state == State.INITIATED) {
            return (short) length; // Amendment D 5.6: without a session no security verification is performed
        }
        requireOwner(caller);
        if (state == State.ABORTED) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        if ((currentLevel & C_MAC) == 0) {
            return (short) length; // level AUTHENTICATED only: the command stays as it is
        }
        return verifyAndStrip(buffer, offset, length);
    }

    /**
     * Verifies the C-MAC with the MAC chaining value (Amendment D 6.2.4), decrypts the data field at a
     * C-DECRYPTION level (6.2.6) and rewrites class byte and Lc. A command without the protection the level
     * requires, or with a wrong one, aborts the session (Amendment D 5.6, GPCS v2.3.1 10.2.3).
     */
    private short verifyAndStrip(byte[] buffer, int offset, int length) {
        int cla = buffer[offset] & 0xFF;
        int lc = length > 4 ? buffer[offset + 4] & 0xFF : -1;
        if (!ClassBytes.secureMessaging(cla) || lc < Scp03Crypto.MAC || length != HEADER + lc) {
            throw abort();
        }
        encryptionCounter++; // 6.2.6: counts every command of the session, encrypted or not
        int dataLength = lc - Scp03Crypto.MAC;
        byte[] header = {(byte) ClassBytes.forMac(cla), buffer[offset + 1], buffer[offset + 2], buffer[offset + 3],
            (byte) lc};
        byte[] data = Arrays.copyOfRange(buffer, offset + HEADER, offset + HEADER + dataLength);
        byte[] mac = Scp03Crypto.mac(sMac, macChainingValue, header, data);
        byte[] received = Arrays.copyOfRange(buffer, offset + HEADER + dataLength, offset + HEADER + lc);
        if (!MessageDigest.isEqual(Scp03Crypto.truncated(mac), received)) {
            throw abort();
        }
        macChainingValue = mac;
        byte[] plain = (currentLevel & C_DECRYPTION) != 0 && dataLength > 0 ? decrypt(data) : data;
        System.arraycopy(plain, 0, buffer, offset + HEADER, plain.length);
        buffer[offset] = (byte) ClassBytes.withoutSecureMessaging(cla);
        buffer[offset + 4] = (byte) plain.length; // a case 1 or 2 command keeps an Lc of '00'
        return (short) (HEADER + plain.length);
    }

    private byte[] decrypt(byte[] data) {
        byte[] plain = data.length % Scp03Crypto.BLOCK == 0
                ? Scp03Crypto.decryptCommand(sEnc, encryptionCounter, data) : null;
        if (plain == null) {
            throw abort();
        }
        return plain;
    }

    /**
     * Protects a response (GlobalPlatform Card API 1.8, SecureChannel.wrap): the status bytes that follow the
     * data are removed, and at an R_MAC level the R-MAC is put in their place.
     *
     * @return the length of the response data to send
     */
    short wrap(AID caller, byte[] buffer, int offset, int length) {
        if (length < 2) {
            throw new ISOException((short) SW_WRONG_LENGTH); // the status bytes are part of the input
        }
        if (state == State.NONE || state == State.INITIATED) {
            return (short) (length - 2);
        }
        requireOwner(caller);
        if (state == State.ABORTED) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        if ((currentLevel & R_MAC) == 0) {
            return (short) (length - 2);
        }
        return protect(buffer, offset, length - 2);
    }

    /**
     * Adds the R-MAC over the MAC chaining value, the response data and the status bytes (Amendment D 6.2.5),
     * after encrypting non-empty data at an R_ENCRYPTION level (6.2.7). A response with an error status word is
     * not protected and carries no data: only '9000' and the warnings '62xx' and '63xx' are protected (6.2.5).
     */
    private short protect(byte[] buffer, int offset, int dataLength) {
        byte[] status = Arrays.copyOfRange(buffer, offset + dataLength, offset + dataLength + 2);
        int sw1 = status[0] & 0xFF;
        boolean success = sw1 == 0x90 && status[1] == 0;
        if (!success && sw1 != 0x62 && sw1 != 0x63) {
            return 0;
        }
        byte[] data = Arrays.copyOfRange(buffer, offset, offset + dataLength);
        if ((currentLevel & R_ENCRYPTION) != 0 && dataLength > 0) {
            data = Scp03Crypto.encryptResponse(sEnc, encryptionCounter, data);
        }
        byte[] mac = Scp03Crypto.truncated(Scp03Crypto.mac(sRmac, macChainingValue, data, status));
        byte[] wrapped = Scp03Crypto.concat(data, mac);
        System.arraycopy(wrapped, 0, buffer, offset, wrapped.length);
        return (short) wrapped.length;
    }

    /**
     * Decrypts or encrypts sensitive data in place with the static Key-DEK: AES-CBC with a zero ICV, no padding
     * (Amendment D 6.2.8; GlobalPlatform Card API 1.8, SecureChannel.decryptData and encryptData).
     *
     * @return the length of the result, which is the length of the input
     */
    short sensitiveData(AID caller, boolean encrypt, byte[] buffer, int offset, int length) {
        if (state != State.OPEN) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        requireOwner(caller);
        if (length < 0 || length % Scp03Crypto.BLOCK != 0) {
            throw new ISOException((short) SW_WRONG_LENGTH);
        }
        byte[] data = Arrays.copyOfRange(buffer, offset, offset + length);
        byte[] result = length == 0 ? data : encrypt ? CryptoUtil.aesCbcEncrypt(domain.dekKey(), data)
                : CryptoUtil.aesCbcDecrypt(domain.dekKey(), data);
        System.arraycopy(result, 0, buffer, offset, length);
        return (short) length;
    }

    /**
     * Returns the Current Security Level for an application (GlobalPlatform Card API 1.8,
     * SecureChannel.getSecurityLevel).
     */
    byte level(AID caller) {
        if (state != State.OPEN) {
            return 0;
        }
        requireOwner(caller);
        return (byte) currentLevel;
    }

    /**
     * Changes the Current Security Level (GlobalPlatform Card API 1.8, SecureChannelx.setSecurityLevel): never
     * below the Session Security Level, and only to a level SCP03 defines and the option "i" allows.
     */
    void level(AID caller, int requested) {
        if (state != State.OPEN) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        requireOwner(caller);
        boolean keepsSessionLevel = (requested & sessionLevel) == sessionLevel;
        boolean authenticated = (requested & 0xC0) == AUTHENTICATED;
        if (!keepsSessionLevel || !authenticated || !Scp03Initiation.levelAllowed(requested & 0x3F, option)) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        currentLevel = requested & 0xFF;
    }

    /**
     * Terminates the session at the request of an application (GlobalPlatform Card API 1.8,
     * SecureChannel.resetSecurity); the request of an application that does not own the session changes
     * nothing, because the method must not fail.
     */
    void reset(AID caller) {
        if (state == State.NONE || Objects.equals(owner, caller)) {
            terminate();
        }
    }

    /** Whether the session, open or aborted, belongs to an application. */
    boolean ownedBy(AID application) {
        return state != State.NONE && Objects.equals(owner, application);
    }

    /**
     * Terminates the session: session keys and MAC chaining value are erased and both security levels are
     * NO_SECURITY_LEVEL (GPCS v2.3.1 10.2.3).
     */
    void terminate() {
        wipe();
        state = State.NONE;
        owner = null;
        sessionLevel = 0;
    }

    /**
     * Aborts the session: everything but the Session Security Level is reset, and the error persists until the
     * session is terminated (GPCS v2.3.1 10.2.3; Amendment D 5.6).
     *
     * @return the exception to throw: security status not satisfied
     */
    private ISOException abort() {
        wipe();
        state = State.ABORTED;
        return new ISOException((short) SW_SECURITY_STATUS_NOT_SATISFIED);
    }

    private void wipe() {
        for (byte[] secret : new byte[][]{sEnc, sMac, sRmac, macChainingValue, context}) {
            if (secret != null) {
                Arrays.fill(secret, (byte) 0);
            }
        }
        sEnc = null;
        sMac = null;
        sRmac = null;
        macChainingValue = null;
        context = null;
        encryptionCounter = 0;
        currentLevel = 0;
    }

    /**
     * A session is bound to the application that initiated it; a call by another application is rejected
     * (GlobalPlatform Card API 1.8, interface SecureChannel).
     */
    private void requireOwner(AID caller) {
        if (!Objects.equals(owner, caller)) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
    }
}
