package name.velikodniy.jcexpress.embedded.gp;

import javacard.framework.ISOException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The rules of the card side of SCP03 as a Security Domain applies them for an application: GlobalPlatform Card
 * Specification v2.3 Amendment D v1.1.2 (amdD), GlobalPlatform Card Specification v2.3.1 (gpcs) and the
 * GlobalPlatform Card API 1.8 (gpapi). The off-card side is {@link Scp03Host}.
 */
class Scp03SessionRulesTest {

    private static final AID APP = AID.fromHex("F000000001");
    private static final AID OTHER = AID.fromHex("F000000002");
    private static final byte[] ECHO = Hex.decode("8010000004CAFEBABE");

    private final SecurityDomain domain = SecurityDomain.withTestKeys();
    private final Scp03Session session = domain.session();
    private final Scp03Host host = Scp03Host.withTestKeys();

    /** Sends a command of the secure channel initiation; returns the response data. */
    private byte[] process(byte[] command) {
        byte[] buffer = Arrays.copyOf(command, 300);
        short length = session.process(APP, buffer, 0, command.length, 5);
        return Arrays.copyOfRange(buffer, 5, 5 + length);
    }

    private void open(int level) {
        assertThat(host.receive(process(host.initializeUpdate(0)))).as("card cryptogram").isTrue();
        assertThat(process(host.externalAuthenticate(level))).isEmpty();
    }

    private byte[] unwrap(byte[] command) {
        byte[] buffer = Arrays.copyOf(command, 300);
        short length = session.unwrap(APP, buffer, 0, command.length);
        return Arrays.copyOf(buffer, length);
    }

    /** Protects response data followed by a status word; returns what the applet would send. */
    private byte[] wrap(String dataAndStatus) {
        byte[] response = Hex.decode(dataAndStatus);
        byte[] buffer = Arrays.copyOf(response, 300);
        short length = session.wrap(APP, buffer, 0, response.length);
        return Arrays.copyOf(buffer, length);
    }

    private static int sw(ThrowingCallable call) {
        ISOException thrown = catchThrowableOfType(ISOException.class, call);
        assertThat(thrown).as("an ISOException").isNotNull();
        return thrown.getReason() & 0xFFFF;
    }

    @Nested
    class InitializeUpdate {

        @Test
        void answersDiversificationDataKeyInformationChallengeAndCryptogram_amdD_Table_7_3() {
            byte[] response = process(host.initializeUpdate(0));

            assertThat(response).hasSize(29);
            assertThat(Hex.encode(Arrays.copyOf(response, 13))).isEqualTo("00000000000000000000" + "FF0300");
            assertThat(host.receive(response)).as("card cryptogram, amdD 6.2.2.2").isTrue();
        }

        @Test
        void theCardChallengeIsNewForEverySession_amdD_5_2() {
            byte[] first = process(host.initializeUpdate(0));
            byte[] second = process(host.initializeUpdate(0));

            assertThat(Arrays.copyOfRange(first, 13, 21)).isNotEqualTo(Arrays.copyOfRange(second, 13, 21));
        }

        @Test
        void keyVersion00AndTheDomainsOwnAreAcceptedAnotherIs6A88_amdD_7_1_1_3() {
            domain.keyVersion(0x30);

            assertThat(process(host.initializeUpdate(0x00))[10]).isEqualTo((byte) 0x30);
            assertThat(process(host.initializeUpdate(0x30))[10]).isEqualTo((byte) 0x30);
            assertThat(sw(() -> process(host.initializeUpdate(0x31)))).isEqualTo(0x6A88);
        }

        @Test
        void aKeyIdentifierOtherThan00Is6A86_amdD_7_1_1_4() {
            byte[] command = host.initializeUpdate(0);
            command[3] = 0x01;

            assertThat(sw(() -> process(command))).isEqualTo(0x6A86);
        }

