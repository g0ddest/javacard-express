package name.velikodniy.jcexpress.livecard.guard;

import name.velikodniy.jcexpress.scp.SCP03;
import name.velikodniy.jcexpress.scp.SCPKeys;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * An {@link ApduGuard} with GP test keys, prefix {@code F04A4358} and ISD {@code A000000151000000}, recording
 * its notes and content changes, plus helpers that play the card's side.
 */
final class GuardHarness {

    static final HexFormat HEX = HexFormat.of().withUpperCase();
    static final byte[] TEST_KEY = HEX.parseHex("404142434445464748494A4B4C4D4E4F");
    static final String SELECT_ISD = "00A4040008A00000015100000000";
    private static final String DIVERSIFICATION = "00000000000000000000";

    final AuthenticationBudget budget;
    final List<String> notes = new ArrayList<>();
    final List<ContentChange> changes = new ArrayList<>();
    final ApduGuard guard;

    GuardHarness() {
        this(1);
    }

    GuardHarness(int maxFailures) {
        budget = new AuthenticationBudget(maxFailures);
        guard = new ApduGuard(new GuardPolicy(HEX.parseHex("F04A4358"), HEX.parseHex("A000000151000000"), TEST_KEY),
                budget, new GuardListener() {
                    @Override
                    public void note(String message) {
                        notes.add(message);
                    }

                    @Override
                    public void contentChanged(ContentChange change) {
                        changes.add(change);
                    }
                });
    }

    /** Returns whether the guard lets the command pass. */
    boolean allows(String command) {
        try {
            guard.check(HEX.parseHex(command));
            return true;
        } catch (GuardViolationException e) {
            return false;
        }
    }

    /** Sends a command the guard must allow and shows it the card's answer. */
    void exchange(String command, String response) {
        byte[] bytes = HEX.parseHex(command);
        guard.check(bytes);
        guard.observe(bytes, HEX.parseHex(response));
    }

    /** SELECT of the ISD answered '9000'. */
    void selectIsd() {
        exchange(SELECT_ISD, "9000");
    }

    /**
     * Runs INITIALIZE UPDATE with the given host challenge; the card answers with a cryptogram made with
     * {@code cardKey}.
     *
     * @return the response data (without SW)
     */
    byte[] initializeUpdate(String cla, byte[] hostChallenge, byte[] cardKey, int option) {
        byte[] cardChallenge = HEX.parseHex("0102030405060708");
        byte[] context = concat(hostChallenge, cardChallenge);
        byte[] sessionMac = GuardCrypto.scp03Kdf(cardKey, 0x06, 128, context);
        byte[] cryptogram = GuardCrypto.scp03Kdf(sessionMac, 0x00, 64, context);
        byte[] data = concat(HEX.parseHex(DIVERSIFICATION + "FF03" + String.format("%02X", option)), cardChallenge,
                cryptogram);
        exchange(cla + "50000008" + HEX.formatHex(hostChallenge) + "00", HEX.formatHex(data) + "9000");
        return data;
    }

    /** The EXTERNAL AUTHENTICATE that the gp module builds for this handshake (an independent implementation). */
    static String externalAuthenticateByGp(byte[] hostChallenge, byte[] response, int level) {
        return HEX.formatHex(SCP03.from(SCPKeys.defaultKeys(), hostChallenge, response, level).externalAuthenticate());
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
