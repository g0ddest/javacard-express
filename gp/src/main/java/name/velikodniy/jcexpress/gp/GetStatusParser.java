package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVException;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses GET STATUS response data in the format selected by P2.b2 = 1: a sequence of 'E3' templates
 * (GPCS v2.3.1 11.4.3.1, Tables 11-36 and 11-37). Malformed data is reported, never silently dropped.
 */
final class GetStatusParser {

    private static final int TAG_REGISTRY_DATA = 0xE3;
    private static final int TAG_AID = 0x4F;
    private static final int TAG_LIFE_CYCLE = 0x9F70;
    private static final int TAG_PRIVILEGES = 0xC5;
    private static final int TAG_LOAD_FILE_AID = 0xC4;
    private static final int TAG_SECURITY_DOMAIN_AID = 0xCC;
    private static final int TAG_VERSION = 0xCE;
    private static final int TAG_MODULE_AID = 0x84;

    private GetStatusParser() {
    }

    /**
     * Parses the 'E3' templates of one GET STATUS response.
     *
     * @param data the response data field
     * @return the entries in card order
     * @throws GPException if the data is not a sequence of well-formed 'E3' templates with an AID
     */
    static List<AppletInfo> parse(byte[] data) {
        TLVList list;
        try {
            list = TLVParser.parse(data);
        } catch (TLVException e) {
            throw new GPException("Malformed GET STATUS response " + Hex.encode(data)
                    + " (GPCS v2.3.1 Table 11-36): " + e.getMessage(), e);
        }
        List<AppletInfo> entries = new ArrayList<>();
        for (TLV tlv : list) {
            if (tlv.tag() != TAG_REGISTRY_DATA) {
                throw new GPException(String.format("Unexpected tag %X in GET STATUS response (expected 'E3',"
                        + " GPCS v2.3.1 11.4.3.1)", tlv.tag()));
            }
            entries.add(entry(tlv));
        }
        return entries;
    }

    private static AppletInfo entry(TLV template) {
        TLVList children;
        try {
            children = template.children();
        } catch (TLVException e) {
            throw new GPException("Malformed 'E3' template in GET STATUS response: " + e.getMessage(), e);
        }
        byte[] aid = children.find(TAG_AID).map(TLV::value)
                .orElseThrow(() -> new GPException("GET STATUS 'E3' template without AID (tag '4F')"));
        byte[] state = value(children, TAG_LIFE_CYCLE);
        byte[] privileges = value(children, TAG_PRIVILEGES);
        List<byte[]> modules = new ArrayList<>();
        for (TLV child : children) {
            if (child.tag() == TAG_MODULE_AID) {
                modules.add(child.value());
            }
        }
        return new AppletInfo(aid, state.length > 0 ? state[0] & 0xFF : 0,
                privileges.length > 0 ? privileges[0] & 0xFF : 0, privileges,
                value(children, TAG_LOAD_FILE_AID), value(children, TAG_SECURITY_DOMAIN_AID),
                value(children, TAG_VERSION), modules);
    }

    private static byte[] value(TLVList children, int tag) {
        return children.find(tag).map(TLV::value).orElse(new byte[0]);
    }
}
