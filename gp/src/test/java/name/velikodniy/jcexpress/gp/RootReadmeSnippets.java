package name.velikodniy.jcexpress.gp;

import com.example.hello.HelloWorldApplet;
import java.nio.file.Path;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUBuilder;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.gp.CAPFile;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import name.velikodniy.jcexpress.scp.SCPKeys;

/**
 * The code blocks of the root README.md that are not complete source files, compiled against the current API
 * (the Quick Start applet and test are {@code com.example.hello}). {@link RootReadmeSnippetsTest} checks that
 * every README code line occurs here and runs {@link #toolkitWithoutJUnit()}.
 */
final class RootReadmeSnippets {

    private RootReadmeSnippets() {
    }

    /** "Use the toolkit without JUnit": runs on jCardSim. */
    static void toolkitWithoutJUnit() {
        try (SmartCardSession card = new EmbeddedSession()) {
            card.install(HelloWorldApplet.class);

            APDUResponse hello = card.send(0x80, 0x01).requireSuccess();   // throws unless SW = 9000
            System.out.println(hello.dataAsString());                        // Hello

            byte[] apdu = APDUBuilder.command().cla(0x80).ins(0x01).build();  // raw command bytes
            APDUResponse again = new APDUResponse(card.transmit(apdu));
            System.out.println(again.dataAsHex());                           // 48656C6C6F
        }
    }

    /**
     * "Load the CAP file onto a real card": compiled only. It is never called, because it would open the first
     * PC/SC reader with a card; tests must not reach real readers.
     */
    static void loadOntoRealCard() {
        try (PcscSession card = PcscSession.open()) {                         // first reader with a card
            GPSession gp = GPSession.on(card)
                .keys(SCPKeys.defaultKeys())    // the 40..4F test keys of development cards: use your card's keys
                .open();                        // SCP02 or SCP03, detected from the card
            gp.loadAndInstall(CAPFile.fromFile(Path.of("target/hello-applet-1.0-SNAPSHOT.cap")),
                    null,                       // instance AID: null installs the applet under its AID in the CAP file
                    0x00,                       // privileges
                    null);                      // install parameters
            gp.close();

            card.select(AID.fromHex("A0000000621201"));
            System.out.println(card.send(0x80, 0x01).dataAsString());       // Hello
        }
    }
}
