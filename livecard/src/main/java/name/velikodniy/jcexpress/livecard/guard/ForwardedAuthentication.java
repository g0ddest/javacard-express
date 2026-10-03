package name.velikodniy.jcexpress.livecard.guard;

/**
 * INITIALIZE UPDATE and EXTERNAL AUTHENTICATE in a proprietary class (GlobalPlatform's coding, Amendment D 7.1.1,
 * 7.1.2) sent to a test applet: an applet that uses the GlobalPlatform API forwards them to its Security Domain,
 * whose key set counts failed authentications, so the guard applies its authentication rules to them; it cannot tell
 * them from an applet's own command that happens to use these INS values.
 */
final class ForwardedAuthentication {

    private ForwardedAuthentication() {
    }

    /**
     * Returns whether a command is coded as INITIALIZE UPDATE or EXTERNAL AUTHENTICATE.
     *
     * @param cla the class byte
     * @param ins the instruction byte
     * @return true for INS '50' or '82' in a proprietary class
     */
    static boolean matches(int cla, int ins) {
        return ClassBytes.proprietary(cla)
                && (ins == ApduGuard.INS_INITIALIZE_UPDATE || ins == ApduGuard.INS_EXTERNAL_AUTHENTICATE);
    }

    /**
     * Adds to the reason such a command was blocked for in a test applet why the rules apply and what to change.
     *
     * @param ins    the instruction byte
     * @param reason why the authentication rules block the command, or null if they let it pass
     * @return the explained reason, or null
     */
    static String explain(int ins, String reason) {
        if (reason == null) {
            return null;
        }
        boolean initializeUpdate = ins == ApduGuard.INS_INITIALIZE_UPDATE;
        return reason + String.format(". INS %02X in a proprietary class is GlobalPlatform's %s", ins,
                initializeUpdate ? "INITIALIZE UPDATE" : "EXTERNAL AUTHENTICATE")
                + ", and the guard applies its authentication rules inside test applets too: an applet that uses the"
                + " GlobalPlatform API forwards it to its Security Domain, whose key set counts failed authentications"
                + (initializeUpdate ? "" : "; this one counts as a failed authentication of the run")
                + ". If this is the applet's own command, give it another INS (LIVE_CARD_TESTING.md, \"Why wrong keys"
                + " never cost an authentication try\")";
    }
}
