package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.pcsc.PcscException;
import name.velikodniy.jcexpress.pcsc.PcscSession;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Connects to a real card through PC/SC ({@code javax.smartcardio}) with
 * {@link PcscSession} (exclusive access, any protocol).
 *
 * <p>This is the only class of the module that reaches the PC/SC subsystem. It does so only when both the
 * given settings and the settings of this JVM ({@link LiveCardConfig#load()}, where only the system property
 * {@code jcx.livecard.enabled} can switch live-card mode on) say {@code enabled=true}; settings built in code cannot
 * open the reader on their own. With live-card tests disabled not even the reader list is read.</p>
 */
public final class PcscConnector implements CardConnector {

    /**
     * Creates the connector.
     */
    public PcscConnector() {
        // stateless
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if live-card tests are disabled in {@code config}
     */
    @Override
    public Presence probe(LiveCardConfig config) {
        requireEnabled(config);
        try {
            return find(config.reader())
                    .map(reader -> new Presence(true, "card present in reader '" + reader.getName() + "'"))
                    .orElseGet(() -> new Presence(false, noCard(config)));
        } catch (LiveCardException e) {
            return new Presence(false, e.getMessage());
        } catch (CardException | RuntimeException e) {
            return new Presence(false, "PC/SC is not available: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if live-card tests are disabled in {@code config}
     */
    @Override
    public Connection connect(LiveCardConfig config) {
        requireEnabled(config);
        CardTerminal reader;
        try {
            reader = find(config.reader()).orElseThrow(() -> new LiveCardException(noCard(config)));
        } catch (CardException e) {
            throw new LiveCardException("Cannot list PC/SC readers: " + e.getMessage(), e);
        }
        try {
            PcscSession session = PcscSession.open(reader, PcscSession.Options.defaults());
            return new Connection(session, reader.getName(), Hex.encode(session.getATR()), session.getProtocol());
        } catch (PcscException e) {
            throw new LiveCardException("Cannot connect to the card in reader '" + reader.getName() + "': "
                    + e.getMessage(), e);
        }
    }

    private static void requireEnabled(LiveCardConfig config) {
        if (!config.enabled() || !LiveCardConfig.load().enabled()) {
            throw new IllegalStateException("Live-card tests are disabled (the JVM system property"
                    + " jcx.livecard.enabled is not true): no PC/SC reader is accessed");
        }
    }

    private static Optional<CardTerminal> find(String readerFilter) throws CardException {
        return choose(TerminalFactory.getDefault().terminals().list(), readerFilter);
    }

    /**
     * Chooses the reader: the one that holds a card and whose name contains {@code readerFilter} (any name
     * without a filter). With cards in several such readers it refuses rather than guess which card is the
     * development card.
     *
     * @param readers      the readers
     * @param readerFilter a substring of the reader name, or null
     * @return the reader, or empty if no matching reader holds a card
     * @throws CardException     if a reader cannot tell whether it holds a card
     * @throws LiveCardException if several matching readers hold a card
     */
    static Optional<CardTerminal> choose(List<CardTerminal> readers, String readerFilter) throws CardException {
        List<CardTerminal> withCard = new ArrayList<>();
        for (CardTerminal reader : readers) {
            if ((readerFilter == null || reader.getName().contains(readerFilter)) && reader.isCardPresent()) {
                withCard.add(reader);
            }
        }
        if (withCard.size() > 1) {
            throw new LiveCardException("Cards in several readers " + withCard.stream().map(CardTerminal::getName)
                    .toList() + (readerFilter == null ? "" : " whose names contain '" + readerFilter + "'")
                    + ": set reader to a part of the name that only the reader with the development card has");
        }
        return withCard.stream().findFirst();
    }

    private static String noCard(LiveCardConfig config) {
        return config.reader() == null ? "no card present in any PC/SC reader"
                : "no card present in a PC/SC reader whose name contains '" + config.reader() + "'";
    }
}