        @Test
        void aHostChallengeThatIsNot8BytesIs6700_amdD_Table_7_2() {
            assertThat(sw(() -> process(Hex.decode("805000000411223344")))).isEqualTo(0x6700);
            assertThat(sw(() -> process(Hex.decode("80500000")))).isEqualTo(0x6700);
        }

        @Test
        void aCommandThatIsNotOfTheProtocolIs6E00Or6D00_gpapi_SecureChannel_processSecurity() {
            assertThat(sw(() -> process(Hex.decode("00A4040000")))).as("not a GlobalPlatform class").isEqualTo(0x6E00);
            assertThat(sw(() -> process(Hex.decode("80CA006600")))).as("another instruction").isEqualTo(0x6D00);
            assertThat(sw(() -> process(Hex.decode("84500000081122334455667788")))).as("INITIALIZE UPDATE with"
                    + " secure messaging, amdD Table 7-2").isEqualTo(0x6E00);
            assertThat(sw(() -> process(Hex.decode("80820000101122334455667788990011223344556677"))))
                    .as("EXTERNAL AUTHENTICATE without secure messaging, amdD Table 7-5").isEqualTo(0x6E00);
        }

        @Test
        void aCommandThatIsNotOfTheProtocolLeavesTheSessionOpen() {
            open(0x01);

            sw(() -> process(Hex.decode("80CA006600")));

            assertThat(session.currentLevel()).isEqualTo(0x81);
        }

        @Test
        void aNewInitializeUpdateTerminatesTheSession_amdD_5_6() {
            open(0x01);

            process(host.initializeUpdate(0));

            assertThat(session.currentLevel()).isZero();
            assertThat(session.sessionLevel()).isZero();
            assertThat(unwrap(ECHO)).as("no session: nothing is checked").isEqualTo(ECHO);
        }

        @Test
        void evenARefusedInitializeUpdateTerminatesTheSession_amdD_5_6() {
            open(0x01);

            sw(() -> process(host.initializeUpdate(0x31)));

            assertThat(session.currentLevel()).isZero();
        }

        @Test
        void thePseudoRandomChallengeFollowsTheSequenceCounterAndTheApplication_amdD_6_2_2_1() {
            domain.scp03Option(SecurityDomain.OPTION_PSEUDO_RANDOM_CHALLENGE);
            byte[] hostChallenge = Hex.decode("80500000081122334455667788");

            byte[] first = process(hostChallenge);
            byte[] second = process(hostChallenge);
            byte[] buffer = Arrays.copyOf(hostChallenge, 64);
            short length = session.process(OTHER, buffer, 0, 13, 5);
            domain.sequenceCounter(0);
            byte[] again = process(hostChallenge);

            assertThat(first).hasSize(32);
            assertThat(Hex.encode(Arrays.copyOfRange(first, 10, 13))).isEqualTo("FF0310");
            assertThat(Hex.encode(Arrays.copyOfRange(first, 29, 32))).isEqualTo("000001");
            assertThat(Hex.encode(Arrays.copyOfRange(second, 29, 32))).isEqualTo("000002");
            assertThat(Arrays.copyOfRange(second, 13, 21)).isNotEqualTo(Arrays.copyOfRange(first, 13, 21));
            assertThat(length).isEqualTo((short) 32);
            assertThat(again).as("same counter, same application, same challenge").isEqualTo(first);
            assertThat(domain.sequenceCounter()).isEqualTo(1);
        }

        @Test
        void aSequenceCounterAtItsMaximumRefusesTheCommand_amdD_6_2_2_1() {
            domain.scp03Option(SecurityDomain.OPTION_PSEUDO_RANDOM_CHALLENGE);
            domain.sequenceCounter(0xFFFFFF);

            assertThat(sw(() -> process(host.initializeUpdate(0)))).isEqualTo(0x6985);
        }
    }

    @Nested
    class ExternalAuthenticate {

