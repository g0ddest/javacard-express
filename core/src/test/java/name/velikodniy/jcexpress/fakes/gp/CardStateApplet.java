package name.velikodniy.jcexpress.fakes.gp;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Shareable;
import javacard.framework.Util;
import org.globalplatform.CVM;
import org.globalplatform.GPRegistryEntry;
import org.globalplatform.GPSystem;
import org.globalplatform.GlobalService;

/**
 * An applet that calls the state, registry and service methods of {@code org.globalplatform.GPSystem} and answers
 * what they returned (class '80'); booleans are answered as '01' or '00'. As a Global Services application it
 * hands out a {@link GlobalService} whose service object is a small {@link CVM}.
 *
 * <pre>
 * INS 30  getCardContentState()
 * INS 32  setCardContentState(P1)
 * INS 34  getCardState()
 * INS 36  lockCard()                       INS 38  terminateCard()
 * INS 3A  setATRHistBytes(command data)
 * INS 3C  getCardInfo(P1 P2)               INS 3E  getCardInfoLength(P1 P2), two bytes
 * INS 40  the entry of the AID in the command data (own entry without data): '00' when null, else '01', state,
 *         three privilege bytes, isAssociated(Issuer Security Domain), the AID of the entry
 * INS 44  setState(P1) on that entry
 * INS 46  registerService(P1 P2)           INS 48  deregisterService(P1 P2)
 * INS 4A  getService(AID of the command data or null, P1 P2): '01' when a GlobalService came back
 * INS 4C  getCVM(P1): '00' when null, else '01' and getTriesRemaining()
 * INS 4E  what the install method saw: getCardState(), and getCardContentState() after register()
 * </pre>
 */
public class CardStateApplet extends Applet implements GlobalService {

    private static final byte[] ISD = {(byte) 0xA0, 0x00, 0x00, 0x01, 0x51, 0x00, 0x00, 0x00};

    private final byte cardStateAtInstall;
    private byte contentStateAtInstall;
    private final TestCvm cvm = new TestCvm();

    private CardStateApplet() {
        cardStateAtInstall = GPSystem.getCardState();
    }

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        CardStateApplet applet = new CardStateApplet();
        applet.register(bArray, (short) (bOffset + 1), bArray[bOffset]);
        applet.contentStateAtInstall = GPSystem.getCardContentState();
    }

    @Override
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return parameter == GPSystem.GLOBAL_SERVICE_IDENTIFIER ? this : null;
    }

    @Override
    public Shareable getServiceInterface(GPRegistryEntry clientRegistryEntry, short sServiceName, byte[] baBuffer,
                                         short sOffset, short sLength) throws ISOException {
        return cvm;
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        short name = Util.getShort(buffer, ISO7816.OFFSET_P1);
        byte p1 = buffer[ISO7816.OFFSET_P1];
        short length = 1;
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x30 -> buffer[0] = GPSystem.getCardContentState();
            case 0x32 -> buffer[0] = bool(GPSystem.setCardContentState(p1));
            case 0x34 -> buffer[0] = GPSystem.getCardState();
            case 0x36 -> buffer[0] = bool(GPSystem.lockCard());
            case 0x38 -> buffer[0] = bool(GPSystem.terminateCard());
            case 0x3A -> buffer[0] = bool(GPSystem.setATRHistBytes(buffer, ISO7816.OFFSET_CDATA, (byte) lc));
            case 0x3C -> length = GPSystem.getCardInfo(buffer, (short) 0, name);
            case 0x3E -> length = Util.setShort(buffer, (short) 0, GPSystem.getCardInfoLength(name));
            case 0x40 -> length = describe(entry(buffer, lc), buffer);
            case 0x44 -> buffer[0] = bool(entry(buffer, lc).setState(p1));
            case 0x46 -> GPSystem.getRegistryEntry(null).registerService(name);
            case 0x48 -> GPSystem.getRegistryEntry(null).deregisterService(name);
            case 0x4A -> buffer[0] = bool(GPSystem.getService(aid(buffer, lc), name) != null);
            case 0x4C -> length = cvm(p1, buffer);
            case 0x4E -> {
                buffer[0] = cardStateAtInstall;
                buffer[1] = contentStateAtInstall;
                length = 2;
            }
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        apdu.setOutgoingAndSend((short) 0, length);
    }

    private static AID aid(byte[] buffer, short lc) {
        return lc == 0 ? null : new AID(buffer, ISO7816.OFFSET_CDATA, (byte) lc);
    }

    private static GPRegistryEntry entry(byte[] buffer, short lc) {
        return lc == 0 ? GPSystem.getRegistryEntry(null) : GPSystem.getRegistryEntry(new AID(buffer,
                ISO7816.OFFSET_CDATA, (byte) lc));
    }

    private static short describe(GPRegistryEntry entry, byte[] buffer) {
        if (entry == null) {
            buffer[0] = 0;
            return 1;
        }
        buffer[0] = 1;
        buffer[1] = entry.getState();
        short next = entry.getPrivileges(buffer, (short) 2);
        buffer[next] = bool(entry.isAssociated(new AID(ISD, (short) 0, (byte) ISD.length)));
        return (short) (next + 1 + entry.getAID().getBytes(buffer, (short) (next + 1)));
    }

    private static short cvm(byte identifier, byte[] buffer) {
        CVM found = GPSystem.getCVM(identifier);
        buffer[0] = bool(found != null);
        if (found == null) {
            return 1;
        }
        buffer[1] = found.getTriesRemaining();
        return 2;
    }

    private static byte bool(boolean value) {
        return (byte) (value ? 1 : 0);
    }
}
