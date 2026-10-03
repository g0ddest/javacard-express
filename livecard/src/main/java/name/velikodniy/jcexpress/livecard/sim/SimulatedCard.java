package name.velikodniy.jcexpress.livecard.sim;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * An in-memory GlobalPlatform card for offline tests of the live-card harness, modelled on the real card the
 * suite was validated on (JCOP 4: ATR, ISD data objects, SCP03 i = '00', KVN 'FF', GET STATUS layout).
 * Applets installed through GlobalPlatform run in jCardSim, one {@link JCardSimCard} per load file (shared with
 * the load files it imports), so the live tests' expectations can be checked without hardware. Selecting
 * another application deselects the applet, and a card reset resets every jCardSim card.
 *
 * <p>Logical channels 1-3 can be opened with MANAGE CHANNEL; on them only the ISD can be selected (SELECT and
 * GET DATA): applets run on the basic channel only, because jCardSim has one selection per card. A SELECT of an
 * installed applet on a logical channel is answered '6881' (logical channel not supported, ISO/IEC 7816-4:2005
 * 5.1.3, Table 6), and the card says why ({@link #onLimit(Consumer)}); an AID the card does not have is answered
 * '6A82'. Every command that reaches the card is recorded, so tests can assert what the guard let through.</p>
 */
public final class SimulatedCard implements SmartCardSession {

    /** The ATR of the real card. */
    public static final String ATR = "3BDC18FF8191FE1FC38073C821136605036351000250";
    /** The ISD AID. */
    public static final String ISD = "A000000151000000";
    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final String FCI = "6F108408A000000151000000A5049F6501FF";

    private final SimulatedContent content;
    private final SimulatedIsd isd;
    private final List<String> received = new ArrayList<>();
    private final Set<Integer> channels = new TreeSet<>();
    private SimulatedContent.Application selected;
    private Consumer<String> limits = note -> { };

    /**
     * Creates a card.
     *
     * @param staticKey the ISD's static key (ENC = MAC = DEK)
     * @param applets   the applets the card can run
     */
    public SimulatedCard(byte[] staticKey, SimulatedApplets applets) {
        this.content = new SimulatedContent(ISD, applets);
        this.isd = new SimulatedIsd(staticKey, content);
    }

    /**
     * Returns every command that reached the card, in order.
     *
     * @return the commands as uppercase hex
     */
    public synchronized List<String> received() {
        return List.copyOf(received);
    }

    /**
     * Returns how many EXTERNAL AUTHENTICATE commands reached the card.
     *
     * @return the count
     */
    public synchronized int externalAuthentications() {
        return isd.externalAuthentications();
    }

    /**
     * Returns how many EXTERNAL AUTHENTICATE commands the card rejected (what a real card counts).
     *
     * @return the count
     */
    public synchronized int failedAuthentications() {
        return isd.failedAuthentications();
    }

    /**
     * Returns the AIDs of the applications on the card.
     *
     * @return application AIDs, uppercase hex
     */
    public synchronized List<String> applications() {
        return content.applications().stream().map(application -> application.aid).toList();
    }

    /**
     * Adds an application that another tool installed (no applet runs behind it), for tests of what the harness
     * must leave alone.
     *
     * @param aid        the application AID, uppercase hex
     * @param loadFile   its Executable Load File AID, uppercase hex
     * @param privileges the first privilege byte (GlobalPlatform Card Specification v2.3.1 Table 11-7)
     * @param state      the life cycle state, e.g. '07' or LOCKED '87'
     */
    public synchronized void addForeignApplication(String aid, String loadFile, int privileges, int state) {
        content.addForeignApplication(aid, loadFile, privileges, state);
    }

    /**
     * Receives a note whenever the card answers a command with a status word that only a limit of the simulated
     * card explains (an applet selected on a logical channel), e.g. for the transcript of the test.
     *
     * @param listener receives one line per such answer
     */
    public synchronized void onLimit(Consumer<String> listener) {
        limits = listener;
    }

    /** Makes this card refuse load files that use the int type (Header flag ACC_INT) with '6A80'. */
    public synchronized void refuseIntPackages() {
        isd.refuseIntPackages();
    }

    /** Makes this card refuse DELETE of a LOCKED application ('6985'), which some cards may do. */
    public synchronized void refuseDeletingLockedApplications() {
        content.refuseDeletingLockedApplications();
    }

    @Override
    public synchronized byte[] transmit(byte[] command) {
        received.add(HEX.formatHex(command));
        int cla = command[0] & 0xFF;
        int ins = command[1] & 0xFF;
        int p1 = command[2] & 0xFF;
        int channel = (cla & 0x40) == 0 ? cla & 0x03 : 4 + (cla & 0x0F);
        byte[] data = data(command);
        if (ins == 0x70 && interindustry(cla)) {
            return manageChannel(channel, p1, command[3] & 0xFF);
        }
        if (channel != 0) {
            return onLogicalChannel(channel, cla, ins, p1, command[3] & 0xFF, data);
        }
        if (ins == 0xA4 && p1 == 0x04 && interindustry(cla)) {
            return select(command, data);
        }
        if (selected != null) {
            return selected.card.transmit(command);
        }
        return isd.process(cla, ins, p1, command[3] & 0xFF, data);
    }

    /** MANAGE CHANNEL (ISO/IEC 7816-4:2005 7.1.2): open from the basic channel, close any open channel. */
    private byte[] manageChannel(int channel, int p1, int p2) {
        if (p1 == 0x00 && channel == 0) {
            for (int number = 1; number <= 3; number++) {
                if ((p2 == 0 || p2 == number) && channels.add(number)) {
                    return p2 == 0 ? new byte[]{(byte) number, (byte) 0x90, 0x00} : SimulatedIsd.sw(0x9000);
                }
            }
            return SimulatedIsd.sw(0x6A81);
        }
        int target = p2 != 0 ? p2 : channel;
        return p1 == 0x80 && channels.remove(target) ? SimulatedIsd.sw(0x9000) : SimulatedIsd.sw(0x6881);
    }

    /**
     * Logical channels see the ISD only: SELECT of the ISD and GET DATA. An installed applet is answered '6881'
     * (logical channel not supported, ISO/IEC 7816-4:2005 Table 6) with a note; an unknown AID '6A82'.
     */
    private byte[] onLogicalChannel(int channel, int cla, int ins, int p1, int p2, byte[] data) {
        if (!channels.contains(channel)) {
            return SimulatedIsd.sw(0x6881);
        }
        if (ins == 0xA4 && p1 == 0x04 && interindustry(cla)) {
            String aid = HEX.formatHex(data);
            if (data.length == 0 || aid.equals(ISD)) {
                return SimulatedIsd.concat(HEX.parseHex(FCI), SimulatedIsd.sw(0x9000));
            }
            if (content.application(aid) == null) {
                return SimulatedIsd.sw(0x6A82);
            }
            limits.accept("simulated card: applets run on the basic channel only (jCardSim has one selection per"
                    + " card), so the SELECT of " + aid + " on logical channel " + channel + " is answered 6881 (logical"
                    + " channel not supported); select the applet on the basic channel, the Issuer Security Domain"
                    + " can be selected on logical channels");
            return SimulatedIsd.sw(0x6881);
        }
        return ins == 0xCA ? isd.plainGetData(p1, p2) : SimulatedIsd.sw(0x6D00);
    }

    /** SELECT by AID; the applet selected before is deselected unless it is selected again on its card. */
    private byte[] select(byte[] command, byte[] aid) {
        isd.endSession();
        String hex = HEX.formatHex(aid);
        SimulatedContent.Application application = aid.length == 0 ? null : content.application(hex);
        boolean selectable = application != null && application.card != null && (application.state & 0x80) == 0
                && (application.state & 0x07) == 0x07;
        if (selected != null && (!selectable || selected.card != application.card)) {
            selected.card.deselect();
        }
        selected = selectable ? application : null;
        if (selectable) {
            return application.card.transmit(command);
        }
        return aid.length == 0 || hex.equals(ISD) ? SimulatedIsd.concat(HEX.parseHex(FCI), SimulatedIsd.sw(0x9000))
                : SimulatedIsd.sw(0x6A82);
    }

    /**
     * The runtime handles SELECT and MANAGE CHANNEL only in an inter-industry class (b8 = 0); with a proprietary
     * class byte they reach the selected application, as on a Java Card.
     */
    private static boolean interindustry(int cla) {
        return (cla & 0x80) == 0;
    }

    private static byte[] data(byte[] command) {
        if (command.length <= 5) {
            return new byte[0];
        }
        int length = command[4] & 0xFF;
        return Arrays.copyOfRange(command, 5, 5 + length);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        return new APDUResponse(transmit(APDUCodec.encode(cla, ins, p1, p2, data, le)));
    }

    /**
     * Resets the card: the ISD is selected, the secure channel ends, applets keep their persistent state.
     */
    @Override
    public synchronized void reset() {
        isd.endSession();
        selected = null;
        channels.clear();
        content.reset();
    }

    @Override
    public void select(AID aid) {
        send(0x00, 0xA4, 0x04, 0x00, aid.toBytes(), 256);
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        throw new UnsupportedOperationException();
    }

    /** The card stays in the reader; its connections are closed by whoever opened them. */
    @Override
    public void close() {
        // nothing to release
    }
}
