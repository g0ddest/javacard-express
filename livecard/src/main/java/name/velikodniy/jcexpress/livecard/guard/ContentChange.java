package name.velikodniy.jcexpress.livecard.guard;

/**
 * A change of the card content made by a command the guard allowed and the card accepted with '9000'.
 * AIDs are uppercase hex.
 */
public sealed interface ContentChange {

    /**
     * INSTALL [for load] was accepted: a load file with this AID may now exist (fully once its LOAD blocks
     * are accepted), GlobalPlatform Card Specification v2.3.1 11.5.2.3.1.
     *
     * @param loadFileAid the Executable Load File AID
     */
    record LoadFileCreated(String loadFileAid) implements ContentChange {
    }

    /**
     * INSTALL [for install] was accepted: an application instance exists (11.5.2.3.2).
     *
     * @param instanceAid the Application AID
     * @param loadFileAid the Executable Load File AID
     * @param moduleAid   the Executable Module AID
     */
    record InstanceCreated(String instanceAid, String loadFileAid, String moduleAid) implements ContentChange {
    }

    /**
     * DELETE was accepted (11.2.2).
     *
     * @param aid     the deleted AID
     * @param related whether related objects were deleted too (P2 '80': a load file and its applications)
     */
    record Deleted(String aid, boolean related) implements ContentChange {
    }
}
