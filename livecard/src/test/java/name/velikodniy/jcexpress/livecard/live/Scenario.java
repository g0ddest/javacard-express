package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.sim.JCardSimCard;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Steps for test applets that run unchanged on the card and on jCardSim: commands with the exact response
 * expected (data and status word), selections, deselection (another application is selected) and card resets.
 * {@link #run(Target)} returns one line {@code "<step> -> <response>"} per command, comparable with
 * {@link #expected()} and {@link #expectedOnJCardSim()}.
 */
final class Scenario {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /** Where a scenario runs. */
    interface Target {
        /**
         * Sends a command.
         *
         * @param command the command APDU
         * @return the response APDU
         */
        byte[] transmit(byte[] command);

        /**
         * Selects an applet; fails unless it is selected.
         *
         * @param aid the instance AID
         */
        void select(AID aid);

        /** Selects another application, so that the selected applet is deselected. */
        void deselect();

        /** Resets the card. */
        void reset();
    }

    private sealed interface Step permits Send, Select, Deselect, Reset {
    }

    private record Send(String name, String command, String expected, String onJCardSim) implements Step {
    }

    private record Select(AID aid) implements Step {
    }

    private record Deselect() implements Step {
    }

    private record Reset() implements Step {
    }

    private final List<Step> steps = new ArrayList<>();

    /**
     * Adds a SELECT of an applet.
     *
     * @param aid the instance AID
     * @return this scenario
     */
    Scenario select(AID aid) {
        steps.add(new Select(aid));
        return this;
    }

    /**
     * Adds a command and the response every Java Card runtime must give.
     *
     * @param name     what the step shows
     * @param command  the command APDU, hex
     * @param expected the response APDU (data and SW1-SW2), hex
     * @return this scenario
     */
    Scenario send(String name, String command, String expected) {
        return send(name, command, expected, expected);
    }

    /**
     * Adds a command for which jCardSim is known to deviate from the Java Card specification.
     *
     * @param name       what the step shows
     * @param command    the command APDU, hex
     * @param expected   the response the specification requires (expected from the card)
     * @param onJCardSim jCardSim's documented response
     * @return this scenario
     */
    Scenario send(String name, String command, String expected, String onJCardSim) {
        steps.add(new Send(name, command, expected, onJCardSim));
        return this;
    }

    /**
     * Adds the selection of another application.
     *
     * @return this scenario
     */
    Scenario deselect() {
        steps.add(new Deselect());
        return this;
    }

    /**
     * Adds a card reset.
     *
     * @return this scenario
     */
    Scenario reset() {
        steps.add(new Reset());
        return this;
    }

    /**
     * Returns the expected lines.
     *
     * @return one line per command
     */
    List<String> expected() {
        return commands().stream().map(send -> send.name() + " -> " + send.expected()).toList();
    }

    /**
     * Returns the lines expected from jCardSim (documented deviations included).
     *
     * @return one line per command
     */
    List<String> expectedOnJCardSim() {
        return commands().stream().map(send -> send.name() + " -> " + send.onJCardSim()).toList();
    }

    /**
     * Runs the steps.
     *
     * @param target the card or jCardSim
     * @return one line per command with the response
     */
    List<String> run(Target target) {
        List<String> lines = new ArrayList<>();
        for (Step step : steps) {
            switch (step) {
                case Send send -> lines.add(send.name() + " -> "
                        + HEX.formatHex(target.transmit(HEX.parseHex(send.command()))));
                case Select select -> target.select(select.aid());
                case Deselect deselect -> target.deselect();
                case Reset reset -> target.reset();
            }
        }
        return lines;
    }

    private List<Send> commands() {
        return steps.stream().filter(Send.class::isInstance).map(Send.class::cast).toList();
    }

    /**
     * The card as a target: deselection selects the ISD, reset is a card reset.
     *
     * @param card the card
     * @return the target
     */
    static Target on(LiveCard card) {
        return new Target() {
            @Override
            public byte[] transmit(byte[] command) {
                return card.session().transmit(command);
            }

            @Override
            public void select(AID aid) {
                card.session().select(aid);
            }

            @Override
            public void deselect() {
                card.session().select(AID.fromHex(card.config().isd()));
            }

            @Override
            public void reset() {
                card.reset();
            }
        };
    }

    /**
     * A jCardSim card as a target.
     *
     * @param simulator the card
     * @return the target
     */
    static Target on(JCardSimCard simulator) {
        return new Target() {
            @Override
            public byte[] transmit(byte[] command) {
                return simulator.transmit(command);
            }

            @Override
            public void select(AID aid) {
                byte[] response = simulator.select(aid);
                String sw = HEX.formatHex(response, response.length - 2, response.length);
                if (!sw.equals("9000")) {
                    throw new IllegalStateException("jCardSim: SELECT " + aid.toHex() + " failed: SW=" + sw);
                }
            }

            @Override
            public void deselect() {
                simulator.deselect();
            }

            @Override
            public void reset() {
                simulator.reset();
            }
        };
    }
}
