package name.velikodniy.jcexpress.livecard.sim;

import name.velikodniy.jcexpress.AID;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The card content of a {@link SimulatedCard}: load files with their modules, and application instances. The
 * applets of a load file run on one {@link JCardSimCard}; a load file that imports another one joins that one's
 * card, so shareable interface objects work across their packages. GET STATUS responses follow GlobalPlatform
 * Card Specification v2.3.1 Tables 11-36/11-37 in the shape the real JCOP 4 card produced (see the realcard
 * transcripts).
 */
final class SimulatedContent {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    /** GET STATUS ISD entry of the real card. */
    private static final String ISD_ENTRY = "E32A4F08A0000001510000009F700107C5039EFE80C407A0000000620001CE020100CC08"
            + "A000000151000000";
    /** The built-in load file of the real card and its module. */
    static final String BUILT_IN_LOAD_FILE = "A0000001515350";
    private static final String BUILT_IN_MODULE = "A000000151535041";

    /** A load file: its modules, the load files it imports, and the jCardSim card its applets run on. */
    private record LoadFile(List<String> modules, List<String> imports, JCardSimCard card) {
    }

    /** An application instance; one that another tool installed has no jCardSim card behind it. */
    static final class Application {
        final String aid;
        final String loadFile;
        final JCardSimCard card;
        final int privileges;
        int state;

        Application(String aid, String loadFile, JCardSimCard card, int privileges, int state) {
            this.aid = aid;
            this.loadFile = loadFile;
            this.card = card;
            this.privileges = privileges;
            this.state = state;
        }
    }

    private final String isd;
    private final SimulatedApplets applets;
    private final Map<String, LoadFile> loadFiles = new LinkedHashMap<>();
    private final Map<String, Application> applications = new LinkedHashMap<>();
    private boolean refuseDeletingLocked;

    /**
     * Creates the content of a new card.
     *
     * @param isd           the ISD AID
     * @param applets the applets the card can run
     */
    SimulatedContent(String isd, SimulatedApplets applets) {
        this.isd = isd;
        this.applets = applets;
        loadFiles.put(BUILT_IN_LOAD_FILE, new LoadFile(List.of(BUILT_IN_MODULE), List.of(), null));
    }

    /** Adds a loaded load file; it shares the jCardSim card of the first load file it imports, if any. */
    void addLoadFile(String aid, List<String> modules, List<String> imports) {
        JCardSimCard card = imports.stream().map(loadFiles::get).filter(Objects::nonNull).map(LoadFile::card)
                .filter(Objects::nonNull).findFirst().orElseGet(() -> new JCardSimCard(applets.classPath()));
        loadFiles.put(aid, new LoadFile(List.copyOf(modules), List.copyOf(imports), card));
    }

    boolean hasModule(String loadFile, String module) {
        LoadFile file = loadFiles.get(loadFile);
        return file != null && file.modules().contains(module);
    }

    /**
     * Creates an instance on its load file's jCardSim card; the applet receives [Li AID][00][La params].
     *
     * @return false if the module has no known applet class
     */
    boolean addApplication(String aid, String loadFile, String module, byte[] params, int state) {
        Optional<String> className = applets.className(module);
        JCardSimCard card = loadFiles.get(loadFile).card();
        if (className.isEmpty() || card == null) {
            return false;
        }
        card.install(className.get(), AID.fromHex(aid), params);
        applications.put(aid, new Application(aid, loadFile, card, 0x00, state));
        return true;
    }

    /** Adds an application that another tool installed: listed by GET STATUS, nothing runs behind it. */
    void addForeignApplication(String aid, String loadFile, int privileges, int state) {
        applications.put(aid, new Application(aid, loadFile, null, privileges, state));
    }

    /** Makes DELETE of a LOCKED application fail with '6985', as a card may (never tried on the real card). */
    void refuseDeletingLockedApplications() {
        refuseDeletingLocked = true;
    }

    Application application(String aid) {
        return applications.get(aid);
    }

    List<Application> applications() {
        return List.copyOf(applications.values());
    }

    /** Card reset: every jCardSim card is reset. */
    void reset() {
        cards().forEach(JCardSimCard::reset);
    }

    /**
     * Deletes an application or a load file. A load file that another load file imports cannot be deleted
     * (GlobalPlatform Card Specification v2.3.1 11.2.2.1).
     *
     * @return the status word
     */
    int delete(String aid, boolean related) {
        Application application = applications.get(aid);
        if (application != null) {
            if (refuseDeletingLocked && (application.state & 0x80) != 0) {
                return 0x6985;
            }
            applications.remove(aid);
            if (application.card != null) {
                application.card.delete(AID.fromHex(aid));
            }
            return 0x9000;
        }
        LoadFile file = loadFiles.get(aid);
        if (file == null || aid.equals(BUILT_IN_LOAD_FILE)) {
            return 0x6A88;
        }
        List<Application> instances = applications.values().stream().filter(a -> a.loadFile.equals(aid)).toList();
        boolean imported = loadFiles.values().stream().anyMatch(other -> other.imports().contains(aid));
        if ((!instances.isEmpty() && !related) || imported) {
            return 0x6985;
        }
        instances.forEach(instance -> delete(instance.aid, false));
        loadFiles.remove(aid);
        if (loadFiles.values().stream().noneMatch(other -> other.card() == file.card())) {
            file.card().close();
        }
        return 0x9000;
    }

