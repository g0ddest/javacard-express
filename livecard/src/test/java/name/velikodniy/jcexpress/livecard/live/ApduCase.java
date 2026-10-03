package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;

import java.util.HexFormat;
import java.util.List;

/**
 * One command for a test applet and the exact response expected from it (data and status word), e.g. taken
 * from a validated real-card run. A list of cases runs on the real card and on jCardSim with
 * {@link #run(SmartCardSession, List)}; add a line to cover another behaviour.
 *
 * @param name     what the case shows
 * @param command  the command APDU, hex
 * @param expected the response APDU (data and SW1-SW2), hex
 */
public record ApduCase(String name, String command, String expected) {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * Sends every case's command and returns the responses.
     *
     * @param session the card (the applet must be selected)
     * @param cases   the cases
     * @return the responses as uppercase hex, in case order
     */
    public static List<String> run(SmartCardSession session, List<ApduCase> cases) {
        return cases.stream().map(c -> HEX.formatHex(session.transmit(HEX.parseHex(c.command())))).toList();
    }

    /**
     * Runs the cases on jCardSim with a fresh instance of the applet.
     *
     * @param applet     the applet
     * @param instance   the instance AID
     * @param parameters the install parameters ('C9' value)
     * @param cases      the cases
     * @return the simulator's responses as uppercase hex
     */
    public static List<String> onSimulator(TestApplet applet, AID instance, byte[] parameters, List<ApduCase> cases) {
        try (EmbeddedSession simulator = new EmbeddedSession()) {
            simulator.install(applet.load(), instance, parameters);
            return run(simulator, cases);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
