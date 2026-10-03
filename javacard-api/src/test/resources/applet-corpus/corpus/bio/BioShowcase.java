package corpus.bio;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacardx.biometry.BioBuilder;
import javacardx.biometry.BioTemplate;
import javacardx.biometry.OwnerBioTemplate;
import javacardx.biometry1toN.Bio1toNBuilder;
import javacardx.biometry1toN.OwnerBioMatcher;
import javacardx.biometry1toN.OwnerBioTemplateData;
import javacardx.external.Memory;
import javacardx.external.MemoryAccess;

/**
 * Corpus applet: biometric templates (1:1 and 1:N) and external memory access.
 */
public class BioShowcase extends Applet {

    private final OwnerBioTemplate fingerprint;
    private final OwnerBioMatcher matcher;

    private BioShowcase() {
        fingerprint = BioBuilder.buildBioTemplate(BioBuilder.FINGERPRINT, (byte) 3);
        matcher = Bio1toNBuilder.buildBioMatcher((short) 4, Bio1toNBuilder.FINGERPRINT, (byte) 3);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BioShowcase();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (!fingerprint.isInitialized()) {
            fingerprint.init(buf, ISO7816.OFFSET_CDATA, len);
            fingerprint.doFinal();
            OwnerBioTemplateData slot = matcher.getBioTemplateData((short) 0);
            slot.init(buf, ISO7816.OFFSET_CDATA, len);
            slot.doFinal();
            return;
        }
        short score = fingerprint.initMatch(buf, ISO7816.OFFSET_CDATA, len);
        if (score != BioTemplate.MATCH_NEEDS_MORE_DATA && score < BioTemplate.MINIMUM_SUCCESSFUL_MATCH_SCORE) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        MemoryAccess mifare = Memory.getMemoryAccessInstance(Memory.MEMORY_TYPE_MIFARE, null, (short) 0);
        mifare.readData(buf, (short) 0, buf, ISO7816.OFFSET_CDATA, (short) 6, (short) 1, (short) 0, (short) 16);
        apdu.setOutgoingAndSend((short) 0, (short) 16);
    }
}
