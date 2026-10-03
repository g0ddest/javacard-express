package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.AppletInfo;
import name.velikodniy.jcexpress.gp.GPException;
import name.velikodniy.jcexpress.gp.GPSession;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deletes card content in a GlobalPlatform session with the guard's write access open: what a test class created,
 * and the leftovers of earlier runs under the AID prefix (DELETE [card content], SET STATUS, GlobalPlatform Card
 * Specification v2.3.1 11.2, 11.10).
 */
final class ContentRemoval {

    /**
     * What leftover removal will delete: everything under the prefix that the harness can have created.
     *
     * @param applications the applications under the prefix
     * @param locked       those of them in a LOCKED state, unlocked first
     * @param loadFiles    the load files under the prefix, deleted without related objects
     */
    record Leftovers(List<String> applications, List<String> locked, List<String> loadFiles) {

        boolean isEmpty() {
            return applications.isEmpty() && loadFiles.isEmpty();
        }

        @Override
        public String toString() {
            return "applications " + applications + " (locked " + locked + "), load files " + loadFiles;
        }
    }

    private final ContentTracker tracker;
    /** Notes in the card's history and transcript, so that every deletion is labelled there. */
    private final Consumer<String> notes;

    /**
     * Creates the removal of a card's content.
     *
     * @param tracker what the card's test class created
     * @param notes   receives a note before every DELETE ({@code "delete <AID> with its applications"})
     */
    ContentRemoval(ContentTracker tracker, Consumer<String> notes) {
        this.tracker = tracker;
        this.notes = notes;
    }

    /**
     * Finds the leftovers under a prefix. The harness installs with privileges '00' only, so an application with
     * privileges (a Security Domain, for instance) is not its leftover: then nothing is deleted at all.
     *
     * @param content the card content (GET STATUS)
     * @param prefix  the AID prefix, uppercase hex
     * @return what to delete
     * @throws LiveCardException if an application under the prefix has privileges
     */
    static Leftovers leftovers(CardContent content, String prefix) {
        List<AppletInfo> applications = content.applications().stream()
                .filter(info -> info.aidHex().startsWith(prefix)).toList();
        List<String> privileged = applications.stream().filter(info -> !allZero(info.privilegeBytes()))
                .map(info -> info.aidHex() + " (privileges " + Hex.encode(info.privilegeBytes()) + ")").toList();
        if (!privileged.isEmpty()) {
            throw new LiveCardException("Not deleted as leftovers under " + prefix + ": " + privileged + "; the"
                    + " harness never installs applications with privileges, so these were not created by it."
                    + " Nothing was deleted; remove them by hand or use another aidPrefix.");
        }
        List<String> locked = applications.stream().filter(info -> (info.lifeCycleState() & 0x80) != 0)
                .map(AppletInfo::aidHex).toList();
        return new Leftovers(applications.stream().map(AppletInfo::aidHex).toList(), locked,
                content.loadFilesUnder(prefix));
    }

    /**
     * Deletes leftovers: LOCKED applications are unlocked first (SET STATUS '40' '00'), then every application,
     * then the load files without related objects, so that the card refuses (and keeps) a load file with an
     * application another tool created outside the prefix.
     *
     * @param gp        the session with write access
     * @param leftovers what to delete
     * @param problems  receives what could not be deleted
     */
    void removeLeftovers(GPSession gp, Leftovers leftovers, List<String> problems) {
        leftovers.locked().forEach(aid -> unlockQuietly(gp, aid, problems));
        leftovers.applications().forEach(aid -> deleteQuietly(gp, aid, false, problems));
        deleteLoadFiles(gp, leftovers.loadFiles(), false, problems);
    }

    /**
     * Deletes load files in the given order, with their applications if {@code related}. A load file that another
     * one imports can only be deleted after it (11.2.2.1), so failures are retried as long as a round deletes
     * something.
     *
     * @param gp        the session with write access
     * @param loadFiles the load files, uppercase hex
     * @param related   true to delete their applications too (P2 '80')
     * @param problems  receives what could not be deleted
     */
    void deleteLoadFiles(GPSession gp, List<String> loadFiles, boolean related, List<String> problems) {
        List<String> pending = loadFiles;
        while (!pending.isEmpty()) {
            List<String> failures = new ArrayList<>();
            List<String> left = pending.stream().filter(aid -> !deleteQuietly(gp, aid, related, failures)).toList();
            if (left.size() == pending.size()) {
                problems.addAll(failures);
                return;
            }
            pending = left;
        }
    }

    /**
     * Deletes an AID; true if it is gone ('6A88' counts as gone), otherwise the problem is added.
     *
     * @param gp       the session with write access
     * @param aid      the AID, uppercase hex
     * @param related  true to delete related objects (P2 '80')
     * @param problems receives the problem
     * @return whether the AID is gone
     */
    boolean deleteQuietly(GPSession gp, String aid, boolean related, List<String> problems) {
        notes.accept("delete " + aid + (related ? " with its applications" : ""));
        try {
            gp.deleteAid(Hex.decode(aid), related);
            return true;
        } catch (GPException e) {
            if (e.statusWord() == 0x6A88) {
                tracker.forget(aid, related);
                return true;
            }
            problems.add(aid + ": " + e.getMessage());
            return false;
        }
    }

    private static void unlockQuietly(GPSession gp, String aid, List<String> problems) {
        try {
            gp.unlockApp(aid);
        } catch (GPException e) {
            problems.add(aid + " (unlock): " + e.getMessage());
        }
    }

    private static boolean allZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
