package name.velikodniy.jcexpress.fakes.library;

/** A class of a "library" package that {@link name.velikodniy.jcexpress.fakes.isolation.LibraryUserApplet} uses. */
public class Counter {

    private short value;

    /**
     * Increments the counter.
     *
     * @return the new value
     */
    public short next() {
        return ++value;
    }
}