        @ParameterizedTest
        @CsvSource({"00,00", "00,01", "00,03", "20,11", "20,13", "60,33", "70,33", "60,01"})
        void opensTheSessionAtTheLevelOfP1_amdD_5_5_and_Table_7_6(String option, String level) {
            domain.scp03Option(Integer.parseInt(option, 16));

            open(Integer.parseInt(level, 16));

            assertThat(session.currentLevel()).isEqualTo(0x80 | Integer.parseInt(level, 16));
            assertThat(session.sessionLevel()).isEqualTo(session.currentLevel());
            assertThat(domain.externalAuthentications()).isEqualTo(1);
            assertThat(domain.failedAuthentications()).isZero();
        }

        @ParameterizedTest
        @CsvSource({"00,11", "00,13", "00,33", "20,33", "70,02", "70,10", "70,30", "70,31", "70,81", "70,07"})
        void aLevelSCP03DoesNotDefineOrTheOptionDoesNotAllowIs6A86_amdD_Table_5_1_and_7_6(String option, String level) {
            domain.scp03Option(Integer.parseInt(option, 16));
            assertThat(host.receive(process(host.initializeUpdate(0)))).isTrue();

            assertThat(sw(() -> process(host.externalAuthenticate(Integer.parseInt(level, 16))))).isEqualTo(0x6A86);
            assertThat(session.currentLevel()).isZero();
            assertThat(domain.failedAuthentications()).isEqualTo(1);
        }

        @Test
        void withoutAPrecedingInitializeUpdateIs6985_amdD_7_1_2_1() {
            host.receive(process(host.initializeUpdate(0)));
            byte[] command = host.externalAuthenticate(0x01);
            session.terminate();

            assertThat(sw(() -> process(command))).isEqualTo(0x6985);
            assertThat(domain.externalAuthentications()).isEqualTo(1);
            assertThat(domain.failedAuthentications()).isEqualTo(1);
        }

        @Test
        void aWrongHostCryptogramIs6300_amdD_Table_7_7() {
            host.receive(process(host.initializeUpdate(0)));
            byte[] command = host.externalAuthenticate(0x01);
            command[5] ^= 0x01;

            assertThat(sw(() -> process(command))).isEqualTo(0x6300);
            assertThat(domain.failedAuthentications()).isEqualTo(1);
            assertThat(session.currentLevel()).isZero();
        }

        @Test
        void aWrongMacOnTheRightCryptogramIs6982_amdD_6_2_3_gpcs_11_1_3() {
            host.receive(process(host.initializeUpdate(0)));
            byte[] command = host.externalAuthenticate(0x01);
            command[20] ^= 0x01;

            assertThat(sw(() -> process(command))).isEqualTo(0x6982);
            assertThat(domain.failedAuthentications()).isEqualTo(1);
        }

        @Test
        void theCryptogramCanBeTriedOncePerInitializeUpdate_amdD_5_2() {
            host.receive(process(host.initializeUpdate(0)));
            byte[] right = host.externalAuthenticate(0x01);
            byte[] wrong = right.clone();
            wrong[5] ^= 0x01;
            sw(() -> process(wrong));

            assertThat(sw(() -> process(right))).isEqualTo(0x6985);
            assertThat(domain.externalAuthentications()).isEqualTo(2);
            assertThat(domain.failedAuthentications()).isEqualTo(2);
        }

        @Test
        void wrongLengthAndP2AreRefused_amdD_Table_7_5() {
            host.receive(process(host.initializeUpdate(0)));
            byte[] shortCommand = Arrays.copyOf(host.externalAuthenticate(0x01), 13);
            shortCommand[4] = 0x08;
            assertThat(sw(() -> process(shortCommand))).isEqualTo(0x6700);

            host.receive(process(host.initializeUpdate(0)));
            byte[] p2 = host.externalAuthenticate(0x01);
            p2[3] = 0x01;
            assertThat(sw(() -> process(p2))).isEqualTo(0x6A86);
        }

