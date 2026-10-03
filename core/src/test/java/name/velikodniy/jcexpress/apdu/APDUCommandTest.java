package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.LoggingSession;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.model.ModelApplet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link APDUCommand}: a command APDU as a value, built with factories and withers or parsed from hex in the
 * cases of ISO/IEC 7816-4:2005 5.1 (case 1, 2, 3 and 4, with short and extended length fields).
 */
class APDUCommandTest {

    @Nested
    class Building {

        @Test
        void ofGivesACase1CommandWithP1AndP2Zero() {
            APDUCommand command = APDUCommand.of(0x80, 0x10);

            assertThat(command.cla()).isEqualTo(0x80);
            assertThat(command.ins()).isEqualTo(0x10);
            assertThat(command.p1()).isZero();
            assertThat(command.p2()).isZero();
            assertThat(command.data()).isEmpty();
            assertThat(command.le()).isEqualTo(SmartCardSession.NO_LE);
            assertThat(command.toBytes()).isEqualTo(Hex.decode("80100000"));
        }

        @Test
        void withersReturnNewCommandsAndLeaveTheOriginalUnchanged() {
            APDUCommand credit = APDUCommand.of(0x80, 0x30);

            APDUCommand full = credit.p1p2(1, 2).data(0x00, 0x64).le(2);

            assertThat(full.toBytes()).isEqualTo(Hex.decode("8030010202006402"));
            assertThat(credit.toBytes()).isEqualTo(Hex.decode("80300000"));
            assertThat(APDUCommand.of(0x00, 0xA4, 0x04, 0x00).dataHex("A0 00 00 00 62").toString())
                    .isEqualTo("00A4040005A000000062");
            assertThat(credit.p1(7).p2(9).toBytes()).isEqualTo(Hex.decode("80300709"));
            assertThat(full.noLe().le()).isEqualTo(SmartCardSession.NO_LE);
            assertThat(credit.data(new byte[]{1}).data()).containsExactly(1);
        }

        @Test
        void byteConstantsOfAnAppletAreAcceptedAsHeaderBytes() {
            APDUCommand command = APDUCommand.of((byte) 0x80, (byte) 0xA4, (byte) 0xFF, (byte) 0x0C);

            assertThat(command.cla()).isEqualTo(0x80);
            assertThat(command.ins()).isEqualTo(0xA4);
            assertThat(command.p1()).isEqualTo(0xFF);
            assertThat(command.toBytes()).isEqualTo(Hex.decode("80A4FF0C"));
            assertThat(APDUCommand.of(0x80, 0x30).data(0xFF, -1).data()).containsExactly(0xFF, 0xFF);
        }

