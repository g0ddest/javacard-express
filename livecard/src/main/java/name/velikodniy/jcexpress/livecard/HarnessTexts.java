package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.sim.SimulatedTerminal;

/**
 * Texts the live-card harness writes into logs, exceptions and transcripts.
 */
final class HarnessTexts {

    /** Logged once per run when a CAP file is loaded onto the card without off-card verification. */
    static final String UNVERIFIED_WARNING = "Live card: verifierSdk=none, so CAP files are loaded onto the"
            + " card without an off-card verifier check (JCVM 3.1 §1.3 requires verification before loading). Set"
            + " verifierSdk to an Oracle Java Card development kit that matches javaCardVersion (e.g."
            + " build/oracle-sdks/jc304_kit for 3.0.4) to check every CAP file before it is loaded"
            + " (LIVE_CARD_TESTING.md).";

    /** Why a deployment is refused when no verifier is configured and {@code verifierSdk=none} is not set. */
    static final String NO_VERIFIER = "No off-card verifier: JCVM 3.1 §1.3 requires every CAP file to be"
            + " verified before it is loaded, and the harness loads only verified CAP files unless told otherwise."
            + " Set verifierSdk to an Oracle Java Card development kit that matches javaCardVersion (e.g."
            + " build/oracle-sdks/jc304_kit for 3.0.4, which is taken when it is there), or"
            + " verifierSdk=none to load unverified CAP files at your own risk. Nothing was sent to the card.";

    /**
     * What the transcript cannot show: javax.smartcardio (SunPCSC) answers '61XX' with GET RESPONSE and repeats a
     * command after '6CXX' itself when its properties {@code sun.security.smartcardio.t0GetResponse} and
     * {@code t1GetResponse} are true (the default), below the guard and the transcript.
     */
    static final String JDK_EXCHANGES = "javax.smartcardio: t0GetResponse="
            + System.getProperty("sun.security.smartcardio.t0GetResponse", "true") + ", t1GetResponse="
            + System.getProperty("sun.security.smartcardio.t1GetResponse", "true") + " (when true, the JDK itself"
            + " answers 61XX with GET RESPONSE and repeats a command after 6CXX, below the guard and this transcript)";

    private HarnessTexts() {
    }

    /**
     * The simulated card's counterpart of {@link #JDK_EXCHANGES}: its terminal is no PC/SC provider, so nothing
     * completes '61XX' or '6CXX' below the session; the card of a {@code @JavaCardTest} class does it in
     * {@code send()}, and its GET RESPONSE and repeated commands appear in the transcript.
     */
    static final String SIMULATED_EXCHANGES = "simulated card: nothing answers 61XX or 6CXX below this transcript"
            + " (no PC/SC provider); the card of a @JavaCardTest class completes them in send() with GET RESPONSE"
            + " or the command again with the exact Le, and those exchanges appear here";

    /**
     * Returns the transcript header line on '61XX' and '6CXX' (ISO/IEC 7816-4:2005 5.1.3) for the reader a card is
     * in: what a PC/SC provider does below the transcript, or that below the simulated card nothing does.
     *
     * @param reader the reader name ({@link name.velikodniy.jcexpress.livecard.sim.SimulatedTerminal#READER} for
     *               the simulated card)
     * @return the line
     */
    static String exchangesNote(String reader) {
        return SimulatedTerminal.READER.equals(reader) ? SIMULATED_EXCHANGES : JDK_EXCHANGES;
    }
}
