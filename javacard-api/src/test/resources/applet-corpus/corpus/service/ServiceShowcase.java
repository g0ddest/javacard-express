package corpus.service;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.service.BasicService;
import javacard.framework.service.Dispatcher;
import javacard.framework.service.SecurityService;
import javacard.framework.service.ServiceException;

/**
 * Corpus applet: the service framework (a dispatcher with a basic service that answers one command).
 */
public class ServiceShowcase extends Applet {

    private final Dispatcher dispatcher;

    private ServiceShowcase() {
        dispatcher = new Dispatcher((short) 2);
        dispatcher.addService(new VersionService(), Dispatcher.PROCESS_COMMAND);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ServiceShowcase();
    }

    public void process(APDU apdu) {
        dispatcher.process(apdu);
    }

    /** Answers INS 0xCA with a two-byte version number. */
    static final class VersionService extends BasicService {

        public boolean processCommand(APDU apdu) {
            if (getINS(apdu) != (byte) 0xCA) {
                return false;
            }
            if (getP1(apdu) != 0) {
                return fail(apdu, ISO7816.SW_WRONG_P1P2);
            }
            byte[] buf = apdu.getBuffer();
            buf[ISO7816.OFFSET_CDATA] = 1;
            buf[ISO7816.OFFSET_CDATA + 1] = SecurityService.PROPERTY_OUTPUT_INTEGRITY;
            try {
                setOutputLength(apdu, (short) 2);
            } catch (ServiceException e) {
                return fail(apdu, ISO7816.SW_UNKNOWN);
            }
            return succeed(apdu);
        }
    }
}
