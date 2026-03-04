package name.velikodniy.jcexpress.api;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.APDUException;
import javacard.framework.Applet;
import javacard.framework.AppletEvent;
import javacard.framework.CardException;
import javacard.framework.CardRuntimeException;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.MultiSelectable;
import javacard.framework.OwnerPIN;
import javacard.framework.PIN;
import javacard.framework.PINException;
import javacard.framework.Shareable;
import javacard.framework.SystemException;
import javacard.framework.TransactionException;
import javacard.framework.UserException;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.Checksum;
import javacard.security.CryptoException;
import javacard.security.DESKey;
import javacard.security.DSAKey;
import javacard.security.DSAPrivateKey;
import javacard.security.DSAPublicKey;
import javacard.security.ECKey;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.HMACKey;
import javacard.security.Key;
import javacard.security.KeyAgreement;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.PrivateKey;
import javacard.security.PublicKey;
import javacard.security.RSAPrivateCrtKey;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.SecretKey;
import javacard.security.Signature;
import javacardx.crypto.Cipher;
import javacardx.crypto.KeyEncryption;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for all JavaCard 3.0.5 API stubs.
 * Verifies that stub methods either return default values or throw RuntimeException("stub").
 */
@SuppressWarnings({"java:S3415", "java:S2699"}) // S3415: constant assertions test API values; S2699: some tests verify no-throw
class ApiMethodStubsTest {

    // ── Helpers ──

