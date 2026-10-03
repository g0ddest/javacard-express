package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.AcceptAnySelectApplet;
import name.velikodniy.jcexpress.fakes.ProbeApplet;
import name.velikodniy.jcexpress.fakes.StdInstallApplet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EmbeddedSession} against the {@link SmartCardSession} contract and the Java Card API.
 */
class EmbeddedSessionTest {

    private EmbeddedSession session;

    @BeforeEach
    void setUp() {
        session = new EmbeddedSession();
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void shouldInstallAndSendCommand() {
        session.install(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    @Test
    void shouldInstallWithExplicitAid() {
        AID aid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        session.install(HelloWorldApplet.class, aid);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldSelectByClass() {
        session.install(HelloWorldApplet.class);
        session.select(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldSelectByAid() {
        AID aid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        session.install(HelloWorldApplet.class, aid);
        session.select(aid);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldTransmitRawApdu() {
        session.install(HelloWorldApplet.class);
        byte[] responseBytes = session.transmit(new byte[]{(byte) 0x80, 0x01, 0x00, 0x00});
        assertThat(new APDUResponse(responseBytes)).isSuccess();
    }

    @Test
    void shouldSendWithAllParameters() {
        session.install(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x02, 0x00, 0x00, new byte[]{0x41, 0x42, 0x43});
        assertThat(response).isSuccess();
        assertThat(response).dataEquals(0x41, 0x42, 0x43);
    }

    @Test
    void shouldSendWithLe() {
        session.install(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01, 0x00, 0x00, null, 5);
        assertThat(response).isSuccess();
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    /** {@link SmartCardSession#reset()}: "equivalent to removing and reinserting the card". */
    @Nested
    class Reset {

        @Test
        void keepsAppletsAndPersistentStateAndClearsClearOnResetTransients() {
            session.install(ProbeApplet.class);
            assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x01, 0x00);
            assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x02, 0x01);

            session.reset();
            session.select(ProbeApplet.class);

            // persistent counter survives; JCSystem.CLEAR_ON_RESET transients are cleared on card reset
            assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x03, 0x00);
        }

        /** core/README.md "Complete Test Lifecycle" - shouldSurviveReset. */
        @Test
        void readmeShouldSurviveResetExample() {
            session.install(HelloWorldApplet.class);
            session.send(0x80, 0x01).requireSuccess();

            session.reset();
            session.select(HelloWorldApplet.class);
            assertThat(session.send(0x80, 0x01)).isSuccess().dataAsString().isEqualTo("Hello");
        }

        @Test
        void installedAppletCannotBeInstalledAgainAfterReset() {
            session.install(HelloWorldApplet.class);
            session.reset();
            assertThatThrownBy(() -> session.install(HelloWorldApplet.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already installed")
                    .hasMessageContaining(AID.auto(HelloWorldApplet.class).toHex());
        }
    }

    /**
     * Java Card API {@code Applet.install(byte[] bArray, short bOffset, byte bLength)}:
     * {@code bArray = [Li][instance AID][Lc][control info][La][applet data]}, bLength &le; 127
     * (GP CS 2.3.1 A.1: the application specific parameters, tag 'C9', are the applet data).
     */
    @Nested
    class InstallParameters {

        @Test
        void appletDataArrivesInTheJavaCardInstallLayout() {
            AID aid = AID.fromHex("F0000000AA");
            session.install(ProbeApplet.class, aid, new byte[]{0x11, 0x22});
            assertThat(session.send(0x80, 0x10))
                    .isSuccess()
                    .dataAsHex().isEqualTo("05" + "F0000000AA" + "00" + "02" + "1122");
        }

        @Test
        void installWithoutParametersStillPassesTheInstanceAid() {
            session.install(ProbeApplet.class);
            assertThat(session.send(0x80, 0x10))
                    .dataAsHex().isEqualTo("08" + AID.auto(ProbeApplet.class).toHex() + "00" + "00");
        }

        @Test
        void appletUsingTheStandardInstallPatternInstalls() {
            assertThatCode(() -> session.install(StdInstallApplet.class)).doesNotThrowAnyException();
            assertThat(session.send(0x80, 0x20)).isSuccess().hasDataLength(0);
        }

        @Test
        void appletDataReachesStandardInstallApplet() {
            session.install(StdInstallApplet.class, AID.fromHex("F000000010"), new byte[]{0x11, 0x22});
            assertThat(session.send(0x80, 0x20)).isSuccess().dataEquals(0x11, 0x22);
            assertThat(session.send(0x80, 0x21)).isSuccess().hasDataLength(0);
        }

        @Test
        void totalLengthIsLimitedTo127Bytes() {
            AID aid = AID.fromHex("F0000000AB"); // 5 bytes: 127 - 3 - 5 = 119 bytes of applet data fit
            assertThatThrownBy(() -> session.install(ProbeApplet.class, aid, new byte[120]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("127")
                    .hasMessageContaining("119");
            assertThatCode(() -> session.install(ProbeApplet.class, aid, new byte[119]))
                    .doesNotThrowAnyException();
        }

        @Test
        void nullParametersMeanNoAppletData() {
            session.install(ProbeApplet.class, AID.fromHex("F0000000AC"), null);
            assertThat(session.send(0x80, 0x10)).dataAsHex().isEqualTo("05F0000000AC0000");
        }

        @Test
        void duplicateAidIsRejectedWithAClearMessage() {
            session.install(HelloWorldApplet.class);
            assertThatThrownBy(() -> session.install(CounterApplet.class, AID.auto(HelloWorldApplet.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already installed");
        }
    }

    /** ISO/IEC 7816-4:2005 7.1.1: SELECT; a failed SELECT must not go unnoticed. */
    @Nested
    class Select {

        @Test
        void unknownAidIsReported() {
            session.install(HelloWorldApplet.class);
            AID unknown = AID.fromHex("A0000000FFFF");
            assertThatThrownBy(() -> session.select(unknown))
                    .isInstanceOf(SelectException.class)
                    .hasMessageContaining("A0000000FFFF")
                    .satisfies(e -> {
                        SelectException se = (SelectException) e;
                        org.assertj.core.api.Assertions.assertThat(se.aid()).isEqualTo(unknown);
                        org.assertj.core.api.Assertions.assertThat(se.sw()).isEqualTo(0x6D00);
                    });
        }

        /** The runtime forwards an unmatched SELECT to the current applet, which may answer 9000. */
        @Test
        void unknownAidAcceptedByTheCurrentAppletIsStillReported() {
            session.install(AcceptAnySelectApplet.class);
            assertThatThrownBy(() -> session.select(AID.fromHex("A0000000FFFF")))
                    .isInstanceOf(SelectException.class)
                    .hasMessageContaining("not installed");
        }

        @Test
        void selectByClassOfAnAppletThatWasNeverInstalled() {
            session.install(HelloWorldApplet.class);
            assertThatThrownBy(() -> session.select(CounterApplet.class))
                    .isInstanceOf(SelectException.class);
        }

        /** Nothing is sent: the session knows what it installed. */
        @Test
        void selectByClassOfAClassThatIsNotInstalledSaysSoAndListsTheInstalledApplets() {
            session.install(HelloWorldApplet.class, AID.fromHex("F000000001"));
            long exchanges = session.history().entries().size();

            assertThatThrownBy(() -> session.select(CounterApplet.class))
                    .isInstanceOf(SelectException.class)
                    .hasMessage("name.velikodniy.jcexpress.CounterApplet is not installed on this card (installed:"
                            + " name.velikodniy.jcexpress.HelloWorldApplet as F000000001)")
                    .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((SelectException) e).sw()).isZero());
            org.assertj.core.api.Assertions.assertThat(session.history().entries()).hasSize((int) exchanges);
        }

        @Test
        void selectByClassOnAnEmptyCard() {
            assertThatThrownBy(() -> session.select(CounterApplet.class))
                    .hasMessage("name.velikodniy.jcexpress.CounterApplet is not installed on this card (nothing is"
                            + " installed)");
        }

        /**
         * Like a Java Card runtime, jCardSim passes a SELECT that matches no applet to the selected applet, which
         * answers whatever it answers to an unknown command (often 6D00 or 6E00); the Issuer Security Domain of a
         * card answers 6A82.
         */
        @Test
        void unknownAidAnsweredByTheSelectedAppletIsExplained() {
            session.install(HelloWorldApplet.class, AID.fromHex("F000000001"));

            assertThatThrownBy(() -> session.select(AID.fromHex("A0000000FFFF")))
                    .isInstanceOf(SelectException.class)
                    .hasMessage("SELECT A0000000FFFF failed: SW=6D00. An applet with this AID is not installed on"
                            + " this card (installed: name.velikodniy.jcexpress.HelloWorldApplet as F000000001);"
                            + " jCardSim passed the SELECT to the selected applet,"
                            + " name.velikodniy.jcexpress.HelloWorldApplet, which answered 6D00 (the Issuer Security"
                            + " Domain of a card answers 6A82)");
        }

        @Test
        void unknownAidWithoutASelectedAppletIsExplained() {
            session.install(HelloWorldApplet.class, AID.fromHex("F000000001"));
            session.reset();

            assertThatThrownBy(() -> session.select(AID.fromHex("A0000000FFFF")))
                    .hasMessage("SELECT A0000000FFFF failed: SW=6999. An applet with this AID is not installed on"
                            + " this card (installed: name.velikodniy.jcexpress.HelloWorldApplet as F000000001);"
                            + " no applet was selected, so jCardSim answered 6999 (the Issuer Security Domain of a"
                            + " card answers 6A82)");
        }

        @Test
        void unknownAidAcceptedByTheSelectedAppletNamesThatApplet() {
            session.install(AcceptAnySelectApplet.class, AID.fromHex("F000000001"));

            assertThatThrownBy(() -> session.select(AID.fromHex("A0000000FFFF")))
                    .hasMessage("SELECT A0000000FFFF returned 9000, but an applet with this AID is not installed"
                            + " on this card (installed: name.velikodniy.jcexpress.fakes.AcceptAnySelectApplet as"
                            + " F000000001); jCardSim passed the SELECT to the selected applet,"
                            + " name.velikodniy.jcexpress.fakes.AcceptAnySelectApplet, which answered 9000 and stays"
                            + " selected");
        }
    }

    /**
     * jCardSim implements only the basic channel: it ignores the channel bits of CLA (the applet selected on
     * channel 0 would receive the command) and has no MANAGE CHANNEL. Such commands are rejected
     * (ISO/IEC 7816-4:2005 5.1.1.2: each channel has its own selection and security status).
     */
    @Nested
    class LogicalChannels {

        @Test
        void commandsForAnotherChannelAreRejectedInsteadOfReachingTheBasicChannel() {
            session.install(HelloWorldApplet.class);

            assertThatThrownBy(() -> session.send(0x81, 0x01))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("channel 1");
            assertThatThrownBy(() -> session.transmit(Hex.decode("4101000000")))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("channel 5");
            assertThatThrownBy(() -> LogicalChannel.basic(session, 2).select(AID.auto(HelloWorldApplet.class)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void manageChannelIsRejected() {
            session.install(HelloWorldApplet.class);

            assertThatThrownBy(() -> LogicalChannel.open(session))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("MANAGE CHANNEL");
        }

        @Test
        void basicChannelClassesStillWork() {
            session.install(HelloWorldApplet.class);

            assertThat(session.send(0x80, 0x01)).isSuccess();
            assertThat(LogicalChannel.basic(session, 0).send(0x80, 0x01)).isSuccess();
            assertThat(session.send(0x80, 0x70)).statusWord(0x6D00); // INS 70 in a proprietary class
        }
    }

    @Nested
    class Close {

        @Test
        void sessionIsUnusableAfterClose() {
            session.install(HelloWorldApplet.class);
            session.close();
            assertThatThrownBy(() -> session.send(0x80, 0x01))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
            assertThatThrownBy(() -> session.install(CounterApplet.class))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void closeIsIdempotent() {
            session.close();
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }
    /** Every session is its own card: sessions in one JVM (also on parallel threads) do not share state. */
    @Nested
    class Isolation {

        @Test
        void twoSessionsKeepTheirOwnAppletsAndState() {
            try (EmbeddedSession other = new EmbeddedSession()) {
                session.install(ProbeApplet.class);
                assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x01, 0x00);

                other.install(ProbeApplet.class);
                assertThat(other.send(0x80, 0x12)).dataEquals(0x00, 0x01, 0x00);
                assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x02, 0x01);
                assertThat(other.send(0x80, 0x12)).dataEquals(0x00, 0x02, 0x01);

                other.reset();
                assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x03, 0x01);
            }
            assertThat(session.send(0x80, 0x12)).dataEquals(0x00, 0x04, 0x01);
        }

        @Test
        void sessionsOnParallelThreadsDoNotInterfere() throws Exception {
            int threads = 4;
            int commands = 200;
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            try {
                java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    results.add(pool.submit(() -> {
                        try (EmbeddedSession card = new EmbeddedSession()) {
                            card.install(ProbeApplet.class);
                            int last = 0;
                            for (int i = 0; i < commands; i++) {
                                byte[] data = card.send(0x80, 0x12).requireSuccess().data();
                                last = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                            }
                            return last;
                        }
                    }));
                }
                for (java.util.concurrent.Future<Integer> result : results) {
                    org.assertj.core.api.Assertions.assertThat(result.get()).isEqualTo(commands);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** Every exchange is recorded, SELECT included, with notes for installs and card resets. */
    @Nested
    class History {

        @Test
        void installsSelectsCommandsAndResetsAreRecorded() {
            AID aid = AID.fromHex("F000000001");

            session.install(HelloWorldApplet.class, aid, new byte[]{0x11, 0x22});
            session.send(0x80, 0x01);
            session.reset();
            session.select(aid);

            assertThat(session.history().transcript()).isEqualTo("""
                    # install name.velikodniy.jcexpress.HelloWorldApplet as F000000001 with parameters 1122
                    C: 00A4040005F00000000100
                    R: 9000
                    C: 80010000
                    R: 48656C6C6F9000
                    # card reset
                    C: 00A4040005F00000000100
                    R: 9000
                    """);
        }

        @Test
        void rawCommandsAndFailedSelectsAreRecorded() {
            session.install(HelloWorldApplet.class);
            session.transmit(Hex.decode("80020000020102"));
            assertThatThrownBy(() -> session.select(AID.fromHex("A0000000FFFF"))).isInstanceOf(SelectException.class);

            assertThat(session.history().transcript()).endsWith("""
                    C: 80020000020102
                    R: 01029000
                    C: 00A4040006A0000000FFFF00
                    R: 6D00
                    """);
        }

        @Test
        void historyEntriesAreTheExchanges() {
            session.install(HelloWorldApplet.class);
            session.send(0x80, 0x01);

            org.assertj.core.api.Assertions.assertThat(session.history().entries())
                    .extracting(APDULogEntry::ins).containsExactly(0xA4, 0x01);
        }
    }
}
