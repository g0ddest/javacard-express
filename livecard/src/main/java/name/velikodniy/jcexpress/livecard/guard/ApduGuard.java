package name.velikodniy.jcexpress.livecard.guard;

import name.velikodniy.jcexpress.livecard.guard.CardContentRules.InstallFields;
import name.velikodniy.jcexpress.livecard.guard.ChannelState.Context;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Independent allow-list safety guard for every command sent to a real card.
 *
 * <p>{@link #check(byte[])} runs before a command is transmitted and throws {@link GuardViolationException}
 * for anything not allowed; nothing is sent then. {@link #observe(byte[], byte[])} sees each response and
 * keeps the guard's own picture of the card per logical channel (ISO/IEC 7816-4:2005 5.1.1).</p>
 *
 * <h2>Policy</h2>
 * <ul>
 *   <li>Never: class 'FF' (invalid, ISO/IEC 7816-4:2005 5.1.1; PC/SC readers take it as a command for the reader
 *       itself).</li>
 *   <li>Always: SELECT, MANAGE CHANNEL and GET RESPONSE in a plain inter-industry class ('00'-'03', '40'-'4F':
 *       any logical channel, no secure messaging, no command chaining; {@link ClassBytes#plain}) and short length
 *       (extended GET RESPONSE only to a test applet); the card's runtime handles those.</li>
 *   <li>Test-applet context (the channel's last SELECT was the standard SELECT by AID of an AID under the prefix,
 *       answered '9000' or '61XX'): any command, because it reaches the tests' own applet, except that INITIALIZE
 *       UPDATE and EXTERNAL AUTHENTICATE in a proprietary class follow the rules below: an applet that uses
 *       GlobalPlatform's SecureChannel API forwards them to its Security Domain, whose key set counts failed
 *       authentications.</li>
 *   <li>Issuer Security Domain context (the channel's last SELECT was the standard SELECT of the configured ISD
 *       AID, answered with success):
 *     <ul>
 *       <li>GET DATA and GET STATUS (read-only, GlobalPlatform Card Specification v2.3.1 11.3, 11.4);</li>
 *       <li>INITIALIZE UPDATE (SCP03 S8, Amendment D 7.1.1);</li>
 *       <li>EXTERNAL AUTHENTICATE only if the guard verified the card cryptogram with the configured keys and
 *           the command carries exactly the host cryptogram and C-MAC the guard computed itself, at most once
 *           per INITIALIZE UPDATE, directly after it, and at a security level the card supports
 *           ({@link Scp03Handshake});</li>
 *       <li>inside a write-access scope ({@link #writeAccess(boolean)}) and never under C-ENC: INSTALL [for
 *           load] / [for install (and make selectable)] / [for make selectable] of AIDs under the prefix with
 *           privileges '00' and no token, LOAD after an accepted INSTALL [for load], DELETE of one AID under
 *           the prefix, SET STATUS lock/unlock of an application under the prefix ({@link CardContentRules});
 *           </li>
 *       <li>nothing else: never PUT KEY, STORE DATA, SET STATUS of the card or a Security Domain, other INSTALL
 *           variants, extended length commands.</li>
 *     </ul>
 *   </li>
 *   <li>Unknown context (a fresh, reset or newly opened channel, a failed SELECT, another application, or a
 *       command that may have changed the selection in a way the guard cannot follow): GET DATA only. A block
 *       there names the command or event after which the selection became unknown and what to do.</li>
 *   <li>Every authentication problem is recorded in the run's {@link AuthenticationBudget}; once it is
 *       exhausted every command is blocked.</li>
 * </ul>
 */
public final class ApduGuard {

    static final int INS_GET_DATA = 0xCA;
    static final int INS_GET_STATUS = 0xF2;
    static final int INS_INITIALIZE_UPDATE = 0x50;
    static final int INS_EXTERNAL_AUTHENTICATE = 0x82;
    static final int INS_INSTALL = 0xE6;
    static final int INS_LOAD = 0xE8;
    static final int INS_DELETE = 0xE4;
    static final int INS_SET_STATUS = 0xF0;
    private static final int SW_OK = 0x9000;
    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final String ONLY_ISD = " only to the Issuer Security Domain, after the standard SELECT of its AID"
            + " on this channel";

    private final GuardPolicy policy;
    private final AuthenticationBudget budget;
    private final GuardListener listener;
    private final Map<Integer, ChannelState> channels = new HashMap<>();
    private boolean writeAccess;
    private int blocked;
    private int verifiedHandshakes;

    /**
     * Creates a guard for one card connection.
     *
     * @param policy   the run's policy (AID prefix, ISD, static Key-MAC)
     * @param budget   the run-wide authentication budget
     * @param listener receives transcript notes and content changes
     */
    public ApduGuard(GuardPolicy policy, AuthenticationBudget budget, GuardListener listener) {
        this.policy = policy;
        this.budget = budget;
        this.listener = listener;
        channels.put(0, new ChannelState());
    }

    /**
     * Decides about a command before it is sent.
     *
     * @param command the raw command APDU
     * @throws GuardViolationException if the command is not allowed (it must not be sent)
     */
    public synchronized void check(byte[] command) {
        Optional<String> abort = budget.abortReason();
        if (abort.isPresent()) {
            throw block(command, abort.get());
        }
        if (command == null || command.length < 4) {
            throw block(command, "not a command APDU");
        }
        String reason;
        try {
            reason = decide(command);
        } catch (IllegalArgumentException e) {
            reason = "malformed command: " + e.getMessage();
        }
        if (reason != null) {
            throw block(command, SelectionLoss.explain(reason, channels.get(ClassBytes.channel(command[0] & 0xFF))));
        }
    }

    private String decide(byte[] raw) {
        int cla = raw[0] & 0xFF;
        int ins = raw[1] & 0xFF;
        if (cla == ClassBytes.INVALID) {
            return "class byte FF is invalid (ISO/IEC 7816-4:2005 5.1.1); PC/SC readers take it as a command for the"
                    + " reader itself";
        }
        ChannelState channel = state(ClassBytes.channel(cla));
        if (runtimeCommand(cla, ins)) {
            Apdu command = Apdu.parse(raw);
            return command.extended() && (ins != ClassBytes.GET_RESPONSE || !channel.testApplet())
                    ? "extended length SELECT, MANAGE CHANNEL or GET RESPONSE (outside the tests' own applets)" : null;
        }
        if (channel.testApplet()) {
            return ForwardedAuthentication.matches(cla, ins)
                    ? ForwardedAuthentication.explain(ins, authenticationRule(Apdu.parse(raw), channel, raw)) : null;
        }
        return strict(Apdu.parse(raw), channel, raw);
    }

    /**
     * Updates the guard's picture of the card with the response to a command that was sent.
     *
     * @param command  the raw command APDU
     * @param response the raw response (data and SW1-SW2)
     */
    public synchronized void observe(byte[] command, byte[] response) {
        if (command == null || command.length < 4 || response == null || response.length < 2) {
            return;
        }
        int sw = ((response[response.length - 2] & 0xFF) << 8) | (response[response.length - 1] & 0xFF);
        byte[] data = Arrays.copyOf(response, response.length - 2);
        int cla = command[0] & 0xFF;
        int ins = command[1] & 0xFF;
        int number = ClassBytes.channel(cla);
        ChannelState channel = state(number);
        if ((ins == ClassBytes.SELECT || ins == ClassBytes.MANAGE_CHANNEL)
                && selectionChanged(command, cla, ins, number, data, sw)) {
            return;
        }
        boolean checked = !channel.testApplet() || ForwardedAuthentication.matches(cla, ins);
        boolean handshakeStep = checked && (ins == INS_INITIALIZE_UPDATE || ins == INS_EXTERNAL_AUTHENTICATE);
        if (!handshakeStep && !(ins == ClassBytes.GET_RESPONSE && ClassBytes.plain(cla))) {
            channel.endHandshake();
        }
        if (checked) {
            try {
                answered(Apdu.parse(command), channel, data, sw);
            } catch (IllegalArgumentException e) {
                listener.note("guard: could not parse an answered command: " + e.getMessage());
            }
        }
    }

    /**
     * Handles SELECT and MANAGE CHANNEL: in a plain class they are the runtime's; in another class a success may
     * still have changed the selection in a way the guard cannot follow, so the affected channels become unknown.
     *
     * @return true if the command was a selection or channel command that the guard took into account
     */
    private boolean selectionChanged(byte[] raw, int cla, int ins, int number, byte[] data, int sw) {
        state(number).endHandshake();
        boolean plain = ClassBytes.plain(cla);
        if (ins == ClassBytes.SELECT && plain) {
            selected(raw, number, sw);
            return true;
        }
        if (ins == ClassBytes.MANAGE_CHANNEL && plain) {
            manageChannel(raw, data, sw);
            return true;
        }
        boolean success = sw == SW_OK || (sw >> 8) == 0x61;
        if (ins == ClassBytes.SELECT && (raw[2] & 0xFF) == 0x04 && success) {
            state(number).unknownAfter(SelectionLoss.select(raw, number, sw));
            listener.note(String.format("guard: channel %d: SELECT in class %02X answered with success; the"
                    + " selected application is unknown (strict allow-list)", number, cla));
            return true;
        }
        if (ins == ClassBytes.MANAGE_CHANNEL && sw == SW_OK) {
            String cause = SelectionLoss.manageChannel(raw, sw);
            channels.replaceAll((channelNumber, state) -> ChannelState.unknown(cause));
            listener.note(String.format("guard: MANAGE CHANNEL in class %02X answered 9000; every channel is"
                    + " treated as unknown (strict allow-list)", cla));
            return true;
        }
        return false;
    }

    /**
     * Records a card reset: logical channels are closed and the default application is selected on the basic
     * channel (ISO/IEC 7816-4:2005 5.1.1.2), which the guard treats as unknown.
     */
    public synchronized void cardReset() {
        channels.clear();
        channels.put(0, ChannelState.unknown(SelectionLoss.RESET));
        listener.note("guard: card reset, the basic channel is back to the default application (strict allow-list)");
    }

    /**
     * Records a transport failure: whether the card processed the command is unknown, so every channel falls
     * back to the strict allow-list until its next successful SELECT.
     */
    public synchronized void transportFailed() {
        channels.replaceAll((number, state) -> ChannelState.unknown(SelectionLoss.TRANSPORT_FAILURE));
        listener.note("guard: transport failure, every channel is treated as unknown (strict allow-list)");
    }

    /**
     * Opens or closes the write-access scope in which card content management commands may pass.
     *
     * @param enabled true to allow INSTALL, LOAD, DELETE and SET STATUS (still subject to the rules)
     */
    public synchronized void writeAccess(boolean enabled) {
        if (writeAccess != enabled) {
            listener.note("guard: write access " + (enabled ? "ON" : "OFF"));
        }
        writeAccess = enabled;
    }

    /**
     * Returns whether the write-access scope is open.
     *
     * @return true inside a write-access scope
     */
    public synchronized boolean writeAccess() {
        return writeAccess;
    }

    /**
     * Returns the number of commands blocked so far.
     *
     * @return the blocked command count
     */
    public synchronized int blockedCount() {
        return blocked;
    }

    /**
     * Returns the number of INITIALIZE UPDATE responses whose card cryptogram the guard verified.
     *
     * @return the verified handshake count
     */
    public synchronized int verifiedHandshakes() {
        return verifiedHandshakes;
    }

    private String strict(Apdu command, ChannelState channel, byte[] raw) {
        if (command.extended()) {
            return "extended length command outside the tests' own applets";
        }
        boolean isd = channel.issuerSecurityDomain();
        return switch (command.ins()) {
            case INS_GET_DATA -> null;
            case INS_GET_STATUS -> isd ? null : "GET STATUS" + ONLY_ISD;
            case INS_INITIALIZE_UPDATE -> isd ? initializeUpdate(command, channel) : "INITIALIZE UPDATE" + ONLY_ISD;
            case INS_EXTERNAL_AUTHENTICATE -> isd ? externalAuthenticate(command, channel, raw)
                    : failedAuthentication(channel, "EXTERNAL AUTHENTICATE without INITIALIZE UPDATE on this channel"
                    + " (authentication goes" + ONLY_ISD + ")");
            case INS_INSTALL -> isd ? install(command, channel) : "INSTALL" + ONLY_ISD;
            case INS_LOAD -> isd ? write(command, channel, "LOAD", () -> load(command, channel)) : "LOAD" + ONLY_ISD;
            case INS_DELETE -> isd ? write(command, channel, "DELETE",
                    () -> CardContentRules.delete(policy, command, command.payload())) : "DELETE" + ONLY_ISD;
            case INS_SET_STATUS -> isd ? write(command, channel, "SET STATUS",
                    () -> CardContentRules.setStatus(policy, command, command.payload())) : "SET STATUS" + ONLY_ISD;
            case ClassBytes.SELECT, ClassBytes.MANAGE_CHANNEL, ClassBytes.GET_RESPONSE -> String.format("INS %02X in"
                    + " class %02X: SELECT, MANAGE CHANNEL and GET RESPONSE pass only in the plain inter-industry"
                    + " classes 00-03 and 40-4F (no secure messaging, no command chaining)", command.ins(),
                    command.cla());
            default -> String.format("INS %02X is not on the allow-list outside the tests' own applets (never sent:"
                    + " PUT KEY, STORE DATA and every other command)", command.ins());
        };
    }

    /** The rules for INITIALIZE UPDATE and EXTERNAL AUTHENTICATE that also hold inside a test applet. */
    private String authenticationRule(Apdu command, ChannelState channel, byte[] raw) {
        return command.ins() == INS_INITIALIZE_UPDATE ? initializeUpdate(command, channel)
                : externalAuthenticate(command, channel, raw);
    }

    private String initializeUpdate(Apdu command, ChannelState channel) {
        byte[] challenge = command.data();
        if (command.extended() || command.secureMessaging() || challenge.length != 8) {
            return "INITIALIZE UPDATE with a " + challenge.length + "-byte host challenge"
                    + (command.secureMessaging() ? " and secure messaging" : "")
                    + "; the guard verifies SCP03 S8 handshakes only";
        }
        channel.handshake(new Scp03Handshake(challenge));
        return null;
    }

    private String externalAuthenticate(Apdu command, ChannelState channel, byte[] raw) {
        Scp03Handshake handshake = channel.handshake();
        String reason = handshake == null ? "EXTERNAL AUTHENTICATE without INITIALIZE UPDATE on this channel"
                : handshake.authorize(command, raw);
        return reason == null ? null : failedAuthentication(channel, reason);
    }

    private String failedAuthentication(ChannelState channel, String reason) {
        channel.authenticated(-1);
        budget.recordFailure("EXTERNAL AUTHENTICATE blocked: " + reason);
        return reason;
    }

    private String install(Apdu command, ChannelState channel) {
        int p1 = command.p1();
        boolean known = p1 == CardContentRules.FOR_LOAD || p1 == CardContentRules.FOR_INSTALL
                || p1 == CardContentRules.FOR_MAKE_SELECTABLE || p1 == CardContentRules.FOR_INSTALL_AND_MAKE_SELECTABLE;
        if (!known || command.p2() != 0) {
            return String.format("INSTALL with P1-P2 %02X%02X is not allowed (only [for load], [for install],"
                    + " [for make selectable])", p1, command.p2());
        }
        return write(command, channel, "INSTALL",
                () -> CardContentRules.install(policy, InstallFields.parse(p1, command.payload())));
    }

    private static String load(Apdu command, ChannelState channel) {
        if (command.p1() != 0x00 && command.p1() != 0x80) {
            return String.format("LOAD with P1=%02X", command.p1());
        }
        return channel.loadPending() ? null
                : "LOAD without an accepted INSTALL [for load] of a test package on this channel";
    }

    private String write(Apdu command, ChannelState channel, String name, Supplier<String> rule) {
        if (!writeAccess) {
            return name + " outside a write-access scope (card content changes only through LiveCard management"
                    + " operations)";
        }
        int level = channel.sessionLevel();
        if (command.secureMessaging() && level > 0 && (level & 0x02) != 0) {
            return name + " under C-ENC: the guard cannot inspect encrypted data; card content management runs at"
                    + " security level 01 (C-MAC)";
        }
        return rule.get();
    }

    /** A plain-class SELECT: only its standard form answered with success establishes a known context. */
    private void selected(byte[] raw, int number, int sw) {
        ChannelState channel = state(number);
        Apdu command;
        try {
            command = Apdu.parse(raw);
        } catch (IllegalArgumentException e) {
            channel.selected(Context.UNKNOWN);
            return;
        }
        if (command.p1() != 0x04) {
            return;  // not an applet selection: the selected application keeps it (JCRE 3.0.5 chapter 4)
        }
        byte[] aid = command.data();
        Context context = contextAfter(command, sw);
        if (context != channel.context()) {
            listener.note("guard: channel " + number + switch (context) {
                case TEST_APPLET -> ": test applet " + HEX.formatHex(aid) + " selected, its commands pass";
                case ISSUER_SECURITY_DOMAIN -> ": Issuer Security Domain selected";
                case UNKNOWN -> ": unknown application, strict allow-list";
            });
        }
        if (context == Context.UNKNOWN) {
            channel.unknownAfter(SelectionLoss.otherSelection(HEX.formatHex(aid), number, sw,
                    ClassBytes.standardSelect(command)));
        } else {
            channel.selected(context);
        }
    }

    /** The context a plain SELECT by DF name establishes: known only for the standard form answered with success. */
    private Context contextAfter(Apdu command, int sw) {
        boolean success = sw == SW_OK || (sw >> 8) == 0x61;
        if (!success || !ClassBytes.standardSelect(command)) {
            return Context.UNKNOWN;
        }
        byte[] aid = command.data();
        return policy.owns(aid) ? Context.TEST_APPLET
                : policy.isIssuerSecurityDomain(aid) ? Context.ISSUER_SECURITY_DOMAIN : Context.UNKNOWN;
    }

    private void manageChannel(byte[] raw, byte[] data, int sw) {
        if (sw != SW_OK) {
            return;
        }
        int p1 = raw[2] & 0xFF;
        int p2 = raw[3] & 0xFF;
        if (p1 == 0x00) {
            int opened = p2 != 0 ? p2 : data.length == 1 ? data[0] & 0xFF : -1;
            if (opened > 0) {
                channels.put(opened, new ChannelState());
            }
        } else if (p1 == 0x80) {
            int closed = p2 != 0 ? p2 : ClassBytes.channel(raw[0] & 0xFF);
            if (closed != 0) {
                channels.remove(closed);
            }
        }
    }

    private void answered(Apdu command, ChannelState channel, byte[] data, int sw) {
        switch (command.ins()) {
            case INS_INITIALIZE_UPDATE -> initializeUpdateAnswered(channel, data, sw);
            case INS_EXTERNAL_AUTHENTICATE -> externalAuthenticateAnswered(command, channel, sw);
            case INS_INSTALL -> installAnswered(command, channel, sw);
            case INS_LOAD -> {
                if (sw != SW_OK || (command.p1() & 0x80) != 0) {
                    channel.loadPending(false);
                }
            }
            case INS_DELETE -> {
                if (sw == SW_OK) {
                    byte[] aid = CardContentRules.deleteAid(command.payload());
                    listener.contentChanged(new ContentChange.Deleted(HEX.formatHex(aid), command.p2() == 0x80));
                }
            }
            default -> {
                // read-only commands change nothing the guard tracks
            }
        }
    }

    private void initializeUpdateAnswered(ChannelState channel, byte[] data, int sw) {
        Scp03Handshake handshake = channel.handshake();
        if (handshake == null) {
            return;
        }
        String failure = handshake.evaluate(policy.staticMacKey(), data, sw);
        if (failure == null) {
            verifiedHandshakes++;
            listener.note("guard: card cryptogram independently VERIFIED (" + handshake.describe() + ")");
        } else {
            budget.recordFailure(failure);
            listener.note("guard: card cryptogram NOT verified: " + failure);
        }
    }

    private void externalAuthenticateAnswered(Apdu command, ChannelState channel, int sw) {
        if (sw == SW_OK) {
            channel.authenticated(command.p1());
            listener.note(String.format("guard: secure channel session open at security level %02X", command.p1()));
            return;
        }
        channel.authenticated(-1);
        String failure = String.format("EXTERNAL AUTHENTICATE rejected by the card with SW=%04X (this attempt may"
                + " count against the card's authentication limit)", sw);
        budget.recordFailure(failure);
        listener.note("guard: " + failure);
    }

    private void installAnswered(Apdu command, ChannelState channel, int sw) {
        InstallFields fields = InstallFields.parse(command.p1(), command.payload());
        if (command.p1() == CardContentRules.FOR_LOAD) {
            channel.loadPending(sw == SW_OK);
            if (sw == SW_OK) {
                listener.contentChanged(new ContentChange.LoadFileCreated(HEX.formatHex(fields.loadFile())));
            }
        } else if (sw == SW_OK && command.p1() != CardContentRules.FOR_MAKE_SELECTABLE) {
            listener.contentChanged(new ContentChange.InstanceCreated(HEX.formatHex(fields.instance()),
                    HEX.formatHex(fields.loadFile()), HEX.formatHex(fields.module())));
        }
    }

    private ChannelState state(int channel) {
        return channels.computeIfAbsent(channel, number -> new ChannelState());
    }

    /** SELECT, MANAGE CHANNEL or GET RESPONSE in a plain class: the card runtime's commands. */
    private static boolean runtimeCommand(int cla, int ins) {
        return ClassBytes.plain(cla)
                && (ins == ClassBytes.SELECT || ins == ClassBytes.MANAGE_CHANNEL || ins == ClassBytes.GET_RESPONSE);
    }

    private GuardViolationException block(byte[] command, String reason) {
        blocked++;
        String hex = command == null ? "(null)" : HEX.formatHex(command);
        listener.note("GUARD BLOCKED (not sent): " + hex + "  reason: " + reason);
        return new GuardViolationException(hex, reason);
    }
}