        @Test
        void valuesOutOfRangeAreRejected() {
            assertThatThrownBy(() -> APDUCommand.of(0x100, 0x10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLA");
            assertThatThrownBy(() -> APDUCommand.of(0x80, -129))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INS");
            assertThatThrownBy(() -> APDUCommand.of(0x80, 0x10).le(65_537))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("le");
            assertThatThrownBy(() -> APDUCommand.of(0x80, 0x10).data(new byte[65_536]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("65535");
            assertThatThrownBy(() -> APDUCommand.of(0x80, 0x10).data(0x100))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void theDataIsCopiedInAndOut() {
            byte[] data = {1, 2};
            APDUCommand command = APDUCommand.of(0x80, 0x30).data(data);

            data[0] = 9;
            command.data()[1] = 9;

            assertThat(command.data()).containsExactly(1, 2);
        }
    }

    @Nested
    class ValueSemantics {

        @Test
        void commandsWithTheSamePartsAreEqual() {
            APDUCommand a = APDUCommand.of(0x80, 0x30).data(0x00, 0x64).le(2);
            APDUCommand b = APDUCommand.fromHex("80 30 00 00 02 0064 02");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(a.data(0x00, 0x65));
            assertThat(a).isNotEqualTo(a.le(4));
        }

        @Test
        void toStringIsTheCommandAsHexLikeTranscriptLines() {
            assertThat(APDUCommand.of(0x80, 0x52).le(2)).hasToString("8052000002");
        }
    }

    /** Parsing follows the length fields of ISO/IEC 7816-4:2005 5.1, Table 1. */
    @Nested
    class FromHex {

        @ParameterizedTest
        @MethodSource("name.velikodniy.jcexpress.apdu.APDUCommandTest#canonicalCommands")
        void everyCaseRoundTrips_iso7816_4_5_1(String name, String hex, int dataLength, int le) {
            APDUCommand command = APDUCommand.fromHex(hex);

            assertThat(command.data()).as(name).hasSize(dataLength);
            assertThat(command.le()).as(name).isEqualTo(le);
            assertThat(Hex.encode(command.toBytes())).as(name).isEqualTo(hex);
        }

        @Test
        void anExtendedEncodingThatAShortOneCouldCarryParsesToTheSameCommand_iso7816_4_5_1() {
            APDUCommand command = APDUCommand.fromHex("80CA00CF000005");

            assertThat(command.le()).isEqualTo(5);
            assertThat(command).isEqualTo(APDUCommand.of(0x80, 0xCA, 0x00, 0xCF).le(5));
            assertThat(command.toBytes()).isEqualTo(Hex.decode("80CA00CF05"));
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                801000             | at least 4 bytes
                80300000 05 0064   | Lc
                80300000 00 0002 0064 01 | extended
                80CA00CF 0000      | extended
                80300000 00 0000 01 | Lc
                """)
        void malformedLengthFieldsAreRejected_iso7816_4_5_1(String hex, String reason) {
            assertThatThrownBy(() -> APDUCommand.fromHex(hex))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(reason)
                    .hasMessageContaining(hex.replace(" ", ""));
        }

        /** fromHex is the only static String factory, so JUnit converts String arguments with it. */
        @ParameterizedTest
        @ValueSource(strings = {"8011000002", "00A4 0400 05 A000000062"})
        void junitConvertsStringArgumentsImplicitly(APDUCommand command) {
            assertThat(command.toBytes()).hasSizeGreaterThan(4);
        }
    }

    /** send(APDUCommand) and sendHex take the path of send(cla, ins, ...): decorators see the same command. */
    @Nested
    class ThroughSessions {

        @Test
        void aLoggingSessionLogsTheCommand() {
            try (EmbeddedSession session = new EmbeddedSession()) {
                session.install(ModelApplet.class, AID.fromHex("F000000001"));
                LoggingSession logged = session.logged();

                logged.send(APDUCommand.of(0x80, 0x10).le(2));
                logged.sendHex("8011000002");

                assertThat(logged.entries()).extracting(entry -> Hex.encode(entry.command()))
                        .containsExactly("8010000002", "8011000002");
                assertThat(logged.lastEntry().response()).isEqualTo(APDUResponse.fromHex("0001 9000"));
            }
        }
    }

    static Stream<Arguments> canonicalCommands() {
        String data300 = "AB".repeat(300);
        return Stream.of(
                Arguments.of("case 1", "80100000", 0, SmartCardSession.NO_LE),
                Arguments.of("case 2S", "8011000002", 0, 2),
                Arguments.of("case 2S, Le '00'", "00C0000000", 0, 256),
                Arguments.of("case 3S", "80300000020064", 2, SmartCardSession.NO_LE),
                Arguments.of("case 4S", "00A4040007A000000062030100", 7, 256),
                Arguments.of("case 2E", "80CA00CF000400", 0, 1024),
                Arguments.of("case 2E, Le '0000'", "80CA00CF000000", 0, 65_536),
                Arguments.of("case 3E", "80DA000000012C" + data300, 300, SmartCardSession.NO_LE),
                Arguments.of("case 4E", "80DA000000012C" + data300 + "0100", 300, 256),
                Arguments.of("case 4E, Le '0000'", "80DA000000012C" + data300 + "0000", 300, 65_536));
    }
}
