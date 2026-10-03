package name.velikodniy.jcexpress.pcsc;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import java.util.List;

/**
 * Finds the card reader a {@link PcscSession} connects to.
 */
final class PcscReaders {

    private PcscReaders() {
    }

    /**
     * Returns the first reader whose name contains the filter and that has a card present.
     *
     * @param terminals        the readers of a {@code javax.smartcardio.TerminalFactory}
     * @param readerNameFilter substring of the reader name, or null for any reader
     * @return the reader
     * @throws PcscException if there is no reader, no matching reader with a card, or the readers cannot be
     *                       listed
     */
    static CardTerminal find(CardTerminals terminals, String readerNameFilter) {
        List<CardTerminal> readers;
        try {
            readers = terminals.list();
        } catch (CardException e) {
            throw new PcscException("Cannot list PC/SC card readers", e);
        }
        if (readers.isEmpty()) {
            throw new PcscException("No PC/SC card readers found");
        }
        for (CardTerminal reader : readers) {
            boolean matches = readerNameFilter == null || reader.getName().contains(readerNameFilter);
            if (matches && isCardPresent(reader)) {
                return reader;
            }
        }
        throw new PcscException(readerNameFilter != null
                ? "No card present in a reader matching '" + readerNameFilter + "'"
                : "No card present in any reader");
    }

    private static boolean isCardPresent(CardTerminal reader) {
        try {
            return reader.isCardPresent();
        } catch (CardException e) {
            throw new PcscException("Cannot query reader '" + reader.getName() + "'", e);
        }
    }
}