    private List<JCardSimCard> cards() {
        return loadFiles.values().stream().map(LoadFile::card).filter(Objects::nonNull).distinct().toList();
    }

    /** GET STATUS response data for a P1 scope, or null for '6A88'. */
    byte[] status(int scope) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (scope) {
            case 0x80 -> out.writeBytes(HEX.parseHex(ISD_ENTRY));
            case 0x40 -> applications.values().forEach(app -> out.writeBytes(applicationEntry(app)));
            case 0x20 -> loadFiles.keySet().forEach(aid -> out.writeBytes(entry(aid, List.of())));
            case 0x10 -> loadFiles.forEach((aid, file) -> out.writeBytes(entry(aid, file.modules())));
            default -> {
                return null;
            }
        }
        return out.size() == 0 ? null : out.toByteArray();
    }

    private byte[] applicationEntry(Application app) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        tlv(body, "4F", HEX.parseHex(app.aid));
        tlv(body, "9F70", new byte[]{(byte) app.state});
        tlv(body, "C5", new byte[]{(byte) app.privileges, 0, 0});
        tlv(body, "C4", HEX.parseHex(app.loadFile));
        tlv(body, "CE", new byte[]{1, 0});
        tlv(body, "CC", HEX.parseHex(isd));
        return wrap(body);
    }

    private static byte[] entry(String aid, List<String> modules) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        tlv(body, "4F", HEX.parseHex(aid));
        tlv(body, "9F70", new byte[]{1});
        modules.forEach(module -> tlv(body, "84", HEX.parseHex(module)));
        return wrap(body);
    }

    private static byte[] wrap(ByteArrayOutputStream body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tlv(out, "E3", body.toByteArray());
        return out.toByteArray();
    }

    private static void tlv(ByteArrayOutputStream out, String tag, byte[] value) {
        out.writeBytes(HEX.parseHex(tag));
        out.write(value.length);
        out.writeBytes(value);
    }

    /** Whether a load file's Header component has the flag ACC_INT (JCVM 3.1 §6.4: flags after magic and version). */
    static boolean usesInt(byte[] loadFileData) {
        int offset = (loadFileData[1] & 0xFF) < 0x80 ? 2 : 2 + (loadFileData[1] & 0x7F);
        while (offset + 3 <= loadFileData.length) {
            int size = ((loadFileData[offset + 1] & 0xFF) << 8) | (loadFileData[offset + 2] & 0xFF);
            if (loadFileData[offset] == 1) {
                return (loadFileData[offset + 9] & 0x01) != 0;
            }
            offset += 3 + size;
        }
        return false;
    }

    /** AIDs of the applet modules listed in a load file's Applet component (JCVM 3.0.5 6.5). */
    static List<String> appletAids(byte[] loadFileData) {
        return aids(loadFileData, 3, 0, 2);
    }

    /** AIDs of the packages listed in a load file's Import component (JCVM 3.0.5 6.6). */
    static List<String> importedAids(byte[] loadFileData) {
        return aids(loadFileData, 4, 2, 0);
    }

    /**
     * AIDs of a component whose entries are {@code [prefix bytes][u1 length][AID][suffix bytes]}: Applet
     * component entries end with a u2 install method offset, Import component entries start with the u1 minor
     * and major version.
     */
    private static List<String> aids(byte[] loadFileData, int componentTag, int prefix, int suffix) {
        int offset = (loadFileData[1] & 0xFF) < 0x80 ? 2 : 2 + (loadFileData[1] & 0x7F);
        List<String> aids = new ArrayList<>();
        while (offset + 3 <= loadFileData.length) {
            int tag = loadFileData[offset] & 0xFF;
            int size = ((loadFileData[offset + 1] & 0xFF) << 8) | (loadFileData[offset + 2] & 0xFF);
            if (tag == componentTag) {
                int position = offset + 4;
                for (int i = 0; i < (loadFileData[offset + 3] & 0xFF); i++) {
                    int length = loadFileData[position + prefix] & 0xFF;
                    int start = position + prefix + 1;
                    aids.add(HEX.formatHex(loadFileData, start, start + length));
                    position = start + length + suffix;
                }
            }
            offset += 3 + size;
        }
        return aids;
    }
}
