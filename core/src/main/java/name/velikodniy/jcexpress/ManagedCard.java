package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.apdu.ClassByte;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.BuildDescriptor;
import name.velikodniy.jcexpress.backend.TestCard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The card a {@link JavaCardTest} class gets: the backend's session plus the applets the run installed, kept in
 * scopes (a class, a test) so that everything installed in a scope is deleted when the scope ends. Imperative
 * installs ({@link #install(Class, AID, byte[])}) join the innermost open scope: one made in {@code @BeforeAll}
 * lives for the class, one made in {@code @BeforeEach} or a test lives for the test.
 *
 * <p>Like a PC/SC reader (javax.smartcardio), the card completes '61XX' with GET RESPONSE in the class of the
 * command and repeats a command answered '6CXX' with the exact Le (ISO/IEC 7816-4:2005 5.1.3) in
 * {@link #send(int, int, int, int, byte[], int)}; {@link #transmit(byte[])} stays raw. It knows which applet is
 * selected on the basic channel (its own SELECT commands and those the tests send), so that a test starts without a
 * new SELECT when its applet still is. {@link #history()} is the session's history from the start of the current
 * test or class.</p>
 */
final class ManagedCard implements SmartCardSession {

    /** SELECT (ISO/IEC 7816-4:2005 7.1.1). */
    private static final int INS_SELECT = 0xA4;
    /** SELECT P1: selection by DF name, the selection of an applet (ISO/IEC 7816-4:2005 Table 39). */
    private static final int P1_BY_NAME = 0x04;
    /** The note the sessions record when the card is reset ({@code EmbeddedSession}, {@code PcscSession}). */
    private static final String RESET_NOTE = "card reset";

    private final TestCard card;
    private final List<AppletDeclaration> declaredRun;
    private final Map<AID, AppletDeclaration> installed = new LinkedHashMap<>();
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private final Set<Class<?>> described = new HashSet<>();
    /** The applet this card selected on the basic channel; null when no applet of the run is known to be. */
    private AID selected;
    /** The history position after the exchange that selected {@link #selected}. */
    private long selectedAt;

    /** Instances installed in a class or a test, and where the scope's {@link #history()} starts. */
    private static final class Scope {
        private final String name;
        private final List<AppletDeclaration> applets = new ArrayList<>();
        private long historyStart;

        Scope(String name, long historyStart) {
            this.name = name;
            this.historyStart = historyStart;
        }
    }

    ManagedCard(TestCard card, List<AppletDeclaration> declaredRun) {
        this.card = card;
        this.declaredRun = new ArrayList<>(declaredRun);
    }

    /** The backend's card. */
    TestCard card() {
        return card;
    }

    /** The whole history of the session: transcripts and the live log use it. */
    APDUHistory fullHistory() {
        return card.session().history();
    }

    /**
     * Starts a scope; its history starts here until {@link #historyStartsNow()}.
     *
     * @param name what the scope is, for messages ("class CounterTest", "test increments()")
     */
    void openScope(String name) {
        scopes.push(new Scope(name, fullHistory().position()));
    }

    /** Lets {@link #history()} of the innermost scope start now: after its installs, before its SELECT. */
    void historyStartsNow() {
        currentScope();
        scopes.peek().historyStart = fullHistory().position();
    }

    /** Whether the run declares no applet and nothing was installed imperatively. */
    boolean nothingDeclared() {
        return declaredRun.isEmpty();
    }

    /**
     * Ends the innermost scope: deletes what was installed in it, the last first. Every instance is attempted;
     * the first failure is thrown with the others suppressed.
     */
    void closeScope() {
        Scope scope = scopes.pop();
        RuntimeException failure = null;
        for (int i = scope.applets.size() - 1; i >= 0; i--) {
            AppletDeclaration applet = scope.applets.get(i);
            if (!installed.containsKey(applet.instanceAid())) {
                continue;
            }
            try {
                remove(applet);
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = new IllegalStateException("Cannot delete " + applet + " at the end of the "
                            + scope.name + ": " + e.getMessage(), e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Installs a declared instance in the innermost scope. It is not selected: the backend installs it as a card
     * does, and on a GlobalPlatform card the Issuer Security Domain is selected afterwards.
     *
     * @param applet the instance
     * @throws IllegalStateException if an instance with its AID is already installed
     */
    void install(AppletDeclaration applet) {
        AppletDeclaration present = installed.get(applet.instanceAid());
        if (present != null) {
            throw new IllegalStateException("Cannot install " + applet + ": " + present + " is already installed"
                    + " with the same instance AID; give one of them an aid suffix (@InstallApplet(aid = \"...\"))");
        }
        if (!declaredRun.contains(applet)) {
            declaredRun.add(applet);
        }
        describeBuild(applet);
        try {
            card.install(applet, List.copyOf(declaredRun));
        } finally {
            selected = null;
        }
        installed.put(applet.instanceAid(), applet);
        currentScope().add(applet);
    }

    /**
     * Selects the first PER_CLASS applet of a class after its installs, so that the class's {@code @BeforeAll}
     * methods talk to it. An applet that declines the selection ({@link SelectException}) does not fail the class,
     * whose tests select their own applet: the history notes why nothing is selected.
     *
     * @param applet the first PER_CLASS instance of the class
     */
    void selectForClass(AppletDeclaration applet) {
        try {
            select(applet.instanceAid());
        } catch (SelectException e) {
            fullHistory().note("selecting the first PER_CLASS applet, " + applet + ", for @BeforeAll failed: "
                    + e.getMessage());
        }
    }

    /**
     * Selects an applet before a test unless it is the applet this card selected and nothing has selected
     * another one or reset the card since (a SELECT by AID on the basic channel or a card reset in the history),
     * so that its state, CLEAR_ON_DESELECT memory included, carries over.
     *
     * @param aid the instance AID
     */
    void selectUnlessSelected(AID aid) {
        if (!aid.equals(selected) || selectionMayHaveChangedSince(selectedAt)) {
            select(aid);
        }
    }

    /** Notes once per applet class which AIDs the build gave it, next to the AIDs of this run. */
    private void describeBuild(AppletDeclaration applet) {
        if (!described.add(applet.appletClass())) {
            return;
        }
        String name = applet.appletClass().getName();
        BuildDescriptor.of(applet.appletClass()).ifPresent(build -> fullHistory().note("build of " + name
                + ": package AID " + build.packageAid().toHex() + ", applet AID "
                + Optional.ofNullable(build.applets().get(name)).map(AID::toHex).orElse("none (not an applet of the"
                + " build)") + ", Java Card " + build.javaCardVersion() + "; this run: package AID "
                + applet.packageAid().toHex() + ", applet AID " + applet.moduleAid().toHex()));
    }

    private List<AppletDeclaration> currentScope() {
        if (scopes.isEmpty()) {
            openScope("test class run");
        }
        return scopes.peek().applets;
    }

    private void remove(AppletDeclaration applet) {
        try {
            card.delete(applet);
        } finally {
            selected = null;
        }
        installed.remove(applet.instanceAid());
    }

    // === SmartCardSession ===

    @Override
    public void install(Class<? extends Applet> appletClass) {
        install(appletClass, null, null);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        install(appletClass, aid, null);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        AppletDeclaration applet = AppletDeclaration.of(card.aids(), appletClass, aid, installParams,
                Isolation.PER_TEST);
        install(applet);
        select(applet.instanceAid());
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if no instance or more than one instance of the class is installed
     */
    @Override
    public void select(Class<? extends Applet> appletClass) {
        select(aid(appletClass));
    }

    @Override
    public void select(AID aid) {
        selected = null;
        card.session().select(aid);
        selected = aid;
        selectedAt = fullHistory().position();
    }

    @Override
    public void reset() {
        selected = null;
        card.session().reset();
    }

    @Override
    public void delete(AID aid) {
        AppletDeclaration applet = installed.get(aid);
        if (applet == null) {
            throw new IllegalStateException("No instance with AID " + aid.toHex() + " was installed by this test"
                    + " run; installed: " + installed.values());
        }
        remove(applet);
        scopes.forEach(scope -> scope.applets.remove(applet));
    }

    @Override
    public AID aid(Class<? extends Applet> appletClass) {
        List<AppletDeclaration> instances = installed.values().stream()
                .filter(applet -> applet.appletClass() == appletClass).toList();
        if (instances.size() == 1) {
            return instances.getFirst().instanceAid();
        }
        throw new IllegalStateException(instances.isEmpty() ? notInstalled(appletClass)
                : ambiguous(appletClass, instances));
    }

    @Override
    public AID aid(String suffixHex) {
        return card.aids().aid(suffixHex);
    }

    @Override
    public void deselect() {
        selected = null;
        card.deselect();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Completes '61XX' and '6CXX' as a PC/SC reader does ({@link APDUSequence}: GET RESPONSE in the class of the
     * command, the command again with the exact Le); the history holds every exchange.</p>
     */
    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        byte[] command = APDUCodec.encode(cla, ins, p1, p2, data, le);
        APDUResponse response = APDUSequence.on(card.session()).transmit(command);
        observe(command, response.sw());
        return response;
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        byte[] response = card.session().transmit(rawApdu);
        if (response.length >= 2) {
            observe(rawApdu, ((response[response.length - 2] & 0xFF) << 8) | (response[response.length - 1] & 0xFF));
        }
        return response;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The entries of the current test, from its start on (after the installs of the test, from the SELECT
     * before it); in {@code @BeforeAll} and {@code @AfterAll} methods those since the class started.</p>
     */
    @Override
    public APDUHistory history() {
        return scopes.isEmpty() ? fullHistory() : fullHistory().since(scopes.peek().historyStart);
    }

    /** Does nothing: the card belongs to the test class run and is closed after it. */
    @Override
    public void close() {
        // closed by JavaCardExtension when the test class ends
    }

    @Override
    public String toString() {
        return "card on " + Backends.name(card.mode()) + " (AID prefix " + card.aids().prefix() + ", installed "
                + installed.values() + ")";
    }

    // === selection on the basic channel ===

    /** Keeps track of the selected applet when a test sends a SELECT by AID itself. */
    private void observe(byte[] command, int sw) {
        if (!appletSelection(command)) {
            return;
        }
        selected = null;
        boolean success = sw == 0x9000 || (sw >> 8) == 0x61;
        int length = command.length > 5 ? command[4] & 0xFF : 0;
        if (!success || length == 0 || command.length < 5 + length) {
            return;
        }
        byte[] name = Arrays.copyOfRange(command, 5, 5 + length);
        installed.keySet().stream().filter(aid -> Arrays.equals(aid.toBytes(), name)).findFirst().ifPresent(aid -> {
            selected = aid;
            selectedAt = fullHistory().position();
        });
    }

    /** Whether a SELECT by AID on the basic channel or a card reset was recorded since a position. */
    private boolean selectionMayHaveChangedSince(long position) {
        APDUHistory history = fullHistory();
        if (history.oldestPosition() > position || history.notesSince(position).contains(RESET_NOTE)) {
            return true;
        }
        return history.entriesSince(position).stream().anyMatch(entry -> appletSelection(entry.command()));
    }

    /** SELECT by DF name in an interindustry class on the basic channel: the runtime selects an applet. */
    private static boolean appletSelection(byte[] command) {
        if (command.length < 4) {
            return false;
        }
        int cla = command[0] & 0xFF;
        return (command[1] & 0xFF) == INS_SELECT && (command[2] & 0xFF) == P1_BY_NAME
                && ClassByte.isInterindustry(cla) && ClassByte.channel(cla) == 0;
    }

    // === messages ===

    /** Why no instance of a class is installed now, and what to do. */
    private String notInstalled(Class<? extends Applet> appletClass) {
        String name = appletClass.getName();
        String simple = appletClass.getSimpleName();
        List<AppletDeclaration> declared = declaredRun.stream()
                .filter(applet -> applet.appletClass() == appletClass).toList();
        if (declared.stream().anyMatch(applet -> applet.isolation() == Isolation.PER_TEST)) {
            return name + " is declared with @InstallApplet for single tests (isolation = Isolation.PER_TEST, the"
                    + " default, or on a test method): such an instance exists only while a test runs, from before"
                    + " the @BeforeEach methods to after the @AfterEach methods, and none is installed now. Call"
                    + " card.aid(" + simple + ".class) in a @BeforeEach method or in the test, or declare it with"
                    + " isolation = Isolation.PER_CLASS to reach it in @BeforeAll and @AfterAll";
        }
        if (!declared.isEmpty()) {
            return name + " is declared with isolation = Isolation.PER_CLASS on a class that is not running now (a"
                    + " @Nested class): its instance exists from before that class's @BeforeAll methods to after its"
                    + " @AfterAll methods; installed: " + installed.values();
        }
        return name + " is not installed for this test; installed: " + installed.values() + ". Declare it with"
                + " @InstallApplet(" + simple + ".class)";
    }

    /**
     * The instances of a class, each with its declared aid suffix (an instance declared without one has the applet's
     * own AID, whose suffix is derived, not written in the test), which one is selected, and how to name one.
     */
    private String ambiguous(Class<? extends Applet> appletClass, List<AppletDeclaration> instances) {
        String prefix = card.aids().prefix();
        String simple = appletClass.getSimpleName();
        List<AppletDeclaration> named = instances.stream().filter(applet -> naming(applet, prefix) != null).toList();
        StringBuilder message = new StringBuilder(simple).append(" has ").append(instances.size())
                .append(" instances installed for this test: ")
                .append(instances.stream().map(applet -> describe(applet, prefix)).collect(Collectors.joining(", ")))
                .append("; a class names none of them.");
        named.stream().filter(applet -> applet.instanceAid().equals(selected)).findFirst()
                .or(() -> named.stream().findFirst()).map(applet -> naming(applet, prefix))
                .ifPresent(name -> message.append(" Name an instance by its AID: ").append(name)
                        .append(", and select it with card.select(").append(name).append(")."));
        if (named.size() < instances.size()) {
            message.append(" An instance declared without an aid suffix has none to be named by: declare one,")
                    .append(" @InstallApplet(value = ").append(simple).append(".class, aid = \"<suffix>\")");
        }
        return message.toString();
    }

    /** How a test names an instance: its declared suffix, its literal AID, or null for the applet's own AID. */
    private static String naming(AppletDeclaration applet, String prefix) {
        if (applet.instanceAid().equals(applet.moduleAid())) {
            return null;
        }
        String hex = applet.instanceAid().toHex();
        return hex.startsWith(prefix) ? "card.aid(\"" + hex.substring(prefix.length()) + "\")"
                : "AID.fromHex(\"" + hex + "\")";
    }

    /** An instance in the ambiguity message: suffix and AID, and whether it is the selected one. */
    private String describe(AppletDeclaration applet, String prefix) {
        String hex = applet.instanceAid().toHex();
        String text = applet.instanceAid().equals(applet.moduleAid())
                ? hex + " (" + applet.appletClass().getSimpleName() + "'s own AID, declared without an aid suffix)"
                : hex.startsWith(prefix) ? "\"" + hex.substring(prefix.length()) + "\" (" + hex + ")" : hex;
        return applet.instanceAid().equals(selected) ? text + " selected now" : text;
    }
}
