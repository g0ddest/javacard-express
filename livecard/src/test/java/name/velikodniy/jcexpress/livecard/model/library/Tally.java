package name.velikodniy.jcexpress.livecard.model.library;

/**
 * A class of a library package that an applet of another package uses ({@code ImporterApplet}): a converted applet
 * package then imports this package (JCVM 3.1 §4.3.3, §6.7). Java Card subset only.
 */
public class Tally {

    private short count;

    /**
     * Counts one more.
     *
     * @return the count after this call
     */
    public short next() {
        count++;
        return count;
    }
}