        @Test
        void inAnOpenSessionItIsRefusedAndTheSessionStays_amdD_7_1_2_1() {
            open(0x01);
            byte[] again = host.externalAuthenticate(0x01);

            assertThat(sw(() -> process(again))).isEqualTo(0x6985);
            assertThat(session.currentLevel()).isEqualTo(0x81);
        }

        @Test
        void anotherApplicationCannotCompleteTheInitiation_gpapi_SecureChannel() {
            host.receive(process(host.initializeUpdate(0)));
            byte[] command = Arrays.copyOf(host.externalAuthenticate(0x01), 64);

            assertThat(sw(() -> session.process(OTHER, command, 0, 21, 5))).isEqualTo(0x6985);
            assertThat(process(Arrays.copyOf(command, 21))).as("the application that sent INITIALIZE UPDATE").isEmpty();
        }
    }

    @Nested
    class Unwrap {

        @Test
        void withoutASessionTheCommandIsReturnedAsItIs_amdD_5_6_gpapi_SecureChannel_unwrap() {
            byte[] protectedLooking = Hex.decode("8410000008CAFEBABE00000000");

            assertThat(unwrap(ECHO)).isEqualTo(ECHO);
            assertThat(unwrap(protectedLooking)).isEqualTo(protectedLooking);
        }

        @Test
        void atLevelAuthenticatedOnlyTheCommandIsLeftAsItIs_gpapi_SecureChannel_unwrap() {
            open(0x00);
            byte[] withSecureMessagingClass = Hex.decode("8410000004CAFEBABE");

            assertThat(unwrap(withSecureMessagingClass)).isEqualTo(withSecureMessagingClass);
            assertThat(unwrap(ECHO)).isEqualTo(ECHO);
            assertThat(session.currentLevel()).isEqualTo(0x80);
        }

        @Test
        void theCMacIsVerifiedAndRemovedLcAndClassByteAreRewritten_amdD_6_2_4() {
            open(0x01);
            byte[] wrapped = host.wrap(ECHO);

            assertThat(wrapped).hasSize(ECHO.length + 8);
            assertThat(wrapped[0]).isEqualTo((byte) 0x84);
            assertThat(wrapped[4]).isEqualTo((byte) 12);
            assertThat(unwrap(wrapped)).isEqualTo(ECHO);
        }

        @Test
        void aCommandWithoutDataKeepsAnLcOf00_gpapi_SecureChannel_unwrap() {
            open(0x03);

            assertThat(Hex.encode(unwrap(host.wrap(Hex.decode("80120000"))))).isEqualTo("8012000000");
        }

        @Test
        void theDataFieldIsDecryptedAndItsPaddingRemoved_amdD_6_2_6() {
            open(0x03);
            byte[] sixteen = Hex.decode("8010000010000102030405060708090A0B0C0D0E0F");

            byte[] wrapped = host.wrap(ECHO);

            assertThat(wrapped).as("16 bytes of ciphertext and the C-MAC").hasSize(5 + 16 + 8);
            assertThat(unwrap(wrapped)).isEqualTo(ECHO);
            assertThat(unwrap(host.wrap(Hex.decode("80120000")))).as("counted, not encrypted").hasSize(5);
            assertThat(unwrap(host.wrap(sixteen))).as("the third command, with the ICV of counter 3").isEqualTo(sixteen);
        }

        @Test
        void commandsAreChainedByTheirMacs_amdD_5_3() {
            open(0x01);
            byte[] first = host.wrap(ECHO);
            byte[] second = host.wrap(ECHO);

            assertThat(second).isNotEqualTo(first);
            assertThat(unwrap(first)).isEqualTo(ECHO);
            assertThat(unwrap(second)).isEqualTo(ECHO);
        }

