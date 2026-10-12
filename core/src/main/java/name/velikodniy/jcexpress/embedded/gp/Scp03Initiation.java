package name.velikodniy.jcexpress.embedded.gp;

import javacard.framework.ISOException;
import name.velikodniy.jcexpress.AID;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * The explicit initiation of an SCP03 session on the card side: INITIALIZE UPDATE and EXTERNAL AUTHENTICATE as
 * an application passes them to its Security Domain (GlobalPlatform Card Specification v2.3 Amendment D v1.1.2
 * sections 5.2, 6.2.1 to 6.2.3, 7.1.1 and 7.1.2; GlobalPlatform Card API 1.8, SecureChannel.processSecurity).
 */
final class Scp03Initiation {

    private static final int INS_INITIALIZE_UPDATE = 0x50;
    private static final int INS_EXTERNAL_AUTHENTICATE = 0x82;
    private static final int SW_AUTHENTICATION_FAILED = 0x6300;
    private static final int SW_WRONG_LENGTH = 0x6700;
    private static final int SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982;
    private static final int SW_CONDITIONS_NOT_SATISFIED = 0x6985;
    private static final int SW_INCORRECT_P1P2 = 0x6A86;
    private static final int SW_REFERENCED_DATA_NOT_FOUND = 0x6A88;
    private static final int SW_INS_NOT_SUPPORTED = 0x6D00;
    private static final int SW_CLA_NOT_SUPPORTED = 0x6E00;
    private static final int HEADER = 5;
    private static final int CHALLENGE = 8;
    private static final byte SCP_IDENTIFIER = 0x03;

    private final Scp03Session session;
    private final SecurityDomain domain;

    Scp03Initiation(Scp03Session session, SecurityDomain domain) {
        this.session = session;
        this.domain = domain;
    }

    /**
     * Processes INITIALIZE UPDATE or EXTERNAL AUTHENTICATE; any other command is not a command of the protocol:
     * '6E00' for a class that is not a GlobalPlatform command class or not the class of the command, '6D00' for
     * another instruction (GlobalPlatform Card API 1.8, SecureChannel.processSecurity).
     *
     * @return the length of the response data written at {@code out}
     */
    short process(AID caller, byte[] buffer, int in, int length, int out) {
        if (length < 4) {
            throw new ISOException((short) SW_WRONG_LENGTH);
        }
        int cla = buffer[in] & 0xFF;
        int ins = buffer[in + 1] & 0xFF;
        if (!ClassBytes.globalPlatform(cla)) {
            throw new ISOException((short) SW_CLA_NOT_SUPPORTED);
        }
        if (ins != INS_INITIALIZE_UPDATE && ins != INS_EXTERNAL_AUTHENTICATE) {
            throw new ISOException((short) SW_INS_NOT_SUPPORTED);
        }
        if (!recognises(cla, ins)) {
            throw new ISOException((short) SW_CLA_NOT_SUPPORTED);
        }
        boolean initialize = ins == INS_INITIALIZE_UPDATE;
        int lc = length > 4 ? buffer[in + 4] & 0xFF : 0;
        boolean complete = length == HEADER + lc || (length == 4 && lc == 0);
        return initialize ? initializeUpdate(caller, buffer, in, complete ? lc : -1, out)
                : externalAuthenticate(caller, buffer, in, complete ? lc : -1);
    }

    /**
     * Tells whether class and instruction are those of INITIALIZE UPDATE or EXTERNAL AUTHENTICATE (Amendment D
     * Tables 7-2 and 7-5).
     *
     * @param cla the class byte, 0 to 255
     * @param ins the instruction byte, 0 to 255
     * @return true for a command of the secure channel initiation
     */
    static boolean recognises(int cla, int ins) {
        return ins == INS_INITIALIZE_UPDATE ? ClassBytes.initializeUpdate(cla)
                : ins == INS_EXTERNAL_AUTHENTICATE && ClassBytes.externalAuthenticate(cla);
    }

    /**
     * INITIALIZE UPDATE (Amendment D 7.1.1): ends the current session (5.6), checks Lc, P2 and the Key Version
     * Number of P1 ('00' selects the key set of the domain; another number that is not its own is '6A88'),
     * generates the card challenge (6.2.2.1), derives the session keys (6.2.1) and the card cryptogram (6.2.2.2)
     * and answers with the data of Table 7-3.
     */
    private short initializeUpdate(AID caller, byte[] buffer, int in, int lc, int out) {
        session.terminate();
        if (lc != CHALLENGE) {
            throw new ISOException((short) SW_WRONG_LENGTH);
        }
        if (buffer[in + 3] != 0) {
            throw new ISOException((short) SW_INCORRECT_P1P2);
        }
        int keyVersion = buffer[in + 2] & 0xFF;
        if (keyVersion != 0 && keyVersion != domain.keyVersion()) {
            throw new ISOException((short) SW_REFERENCED_DATA_NOT_FOUND);
        }
        int option = domain.scp03Option();
        byte[] host = Arrays.copyOfRange(buffer, in + HEADER, in + HEADER + CHALLENGE);
        byte[] sequence = (option & SecurityDomain.OPTION_PSEUDO_RANDOM_CHALLENGE) != 0 ? sequenceCounter()
                : new byte[0];
        byte[] card = sequence.length == 0 ? domain.nextChallenge()
                : Scp03Crypto.cardChallenge(domain.encKey(), sequence, caller);
        byte[] context = Scp03Crypto.concat(host, card);
        byte[][] keys = sessionKeys(option, context);
        byte[] cryptogram = Scp03Crypto.derive(keys[1], Scp03Crypto.CARD_CRYPTOGRAM, 64, context);
        byte[] keyInformation = {(byte) domain.keyVersion(), SCP_IDENTIFIER, (byte) option};
        byte[] response = Scp03Crypto.concat(domain.keyDiversificationData(), keyInformation, card, cryptogram,
                sequence);
        System.arraycopy(response, 0, buffer, out, response.length);
        session.initiated(caller, option, context, keys);
        return (short) response.length;
    }

