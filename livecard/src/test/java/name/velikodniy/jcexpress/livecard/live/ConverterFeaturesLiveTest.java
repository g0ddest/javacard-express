package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.gp.GPException;
import name.velikodniy.jcexpress.livecard.AppletInstance;
import name.velikodniy.jcexpress.livecard.Deployment;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import name.velikodniy.jcexpress.livecard.sim.JCardSimCard;
import name.velikodniy.jcexpress.livecard.sim.TestClassPath;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * LC-CONV: the converter areas that were broken before, proven on the card's JCVM. Each case deploys a test
 * applet package converted by this project's converter (configured target, default mode, off-card verifier of
 * {@code verifierSdk} unless it is {@code none}), runs a {@link Scenario} with exact expected responses on the
 * card, runs it again on jCardSim with the applet classes, and requires both to answer as expected, so the card
 * behaves exactly like jCardSim. The one documented jCardSim deviation (no rollback of aborted transactions) is
 * asserted as such.
 */
@LiveCardTest
@Order(5)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConverterFeaturesLiveTest {

    private static final String OK = "9000";
    private static final Map<TestApplet, Deployment> DEPLOYED = new EnumMap<>(TestApplet.class);

    @BeforeAll
    static void forgetDeployments() {
        DEPLOYED.clear();
    }

    @Test
    @Order(1)
    void virtualDispatch(LiveCard card) {
        assertOnCardAndJCardSim(card, TestApplet.DISPATCH, applet(card, TestApplet.DISPATCH)
                .send("abstract area(), overridden sides() of a Square(2)", "8001000000", "002C" + OK)
                .send("the same calls on a Triangle", "8001010000", "003F" + OK)
                .send("value() of Base, Middle, Leaf: super calls", "8002000000", "0001000B006F" + OK)
                .send("package-private hidden() overridden in Middle", "8003000000", "001000200020" + OK)
                .send("interface next() on Up and Down, 3 times", "8004000000", "0003FFFD" + OK)
                .send("their persistent counts continue", "8004000000", "0006FFFA" + OK));
    }

    @Test
    @Order(2)
    void exceptions(LiveCard card) {
        assertOnCardAndJCardSim(card, TestApplet.EXCEPTIONS, applet(card, TestApplet.EXCEPTIONS)
                .send("catch (ProbeException) of a CardRuntimeException subclass", "8010000000", "1055" + OK)
                .send("catch (ISOException) next to it", "8010010000", "2A88" + OK)
                .send("nothing thrown", "8010020000", "0000" + OK)
                .send("nested try/finally trace", "8011000000", "0171" + OK)
                .send("rethrow counter before", "8013000000", "0000" + OK)
                .send("ISOException caught, counted and rethrown", "8012000000", "6985")
                .send("the side effect before the rethrow persists", "8013000000", "0001" + OK)
                .send("uncaught ProbeException", "8014000000", "6F00"));
    }

    @Test
    @Order(3)
    void arrays(LiveCard card) {
        assertOnCardAndJCardSim(card, TestApplet.ARRAYS, applet(card, TestApplet.ARRAYS)
                .send("length of byte[5], short[3], boolean[4], Object[2]", "8020000000", "0005000300040002" + OK)
                .send("buf[off++] = 3 * i, then buf[off++] = off", "8021000000", "000306090C0F07" + OK)
                .send("+=, ++ and x = a[i]++ on short[] and boolean[]", "8022000000", "000600010005000501" + OK)
                .send("the same again on the persistent arrays", "8022000000", "000C0002000B001000" + OK)
                .send("Util arrayCopy, NonAtomic copy/fill, arrayCompare", "8023000000", "2030405000EEEEEE00" + OK)
                .send("checkcast of Object[] elements", "8024000000", "00070002" + OK)
                .send("field as index: a[f++], a[++f], a[f - 2] += f, f--", "8025000000", "0A0B000C000200030003" + OK)
                .send("the same again: the tally persists", "8025000000", "0A0B000C000200030006" + OK));
    }

    @Test
    @Order(4)
    void controlFlow(LiveCard card) {
        Scenario scenario = applet(card, TestApplet.FLOW)
                .send("if over more than 127 bytes, then-branch", "8030000000", shorts(20, 3, 7) + OK)
                .send("if over more than 127 bytes, else-branch", "8030010000", shorts(20, 5, -3) + OK)
                .send("loop with a backward branch over more than 127 bytes", "8033000000", shorts(180, 3, 7) + OK);
        for (int value : new int[]{0, 1, 5, 7, 8, 0x7F, -1}) {
            String result = value >= 0 && value <= 7 ? hex((short) (11 * (value + 1))) : "FFFF";
            scenario.send("dense switch (tableswitch) on " + value, String.format("8031%02X0000", value & 0xFF),
                    result + OK);
        }
        int[] cases = {-30000, -5, 1, 100, 1000, 30000};
        int[] misses = {0, 2, -30001, 0x7FFF};
        for (int i = 0; i < cases.length; i++) {
            scenario.send("sparse switch (lookupswitch) on " + cases[i], sparse(cases[i]), hex((short) (i + 1)) + OK);
        }
        for (int miss : misses) {
            scenario.send("sparse switch default for " + miss, sparse(miss), "0000" + OK);
        }
        assertOnCardAndJCardSim(card, TestApplet.FLOW, scenario);
    }

    @Test
    @Order(5)
    void statics(LiveCard card) {
        String initial = "12" + "1234" + "01" + "010203" + "A0A1" + "0102F00F7FFF" + "010001" + "0BEE" + "0000";
        String changed = "13" + "1244" + "00" + "010209" + "A0A1" + "0102F00F7FFF" + "010001" + "0BEE" + "0001";
        assertOnCardAndJCardSim(card, TestApplet.STATICS, applet(card, TestApplet.STATICS)
                .send("initial values from the StaticField component", "8040000000", initial + OK)
                .send("byte++, short += 0x10, !boolean, table[2] = 9, counter++", "8041000000", changed + OK)
                .send("the changes persist", "8040000000", changed + OK));
    }

    @Test
    @Order(6)
    void committedTransactionAndTransientMemory(LiveCard card) {
        AID applet = TestApplet.TRANSACTIONS.moduleAid(card.config());
        assertOnCardAndJCardSim(card, TestApplet.TRANSACTIONS, applet(card, TestApplet.TRANSACTIONS)
                .send("commit keeps value and journal; depth inside was 1", "8056000000", "00080301" + OK)
                .send("committed state", "8057000000", "000803" + OK)
                .send("write 5A to CLEAR_ON_DESELECT and CLEAR_ON_RESET memory", "80585A00", OK)
                .send("both hold 5A", "8059000000", "5A5A" + OK)
                .deselect().select(applet)
                .send("after another application was selected: COD cleared", "8059000000", "005A" + OK)
                .send("write 6B", "80586B00", OK)
                .reset().select(applet)
                .send("after a card reset: both cleared", "8059000000", "0000" + OK)
                .send("persistent state survives the reset", "8057000000", "000803" + OK));
    }

    @Test
    @Order(7)
    void abortedTransactionRestoresState(LiveCard card) {
        Scenario scenario = applet(card, TestApplet.TRANSACTIONS).send("value 5, journal 0; begin; 7, 9; abort",
                "8055000000", "00050000" + OK, "00070900" + OK);
        assertThat(onJCardSim(scenario, single(card.config(), TestApplet.TRANSACTIONS))).as("jCardSim deviation")
                .containsExactlyElementsOf(scenario.expectedOnJCardSim());
        assumeFalse(SimulatedCardConnector.READER.equals(card.reader()),
                "the simulated card runs on jCardSim, which does not roll back aborted transactions");
        deployed(card, TestApplet.TRANSACTIONS);
        assertThat(scenario.run(Scenario.on(card))).as("card").containsExactlyElementsOf(scenario.expected());
    }

    @Test
    @Order(8)
    void apduInputAndOutput(LiveCard card) {
        assertOnCardAndJCardSim(card, TestApplet.APDU_IO, applet(card, TestApplet.APDU_IO)
                .send("256 bytes with Le 00 (sendBytesLong)", "8035000000", pattern(256, 3, 1) + OK)
                .send("40 bytes in five sendBytes chunks", "8036000000", pattern(40, 1, 0) + OK)
                .send("Lc 255 through setIncomingAndReceive/receiveBytes", "80370000FF" + pattern(255, 5, 2), OK)
                .send("count and sum of the received bytes", "8038000000", "00FF" + patternSum(255, 5, 2) + OK));
    }

    @Test
    @Order(9)
    void shareableInterfaceAcrossPackages(LiveCard card) {
        LiveCardConfig config = card.config();
        AID server = TestApplet.SIO_SERVER.moduleAid(config);
        AID client = TestApplet.SIO_CLIENT.moduleAid(config);
        Deployment exporting = card.deploy(TestApplet.SIO_SERVER.pkg(config));
        card.deploy(TestApplet.SIO_CLIENT.pkg(config).withExportPath(exporting.exportPath()),
                AppletInstance.of(client).withParameters(server.toBytes()));
        Scenario scenario = new Scenario().select(client)
                .send("client adds 5 through the server's Ledger", "8071050000", "0005" + OK)
                .send("client adds 3", "8071030000", "0008" + OK)
                .send("server refuses parameter 2 (null)", "8072000000", "01" + OK)
                .select(server)
                .send("the server's own total", "8073000000", "0008" + OK);
        assertOnCardAndJCardSim(card, scenario, simulator -> {
            simulator.install(TestApplet.SIO_SERVER.className(), server, new byte[0]);
            simulator.install(TestApplet.SIO_CLIENT.className(), client, server.toBytes());
        });
        assertThat(card.delete(TestApplet.SIO_CLIENT.packageAid(config), true).sw()).isEqualTo(0x9000);
        assertThat(card.delete(TestApplet.SIO_SERVER.packageAid(config), true).sw()).isEqualTo(0x9000);
        assertThat(card.content().aidsUnder(config.aidPrefix())).doesNotContain(exporting.packageAid(),
                TestApplet.SIO_CLIENT.packageAid(config).toHex(), server.toHex(), client.toHex());
    }

    /**
     * A card without the optional int type rejects the package at LOAD or INSTALL ('6A80'/'6985'): reported as
     * aborted, but only when the CAP file passed the off-card verifier. Without verification
     * ({@code verifierSdk=none}) a broken CAP file cannot be ruled out, so the rejection fails the test.
     */
    @Test
    @Order(10)
    void intSupport(LiveCard card) {
        try {
            deployed(card, TestApplet.INT_OPS);
        } catch (GPException e) {
            boolean verified = card.config().verifierSdk() != null;
            if (verified && (e.statusWord() == 0x6A80 || e.statusWord() == 0x6985)) {
                abort(String.format("card has no int support: it rejected the int package with SW=%04X, and the"
                        + " CAP file passed the off-card verifier", e.statusWord()));
            }
            throw e;
        }
        assertOnCardAndJCardSim(card, TestApplet.INT_OPS, applet(card, TestApplet.INT_OPS)
                .send("100000 * -7, / -7, % -7, + (-7)^3", "808A000000",
                        ints(100000 * -7, 100000 / -7, 100000 % -7, 100000 + -7 * -7 * -7) + OK)
                .send("signed and unsigned shifts of 0x80000001", "808B000000",
                        ints(0x80000001 >> 4, 0x80000001 >>> 4, 0x80000001 << 3, 1) + OK)
                .send("int[] of 4: length, a[3], sum", "808C000000", ints(4, 0x80020003, 0x00020006) + OK)
                .send("persistent int += 0x12345", "808D000000", ints(0x12345) + OK)
                .send("again", "808D000000", ints(0x2468A) + OK)
                .send("int to short and byte, short and byte to int", "808E810000",
                        ints((short) 0x12345678, (byte) 0x12345678, (short) -2, (byte) 0x81 * 0x01000000) + OK));
    }

    /** Deploys the applet's package once per class run; the extension deletes it after the class. */
    private static void deployed(LiveCard card, TestApplet applet) {
        if (!DEPLOYED.containsKey(applet)) {
            DEPLOYED.put(applet, card.deploy(applet.pkg(card.config())));
        }
    }

    /** A scenario that starts by selecting the applet's instance. */
    private static Scenario applet(LiveCard card, TestApplet applet) {
        return new Scenario().select(applet.moduleAid(card.config()));
    }

    private static void assertOnCardAndJCardSim(LiveCard card, TestApplet applet, Scenario scenario) {
        deployed(card, applet);
        assertOnCardAndJCardSim(card, scenario, single(card.config(), applet));
    }

    private static void assertOnCardAndJCardSim(LiveCard card, Scenario scenario, Consumer<JCardSimCard> install) {
        List<String> onCard = scenario.run(Scenario.on(card));
        List<String> onJCardSim = onJCardSim(scenario, install);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(onCard).as("card").containsExactlyElementsOf(scenario.expected());
        softly.assertThat(onJCardSim).as("jCardSim").containsExactlyElementsOf(scenario.expectedOnJCardSim());
        softly.assertAll();
    }

    private static List<String> onJCardSim(Scenario scenario, Consumer<JCardSimCard> install) {
        try (JCardSimCard simulator = new JCardSimCard(TestClassPath.applets())) {
            install.accept(simulator);
            return scenario.run(Scenario.on(simulator));
        }
    }

    private static Consumer<JCardSimCard> single(LiveCardConfig config, TestApplet applet) {
        return simulator -> simulator.install(applet.className(), applet.moduleAid(config), new byte[0]);
    }

    /** FlowApplet's arithmetic on the host: rounds of acc = acc * factor + addend from 1, wrapping as short. */
    private static String shorts(int rounds, int factor, int addend) {
        short acc = 1;
        for (int i = 0; i < rounds; i++) {
            acc = (short) (acc * factor + addend);
        }
        return hex(acc);
    }

    private static String sparse(int value) {
        return String.format("8032%04X00", value & 0xFFFF);
    }

    private static String hex(short value) {
        return String.format("%04X", value & 0xFFFF);
    }

    private static String ints(int... values) {
        StringBuilder out = new StringBuilder();
        for (int value : values) {
            out.append(String.format("%08X", value));
        }
        return out.toString();
    }

    /**
     * The bytes {@code factor * i + offset} as hex. (A plain 00..FF run would contain the GP test key 40..4F,
     * which the transcript checks rightly treat as a leaked key.)
     */
    private static String pattern(int length, int factor, int offset) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append(String.format("%02X", (factor * i + offset) & 0xFF));
        }
        return out.toString();
    }

    /** The sum of the {@link #pattern} bytes, as the applet adds them up in a short. */
    private static String patternSum(int length, int factor, int offset) {
        short sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (short) ((factor * i + offset) & 0xFF);
        }
        return hex(sum);
    }
}
