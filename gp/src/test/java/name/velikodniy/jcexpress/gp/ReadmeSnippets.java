package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.KeyDiversification;
import name.velikodniy.jcexpress.scp.KeyInfo;
import name.velikodniy.jcexpress.scp.SCPKeys;
import name.velikodniy.jcexpress.scp.SecureChannel;

import java.nio.file.Path;
import java.util.List;

/**
 * The Java code blocks of gp/README.md, one method per block, with the README's variable names as parameters.
 *
 * <p>The methods are compiled but never executed (they need a real card); {@link ReadmeSnippetsTest} checks
 * that every line of every README code block appears here, so the README cannot drift from the API.</p>
 */
@SuppressWarnings("unused")
final class ReadmeSnippets {

    private ReadmeSnippets() {
    }

    static void quickStart(SmartCardSession card) {
        GPSession gp = GPSession.on(card)
            .keys(SCPKeys.defaultKeys())   // the well-known 40..4F test keys of development cards
            .open();

        List<AppletInfo> apps = gp.getStatus();
        byte[] iin = gp.getIIN();

        gp.close();                        // destroys the session keys
    }

    static void authentication(SmartCardSession card, byte[] encKey, byte[] macKey, byte[] dekKey) {
        GPSession gp = GPSession.on(card)
            .securityDomain("A000000151000000")        // SELECT the ISD first (optional)
            .keys(SCPKeys.of(encKey, macKey, dekKey))   // static keys
            .keyVersion(0x30)                           // INITIALIZE UPDATE P1 (0 = card's choice)
            .securityLevel(GP.SECURITY_C_MAC_C_ENC)     // EXTERNAL AUTHENTICATE P1
            .open();
    }

    static void scp03S16(SmartCardSession card, SCPKeys keys) {
        GPSession.on(card).keys(keys).scp03S16(true).open();
    }

    static void scp02Option(SmartCardSession card, SCPKeys keys) {
        CardData data = CardData.parse(card.send(0x80, 0xCA, 0x00, 0x66, null, 256).data());
        data.secureChannelProtocols();   // e.g. [SCP02 i=15]

        GPSession.on(card).keys(keys).scp02Option(0x55).open();   // only needed for options other than 15/55
    }

    static void secureChannelWithApduSequence(SecureChannel scp, SmartCardSession card, byte[] plainApdu) {
        byte[] wrapped = scp.wrap(plainApdu);
        APDUResponse r = scp.unwrap(APDUSequence.on(card).leCorrection(false).transmit(wrapped));   // 61XX completed
    }