        @Test
        void aWrongMacIs6982AndAbortsTheSession_amdD_5_6_gpcs_10_2_3() {
            open(0x01);
            byte[] wrapped = host.wrap(ECHO);
            wrapped[wrapped.length - 1] ^= 0x01;

            assertThat(sw(() -> unwrap(wrapped))).isEqualTo(0x6982);
            assertThat(session.currentLevel()).as("Current Security Level").isZero();
            assertThat(session.sessionLevel()).as("Session Security Level is kept").isEqualTo(0x81);
        }

        @Test
        void aReplayedCommandIs6982_amdD_5_3() {
            open(0x01);
            byte[] wrapped = host.wrap(ECHO);
            unwrap(wrapped);

            assertThat(sw(() -> unwrap(wrapped))).isEqualTo(0x6982);
        }

        @Test
        void anUnprotectedCommandInACMacSessionIs6982AndAbortsTheSession_amdD_5_6_gpcs_10_2_3() {
            open(0x01);

            assertThat(sw(() -> unwrap(ECHO))).isEqualTo(0x6982);
            assertThat(session.currentLevel()).isZero();
        }

        @Test
        void aLengthThatDoesNotMatchLcOrLeavesNoRoomForTheMacIs6982() {
            open(0x01);
            byte[] wrapped = host.wrap(ECHO);
            assertThat(sw(() -> session.unwrap(APP, Arrays.copyOf(wrapped, 64), 0, wrapped.length - 1))).isEqualTo(0x6982);

            open(0x01);
            assertThat(sw(() -> unwrap(Hex.decode("8410000004CAFEBABE")))).isEqualTo(0x6982);
        }

        @Test
        void anEncryptedFieldWithoutPaddingOrOfAWrongLengthIs6982_amdD_6_2_6() {
            open(0x03);
            byte[] unpadded = CryptoUtil.aesCbcEncrypt(host.sessionEncryptionKey(), new byte[16], host.nextCommandIcv());
            assertThat(sw(() -> unwrap(host.withMac(ECHO, unpadded)))).as("no '80' in the plain text").isEqualTo(0x6982);
            assertThat(session.currentLevel()).isZero();

            open(0x03);
            host.nextCommandIcv();
            assertThat(sw(() -> unwrap(host.withMac(ECHO, new byte[5])))).as("not a block multiple").isEqualTo(0x6982);
        }

        @Test
        void anAbortedSessionRefusesEveryUseUntilItIsTerminated_gpcs_10_2_3_gpapi_SecureChannel() {
            open(0x01);
            sw(() -> unwrap(ECHO));

            assertThat(sw(() -> unwrap(ECHO))).isEqualTo(0x6985);
            assertThat(sw(() -> unwrap(host.wrap(ECHO)))).isEqualTo(0x6985);
            assertThat(sw(() -> wrap("CAFE9000"))).isEqualTo(0x6985);
            assertThat(sw(() -> session.sensitiveData(APP, false, new byte[16], 0, 16))).isEqualTo(0x6985);
            assertThat(session.level(APP)).isZero();

            session.reset(APP);

            assertThat(session.sessionLevel()).isZero();
            assertThat(unwrap(ECHO)).as("terminated: no session").isEqualTo(ECHO);
        }

        @Test
        void aNewInitiationEndsAnAbortedSession_amdD_5_6() {
            open(0x01);
            sw(() -> unwrap(ECHO));

            open(0x01);

            assertThat(unwrap(host.wrap(ECHO))).isEqualTo(ECHO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"84", "8C", "88"})
        void anyIndicationOfSecureMessagingIsAcceptedAndCleared_gpcs_11_1_4_amdD_6_2_4(String cla) {
            open(0x01);
            byte[] wrapped = host.wrap(ECHO);
            wrapped[0] = (byte) Integer.parseInt(cla, 16);

            assertThat(unwrap(wrapped)).as("the MAC covers the class byte '84'").isEqualTo(ECHO);
        }
    }

    @Nested
    class Wrap {

