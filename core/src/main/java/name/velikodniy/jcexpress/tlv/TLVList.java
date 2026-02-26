package name.velikodniy.jcexpress.tlv;

import java.util.*;
import java.util.stream.Stream;

/**
 * An ordered list of TLV elements with search capabilities.
 *
 * <p>Supports direct search via {@link #find(int)} and recursive search
 * via {@link #findRecursive(int)} that descends into constructed TLVs.</p>
 */
public final class TLVList implements Iterable<TLV> {

    private static final TLVList EMPTY = new TLVList(Collections.emptyList());

    private final List<TLV> elements;

    TLVList(List<TLV> elements) {
        this.elements = Collections.unmodifiableList(elements);
    }

    /**
     * Returns an empty TLVList.
     *
     * @return an empty, immutable TLVList
     */
    public static TLVList empty() {
        return EMPTY;
    }

    /**
     * Returns the number of TLV elements.
     *
     * @return element count
     */
    public int size() {
        return elements.size();
    }

    /**
     * Returns true if this list contains no elements.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Returns the element at the given index.
     *
     * @param index zero-based index
     * @return the TLV at that position
     */
    public TLV get(int index) {
        return elements.get(index);
    }

    /**
     * Finds the first direct element with the given tag.
     *
     * @param tag the tag to search for
     * @return the first matching element, or empty
     */
    public Optional<TLV> find(int tag) {
        for (TLV tlv : elements) {
            if (tlv.tag() == tag) {
                return Optional.of(tlv);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns true if any direct element has the given tag.
     *
     * @param tag the tag to check
     * @return true if found
     */
    public boolean contains(int tag) {
        return find(tag).isPresent();
    }

    /**
     * Recursively finds the first element with the given tag,
     * descending into constructed TLVs.
     *
     * @param tag the tag to search for
     * @return the first matching element at any depth, or empty
     */
    public Optional<TLV> findRecursive(int tag) {
        for (TLV tlv : elements) {
            if (tlv.tag() == tag) {
                return Optional.of(tlv);
            }
            if (tlv.isConstructed()) {
                Optional<TLV> found = tlv.children().findRecursive(tag);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Navigates a path of tags through nested constructed TLVs.
     *
     * <p>Example: {@code list.at(0x6F, 0x84)} finds tag 0x6F, then within its
     * children finds tag 0x84.</p>
     *
     * @param tags the tag path to follow
     * @return the TLV at the end of the path, or empty if any tag is not found
     */
    public Optional<TLV> at(int... tags) {
        TLVList current = this;
        for (int i = 0; i < tags.length; i++) {
            Optional<TLV> found = current.find(tags[i]);
            if (found.isEmpty()) return Optional.empty();
            if (i == tags.length - 1) return found;
            current = found.get().children();
        }
        return Optional.empty();
    }

    /**
     * Returns a sequential stream of elements.
     *
     * @return stream of TLV elements
     */
    public Stream<TLV> stream() {
        return elements.stream();
    }

    @Override
    public Iterator<TLV> iterator() {
        return elements.iterator();
    }

    @Override
    public String toString() {
        return "TLVList" + elements;
    }
}
