package name.velikodniy.jcexpress.livecard.guard;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Known-answer self-check of the guard, run once before the first command of a live-card run: if the guard's
 * own cryptography or policy is broken, no command reaches the card.
 *
 * <p>Vectors: AES-CMAC examples of RFC 4493 section 4, and two SCP03 handshakes (GP test keys '40'..'4F',
 * KVN 'FF', i '00') produced by an independent reference implementation: one whose INITIALIZE UPDATE response
 * was made with the right keys (EXTERNAL AUTHENTICATE must pass) and one made with other keys (it must be
 * blocked). Corrupted variants and the never-allowed commands must be blocked as well.</p>
 */
public final class GuardSelfCheck {

    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] TEST_KEY = HEX.parseHex("404142434445464748494a4b4c4d4e4f");
    private static final byte[] PREFIX = HEX.parseHex("f04a4358");
    private static final byte[] ISD = HEX.parseHex("a000000151000000");
    private static final String SELECT_ISD = "00a4040008a00000015100000000";

    /** Reference handshake: INITIALIZE UPDATE command, its response (with SW) and the EXTERNAL AUTHENTICATE. */
    record Handshake(boolean keysMatch, String initializeUpdate, String response, String externalAuthenticate) {
    }

    /** Handshakes from the independent reference implementation (Python, GP Amendment D). */
    static final List<Handshake> REFERENCE_HANDSHAKES = List.of(
            new Handshake(true, "80500000083975b80ffd2445a100",
                    "00000000000000000000ff0300f32cec24f61679c43b609e829b5ff8249000",
                    "84820100108f16d6a1e346f478b250bc24d6186975"),
            new Handshake(false, "80500000089a6e18e7f022148100",
                    "00000000000000000000ff03006704c8c56bc1941fe9b772155ffc97819000",
                    "848201001082b7618b5d8bfc4ba9af3807665093e3"));

    private GuardSelfCheck() {
    }

    /**
     * Runs all known-answer checks.
     *
     * @throws IllegalStateException listing every check that failed
     */
    public static void verify() {
        List<String> failures = new ArrayList<>();
        checkCmac(failures);
        for (Handshake handshake : REFERENCE_HANDSHAKES) {
            checkHandshake(handshake, failures);
        }
        checkPolicy(failures);
        if (!failures.isEmpty()) {
            throw new IllegalStateException("APDU guard self-check failed, no command is sent to the card: "
                    + String.join("; ", failures));
        }
    }

    private static void checkCmac(List<String> failures) {
        byte[] key = HEX.parseHex("2b7e151628aed2a6abf7158809cf4f3c");
        String message = "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51"
                + "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710";
        String[][] examples = {
            {"", "bb1d6929e95937287fa37d129b756746"},
            {message.substring(0, 32), "070a16b46b4d4144f79bdd9dd04a287c"},
            {message.substring(0, 80), "dfa66747de9ae63030ca32611497c827"},
            {message, "51f0bebf7e3b9d92fc49741779363cfe"},
        };
        for (String[] example : examples) {
            String mac = HEX.formatHex(GuardCrypto.aesCmac(key, HEX.parseHex(example[0])));
            if (!mac.equals(example[1])) {
                failures.add("RFC 4493 AES-CMAC of " + example[0].length() / 2 + " bytes");
            }
        }
    }

    private static void checkHandshake(Handshake handshake, List<String> failures) {
        String label = handshake.keysMatch() ? "reference handshake (right keys)" : "reference handshake (other keys)";
        expect(failures, label, guardAfter(handshake), handshake.externalAuthenticate(), handshake.keysMatch());
        if (handshake.keysMatch()) {
            byte[] ea = HEX.parseHex(handshake.externalAuthenticate());
            expect(failures, "corrupted C-MAC", guardAfter(handshake), flip(ea, ea.length - 1), false);
            expect(failures, "corrupted host cryptogram", guardAfter(handshake), flip(ea, 5), false);
            byte[] level33 = ea.clone();
            level33[2] = 0x33;
            expect(failures, "R-ENC level on a card with i=00", guardAfter(handshake), HEX.formatHex(level33), false);
            ApduGuard twice = guardAfter(handshake);
            expect(failures, "first EXTERNAL AUTHENTICATE", twice, handshake.externalAuthenticate(), true);
            expect(failures, "second EXTERNAL AUTHENTICATE", twice, handshake.externalAuthenticate(), false);
        }
    }

    private static void checkPolicy(List<String> failures) {
        ApduGuard guard = newGuard();
        expect(failures, "GET STATUS before the ISD is selected", guard, "80f24002024f0000", false);
        expect(failures, "class FF", guard, "ffca000000", false);
        select(guard, SELECT_ISD);
        expect(failures, "GET STATUS", guard, "80f24002024f0000", true);
        expect(failures, "PUT KEY", guard, "80d80181" + "10" + "00".repeat(16), false);
        expect(failures, "STORE DATA", guard, "80e2800003010203", false);
        expect(failures, "SET STATUS card locked", guard, "80f0807f", false);
        expect(failures, "DELETE outside write access", guard, "80e40000084f06f04a43580102", false);
        guard.writeAccess(true);
        expect(failures, "DELETE of the ISD", guard, "80e400000a4f08a000000151000000", false);
        expect(failures, "SET STATUS of a Security Domain", guard, "80f0600f08a000000151000000", false);
        expect(failures, "INSTALL with privileges", guard,
                "80e60c001d" + "06f04a43580101" + "07f04a4358010101" + "07f04a4358010101" + "0104" + "02c900" + "0000",
                false);
        expect(failures, "INSTALL [for load] of another package", guard, "80e602000c07a0000000620101000000000000",
                false);
        expect(failures, "DELETE of a test package", guard, "80e40000084f06f04a43580102", true);
        checkContexts(failures);
    }

    /** Only the standard SELECT enters a context; authentication rules hold inside test applets too. */
    private static void checkContexts(List<String> failures) {
        ApduGuard chained = newGuard();
        select(chained, SELECT_ISD);
        chained.observe(HEX.parseHex("10a4040007f04a435801010100"), HEX.parseHex("9000"));
        expect(failures, "PUT KEY after a SELECT with the chaining bit", chained, "80d80181" + "10" + "00".repeat(16),
                false);
        ApduGuard applet = newGuard();
        select(applet, "00a4040007f04a435801010100");
        expect(failures, "command to a test applet", applet, "8001000000", true);
        expect(failures, "EXTERNAL AUTHENTICATE without handshake in a test applet", applet,
                REFERENCE_HANDSHAKES.get(0).externalAuthenticate(), false);
    }

    private static void select(ApduGuard guard, String select) {
        byte[] command = HEX.parseHex(select);
        guard.check(command);
        guard.observe(command, HEX.parseHex("9000"));
    }

    private static ApduGuard guardAfter(Handshake handshake) {
        ApduGuard guard = newGuard();
        select(guard, SELECT_ISD);
        byte[] initializeUpdate = HEX.parseHex(handshake.initializeUpdate());
        guard.check(initializeUpdate);
        guard.observe(initializeUpdate, HEX.parseHex(handshake.response()));
        return guard;
    }

    private static ApduGuard newGuard() {
        return new ApduGuard(new GuardPolicy(PREFIX, ISD, TEST_KEY), new AuthenticationBudget(Integer.MAX_VALUE),
                GuardListener.NONE);
    }

    private static void expect(List<String> failures, String label, ApduGuard guard, String command, boolean allowed) {
        boolean passed;
        try {
            guard.check(HEX.parseHex(command));
            passed = true;
        } catch (GuardViolationException e) {
            passed = false;
        }
        if (passed != allowed) {
            failures.add(label + " expected " + (allowed ? "ALLOWED" : "BLOCKED"));
        }
    }

    private static String flip(byte[] bytes, int index) {
        byte[] copy = bytes.clone();
        copy[index] ^= 0x01;
        return HEX.formatHex(copy);
    }
}
