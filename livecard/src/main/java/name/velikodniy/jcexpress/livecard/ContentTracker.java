package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.livecard.guard.ContentChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers every load file and application instance that commands of one {@link LiveCard} created, as the
 * {@link name.velikodniy.jcexpress.livecard.guard.ApduGuard} confirmed them ('9000' to INSTALL), whichever API
 * sent the commands. {@link LiveCard#cleanup()} deletes what is still there.
 */
final class ContentTracker {

    /** Load files still on the card, in creation order. */
    private final Set<String> loadFiles = new LinkedHashSet<>();
    /** Instances still on the card, with their load file. */
    private final Map<String, String> instances = new LinkedHashMap<>();
    /** Everything ever created, for the final GET STATUS check. */
    private final Set<String> created = new LinkedHashSet<>();

    /**
     * Applies a confirmed change.
     *
     * @param change the change
     */
    synchronized void apply(ContentChange change) {
        switch (change) {
            case ContentChange.LoadFileCreated(String aid) -> {
                loadFiles.add(aid);
                created.add(aid);
            }
            case ContentChange.InstanceCreated(String instance, String loadFile, String _) -> {
                instances.put(instance, loadFile);
                created.add(instance);
            }
            case ContentChange.Deleted(String aid, boolean related) -> forget(aid, related);
        }
    }

    /**
     * Forgets an AID that is no longer on the card.
     *
     * @param aid     the AID
     * @param related whether the load file's instances went with it
     */
    synchronized void forget(String aid, boolean related) {
        instances.remove(aid);
        if (loadFiles.remove(aid) && related) {
            instances.values().removeIf(aid::equals);
        }
    }

    /**
     * Returns the load files still on the card, newest first.
     *
     * @return the load file AIDs
     */
    synchronized List<String> loadFilesNewestFirst() {
        List<String> newestFirst = new ArrayList<>(loadFiles);
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /**
     * Returns the instances whose load file was not created through this card (deleting such a load file
     * with related objects does not remove them).
     *
     * @return the instance AIDs
     */
    synchronized List<String> orphanInstances() {
        return instances.entrySet().stream()
                .filter(entry -> !loadFiles.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Returns whether anything created is still on the card.
     *
     * @return true when nothing is left to delete
     */
    synchronized boolean isEmpty() {
        return loadFiles.isEmpty() && instances.isEmpty();
    }

    /**
     * Returns everything ever created through this card.
     *
     * @return the AIDs
     */
    synchronized Set<String> created() {
        return Set.copyOf(created);
    }
}