    private static AID createAid() {
        return new AID(new byte[]{(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x12, 0x01}, (short) 0, (byte) 7);
    }

    private static APDU createApdu() {
        // APDU has no public constructor; use reflection to bypass
        try {
            var ctor = APDU.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Concrete Applet subclass for testing protected methods. */
    private static class TestableApplet extends Applet {
        TestableApplet() {
            super();
        }

        @Override
        public void process(APDU apdu) throws ISOException {
            // no-op
        }

        boolean callSelectingApplet() {
            return selectingApplet();
        }

        void callRegister() {
            register();
        }

        void callRegisterWithAid(byte[] bArray, short bOffset, byte bLength) {
            register(bArray, bOffset, bLength);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.AID
    // ══════════════════════════════════════════════════════════════

    @Nested
    class AIDTests {

        @Test
        void constructor() {
            AID aid = createAid();
            assertThat(aid).isNotNull();
        }

        @Test
        void getBytes() {
            AID aid = createAid();
            byte result = aid.getBytes(new byte[16], (short) 0);
            assertThat(result).isZero();
        }

        @Test
        void equalsBytes() {
            AID aid = createAid();
            boolean result = aid.equals(new byte[]{(byte) 0xA0, 0x00}, (short) 0, (byte) 2);
            assertThat(result).isFalse();
        }

        @Test
        void equalsObject() {
            AID aid = createAid();
            assertThat(aid.equals(new Object())).isFalse();
        }

        @Test
        void partialEquals() {
            AID aid = createAid();
            assertThat(aid.partialEquals(new byte[5], (short) 0, (byte) 5)).isFalse();
        }

        @Test
        void ridEquals() {
            AID aid1 = createAid();
            AID aid2 = createAid();
            assertThat(aid1.RIDEquals(aid2)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.APDU
    // ══════════════════════════════════════════════════════════════

    @Nested
    class APDUTests {

        @Test
        void stateConstants() {
            assertThat(APDU.STATE_INITIAL).isZero();
            assertThat(APDU.STATE_PARTIAL_INCOMING).isEqualTo((byte) 1);
            assertThat(APDU.STATE_FULL_INCOMING).isEqualTo((byte) 2);
            assertThat(APDU.STATE_OUTGOING).isEqualTo((byte) 3);
            assertThat(APDU.STATE_OUTGOING_LENGTH_KNOWN).isEqualTo((byte) 4);
            assertThat(APDU.STATE_PARTIAL_OUTGOING).isEqualTo((byte) 5);
            assertThat(APDU.STATE_FULL_OUTGOING).isEqualTo((byte) 6);
            assertThat(APDU.STATE_ERROR_NO_T0_GETRESPONSE).isEqualTo((byte) 7);
            assertThat(APDU.STATE_ERROR_T1_IFD_ABORT).isEqualTo((byte) 8);
            assertThat(APDU.STATE_ERROR_IO).isEqualTo((byte) 9);
            assertThat(APDU.STATE_ERROR_NO_T0_REISSUE).isEqualTo((byte) 10);
        }

        @Test
        void protocolConstants() {
            assertThat(APDU.PROTOCOL_MEDIA_DEFAULT).isZero();
            assertThat(APDU.PROTOCOL_T0).isZero();
            assertThat(APDU.PROTOCOL_T1).isEqualTo((byte) 1);
        }

        @Test
        void getBuffer() {
            APDU apdu = createApdu();
            assertThat(apdu.getBuffer()).isNull();
        }

        @Test
        void getProtocol() {
            assertThat(APDU.getProtocol()).isZero();
        }

        @Test
        void getInBlockSize() {
            assertThat(APDU.getInBlockSize()).isZero();
        }

        @Test
        void getOutBlockSize() {
            assertThat(APDU.getOutBlockSize()).isZero();
        }

        @Test
        void getNAD() {
            APDU apdu = createApdu();
            assertThat(apdu.getNAD()).isZero();
        }

        @Test
        void setOutgoing() {
            APDU apdu = createApdu();
            assertThat(apdu.setOutgoing()).isZero();
        }

        @Test
        void setOutgoingNoChaining() {
            APDU apdu = createApdu();
            assertThat(apdu.setOutgoingNoChaining()).isZero();
        }

        @Test
        void setOutgoingLength() {
            APDU apdu = createApdu();
            assertThatThrownBy(() -> apdu.setOutgoingLength((short) 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void receiveBytes() {
            APDU apdu = createApdu();
            assertThat(apdu.receiveBytes((short) 0)).isZero();
        }

        @Test
        void setIncomingAndReceive() {
            APDU apdu = createApdu();
            assertThat(apdu.setIncomingAndReceive()).isZero();
        }

        @Test
        void sendBytes() {
            APDU apdu = createApdu();
            assertThatThrownBy(() -> apdu.sendBytes((short) 0, (short) 5))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void sendBytesLong() {
            APDU apdu = createApdu();
            assertThatThrownBy(() -> apdu.sendBytesLong(new byte[10], (short) 0, (short) 5))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void setOutgoingAndSend() {
            APDU apdu = createApdu();
            assertThatThrownBy(() -> apdu.setOutgoingAndSend((short) 0, (short) 5))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void getMaxCommitCapacity() {
            assertThat(APDU.getMaxCommitCapacity()).isZero();
        }

        @Test
        void getCurrentState() {
            APDU apdu = createApdu();
            assertThat(apdu.getCurrentState()).isZero();
        }

        @Test
        void waitExtension() {
            assertThatThrownBy(APDU::waitExtension)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.Applet
    // ══════════════════════════════════════════════════════════════

    @Nested
    class AppletTests {

        @Test
        void installDoesNotThrow() {
            Applet.install(new byte[0], (short) 0, (byte) 0);
        }

        @Test
        void selectReturnsTrue() {
            TestableApplet applet = new TestableApplet();
            assertThat(applet.select()).isTrue();
        }

        @Test
        void deselectDoesNotThrow() {
            TestableApplet applet = new TestableApplet();
            applet.deselect();
        }

        @Test
        void getShareableInterfaceObjectReturnsNull() {
            TestableApplet applet = new TestableApplet();
            assertThat(applet.getShareableInterfaceObject(createAid(), (byte) 0)).isNull();
        }

        @Test
        void registerThrowsStub() {
            TestableApplet applet = new TestableApplet();
            assertThatThrownBy(applet::callRegister)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void registerWithAidThrowsStub() {
            TestableApplet applet = new TestableApplet();
            assertThatThrownBy(() -> applet.callRegisterWithAid(new byte[7], (short) 0, (byte) 7))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void selectingAppletReturnsFalse() {
            TestableApplet applet = new TestableApplet();
            assertThat(applet.callSelectingApplet()).isFalse();
        }

        @Test
        void equalsUsesIdentity() {
            TestableApplet applet = new TestableApplet();
            assertThat(applet.equals(applet)).isTrue();
            assertThat(applet.equals(new TestableApplet())).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.CardException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class CardExceptionTests {

        @Test
        void constructorAndGetReason() {
            CardException ex = new CardException((short) 42);
            assertThat(ex.getReason()).isEqualTo((short) 42);
        }

        @Test
        void setReason() {
            CardException ex = new CardException((short) 1);
            ex.setReason((short) 99);
            assertThat(ex.getReason()).isEqualTo((short) 99);
        }

        @Test
        void throwIt() {
            assertThatThrownBy(() -> CardException.throwIt((short) 7))
                    .isInstanceOf(CardException.class)
                    .satisfies(t -> assertThat(((CardException) t).getReason()).isEqualTo((short) 7));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.CardRuntimeException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class CardRuntimeExceptionTests {

        @Test
        void constructorAndGetReason() {
            CardRuntimeException ex = new CardRuntimeException((short) 10);
            assertThat(ex.getReason()).isEqualTo((short) 10);
        }

        @Test
        void setReason() {
            CardRuntimeException ex = new CardRuntimeException((short) 1);
            ex.setReason((short) 55);
            assertThat(ex.getReason()).isEqualTo((short) 55);
        }

        @Test
        void throwIt() {
            assertThatThrownBy(() -> CardRuntimeException.throwIt((short) 3))
                    .isInstanceOf(CardRuntimeException.class)
                    .satisfies(t -> assertThat(((CardRuntimeException) t).getReason()).isEqualTo((short) 3));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.ISO7816
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ISO7816Tests {

        @Test
        void offsetConstants() {
            assertThat(ISO7816.OFFSET_CLA).isZero();
            assertThat(ISO7816.OFFSET_INS).isEqualTo((byte) 1);
            assertThat(ISO7816.OFFSET_P1).isEqualTo((byte) 2);
            assertThat(ISO7816.OFFSET_P2).isEqualTo((byte) 3);
            assertThat(ISO7816.OFFSET_LC).isEqualTo((byte) 4);
            assertThat(ISO7816.OFFSET_CDATA).isEqualTo((byte) 5);
        }

        @Test
        void claConstant() {
            assertThat(ISO7816.CLA_ISO7816).isZero();
        }

        @Test
        void statusWordConstants() {
            assertThat(ISO7816.SW_NO_ERROR).isEqualTo((short) 0x9000);
            assertThat(ISO7816.SW_BYTES_REMAINING_00).isEqualTo((short) 0x6100);
            assertThat(ISO7816.SW_WRONG_LENGTH).isEqualTo((short) 0x6700);
            assertThat(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED).isEqualTo((short) 0x6982);
            assertThat(ISO7816.SW_FILE_INVALID).isEqualTo((short) 0x6983);
            assertThat(ISO7816.SW_DATA_INVALID).isEqualTo((short) 0x6984);
            assertThat(ISO7816.SW_CONDITIONS_NOT_SATISFIED).isEqualTo((short) 0x6985);
            assertThat(ISO7816.SW_COMMAND_NOT_ALLOWED).isEqualTo((short) 0x6986);
            assertThat(ISO7816.SW_APPLET_SELECT_FAILED).isEqualTo((short) 0x6999);
            assertThat(ISO7816.SW_WRONG_DATA).isEqualTo((short) 0x6A80);
            assertThat(ISO7816.SW_FUNC_NOT_SUPPORTED).isEqualTo((short) 0x6A81);
            assertThat(ISO7816.SW_FILE_NOT_FOUND).isEqualTo((short) 0x6A82);
            assertThat(ISO7816.SW_RECORD_NOT_FOUND).isEqualTo((short) 0x6A83);
            assertThat(ISO7816.SW_INCORRECT_P1P2).isEqualTo((short) 0x6A86);
            assertThat(ISO7816.SW_WRONG_P1P2).isEqualTo((short) 0x6B00);
            assertThat(ISO7816.SW_CORRECT_LENGTH_00).isEqualTo((short) 0x6C00);
            assertThat(ISO7816.SW_INS_NOT_SUPPORTED).isEqualTo((short) 0x6D00);
            assertThat(ISO7816.SW_CLA_NOT_SUPPORTED).isEqualTo((short) 0x6E00);
            assertThat(ISO7816.SW_UNKNOWN).isEqualTo((short) 0x6F00);
            assertThat(ISO7816.SW_FILE_FULL).isEqualTo((short) 0x6A84);
            assertThat(ISO7816.SW_LOGICAL_CHANNEL_NOT_SUPPORTED).isEqualTo((short) 0x6881);
            assertThat(ISO7816.SW_SECURE_MESSAGING_NOT_SUPPORTED).isEqualTo((short) 0x6882);
            assertThat(ISO7816.SW_WARNING_STATE_UNCHANGED).isEqualTo((short) 0x6200);
            assertThat(ISO7816.SW_LAST_COMMAND_EXPECTED).isEqualTo((short) 0x6883);
            assertThat(ISO7816.SW_COMMAND_CHAINING_NOT_SUPPORTED).isEqualTo((short) 0x6884);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.JCSystem
    // ══════════════════════════════════════════════════════════════

    @Nested
    class JCSystemTests {

        @Test
        void memoryTypeConstants() {
            assertThat(JCSystem.MEMORY_TYPE_PERSISTENT).isZero();
            assertThat(JCSystem.MEMORY_TYPE_TRANSIENT_RESET).isEqualTo((byte) 1);
            assertThat(JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT).isEqualTo((byte) 2);
        }

        @Test
        void transientConstants() {
            assertThat(JCSystem.NOT_A_TRANSIENT_OBJECT).isZero();
            assertThat(JCSystem.CLEAR_ON_RESET).isEqualTo((byte) 1);
            assertThat(JCSystem.CLEAR_ON_DESELECT).isEqualTo((byte) 2);
        }

        @Test
        void isTransient() {
            assertThat(JCSystem.isTransient(new Object())).isEqualTo(JCSystem.NOT_A_TRANSIENT_OBJECT);
        }

        @Test
        void makeTransientByteArray() {
            byte[] arr = JCSystem.makeTransientByteArray((short) 10, JCSystem.CLEAR_ON_RESET);
            assertThat(arr).hasSize(10);
        }

        @Test
        void makeTransientShortArray() {
            short[] arr = JCSystem.makeTransientShortArray((short) 5, JCSystem.CLEAR_ON_DESELECT);
            assertThat(arr).hasSize(5);
        }

        @Test
        void makeTransientBooleanArray() {
            boolean[] arr = JCSystem.makeTransientBooleanArray((short) 3, JCSystem.CLEAR_ON_RESET);
            assertThat(arr).hasSize(3);
        }

        @Test
        void makeTransientObjectArray() {
            Object[] arr = JCSystem.makeTransientObjectArray((short) 4, JCSystem.CLEAR_ON_DESELECT);
            assertThat(arr).hasSize(4);
        }

        @Test
        void getVersion() {
            assertThat(JCSystem.getVersion()).isEqualTo((short) 0x0305);
        }

        @Test
        void getAID() {
            assertThat(JCSystem.getAID()).isNull();
        }

        @Test
        void lookupAID() {
            assertThat(JCSystem.lookupAID(new byte[5], (short) 0, (byte) 5)).isNull();
        }

        @Test
        void getPreviousContextAID() {
            assertThat(JCSystem.getPreviousContextAID()).isNull();
        }

        @Test
        void getAvailableMemory() {
            assertThat(JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_PERSISTENT)).isEqualTo(Short.MAX_VALUE);
        }

        @Test
        void getAssignedChannel() {
            assertThat(JCSystem.getAssignedChannel()).isZero();
        }

        @Test
        void beginTransaction() {
            assertThatThrownBy(JCSystem::beginTransaction)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void abortTransaction() {
            assertThatThrownBy(JCSystem::abortTransaction)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void commitTransaction() {
            assertThatThrownBy(JCSystem::commitTransaction)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void getTransactionDepth() {
            assertThat(JCSystem.getTransactionDepth()).isZero();
        }

        @Test
        void getMaxCommitCapacity() {
            assertThat(JCSystem.getMaxCommitCapacity()).isEqualTo(Short.MAX_VALUE);
        }

        @Test
        void requestObjectDeletion() {
            assertThatThrownBy(JCSystem::requestObjectDeletion)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void getAppletShareableInterfaceObject() {
            assertThat(JCSystem.getAppletShareableInterfaceObject(createAid(), (byte) 0)).isNull();
        }

        @Test
        void isObjectDeletionSupported() {
            assertThat(JCSystem.isObjectDeletionSupported()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.OwnerPIN
    // ══════════════════════════════════════════════════════════════

    @Nested
    class OwnerPINTests {

        @Test
        void constructor() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThat(pin).isNotNull();
        }

        @Test
        void check() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThat(pin.check(new byte[]{1, 2, 3, 4}, (short) 0, (byte) 4)).isFalse();
        }

        @Test
        void isValidated() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThat(pin.isValidated()).isFalse();
        }

        @Test
        void getTriesRemaining() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThat(pin.getTriesRemaining()).isZero();
        }

        @Test
        void reset() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThatThrownBy(pin::reset)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void update() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThatThrownBy(() -> pin.update(new byte[]{1, 2, 3, 4}, (short) 0, (byte) 4))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }

        @Test
        void resetAndUnblock() {
            OwnerPIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThatThrownBy(pin::resetAndUnblock)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stub");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.Util
    // ══════════════════════════════════════════════════════════════

    @Nested
    class UtilTests {

        @Test
        void arrayCopy() {
            short result = Util.arrayCopy(new byte[5], (short) 0, new byte[5], (short) 2, (short) 3);
            assertThat(result).isEqualTo((short) 5);
        }

        @Test
        void arrayCopyNonAtomic() {
            short result = Util.arrayCopyNonAtomic(new byte[5], (short) 0, new byte[5], (short) 1, (short) 4);
            assertThat(result).isEqualTo((short) 5);
        }

        @Test
        void arrayFill() {
            short result = Util.arrayFill(new byte[10], (short) 2, (short) 5, (byte) 0xFF);
            assertThat(result).isEqualTo((short) 7);
        }

        @Test
        void arrayFillNonAtomic() {
            short result = Util.arrayFillNonAtomic(new byte[10], (short) 0, (short) 10, (byte) 0);
            assertThat(result).isEqualTo((short) 10);
        }

        @Test
        void arrayCompare() {
            short result = Util.arrayCompare(new byte[3], (short) 0, new byte[3], (short) 0, (short) 3);
            assertThat(result).isZero();
        }

        @Test
        void makeShort() {
            assertThat(Util.makeShort((byte) 0x12, (byte) 0x34)).isEqualTo((short) 0x1234);
            assertThat(Util.makeShort((byte) 0, (byte) 0)).isZero();
            assertThat(Util.makeShort((byte) 0xFF, (byte) 0xFF)).isEqualTo((short) -1);
        }

        @Test
        void getShort() {
            byte[] arr = {0x12, 0x34};
            assertThat(Util.getShort(arr, (short) 0)).isEqualTo((short) 0x1234);
        }

        @Test
        void setShort() {
            short result = Util.setShort(new byte[4], (short) 1, (short) 0x5678);
            assertThat(result).isEqualTo((short) 3);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.ISOException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ISOExceptionTests {

        @Test
        void constructor() {
            ISOException ex = new ISOException(ISO7816.SW_NO_ERROR);
            assertThat(ex.getReason()).isEqualTo(ISO7816.SW_NO_ERROR);
        }

        @Test
        void throwIt() {
            assertThatThrownBy(() -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED))
                    .isInstanceOf(ISOException.class)
                    .satisfies(t -> assertThat(((ISOException) t).getReason()).isEqualTo(ISO7816.SW_INS_NOT_SUPPORTED));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.APDUException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class APDUExceptionTests {

        @Test
        void constants() {
            assertThat(APDUException.ILLEGAL_USE).isEqualTo((short) 1);
            assertThat(APDUException.BUFFER_BOUNDS).isEqualTo((short) 2);
            assertThat(APDUException.BAD_LENGTH).isEqualTo((short) 3);
            assertThat(APDUException.IO_ERROR).isEqualTo((short) 4);
            assertThat(APDUException.NO_T0_GETRESPONSE).isEqualTo((short) 0xAA);
            assertThat(APDUException.NO_T0_REISSUE).isEqualTo((short) 0xAB);
            assertThat(APDUException.T1_IFD_ABORT).isEqualTo((short) 1);
        }

        @Test
        void constructorAndThrowIt() {
            APDUException ex = new APDUException(APDUException.BAD_LENGTH);
            assertThat(ex.getReason()).isEqualTo(APDUException.BAD_LENGTH);

            assertThatThrownBy(() -> APDUException.throwIt(APDUException.IO_ERROR))
                    .isInstanceOf(APDUException.class)
                    .satisfies(t -> assertThat(((APDUException) t).getReason()).isEqualTo(APDUException.IO_ERROR));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.SystemException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class SystemExceptionTests {

        @Test
        void constants() {
            assertThat(SystemException.ILLEGAL_VALUE).isEqualTo((short) 1);
            assertThat(SystemException.NO_TRANSIENT_SPACE).isEqualTo((short) 2);
            assertThat(SystemException.ILLEGAL_TRANSIENT).isEqualTo((short) 3);
            assertThat(SystemException.ILLEGAL_AID).isEqualTo((short) 4);
            assertThat(SystemException.NO_RESOURCE).isEqualTo((short) 5);
            assertThat(SystemException.ILLEGAL_USE).isEqualTo((short) 6);
        }

        @Test
        void constructorAndThrowIt() {
            SystemException ex = new SystemException(SystemException.ILLEGAL_VALUE);
            assertThat(ex.getReason()).isEqualTo(SystemException.ILLEGAL_VALUE);

            assertThatThrownBy(() -> SystemException.throwIt(SystemException.NO_RESOURCE))
                    .isInstanceOf(SystemException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.PINException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class PINExceptionTests {

        @Test
        void constants() {
            assertThat(PINException.ILLEGAL_VALUE).isEqualTo((short) 1);
            assertThat(PINException.ILLEGAL_STATE).isEqualTo((short) 2);
        }

        @Test
        void constructorAndThrowIt() {
            PINException ex = new PINException(PINException.ILLEGAL_VALUE);
            assertThat(ex.getReason()).isEqualTo(PINException.ILLEGAL_VALUE);

            assertThatThrownBy(() -> PINException.throwIt(PINException.ILLEGAL_STATE))
                    .isInstanceOf(PINException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.TransactionException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class TransactionExceptionTests {

        @Test
        void constants() {
            assertThat(TransactionException.IN_PROGRESS).isEqualTo((short) 1);
            assertThat(TransactionException.NOT_IN_PROGRESS).isEqualTo((short) 2);
            assertThat(TransactionException.BUFFER_FULL).isEqualTo((short) 3);
            assertThat(TransactionException.INTERNAL_FAILURE).isEqualTo((short) 4);
            assertThat(TransactionException.ILLEGAL_USE).isEqualTo((short) 5);
        }

        @Test
        void constructorAndThrowIt() {
            TransactionException ex = new TransactionException(TransactionException.BUFFER_FULL);
            assertThat(ex.getReason()).isEqualTo(TransactionException.BUFFER_FULL);

            assertThatThrownBy(() -> TransactionException.throwIt(TransactionException.IN_PROGRESS))
                    .isInstanceOf(TransactionException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework.UserException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class UserExceptionTests {

        @Test
        void defaultConstructor() {
            UserException ex = new UserException();
            assertThat(ex.getReason()).isZero();
        }

        @Test
        void constructorWithReason() {
            UserException ex = new UserException((short) 42);
            assertThat(ex.getReason()).isEqualTo((short) 42);
        }

        @Test
        void throwIt() {
            assertThatThrownBy(() -> UserException.throwIt((short) 99))
                    .isInstanceOf(UserException.class)
                    .satisfies(t -> assertThat(((UserException) t).getReason()).isEqualTo((short) 99));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.framework interfaces: Shareable, PIN, AppletEvent, MultiSelectable
    // ══════════════════════════════════════════════════════════════

    @Nested
    class InterfaceTests {

        @Test
        void shareableIsMarkerInterface() {
            // Shareable has no methods; just verify it can be implemented
            Shareable s = new Shareable() {};
            assertThat(s).isNotNull();
        }

        @Test
        void pinInterfaceMethodsViaConcrete() {
            // PIN methods are tested via OwnerPIN which implements PIN
            PIN pin = new OwnerPIN((byte) 3, (byte) 8);
            assertThat(pin.isValidated()).isFalse();
            assertThat(pin.getTriesRemaining()).isZero();
            assertThat(pin.check(new byte[4], (short) 0, (byte) 4)).isFalse();
        }

        @Test
        void appletEventInterface() {
            // Verify AppletEvent can be implemented
            AppletEvent event = () -> {};
            event.uninstall();
            assertThat(event).isNotNull();
        }

        @Test
        void multiSelectableInterface() {
            MultiSelectable ms = new MultiSelectable() {
                @Override
                public boolean select(boolean appInstAlreadyActive) {
                    return false;
                }

                @Override
                public void deselect(boolean appInstStillActive) {
                    // Intentionally empty: test stub for MultiSelectable interface
                }
            };
            assertThat(ms.select(false)).isFalse();
            ms.deselect(true);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.CryptoException
    // ══════════════════════════════════════════════════════════════

    @Nested
    class CryptoExceptionTests {

        @Test
        void constants() {
            assertThat(CryptoException.ILLEGAL_VALUE).isEqualTo((short) 1);
            assertThat(CryptoException.UNINITIALIZED_KEY).isEqualTo((short) 2);
            assertThat(CryptoException.NO_SUCH_ALGORITHM).isEqualTo((short) 3);
            assertThat(CryptoException.INVALID_INIT).isEqualTo((short) 4);
            assertThat(CryptoException.ILLEGAL_USE).isEqualTo((short) 5);
        }

        @Test
        void constructorAndThrowIt() {
            CryptoException ex = new CryptoException(CryptoException.NO_SUCH_ALGORITHM);
            assertThat(ex.getReason()).isEqualTo(CryptoException.NO_SUCH_ALGORITHM);

            assertThatThrownBy(() -> CryptoException.throwIt(CryptoException.ILLEGAL_VALUE))
                    .isInstanceOf(CryptoException.class)
                    .satisfies(t -> assertThat(((CryptoException) t).getReason()).isEqualTo(CryptoException.ILLEGAL_VALUE));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.KeyAgreement
    // ══════════════════════════════════════════════════════════════

    @Nested
    class KeyAgreementTests {

        @Test
        void constants() {
            assertThat(KeyAgreement.ALG_EC_SVDP_DH).isEqualTo((byte) 1);
            assertThat(KeyAgreement.ALG_EC_SVDP_DHC).isEqualTo((byte) 2);
            assertThat(KeyAgreement.ALG_EC_SVDP_DH_PLAIN).isEqualTo((byte) 3);
            assertThat(KeyAgreement.ALG_EC_SVDP_DHC_PLAIN).isEqualTo((byte) 4);
        }

        @Test
        void getInstanceReturnsNull() {
            assertThat(KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DH, false)).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.KeyBuilder
    // ══════════════════════════════════════════════════════════════

    @Nested
    class KeyBuilderTests {

        @Test
        void typeConstants() {
            assertThat(KeyBuilder.TYPE_DES_TRANSIENT_RESET).isEqualTo((byte) 1);
            assertThat(KeyBuilder.TYPE_DES_TRANSIENT_DESELECT).isEqualTo((byte) 2);
            assertThat(KeyBuilder.TYPE_DES).isEqualTo((byte) 3);
            assertThat(KeyBuilder.TYPE_RSA_PUBLIC).isEqualTo((byte) 4);
            assertThat(KeyBuilder.TYPE_RSA_PRIVATE).isEqualTo((byte) 5);
            assertThat(KeyBuilder.TYPE_RSA_CRT_PRIVATE).isEqualTo((byte) 6);
            assertThat(KeyBuilder.TYPE_DSA_PUBLIC).isEqualTo((byte) 7);
            assertThat(KeyBuilder.TYPE_DSA_PRIVATE).isEqualTo((byte) 8);
            assertThat(KeyBuilder.TYPE_EC_F2M_PUBLIC).isEqualTo((byte) 9);
            assertThat(KeyBuilder.TYPE_EC_F2M_PRIVATE).isEqualTo((byte) 10);
            assertThat(KeyBuilder.TYPE_EC_FP_PUBLIC).isEqualTo((byte) 11);
            assertThat(KeyBuilder.TYPE_EC_FP_PRIVATE).isEqualTo((byte) 12);
            assertThat(KeyBuilder.TYPE_AES_TRANSIENT_RESET).isEqualTo((byte) 13);
            assertThat(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT).isEqualTo((byte) 14);
            assertThat(KeyBuilder.TYPE_AES).isEqualTo((byte) 15);
            assertThat(KeyBuilder.TYPE_HMAC_TRANSIENT_RESET).isEqualTo((byte) 18);
            assertThat(KeyBuilder.TYPE_HMAC_TRANSIENT_DESELECT).isEqualTo((byte) 19);
            assertThat(KeyBuilder.TYPE_HMAC).isEqualTo((byte) 20);
        }

        @Test
        void lengthConstants() {
            assertThat(KeyBuilder.LENGTH_DES).isEqualTo((short) 64);
            assertThat(KeyBuilder.LENGTH_DES3_2KEY).isEqualTo((short) 128);
            assertThat(KeyBuilder.LENGTH_DES3_3KEY).isEqualTo((short) 192);
            assertThat(KeyBuilder.LENGTH_RSA_512).isEqualTo((short) 512);
            assertThat(KeyBuilder.LENGTH_RSA_1024).isEqualTo((short) 1024);
            assertThat(KeyBuilder.LENGTH_RSA_2048).isEqualTo((short) 2048);
            assertThat(KeyBuilder.LENGTH_EC_FP_128).isEqualTo((short) 128);
            assertThat(KeyBuilder.LENGTH_EC_FP_160).isEqualTo((short) 160);
            assertThat(KeyBuilder.LENGTH_EC_FP_192).isEqualTo((short) 192);
            assertThat(KeyBuilder.LENGTH_EC_FP_224).isEqualTo((short) 224);
            assertThat(KeyBuilder.LENGTH_EC_FP_256).isEqualTo((short) 256);
            assertThat(KeyBuilder.LENGTH_EC_FP_384).isEqualTo((short) 384);
            assertThat(KeyBuilder.LENGTH_EC_FP_521).isEqualTo((short) 521);
            assertThat(KeyBuilder.LENGTH_AES_128).isEqualTo((short) 128);
            assertThat(KeyBuilder.LENGTH_AES_192).isEqualTo((short) 192);
            assertThat(KeyBuilder.LENGTH_AES_256).isEqualTo((short) 256);
            assertThat(KeyBuilder.LENGTH_HMAC_SHA_1_BLOCK_64).isEqualTo((short) 64);
            assertThat(KeyBuilder.LENGTH_HMAC_SHA_256_BLOCK_64).isEqualTo((short) 64);
            assertThat(KeyBuilder.LENGTH_HMAC_SHA_384_BLOCK_128).isEqualTo((short) 128);
            assertThat(KeyBuilder.LENGTH_HMAC_SHA_512_BLOCK_128).isEqualTo((short) 128);
        }

        @Test
        void buildKeyReturnsNull() {
            assertThat(KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_256, false)).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.KeyPair
    // ══════════════════════════════════════════════════════════════

    @Nested
    class KeyPairTests {

        @Test
        void constants() {
            assertThat(KeyPair.ALG_RSA).isEqualTo((byte) 1);
            assertThat(KeyPair.ALG_RSA_CRT).isEqualTo((byte) 2);
            assertThat(KeyPair.ALG_DSA).isEqualTo((byte) 3);
            assertThat(KeyPair.ALG_EC_F2M).isEqualTo((byte) 4);
            assertThat(KeyPair.ALG_EC_FP).isEqualTo((byte) 5);
        }

        @Test
        void constructorWithAlgorithm() {
            KeyPair kp = new KeyPair(KeyPair.ALG_RSA, (short) 1024);
            assertThat(kp).isNotNull();
        }

        @Test
        void constructorWithKeys() {
            KeyPair kp = new KeyPair(null, null);
            assertThat(kp).isNotNull();
        }

        @Test
        void genKeyPairDoesNotThrow() {
            KeyPair kp = new KeyPair(KeyPair.ALG_RSA, (short) 1024);
            kp.genKeyPair(); // should not throw
        }

        @Test
        void getPublicReturnsNull() {
            KeyPair kp = new KeyPair(KeyPair.ALG_EC_FP, (short) 256);
            assertThat(kp.getPublic()).isNull();
        }

        @Test
        void getPrivateReturnsNull() {
            KeyPair kp = new KeyPair(KeyPair.ALG_EC_FP, (short) 256);
            assertThat(kp.getPrivate()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.MessageDigest
    // ══════════════════════════════════════════════════════════════

    @Nested
    class MessageDigestTests {

        @Test
        void constants() {
            assertThat(MessageDigest.ALG_SHA).isEqualTo((byte) 1);
            assertThat(MessageDigest.ALG_MD5).isEqualTo((byte) 2);
            assertThat(MessageDigest.ALG_SHA_256).isEqualTo((byte) 4);
            assertThat(MessageDigest.ALG_SHA_384).isEqualTo((byte) 5);
            assertThat(MessageDigest.ALG_SHA_512).isEqualTo((byte) 6);
            assertThat(MessageDigest.ALG_SHA_224).isEqualTo((byte) 7);
        }

        @Test
        void getInstanceReturnsNull() {
            assertThat(MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false)).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.RandomData
    // ══════════════════════════════════════════════════════════════

    @Nested
    class RandomDataTests {

        @Test
        void constants() {
            assertThat(RandomData.ALG_PSEUDO_RANDOM).isEqualTo((byte) 1);
            assertThat(RandomData.ALG_SECURE_RANDOM).isEqualTo((byte) 2);
        }

        @Test
        void getInstanceReturnsNull() {
            assertThat(RandomData.getInstance(RandomData.ALG_SECURE_RANDOM)).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.Signature
    // ══════════════════════════════════════════════════════════════

    @Nested
    class SignatureTests {

        @Test
        void algorithmConstants() {
            assertThat(Signature.ALG_DES_MAC4_NOPAD).isEqualTo((byte) 1);
            assertThat(Signature.ALG_DES_MAC8_NOPAD).isEqualTo((byte) 2);
            assertThat(Signature.ALG_DES_MAC4_ISO9797_M1).isEqualTo((byte) 3);
            assertThat(Signature.ALG_DES_MAC8_ISO9797_M1).isEqualTo((byte) 4);
            assertThat(Signature.ALG_DES_MAC4_ISO9797_M2).isEqualTo((byte) 5);
            assertThat(Signature.ALG_DES_MAC8_ISO9797_M2).isEqualTo((byte) 6);
            assertThat(Signature.ALG_DES_MAC4_PKCS5).isEqualTo((byte) 7);
            assertThat(Signature.ALG_DES_MAC8_PKCS5).isEqualTo((byte) 8);
            assertThat(Signature.ALG_RSA_SHA_ISO9796).isEqualTo((byte) 9);
            assertThat(Signature.ALG_RSA_SHA_PKCS1).isEqualTo((byte) 10);
            assertThat(Signature.ALG_RSA_MD5_PKCS1).isEqualTo((byte) 11);
            assertThat(Signature.ALG_RSA_RIPEMD160_ISO9796).isEqualTo((byte) 12);
            assertThat(Signature.ALG_RSA_RIPEMD160_PKCS1).isEqualTo((byte) 13);
            assertThat(Signature.ALG_DSA_SHA).isEqualTo((byte) 14);
            assertThat(Signature.ALG_RSA_SHA_RFC2409).isEqualTo((byte) 15);
            assertThat(Signature.ALG_RSA_MD5_RFC2409).isEqualTo((byte) 16);
            assertThat(Signature.ALG_ECDSA_SHA).isEqualTo((byte) 17);
            assertThat(Signature.ALG_AES_MAC_128_NOPAD).isEqualTo((byte) 18);
            assertThat(Signature.ALG_HMAC_SHA1).isEqualTo((byte) 24);
            assertThat(Signature.ALG_HMAC_SHA_256).isEqualTo((byte) 25);
            assertThat(Signature.ALG_HMAC_SHA_384).isEqualTo((byte) 26);
            assertThat(Signature.ALG_HMAC_SHA_512).isEqualTo((byte) 27);
            assertThat(Signature.ALG_ECDSA_SHA_256).isEqualTo((byte) 33);
            assertThat(Signature.ALG_ECDSA_SHA_384).isEqualTo((byte) 34);
            assertThat(Signature.ALG_ECDSA_SHA_512).isEqualTo((byte) 38);
            assertThat(Signature.ALG_RSA_SHA_256_PKCS1).isEqualTo((byte) 40);
            assertThat(Signature.ALG_RSA_SHA_384_PKCS1).isEqualTo((byte) 41);
            assertThat(Signature.ALG_RSA_SHA_512_PKCS1).isEqualTo((byte) 42);
            assertThat(Signature.ALG_AES_CMAC_128).isEqualTo((byte) 49);
        }

        @Test
        void modeConstants() {
            assertThat(Signature.MODE_SIGN).isEqualTo((byte) 1);
            assertThat(Signature.MODE_VERIFY).isEqualTo((byte) 2);
        }

        @Test
        void getInstanceReturnsNull() {
            assertThat(Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false)).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacardx.crypto.Cipher
    // ══════════════════════════════════════════════════════════════

    @Nested
    class CipherTests {

        @Test
        void desConstants() {
            assertThat(Cipher.ALG_DES_CBC_NOPAD).isEqualTo((byte) 1);
            assertThat(Cipher.ALG_DES_CBC_ISO9797_M1).isEqualTo((byte) 2);
            assertThat(Cipher.ALG_DES_CBC_ISO9797_M2).isEqualTo((byte) 3);
            assertThat(Cipher.ALG_DES_CBC_PKCS5).isEqualTo((byte) 4);
            assertThat(Cipher.ALG_DES_ECB_NOPAD).isEqualTo((byte) 5);
            assertThat(Cipher.ALG_DES_ECB_ISO9797_M1).isEqualTo((byte) 6);
            assertThat(Cipher.ALG_DES_ECB_ISO9797_M2).isEqualTo((byte) 7);
            assertThat(Cipher.ALG_DES_ECB_PKCS5).isEqualTo((byte) 8);
        }

        @Test
        void rsaConstants() {
            assertThat(Cipher.ALG_RSA_ISO14888).isEqualTo((byte) 9);
            assertThat(Cipher.ALG_RSA_PKCS1).isEqualTo((byte) 10);
            assertThat(Cipher.ALG_RSA_ISO9796).isEqualTo((byte) 11);
            assertThat(Cipher.ALG_RSA_NOPAD).isEqualTo((byte) 12);
            assertThat(Cipher.ALG_RSA_PKCS1_OAEP).isEqualTo((byte) 15);
        }

        @Test
        void aesConstants() {
            assertThat(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD).isEqualTo((byte) 13);
            assertThat(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD).isEqualTo((byte) 14);
            assertThat(Cipher.ALG_AES_CBC_ISO9797_M1).isEqualTo((byte) 16);
            assertThat(Cipher.ALG_AES_CBC_ISO9797_M2).isEqualTo((byte) 17);
            assertThat(Cipher.ALG_AES_CBC_PKCS5).isEqualTo((byte) 18);
            assertThat(Cipher.ALG_AES_ECB_ISO9797_M1).isEqualTo((byte) 19);
            assertThat(Cipher.ALG_AES_ECB_ISO9797_M2).isEqualTo((byte) 20);
            assertThat(Cipher.ALG_AES_ECB_PKCS5).isEqualTo((byte) 21);
        }

        @Test
        void modeConstants() {
            assertThat(Cipher.MODE_DECRYPT).isEqualTo((byte) 1);
            assertThat(Cipher.MODE_ENCRYPT).isEqualTo((byte) 2);
        }

        @Test
        void getInstanceReturnsNull() {
            assertThat(Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false)).isNull();
        }

        @Test
        void getInstanceWithExternalAccessReturnsNull() {
            assertThat(Cipher.getInstance(Cipher.ALG_DES_CBC_NOPAD, true)).isNull();
        }

        @Test
        void abstractMethodsAreDeclared() {
            // Verify all abstract methods exist: init(2-arg), init(5-arg), getAlgorithm, doFinal, update
            assertThat(Cipher.class.getDeclaredMethods()).hasSizeGreaterThanOrEqualTo(5);
        }

        @Test
        void concreteSubclassCanBeCreated() {
            Cipher cipher = new Cipher() {
                @Override
                public void init(Key theKey, byte theMode) {
                    // no-op
                }

                @Override
                public void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) {
                    // no-op
                }

                @Override
                public byte getAlgorithm() {
                    return ALG_DES_CBC_NOPAD;
                }

                @Override
                public short doFinal(byte[] inBuff, short inOffset, short inLength,
                                     byte[] outBuff, short outOffset) {
                    return 0;
                }

                @Override
                public short update(byte[] inBuff, short inOffset, short inLength,
                                    byte[] outBuff, short outOffset) {
                    return 0;
                }
            };
            assertThat(cipher.getAlgorithm()).isEqualTo(Cipher.ALG_DES_CBC_NOPAD);
            cipher.init(null, Cipher.MODE_ENCRYPT); // should not throw
            cipher.init(null, Cipher.MODE_DECRYPT, new byte[8], (short) 0, (short) 8); // should not throw
            assertThat(cipher.doFinal(new byte[8], (short) 0, (short) 8,
                    new byte[8], (short) 0)).isZero();
            assertThat(cipher.update(new byte[8], (short) 0, (short) 8,
                    new byte[8], (short) 0)).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacardx.crypto.KeyEncryption (interface)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class KeyEncryptionTests {

        @Test
        void interfaceMethods() {
            KeyEncryption ke = new KeyEncryption() {
                private Cipher cipher;

                @Override
                public void setKeyCipher(Cipher keyCipher) {
                    this.cipher = keyCipher;
                }

                @Override
                public Cipher getKeyCipher() {
                    return cipher;
                }
            };
            ke.setKeyCipher(null);
            assertThat(ke.getKeyCipher()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security.Checksum
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ChecksumTests {

        @Test
        void constants() {
            assertThat(Checksum.ALG_ISO3309_CRC16).isEqualTo((byte) 1);
            assertThat(Checksum.ALG_ISO3309_CRC32).isEqualTo((byte) 2);
        }

        @Test
        void getInstanceReturnsNull() {
            assertThat(Checksum.getInstance(Checksum.ALG_ISO3309_CRC16, false)).isNull();
        }

        @Test
        void getInstanceWithExternalAccessReturnsNull() {
            assertThat(Checksum.getInstance(Checksum.ALG_ISO3309_CRC32, true)).isNull();
        }

        @Test
        void abstractMethodsAreDeclared() {
            // Verify all abstract methods exist: getAlgorithm, doFinal, update, init
            assertThat(Checksum.class.getDeclaredMethods()).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        void concreteSubclassCanBeCreated() {
            Checksum cs = new Checksum() {
                @Override
                public byte getAlgorithm() {
                    return ALG_ISO3309_CRC16;
                }

                @Override
                public short doFinal(byte[] inBuff, short inOffset, short inLength,
                                     byte[] outBuff, short outOffset) {
                    return 0;
                }

                @Override
                public void update(byte[] inBuff, short inOffset, short inLength) {
                    // no-op
                }

                @Override
                public void init(byte[] bArray, short bOff, short bLen) {
                    // no-op
                }
            };
            assertThat(cs.getAlgorithm()).isEqualTo(Checksum.ALG_ISO3309_CRC16);
            assertThat(cs.doFinal(new byte[4], (short) 0, (short) 4,
                    new byte[4], (short) 0)).isZero();
            cs.update(new byte[4], (short) 0, (short) 4); // should not throw
            cs.init(new byte[4], (short) 0, (short) 4); // should not throw
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security: Key interfaces (marker and concrete)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class KeyInterfaceTests {

        @Test
        void keyInterfaceIsAssignableFromSecretKey() {
            assertThat(Key.class).isAssignableFrom(SecretKey.class);
        }

        @Test
        void keyInterfaceIsAssignableFromPublicKey() {
            assertThat(Key.class).isAssignableFrom(PublicKey.class);
        }

        @Test
        void keyInterfaceIsAssignableFromPrivateKey() {
            assertThat(Key.class).isAssignableFrom(PrivateKey.class);
        }

        @Test
        void aesKeyExtendsSecretKey() {
            assertThat(SecretKey.class).isAssignableFrom(AESKey.class);
        }

        @Test
        void desKeyExtendsSecretKey() {
            assertThat(SecretKey.class).isAssignableFrom(DESKey.class);
        }

        @Test
        void hmacKeyExtendsSecretKey() {
            assertThat(SecretKey.class).isAssignableFrom(HMACKey.class);
        }

        @Test
        void rsaPublicKeyExtendsPublicKey() {
            assertThat(PublicKey.class).isAssignableFrom(RSAPublicKey.class);
        }

        @Test
        void rsaPrivateKeyExtendsPrivateKey() {
            assertThat(PrivateKey.class).isAssignableFrom(RSAPrivateKey.class);
        }

        @Test
        void rsaPrivateCrtKeyExtendsPrivateKey() {
            assertThat(PrivateKey.class).isAssignableFrom(RSAPrivateCrtKey.class);
        }

        @Test
        void ecPublicKeyExtendsPublicKeyAndECKey() {
            assertThat(PublicKey.class).isAssignableFrom(ECPublicKey.class);
            assertThat(ECKey.class).isAssignableFrom(ECPublicKey.class);
        }

        @Test
        void ecPrivateKeyExtendsPrivateKeyAndECKey() {
            assertThat(PrivateKey.class).isAssignableFrom(ECPrivateKey.class);
            assertThat(ECKey.class).isAssignableFrom(ECPrivateKey.class);
        }

        @Test
        void dsaPublicKeyExtendsPublicKeyAndDSAKey() {
            assertThat(PublicKey.class).isAssignableFrom(DSAPublicKey.class);
            assertThat(DSAKey.class).isAssignableFrom(DSAPublicKey.class);
        }

        @Test
        void dsaPrivateKeyExtendsPrivateKeyAndDSAKey() {
            assertThat(PrivateKey.class).isAssignableFrom(DSAPrivateKey.class);
            assertThat(DSAKey.class).isAssignableFrom(DSAPrivateKey.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // javacard.security: Concrete Key interface method counts
    // (verifying all methods declared on interfaces exist)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class KeyInterfaceMethodTests {

        @Test
        void aesKeyMethods() {
            // AESKey has setKey and getKey
            assertThat(AESKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void desKeyMethods() {
            assertThat(DESKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void hmacKeyMethods() {
            // HMACKey has setKey(byte[], short, short) and getKey(byte[], short)
            assertThat(HMACKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void rsaPublicKeyMethods() {
            // setModulus, setExponent, getModulus, getExponent
            assertThat(RSAPublicKey.class.getDeclaredMethods()).hasSize(4);
        }

        @Test
        void rsaPrivateKeyMethods() {
            assertThat(RSAPrivateKey.class.getDeclaredMethods()).hasSize(4);
        }

        @Test
        void rsaPrivateCrtKeyMethods() {
            // setP, setQ, setDP1, setDQ1, setPQ, getP, getQ, getDP1, getDQ1, getPQ
            assertThat(RSAPrivateCrtKey.class.getDeclaredMethods()).hasSize(10);
        }

        @Test
        void ecKeyMethods() {
            // setFieldFP, setA, setB, setG, setR, setK, getField, getA, getB, getG, getR, getK
            assertThat(ECKey.class.getDeclaredMethods()).hasSize(12);
        }

        @Test
        void ecPublicKeyMethods() {
            // setW, getW
            assertThat(ECPublicKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void ecPrivateKeyMethods() {
            // setS, getS
            assertThat(ECPrivateKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void dsaKeyMethods() {
            // setP, setQ, setG, getP, getQ, getG
            assertThat(DSAKey.class.getDeclaredMethods()).hasSize(6);
        }

        @Test
        void dsaPublicKeyMethods() {
            // setY, getY
            assertThat(DSAPublicKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void dsaPrivateKeyMethods() {
            // setX, getX
            assertThat(DSAPrivateKey.class.getDeclaredMethods()).hasSize(2);
        }

        @Test
        void keyBaseMethods() {
            // getSize, getType, isInitialized, clearKey
            assertThat(Key.class.getDeclaredMethods()).hasSize(4);
        }
    }
}
