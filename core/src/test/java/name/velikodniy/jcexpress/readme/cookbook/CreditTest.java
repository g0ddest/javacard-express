package name.velikodniy.jcexpress.readme.cookbook;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.CREDIT;
import static name.velikodniy.jcexpress.readme.cookbook.WalletAppletTest.GET_BALANCE;

// Recipe "Your own test annotation" of TESTING.md: a composed annotation and a class that gets its card and applet
// from it. JUnit finds the extension of @JavaCardTest through @WalletTest, and the extension finds @InstallApplet
// through it.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@JavaCardTest
@InstallApplet(WalletApplet.class)
@interface WalletTest {
}

@WalletTest
class CreditTest {

    @Test
    void credits(SmartCardSession card) {
        assertThat(card.send(CREDIT.data(0x00, 0x0A))).isSuccess();
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(10);
    }
}
