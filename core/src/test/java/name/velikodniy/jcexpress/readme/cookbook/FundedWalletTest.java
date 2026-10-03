package name.velikodniy.jcexpress.readme.cookbook;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.GET_BALANCE;

/** Recipe "Install parameters" of TESTING.md. */
@JavaCardTest
@InstallApplet(value = WalletApplet.class, params = "0064")   // the install method reads the starting balance
class FundedWalletTest {

    @Test
    void startsWithItsInstallParameter(SmartCardSession card) {
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(100);
    }
}