        @Test
        void withoutASessionOnlyTheStatusBytesAreRemoved_gpapi_SecureChannel_wrap() {
            assertThat(Hex.encode(wrap("CAFEBABE9000"))).isEqualTo("CAFEBABE");
            assertThat(wrap("9000")).isEmpty();
        }

        @Test
        void aLevelWithoutRMacAddsNothing_gpapi_SecureChannel_wrap() {
            open(0x03);
            unwrap(host.wrap(ECHO));

            assertThat(Hex.encode(wrap("CAFEBABE9000"))).isEqualTo("CAFEBABE");
        }

        @Test
        void theRMacCoversChainingValueDataAndStatus_amdD_6_2_5() {
            domain.scp03Option(SecurityDomain.OPTION_R_MAC);
            open(0x11);
            unwrap(host.wrap(ECHO));

            byte[] response = wrap("CAFEBABE9000");

            assertThat(response).hasSize(12);
            assertThat(Hex.encode(Arrays.copyOf(response, 4))).as("data stay plain").isEqualTo("CAFEBABE");
            assertThat(Hex.encode(host.unwrap(Scp03Host.concat(response, Hex.decode("9000"))))).isEqualTo("CAFEBABE9000");
        }

        @Test
        void aResponseWithoutDataGetsTheRMacOnly_amdD_6_2_5_and_6_2_7() {
            domain.scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);
            open(0x33);
            unwrap(host.wrap(ECHO));

            byte[] response = wrap("9000");

            assertThat(response).hasSize(8);
            assertThat(Hex.encode(host.unwrap(Scp03Host.concat(response, Hex.decode("9000"))))).isEqualTo("9000");
        }

        @Test
        void responseDataAreEncryptedThenMaced_amdD_6_2_7() {
            domain.scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);
            open(0x33);
            unwrap(host.wrap(ECHO));
            byte[] first = wrap("CAFEBABE9000");
            unwrap(host.wrap(ECHO));
            byte[] second = wrap("CAFEBABE9000");

