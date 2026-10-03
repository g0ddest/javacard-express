package name.velikodniy.jcexpress.readme.cookbook;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.GET_BALANCE;

/** Recipe "Several applets" of TESTING.md. */
@JavaCardTest
@InstallApplet(value = WalletApplet.class, aid = "0101", params = "0064")
@InstallApplet(value = WalletApplet.class, aid = "0102", params = "00C8")
class TwoWalletsTest {

    @Test
    void everyInstanceHasItsOwnBalance(SmartCardSession card) {
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(100);   // the first declared instance is selected
        card.select(card.aid("0102"));
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(200);
    }
}
