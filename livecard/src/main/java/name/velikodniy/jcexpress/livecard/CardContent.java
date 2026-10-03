package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.gp.AppletInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The card content as reported by GET STATUS (GlobalPlatform Card Specification v2.3.1 11.4).
 *
 * @param applications applications and Security Domains (P1 '40')
 * @param loadFiles    Executable Load Files (P1 '20')
 */
public record CardContent(List<AppletInfo> applications, List<AppletInfo> loadFiles) {

    /**
     * Copies the lists.
     */
    public CardContent {
        applications = List.copyOf(applications);
        loadFiles = List.copyOf(loadFiles);
    }

    /**
     * Finds an application.
     *
     * @param aid the application AID
     * @return its GET STATUS entry, or empty if it is not on the card
     */
    public Optional<AppletInfo> application(AID aid) {
        return applications.stream().filter(info -> info.aidHex().equals(aid.toHex())).findFirst();
    }

    /**
     * Finds a load file.
     *
     * @param aid the load file AID
     * @return its GET STATUS entry, or empty if it is not on the card
     */
    public Optional<AppletInfo> loadFile(AID aid) {
        return loadFiles.stream().filter(info -> info.aidHex().equals(aid.toHex())).findFirst();
    }

    /**
     * Returns the AIDs of applications and load files that start with a prefix.
     *
     * @param prefixHex the prefix, uppercase hex
     * @return the matching AIDs, applications first
     */
    public List<String> aidsUnder(String prefixHex) {
        return Stream.concat(applicationsUnder(prefixHex).stream(), loadFilesUnder(prefixHex).stream()).toList();
    }

    /**
     * Returns the AIDs of applications that start with a prefix.
     *
     * @param prefixHex the prefix, uppercase hex
     * @return the matching application AIDs
     */
    public List<String> applicationsUnder(String prefixHex) {
        return applications.stream().map(AppletInfo::aidHex).filter(aid -> aid.startsWith(prefixHex)).toList();
    }

    /**
     * Returns the AIDs of load files that start with a prefix.
     *
     * @param prefixHex the prefix, uppercase hex
     * @return the matching load file AIDs
     */
    public List<String> loadFilesUnder(String prefixHex) {
        return loadFiles.stream().map(AppletInfo::aidHex).filter(aid -> aid.startsWith(prefixHex)).toList();
    }
}