            assertThat(first).as("one block of ciphertext and the R-MAC").hasSize(24);
            assertThat(second).as("another counter, another ICV").isNotEqualTo(first);
            assertThat(Hex.encode(host.unwrap(Scp03Host.concat(second, Hex.decode("9000"))))).isEqualTo("CAFEBABE9000");
        }

        @Test
        void aWarningIsProtectedAnErrorStatusWordGoesOutAlone_amdD_6_2_5() {
            domain.scp03Option(SecurityDomain.OPTION_R_MAC);
            open(0x11);
            unwrap(host.wrap(ECHO));

            byte[] warning = wrap("CAFE6310");
            assertThat(warning).hasSize(10);
            assertThat(Hex.encode(host.unwrap(Scp03Host.concat(warning, Hex.decode("6310"))))).isEqualTo("CAFE6310");
            assertThat(wrap("CAFE6A80")).as("no data, no R-MAC").isEmpty();
        }

        @Test
        void fewerThanTheTwoStatusBytesIs6700() {
            assertThat(sw(() -> wrap("90"))).isEqualTo(0x6700);
        }
    }

    @Nested
    class SensitiveData {

        @Test
        void isDecryptedAndEncryptedWithKeyDekInCbcModeWithAZeroIcv_amdD_6_2_8() throws Exception {
            open(0x00);
            byte[] clear = Hex.decode("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
            Cipher cbc = Cipher.getInstance("AES/CBC/NoPadding");
            cbc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(domain.keyDek(), "AES"), new IvParameterSpec(new byte[16]));
            byte[] encrypted = cbc.doFinal(clear);

            byte[] buffer = Scp03Host.concat(new byte[3], encrypted, new byte[3]);
            assertThat(session.sensitiveData(APP, false, buffer, 3, 32)).isEqualTo((short) 32);
            assertThat(Arrays.copyOfRange(buffer, 3, 35)).as("no padding is removed").isEqualTo(clear);
            assertThat(session.sensitiveData(APP, true, buffer, 3, 32)).isEqualTo((short) 32);
            assertThat(Arrays.copyOfRange(buffer, 3, 35)).isEqualTo(encrypted);
            assertThat(Arrays.copyOf(buffer, 3)).as("bytes around the data stay").isEqualTo(new byte[3]);
        }

        @Test
        void needsAnOpenSessionAndWholeBlocks_gpapi_SecureChannel_decryptData() {
            assertThat(sw(() -> session.sensitiveData(APP, false, new byte[16], 0, 16))).isEqualTo(0x6985);
            assertThat(sw(() -> session.sensitiveData(APP, true, new byte[16], 0, 16))).isEqualTo(0x6985);

            open(0x01);

            assertThat(sw(() -> session.sensitiveData(APP, false, new byte[16], 0, 15))).isEqualTo(0x6700);
            assertThat(sw(() -> session.sensitiveData(APP, true, new byte[32], 0, 17))).isEqualTo(0x6700);
            assertThat(session.sensitiveData(APP, false, new byte[16], 0, 0)).isZero();
        }
    }

    @Nested
    class Levels {

        @Test
        void theApplicationCanRaiseTheCurrentLevelAndReturnToTheSessionLevel_gpapi_SecureChannelx_setSecurityLevel() {
            domain.scp03Option(SecurityDomain.OPTION_R_MAC);
            open(0x01);
            unwrap(host.wrap(ECHO));

            session.level(APP, 0x91);
            assertThat(session.level(APP)).isEqualTo((byte) 0x91);
            assertThat(wrap("CAFE9000")).as("R-MAC is added now").hasSize(10);

            session.level(APP, 0x81);
            assertThat(wrap("CAFE9000")).hasSize(2);
            assertThat(session.sessionLevel()).isEqualTo(0x81);
        }

        @Test
        void belowTheSessionLevelUnsupportedOrWithoutASessionIs6985_gpapi_SecureChannelx_setSecurityLevel() {
            assertThat(sw(() -> session.level(APP, 0x81))).as("no session").isEqualTo(0x6985);

            open(0x03);

            assertThat(sw(() -> session.level(APP, 0x81))).as("below the session level").isEqualTo(0x6985);
            assertThat(sw(() -> session.level(APP, 0x93))).as("R-MAC without option b6").isEqualTo(0x6985);
            assertThat(sw(() -> session.level(APP, 0x03))).as("authentication cannot be dropped").isEqualTo(0x6985);
            assertThat(session.currentLevel()).isEqualTo(0x83);
        }
    }

    @Nested
    class Owner {

        @Test
        void aSessionBelongsToTheApplicationThatOpenedIt_gpapi_SecureChannel() {
            open(0x01);
            byte[] wrapped = Arrays.copyOf(host.wrap(ECHO), 64);

            assertThat(sw(() -> session.unwrap(OTHER, wrapped, 0, ECHO.length + 8))).isEqualTo(0x6985);
            assertThat(sw(() -> session.wrap(OTHER, Hex.decode("9000"), 0, 2))).isEqualTo(0x6985);
            assertThat(sw(() -> session.level(OTHER))).isEqualTo(0x6985);
            assertThat(sw(() -> session.sensitiveData(OTHER, false, new byte[16], 0, 16))).isEqualTo(0x6985);
            assertThat(sw(() -> session.level(OTHER, 0x81))).isEqualTo(0x6985);
        }

        @Test
        void resetSecurityOfAnotherApplicationChangesNothingTheOwnersTerminates_gpapi_SecureChannel_resetSecurity() {
            open(0x01);

            session.reset(OTHER);
            assertThat(session.currentLevel()).isEqualTo(0x81);

            session.reset(APP);
            assertThat(session.currentLevel()).isZero();
            session.reset(APP);
            assertThat(session.sessionLevel()).as("does not fail without a session").isZero();
        }
    }
}
