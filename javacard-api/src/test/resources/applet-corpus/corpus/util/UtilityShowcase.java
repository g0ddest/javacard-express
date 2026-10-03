package corpus.util;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacardx.framework.math.BCDUtil;
import javacardx.framework.math.BigNumber;
import javacardx.framework.math.ParityBit;
import javacardx.framework.string.StringUtil;
import javacardx.framework.tlv.BERTLV;
import javacardx.framework.tlv.BERTag;
import javacardx.framework.tlv.ConstructedBERTLV;
import javacardx.framework.tlv.PrimitiveBERTLV;
import javacardx.framework.tlv.PrimitiveBERTag;
import javacardx.framework.util.ArrayLogic;
import javacardx.framework.util.intx.JCint;

/**
 * Corpus applet: the javacardx utility packages (BER-TLV, big numbers, BCD, parity, UTF-8 strings, generic
 * array logic and the optional int helpers).
 */
public class UtilityShowcase extends Applet {

    private final ConstructedBERTLV template = new ConstructedBERTLV((short) 128);
    private final PrimitiveBERTLV element = new PrimitiveBERTLV((short) 32);
    private final PrimitiveBERTag tag = new PrimitiveBERTag();
    private final BigNumber amount = new BigNumber((short) 8);
    private final short[] words = new short[8];

    private UtilityShowcase() {
        tag.init(BERTag.BER_TAG_CLASS_MASK_APPLICATION, (short) 0x10);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new UtilityShowcase();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        short out = parseTlv(buf, len);
        out += arithmetic(buf, out);
        out += text(buf, out);
        apdu.setOutgoingAndSend((short) 0, out);
    }

    private short parseTlv(byte[] buf, short len) {
        if (!BERTLV.verifyFormat(buf, ISO7816.OFFSET_CDATA, len)) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        template.init(buf, ISO7816.OFFSET_CDATA, len);
        BERTLV found = template.find(tag);
        if (found == null) {
            element.init(tag, buf, ISO7816.OFFSET_CDATA, (short) 1);
            template.append(element);
            found = element;
        }
        short valueOffset = PrimitiveBERTLV.getValueOffset(buf, ISO7816.OFFSET_CDATA);
        if (valueOffset > (short) (ISO7816.OFFSET_CDATA + len)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        return found.toBytes(buf, (short) 0);
    }

    private short arithmetic(byte[] buf, short off) {
        amount.init(buf, off, (short) 4, BigNumber.FORMAT_HEX);
        amount.add(buf, off, (short) 2, BigNumber.FORMAT_HEX);
        if (amount.compareTo(buf, off, (short) 4, BigNumber.FORMAT_HEX) < 0) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }
        short n = BCDUtil.convertToBCD(buf, off, (short) 4, buf, (short) (off + 4));
        ParityBit.set(buf, off, (short) 8, false);
        ArrayLogic.arrayCopyRepack(buf, off, (short) 16, words, (short) 0);
        int counter = JCint.getInt(buf, off);
        JCint.setInt(buf, off, counter + 1);
        return n;
    }

    private short text(byte[] buf, short off) {
        if (!StringUtil.check(buf, off, (short) 8)) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        short n = StringUtil.toUpperCase(buf, off, (short) 8, buf, (short) (off + 8));
        if (StringUtil.compare(true, buf, off, (short) 8, buf, (short) (off + 8), n) != 0) {
            n += StringUtil.valueOf((short) 42, buf, (short) (off + 8 + n));
        }
        return n;
    }
}
