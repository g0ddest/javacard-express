package name.velikodniy.jcexpress.livecard.sim;

import name.velikodniy.jcexpress.livecard.CardConnector;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.thirdparty.ThirdPartyApplet;
import name.velikodniy.jcexpress.pcsc.PcscSession;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link CardConnector} for a {@link SimulatedCard} that stays "in the reader" between connections, like a
 * real card, reached through the project's {@link PcscSession}. The live tests run against it through the
 * JUnit configuration parameter
 * {@code jcx.livecard.connector=name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector}.
 */
public final class SimulatedCardConnector implements CardConnector {

    /** Reader name reported for the simulated card. */
    public static final String READER = SimulatedTerminal.READER;
    private static final byte[] TEST_KEY = HexFormat.of().parseHex("404142434445464748494A4B4C4D4E4F");
    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static SimulatedCard card;

    /**
     * Creates the connector (JUnit instantiates it by class name).
     */
    public SimulatedCardConnector() {
        // the card is shared
    }

    /**
     * Inserts a new card with the GP test keys.
     *
     * @return the new card
     */
    public static synchronized SimulatedCard insertNewCard() {
        return insertNewCard(TEST_KEY);
    }

    /**
     * Inserts a new card; the test applets and the third-party applets resolve under any AID prefix.
     *
     * @param staticKey the card's ISD key (ENC = MAC = DEK)
     * @return the new card
     */
    public static synchronized SimulatedCard insertNewCard(byte[] staticKey) {
        return insertNewCard(staticKey, module -> TestApplet.byModuleAid(module).map(TestApplet::className)
                .or(() -> ThirdPartyApplet.byModuleAid(module).map(ThirdPartyApplet::className)));
    }

    /**
     * Inserts a new card whose applets are found by the given function.
     *
     * @param staticKey     the card's ISD key (ENC = MAC = DEK)
     * @param appletClasses finds the applet class name of a module AID (hex)
     * @return the new card
     */
    public static synchronized SimulatedCard insertNewCard(byte[] staticKey,
                                                           Function<String, Optional<String>> appletClasses) {
        card = new SimulatedCard(staticKey, applets(appletClasses, TestClassPath::applets));
        CONNECTIONS.set(0);
        return card;
    }

    /**
     * The applets of an inserted card, with the class path read when a package is loaded (as
     * {@link SimulatedApplets#classPath()} specifies): a third-party applet is often built after the card was
     * inserted, and a class path taken at insertion lacked it (the live suite failed when its class ran first, as
     * in an IDE or alone).
     *
     * @param appletClasses finds the applet class name of a module AID (hex)
     * @param classPath     the class path at the time of a load
     * @return the applets
     */
    static SimulatedApplets applets(Function<String, Optional<String>> appletClasses, Supplier<List<Path>> classPath) {
        return new SimulatedApplets() {
            @Override
            public Optional<String> className(String moduleAid) {
                return appletClasses.apply(moduleAid);
            }

            @Override
            public List<Path> classPath() {
                return List.copyOf(classPath.get());
            }
        };
    }

    /**
     * Returns the card in the reader.
     *
     * @return the card, inserting a new one if there is none
     */
    public static synchronized SimulatedCard card() {
        return card != null ? card : insertNewCard();
    }

    /**
     * Returns the number of connections made since the card was inserted.
     *
     * @return the count
     */
    public static int connections() {
        return CONNECTIONS.get();
    }

    @Override
    public Presence probe(LiveCardConfig config) {
        return new Presence(true, "simulated card in '" + READER + "'");
    }

    /**
     * Connects through the project's {@link PcscSession} (default options: exclusive access, any protocol) to a
     * {@link SimulatedTerminal} holding the card, as {@link name.velikodniy.jcexpress.livecard.PcscConnector}
     * does with a real reader.
     */
    @Override
    public Connection connect(LiveCardConfig config) {
        CONNECTIONS.incrementAndGet();
        PcscSession session = PcscSession.open(new SimulatedTerminal(card()), PcscSession.Options.defaults());
        return new Connection(session, READER, SimulatedCard.ATR, session.getProtocol());
    }
}