    /** The incremented sequence counter; at its maximum the command is refused (Amendment D 6.2.2.1). */
    private byte[] sequenceCounter() {
        byte[] sequence = domain.nextSequenceCounter();
        if (sequence == null) {
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        return sequence;
    }

    /**
     * S-ENC from Key-ENC, S-MAC and, when the option "i" announces R-MAC, S-RMAC from Key-MAC, each as long as
     * the static keys, with the context host challenge and card challenge (Amendment D 6.2.1, Table 6-2).
     */
    private byte[][] sessionKeys(int option, byte[] context) {
        int bits = domain.encKey().length * 8;
        byte[] sEnc = Scp03Crypto.derive(domain.encKey(), Scp03Crypto.S_ENC, bits, context);
        byte[] sMac = Scp03Crypto.derive(domain.macKey(), Scp03Crypto.S_MAC, bits, context);
        byte[] sRmac = (option & SecurityDomain.OPTION_R_MAC) != 0
                ? Scp03Crypto.derive(domain.macKey(), Scp03Crypto.S_RMAC, bits, context) : null;
        return new byte[][]{sEnc, sMac, sRmac};
    }

    /**
     * EXTERNAL AUTHENTICATE (Amendment D 7.1.2): only directly after the INITIALIZE UPDATE of the same
     * application ('6985' otherwise), and once: whatever the outcome, a new INITIALIZE UPDATE is needed for the
     * next attempt (5.2: a failed step restarts the mutual authentication). The Security Domain counts the
     * command and every rejection.
     */
    private short externalAuthenticate(AID caller, byte[] buffer, int in, int lc) {
        domain.authenticationReceived();
        if (!session.awaitsAuthentication(caller)) {
            domain.authenticationRejected();
            throw new ISOException((short) SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] context = session.context().clone();
        byte[] sMac = session.sMac().clone();
        int option = session.option();
        try {
            int level = buffer[in + 2] & 0xFF;
            byte[] mac = verify(buffer, in, lc, level, option, sMac, context);
            session.opened(level, mac);
        } catch (ISOException e) {
            session.terminate();
            domain.authenticationRejected();
            throw e;
        }
        return 0;
    }

    /**
     * Checks Lc, P2 and the security level of P1 (Table 7-6, within what the option "i" allows), then the host
     * cryptogram (6.2.2.3, '6300' when wrong, Table 7-7) and the C-MAC of the command, computed with a MAC
     * chaining value of 16 zero bytes (6.2.3, 6.2.4; a wrong MAC is the general error '6982', GPCS v2.3.1
     * 11.1.3).
     *
     * @return the full C-MAC, the first MAC chaining value of the session
     */
    private static byte[] verify(byte[] buffer, int in, int lc, int level, int option, byte[] sMac, byte[] context) {
        if (lc != 2 * CHALLENGE) {
            throw new ISOException((short) SW_WRONG_LENGTH);
        }
        if (buffer[in + 3] != 0 || !levelAllowed(level, option)) {
            throw new ISOException((short) SW_INCORRECT_P1P2);
        }
        byte[] expected = Scp03Crypto.derive(sMac, Scp03Crypto.HOST_CRYPTOGRAM, 64, context);
        byte[] cryptogram = Arrays.copyOfRange(buffer, in + HEADER, in + HEADER + CHALLENGE);
        if (!MessageDigest.isEqual(expected, cryptogram)) {
            throw new ISOException((short) SW_AUTHENTICATION_FAILED);
        }
        byte[] header = {(byte) ClassBytes.forMac(buffer[in] & 0xFF), buffer[in + 1], buffer[in + 2], buffer[in + 3],
            (byte) lc};
        byte[] mac = Scp03Crypto.mac(sMac, new byte[Scp03Crypto.BLOCK], header, cryptogram);
        byte[] received = Arrays.copyOfRange(buffer, in + HEADER + CHALLENGE, in + HEADER + 2 * CHALLENGE);
        if (!MessageDigest.isEqual(Scp03Crypto.truncated(mac), received)) {
            throw new ISOException((short) SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        return mac;
    }

    /**
     * Tells whether a security level is one SCP03 defines (Amendment D Table 7-6: '00', '01', '03', '11', '13',
     * '33') and the option "i" allows: R-MAC needs b6, R-ENCRYPTION b7 (Table 5-1).
     *
     * @param level  the level without the authentication bits
     * @param option the option "i" of the session
     * @return true for an allowed level
     */
    static boolean levelAllowed(int level, int option) {
        boolean defined = level == 0x00 || level == 0x01 || level == 0x03 || level == 0x11 || level == 0x13
                || level == 0x33;
        boolean macAllowed = (level & Scp03Session.R_MAC) == 0 || (option & 0x20) != 0;
        boolean encryptionAllowed = (level & Scp03Session.R_ENCRYPTION) == 0 || (option & 0x40) != 0;
        return defined && macAllowed && encryptionAllowed;
    }
}
