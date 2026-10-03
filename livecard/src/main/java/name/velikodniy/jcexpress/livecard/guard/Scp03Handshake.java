package name.velikodniy.jcexpress.livecard.guard;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * One SCP03 INITIALIZE UPDATE / EXTERNAL AUTHENTICATE exchange, verified by the guard with its own
 * cryptography (GlobalPlatform Card Specification v2.3 Amendment D v1.1.2, S8 mode).
 *
 * <ul>
 *   <li>INITIALIZE UPDATE response (Table 7-3): {@code key diversification data (10) || KVN || '03' || i ||
 *       card challenge (8) || card cryptogram (8) [|| sequence counter (3)]}.</li>
 *   <li>S-MAC = KDF(static Key-MAC, '06', key length, host challenge || card challenge) (6.2.1); card
 *       cryptogram = KDF(S-MAC, '00', 64, context), host cryptogram = KDF(S-MAC, '01', 64, context)
 *       (6.2.2.2, 6.2.2.3).</li>
 *   <li>EXTERNAL AUTHENTICATE (7.1.2): {@code 84 82 P1 00 10 || host cryptogram || C-MAC} with C-MAC = first 8
 *       bytes of AES-CMAC(S-MAC, 16 x '00' || CLA INS P1 P2 Lc || host cryptogram) (6.2.4).</li>
 * </ul>
 * <p>A handshake authorizes at most one EXTERNAL AUTHENTICATE.</p>
 */
final class Scp03Handshake {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final int CHALLENGE = 8;
    private static final int DIVERSIFICATION_DATA = 10;
    private static final int CARD_CHALLENGE_OFFSET = 13;
    private static final int CARD_CRYPTOGRAM_OFFSET = 21;

    private final byte[] hostChallenge;
    private byte[] sessionMac;
    private byte[] expectedHostCryptogram;
    private int option;
    private int keyVersion;
    private String failure = "INITIALIZE UPDATE has not been answered";
    private boolean used;

    Scp03Handshake(byte[] hostChallenge) {
        this.hostChallenge = hostChallenge.clone();
    }

    /**
     * Evaluates the INITIALIZE UPDATE response.
     *
     * @param staticMacKey the configured static Key-MAC
     * @param data         the response data (without SW1-SW2)
     * @param sw           the status word
     * @return null when the card cryptogram matches the configured keys, otherwise why not
     */
    String evaluate(byte[] staticMacKey, byte[] data, int sw) {
        failure = problem(data, sw);
        if (failure != null) {
            return failure;
        }
        byte[] context = new byte[2 * CHALLENGE];
        System.arraycopy(hostChallenge, 0, context, 0, CHALLENGE);
        System.arraycopy(data, CARD_CHALLENGE_OFFSET, context, CHALLENGE, CHALLENGE);
        option = data[DIVERSIFICATION_DATA + 2] & 0xFF;
        keyVersion = data[DIVERSIFICATION_DATA] & 0xFF;
        sessionMac = GuardCrypto.scp03Kdf(staticMacKey, 0x06, staticMacKey.length * 8, context);
        byte[] expectedCard = GuardCrypto.scp03Kdf(sessionMac, 0x00, 64, context);
        byte[] cardCryptogram = Arrays.copyOfRange(data, CARD_CRYPTOGRAM_OFFSET, CARD_CRYPTOGRAM_OFFSET + CHALLENGE);
        if (!MessageDigest.isEqual(expectedCard, cardCryptogram)) {
            failure = "the card cryptogram does not match the configured keys (wrong keys, wrong key version, or not"
                    + " the expected card); no EXTERNAL AUTHENTICATE will be sent";
            return failure;
        }
        expectedHostCryptogram = GuardCrypto.scp03Kdf(sessionMac, 0x01, 64, context);
        return null;
    }

    private static String problem(byte[] data, int sw) {
        if (sw != 0x9000) {
            return String.format("INITIALIZE UPDATE answered SW=%04X", sw);
        }
        int scp = data.length > DIVERSIFICATION_DATA + 1 ? data[DIVERSIFICATION_DATA + 1] & 0xFF : -1;
        if (scp != 0x03 && scp != -1) {
            return String.format("the card answered SCP%02X; the guard verifies SCP03 handshakes only", scp);
        }
        if (data.length != 29 && data.length != 32) {
            return "unexpected INITIALIZE UPDATE response length " + data.length + " (SCP03 S8: 29 or 32 bytes)";
        }
        if ((data[DIVERSIFICATION_DATA + 2] & 0x01) != 0) {
            return "the card uses SCP03 S16 mode; the guard verifies S8 handshakes only";
        }
        return null;
    }

    /**
     * Decides about an EXTERNAL AUTHENTICATE command. The handshake is used up by this call.
     *
     * @param command the parsed command
     * @param raw     the raw command bytes
     * @return null when the command is the one the guard computed itself, otherwise why it is blocked
     */
    String authorize(Apdu command, byte[] raw) {
        if (used) {
            return "this INITIALIZE UPDATE was already followed by an EXTERNAL AUTHENTICATE";
        }
        used = true;
        if (expectedHostCryptogram == null) {
            return "card cryptogram not independently verified: " + failure;
        }
        byte[] data = command.data();
        if (command.extended() || data.length != 2 * CHALLENGE || !command.secureMessaging() || command.p2() != 0) {
            return "unexpected EXTERNAL AUTHENTICATE format " + command;
        }
        if (!levelAllowed(command.p1(), option)) {
            return String.format("security level %02X is not allowed for a card with i=%02X (allowed: 00, 01, 03,"
                    + " plus R-MAC/R-ENC levels when the card supports them)", command.p1(), option);
        }
        if (!MessageDigest.isEqual(expectedHostCryptogram, Arrays.copyOf(data, CHALLENGE))) {
            return "host cryptogram differs from the guard's own computation";
        }
        if (!MessageDigest.isEqual(expectedMac(raw, data), Arrays.copyOfRange(data, CHALLENGE, 2 * CHALLENGE))) {
            return "C-MAC differs from the guard's own computation";
        }
        return null;
    }

    private byte[] expectedMac(byte[] raw, byte[] data) {
        byte[] input = new byte[16 + 5 + CHALLENGE];
        System.arraycopy(raw, 0, input, 16, 4);
        input[20] = (byte) data.length;
        System.arraycopy(data, 0, input, 21, CHALLENGE);
        return Arrays.copyOf(GuardCrypto.aesCmac(sessionMac, input), CHALLENGE);
    }

    /** Amendment D Table 7-6 together with the "i" parameter (Table 5-1: b6 R-MAC, b7 R-ENC support). */
    static boolean levelAllowed(int level, int option) {
        return switch (level) {
            case 0x00, 0x01, 0x03 -> true;
            case 0x11, 0x13 -> (option & 0x20) != 0;
            case 0x33 -> (option & 0x40) != 0;
            default -> false;
        };
    }

    /**
     * Returns a one-line description of the verified handshake for the transcript.
     *
     * @return key version and "i" parameter
     */
    String describe() {
        return String.format("KVN %02X, SCP03 i=%02X, host challenge %s", keyVersion, option,
                HEX.formatHex(hostChallenge));
    }
}
