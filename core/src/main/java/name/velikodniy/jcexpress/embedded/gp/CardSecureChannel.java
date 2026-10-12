package name.velikodniy.jcexpress.embedded.gp;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import name.velikodniy.jcexpress.AID;
import org.globalplatform.SecureChannelx;
import org.globalplatform.SecureChannelx2;

import java.util.function.Supplier;

/**
 * The object {@code GPSystem.getSecureChannel()} returns on a simulated card: the secure channel services of the
 * card's Security Domain (GlobalPlatform Card API 1.8, interfaces SecureChannel, SecureChannelx and
 * SecureChannelx2), with SCP03 behind them ({@link Scp03Session}). Every call is made on behalf of the application
 * whose code runs; the session belongs to the application that opened it.
 */
final class CardSecureChannel implements SecureChannelx, SecureChannelx2 {

    private final Scp03Session session;
    private final Supplier<AID> caller;

    /**
     * Creates the channel object of a Security Domain.
     *
     * @param session the protocol state of the domain
     * @param caller  tells which application is calling
     */
    CardSecureChannel(Scp03Session session, Supplier<AID> caller) {
        this.session = session;
        this.caller = caller;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The data field of INITIALIZE UPDATE and EXTERNAL AUTHENTICATE is received here when the applet has not
     * received it yet (GlobalPlatform Card API 1.8, SecureChannel.processSecurity: the method receives the data
     * of the commands it recognises); applets that call {@code setIncomingAndReceive()} first are served as well.
     * Other commands are refused before anything is received. GlobalPlatform commands have short lengths (GPCS
     * v2.3.1 11.1.5), so an extended length command is a wrong length.</p>
     */
    @Override
    public short processSecurity(APDU apdu) throws ISOException {
        byte[] buffer = apdu.getBuffer();
        boolean recognised = Scp03Initiation.recognises(buffer[ISO7816.OFFSET_CLA] & 0xFF,
                buffer[ISO7816.OFFSET_INS] & 0xFF);
        if (recognised && apdu.getCurrentState() == APDU.STATE_INITIAL) {
            apdu.setIncomingAndReceive();
        }
        if (recognised && apdu.getOffsetCdata() != ISO7816.OFFSET_CDATA) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        int length = ISO7816.OFFSET_CDATA + (buffer[ISO7816.OFFSET_LC] & 0xFF);
        return session.process(caller.get(), buffer, 0, length, ISO7816.OFFSET_CDATA);
    }

    @Override
    public short processSecurity(byte[] baBuffer, short sInOffset, short sInLength, short sOutOffset)
            throws ISOException {
        return session.process(caller.get(), baBuffer, sInOffset, sInLength, sOutOffset);
    }

    @Override
    public short wrap(byte[] baBuffer, short sOffset, short sLength) throws ISOException {
        return session.wrap(caller.get(), baBuffer, sOffset, sLength);
    }

    @Override
    public short unwrap(byte[] baBuffer, short sOffset, short sLength) throws ISOException {
        return session.unwrap(caller.get(), baBuffer, sOffset, sLength);
    }

    @Override
    public short decryptData(byte[] baBuffer, short sOffset, short sLength) throws ISOException {
        return session.sensitiveData(caller.get(), false, baBuffer, sOffset, sLength);
    }

    @Override
    public short encryptData(byte[] baBuffer, short sOffset, short sLength) throws ISOException {
        return session.sensitiveData(caller.get(), true, baBuffer, sOffset, sLength);
    }

    @Override
    public void resetSecurity() {
        session.reset(caller.get());
    }

    @Override
    public byte getSecurityLevel() {
        return session.level(caller.get());
    }

    @Override
    public void setSecurityLevel(byte bSecurityLevel) {
        session.level(caller.get(), bSecurityLevel & 0xFF);
    }
}
