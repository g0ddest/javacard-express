package name.velikodniy.jcexpress.readme.cookbook;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.CREDIT;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.DEBIT;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.GET_BALANCE;

// Recipe "A scenario across tests" of TESTING.md: one instance for the class, set up in @BeforeAll, a PIN verified in
// one test and still verified in the next.
@JavaCardTest
@InstallApplet(value = WalletApplet.class, isolation = Isolation.PER_CLASS)   // one instance for all tests
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentScenarioTest {

    @BeforeAll
    static void fund(SmartCardSession card) {
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();      // the class's applet is selected already
    }

    @Test
    @Order(1)
    void verifiesThePin(SmartCardSession card) {
        card.pin().cla(0x80).padTo(8, 0xFF).verify(1, "1234").requireSuccess();
    }

    @Test
    @Order(2)
    void debitsWithThePinOfTheFirstTest(SmartCardSession card) {
        assertThat(card.send(DEBIT.data(0x00, 0x0A))).isSuccess();   // no SELECT in between: still verified
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(90);
    }
}
