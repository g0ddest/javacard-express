// Mirror of the root README.md (RootReadmeSnippetsTest keeps both in sync): the test of the Quick Start, which
// runs here on jCardSim like in the README's project.
package com.example.hello;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@JavaCardTest
@InstallApplet(HelloWorldApplet.class)          // installed before each test, deleted after it
class HelloWorldAppletTest {

    @Test
    void returnsHello(SmartCardSession card) {
        assertThat(card.send(0x80, 0x01))
                .isSuccess()
                .dataAsString().isEqualTo("Hello");
    }
}