    static void loadAndInstall(GPSession gp, String appletAid, String instanceAid, byte[] params) {
        CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));

        // INSTALL [for load] + LOAD + INSTALL [for install and make selectable]
        gp.loadAndInstall(cap, null, 0x00, null);                       // single-applet CAP, instance AID = applet AID
        gp.loadAndInstall(cap, "A0000000031010", 0x00, null);           // other instance AID
        gp.loadAndInstall(cap, appletAid, instanceAid, 0x00, params);   // CAP files with several applets
    }

    static void individualSteps(GPSession gp, CAPFile cap, byte[] sdAid, byte[] loadParams, String packageAid,
                                String appletAid, String instanceAid, byte[] installParams) {
        gp.installForLoad(cap.packageAidHex(), null);                    // null = the selected Security Domain
        gp.installForLoad(cap.packageAid(), sdAid, cap.loadFileDataBlockHash("SHA-256", true), loadParams);
        gp.load(cap);                                                    // or gp.load(cap.loadFileData(false)) without Descriptor
        gp.installForInstall(packageAid, appletAid, instanceAid, 0x00, installParams);
        gp.deleteAid(instanceAid);
        gp.deleteAid(Hex.decode(packageAid), true);                      // package and all its applications
    }

    static void getStatus(GPSession gp) {
        List<AppletInfo> apps = gp.getStatus();                                    // applications and SDs ('40')
        AppletInfo isd = gp.getStatus(Lifecycle.SCOPE_ISD).getFirst();             // '80'
        List<AppletInfo> files = gp.getLoadFiles();                                // '20'
        List<AppletInfo> modules = gp.getStatus(Lifecycle.SCOPE_LOAD_FILES_AND_MODULES);  // '10'

        isd.privilegeBytes();                 // all three privilege bytes (tag 'C5')
        files.getFirst().versionNumber();     // tag 'CE'
        modules.getFirst().executableModuleAids();   // tags '84'
    }

    static void getData(GPSession gp) {
        byte[] iin = gp.getIIN();                       // GET DATA '0042'
        byte[] cin = gp.getCIN();                       // GET DATA '0045'
        CardData cardData = gp.getCardData();           // GET DATA '0066', Card Recognition Data
        cardData.gpVersion();                           // e.g. "1.2.840.114283.2.2.1.1" (GP 2.1.1)
        cardData.secureChannelProtocols();              // e.g. [SCP02 i=15]
        CPLCData cplc = gp.getCPLC();                   // GET DATA '9F7F', 42-byte CPLC
        List<KeyInfoEntry> keys = gp.getKeyInformation();   // GET DATA '00E0'
        int counter = gp.getSequenceCounter();          // GET DATA '00C1'
        APDUResponse r = gp.getData(0x00, 0xCF);        // any GET DATA, Le '00'
    }

    static void keyManagement(GPSession gp, byte[] enc, byte[] mac, byte[] dek, byte[] keyBytes) {
        // Replace the current key set, or add one if the card still has a factory key set (KVN above '7F')
        gp.putKeys(SCPKeys.aes(enc, mac, dek), 0x01);

        // Replace key set 01 by key set 02: same key type and length as the keys being replaced
        gp.putKeys(SCPKeys.aes(enc, mac, dek), 0x02, 0x01);

        // Add a single key (Key Identifier 1) as a new key set 03
        gp.putKey(1, keyBytes, KeyInfo.KeyType.AES, 0x03, 0x00);
    }

    static void diversification(SmartCardSession card, byte[] masterKey) {
        GPSession gp = GPSession.on(card)
            .keys(SCPKeys.fromMasterKey(masterKey))
            .diversification(KeyDiversification::visa2)
            .open();
    }

    static void lifecycle(GPSession gp, byte[] sdAid) {
        gp.lockApp("A0000000031010");      // SET STATUS 40 80 <AID>: to LOCKED
        gp.unlockApp("A0000000031010");    // SET STATUS 40 00 <AID>: back from LOCKED
        gp.setStatus(Lifecycle.SCOPE_SD_AND_APPS, sdAid, Lifecycle.APP_LOCKED);   // SD and its applications
    }

    static void cardLifeCycle(GPSession gp) {
        gp.lockCard();                     // SET STATUS 80 7F: SECURED -> CARD_LOCKED, read the rules below first
        gp.unlockCard();                   // SET STATUS 80 0F: CARD_LOCKED -> SECURED, only where it is still possible
        gp.terminateCard();                // SET STATUS 80 FF: TERMINATED, irreversible, the card is unusable
    }

    static void securityDomains(GPSession gp, byte[] data) {
        List<AppletInfo> domains = gp.getDomains();     // GET STATUS '40' filtered by the SD privilege
        gp.extradite("A0000000031010", "A000000004");   // INSTALL [for extradition]
        gp.personalize("A0000000031010");               // INSTALL [for personalization], then storeData(...)
        gp.storeData(data);                             // STORE DATA blocks, P1 '80' on the last one
        gp.registryUpdate("A0000000031010", Privileges.CARD_RESET);   // INSTALL [for registry update]
    }

    static void capFile() {
        CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));

        cap.packageAidHex();          // Load File AID (package AID, or CAP AID of an Extended CAP file)
        cap.appletAids();             // applet AIDs from the Applet component
        cap.isExtended();             // Extended format (CAP 2.3)
        cap.componentNames();         // loadable components in the JCVM 3.1 section 6.3 install order
        cap.loadFileData();           // 'C4' BER-length components (with Descriptor)
        cap.loadFileData(false);      // without the Descriptor, which is optional for loading
    }
}
