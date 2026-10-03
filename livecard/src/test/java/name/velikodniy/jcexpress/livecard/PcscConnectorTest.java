package name.velikodniy.jcexpress.livecard;

import org.junit.jupiter.api.Test;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which reader the connector picks, decided on a list of readers (fakes: no PC/SC is touched). Without the
 * setting {@code reader} it takes the only reader with a card; with cards in several readers it refuses rather
 * than guess, so the suite never talks to another card than the development card.
 */
class PcscConnectorTest {

    /** A reader that never connects. */
    private static final class FakeReader extends CardTerminal {
        private final String name;
        private final boolean card;

        FakeReader(String name, boolean card) {
            this.name = name;
            this.card = card;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Card connect(String protocol) {
            throw new UnsupportedOperationException("a fake reader");
        }

        @Override
        public boolean isCardPresent() {
            return card;
        }

        @Override
        public boolean waitForCardPresent(long timeout) {
            return card;
        }

        @Override
        public boolean waitForCardAbsent(long timeout) {
            return !card;
        }
    }

    private static final CardTerminal EMPTY = new FakeReader("ACS ACR39U 00", false);
    private static final CardTerminal DEVELOPMENT = new FakeReader("Generic EMV Smartcard Reader", true);
    private static final CardTerminal OTHER = new FakeReader("Identiv uTrust 3700 F", true);

    @Test
    void theOnlyReaderWithACardIsChosen() throws CardException {
        assertThat(PcscConnector.choose(List.of(EMPTY, DEVELOPMENT), null)).contains(DEVELOPMENT);
        assertThat(PcscConnector.choose(List.of(EMPTY), null)).isEmpty();
    }

    @Test
    void cardsInSeveralReadersNeedTheReaderSetting() throws CardException {
        assertThatThrownBy(() -> PcscConnector.choose(List.of(DEVELOPMENT, EMPTY, OTHER), null))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining("Generic EMV Smartcard Reader")
                .hasMessageContaining("Identiv uTrust 3700 F")
                .hasMessageContaining("set reader");
        assertThat(PcscConnector.choose(List.of(DEVELOPMENT, EMPTY, OTHER), "Generic")).contains(DEVELOPMENT);
    }

    @Test
    void aReaderSettingThatMatchesSeveralReadersWithCardsIsAmbiguous() {
        assertThatThrownBy(() -> PcscConnector.choose(List.of(DEVELOPMENT, OTHER), "e"))
                .isInstanceOf(LiveCardException.class).hasMessageContaining("several readers");
    }
}
