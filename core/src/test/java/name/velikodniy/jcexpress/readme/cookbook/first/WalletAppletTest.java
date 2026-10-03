package name.velikodniy.jcexpress.readme.cookbook.first;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.readme.cookbook.WalletApplet;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;   // also AssertJ's assertThat

// Recipe "The first test" of TESTING.md, as the page shows it (in its own package: the other recipes extend a class of
// the same name in the cookbook package).
@JavaCardTest
@InstallApplet(WalletApplet.class)              // a fresh instance before every test, deleted after it
class WalletAppletTest {

    @Test
    void startsEmpty(SmartCardSession card) {
        assertThat(card.send(0x80, 0x52, 0x00, 0x00, null, 2))     // GET BALANCE, Le = 2
                .isSuccess()
                .hasDataHex("0000");
    }
}
